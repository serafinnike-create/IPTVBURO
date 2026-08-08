/**
 * The admin panel's markup.
 *
 * One page, in Portuguese, because there is one administrator. It does four things: show how many
 * devices exist, find one, give it time, and make keys.
 *
 * The token is kept in the browser's session storage rather than a cookie: session storage is
 * per-tab and gone when the tab closes, and a cookie would be sent on every request to this origin
 * including the customer-facing pages, which have no business seeing it.
 */

export function adminPage() {
  return `<!doctype html>
<html lang="pt">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>IPTV BURO — administração</title>
<style>
  :root {
    --canvas: #0b0b0d; --surface: #17171b; --raised: #1f1f24;
    --text: #f2f0ec; --muted: #a8a49c; --subtle: #9a968e;
    --gold: #d6a956; --ok: #6fbf73; --bad: #e08585;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 24px; background: var(--canvas); color: var(--text);
    font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif;
  }
  .wrap { max-width: 1000px; margin: 0 auto; }
  h1 { font-size: 20px; margin: 0 0 4px; }
  h1 span { color: var(--gold); }
  .sub { color: var(--subtle); font-size: 13px; margin: 0 0 24px; }
  .panel { background: var(--surface); border: 1px solid #262429; border-radius: 14px; padding: 20px; margin-bottom: 18px; }
  .panel h2 { font-size: 14px; margin: 0 0 14px; color: var(--muted); font-weight: 600; text-transform: uppercase; letter-spacing: .08em; }
  input, select, button {
    font: inherit; font-size: 16px; padding: 10px 12px; border-radius: 8px; border: 1px solid #67646f;
    background: var(--raised); color: var(--text);
  }
  button { background: var(--gold); color: #1a1509; font-weight: 700; border: 0; cursor: pointer; }
  button.ghost { background: var(--raised); color: var(--text); border: 1px solid #67646f; font-weight: 500; }
  button:hover { filter: brightness(1.08); }
  button:focus-visible, input:focus-visible, select:focus-visible {
    outline: 3px solid var(--gold); outline-offset: 3px;
  }
  .row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
  .row.between { justify-content: space-between; }
  .field { display: grid; gap: 5px; min-width: 160px; }
  .field > span { color: var(--muted); font-size: 13px; }
  .stats { display: flex; gap: 28px; }
  .stat b { display: block; font-size: 28px; color: var(--gold); }
  .stat span { font-size: 12px; color: var(--subtle); }
  table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
  .table-wrap { max-width: 100%; overflow-x: auto; border: 1px solid #45424a; border-radius: 10px; }
  th { text-align: left; color: var(--subtle); font-weight: 500; padding: 8px 10px; border-bottom: 1px solid #262429; }
  td { padding: 10px; border-bottom: 1px solid #1e1c22; vertical-align: middle; }
  code { font-family: ui-monospace, Consolas, monospace; user-select: all; }
  .tag { font-size: 11px; padding: 3px 8px; border-radius: 20px; background: var(--raised); }
  .tag.active { background: #1d3320; color: var(--ok); }
  .tag.trial { background: #33301d; color: var(--gold); }
  .tag.dead { background: #331d1d; color: #e08585; }
  .keys { font-family: ui-monospace, Consolas, monospace; line-height: 2; user-select: all; }
  #login { max-width: 380px; margin: 15vh auto; }
  .hidden { display: none; }
  [aria-busy="true"] { opacity: .72; }
  @media (max-width: 640px) {
    body { padding: 12px; }
    .panel { padding: 16px; }
    .stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
    .row > .field { width: 100%; flex: 1 1 100% !important; }
    .row > button { min-height: 44px; }
  }
</style>
</head>
<body>
<div class="wrap">

  <form id="login" class="panel" onsubmit="event.preventDefault(); signIn()">
    <h1>IPTV <span>BURO</span></h1>
    <p class="sub">Administração</p>
    <div class="row">
      <label class="field" style="flex:1">
        <span>Token de acesso</span>
        <input id="token" type="password" autocomplete="current-password" aria-describedby="loginError" autofocus>
      </label>
      <button type="submit">Entrar</button>
    </div>
    <p class="sub" id="loginError" role="alert" aria-live="polite" style="color:#e08585"></p>
  </form>

  <div id="panel" class="hidden">
    <div class="row between">
      <div><h1>IPTV <span>BURO</span></h1><p class="sub">Administração</p></div>
      <button class="ghost" type="button" onclick="signOut()">Sair</button>
    </div>

    <div class="panel">
      <h2>Resumo</h2>
      <div class="stats" id="stats" role="status" aria-live="polite"></div>
    </div>

    <div class="panel">
      <h2>Procurar dispositivo</h2>
      <form class="row" onsubmit="event.preventDefault(); search()">
        <label class="field" style="flex:1">
          <span>Código, MAC ou nota</span>
          <input id="query" autocomplete="off">
        </label>
        <button type="submit">Procurar</button>
      </form>
      <div id="results" role="status" aria-live="polite" style="margin-top:14px"></div>
    </div>

    <div class="panel">
      <h2>Libertar dispositivo à mão</h2>
      <p class="sub">Para quem pagou por fora, ou para dar mais tempo a alguém.</p>
      <div class="row">
        <label class="field"><span>Dispositivo</span><input id="grantDevice" placeholder="XXXX-XXXX-XXXX" style="width:190px"></label>
        <label class="field"><span>Duração</span><select id="grantDays">
          <option value="7">7 dias</option>
          <option value="30" selected>30 dias</option>
          <option value="90">90 dias</option>
          <option value="365">1 ano</option>
          <option value="730">2 anos</option>
        </select></label>
        <label class="field" style="flex:1"><span>Motivo (fica registrado)</span><input id="grantNote"></label>
        <button type="button" onclick="grant()">Liberar</button>
      </div>
      <p class="sub" id="grantResult" role="status" aria-live="polite"></p>
    </div>

    <div class="panel">
      <h2>Códigos de ativação</h2>
      <p class="sub">Para dar a alguém sem pedir o dispositivo primeiro. Cada código serve uma vez.</p>
      <div class="row">
        <label class="field"><span>Duração</span><select id="keyDays">
          <option value="30" selected>30 dias</option>
          <option value="90">90 dias</option>
          <option value="365">1 ano</option>
          <option value="730">2 anos</option>
        </select></label>
        <label class="field"><span>Quantidade</span><input id="keyCount" type="number" value="1" min="1" max="50" style="width:90px"></label>
        <label class="field" style="flex:1"><span>Para quem / por quê</span><input id="keyNote"></label>
        <button type="button" onclick="makeKeys()">Gerar</button>
        <button type="button" class="ghost" onclick="loadKeys()">Ver códigos</button>
      </div>
      <div class="keys" id="keyResult" role="status" aria-live="polite" style="margin-top:12px"></div>
    </div>
  </div>
</div>

<script>
  // Session storage rather than a cookie: per-tab, gone when the tab closes, and never sent
  // automatically to the customer-facing pages on this same origin.
  let token = sessionStorage.getItem('buro-admin') || '';

  if (token) { verify(); }

  function signIn() {
    token = document.getElementById('token').value.trim();
    verify();
  }

  function signOut() {
    token = '';
    sessionStorage.removeItem('buro-admin');
    document.getElementById('panel').classList.add('hidden');
    document.getElementById('login').classList.remove('hidden');
    document.getElementById('token').value = '';
    document.getElementById('token').focus();
  }

  async function verify() {
    const response = await api('/admin/summary');
    if (!response) {
      // Whichever of the three it was. "Token inválido" for all of them sent us hunting for a wrong
      // password when the server was answering something else entirely, and the difference between
      // "wrong token" and "the server is broken" is the difference between a retry and a deploy.
      const reason = lastStatus === 401 ? 'Token inválido.'
        : lastStatus === 0 ? 'Sem resposta do servidor.'
        : 'O servidor respondeu ' + lastStatus + '.';
      document.getElementById('loginError').textContent = reason;
      sessionStorage.removeItem('buro-admin');
      return;
    }
    sessionStorage.setItem('buro-admin', token);
    document.getElementById('login').classList.add('hidden');
    document.getElementById('panel').classList.remove('hidden');
    document.getElementById('stats').innerHTML =
      stat(response.active, 'ativos') + stat(response.trial, 'em teste') + stat(response.paid, 'pagaram');
  }

  function stat(value, label) {
    return '<div class="stat"><b>' + value + '</b><span>' + label + '</span></div>';
  }

  // What the last call answered, so a failure can say which kind it was. Zero means the request
  // never arrived anywhere.
  let lastStatus = 0;

  async function api(path, body) {
    lastStatus = 0;
    try {
      const response = await fetch(path, {
        method: body ? 'POST' : 'GET',
        headers: { authorization: 'Bearer ' + token, 'content-type': 'application/json' },
        body: body ? JSON.stringify(body) : undefined,
      });
      lastStatus = response.status;
      if (!response.ok) return null;
      return await response.json();
    } catch { return null; }
  }

  async function search() {
    const query = document.getElementById('query').value.trim();
    if (!query) return;
    const target = document.getElementById('results');
    target.setAttribute('aria-busy', 'true');
    const data = await api('/admin/search?q=' + encodeURIComponent(query));
    target.removeAttribute('aria-busy');
    if (!data || !data.devices.length) { target.innerHTML = '<p class="sub">Nada encontrado.</p>'; return; }

    target.innerHTML = '<div class="table-wrap"><table><thead><tr><th>Dispositivo</th><th>MAC</th><th>Estado</th><th>Até</th><th>Nota</th><th>Ações</th></tr></thead><tbody>'
      + data.devices.map(function (device) {
          return '<tr>'
            + '<td><code>' + esc(device.device_id) + '</code></td>'
            + '<td class="sub">' + esc(device.mac_address || '—') + '</td>'
            + '<td>' + tag(device.status) + '</td>'
            + '<td class="sub">' + date(device.expires_at || device.trial_ends_at) + '</td>'
            + '<td class="sub">' + esc(device.note || '') + '</td>'
            + '<td><button class="ghost" onclick="revoke(\\'' + esc(device.device_id) + '\\')">Revogar</button></td>'
            + '</tr>';
        }).join('') + '</tbody></table></div>';
  }

  async function grant() {
    const device = document.getElementById('grantDevice').value.trim().toUpperCase();
    const days = Number(document.getElementById('grantDays').value);
    const note = document.getElementById('grantNote').value.trim();
    if (!device) return;
    const result = await api('/admin/grant', { device: device, days: days, note: note });
    document.getElementById('grantResult').textContent =
      result ? 'Liberado por ' + days + ' dias.' : 'Falhou — confira o código do dispositivo.';
  }

  async function revoke(device) {
    if (!confirm('Revogar ' + device + '?')) return;
    await api('/admin/revoke', { device: device, note: 'revogado no painel' });
    search();
  }

  async function makeKeys() {
    const result = await api('/admin/keys', {
      days: Number(document.getElementById('keyDays').value),
      count: Number(document.getElementById('keyCount').value),
      note: document.getElementById('keyNote').value.trim(),
    });
    document.getElementById('keyResult').innerHTML =
      result ? result.keys.map(esc).join('<br>') : 'Falhou.';
  }

  async function loadKeys() {
    const result = await api('/admin/keys');
    if (!result) return;
    document.getElementById('keyResult').innerHTML = result.keys.map(function (key) {
      return esc(key.key_code) + '  ·  ' + key.grant_days + 'd'
        + (key.redeemed_by ? '  ·  usado por ' + esc(key.redeemed_by) : '  ·  livre')
        + (key.note ? '  ·  ' + esc(key.note) : '');
    }).join('<br>');
  }

  function tag(status) {
    const cls = status === 'ACTIVE' ? 'active' : status === 'TRIAL' ? 'trial' : 'dead';
    return '<span class="tag ' + cls + '">' + esc(status) + '</span>';
  }

  function date(value) {
    if (!value) return '—';
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return esc(value);
    return '<time datetime="' + esc(value) + '">'
      + new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' }).format(parsed)
      + '</time>';
  }

  // Everything from the database goes through here before it reaches the page. A note is free text
  // typed by hand, and a device id could be anything if validation upstream were ever wrong.
  function esc(value) {
    return String(value == null ? '' : value)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
  }
</script>
</body>
</html>`;
}
