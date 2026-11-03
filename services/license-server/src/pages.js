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
    pairPageTitle: 'Enviar para a TV',
    pairPageLead: 'Digite o código que apareceu na televisão e cole a chave. A TV recebe sozinha.',
    pairPageCode: 'Código da TV',
    pairPageValue: 'Chave',
    pairPageValueHint: 'Cole aqui a chave do TMDb ou do OMDb',
    pairPageSend: 'Enviar para a TV',
    pairPageSent: 'Enviado. Confira a televisão.',
    pairPageUnknown: 'Código não encontrado ou vencido. Peça um novo na TV.',
    pairPageTaken: 'Este código já recebeu uma chave.',
    pairPageBadCode: 'O código tem seis dígitos.',
    pairPageBadValue: 'Cole uma chave antes de enviar.',
    pairPageFailed: 'Não foi possível enviar. Tente de novo.',
    pairTitlePageTitle: 'Enviar título para a TV',
    pairTitlePageLead: 'Digite o código mostrado na televisão e cole um link de título criado pelo IPTV BURO.',
    pairTitlePageValue: 'Link do título',
    pairTitlePageValueHint: 'Cole aqui o link do título',
    pairTitlePageSent: 'Título enviado. Confira a televisão.',
    pairTitlePageTaken: 'Este código já recebeu um título.',
    pairTitlePageBadValue: 'Cole um link de título antes de enviar.',
    // The activation pages' frame, added with the redesign.
    language: 'Idioma',
    years: 'anos',
    buyKicker: 'LICENÇA POR DISPOSITIVO',
    keyKicker: 'CÓDIGO DE ATIVAÇÃO',
    noSubscription: 'Pagamento único. Sem renovação automática.',
    enterDevice: 'Digite o código do dispositivo',
    enterDeviceBody:
      'Ele aparece na tela de ativação do aplicativo, no formato XXXX-XXXX-XXXX. Ler o código QR '
      + 'dessa tela abre esta página já preenchida.',
    continueToPrice: 'Ver preço e pagar',
    deviceHint: 'Na tela de ativação do aplicativo.',
    keyHint: 'Cada código ativa um único dispositivo.',
    buyInstead: 'Não tem um código? Comprar com cartão',
    keyInstead: 'Recebeu um código de ativação? Use aqui',
    activeAlready: 'Este dispositivo já está ativo. Não é preciso pagar de novo.',
    safetyTitle: 'Antes de pagar',
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
    pairPageTitle: 'Send to the TV',
    pairPageLead: 'Enter the code shown on the television and paste the key. The TV picks it up on its own.',
    pairPageCode: 'TV code',
    pairPageValue: 'Key',
    pairPageValueHint: 'Paste your TMDb or OMDb key here',
    pairPageSend: 'Send to the TV',
    pairPageSent: 'Sent. Check the television.',
    pairPageUnknown: 'Code not found or expired. Ask the TV for a new one.',
    pairPageTaken: 'This code already received a key.',
    pairPageBadCode: 'The code is six digits.',
    pairPageBadValue: 'Paste a key before sending.',
    pairPageFailed: 'Could not send. Try again.',
    pairTitlePageTitle: 'Send a title to the TV',
    pairTitlePageLead: 'Enter the code shown on the television and paste a title link created by IPTV BURO.',
    pairTitlePageValue: 'Title link',
    pairTitlePageValueHint: 'Paste the title link here',
    pairTitlePageSent: 'Title sent. Check the television.',
    pairTitlePageTaken: 'This code already received a title.',
    pairTitlePageBadValue: 'Paste a title link before sending.',
    // The activation pages' frame, added with the redesign.
    language: 'Language',
    years: 'years',
    buyKicker: 'ONE LICENCE PER DEVICE',
    keyKicker: 'ACTIVATION CODE',
    noSubscription: 'One payment. No automatic renewal.',
    enterDevice: 'Enter the device code',
    enterDeviceBody:
      'It is shown on the app’s activation screen, as XXXX-XXXX-XXXX. Scanning the QR code on that '
      + 'screen opens this page already filled in.',
    continueToPrice: 'See the price and pay',
    deviceHint: 'On the app’s activation screen.',
    keyHint: 'Each code activates a single device.',
    buyInstead: 'No code? Buy by card',
    keyInstead: 'Received an activation code? Use it here',
    activeAlready: 'This device is already active. There is nothing to pay.',
    safetyTitle: 'Before you pay',
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
    pairPageTitle: 'An den Fernseher senden',
    pairPageLead: 'Gib den Code ein, der auf dem Fernseher steht, und füge den Schlüssel ein. Der Fernseher holt ihn selbst ab.',
    pairPageCode: 'Fernseher-Code',
    pairPageValue: 'Schlüssel',
    pairPageValueHint: 'Füge hier deinen TMDb- oder OMDb-Schlüssel ein',
    pairPageSend: 'An den Fernseher senden',
    pairPageSent: 'Gesendet. Sieh auf den Fernseher.',
    pairPageUnknown: 'Code nicht gefunden oder abgelaufen. Fordere am Fernseher einen neuen an.',
    pairPageTaken: 'Dieser Code hat bereits einen Schlüssel erhalten.',
    pairPageBadCode: 'Der Code hat sechs Ziffern.',
    pairPageBadValue: 'Füge einen Schlüssel ein, bevor du sendest.',
    pairPageFailed: 'Senden nicht möglich. Versuche es erneut.',
    pairTitlePageTitle: 'Titel an den Fernseher senden',
    pairTitlePageLead: 'Gib den Code vom Fernseher ein und füge einen von IPTV BURO erstellten Titellink ein.',
    pairTitlePageValue: 'Titellink',
    pairTitlePageValueHint: 'Titellink hier einfügen',
    pairTitlePageSent: 'Titel gesendet. Sieh auf den Fernseher.',
    pairTitlePageTaken: 'Dieser Code hat bereits einen Titel erhalten.',
    pairTitlePageBadValue: 'Füge vor dem Senden einen Titellink ein.',
    // The activation pages' frame, added with the redesign.
    language: 'Sprache',
    years: 'Jahre',
    buyKicker: 'EINE LIZENZ PRO GERÄT',
    keyKicker: 'AKTIVIERUNGSCODE',
    noSubscription: 'Einmalzahlung. Keine automatische Verlängerung.',
    enterDevice: 'Gerätecode eingeben',
    enterDeviceBody:
      'Er steht auf dem Aktivierungsbildschirm der App, im Format XXXX-XXXX-XXXX. Wer den QR-Code '
      + 'dort scannt, landet hier mit bereits ausgefülltem Code.',
    continueToPrice: 'Preis ansehen und bezahlen',
    deviceHint: 'Auf dem Aktivierungsbildschirm der App.',
    keyHint: 'Jeder Code aktiviert genau ein Gerät.',
    buyInstead: 'Kein Code? Mit Karte kaufen',
    keyInstead: 'Aktivierungscode erhalten? Hier einlösen',
    activeAlready: 'Dieses Gerät ist bereits aktiv. Es ist nichts zu bezahlen.',
    safetyTitle: 'Vor dem Bezahlen',
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
    pairPageTitle: 'Invia al televisore',
    pairPageLead: 'Inserisci il codice mostrato sul televisore e incolla la chiave. Il televisore la riceve da solo.',
    pairPageCode: 'Codice del televisore',
    pairPageValue: 'Chiave',
    pairPageValueHint: 'Incolla qui la tua chiave TMDb o OMDb',
    pairPageSend: 'Invia al televisore',
    pairPageSent: 'Inviata. Controlla il televisore.',
    pairPageUnknown: 'Codice non trovato o scaduto. Richiedine uno nuovo sul televisore.',
    pairPageTaken: 'Questo codice ha già ricevuto una chiave.',
    pairPageBadCode: 'Il codice è di sei cifre.',
    pairPageBadValue: 'Incolla una chiave prima di inviare.',
    pairPageFailed: 'Invio non riuscito. Riprova.',
    pairTitlePageTitle: 'Invia un titolo al televisore',
    pairTitlePageLead: 'Inserisci il codice mostrato sul televisore e incolla un link titolo creato da IPTV BURO.',
    pairTitlePageValue: 'Link del titolo',
    pairTitlePageValueHint: 'Incolla qui il link del titolo',
    pairTitlePageSent: 'Titolo inviato. Controlla il televisore.',
    pairTitlePageTaken: 'Questo codice ha già ricevuto un titolo.',
    pairTitlePageBadValue: 'Incolla un link del titolo prima di inviare.',
    // The activation pages' frame, added with the redesign.
    language: 'Lingua',
    years: 'anni',
    buyKicker: 'UNA LICENZA PER DISPOSITIVO',
    keyKicker: 'CODICE DI ATTIVAZIONE',
    noSubscription: 'Pagamento unico. Nessun rinnovo automatico.',
    enterDevice: 'Inserisci il codice del dispositivo',
    enterDeviceBody:
      'Compare nella schermata di attivazione dell’app, nel formato XXXX-XXXX-XXXX. Inquadrando il '
      + 'codice QR di quella schermata questa pagina si apre già compilata.',
    continueToPrice: 'Vedi il prezzo e paga',
    deviceHint: 'Nella schermata di attivazione dell’app.',
    keyHint: 'Ogni codice attiva un solo dispositivo.',
    buyInstead: 'Nessun codice? Acquista con carta',
    keyInstead: 'Hai ricevuto un codice di attivazione? Usalo qui',
    activeAlready: 'Questo dispositivo è già attivo. Non c’è nulla da pagare.',
    safetyTitle: 'Prima di pagare',
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

/**
 * The purchase page.
 *
 * Two columns on a wide screen: what is being bought and the button on the left, how it works on the
 * right. Someone who scanned the QR code has done the first two steps already and wants the button;
 * someone who arrived another way reads across. On a phone the columns stack in that same order.
 */
export function buyPage({ deviceId, device, language, price, currency }) {
  const t = copyFor(language);

  // No device: ask for one. This used to be a sentence and nothing else — a dead end for anybody
  // who reached /comprar from a bookmark or a forwarded link, with the code sitting on their screen
  // and nowhere to type it.
  if (!deviceId) {
    return shell(t.title, language, `
      <main class="page narrow">
        <section class="panel">
          <p class="kicker">${escape(t.buyKicker)}</p>
          <h1>${escape(t.title)}</h1>
          <p class="lead">${escape(t.missingDevice)}</p>
          <form method="GET" action="/comprar" class="stack">
            <input type="hidden" name="lang" value="${escape(language)}">
            <label>
              <span>${escape(t.enterDevice)}</span>
              <input name="device" placeholder="XXXX-XXXX-XXXX" maxlength="14" required
                     autocomplete="off" autocapitalize="characters" spellcheck="false"
                     aria-describedby="device-hint">
              <small id="device-hint">${escape(t.enterDeviceBody)}</small>
            </label>
            <button type="submit">${escape(t.continueToPrice)}</button>
          </form>
          <p class="alt-link"><a href="/ativar?lang=${escape(language)}">${escape(t.keyInstead)} <span aria-hidden="true">→</span></a></p>
        </section>
      </main>
    `, { path: '/comprar' });
  }

  // An already-active device is told so rather than sold to again. Taking a second payment for
  // something the customer already owns is the fastest route to a chargeback.
  const alreadyActive = device?.status === 'ACTIVE';

  return shell(t.title, language, `
    <main class="page">
      <div class="split">
        <section class="primary">
          <p class="kicker">${escape(t.buyKicker)}</p>
          <h1>${escape(t.title)}</h1>
          <p class="lead">${escape(t.lead)}</p>

          ${deviceSummary(t, language, deviceId, device?.status, device?.expires_at)}

          ${alreadyActive ? `<p class="notice ok" role="status">${escape(t.activeAlready)}</p>` : `
            <div class="price-block">
              <div class="price">${escape(price.label)}<span class="period"> / 2 ${escape(t.years)}</span></div>
              <p class="small">${escape(t.noSubscription)}</p>
            </div>
            <form method="POST" action="/checkout">
              <input type="hidden" name="device" value="${escape(deviceId)}">
              <input type="hidden" name="lang" value="${escape(language)}">
              <button type="submit">${escape(t.buy)}</button>
            </form>
            <p class="secure">${lockIcon()}<span>${escape(t.secure)}</span></p>
            <p class="alt-link"><a href="/ativar?device=${escape(deviceId)}&amp;lang=${escape(language)}">${escape(t.keyInstead)} <span aria-hidden="true">→</span></a></p>
          `}
        </section>

        <aside class="secondary">
          ${alreadyActive ? '' : howItWorks(t)}
          <div class="notes">
            ${alreadyActive ? '' : `<h2>${escape(t.safetyTitle)}</h2>`}
            <p>${escape(t.oneDevice)}</p>
            <p>${escape(t.whyNotLifetime)}</p>
            <p class="disclaimer">${escape(t.noContent)}</p>
          </div>
        </aside>
      </div>
    </main>
  `, { path: '/comprar', params: { device: deviceId } });
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
  const tone = paid ? 'ok' : processing ? 'wait' : 'bad';
  const actionUrl = processing && retryUrl ? retryUrl : `/?lang=${language}`;
  const actionLabel = processing ? t.thanksRetry : t.thanksBack;
  // The language links keep the session, so switching language while waiting still checks the
  // same payment rather than dropping to "not confirmed".
  const retryParams = retryUrl ? Object.fromEntries(new URL(retryUrl, 'https://local').searchParams) : {};

  return shell(title, language, `
    <main class="page narrow">
      <section class="panel" role="status">
        <div class="state-icon ${tone}" aria-hidden="true">${symbol}</div>
        <h1>${escape(title)}</h1>
        <p class="lead">${escape(body)}</p>
        ${deviceId ? deviceSummary(t, language, deviceId, null, expiresAt) : ''}
        <a class="cta" href="${escape(actionUrl)}">${escape(actionLabel)}</a>
      </section>
    </main>
  `, { path: '/obrigado', params: { device: deviceId, ...retryParams } });
}

/**
 * The four steps, drawn.
 *
 * Beside the price rather than above it. Someone who arrived by scanning the QR code has already
 * done steps one and two and wants the button; someone who found the site another way needs the
 * explanation. Putting the button first serves the first without failing the second.
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

/** The device code, its state and its end date, in the one panel every page shows them in. */
function deviceSummary(t, language, deviceId, status, expiresAt) {
  return `
    <div class="device">
      <div class="row"><span>${escape(t.device)}</span><code>${escape(deviceId)}</code></div>
      ${status !== null ? `<div class="row"><span>${escape(t.status)}</span><strong class="pill ${escape(statusTone(status))}">${escape(statusLabelFor(status, t))}</strong></div>` : ''}
      ${expiresAt ? `<div class="row"><span>${escape(t.expires)}</span><strong><time datetime="${escape(expiresAt)}">${escape(formatDate(expiresAt, language))}</time></strong></div>` : ''}
    </div>`;
}

/**
 * Redeeming a key that was handed out by hand.
 *
 * Separate from the purchase page because the two audiences do not overlap: someone with a key is
 * not deciding whether to buy, and putting a "or enter a code" field next to a price invites the
 * customer to go looking for one on the internet instead of paying. Each page links to the other
 * once, quietly, for the customer who opened the wrong one.
 */
export function activatePage({ deviceId, language, message, done }) {
  const t = copyFor(language);

  if (done) {
    return shell(t.activated, language, `
      <main class="page narrow">
        <section class="panel" role="status">
          <div class="state-icon ok" aria-hidden="true">✓</div>
          <h1>${escape(t.activated)}</h1>
          <p class="lead">${escape(t.activatedBody)}</p>
        </section>
      </main>
    `, { path: '/ativar' });
  }

  const buyLink = deviceId
    ? `/comprar?device=${escape(deviceId)}&amp;lang=${escape(language)}`
    : `/comprar?lang=${escape(language)}`;

  return shell(t.haveKey, language, `
    <main class="page narrow">
      <section class="panel">
        <p class="kicker">${escape(t.keyKicker)}</p>
        <h1>${escape(t.haveKey)}</h1>
        <p class="lead">${escape(t.haveKeyBody)}</p>

        ${message ? `<p class="notice bad" role="alert">${escape(message)}</p>` : ''}

        <form method="POST" action="/ativar" class="stack">
          <input type="hidden" name="lang" value="${escape(language)}">
          <label>
            <span>${escape(t.device)}</span>
            <input name="device" value="${escape(deviceId ?? '')}" placeholder="XXXX-XXXX-XXXX"
                   maxlength="14" required autocomplete="off" autocapitalize="characters"
                   spellcheck="false" aria-describedby="device-hint">
            <small id="device-hint">${escape(t.deviceHint)}</small>
          </label>
          <label>
            <span>${escape(t.keyLabel)}</span>
            <input name="key" placeholder="XXXX-XXXX" required autocomplete="off"
                   autocapitalize="characters" spellcheck="false" aria-describedby="key-hint">
            <small id="key-hint">${escape(t.keyHint)}</small>
          </label>
          <button type="submit">${escape(t.activate)}</button>
        </form>
        <p class="alt-link"><a href="${buyLink}">${escape(t.buyInstead)} <span aria-hidden="true">→</span></a></p>
      </section>
    </main>
  `, { path: '/ativar', params: { device: deviceId } });
}

/**
 * The page the phone opens to send a key to a television.
 *
 * A plain form that posts to itself, with no JavaScript: the whole point is that this works on
 * whatever phone somebody happens to be holding, including one whose browser is old enough that
 * the television it is helping was new.
 *
 * The code field is `inputmode="numeric"` so a phone offers the number pad, and the key field is
 * left unrestricted because it holds anything from a 32-character hex string to a 239-character
 * token, and guessing at the shape here would reject a credential the app itself accepts.
 */
export function pairPage({ language, message, sent, code, kind }) {
  const t = copyFor(language);
  const titleMode = kind === 'open_title';
  const pageTitle = titleMode ? t.pairTitlePageTitle : t.pairPageTitle;
  const pageLead = titleMode ? t.pairTitlePageLead : t.pairPageLead;
  const valueLabel = titleMode ? t.pairTitlePageValue : t.pairPageValue;
  const valueHint = titleMode ? t.pairTitlePageValueHint : t.pairPageValueHint;
  const sentCopy = titleMode ? t.pairTitlePageSent : t.pairPageSent;
  const here = { path: '/parear', params: { kind: titleMode ? 'open_title' : null } };

  if (sent) {
    return shell(pageTitle, language, `
      <main class="page narrow">
        <section class="panel" role="status">
          <div class="state-icon ok" aria-hidden="true">✓</div>
          <h1>${escape(sentCopy)}</h1>
        </section>
      </main>
    `, here);
  }

  return shell(pageTitle, language, `
    <main class="page narrow">
      <section class="panel">
        <h1>${escape(pageTitle)}</h1>
        <p class="lead">${escape(pageLead)}</p>

        ${message ? `<p class="notice bad" role="alert">${escape(message)}</p>` : ''}

        <form method="POST" action="/parear" class="stack">
          <input type="hidden" name="lang" value="${escape(language)}">
          ${titleMode ? '<input type="hidden" name="kind" value="open_title">' : ''}
          <label>
            <span>${escape(t.pairPageCode)}</span>
            <input name="code" value="${escape(code ?? '')}" placeholder="000000"
                   inputmode="numeric" pattern="[0-9]{6}" maxlength="6" class="digits"
                   required autocomplete="off" spellcheck="false">
          </label>
          <label>
            <span>${escape(valueLabel)}</span>
            <input name="value" placeholder="${escape(valueHint)}" class="free"
                   required autocomplete="off" spellcheck="false">
          </label>
          <button type="submit">${escape(t.pairPageSend)}</button>
        </form>
      </section>
    </main>
  `, { ...here, params: { ...here.params, code } });
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
    <main class="page">
      <div class="card landing">
        <section class="hero">
          <div class="hero-copy">
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
      </div>
    </main>
  `, { path: '/' });
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
    <main class="page narrow">
      <section class="panel" role="alert">
        <div class="state-icon bad" aria-hidden="true">!</div>
        <h1>${escape(t.unavailable)}</h1>
        <p class="lead">${escape(t.unavailableBody)}</p>
        ${deviceId ? deviceSummary(t, language, deviceId, null, null) : ''}
        <form method="GET" action="/comprar">
          <input type="hidden" name="device" value="${escape(deviceId ?? '')}">
          <input type="hidden" name="lang" value="${escape(language)}">
          <button type="submit">${escape(t.tryAgain)}</button>
        </form>
      </section>
    </main>
  `, { path: '/comprar', params: { device: deviceId } });
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

/** The colour a state is shown in: green in force, gold on trial, red stopped, grey unknown. */
function statusTone(status) {
  if (status === 'ACTIVE') return 'ok';
  if (status === 'TRIAL' || status === 'GRACE') return 'wait';
  if (status === 'EXPIRED' || status === 'REVOKED' || status === 'REFUNDED') return 'bad';
  return 'none';
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

/** A padlock beside the payment note. Drawn inline: the page policy loads nothing. */
function lockIcon() {
  return '<svg class="lock" viewBox="0 0 16 16" width="14" height="14" aria-hidden="true" focusable="false">'
    + '<rect x="3" y="7" width="10" height="7" rx="1.6" fill="currentColor"/>'
    + '<path d="M5.2 7V5.2a2.8 2.8 0 0 1 5.6 0V7" fill="none" stroke="currentColor" stroke-width="1.6"/></svg>';
}

/**
 * The four languages, as links back to this same page.
 *
 * Plain links rather than a select: these pages run no script, and a link works on every browser a
 * customer might be holding. Each keeps the page's own parameters — the device, the pairing code —
 * so changing language never loses the thing the customer came to do.
 */
function languageSwitcher(current, here) {
  const t = copyFor(current);
  const links = ['pt', 'en', 'de', 'it'].map((code) => {
    const query = new URLSearchParams();
    for (const [name, value] of Object.entries(here.params ?? {})) {
      if (value) query.set(name, String(value));
    }
    query.set('lang', code);
    const href = `${here.path}?${query}`;
    const currentMark = code === current ? ' aria-current="page"' : '';
    return `<a href="${escape(href)}" hreflang="${code}" lang="${code}"${currentMark}>${code.toUpperCase()}</a>`;
  }).join('');
  return `<nav class="languages" aria-label="${escape(t.language)}">${links}</nav>`;
}

/**
 * The page frame: the app's own dark palette, so the two do not look like different products.
 *
 * The brand sits in a bar of its own, with the language links opposite, instead of inside each
 * card; every page gets both without repeating them. [here] is the page's path and the parameters a
 * language link must keep — null for a page that should not offer the switch.
 */
function shell(title, language, body, here = null) {
  return `<!doctype html>
<html lang="${escape(language)}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="dark">
<meta name="theme-color" content="#0b0b0d">
<title>${escape(title === 'IPTV BURO' ? title : `${title} · IPTV BURO`)}</title>
<style>
  :root {
    --canvas: #0b0b0d;
    --surface: #141418;
    --surface-2: #101013;
    --text: #f2f0ec;
    --muted: #b3afa7;
    --subtle: #9a968e;
    --border: #2c2a30;
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
  html { -webkit-text-size-adjust: 100%; }
  body {
    margin: 0;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    background:
      radial-gradient(60rem 30rem at 85% -10%, rgba(214, 169, 86, .10), transparent 70%),
      radial-gradient(40rem 24rem at -10% 110%, rgba(214, 169, 86, .05), transparent 70%),
      var(--canvas);
    color: var(--text);
    font: 16px/1.55 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
  }
  a { color: var(--gold-strong); }

  /* The bar. */
  .topbar {
    width: 100%;
    max-width: 1120px;
    margin: 0 auto;
    padding: 18px 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
  }
  .brand { display: inline-flex; align-items: center; gap: 10px; text-decoration: none; }
  .brand span { font-size: 15px; letter-spacing: .14em; color: var(--text); font-weight: 500; }
  .brand b { color: var(--gold); font-weight: 800; }
  .languages { display: flex; gap: 2px; padding: 3px; border: 1px solid var(--border); border-radius: 999px; background: rgba(20, 20, 24, .7); }
  .languages a {
    min-width: 38px; min-height: 30px; display: grid; place-items: center;
    padding: 0 8px; border-radius: 999px;
    color: var(--muted); font-size: 12px; font-weight: 700; letter-spacing: .06em; text-decoration: none;
  }
  .languages a:hover { color: var(--text); }
  .languages a[aria-current] { background: var(--gold); color: #1a1509; }

  /* The page. */
  .page { width: 100%; max-width: 1120px; margin: 0 auto; padding: 8px 24px 48px; flex: 1; }
  .page.narrow { max-width: 560px; display: flex; flex-direction: column; justify-content: center; }
  .panel, .split {
    background: linear-gradient(180deg, rgba(31, 31, 36, .55), rgba(20, 20, 24, .92) 160px), var(--surface);
    border: 1px solid var(--border);
    border-radius: 20px;
    box-shadow: 0 30px 80px rgba(0, 0, 0, .35), inset 0 1px 0 rgba(255, 255, 255, .04);
  }
  .panel { padding: 36px; }
  .split { display: grid; grid-template-columns: minmax(0, 1.05fr) minmax(0, .95fr); overflow: hidden; }
  .primary { padding: 40px; }
  .secondary { padding: 40px; background: var(--surface-2); border-left: 1px solid var(--border); }

  .kicker { margin: 0 0 10px; color: var(--gold-strong); font-size: 12px; font-weight: 800; letter-spacing: .14em; }
  h1 { margin: 0 0 8px; font-size: clamp(26px, 3.2vw, 34px); line-height: 1.15; letter-spacing: -.02em; }
  .lead { margin: 0 0 24px; color: var(--muted); }
  .small { margin: 2px 0 0; color: var(--subtle); font-size: 13.5px; }

  .state-icon {
    width: 52px; height: 52px; margin-bottom: 18px; border-radius: 50%;
    display: grid; place-items: center; font-size: 24px; font-weight: 800;
  }
  .state-icon.ok { background: rgba(111, 191, 115, .14); color: var(--ok); box-shadow: inset 0 0 0 1px rgba(111, 191, 115, .4); }
  .state-icon.wait { background: rgba(214, 169, 86, .14); color: var(--gold-strong); box-shadow: inset 0 0 0 1px rgba(214, 169, 86, .4); }
  .state-icon.bad { background: rgba(224, 133, 133, .14); color: var(--bad); box-shadow: inset 0 0 0 1px rgba(224, 133, 133, .4); }

  .device { background: var(--surface-2); border: 1px solid var(--border); border-radius: 14px; padding: 10px 16px; margin-bottom: 24px; }
  .row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 7px 0; font-size: 14px; }
  .row + .row { border-top: 1px solid var(--border); }
  .row span { color: var(--subtle); }
  code {
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    font-size: 15px;
    letter-spacing: .06em;
    /* Selectable and easy to copy: this is the value a customer may need to read out. */
    user-select: all;
  }
  .pill { padding: 3px 10px; border-radius: 999px; font-size: 12.5px; font-weight: 700; }
  .pill.ok { background: rgba(111, 191, 115, .14); color: var(--ok); }
  .pill.wait { background: rgba(214, 169, 86, .14); color: var(--gold-strong); }
  .pill.bad { background: rgba(224, 133, 133, .14); color: var(--bad); }
  .pill.none { background: rgba(154, 150, 142, .14); color: var(--muted); }

  .price-block { margin-bottom: 20px; }
  .price { font-size: 44px; font-weight: 800; line-height: 1.1; color: var(--gold-strong); letter-spacing: -.02em; }
  .period { font-size: 16px; font-weight: 500; color: var(--muted); letter-spacing: 0; }

  button, .cta {
    width: 100%;
    min-height: 52px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 14px 18px;
    font: inherit;
    font-weight: 800;
    color: #1a1509;
    background: linear-gradient(180deg, var(--gold-strong), var(--gold));
    border: 0;
    border-radius: 12px;
    box-shadow: 0 10px 30px rgba(214, 169, 86, .18);
    cursor: pointer;
    text-decoration: none;
  }
  button:hover, .cta:hover { filter: brightness(1.07); }
  .cta.secondary { width: auto; border: 1px solid var(--line); background: rgba(23, 23, 27, .82); color: var(--text); box-shadow: none; }
  a:focus-visible, button:focus-visible, input:focus-visible {
    outline: 3px solid var(--gold-strong);
    outline-offset: 3px;
  }
  .secure { display: flex; gap: 8px; align-items: flex-start; justify-content: center; margin: 14px 0 0; font-size: 12.5px; color: var(--subtle); text-align: left; }
  .lock { flex: none; margin-top: 2px; color: var(--ok); }
  .alt-link { margin: 22px 0 0; padding-top: 18px; border-top: 1px solid var(--border); font-size: 14px; text-align: center; }
  .alt-link a { text-decoration: none; font-weight: 600; }
  .alt-link a:hover { text-decoration: underline; }

  .notice { border-radius: 12px; padding: 12px 14px; font-size: 14px; margin: 0 0 20px; }
  .notice.ok { background: rgba(111, 191, 115, .1); border: 1px solid rgba(111, 191, 115, .35); color: #bfe3c1; }
  .notice.bad { background: #2a1618; border: 1px solid #4a2428; color: #f0b3b3; }

  .notes { margin-top: 28px; padding-top: 20px; border-top: 1px solid var(--border); }
  .notes h2, .how h2 {
    font-size: 12px; letter-spacing: .12em; text-transform: uppercase;
    color: var(--subtle); font-weight: 700; margin: 0 0 14px;
  }
  .notes p { margin: 0 0 12px; font-size: 13.5px; color: var(--subtle); }
  .disclaimer { color: var(--muted); }
  .muted { color: var(--muted); }

  /* The four steps. */
  .how { scroll-margin-top: 20px; }
  .steps {
    list-style: none; margin: 0; padding: 0;
    display: grid; gap: 22px 18px;
    /* Two across on a phone and in the side column, four on the wide front page: the minimum width decides. */
    grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  }
  .steps li { margin: 0; }
  .steps h3 { font-size: 14px; margin: 10px 0 3px; }
  .steps p { font-size: 12.5px; color: var(--subtle); margin: 0; line-height: 1.45; }
  .art-frame { position: relative; background: var(--surface-2); border: 1px solid var(--line); border-radius: 12px; padding: 10px; }
  .secondary .art-frame { background: #17171b; }
  .art { display: block; width: 100%; height: auto; }
  .step-number {
    position: absolute; top: -8px; left: -8px;
    width: 24px; height: 24px; border-radius: 50%;
    background: var(--gold); color: #1a1509;
    font-size: 12px; font-weight: 800;
    display: grid; place-items: center;
  }

  /* Forms. */
  .stack { display: grid; gap: 16px; }
  .stack label { display: grid; gap: 7px; }
  .stack label > span { font-size: 13px; font-weight: 600; color: var(--muted); }
  .stack small { font-size: 12.5px; color: var(--subtle); line-height: 1.45; }
  .stack input {
    font: inherit;
    font-family: ui-monospace, "Cascadia Code", Consolas, monospace;
    font-size: 17px;
    letter-spacing: .1em;
    text-transform: uppercase;
    padding: 14px 16px;
    background: var(--surface-2);
    border: 1px solid var(--line);
    border-radius: 12px;
    color: var(--text);
  }
  .stack input.free { text-transform: none; letter-spacing: 0; font-size: 15px; }
  .stack input.digits { letter-spacing: .4em; font-size: 22px; }
  .stack input::placeholder { color: #5f5c66; }
  .stack input:focus { border-color: var(--gold); }
  .stack button { margin-top: 4px; }

  /* The front page. */
  .card.landing {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 20px;
    overflow: hidden;
    box-shadow: 0 28px 80px rgba(0, 0, 0, .38);
  }
  .hero { min-height: 500px; position: relative; display: flex; align-items: center; isolation: isolate; overflow: hidden; background: #0f1013; }
  .hero::after {
    content: '';
    position: absolute;
    inset: 0;
    z-index: -1;
    background: linear-gradient(90deg, #101014 0%, rgba(16, 16, 20, .96) 38%, rgba(16, 16, 20, .32) 72%, rgba(16, 16, 20, .08));
  }
  .hero-copy { width: min(54%, 600px); padding: 52px; }
  .hero-copy h1 { max-width: 12ch; margin-bottom: 14px; font-size: clamp(38px, 5vw, 62px); letter-spacing: -.035em; }
  .hero-copy .lead { max-width: 48ch; font-size: clamp(17px, 2vw, 20px); }
  .hero-art { position: absolute; inset: 0; z-index: -2; }
  .hero-art img { width: 100%; height: 100%; display: block; object-fit: cover; object-position: center right; }
  .eyebrow { margin: 0 0 16px; color: var(--gold-strong); font-size: 12px; font-weight: 800; letter-spacing: .14em; }
  .actions { display: flex; flex-wrap: wrap; gap: 12px; margin: 26px 0 20px; }
  .actions .cta { width: auto; }
  .platform-note { max-width: 48ch; margin: 0; color: var(--subtle); font-size: 13px; }
  .trial { color: var(--gold); font-size: 14px; font-weight: 600; margin: 0 0 8px; }
  .proof { display: grid; grid-template-columns: repeat(3, 1fr); border-block: 1px solid var(--border); background: #111115; }
  .proof div { min-height: 104px; display: grid; place-items: center; align-content: center; gap: 3px; padding: 18px; text-align: center; }
  .proof div + div { border-left: 1px solid var(--border); }
  .proof strong { color: var(--gold-strong); font-size: 24px; line-height: 1; }
  .proof span { color: var(--muted); font-size: 13px; }
  .landing-body { padding: 32px 48px 42px; }

  @media (max-width: 860px) {
    .split { grid-template-columns: 1fr; }
    .secondary { border-left: 0; border-top: 1px solid var(--border); }
  }
  @media (max-width: 560px) {
    .topbar { padding: 14px 16px; }
    .brand span { font-size: 13px; }
    .page { padding: 4px 12px 32px; }
    .panel, .primary, .secondary { padding: 24px 20px; }
    .price { font-size: 38px; }
    .hero { min-height: 610px; align-items: flex-end; }
    .hero::after { background: linear-gradient(0deg, #101014 0%, rgba(16, 16, 20, .96) 48%, rgba(16, 16, 20, .34) 100%); }
    .hero-art img { object-position: 68% center; }
    .hero-copy { width: 100%; padding: 30px 24px; }
    .hero-copy h1 { font-size: 42px; }
    .actions { display: grid; }
    .proof { grid-template-columns: 1fr; }
    .proof div { min-height: 84px; }
    .proof div + div { border-left: 0; border-top: 1px solid var(--border); }
    .landing-body { padding: 24px 20px 28px; }
  }
  @media (prefers-reduced-motion: reduce) {
    *, *::before, *::after { scroll-behavior: auto !important; transition-duration: .01ms !important; animation-duration: .01ms !important; }
  }
</style>
</head>
<body>
<header class="topbar">
  <a class="brand" href="/?lang=${escape(language)}">${buroMark(30)}<span>IPTV <b>BURO</b></span></a>
  ${here ? languageSwitcher(language, here) : ''}
</header>
${body}
</body>
</html>`;
}
