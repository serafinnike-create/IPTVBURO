/**
 * The pages a customer sees: buy, thank you, and the manage screen.
 *
 * Served by the Worker itself rather than hosted separately. Two reasons: the device id and its
 * status have to be looked up server-side anyway, and one origin means no CORS to configure and
 * nothing to keep in sync between a static host and this service.
 *
 * Written as plain HTML with inline CSS. A build step, a framework and a bundler would add three
 * things to maintain for a page that is a headline, a price and a button.
 */

/**
 * Everything visible, in the four languages the app ships in.
 *
 * A customer who bought a Portuguese app and lands on an English payment page hesitates, and
 * hesitation at the payment step is the expensive kind.
 */
import {
  buroMark,
  cardPayment,
  phoneScanning,
  screenWithCode,
  unlockedApp,
} from './illustrations.js';

const COPY = {
  pt: {
    title: 'Ativar o IPTV BURO',
    lead: 'Ative este dispositivo por 2 anos.',
    device: 'Dispositivo',
    status: 'Estado',
    expires: 'Válido até',
    buy: 'Pagar com cartão',
    secure: 'Pagamento processado pela Stripe. Não guardamos dados do seu cartão.',
    whyNotLifetime:
      'Porque não é vitalício: o aplicativo continua a ser mantido e atualizado — provedores mudam, '
      + 'formatos mudam, o Windows muda. Os 2 anos pagam esse trabalho.',
    oneDevice:
      'Cada pagamento ativa um dispositivo. O identificador acima é desta máquina.',
    noContent:
      'O IPTV BURO não fornece conteúdo, listas nem credenciais. É um reprodutor: funciona com a '
      + 'lista que você já tem.',
    statusActive: 'Ativo',
    statusTrial: 'Período de teste',
    statusExpired: 'Expirado',
    statusGrace: 'Período de tolerância',
    statusRevoked: 'Revogado',
    statusRefunded: 'Reembolsado',
    statusUnknown: 'Ainda não cadastrado',
    thanks: 'Pagamento confirmado',
    thanksBody:
      'O seu dispositivo está ativo. Volte ao aplicativo e ele vai reconhecer automaticamente — '
      + 'se estiver aberto, feche e abra de novo.',
    thanksProcessing: 'Pagamento em processamento',
    thanksProcessingBody:
      'A Stripe retornou ao site, mas a ativação ainda não chegou ao servidor. Aguarde alguns '
      + 'segundos e verifique novamente; não pague uma segunda vez.',
    thanksUnverified: 'Pagamento não confirmado',
    thanksUnverifiedBody:
      'Este endereço não contém uma sessão de pagamento válida para o dispositivo. Abrir esta '
      + 'página não ativa nem cobra nada.',
    thanksRetry: 'Verificar novamente',
    thanksBack: 'Voltar ao início',
    missingDevice: 'Abra esta página pelo aplicativo ou leia o código QR da tela de ativação.',
    unavailable: 'Não foi possível abrir o pagamento',
    unavailableBody:
      'Nada foi cobrado. O sistema de pagamento não respondeu — tente de novo dentro de alguns '
      + 'minutos. Se continuar, escreva-nos com o identificador acima.',
    tryAgain: 'Tentar de novo',

    howTitle: 'Como funciona',
    step1: 'Abra o aplicativo',
    step1Body: 'O código do seu dispositivo aparece na tela de ativação.',
    step2: 'Leia o código QR',
    step2Body: 'Use o celular ou clique no botão para abrir esta página.',
    step3: 'Pague com cartão',
    step3Body: 'Pela Stripe. Não vemos nem guardamos os dados do seu cartão.',
    step4: 'Volte ao aplicativo',
    step4Body: 'Fica ativo em segundos. Não é preciso escrever nada.',

    haveKey: 'Tem um código de ativação?',
    haveKeyBody: 'Se recebeu um código, ative aqui sem pagar de novo.',
    keyLabel: 'Código de ativação',
    activate: 'Ativar',
    activated: 'Dispositivo ativado',
    activatedBody: 'Volte ao aplicativo e abra de novo. Já está ativo.',
    keyUnknown: 'Esse código não existe.',
    keyUsed: 'Esse código já foi usado.',
    keyExpiredMsg: 'Esse código expirou.',
    keyBadDevice: 'O código do dispositivo não está correto.',
    keyFailed: 'Não foi possível ativar. Tente de novo.',

    homeLead: 'Um reprodutor para as suas próprias listas. TV, filmes e séries, organizados.',
    trialNote: '7 dias grátis. Depois, 2 anos por um pagamento único.',
    homeEyebrow: 'SEU CONTEÚDO, BEM ORGANIZADO',
    homeActivate: 'Ativar um código',
    homeHow: 'Ver como funciona',
    homePlatform:
      'Disponível primeiro para Windows. Outras plataformas só serão anunciadas depois de validadas.',
    proofTrial: '7 dias para testar',
    proofTerm: '2 anos por pagamento único',
    proofPrivate: 'As suas listas continuam sendo suas',
  },
  en: {
    title: 'Activate IPTV BURO',
    lead: 'Activate this device for 2 years.',
    device: 'Device',
    status: 'Status',
    expires: 'Valid until',
    buy: 'Pay by card',
    secure: 'Payment handled by Stripe. We never see or store your card details.',
    whyNotLifetime:
      'Why not lifetime: the app keeps being maintained and updated — providers change, formats '
      + 'change, Windows changes. The 2 years pay for that work.',
    oneDevice: 'Each payment activates one device. The identifier above is this machine.',
    noContent:
      'IPTV BURO provides no content, no playlists and no credentials. It is a player: it works '
      + 'with the list you already have.',
    statusActive: 'Active',
    statusTrial: 'Trial',
    statusExpired: 'Expired',
    statusGrace: 'Grace period',
    statusRevoked: 'Revoked',
    statusRefunded: 'Refunded',
    statusUnknown: 'Not registered yet',
    thanks: 'Payment confirmed',
    thanksBody:
      'Your device is active. Go back to the app and it will pick this up automatically — if it is '
      + 'open, close and reopen it.',
    thanksProcessing: 'Payment processing',
    thanksProcessingBody:
      'Stripe returned to the site, but activation has not reached the server yet. Wait a few '
      + 'seconds and check again; do not pay a second time.',
    thanksUnverified: 'Payment not confirmed',
    thanksUnverifiedBody:
      'This address does not contain a valid payment session for the device. Opening this page '
      + 'does not activate or charge anything.',
    thanksRetry: 'Check again',
    thanksBack: 'Back to home',
    missingDevice: 'Open this page from the app, or scan the QR code on the activation screen.',
    unavailable: 'Could not open payment',
    unavailableBody:
      'You have not been charged. The payment system did not respond — please try again in a few '
      + 'minutes. If it keeps happening, write to us quoting the identifier above.',
    tryAgain: 'Try again',

    howTitle: 'How it works',
    step1: 'Open the app',
    step1Body: 'Your device code appears on the activation screen.',
    step2: 'Scan the QR code',
    step2Body: 'With your phone, or click the button to open this page.',
    step3: 'Pay by card',
    step3Body: 'Through Stripe. We never see or store your card details.',
    step4: 'Go back to the app',
    step4Body: 'Active within seconds. Nothing to type in.',

    haveKey: 'Have an activation code?',
    haveKeyBody: 'If you were given a code, activate here without paying again.',
    keyLabel: 'Activation code',
    activate: 'Activate',
    activated: 'Device activated',
    activatedBody: 'Go back to the app and open it again. It is active.',
    keyUnknown: 'That code does not exist.',
    keyUsed: 'That code has already been used.',
    keyExpiredMsg: 'That code has expired.',
    keyBadDevice: 'The device code is not valid.',
    keyFailed: 'Could not activate. Please try again.',

    homeLead: 'A player for your own playlists. TV, films and series, organised.',
    trialNote: '7 days free. Then 2 years for a single payment.',
    homeEyebrow: 'YOUR MEDIA, BEAUTIFULLY ORGANISED',
    homeActivate: 'Activate a code',
    homeHow: 'See how it works',
    homePlatform:
      'Available first on Windows. Other platforms will only be announced after validation.',
    proofTrial: '7 days to try it',
    proofTerm: '2 years, one payment',
    proofPrivate: 'Your playlists remain yours',
  },
  de: {
    title: 'IPTV BURO aktivieren',
    lead: 'Aktivieren Sie dieses Gerät für 2 Jahre.',
    device: 'Gerät',
    status: 'Status',
    expires: 'Gültig bis',
    buy: 'Mit Karte bezahlen',
    secure: 'Zahlung über Stripe. Ihre Kartendaten sehen wir nie.',
    whyNotLifetime:
      'Warum nicht lebenslang: Die App wird weiter gepflegt und aktualisiert — Anbieter ändern '
      + 'sich, Formate ändern sich, Windows ändert sich. Die 2 Jahre bezahlen diese Arbeit.',
    oneDevice: 'Jede Zahlung aktiviert ein Gerät. Die Kennung oben gehört zu diesem Rechner.',
    noContent:
      'IPTV BURO liefert keine Inhalte, keine Listen und keine Zugangsdaten. Es ist ein Player: Er '
      + 'arbeitet mit der Liste, die Sie bereits haben.',
    statusActive: 'Aktiv',
    statusTrial: 'Testzeitraum',
    statusExpired: 'Abgelaufen',
    statusGrace: 'Toleranzzeitraum',
    statusRevoked: 'Widerrufen',
    statusRefunded: 'Erstattet',
    statusUnknown: 'Noch nicht registriert',
    thanks: 'Zahlung bestätigt',
    thanksBody:
      'Ihr Gerät ist aktiv. Kehren Sie zur App zurück — falls sie offen ist, schließen und erneut '
      + 'öffnen.',
    thanksProcessing: 'Zahlung wird verarbeitet',
    thanksProcessingBody:
      'Stripe hat zur Website zurückgeleitet, aber die Aktivierung ist noch nicht am Server '
      + 'angekommen. Warten Sie einige Sekunden und prüfen Sie erneut; zahlen Sie nicht noch einmal.',
    thanksUnverified: 'Zahlung nicht bestätigt',
    thanksUnverifiedBody:
      'Diese Adresse enthält keine gültige Zahlungssitzung für das Gerät. Das Öffnen dieser Seite '
      + 'aktiviert nichts und löst keine Zahlung aus.',
    thanksRetry: 'Erneut prüfen',
    thanksBack: 'Zur Startseite',
    missingDevice: 'Öffnen Sie diese Seite aus der App oder scannen Sie den QR-Code.',
    unavailable: 'Zahlung konnte nicht geöffnet werden',
    unavailableBody:
      'Es wurde nichts abgebucht. Das Zahlungssystem hat nicht geantwortet — bitte versuchen Sie es '
      + 'in einigen Minuten erneut. Bleibt es dabei, schreiben Sie uns mit der obigen Kennung.',
    tryAgain: 'Erneut versuchen',

    howTitle: 'So funktioniert es',
    step1: 'App öffnen',
    step1Body: 'Ihr Gerätecode erscheint auf dem Aktivierungsbildschirm.',
    step2: 'QR-Code scannen',
    step2Body: 'Mit dem Handy, oder auf die Schaltfläche klicken.',
    step3: 'Mit Karte bezahlen',
    step3Body: 'Über Stripe. Ihre Kartendaten sehen und speichern wir nie.',
    step4: 'Zurück zur App',
    step4Body: 'In Sekunden aktiv. Nichts einzutippen.',

    haveKey: 'Haben Sie einen Aktivierungscode?',
    haveKeyBody: 'Wenn Sie einen Code erhalten haben, aktivieren Sie hier ohne erneut zu zahlen.',
    keyLabel: 'Aktivierungscode',
    activate: 'Aktivieren',
    activated: 'Gerät aktiviert',
    activatedBody: 'Gehen Sie zur App zurück und öffnen Sie sie erneut. Sie ist aktiv.',
    keyUnknown: 'Diesen Code gibt es nicht.',
    keyUsed: 'Dieser Code wurde bereits verwendet.',
    keyExpiredMsg: 'Dieser Code ist abgelaufen.',
    keyBadDevice: 'Der Gerätecode ist ungültig.',
    keyFailed: 'Aktivierung fehlgeschlagen. Bitte erneut versuchen.',

    homeLead: 'Ein Player für Ihre eigenen Playlists. TV, Filme und Serien, geordnet.',
    trialNote: '7 Tage kostenlos. Danach 2 Jahre für eine einmalige Zahlung.',
    homeEyebrow: 'IHRE MEDIEN, KLAR GEORDNET',
    homeActivate: 'Code aktivieren',
    homeHow: 'So funktioniert es',
    homePlatform:
      'Zuerst für Windows verfügbar. Weitere Plattformen werden erst nach der Validierung angekündigt.',
    proofTrial: '7 Tage testen',
    proofTerm: '2 Jahre, eine Zahlung',
    proofPrivate: 'Ihre Playlists bleiben Ihre',
  },
  it: {
    title: 'Attiva IPTV BURO',
    lead: 'Attiva questo dispositivo per 2 anni.',
    device: 'Dispositivo',
    status: 'Stato',
    expires: 'Valido fino al',
    buy: 'Paga con carta',
    secure: 'Pagamento gestito da Stripe. I dati della carta non passano da noi.',
    whyNotLifetime:
      'Perché non a vita: l\'app continua a essere aggiornata — i provider cambiano, i formati '
      + 'cambiano, Windows cambia. I 2 anni pagano questo lavoro.',
    oneDevice: 'Ogni pagamento attiva un dispositivo. L\'identificatore sopra è di questa macchina.',
    noContent:
      'IPTV BURO non fornisce contenuti, liste o credenziali. È un lettore: funziona con la lista '
      + 'che hai già.',
    statusActive: 'Attivo',
    statusTrial: 'Periodo di prova',
    statusExpired: 'Scaduto',
    statusGrace: 'Periodo di tolleranza',
    statusRevoked: 'Revocato',
    statusRefunded: 'Rimborsato',
    statusUnknown: 'Non ancora registrato',
    thanks: 'Pagamento confermato',
    thanksBody:
      'Il dispositivo è attivo. Torna all\'app: se è aperta, chiudila e riaprila.',
    thanksProcessing: 'Pagamento in elaborazione',
    thanksProcessingBody:
      'Stripe è tornato al sito, ma l\'attivazione non è ancora arrivata al server. Attendi qualche '
      + 'secondo e controlla di nuovo; non pagare una seconda volta.',
    thanksUnverified: 'Pagamento non confermato',
    thanksUnverifiedBody:
      'Questo indirizzo non contiene una sessione di pagamento valida per il dispositivo. Aprire '
      + 'questa pagina non attiva e non addebita nulla.',
    thanksRetry: 'Controlla di nuovo',
    thanksBack: 'Torna alla home',
    missingDevice: 'Apri questa pagina dall\'app o inquadra il codice QR.',
    unavailable: 'Impossibile aprire il pagamento',
    unavailableBody:
      'Non ti è stato addebitato nulla. Il sistema di pagamento non ha risposto — riprova tra '
      + 'qualche minuto. Se continua, scrivici indicando l\'identificatore qui sopra.',
    tryAgain: 'Riprova',

    howTitle: 'Come funziona',
    step1: 'Apri l\'app',
    step1Body: 'Il codice del dispositivo appare nella schermata di attivazione.',
    step2: 'Inquadra il codice QR',
    step2Body: 'Con il telefono, oppure premi il pulsante per aprire questa pagina.',
    step3: 'Paga con carta',
    step3Body: 'Tramite Stripe. Non vediamo né conserviamo i dati della carta.',
    step4: 'Torna all\'app',
    step4Body: 'Attiva in pochi secondi. Niente da digitare.',

    haveKey: 'Hai un codice di attivazione?',
    haveKeyBody: 'Se hai ricevuto un codice, attiva qui senza pagare di nuovo.',
    keyLabel: 'Codice di attivazione',
    activate: 'Attiva',
    activated: 'Dispositivo attivato',
    activatedBody: 'Torna all\'app e riaprila. È attiva.',
    keyUnknown: 'Questo codice non esiste.',
    keyUsed: 'Questo codice è già stato usato.',
    keyExpiredMsg: 'Questo codice è scaduto.',
    keyBadDevice: 'Il codice del dispositivo non è valido.',
    keyFailed: 'Attivazione non riuscita. Riprova.',

    homeLead: 'Un lettore per le tue playlist. TV, film e serie, in ordine.',
    trialNote: '7 giorni gratis. Poi 2 anni con un pagamento unico.',
    homeEyebrow: 'I TUOI CONTENUTI, BENE ORGANIZZATI',
    homeActivate: 'Attiva un codice',
    homeHow: 'Scopri come funziona',
    homePlatform:
      'Disponibile prima su Windows. Le altre piattaforme saranno annunciate solo dopo la convalida.',
    proofTrial: '7 giorni di prova',
    proofTerm: '2 anni, un solo pagamento',
    proofPrivate: 'Le tue playlist restano tue',
  },
};

/** Falls back to Portuguese, which is the product's first language. */
export function copyFor(language) {
  return COPY[language] ?? COPY.pt;
}

/**
 * Picks a language from the browser's Accept-Language header.
 *
 * Only the four the app ships in. Anything else gets Portuguese rather than a half-translated page.
 */
export function languageFrom(header) {
  const supported = new Set(['pt', 'en', 'de', 'it']);
  const choices = String(header ?? '')
    .split(',')
    .map((part, index) => {
      const [rawTag, ...parameters] = part.trim().toLowerCase().split(';');
      const code = rawTag.split('-')[0];
      const qualityParameter = parameters.find((parameter) => parameter.trim().startsWith('q='));
      const parsedQuality = qualityParameter
        ? Number.parseFloat(qualityParameter.trim().slice(2))
        : 1;
      return {
        code,
        index,
        quality: Number.isFinite(parsedQuality) ? parsedQuality : 0,
      };
    })
    .filter(({ code, quality }) => supported.has(code) && quality > 0)
    .sort((left, right) => right.quality - left.quality || left.index - right.index);

  return choices[0]?.code ?? 'pt';
}

/**
 * The language for a request, preferring an explicit choice over the browser's.
 *
 * The app puts `?lang=` on every link and QR code it produces, because it knows something the
 * browser header cannot express: which language the customer actually chose in the app. Someone
 * running the app in English on a Portuguese Windows announces `pt` in every request, and would land
 * on a Portuguese payment page — hesitation at exactly the wrong moment.
 *
 * It matters most for the QR code, where the page opens on a *phone* whose language may have nothing
 * to do with the computer the licence is for.
 *
 * An unrecognised value falls through to the header rather than to Portuguese, so a mangled link
 * still gets the best available guess.
 */
export function languageForRequest(request, url) {
  const requested = String(url?.searchParams?.get('lang') ?? '').toLowerCase().split('-')[0];
  if (['pt', 'en', 'de', 'it'].includes(requested)) return requested;

  return languageFrom(request.headers.get('accept-language'));
}

/** The purchase page. */
export function buyPage({ deviceId, device, language, price, currency }) {
  const t = copyFor(language);

  if (!deviceId) {
    return shell(t.title, language, `
      <div class="card">
        <h1>${t.title}</h1>
        <p class="muted">${escape(t.missingDevice)}</p>
      </div>
    `);
  }

  const statusLabel = statusLabelFor(device?.status, t);
  // An already-active device is told so rather than sold to again. Taking a second payment for
  // something the customer already owns is the fastest route to a chargeback.
  const alreadyActive = device?.status === 'ACTIVE';

  return shell(t.title, language, `
    <div class="card">
      ${header()}
      <h1>${t.title}</h1>
      <p class="lead">${escape(t.lead)}</p>

      <div class="device">
        <div class="row"><span>${escape(t.device)}</span><code>${escape(deviceId)}</code></div>
        <div class="row"><span>${escape(t.status)}</span><strong>${escape(statusLabel)}</strong></div>
        ${device?.expires_at ? `<div class="row"><span>${escape(t.expires)}</span><strong><time datetime="${escape(device.expires_at)}">${escape(formatDate(device.expires_at, language))}</time></strong></div>` : ''}
      </div>

      ${alreadyActive ? '' : `
        <div class="price">${escape(price.label)}<span class="period"> / 2 ${language === 'pt' ? 'anos' : language === 'de' ? 'Jahre' : language === 'it' ? 'anni' : 'years'}</span></div>
        <form method="POST" action="/checkout">
          <input type="hidden" name="device" value="${escape(deviceId)}">
          <input type="hidden" name="lang" value="${escape(language)}">
          <button type="submit">${escape(t.buy)}</button>
        </form>
        <p class="secure">${escape(t.secure)}</p>
      `}

      ${alreadyActive ? '' : howItWorks(t)}

      <div class="notes">
        <p>${escape(t.oneDevice)}</p>
        <p>${escape(t.whyNotLifetime)}</p>
        <p class="disclaimer">${escape(t.noContent)}</p>
      </div>
    </div>
  `);
}

/** The return from Checkout. Only a server-verified ledger row may render the success state. */
export function thanksPage({
  language,
  state = 'unverified',
  deviceId = null,
  expiresAt = null,
  retryUrl = null,
}) {
  const t = copyFor(language);
  const paid = state === 'paid';
  const processing = state === 'processing';
  const title = paid ? t.thanks : processing ? t.thanksProcessing : t.thanksUnverified;
  const body = paid ? t.thanksBody : processing ? t.thanksProcessingBody : t.thanksUnverifiedBody;
  const symbol = paid ? '✓' : processing ? '…' : '!';
  const actionUrl = processing && retryUrl ? retryUrl : '/';
  const actionLabel = processing ? t.thanksRetry : t.thanksBack;

  return shell(title, language, `
    <div class="card" role="status">
      ${header()}
      <h1>${symbol} ${escape(title)}</h1>
      <p class="lead">${escape(body)}</p>
      ${deviceId ? `
        <div class="device">
          <div class="row"><span>${escape(t.device)}</span><code>${escape(deviceId)}</code></div>
          ${expiresAt ? `<div class="row"><span>${escape(t.expires)}</span><strong><time datetime="${escape(expiresAt)}">${escape(formatDate(expiresAt, language))}</time></strong></div>` : ''}
        </div>
      ` : ''}
      <a class="cta" href="${escape(actionUrl)}">${escape(actionLabel)}</a>
    </div>
  `);
}

/**
 * The four steps, drawn.
 *
 * Placed below the price rather than above it. Someone who arrived by scanning the QR code has
 * already done steps one and two and wants the button; someone who found the site another way needs
 * the explanation. Putting the button first serves the first without failing the second.
 */
function howItWorks(t) {
  const steps = [
    [screenWithCode(), t.step1, t.step1Body],
    [phoneScanning(), t.step2, t.step2Body],
    [cardPayment(), t.step3, t.step3Body],
    [unlockedApp(), t.step4, t.step4Body],
  ];

  return `
    <section class="how" id="como-funciona">
      <h2>${escape(t.howTitle)}</h2>
      <ol class="steps">
        ${steps.map(([art, title, body], index) => `
          <li>
            <div class="art-frame">${art}<span class="step-number">${index + 1}</span></div>
            <h3>${escape(title)}</h3>
            <p>${escape(body)}</p>
          </li>
        `).join('')}
      </ol>
    </section>`;
}

/**
 * Redeeming a key that was handed out by hand.
 *
 * Separate from the purchase page because the two audiences do not overlap: someone with a key is
 * not deciding whether to buy, and putting a "or enter a code" field next to a price invites the
 * customer to go looking for one on the internet instead of paying.
 */
export function activatePage({ deviceId, language, message, done }) {
  const t = copyFor(language);

  if (done) {
    return shell(t.activated, language, `
      <div class="card">
        ${header()}
        <h1>✓ ${escape(t.activated)}</h1>
        <p class="lead">${escape(t.activatedBody)}</p>
      </div>
    `);
  }

  return shell(t.haveKey, language, `
    <div class="card">
      ${header()}
      <h1>${escape(t.haveKey)}</h1>
      <p class="lead">${escape(t.haveKeyBody)}</p>

      ${message ? `<p class="error">${escape(message)}</p>` : ''}

      <form method="POST" action="/ativar" class="stack">
        <input type="hidden" name="lang" value="${escape(language)}">
        <label>
          <span>${escape(t.device)}</span>
          <input name="device" value="${escape(deviceId ?? '')}" placeholder="XXXX-XXXX-XXXX"
                 required autocomplete="off" spellcheck="false">
        </label>
        <label>
          <span>${escape(t.keyLabel)}</span>
          <input name="key" placeholder="XXXX-XXXX" required autocomplete="off" spellcheck="false">
        </label>
        <button type="submit">${escape(t.activate)}</button>
      </form>
    </div>
  `);
}

/**
 * The front page, for somebody who arrived without a device id.
 *
 * Its job is to explain the product and then get out of the way. It cannot sell anything, because
 * without a device there is nothing to sell — which is why the only action it offers is opening the
 * app.
 */
export function homePage({ language }) {
  const t = copyFor(language);

  return shell('IPTV BURO', language, `
    <main class="card landing">
      <section class="hero">
        <div class="hero-copy">
          ${header()}
          <p class="eyebrow">${escape(t.homeEyebrow)}</p>
          <h1>IPTV BURO</h1>
          <p class="lead">${escape(t.homeLead)}</p>
          <p class="trial">${escape(t.trialNote)}</p>
          <div class="actions">
            <a class="cta" href="/ativar?lang=${escape(language)}">${escape(t.homeActivate)}</a>
            <a class="cta secondary" href="#como-funciona">${escape(t.homeHow)}</a>
          </div>
          <p class="platform-note">${escape(t.homePlatform)}</p>
        </div>
        <div class="hero-art" aria-hidden="true">
          <img src="/assets/buro-cinematic-multiscreen-hero.webp" alt="" width="1600" height="900">
        </div>
      </section>

      <section class="proof" aria-label="${escape(t.trialNote)}">
        <div><strong aria-hidden="true">7</strong><span>${escape(t.proofTrial)}</span></div>
        <div><strong aria-hidden="true">2</strong><span>${escape(t.proofTerm)}</span></div>
        <div><strong aria-hidden="true">✓</strong><span>${escape(t.proofPrivate)}</span></div>
      </section>

      <div class="landing-body">
        ${howItWorks(t)}
        <div class="notes">
          <p>${escape(t.missingDevice)}</p>
          <p class="disclaimer">${escape(t.noContent)}</p>
        </div>
      </div>
    </main>
  `);
}

/** The mark and wordmark, on every page. */
function header() {
  return `<div class="brand">${buroMark(30)}<span>IPTV <b>BURO</b></span></div>`;
}

/**
 * Shown when Stripe could not be reached at all.
 *
 * The first line says nothing was charged, because that is the only question the customer actually
 * has. A page that merely says "error" leaves them wondering whether to try again or check their
 * bank, and that uncertainty is what turns a transient failure into a support message.
 */
export function checkoutUnavailablePage({ language, deviceId }) {
  const t = copyFor(language);
  return shell(t.unavailable, language, `
    <div class="card">
      ${header()}
      <h1>${escape(t.unavailable)}</h1>
      <p class="lead">${escape(t.unavailableBody)}</p>
      ${deviceId ? `<div class="device"><code>${escape(deviceId)}</code></div>` : ''}
      <form method="GET" action="/comprar">
        <input type="hidden" name="device" value="${escape(deviceId ?? '')}">
        <input type="hidden" name="lang" value="${escape(language)}">
        <button type="submit">${escape(t.tryAgain)}</button>
      </form>
    </div>
  `);
}

function statusLabelFor(status, t) {
  switch (status) {
    case 'ACTIVE':
      return t.statusActive;
    case 'TRIAL':
      return t.statusTrial;
    case 'GRACE':
      return t.statusGrace;
    case 'EXPIRED':
      return t.statusExpired;
    case 'REVOKED':
      return t.statusRevoked;
    case 'REFUNDED':
      return t.statusRefunded;
    default:
      return t.statusUnknown;
  }
}

function formatDate(iso, language) {
  const locale = { pt: 'pt-BR', en: 'en', de: 'de', it: 'it' }[language] ?? 'pt-BR';
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return String(iso).slice(0, 10);
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeZone: 'UTC' }).format(parsed);
}

/**
 * Escapes text before it reaches the page.
 *
 * The device id is validated upstream, but this is the boundary where a value becomes markup, and
 * a value that reaches here unescaped is a cross-site scripting hole regardless of how carefully
 * it was checked somewhere else.
 */
function escape(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

/** The page frame: the app's own dark palette, so the two do not look like different products. */
function shell(title, language, body) {
  return `<!doctype html>
<html lang="${escape(language)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(title)}</title>
<style>
  :root {
    --canvas: #0b0b0d;
    --surface: #17171b;
    --text: #f2f0ec;
    --muted: #a8a49c;
    --subtle: #9a968e;
    --gold: #d6a956;
    --gold-strong: #f0c877;
    /* Used by the drawings in illustrations.js, which reference these rather than carrying their
       own colours — so the art follows the palette instead of needing to be re-exported. */
    --raised: #1f1f24;
    --line: #67646f;
    --ok: #6fbf73;
    --bad: #e08585;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    min-height: 100vh;
    display: grid;
    place-items: center;
    padding: 24px;
    background:
      radial-gradient(circle at 80% 5%, rgba(214, 169, 86, .08), transparent 28rem),
      var(--canvas);
    color: var(--text);
    font: 16px/1.55 system-ui, -apple-system, "Segoe UI", sans-serif;
  }
  .card {
    width: 100%;
    max-width: 460px;
    background: var(--surface);
    border: 1px solid #45424a;
    border-radius: 18px;
    padding: 32px;
  }
  .card.wide { max-width: 720px; }
  .card.landing {
    max-width: 1180px;
    padding: 0;
    overflow: hidden;
    box-shadow: 0 28px 80px rgba(0, 0, 0, .38);
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 20px;
  }
  .brand span {
    font-size: 15px;
    letter-spacing: 0.14em;
    color: var(--text);
    font-weight: 500;
  }
  .brand b { color: var(--gold); font-weight: 800; }
  h1 { margin: 0 0 8px; font-size: 26px; line-height: 1.2; }
  .lead { margin: 0 0 24px; color: var(--muted); }
  .hero {
    min-height: 500px;
    position: relative;
    display: flex;
    align-items: center;
    isolation: isolate;
    overflow: hidden;
    background: #0f1013;
  }
  .hero::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    background: linear-gradient(90deg, #101014 0%, rgba(16, 16, 20, .96) 38%, rgba(16, 16, 20, .32) 72%, rgba(16, 16, 20, .08));
  }
  .hero-copy {
    width: min(54%, 600px);
    padding: 52px;
  }
  .hero-copy h1 {
    max-width: 12ch;
    margin-bottom: 14px;
    font-size: clamp(38px, 5vw, 62px);
    letter-spacing: -.035em;
  }
  .hero-copy .lead { max-width: 48ch; font-size: clamp(17px, 2vw, 20px); }
  .hero-art { position: absolute; inset: 0; z-index: -2; }
  .hero-art img { width: 100%; height: 100%; display: block; object-fit: cover; object-position: center right; }
  .eyebrow {
    margin: 0 0 16px;
    color: var(--gold-strong);
    font-size: 12px;
    font-weight: 800;
    letter-spacing: .14em;
  }
  .actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 26px 0 20px; }
  .cta {
    min-height: 48px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 12px 18px;
    border-radius: 10px;
    background: linear-gradient(180deg, var(--gold-strong), var(--gold));
    color: #1a1509;
    font-weight: 800;
    text-decoration: none;
  }
  .cta.secondary { border: 1px solid var(--line); background: rgba(23, 23, 27, .82); color: var(--text); }
  .cta:hover { filter: brightness(1.08); }
  .cta:focus-visible, button:focus-visible, input:focus-visible, select:focus-visible {
    outline: 3px solid var(--gold-strong);
    outline-offset: 3px;
  }
  .platform-note { max-width: 48ch; margin: 0; color: var(--subtle); font-size: 13px; }
  .proof {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    border-block: 1px solid #45424a;
    background: #111115;
  }
  .proof div { min-height: 104px; display: grid; place-items: center; align-content: center; gap: 3px; padding: 18px; text-align: center; }
  .proof div + div { border-left: 1px solid #45424a; }
  .proof strong { color: var(--gold-strong); font-size: 24px; line-height: 1; }
  .proof span { color: var(--muted); font-size: 13px; }
  .landing-body { padding: 0 48px 42px; }
  .device {
    background: #101014;
    border-radius: 12px;
    padding: 14px 16px;
    margin-bottom: 24px;
  }
  .row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
    padding: 5px 0;
    font-size: 14px;
  }
  .row span { color: var(--subtle); }
  code {
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    font-size: 15px;
    letter-spacing: 0.04em;
    /* Selectable and easy to copy: this is the value a customer may need to read out. */
    user-select: all;
  }
  .price {
    font-size: 38px;
    font-weight: 800;
    color: var(--gold);
    margin-bottom: 18px;
  }
  .period { font-size: 15px; font-weight: 500; color: var(--muted); }
  button {
    width: 100%;
    padding: 15px;
    font: inherit;
    font-weight: 700;
    color: #1a1509;
    background: linear-gradient(180deg, var(--gold-strong), var(--gold));
    border: 0;
    border-radius: 10px;
    cursor: pointer;
  }
  button:hover { filter: brightness(1.06); }
  .secure { margin: 12px 0 0; font-size: 12.5px; color: var(--subtle); text-align: center; }
  .notes { margin-top: 26px; border-top: 1px solid #262429; padding-top: 18px; }
  .notes p { margin: 0 0 12px; font-size: 13px; color: var(--subtle); }
  .disclaimer { color: var(--muted); }
  .muted { color: var(--muted); }
  .trial { color: var(--gold); font-size: 14px; font-weight: 600; margin: 0 0 8px; }

  /* The four steps. */
  .how { margin-top: 30px; border-top: 1px solid #45424a; padding-top: 24px; scroll-margin-top: 20px; }
  .how h2 {
    font-size: 12px; letter-spacing: .1em; text-transform: uppercase;
    color: var(--subtle); font-weight: 600; margin: 0 0 20px;
  }
  .steps {
    list-style: none; margin: 0; padding: 0;
    display: grid; gap: 22px;
    /* Two across on a phone, four on a desktop, without a media query: the minimum width decides. */
    grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  }
  .steps li { margin: 0; }
  .steps h3 { font-size: 14px; margin: 10px 0 3px; }
  .steps p { font-size: 12.5px; color: var(--subtle); margin: 0; line-height: 1.45; }
  .art-frame {
    position: relative;
    background: #101014;
    border: 1px solid var(--line);
    border-radius: 10px;
    padding: 10px;
  }
  .art { display: block; width: 100%; height: auto; }
  .step-number {
    position: absolute; top: -8px; left: -8px;
    width: 22px; height: 22px; border-radius: 50%;
    background: var(--gold); color: #1a1509;
    font-size: 12px; font-weight: 800;
    display: grid; place-items: center;
  }

  /* The activation form. */
  .stack { display: grid; gap: 14px; }
  .stack label { display: grid; gap: 6px; }
  .stack label span { font-size: 12.5px; color: var(--subtle); }
  .stack input {
    font: inherit;
    font-family: ui-monospace, Consolas, monospace;
    letter-spacing: .08em;
    text-transform: uppercase;
    padding: 13px 14px;
    background: #101014;
    border: 1px solid var(--line);
    border-radius: 10px;
    color: var(--text);
  }
  .stack input:focus { outline: 2px solid var(--gold); outline-offset: 1px; }
  .error {
    background: #2a1618; border: 1px solid #4a2428; color: #f0b3b3;
    border-radius: 10px; padding: 11px 14px; font-size: 13.5px; margin: 0 0 18px;
  }
  @media (max-width: 760px) {
    body { padding: 12px; }
    .card { padding: 24px; }
    .card.landing { padding: 0; }
    .hero { min-height: 610px; align-items: flex-end; }
    .hero::after {
      background: linear-gradient(0deg, #101014 0%, rgba(16, 16, 20, .96) 48%, rgba(16, 16, 20, .34) 100%);
    }
    .hero-art img { object-position: 68% center; }
    .hero-copy { width: 100%; padding: 30px 24px; }
    .hero-copy h1 { font-size: 42px; }
    .actions { display: grid; }
    .proof { grid-template-columns: 1fr; }
    .proof div { min-height: 84px; }
    .proof div + div { border-left: 0; border-top: 1px solid #45424a; }
    .landing-body { padding: 0 24px 28px; }
  }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; }
  }
</style>
</head>
<body>${body}</body>
</html>`;
}
