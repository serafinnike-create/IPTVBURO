/**
 * Configurar a lista de um cliente de longe, pelo painel.
 *
 * Quem vende IPTV vende para gente que muitas vezes não sabe cadastrar um
 * servidor Xtream: três campos, um deles uma senha, digitados no controle remoto
 * de uma televisão. Não dá para ir à casa da pessoa, e ditar uma senha por
 * telefone termina em erro de digitação.
 *
 * O gesto é simples: o cliente lê o código do aparelho na tela e manda por
 * mensagem; o painel busca esse código e aplica endereço, usuário e senha; o
 * cliente fecha e abre o aplicativo, e a lista está lá.
 *
 * O código é o consentimento. Só chega aqui o aparelho cuja pessoa leu o código
 * na própria tela e o enviou — quem usa o aplicativo com outra operadora nunca
 * é alcançado, sem precisar de uma tela de vínculo para garantir isso.
 *
 * O que este módulo deliberadamente não faz: guardar credencial para consulta. A
 * senha vive no KeyManager da televisão, que é de onde ela nunca saiu. Aqui ela
 * fica de passagem e é apagada quando a televisão confirma. O painel escreve e
 * nunca lê — trocar um endereço que caiu não exige recuperar o antigo — então um
 * vazamento deste banco continua não entregando a lista de ninguém.
 */

const MAX_PAYLOAD_BYTES = 2048;
const MAX_ATTEMPTS = 10;

/**
 * A chave de cifra, derivada do identificador do aparelho.
 *
 * O mesmo desenho do pareamento, com um sal próprio para que um registro de um
 * não possa ser lido como do outro. Não é segredo forte — o identificador
 * aparece na tela da televisão — e não pretende ser: protege o registro em
 * repouso contra quem lê a base sem saber de qual aparelho é cada linha, não
 * contra quem já tem o aparelho na frente.
 */
async function deriveKey(deviceId) {
  const material = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(deviceId),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return await crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: new TextEncoder().encode('iptvburo-provisioning-v1'),
      iterations: 100000,
      hash: 'SHA-256',
    },
    material,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

function encodeBase64(bytes) {
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary);
}

function decodeBase64(value) {
  try {
    const binary = atob(String(value ?? ''));
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return bytes;
  } catch {
    return null;
  }
}

async function encryptPayload(deviceId, plaintext) {
  const key = await deriveKey(deviceId);
  const nonce = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(
    await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv: nonce },
      key,
      new TextEncoder().encode(plaintext),
    ),
  );
  return { payload: encodeBase64(ciphertext), nonce: encodeBase64(nonce) };
}

async function decryptPayload(deviceId, payloadBase64, nonceBase64) {
  const ciphertext = decodeBase64(payloadBase64);
  const nonce = decodeBase64(nonceBase64);
  if (!ciphertext || !nonce) return null;
  try {
    const key = await deriveKey(deviceId);
    const plaintext = await crypto.subtle.decrypt({ name: 'AES-GCM', iv: nonce }, key, ciphertext);
    return new TextDecoder().decode(plaintext);
  } catch {
    // Uma etiqueta que não confere significa registro adulterado ou de outro
    // aparelho. Os dois casos são "não".
    return null;
  }
}

function nowIso() {
  return new Date().toISOString();
}

/**
 * O endereço do provedor, conferido antes de ser guardado.
 *
 * Recusar aqui é melhor do que a televisão recusar depois: quem digitou no
 * painel está olhando para o formulário e pode corrigir, enquanto o cliente do
 * outro lado só veria a lista não aparecer.
 */
function validServer(value) {
  const text = String(value ?? '').trim();
  if (!text || text.length > 400) return null;
  if (!/^https?:\/\//i.test(text)) return null;
  // Credencial embutida no endereço seria uma segunda cópia da senha num campo
  // que o painel mostra em claro.
  if (/^https?:\/\/[^/\s]*@/i.test(text)) return null;
  return text;
}

function validUsername(value) {
  const text = String(value ?? '').trim();
  return text && text.length <= 120 ? text : null;
}

function validPassword(value) {
  const text = String(value ?? '');
  return text && text.length <= 200 ? text : null;
}

/**
 * Uma chave de API, opcional.
 *
 * Vai junto porque resolve o mesmo problema pela mesma pessoa: quem não consegue
 * cadastrar um servidor Xtream também não vai criar conta no TMDb e colar uma
 * chave. Quem vende pode criar a conta e entregar o aplicativo já mostrando capa,
 * elenco e sinopse.
 *
 * Devolve `null` tanto para "não enviou" quanto para "enviou algo inválido", e o
 * chamador trata os dois do mesmo jeito: a lista é aplicada e a chave não. Uma
 * chave errada nunca deve impedir o cliente de assistir.
 *
 * O limite cobre a v4 do TMDb, que é um token de 239 caracteres — bem maior do
 * que a chave v3 de 32 que a maioria copia.
 */
function validApiKey(value) {
  const text = String(value ?? '').trim();
  if (!text || text.length > 400) return null;
  // Só o alfabeto que essas chaves usam. Um valor com espaço ou aspas dentro não
  // é uma chave: é engano de recorte, e viraria uma URL malformada no aplicativo.
  return /^[A-Za-z0-9._-]+$/.test(text) ? text : null;
}

/**
 * Guarda o que a televisão vai aplicar.
 *
 * Um aparelho tem no máximo um provisionamento pendente: o painel sobrescreve o
 * anterior. É o comportamento certo para o caso que motivou tudo — o endereço do
 * provedor caiu e é preciso mandar outro — e evita uma fila de configurações
 * antigas esperando para serem aplicadas fora de ordem.
 */
export async function saveProvisioning(deviceId, body, actor, env) {
  const server = validServer(body?.server);
  const username = validUsername(body?.username);
  const password = validPassword(body?.password);
  if (!server || !username || !password) return { error: 'bad_credentials' };

  // Opcionais, e omitidas quando ausentes em vez de irem como null: o aplicativo
  // trata "campo ausente" como "não mexer na chave que já está lá", de modo que
  // reenviar só o endereço não apaga a chave que o cliente configurou sozinho.
  const metadataKey = validApiKey(body?.metadataKey);
  const criticsKey = validApiKey(body?.criticsKey);

  const plaintext = JSON.stringify({
    server,
    username,
    password,
    ...(metadataKey ? { metadataKey } : {}),
    ...(criticsKey ? { criticsKey } : {}),
  });
  if (new TextEncoder().encode(plaintext).length > MAX_PAYLOAD_BYTES) {
    return { error: 'payload_too_large' };
  }

  const sealed = await encryptPayload(deviceId, plaintext);
  await env.DB.prepare(
    `INSERT INTO device_provisioning
       (device_id, payload, payload_nonce, server_label, username_label, state,
        created_at, created_by, applied_at, attempts, last_error,
        has_metadata_key, has_critics_key)
     VALUES (?1, ?2, ?3, ?4, ?5, 'PENDING', ?6, ?7, NULL, 0, NULL, ?8, ?9)
     ON CONFLICT(device_id) DO UPDATE SET
       payload = excluded.payload,
       payload_nonce = excluded.payload_nonce,
       server_label = excluded.server_label,
       username_label = excluded.username_label,
       state = 'PENDING',
       created_at = excluded.created_at,
       created_by = excluded.created_by,
       applied_at = NULL,
       attempts = 0,
       last_error = NULL,
       has_metadata_key = excluded.has_metadata_key,
       has_critics_key = excluded.has_critics_key`,
  ).bind(
    deviceId, sealed.payload, sealed.nonce, server, username, nowIso(), actor,
    metadataKey ? 1 : 0, criticsKey ? 1 : 0,
  ).run();

  return { ok: true, server, username, metadataKey: !!metadataKey, criticsKey: !!criticsKey };
}

/**
 * O que o painel mostra sobre um aparelho.
 *
 * Endereço e usuário em claro, senha só como "definida". Não é uma limitação
 * técnica contornável: a senha existe apenas dentro do payload cifrado, e para
 * trocar um endereço que caiu não é preciso ler o que estava lá antes.
 */
export async function provisioningStatus(deviceId, env) {
  const row = await env.DB.prepare(
    `SELECT server_label, username_label, state, created_at, created_by, applied_at,
            attempts, last_error, has_metadata_key, has_critics_key
       FROM device_provisioning WHERE device_id = ?1`,
  ).bind(deviceId).first();
  if (!row) return null;
  return {
    server: row.server_label,
    username: row.username_label,
    hasPassword: true,
    // Só se foram enviadas, nunca o valor: o painel escreve e não lê, igual à senha.
    metadataKey: !!row.has_metadata_key,
    criticsKey: !!row.has_critics_key,
    state: row.state,
    createdAt: row.created_at,
    createdBy: row.created_by,
    appliedAt: row.applied_at,
    attempts: Number(row.attempts) || 0,
    lastError: row.last_error,
  };
}

/**
 * Retira o provisionamento pendente, para a televisão que está abrindo.
 *
 * Devolve o texto em claro uma vez. O registro só é marcado como aplicado quando
 * a televisão confirma — uma entrega que se perde no caminho precisa poder ser
 * tentada de novo na abertura seguinte, senão o cliente ficaria sem lista e sem
 * saber por quê.
 */
export async function claimProvisioning(deviceId, env) {
  const row = await env.DB.prepare(
    `SELECT payload, payload_nonce, attempts FROM device_provisioning
       WHERE device_id = ?1 AND state = 'PENDING'`,
  ).bind(deviceId).first();
  if (!row) return null;

  const attempts = (Number(row.attempts) || 0) + 1;
  if (attempts > MAX_ATTEMPTS) {
    // Dez aberturas sem conseguir aplicar não é rede instável, é configuração
    // errada. Parar aqui evita um laço silencioso e faz o painel mostrar o
    // problema a quem pode corrigi-lo.
    await env.DB.prepare(
      `UPDATE device_provisioning SET state = 'FAILED', last_error = 'too_many_attempts'
         WHERE device_id = ?1`,
    ).bind(deviceId).run();
    return null;
  }
  await env.DB.prepare(
    'UPDATE device_provisioning SET attempts = ?2 WHERE device_id = ?1',
  ).bind(deviceId, attempts).run();

  const plaintext = await decryptPayload(deviceId, row.payload, row.payload_nonce);
  if (!plaintext) return null;
  try {
    return JSON.parse(plaintext);
  } catch {
    return null;
  }
}

/**
 * A televisão confirmou que aplicou.
 *
 * O payload é esvaziado aqui, e não guardado "para o caso de": a partir deste
 * momento ele só seria risco. Os rótulos ficam, para o painel poder dizer qual
 * lista está naquele aparelho sem guardar a senha.
 */
export async function confirmProvisioning(deviceId, env) {
  const result = await env.DB.prepare(
    `UPDATE device_provisioning
        SET state = 'APPLIED', applied_at = ?2, payload = '', payload_nonce = ''
      WHERE device_id = ?1 AND state = 'PENDING'`,
  ).bind(deviceId, nowIso()).run();
  return Boolean(result?.meta?.changes);
}

/** Uma falha relatada pela televisão, para o painel saber o que dizer ao cliente. */
export async function reportProvisioningError(deviceId, code, env) {
  const reason = String(code ?? '').slice(0, 60) || 'unknown';
  await env.DB.prepare(
    'UPDATE device_provisioning SET last_error = ?2 WHERE device_id = ?1',
  ).bind(deviceId, reason).run();
}

/** Cancela o que ainda não foi aplicado. */
export async function clearProvisioning(deviceId, env) {
  const result = await env.DB.prepare(
    'DELETE FROM device_provisioning WHERE device_id = ?1',
  ).bind(deviceId).run();
  return Boolean(result?.meta?.changes);
}

export const PROVISIONING_INTERNALS = Object.freeze({
  validServer,
  validUsername,
  validPassword,
  encryptPayload,
  decryptPayload,
  MAX_ATTEMPTS,
});
