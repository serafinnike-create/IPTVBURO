/**
 * Configurar a lista de um cliente pelo painel.
 *
 * Exercita o handler real contra o SQLite em memória do Node, como os outros
 * testes do Worker: sem conta Cloudflare, sem rede.
 *
 * O que se verifica é sobretudo o que **não** pode acontecer. O código do
 * aparelho aparece na tela da televisão — qualquer pessoa que veja uma foto
 * dessa tela o tem — então ele sozinho jamais pode bastar para buscar uma
 * credencial. E a senha, que é do cliente, não pode ficar legível no banco nem
 * vazar para a trilha de auditoria.
 */

import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.js';
import {
  saveProvisioning,
  provisioningStatus,
  claimProvisioning,
  confirmProvisioning,
  clearProvisioning,
  PROVISIONING_INTERNALS,
} from '../src/provisioning.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
const MIGRATION = readFileSync(
  new URL('../migrations/0010_device_provisioning.sql', import.meta.url),
  'utf8',
);

class LocalD1Statement {
  constructor(database, sql) {
    this.statement = database.prepare(sql);
    this.values = [];
  }

  bind(...values) {
    this.values = values;
    return this;
  }

  first() {
    return this.statement.get(...this.values) ?? null;
  }

  all() {
    return { results: this.statement.all(...this.values) };
  }

  run() {
    const result = this.statement.run(...this.values);
    return { success: true, meta: { changes: Number(result.changes) } };
  }
}

class LocalD1 {
  constructor() {
    this.database = new DatabaseSync(':memory:');
    this.database.exec(SCHEMA);
    this.database.exec(MIGRATION);
  }

  prepare(sql) {
    return new LocalD1Statement(this.database, sql);
  }

  close() {
    this.database.close();
  }
}

function createEnv() {
  return { DB: new LocalD1() };
}

const DEVICE = 'SUMR-SRQG-H4BJ';
const CREDENTIAL = {
  server: 'http://meuprovedor.com:8080',
  username: 'cliente123',
  password: 'senha-do-cliente',
};

function post(path, body) {
  return new Request(`https://iptvburo.test${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  });
}

test('o painel guarda o que a televisão vai aplicar', async () => {
  const env = createEnv();
  const saved = await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  assert.equal(saved.ok, true);

  const status = await provisioningStatus(DEVICE, env);
  assert.equal(status.state, 'PENDING');
  assert.equal(status.server, CREDENTIAL.server);
  assert.equal(status.username, CREDENTIAL.username);
  assert.equal(status.createdBy, 'dono@exemplo');
  env.DB.close();
});

test('a senha não é legível no banco, nem em coluna própria', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);

  /*
    O ponto: um vazamento desta base não pode entregar a lista de ninguém. O
    endereço e o usuário ficam em claro de propósito, para o painel poder dizer
    qual lista está no aparelho; a senha existe apenas dentro do payload cifrado.
  */
  const row = env.DB.prepare('SELECT * FROM device_provisioning WHERE device_id = ?')
    .bind(DEVICE).first();
  const dump = JSON.stringify(row);
  assert.ok(!dump.includes(CREDENTIAL.password));
  assert.ok(dump.includes(CREDENTIAL.server), 'o endereço fica visível para o suporte');
  env.DB.close();
});

test('o painel mostra que existe senha, sem mostrar a senha', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  const status = await provisioningStatus(DEVICE, env);
  /*
    Não é limitação contornável: para trocar um endereço que caiu não é preciso
    recuperar o que estava lá. O painel escreve e nunca lê.
  */
  assert.equal(status.hasPassword, true);
  assert.ok(!JSON.stringify(status).includes(CREDENTIAL.password));
  env.DB.close();
});

test('a televisão recebe a credencial inteira, uma vez pedida', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  const claimed = await claimProvisioning(DEVICE, env);
  assert.deepEqual(claimed, CREDENTIAL);
  env.DB.close();
});

test('a entrega só é dada por concluída quando a televisão confirma', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);

  /*
    Uma entrega que se perde no caminho — a televisão desligada no meio, a rede
    caindo — precisa poder ser tentada de novo na abertura seguinte. Marcar como
    aplicada no momento da entrega deixaria o cliente sem lista e sem como pedir
    de novo.
  */
  await claimProvisioning(DEVICE, env);
  assert.equal((await provisioningStatus(DEVICE, env)).state, 'PENDING');
  assert.deepEqual(await claimProvisioning(DEVICE, env), CREDENTIAL);

  assert.equal(await confirmProvisioning(DEVICE, env), true);
  const applied = await provisioningStatus(DEVICE, env);
  assert.equal(applied.state, 'APPLIED');
  assert.ok(applied.appliedAt);
  env.DB.close();
});

test('confirmada a aplicação, o payload deixa de existir', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  await claimProvisioning(DEVICE, env);
  await confirmProvisioning(DEVICE, env);

  /* A partir daqui o payload só seria risco: a televisão já tem a credencial no
     próprio cofre. Os rótulos ficam, para o suporte saber qual lista é. */
  const row = env.DB.prepare('SELECT payload, server_label FROM device_provisioning WHERE device_id = ?')
    .bind(DEVICE).first();
  assert.equal(row.payload, '');
  assert.equal(row.server_label, CREDENTIAL.server);
  assert.equal(await claimProvisioning(DEVICE, env), null);
  env.DB.close();
});

test('aplicar de novo substitui o anterior em vez de enfileirar', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  /* O caso que motivou tudo: o endereço do provedor caiu e é preciso mandar
     outro. Uma fila de configurações antigas seria aplicada fora de ordem. */
  await saveProvisioning(
    DEVICE, { ...CREDENTIAL, server: 'http://novoprovedor.com:8080' }, 'dono@exemplo', env,
  );
  const claimed = await claimProvisioning(DEVICE, env);
  assert.equal(claimed.server, 'http://novoprovedor.com:8080');

  const total = env.DB.prepare('SELECT COUNT(*) AS total FROM device_provisioning').bind().first();
  assert.equal(total.total, 1);
  env.DB.close();
});

test('um endereço malformado é recusado antes de virar registro', async () => {
  const env = createEnv();
  for (const server of ['', 'nao-e-url', 'ftp://x.com', 'http://u:p@host.com/x']) {
    const result = await saveProvisioning(DEVICE, { ...CREDENTIAL, server }, 'dono', env);
    assert.equal(result.error, 'bad_credentials', `deveria recusar: ${server}`);
  }
  /*
    Credencial embutida no endereço seria uma segunda cópia da senha num campo
    que o painel mostra em claro.
  */
  assert.equal(PROVISIONING_INTERNALS.validServer('http://u:p@host.com/x'), null);
  const total = env.DB.prepare('SELECT COUNT(*) AS total FROM device_provisioning').bind().first();
  assert.equal(total.total, 0);
  env.DB.close();
});

test('usuário ou senha em falta não geram registro pela metade', async () => {
  const env = createEnv();
  assert.equal((await saveProvisioning(DEVICE, { ...CREDENTIAL, username: '' }, 'a', env)).error,
    'bad_credentials');
  assert.equal((await saveProvisioning(DEVICE, { ...CREDENTIAL, password: '' }, 'a', env)).error,
    'bad_credentials');
  const total = env.DB.prepare('SELECT COUNT(*) AS total FROM device_provisioning').bind().first();
  assert.equal(total.total, 0);
  env.DB.close();
});

test('o código na tela não basta: a televisão precisa provar quem é', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);

  /*
    O ponto mais importante deste arquivo. O identificador aparece na tela de
    Licença da televisão: qualquer pessoa que veja uma foto dessa tela o tem. Se
    ele bastasse, veria também a credencial do cliente.
  */
  const response = await worker.fetch(post('/v1/provisioning/claim', { deviceId: DEVICE }), env);
  assert.equal(response.status, 400);
  const body = await response.json();
  assert.ok(!JSON.stringify(body).includes(CREDENTIAL.password));

  /* E continua pendente: uma tentativa recusada não consome a entrega. */
  assert.equal((await provisioningStatus(DEVICE, env)).state, 'PENDING');
  env.DB.close();
});

test('um aparelho sem provisionamento recebe silêncio, não erro', async () => {
  const env = createEnv();
  /* O caso comum: toda abertura de todo aparelho que nunca foi provisionado. */
  assert.equal(await claimProvisioning('OUTRO-APARELHO-XX', env), null);
  assert.equal(await provisioningStatus('OUTRO-APARELHO-XX', env), null);
  env.DB.close();
});

test('tentativas repetidas sem sucesso param, em vez de virarem laço', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  /*
    Dez aberturas sem conseguir aplicar não é rede instável, é configuração
    errada. Parar faz o painel mostrar o problema a quem pode corrigi-lo.
  */
  for (let attempt = 0; attempt < PROVISIONING_INTERNALS.MAX_ATTEMPTS; attempt += 1) {
    assert.ok(await claimProvisioning(DEVICE, env));
  }
  assert.equal(await claimProvisioning(DEVICE, env), null);
  assert.equal((await provisioningStatus(DEVICE, env)).state, 'FAILED');
  env.DB.close();
});

test('cancelar remove o que ainda não foi aplicado', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  assert.equal(await clearProvisioning(DEVICE, env), true);
  assert.equal(await provisioningStatus(DEVICE, env), null);
  assert.equal(await claimProvisioning(DEVICE, env), null);
  env.DB.close();
});

test('o payload de um aparelho não abre com a chave de outro', async () => {
  const sealed = await PROVISIONING_INTERNALS.encryptPayload(DEVICE, 'segredo');
  assert.equal(
    await PROVISIONING_INTERNALS.decryptPayload('OUTRO-APARELHO', sealed.payload, sealed.nonce),
    null,
  );
  assert.equal(
    await PROVISIONING_INTERNALS.decryptPayload(DEVICE, sealed.payload, sealed.nonce),
    'segredo',
  );
});
