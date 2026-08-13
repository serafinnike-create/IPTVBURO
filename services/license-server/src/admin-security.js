import { isAdmin } from './admin.js';

const SESSION_HOURS = 8;
const encoder = new TextEncoder();

export async function adminMfaStatus(env) {
  const row = await env.DB.prepare('SELECT enabled_at FROM admin_mfa WHERE id = 1').first();
  return {
    available: Boolean(env.ADMIN_MFA_ENCRYPTION_KEY),
    configured: Boolean(row),
    enabled: Boolean(row?.enabled_at),
  };
}

export async function beginMfaSetup(request, env) {
  if (!isAdmin(request, env)) return { ok: false, status: 401, error: 'unauthorized' };
  if (!env.ADMIN_MFA_ENCRYPTION_KEY) {
    return { ok: false, status: 503, error: 'mfa_encryption_key_missing' };
  }
  const current = await env.DB.prepare('SELECT enabled_at FROM admin_mfa WHERE id = 1').first();
  if (current?.enabled_at) return { ok: false, status: 409, error: 'mfa_already_enabled' };

  const secretBytes = crypto.getRandomValues(new Uint8Array(20));
  const secret = base32Encode(secretBytes);
  const encrypted = await encryptSecret(secret, env.ADMIN_MFA_ENCRYPTION_KEY);
  const now = new Date().toISOString();
  await env.DB.prepare(
    `INSERT INTO admin_mfa (id, secret_ciphertext, enabled_at, updated_at)
     VALUES (1, ?, NULL, ?)
     ON CONFLICT(id) DO UPDATE SET secret_ciphertext = excluded.secret_ciphertext,
       enabled_at = NULL, updated_at = excluded.updated_at`,
  ).bind(encrypted, now).run();
  const label = encodeURIComponent('IPTV BURO Admin');
  const issuer = encodeURIComponent('IPTV BURO');
  return {
    ok: true,
    secret,
    otpauth: `otpauth://totp/${label}?secret=${secret}&issuer=${issuer}&digits=6&period=30`,
  };
}

export async function confirmMfaSetup(request, code, env) {
  if (!isAdmin(request, env)) return { ok: false, status: 401, error: 'unauthorized' };
  const row = await env.DB.prepare('SELECT * FROM admin_mfa WHERE id = 1').first();
  if (!row || !env.ADMIN_MFA_ENCRYPTION_KEY) {
    return { ok: false, status: 409, error: 'mfa_setup_missing' };
  }
  const secret = await decryptSecret(row.secret_ciphertext, env.ADMIN_MFA_ENCRYPTION_KEY);
  if (!(await verifyTotp(secret, code))) return { ok: false, status: 401, error: 'bad_mfa_code' };
  const now = new Date().toISOString();
  await env.DB.prepare('UPDATE admin_mfa SET enabled_at = ?, updated_at = ? WHERE id = 1')
    .bind(now, now).run();
  return { ok: true, enabled: true };
}

export async function createAdminSession(request, body, env) {
  if (!isAdmin(request, env)) return { ok: false, status: 401, error: 'unauthorized' };
  const mfa = await env.DB.prepare('SELECT * FROM admin_mfa WHERE id = 1').first();
  if (mfa?.enabled_at) {
    if (!env.ADMIN_MFA_ENCRYPTION_KEY) {
      return { ok: false, status: 503, error: 'mfa_encryption_key_missing' };
    }
    const secret = await decryptSecret(mfa.secret_ciphertext, env.ADMIN_MFA_ENCRYPTION_KEY);
    if (!(await verifyTotp(secret, body?.code))) {
      return { ok: false, status: 401, error: 'mfa_required' };
    }
  }

  const actor = normaliseActor(body?.actor);
  const tokenBytes = crypto.getRandomValues(new Uint8Array(32));
  const token = base64Url(tokenBytes);
  const tokenHash = await sha256(token);
  const now = new Date();
  const expires = new Date(now.getTime() + SESSION_HOURS * 60 * 60 * 1000);
  await env.DB.prepare(
    `INSERT INTO admin_sessions (token_hash, actor, created_at, expires_at, last_used_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).bind(tokenHash, actor, now.toISOString(), expires.toISOString(), now.toISOString()).run();
  return { ok: true, token, actor, expiresAt: expires.toISOString(), mfa: Boolean(mfa?.enabled_at) };
}

export async function authenticateAdmin(request, env) {
  const session = request.headers.get('x-admin-session') ?? '';
  if (session) {
    const hash = await sha256(session);
    const now = new Date().toISOString();
    const row = await env.DB.prepare(
      'SELECT actor, expires_at FROM admin_sessions WHERE token_hash = ? AND expires_at > ?',
    ).bind(hash, now).first();
    if (row) {
      await env.DB.prepare('UPDATE admin_sessions SET last_used_at = ? WHERE token_hash = ?')
        .bind(now, hash).run();
      return { actor: row.actor, method: 'session' };
    }
  }

  // Compatibility while MFA is not enrolled. The instant MFA is enabled, raw bearer tokens stop
  // authorising data and mutation routes; they remain useful only for creating an MFA session.
  const mfa = await env.DB.prepare('SELECT enabled_at FROM admin_mfa WHERE id = 1').first();
  if (!mfa?.enabled_at && isAdmin(request, env)) {
    return { actor: normaliseActor(request.headers.get('x-admin-actor')), method: 'token' };
  }
  return null;
}

export async function cleanupAdminSessions(env) {
  await env.DB.prepare('DELETE FROM admin_sessions WHERE expires_at <= ?')
    .bind(new Date().toISOString()).run();
}

export async function verifyTotp(secret, code, nowMs = Date.now()) {
  const digits = String(code ?? '').replace(/\s+/g, '');
  if (!/^\d{6}$/.test(digits)) return false;
  for (const offset of [-1, 0, 1]) {
    const expected = await totp(secret, Math.floor(nowMs / 30000) + offset);
    if (timingSafeDigits(expected, digits)) return true;
  }
  return false;
}

async function totp(secret, counter) {
  const key = await crypto.subtle.importKey(
    'raw', base32Decode(secret), { name: 'HMAC', hash: 'SHA-1' }, false, ['sign'],
  );
  const message = new Uint8Array(8);
  let value = BigInt(counter);
  for (let index = 7; index >= 0; index -= 1) {
    message[index] = Number(value & 255n);
    value >>= 8n;
  }
  const digest = new Uint8Array(await crypto.subtle.sign('HMAC', key, message));
  const offset = digest[digest.length - 1] & 15;
  const binary = ((digest[offset] & 127) << 24)
    | (digest[offset + 1] << 16)
    | (digest[offset + 2] << 8)
    | digest[offset + 3];
  return String(binary % 1_000_000).padStart(6, '0');
}

async function encryptSecret(value, keyText) {
  const key = await importAesKey(keyText);
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv }, key, encoder.encode(value),
  ));
  return `${base64Url(iv)}.${base64Url(ciphertext)}`;
}

async function decryptSecret(envelope, keyText) {
  const [ivText, cipherText] = String(envelope ?? '').split('.');
  if (!ivText || !cipherText) throw new Error('invalid_mfa_secret');
  const key = await importAesKey(keyText);
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: base64UrlDecode(ivText) }, key, base64UrlDecode(cipherText),
  );
  return new TextDecoder().decode(plain);
}

async function importAesKey(value) {
  const bytes = base64UrlDecode(String(value ?? '').replaceAll('+', '-').replaceAll('/', '_'));
  if (bytes.length !== 32) throw new Error('invalid_mfa_encryption_key');
  return await crypto.subtle.importKey('raw', bytes, 'AES-GCM', false, ['encrypt', 'decrypt']);
}

function normaliseActor(value) {
  const clean = String(value ?? '').replace(/[\u0000-\u001f\u007f]/g, ' ').replace(/\s+/g, ' ').trim();
  return clean.slice(0, 80) || 'Administrador';
}

async function sha256(value) {
  return base64Url(new Uint8Array(await crypto.subtle.digest('SHA-256', encoder.encode(value))));
}

function timingSafeDigits(left, right) {
  if (left.length !== right.length) return false;
  let difference = 0;
  for (let i = 0; i < left.length; i += 1) difference |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return difference === 0;
}

function base32Encode(bytes) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let value = 0;
  let output = '';
  for (const byte of bytes) {
    value = (value << 8) | byte;
    bits += 8;
    while (bits >= 5) {
      output += alphabet[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) output += alphabet[(value << (5 - bits)) & 31];
  return output;
}

function base32Decode(value) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let buffer = 0;
  const output = [];
  for (const character of String(value).replace(/=+$/g, '').toUpperCase()) {
    const index = alphabet.indexOf(character);
    if (index < 0) throw new Error('invalid_base32');
    buffer = (buffer << 5) | index;
    bits += 5;
    if (bits >= 8) {
      output.push((buffer >>> (bits - 8)) & 255);
      bits -= 8;
    }
  }
  return new Uint8Array(output);
}

function base64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/, '');
}

function base64UrlDecode(value) {
  const padded = String(value).replaceAll('-', '+').replaceAll('_', '/').padEnd(
    Math.ceil(String(value).length / 4) * 4,
    '=',
  );
  return Uint8Array.from(atob(padded), (character) => character.charCodeAt(0));
}
