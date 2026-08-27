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
import worker, { provisioningAuditDetail } from '../src/index.js';
import {
  saveProvisioning,
  provisioningStatus,
  claimProvisioning,
  confirmProvisioning,
  clearProvisioning,
  PROVISIONING_INTERNALS,
} from '../src/provisioning.js';

const SCHEMA = readFileSync(new URL('../schema.sql', import.meta.url), 'utf8');
/**
 * As migrações posteriores ao schema, nomeadas — e todas elas.
 *
 * Não é a pasta inteira: `schema.sql` já traz o estado atual das tabelas antigas,
 * e reaplicar as primeiras migrações falha com "duplicate column name". São só as
 * que vieram depois dele.
 *
 * Uma migração nova precisa ser acrescentada aqui. Foi o que faltou ao criar a
 * 0011: onze testes falharam por coluna inexistente, e o defeito estava no teste,
 * não no código que ele deveria proteger.
 */
const MIGRATION = [
  '0010_device_provisioning.sql',
  '0011_provisioning_key_labels.sql',
  '0012_provisioning_list_label.sql',
]
  .map((name) => readFileSync(new URL('../migrations/' + name, import.meta.url), 'utf8'))
  .join('\n');

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

function createEnv(overrides = {}) {
  return { DB: new LocalD1(), PROVISIONING_ENCRYPTION_KEY: 'fixture-provisioning-server-secret', ...overrides };
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
  const env = createEnv();
  const sealed = await PROVISIONING_INTERNALS.encryptPayload(DEVICE, 'segredo', env);
  assert.equal(
    await PROVISIONING_INTERNALS.decryptPayload('OUTRO-APARELHO', sealed.payload, sealed.nonce, env),
    null,
  );
  assert.equal(
    await PROVISIONING_INTERNALS.decryptPayload(DEVICE, sealed.payload, sealed.nonce, env),
    'segredo',
  );
});

/**
 * O ponto da auditoria: o device_id sozinho não pode ser a chave.
 *
 * Ele é público — aparece na tela da televisão, na URL da página de compra e no
 * QR code — e fica gravado na própria linha que traz o payload cifrado. Se a
 * derivação da chave dependesse só dele, qualquer leitura desta tabela (um
 * vazamento de backup, um acesso indevido ao D1) recalcularia a chave de cada
 * linha a partir de dado público na própria linha, e a senha do Xtream do
 * cliente sairia em claro. Este teste prova que não basta mais: sem o segredo
 * do servidor certo, nem sabendo o device_id o payload abre.
 */
test('sem o segredo do servidor, o device_id sozinho não decifra o payload', async () => {
  const envA = createEnv({ PROVISIONING_ENCRYPTION_KEY: 'segredo-do-servidor-A' });
  const sealed = await PROVISIONING_INTERNALS.encryptPayload(DEVICE, 'senha-do-cliente', envA);

  const envB = createEnv({ PROVISIONING_ENCRYPTION_KEY: 'segredo-do-servidor-B' });
  assert.equal(
    await PROVISIONING_INTERNALS.decryptPayload(DEVICE, sealed.payload, sealed.nonce, envB),
    null,
    'o mesmo device_id, com o segredo de servidor errado, não deve decifrar nada',
  );

  await assert.rejects(
    () => PROVISIONING_INTERNALS.encryptPayload(DEVICE, 'x', {}),
    /ProvisioningEncryptionKeyMissing/,
    'sem PROVISIONING_ENCRYPTION_KEY configurado, cifrar deve falhar alto e claro, não em silêncio',
  );
});

test('as chaves de API vão junto, dentro do payload cifrado', async () => {
  const env = createEnv();
  /* Resolve o mesmo problema pela mesma pessoa: quem não cadastra um Xtream
     também não cria conta no TMDb. Assim o aplicativo chega mostrando capa e
     sinopse, sem o cliente configurar nada. */
  await saveProvisioning(
    DEVICE,
    { ...CREDENTIAL, metadataKey: 'chave-tmdb-de-teste', criticsKey: 'chave-omdb' },
    'dono@exemplo',
    env,
  );
  const claimed = await claimProvisioning(DEVICE, env);
  assert.equal(claimed.metadataKey, 'chave-tmdb-de-teste');
  assert.equal(claimed.criticsKey, 'chave-omdb');
  env.DB.close();
});

test('as chaves não ficam legíveis no banco', async () => {
  const env = createEnv();
  await saveProvisioning(
    DEVICE, { ...CREDENTIAL, metadataKey: 'chave-tmdb-secreta', criticsKey: 'chave-omdb-secreta' },
    'dono@exemplo', env,
  );
  /* São credenciais como a senha: existem só dentro do payload. O painel escreve
     e nunca lê — trocar um endereço não exige recuperar a chave anterior. */
  const row = env.DB.prepare('SELECT * FROM device_provisioning WHERE device_id = ?')
    .bind(DEVICE).first();
  const dump = JSON.stringify(row);
  assert.ok(!dump.includes('chave-tmdb-secreta'));
  assert.ok(!dump.includes('chave-omdb-secreta'));
  env.DB.close();
});

test('o painel sabe que foram enviadas, sem poder lê-las', async () => {
  const env = createEnv();
  await saveProvisioning(
    DEVICE, { ...CREDENTIAL, metadataKey: 'chave-tmdb-secreta' }, 'dono@exemplo', env,
  );
  const status = await provisioningStatus(DEVICE, env);
  assert.equal(status.metadataKey, true, 'quem vendeu precisa poder dizer que mandou');
  assert.equal(status.criticsKey, false);
  assert.ok(!JSON.stringify(status).includes('chave-tmdb-secreta'));
  env.DB.close();
});

test('o rótulo sobrevive à confirmação, quando o payload já sumiu', async () => {
  const env = createEnv();
  await saveProvisioning(
    DEVICE, { ...CREDENTIAL, metadataKey: 'chave-tmdb' }, 'dono@exemplo', env,
  );
  await claimProvisioning(DEVICE, env);
  await confirmProvisioning(DEVICE, env);
  /* Sem coluna própria não haveria de onde deduzir isto depois: o payload é
     esvaziado na confirmação. */
  assert.equal((await provisioningStatus(DEVICE, env)).metadataKey, true);
  env.DB.close();
});

test('uma lista sem chaves continua sendo aplicada', async () => {
  const env = createEnv();
  /* O caso comum: quem vende manda só a lista. As chaves são opcionais e a
     ausência delas não pode impedir o cliente de assistir. */
  const saved = await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);
  assert.equal(saved.ok, true);
  const claimed = await claimProvisioning(DEVICE, env);
  assert.deepEqual(claimed, CREDENTIAL, 'nem metadataKey nem criticsKey aparecem');
  assert.equal((await provisioningStatus(DEVICE, env)).metadataKey, false);
  env.DB.close();
});

test('uma chave inválida é ignorada, e a lista vai assim mesmo', async () => {
  const env = createEnv();
  /* Uma chave errada nunca deve impedir o cliente de assistir: sem ela o
     aplicativo mostra a lista sem capa, que é o que já fazia antes. */
  for (const bad of ['com espaco', 'aspas"dentro', '<script>', 'x'.repeat(401)]) {
    await saveProvisioning(DEVICE, { ...CREDENTIAL, metadataKey: bad }, 'dono', env);
    const claimed = await claimProvisioning(DEVICE, env);
    assert.equal(claimed.server, CREDENTIAL.server, `a lista deve ir mesmo assim: ${bad}`);
    assert.equal(claimed.metadataKey, undefined, `a chave inválida não pode passar: ${bad}`);
  }
  env.DB.close();
});

test('reenviar sem chave não apaga a que o cliente já tinha', async () => {
  const env = createEnv();
  /* O caso do endereço que caiu: quem vende reenvia só o servidor. O campo
     ausente significa "não mexer", e não "apagar" — o aplicativo distingue os
     dois porque a chave é omitida do payload em vez de ir como null. */
  await saveProvisioning(DEVICE, { ...CREDENTIAL, metadataKey: 'chave-antiga' }, 'dono', env);
  await claimProvisioning(DEVICE, env);
  await confirmProvisioning(DEVICE, env);

  await saveProvisioning(
    DEVICE, { ...CREDENTIAL, server: 'http://novoprovedor.com:8080' }, 'dono', env,
  );
  const claimed = await claimProvisioning(DEVICE, env);
  assert.equal(claimed.server, 'http://novoprovedor.com:8080');
  assert.equal(
    Object.prototype.hasOwnProperty.call(claimed, 'metadataKey'), false,
    'a chave tem de estar ausente, para o aplicativo saber que não deve mexer nela',
  );
  env.DB.close();
});

test('o nome da lista vai junto, para o cliente ver o que comprou', async () => {
  const env = createEnv();
  /* Sem isto o aplicativo deriva o nome do endereço — "cb.visualplay.online" —
     que é como o servidor se chama, não como quem vendeu quer que apareça. */
  await saveProvisioning(DEVICE, { ...CREDENTIAL, listLabel: 'Lista do Lucas' }, 'dono', env);
  const claimed = await claimProvisioning(DEVICE, env);
  assert.equal(claimed.listLabel, 'Lista do Lucas');
  env.DB.close();
});

test('o painel lê o nome de volta, ao contrário da senha', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, { ...CREDENTIAL, listLabel: 'Lista do Lucas' }, 'dono', env);
  /* Um rótulo não é segredo: aparece na tela do cliente. Quem vendeu precisa
     poder conferir o que mandou, inclusive depois de aplicado. */
  assert.equal((await provisioningStatus(DEVICE, env)).listLabel, 'Lista do Lucas');
  await claimProvisioning(DEVICE, env);
  await confirmProvisioning(DEVICE, env);
  assert.equal((await provisioningStatus(DEVICE, env)).listLabel, 'Lista do Lucas');
  env.DB.close();
});

test('sem nome, a lista continua sendo aplicada', async () => {
  const env = createEnv();
  const saved = await saveProvisioning(DEVICE, CREDENTIAL, 'dono', env);
  assert.equal(saved.ok, true);
  const claimed = await claimProvisioning(DEVICE, env);
  /* Ausente e não vazio: o aplicativo distingue os dois e cai para o endereço. */
  assert.equal(Object.prototype.hasOwnProperty.call(claimed, 'listLabel'), false);
  assert.equal((await provisioningStatus(DEVICE, env)).listLabel, null);
  env.DB.close();
});

test('reenviar sem nome não renomeia a lista que o cliente já reconhece', async () => {
  const env = createEnv();
  /* O caso do endereço que caiu: quem vende reenvia só o servidor. */
  await saveProvisioning(DEVICE, { ...CREDENTIAL, listLabel: 'Lista do Lucas' }, 'dono', env);
  await claimProvisioning(DEVICE, env);
  await confirmProvisioning(DEVICE, env);
  await saveProvisioning(DEVICE, { ...CREDENTIAL, server: 'http://novo.com:8080' }, 'dono', env);
  const claimed = await claimProvisioning(DEVICE, env);
  assert.equal(
    Object.prototype.hasOwnProperty.call(claimed, 'listLabel'), false,
    'ausente para o aplicativo saber que não deve mexer no nome',
  );
  env.DB.close();
});

test('um nome absurdo ou sujo é ignorado, e a lista vai assim mesmo', async () => {
  const env = createEnv();
  /* Um nome errado nunca deve impedir o cliente de assistir: sem ele o
     aplicativo cai para o endereço, que é o que fazia antes. */
  for (const bad of ['x'.repeat(61), 'com\u0000nulo', 'quebra\nde linha', '   ']) {
    await saveProvisioning(DEVICE, { ...CREDENTIAL, listLabel: bad }, 'dono', env);
    const claimed = await claimProvisioning(DEVICE, env);
    assert.equal(claimed.server, CREDENTIAL.server, `a lista deve ir mesmo assim: ${bad}`);
    assert.equal(claimed.listLabel, undefined, `o nome inválido não pode passar: ${bad}`);
  }
  env.DB.close();
});

/*
  Envio parcial.

  Quem vende precisava preencher endereço, usuário e senha para entregar qualquer
  coisa — mesmo só uma chave do TMDb a um cliente cuja lista já está funcionando.
  Relatado assim: "nao deixa eu enviar so api tmdb preciso enviar tudo".
*/

test('só a chave do TMDb basta para um envio', async () => {
  const env = createEnv();
  const saved = await saveProvisioning(DEVICE, { metadataKey: 'chave-tmdb-sintetica' }, 'dono@exemplo', env);

  assert.equal(saved.ok, true);
  const status = await provisioningStatus(DEVICE, env);
  assert.equal(status.metadataKey, true);
  assert.equal(status.server, null, 'sem credencial, não inventa endereço');
  env.DB.close();
});

test('só o nome da lista basta para um envio', async () => {
  const env = createEnv();
  const saved = await saveProvisioning(DEVICE, { listLabel: 'Plano Familia' }, 'dono@exemplo', env);

  assert.equal(saved.ok, true);
  assert.equal((await provisioningStatus(DEVICE, env)).listLabel, 'Plano Familia');
  env.DB.close();
});

test('uma chave enviada depois não apaga a lista que o aparelho já recebeu', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, { ...CREDENTIAL, listLabel: 'Plano Familia' }, 'dono@exemplo', env);
  await saveProvisioning(DEVICE, { metadataKey: 'chave-tmdb-sintetica' }, 'dono@exemplo', env);

  /*
    O painel mostra qual lista está no aparelho. Sobrescrever os rótulos com nada
    deixaria o suporte sem saber o que o cliente tem, logo depois de mandar uma
    chave para melhorar exatamente essa lista.
  */
  const status = await provisioningStatus(DEVICE, env);
  assert.equal(status.server, CREDENTIAL.server);
  assert.equal(status.username, CREDENTIAL.username);
  assert.equal(status.listLabel, 'Plano Familia');
  assert.equal(status.metadataKey, true);
  env.DB.close();
});

test('meia credencial é recusada', async () => {
  const env = createEnv();

  /* Endereço sem senha nunca conecta: aceitar isso entregaria ao cliente uma lista
     que não abre, e o erro só apareceria na televisão dele. */
  const missingPassword = await saveProvisioning(
    DEVICE, { server: CREDENTIAL.server, username: CREDENTIAL.username }, 'dono@exemplo', env,
  );
  assert.equal(missingPassword.error, 'bad_credentials');

  const missingServer = await saveProvisioning(
    DEVICE, { username: CREDENTIAL.username, password: CREDENTIAL.password }, 'dono@exemplo', env,
  );
  assert.equal(missingServer.error, 'bad_credentials');
  env.DB.close();
});

test('um formulário inteiramente vazio não marca o aparelho como pendente', async () => {
  const env = createEnv();
  const saved = await saveProvisioning(DEVICE, {}, 'dono@exemplo', env);

  assert.equal(saved.error, 'nothing_to_send');
  assert.equal(await provisioningStatus(DEVICE, env), null);
  env.DB.close();
});

test('a credencial continua a viajar inteira quando é enviada', async () => {
  const env = createEnv();
  await saveProvisioning(DEVICE, CREDENTIAL, 'dono@exemplo', env);

  /* O envio parcial não pode ter deixado a senha de fora do payload no caso comum. */
  const claimed = await claimProvisioning(DEVICE, env);
  assert.deepEqual(claimed, CREDENTIAL);
  env.DB.close();
});

/*
  A trilha de auditoria num envio sem conexão.

  A rota lia `saved.server.slice(...)` direto. Num envio só de chave esse valor é
  null, então o pedido inteiro estourava e quem vende via "Não foi possível enviar.
  Confira o endereço do servidor" — apontando para um campo deixado vazio de
  propósito. Os testes anteriores chamavam saveProvisioning direto e nunca
  passavam pela rota, que é onde o defeito estava.
*/

test('a trilha descreve um envio que não trouxe conexão', () => {
  const detail = provisioningAuditDetail({
    ok: true, server: null, username: null, listLabel: null,
    metadataKey: true, criticsKey: false,
  });

  assert.equal(detail, 'chave:tmdb');
});

test('a trilha continua descrevendo a conexão quando ela é enviada', () => {
  const detail = provisioningAuditDetail({
    ok: true, server: CREDENTIAL.server, username: CREDENTIAL.username,
    listLabel: 'Plano Familia', metadataKey: false, criticsKey: false,
  });

  assert.ok(detail.includes(CREDENTIAL.server));
  assert.ok(detail.includes(CREDENTIAL.username));
  assert.ok(detail.includes('Plano Familia'));
});

test('a senha nunca entra na trilha', () => {
  const detail = provisioningAuditDetail({
    ok: true, server: CREDENTIAL.server, username: CREDENTIAL.username,
    password: CREDENTIAL.password, metadataKey: true, criticsKey: true,
  });

  assert.ok(!detail.includes(CREDENTIAL.password));
});

test('a rota registra a trilha pelo auxiliar, nunca lendo o campo direto', () => {
  /* Ler saved.server direto é exatamente o defeito: volta a estourar num envio
     que não traz conexão, e o painel culpa o endereço vazio. */
  const source = readFileSync(new URL('../src/index.js', import.meta.url), 'utf8');

  assert.ok(source.includes('provisioningAuditDetail(saved)'), 'a rota usa o auxiliar');
  assert.ok(!source.includes('saved.server.slice'), 'e nunca lê o campo direto');
});
