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
  .wrap { max-width: 1180px; margin: 0 auto; }
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
  .stats { display: grid; grid-template-columns: repeat(6, minmax(110px, 1fr)); gap: 8px; }
  /* Buttons, not captions: each figure opens the list behind it. Styled flat so the panel still
     reads as a summary rather than as a row of controls. */
  .stat {
    background: transparent; border: 1px solid transparent; border-radius: 10px;
    padding: 8px 16px 8px 12px; cursor: pointer; text-align: left; font: inherit;
  }
  .stat:hover { background: var(--raised); border-color: #45424a; }
  .stat b { display: block; font-size: 28px; color: var(--gold); font-weight: 700; }
  .stat span { font-size: 12px; color: var(--subtle); }
  .toolbar { display: grid; grid-template-columns: minmax(240px, 1fr) 190px auto; gap: 10px; align-items: end; }
  .device-list { display: grid; gap: 12px; }
  .device-card { background: var(--raised); border: 1px solid #35323a; border-radius: 12px; padding: 15px; }
  .device-card.archived { opacity: .78; border-style: dashed; }
  .device-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
  .device-title { display: grid; gap: 3px; }
  .device-title strong { font-size: 17px; }
  .device-title .model { color: var(--muted); }
  .facts { display: grid; grid-template-columns: repeat(4, minmax(130px, 1fr)); gap: 10px; margin: 14px 0; }
  .fact { min-width: 0; }
  .fact small { display: block; color: var(--subtle); font-size: 11px; text-transform: uppercase; letter-spacing: .05em; }
  .fact span { display: block; overflow-wrap: anywhere; }
  .actions { display: flex; gap: 8px; flex-wrap: wrap; }
  button.danger { background: #4a2327; color: #ffd9d9; border: 1px solid #7a343b; }
  .details { margin-top: 12px; border-top: 1px solid #3a3740; padding-top: 12px; }
  .timeline { display: grid; gap: 7px; margin: 8px 0 0; padding: 0; list-style: none; }
  .timeline li { display: grid; grid-template-columns: 150px 120px 1fr; gap: 10px; color: var(--muted); }
  .result-head { display: flex; justify-content: space-between; align-items: center; margin: 14px 0 10px; }
  .result-head .sub { margin: 0; }
  /* Time running out. Amber for days, red for gone — the two states worth noticing in a list. */
  .soon { color: var(--gold); font-weight: 600; }
  .over { color: var(--bad); }
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
    .stats { grid-template-columns: repeat(2, 1fr); }
    .toolbar { grid-template-columns: 1fr; }
    .facts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    .device-head { flex-direction: column; }
    .timeline li { grid-template-columns: 1fr; gap: 1px; border-bottom: 1px solid #302e34; padding-bottom: 7px; }
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
      <h2>Dispositivos</h2>
      <p class="sub">País aproximado pela rede (pode mudar com VPN ou rede móvel). IP e localização exata não são armazenados.</p>
      <form class="toolbar" onsubmit="event.preventDefault(); search()">
        <label class="field">
          <span>Código, modelo, fabricante, plataforma, país ou nota</span>
          <input id="query" autocomplete="off" placeholder="Ex.: Samsung, Windows, BR, código...">
        </label>
        <label class="field">
          <span>Mostrar</span>
          <select id="deviceFilter" onchange="listByStatus(this.value)">
            <option value="ALL">Todos</option>
            <option value="ACTIVE">Ativos</option>
            <option value="TRIAL">Em teste</option>
            <option value="paid">Pagaram</option>
            <option value="REVOKED">Bloqueados</option>
            <option value="EXPIRED">Expirados</option>
            <option value="ARCHIVED">Apagados da lista</option>
          </select>
        </label>
        <button type="submit">Procurar</button>
      </form>
      <div class="result-head"><p class="sub" id="resultCount">A carregar…</p><button type="button" class="ghost" onclick="refreshDevices()">Atualizar</button></div>
      <div id="results" role="status" aria-live="polite"></div>
    </div>

    <div class="panel">
      <h2>Liberar dispositivo</h2>
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
  // Declared before the automatic verification: api() writes it synchronously before verify()
  // reaches its first await, so declaring it later makes a returning signed-in tab hit the
  // temporal dead zone and show the login screen after every refresh.
  let lastStatus = 0;

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
      stat(response.active, 'ativos', 'ACTIVE')
      + stat(response.trial, 'em teste', 'TRIAL')
      + stat(response.paid, 'pagaram', 'paid')
      + stat(response.revoked, 'bloqueados', 'REVOKED')
      + stat(response.expired, 'expirados', 'EXPIRED')
      + stat(response.archived, 'apagados', 'ARCHIVED');
    await listByStatus('ALL');
  }

  /**
   * A summary figure, as a button.
   *
   * "3 em teste" with nothing to click on is a dead end: the panel could only find a device by a
   * code the administrator had no way of knowing. Each figure now opens the list behind it.
   */
  function stat(value, label, status) {
    return '<button type="button" class="stat" onclick="listByStatus(\\'' + status + '\\')">'
      + '<b>' + value + '</b><span>' + label + '</span></button>';
  }

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

  let currentDevicePath = '/admin/list?status=ALL';

  async function search() {
    const query = document.getElementById('query').value.trim();
    if (!query) return listByStatus(document.getElementById('deviceFilter').value);
    await showDevices('/admin/search?q=' + encodeURIComponent(query));
  }

  /** The summary figures link here, so a count is something you can open. */
  async function listByStatus(status) {
    document.getElementById('query').value = '';
    document.getElementById('deviceFilter').value = status;
    await showDevices('/admin/list?status=' + encodeURIComponent(status));
  }

  async function refreshDevices() {
    await showDevices(currentDevicePath);
    const summary = await api('/admin/summary');
    if (summary) {
      document.getElementById('stats').innerHTML =
        stat(summary.active, 'ativos', 'ACTIVE') + stat(summary.trial, 'em teste', 'TRIAL')
        + stat(summary.paid, 'pagaram', 'paid') + stat(summary.revoked, 'bloqueados', 'REVOKED')
        + stat(summary.expired, 'expirados', 'EXPIRED') + stat(summary.archived, 'apagados', 'ARCHIVED');
    }
  }

  async function showDevices(path) {
    currentDevicePath = path;
    const target = document.getElementById('results');
    const count = document.getElementById('resultCount');
    target.setAttribute('aria-busy', 'true');
    const data = await api(path);
    target.removeAttribute('aria-busy');
    if (!data) {
      count.textContent = 'Falha ao carregar.';
      target.innerHTML = '<p class="sub">Tente atualizar.</p>';
      return;
    }
    count.textContent = data.devices.length + (data.devices.length === 1 ? ' dispositivo' : ' dispositivos');
    if (!data.devices.length) { target.innerHTML = '<p class="sub">Nada encontrado.</p>'; return; }
    target.innerHTML = '<div class="device-list">' + data.devices.map(deviceCard).join('') + '</div>';
  }

  function deviceCard(device) {
    const id = esc(device.device_id);
    const archived = Boolean(device.archived_at);
    const hardware = [device.manufacturer, device.model].filter(Boolean).join(' ') || 'Modelo ainda não informado';
    const actionButtons = archived
      ? '<button class="ghost" onclick="restoreDevice(\\'' + id + '\\')">Restaurar na lista</button>'
      : '<button class="ghost" onclick="showDetails(\\'' + id + '\\')">Detalhes e histórico</button>'
        + ' <button onclick="fillGrant(\\'' + id + '\\')">Liberar</button>'
        + (device.status === 'REVOKED' ? '' : ' <button class="danger" onclick="revoke(\\'' + id + '\\')">Bloquear</button>')
        + ' <button class="ghost" onclick="archiveDevice(\\'' + id + '\\')">Apagar da lista</button>';

    return '<article class="device-card' + (archived ? ' archived' : '') + '">'
      + '<div class="device-head"><div class="device-title"><strong>' + esc(hardware) + '</strong>'
      + '<code>' + id + '</code></div><div>' + tag(device.status)
      + (archived ? ' <span class="tag dead">ARQUIVADO</span>' : '') + '</div></div>'
      + '<div class="facts">'
      + fact('Tipo', deviceType(device.device_type))
      + fact('Sistema', esc(device.os_version || platformLabel(device.platform)))
      + fact('Versão do app', esc(device.app_version || 'Ainda não informado'))
      + fact('Último contato', dateTime(device.last_seen_at || device.updated_at))
      + fact('País da ativação', countryLabel(device.activation_country))
      + fact('Último país de uso', countryLabel(device.last_country))
      + fact('Origem', sourceLabel(device.source))
      + fact('Tempo restante', remaining(device))
      + fact('Válido até', date(device.expires_at || device.trial_ends_at))
      + fact('Primeiro acesso', date(device.first_seen_at))
      + '</div>'
      + '<p class="sub" style="margin:0 0 12px">Nota: ' + esc(device.note || '—') + '</p>'
      + '<div class="actions">' + actionButtons + '</div>'
      + '<div class="details hidden" id="details-' + id.replace(/-/g, '') + '"></div>'
      + '</article>';
  }

  function fact(label, value) {
    return '<div class="fact"><small>' + esc(label) + '</small><span>' + (value || '—') + '</span></div>';
  }

  /* Kept only as a simple table renderer for old snapshots; the live panel uses the cards above. */
  async function showDevicesLegacy(path) {
    const target = document.getElementById('results');
    target.setAttribute('aria-busy', 'true');
    const data = await api(path);
    target.removeAttribute('aria-busy');
    if (!data || !data.devices.length) { target.innerHTML = '<p class="sub">Nada encontrado.</p>'; return; }

    target.innerHTML = '<div class="table-wrap"><table><thead><tr>'
      + '<th>Dispositivo</th><th>Estado</th><th>Falta</th><th>Até</th><th>Desde</th><th>Nota</th><th>Ações</th>'
      + '</tr></thead><tbody>'
      + data.devices.map(function (device) {
          return '<tr>'
            + '<td><code>' + esc(device.device_id) + '</code></td>'
            + '<td>' + tag(device.status) + '</td>'
            + '<td>' + remaining(device) + '</td>'
            + '<td class="sub">' + date(device.expires_at || device.trial_ends_at) + '</td>'
            + '<td class="sub">' + date(device.first_seen_at) + '</td>'
            + '<td class="sub">' + esc(device.note || '—') + '</td>'
            + '<td><button class="ghost" onclick="revoke(\\'' + esc(device.device_id) + '\\')">Revogar</button>'
            + ' <button class="ghost" onclick="fillGrant(\\'' + esc(device.device_id) + '\\')">Liberar</button></td>'
            + '</tr>';
        }).join('') + '</tbody></table></div>';
  }

  /** Copies a device into the grant form, so releasing one found by search needs no retyping. */
  function fillGrant(device) {
    const field = document.getElementById('grantDevice');
    field.value = device;
    field.focus();
    field.scrollIntoView({ block: 'center' });
  }

  async function grant() {
    const device = document.getElementById('grantDevice').value.trim().toUpperCase();
    const days = Number(document.getElementById('grantDays').value);
    const note = document.getElementById('grantNote').value.trim();
    if (!device) return;
    const result = await api('/admin/grant', { device: device, days: days, note: note });
    document.getElementById('grantResult').textContent =
      result ? 'Liberado por ' + days + ' dias.' : 'Falhou — confira o código do dispositivo.';
    if (result) await refreshDevices();
  }

  async function revoke(device) {
    if (!confirm('Bloquear ' + device + '?\\n\\nO aplicativo perderá o acesso na próxima validação.')) return;
    const result = await api('/admin/revoke', { device: device, note: 'bloqueado no painel' });
    if (!result) alert('Não foi possível bloquear o dispositivo.');
    await refreshDevices();
  }

  async function archiveDevice(device) {
    if (!confirm('Apagar ' + device + ' da lista?\\n\\nEle será bloqueado e ocultado, mas o histórico será preservado para impedir novo teste gratuito.')) return;
    const result = await api('/admin/archive', { device: device, note: 'apagado da lista pelo administrador' });
    if (!result) alert('Não foi possível apagar da lista.');
    await refreshDevices();
  }

  async function restoreDevice(device) {
    const result = await api('/admin/restore', { device: device });
    if (!result) alert('Não foi possível restaurar o dispositivo.');
    await refreshDevices();
  }

  async function showDetails(device) {
    const target = document.getElementById('details-' + device.replace(/-/g, ''));
    if (!target.classList.contains('hidden')) { target.classList.add('hidden'); return; }
    target.classList.remove('hidden');
    target.textContent = 'A carregar histórico…';
    const result = await api('/admin/device?device=' + encodeURIComponent(device));
    if (!result) { target.textContent = 'Não foi possível carregar o histórico.'; return; }
    if (!result.events.length) { target.innerHTML = '<p class="sub">Sem eventos registrados.</p>'; return; }
    target.innerHTML = '<strong>Histórico</strong><ul class="timeline">'
      + result.events.map(function (entry) {
          return '<li><span>' + dateTime(entry.created_at) + '</span><b>' + esc(eventLabel(entry.kind))
            + '</b><span>' + esc(entry.detail || '—') + '</span></li>';
        }).join('') + '</ul>';
  }

  async function makeKeys() {
    const days = Number(document.getElementById('keyDays').value);
    const note = document.getElementById('keyNote').value.trim();
    const result = await api('/admin/keys', {
      days: days,
      count: Number(document.getElementById('keyCount').value),
      note: note,
    });
    if (!result) { document.getElementById('keyResult').textContent = 'Falhou.'; return; }

    // Codes are shown in the same table as every other code, rather than as a bare list. A code on
    // its own is not usable information: the whole point of the note and the duration is knowing,
    // later, who a code was for and what it grants.
    await loadKeys();
    document.getElementById('keyResult').scrollIntoView({ block: 'nearest' });
  }

  async function loadKeys() {
    const target = document.getElementById('keyResult');
    target.setAttribute('aria-busy', 'true');
    const result = await api('/admin/keys');
    target.removeAttribute('aria-busy');
    if (!result) { target.textContent = 'Falhou.'; return; }
    if (!result.keys.length) { target.innerHTML = '<p class="sub">Nenhum código ainda.</p>'; return; }

    target.innerHTML =
      '<div class="table-wrap"><table><thead><tr>'
      + '<th>Código</th><th>Vale</th><th>Para quem</th><th>Criado</th><th>Estado</th><th>Ações</th>'
      + '</tr></thead><tbody>'
      + result.keys.map(function (key) {
          const used = Boolean(key.redeemed_by);
          return '<tr>'
            + '<td><code>' + esc(key.key_code) + '</code></td>'
            + '<td>' + duration(key.grant_days) + '</td>'
            + '<td class="sub">' + esc(key.note || '—') + '</td>'
            + '<td class="sub">' + date(key.created_at) + '</td>'
            + '<td>' + (used
                ? '<span class="tag dead">usado</span> <span class="sub">'
                  + esc(key.redeemed_by) + ' · ' + date(key.redeemed_at) + '</span>'
                : '<span class="tag active">livre</span>')
            + '</td>'
            // Only an unused key can be cancelled. A redeemed one is history: deleting it would
            // break the link between a device and how it was activated, and the way to take back
            // what it granted is to revoke that device.
            + '<td>' + (used
                ? '<span class="sub">—</span>'
                : '<button class="ghost" onclick="cancelKey(\\'' + esc(key.key_code) + '\\')">Cancelar</button>')
            + '</td>'
            + '</tr>';
        }).join('') + '</tbody></table></div>';
  }

  /**
   * Cancels an unused code.
   *
   * Confirmed, because it cannot be undone — the code stops working the moment this returns, and
   * anybody already holding it finds it invalid.
   */
  async function cancelKey(code) {
    if (!confirm('Cancelar o código ' + code + '?\\n\\nDeixa de funcionar imediatamente.')) return;
    const result = await api('/admin/keys/cancel', { key: code });
    if (!result) {
      alert('Não foi possível cancelar. Se já foi usado, revogue o dispositivo em vez disso.');
    }
    await loadKeys();
  }

  /** Days as something readable: "30 dias", "1 ano", "2 anos". */
  function duration(days) {
    const value = Number(days);
    if (!Number.isFinite(value)) return '—';
    if (value >= 365 && value % 365 === 0) {
      const years = value / 365;
      return years === 1 ? '1 ano' : years + ' anos';
    }
    return value + (value === 1 ? ' dia' : ' dias');
  }

  /**
   * How long is left, which is the question actually being asked.
   *
   * A date alone makes the reader do arithmetic against today, and the reason for looking a device
   * up is nearly always "how long do they have?".
   */
  function remaining(device) {
    const end = device.expires_at || device.trial_ends_at;
    if (!end) return '—';
    const parsed = new Date(end);
    if (Number.isNaN(parsed.getTime())) return '—';

    const days = Math.ceil((parsed.getTime() - Date.now()) / 86400000);
    if (days < 0) return '<span class="over">terminou</span>';
    if (days === 0) return '<span class="soon">último dia</span>';
    const label = days + (days === 1 ? ' dia' : ' dias');
    return days <= 3 ? '<span class="soon">' + label + '</span>' : label;
  }

  function deviceType(value) {
    return esc({
      WINDOWS_PC: 'Computador Windows',
      ANDROID_PHONE: 'Celular Android',
      ANDROID_TABLET: 'Tablet Android',
      ANDROID_TV: 'Android TV',
      TV: 'Smart TV',
    }[value] || 'Ainda não informado');
  }

  function platformLabel(value) {
    return { WINDOWS: 'Windows', ANDROID: 'Android', TIZEN: 'Samsung Tizen', WEBOS: 'LG webOS' }[value]
      || 'Ainda não informado';
  }

  function sourceLabel(value) {
    return esc({
      STRIPE: 'Pagamento Stripe',
      GOOGLE_PLAY: 'Google Play',
      ACTIVATION_KEY: 'Código de ativação',
      MANUAL: 'Liberação manual',
      TRIAL: 'Teste gratuito',
    }[value] || 'Não identificada');
  }

  function countryLabel(value) {
    const code = String(value || '').toUpperCase();
    if (!/^[A-Z]{2}$/.test(code)) return 'Ainda não informado';
    const flag = String.fromCodePoint(...Array.from(code).map(function (letter) {
      return 127397 + letter.charCodeAt(0);
    }));
    let name = code;
    try {
      if (typeof Intl.DisplayNames === 'function') {
        name = new Intl.DisplayNames(['pt-BR'], { type: 'region' }).of(code) || code;
      }
    } catch (_) { /* Older admin browsers keep the safe ISO code. */ }
    return flag + ' ' + esc(name) + ' (' + esc(code) + ')';
  }

  function eventLabel(value) {
    return {
      registered: 'Registrado', validated: 'Validado', purchased: 'Pagamento',
      google_play_purchased: 'Google Play', redeemed: 'Código usado', granted: 'Liberado',
      revoked: 'Bloqueado', refunded: 'Reembolsado', archived: 'Apagado da lista',
      restored: 'Restaurado', identity_adopted: 'Identidade atualizada',
    }[value] || value;
  }

  function tag(status) {
    const cls = status === 'ACTIVE' ? 'active' : status === 'TRIAL' ? 'trial' : 'dead';
    const label = {
      ACTIVE: 'ATIVO', TRIAL: 'EM TESTE', REVOKED: 'BLOQUEADO',
      EXPIRED: 'EXPIRADO', REFUNDED: 'REEMBOLSADO',
    }[status] || status;
    return '<span class="tag ' + cls + '">' + esc(label) + '</span>';
  }

  function date(value) {
    if (!value) return '—';
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return esc(value);
    return '<time datetime="' + esc(value) + '">'
      + new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' }).format(parsed)
      + '</time>';
  }

  function dateTime(value) {
    if (!value) return '—';
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return esc(value);
    return '<time datetime="' + esc(value) + '">'
      + new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(parsed)
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
