(() => {
  'use strict';

  const WORKER_ORIGIN = 'https://iptvburo.iptvburo.workers.dev';
  const DEVICE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const DEVICE_PATTERN = new RegExp(`^[${DEVICE_ALPHABET}]{4}(?:-[${DEVICE_ALPHABET}]{4}){2}$`);
  const languageNames = { pt: 'pt-BR', en: 'en', de: 'de', it: 'it' };

  const pt = {
    skip: 'Pular para o conteúdo', menu: 'Abrir menu', language: 'Idioma',
    navDemo: 'Demonstração', navFeatures: 'Funções', navPlatforms: 'Plataformas', navPricing: 'Ativar', navFaq: 'Perguntas', activate: 'Ativar dispositivo',
    heroEyebrow: 'PLAYER PREMIUM · SUA FONTE, SUA BIBLIOTECA', heroTitle: 'Tudo o que você assiste.<br>Finalmente no lugar certo.',
    heroLead: 'Organize TV ao vivo, filmes e séries das fontes autorizadas que você já possui — numa experiência rápida, privada e cinematográfica.',
    watchDemo: 'Ver como funciona', startTrial: 'Começar os 7 dias grátis', factTrial: 'dias grátis', factTerm: 'anos por pagamento', factContent: 'conteúdo fornecido', explore: 'Explore',
    trustIntro: 'Feito para as fontes que você já usa', trustPrivate: 'Credenciais protegidas',
    demoKicker: 'DEMONSTRAÇÃO INTERATIVA', demoTitle: 'Seis telas. Uma biblioteca.', demoLead: 'Explore as funções atuais e conheça a próxima grande experiência mobile do IPTV BURO.',
    demoHome: 'Início', demoHomeSub: 'Sua noite, pronta', demoLive: 'Ao vivo', demoLiveSub: 'Agora e a seguir', demoMovies: 'Filmes', demoMoviesSub: 'Capas e detalhes', demoSeries: 'Séries', demoSeriesSub: 'Temporadas organizadas', demoMultiSub: 'Até quatro canais', demoOffline: 'Downloads', demoOfflineSub: 'Próximo no mobile', pauseTour: 'Pausar apresentação', resumeTour: 'Retomar apresentação', demoConnected: 'Fonte conectada', play: 'Assistir', details: 'Detalhes', downloadAction: 'Baixar', offlineLibrary: 'Downloads',
    motionKicker: 'BURO NOCTURNE', motionTitle: 'Uma interface que desacelera o ruído.', motionLead: 'Movimento suave, foco claro e espaço para o que importa. No sofá, no computador ou numa tela menor.', motionOne: 'Foco visível para controle remoto, teclado e mouse', motionTwo: 'Layouts adaptativos para TV, desktop e mobile', motionThree: 'Português, inglês, alemão e italiano', motionCaption: 'Experiência visual IPTV BURO',
    featuresKicker: 'FUNÇÕES REAIS', featuresTitle: 'Menos configuração.<br>Mais descoberta.', featuresLead: 'O IPTV BURO não é uma lista de canais com outra pintura. Ele transforma a sua fonte numa biblioteca navegável e separa cada experiência com clareza.',
    organizeKicker: 'ORGANIZAÇÃO EDITORIAL', organizeTitle: 'A lista vira biblioteca.', organizeBody: 'Capas, categorias, busca, detalhes, elenco, favoritos e continuidade — sem misturar TV ao vivo com filmes e séries.', search: 'Busca', favorites: 'Favoritos', continue: 'Continuar assistindo', available: 'Disponível', preview: 'Prévia',
    liveTitle: 'TV ao vivo com contexto', liveBody: 'Agora e a seguir, troca rápida de canal e Multiview de até quatro canais no Windows.', profilesTitle: 'Perfis e Kids', profilesBody: 'Até cinco perfis, favoritos separados e proteção conservadora para conteúdo adulto.', continueTitle: 'Continuidade real', continueBody: 'Pare num episódio ou filme e retome do ponto certo no mesmo perfil.', privateTitle: 'Privado por desenho', privateBody: 'Credenciais protegidas pelo sistema e URLs autenticadas resolvidas apenas em memória.', formatsTitle: 'Sua fonte, sem prisão', formatsBody: 'M3U/M3U8, Xtream-compatible e fundação Stalker/Ministra numa única experiência.', updatesTitle: 'Evolui com você', updatesBody: 'Atualizações verificadas, quatro idiomas e uma arquitetura preparada para novas plataformas.',
    offlineStatus: 'EM PREPARAÇÃO PARA MOBILE', offlineKicker: 'BURO OFFLINE VAULT', offlineTitle: 'A internet pode falhar.<br>Sua sessão não precisa parar.', offlineLead: 'Baixe antecipadamente filmes e episódios elegíveis e assista depois dentro do IPTV BURO — ideal para viagens, sinal fraco ou uma conexão instável.', offlineOneTitle: 'Sem travamentos', offlineOneBody: 'O arquivo local não depende da velocidade da internet durante o filme.', offlineTwoTitle: 'Tudo dentro do app', offlineTwoBody: 'Downloads, capas e progresso ficam organizados na biblioteca privada do IPTV BURO.', offlineThreeTitle: 'Feito para o mobile', offlineThreeBody: 'A experiência está sendo preparada para chegar ao aplicativo móvel após os testes de segurança e reprodução.', offlineDisclaimer: 'Recurso ainda oculto nas versões públicas. Será liberado apenas para mídia elegível, sem baixar TV ao vivo nem contornar proteção.',
    screensKicker: 'UMA LINGUAGEM, VÁRIAS TELAS', screensTitle: 'A mesma calma,<br>onde você estiver.', screensBody: 'A experiência adapta navegação, densidade e controles sem perder a identidade do produto.',
    platformsKicker: 'PLATAFORMAS', platformsTitle: 'Comece onde já funciona.', platformsLead: 'As versões atuais são prévias. Novas plataformas só aparecem como disponíveis depois de validação real.', windowsBody: 'Player integrado, catálogo completo, Multiview e atualizações verificadas.', androidBody: 'Aplicativo adaptativo com Media3, perfis, catálogo e reprodução. Offline Vault em preparação para o mobile.', previewAvailable: 'Prévia disponível', nextPlatforms: 'Outras plataformas', nextPlatformsBody: 'Fire TV, Apple, Samsung, LG e mais fazem parte do roteiro, não da promessa atual.', planned: 'Planejado', seeReleases: 'Ver versões disponíveis no GitHub',
    pricingKicker: 'ATIVAÇÃO SEGURA', pricingTitle: 'Teste primeiro.<br>Pague uma vez.', pricingLead: 'Sem assinatura automática. O código liga o pagamento apenas à instalação mostrada no aplicativo.', priceCheckOne: '7 dias grátis, sem cartão', priceCheckTwo: 'Pagamento processado pela Stripe', priceCheckThree: 'Não guardamos dados do cartão', licenseLabel: 'Licença por dispositivo', singlePayment: 'PAGAMENTO ÚNICO', years: 'anos', regionalPrice: 'O valor final e a moeda são confirmados antes do pagamento.', deviceCode: 'Código do dispositivo', codeHelp: 'Encontre este código na tela de ativação do aplicativo.', continuePayment: 'Continuar para pagamento seguro', or: 'ou', haveKey: 'Já tem uma chave de ativação?', haveKeyBody: 'Use a chave sem pagar novamente.', secureNote: 'Conexão segura. A ativação só acontece depois do webhook confirmado pelo servidor.', invalidCode: 'Digite o código de 12 caracteres mostrado no aplicativo.', validCode: 'Código pronto. Você será encaminhado ao pagamento seguro.',
    stepsKicker: 'DO APP À ATIVAÇÃO', stepsTitle: 'Quatro passos. Alguns segundos.', stepOneTitle: 'Abra o IPTV BURO', stepOneBody: 'O teste começa na primeira abertura e o aplicativo cria um código privado para a instalação.', stepTwoTitle: 'Leia o QR code', stepTwoBody: 'A página já abre com o dispositivo preenchido. Também é possível copiar o código.', stepThreeTitle: 'Confirme na Stripe', stepThreeBody: 'Preço, moeda e período são validados pelo servidor, não pelo navegador.', stepFourTitle: 'Volte ao aplicativo', stepFourBody: 'A compra é reconhecida automaticamente após a confirmação segura.',
    faqKicker: 'SEM LETRAS PEQUENAS', faqTitle: 'Perguntas importantes.', faqContentQ: 'O IPTV BURO fornece canais ou filmes?', faqContentA: 'Não. O IPTV BURO é um player e organizador. Você adiciona apenas fontes de mídia que possui autorização para usar.', faqLifetimeQ: 'A licença é vitalícia?', faqLifetimeA: 'Não. O pagamento libera um dispositivo por 730 dias. Isso financia manutenção contínua para mudanças de formatos, provedores e sistemas.', faqDeviceQ: 'Posso usar em vários dispositivos?', faqDeviceA: 'Cada pagamento ativa uma instalação. Perfis são pessoas dentro do mesmo aparelho; não multiplicam a licença.', faqPrivateQ: 'O site recebe minha lista IPTV?', faqPrivateA: 'Não. O portal de pagamento recebe apenas o código público da instalação. A sua lista e as credenciais ficam no aplicativo.', faqOfflineQ: 'Já posso baixar filmes no celular?', faqOfflineA: 'O Offline Vault está em preparação para uma próxima versão mobile. Quando passar pelos testes de armazenamento, segurança e reprodução, os downloads elegíveis ficarão dentro da biblioteca privada do aplicativo.', faqRefundQ: 'O que acontece num reembolso?', faqRefundA: 'Um reembolso integral confirmado revoga a licença ligada à compra. Reembolsos e disputas são processados pelo servidor de forma auditável.',
    closingTitle: 'Sua fonte.<br>Sua tela.<br>Do seu jeito.', footerBody: 'Player e organizador para fontes de mídia autorizadas pelo usuário.', footerProduct: 'Produto', footerActivation: 'Ativação', footerProject: 'Projeto', useKey: 'Usar uma chave', releases: 'Versões ↗', footerLegal: 'Não vende, hospeda ou fornece conteúdo, listas ou credenciais.',
    videoPause: 'Pausar vídeo', videoPlay: 'Reproduzir vídeo'
  };

  const en = {
    ...pt,
    skip: 'Skip to content', menu: 'Open menu', language: 'Language', navDemo: 'Demo', navFeatures: 'Features', navPlatforms: 'Platforms', navPricing: 'Activate', navFaq: 'Questions', activate: 'Activate device',
    heroEyebrow: 'PREMIUM PLAYER · YOUR SOURCE, YOUR LIBRARY', heroTitle: 'Everything you watch.<br>Finally in the right place.', heroLead: 'Organize live TV, movies, and series from media sources you are authorized to use — in a fast, private, cinematic experience.', watchDemo: 'See how it works', startTrial: 'Start the 7-day trial', factTrial: 'free days', factTerm: 'years per payment', factContent: 'content supplied', explore: 'Explore', trustIntro: 'Built for the sources you already use', trustPrivate: 'Protected credentials',
    demoKicker: 'INTERACTIVE DEMO', demoTitle: 'Six screens. One library.', demoLead: 'Explore today’s features and discover IPTV BURO’s next major mobile experience.', demoHome: 'Home', demoHomeSub: 'Your evening, ready', demoLive: 'Live TV', demoLiveSub: 'Now and next', demoMovies: 'Movies', demoMoviesSub: 'Artwork and details', demoSeries: 'Series', demoSeriesSub: 'Organized seasons', demoMultiSub: 'Up to four channels', demoOffline: 'Downloads', demoOfflineSub: 'Coming to mobile', pauseTour: 'Pause tour', resumeTour: 'Resume tour', demoConnected: 'Source connected', play: 'Play', details: 'Details', downloadAction: 'Download', offlineLibrary: 'Downloads',
    motionTitle: 'An interface that turns down the noise.', motionLead: 'Gentle motion, clear focus, and room for what matters. On the sofa, at your computer, or on a smaller screen.', motionOne: 'Visible focus for remote, keyboard, and mouse', motionTwo: 'Adaptive layouts for TV, desktop, and mobile', motionThree: 'Portuguese, English, German, and Italian', motionCaption: 'IPTV BURO visual experience',
    featuresKicker: 'REAL FEATURES', featuresTitle: 'Less setup.<br>More discovery.', featuresLead: 'IPTV BURO is not a channel list with a new coat of paint. It turns your source into a browsable library and keeps every experience clear.', organizeKicker: 'EDITORIAL ORGANIZATION', organizeTitle: 'The list becomes a library.', organizeBody: 'Artwork, categories, search, details, cast, favorites, and continuity — without mixing live TV with movies and series.', search: 'Search', favorites: 'Favorites', continue: 'Continue watching', available: 'Available', preview: 'Preview',
    liveTitle: 'Live TV with context', liveBody: 'Now and next, quick channel switching, and up to four-channel Multiview on Windows.', profilesTitle: 'Profiles and Kids', profilesBody: 'Up to five profiles, separate favorites, and conservative protection for adult content.', continueTitle: 'True continuity', continueBody: 'Stop a movie or episode and resume at the right point in the same profile.', privateTitle: 'Private by design', privateBody: 'System-protected credentials and authenticated URLs resolved only in memory.', formatsTitle: 'Your source, no lock-in', formatsBody: 'M3U/M3U8, Xtream-compatible, and a Stalker/Ministra foundation in one experience.', updatesTitle: 'Built to evolve', updatesBody: 'Verified updates, four languages, and an architecture prepared for new platforms.',
    offlineStatus: 'IN PREPARATION FOR MOBILE', offlineKicker: 'BURO OFFLINE VAULT', offlineTitle: 'The internet may fail.<br>Your session does not have to.', offlineLead: 'Download eligible movies and episodes ahead of time, then watch inside IPTV BURO — ideal for travel, weak signal, or an unstable connection.', offlineOneTitle: 'No buffering', offlineOneBody: 'A local copy does not depend on internet speed while the movie is playing.', offlineTwoTitle: 'Everything inside the app', offlineTwoBody: 'Downloads, artwork, and progress stay organized in IPTV BURO’s private library.', offlineThreeTitle: 'Designed for mobile', offlineThreeBody: 'The experience is being prepared for the mobile app after security and playback validation.', offlineDisclaimer: 'Still hidden in public releases. It will ship only for eligible media, never live TV, and never by bypassing protection.',
    screensKicker: 'ONE LANGUAGE, MANY SCREENS', screensTitle: 'The same calm,<br>wherever you are.', screensBody: 'The experience adapts navigation, density, and controls without losing the product identity.', platformsTitle: 'Start where it already works.', platformsLead: 'Current versions are previews. New platforms are only marked available after real validation.', windowsBody: 'Integrated player, full catalog, Multiview, and verified updates.', androidBody: 'Adaptive Media3 app with profiles, catalog, and playback. Offline Vault is in preparation for mobile.', previewAvailable: 'Preview available', nextPlatforms: 'Other platforms', nextPlatformsBody: 'Fire TV, Apple, Samsung, LG, and more are on the roadmap, not in the current promise.', planned: 'Planned', seeReleases: 'See available releases on GitHub',
    pricingKicker: 'SECURE ACTIVATION', pricingTitle: 'Try first.<br>Pay once.', pricingLead: 'No automatic subscription. The code links payment only to the installation shown in the app.', priceCheckOne: '7 free days, no card required', priceCheckTwo: 'Payment processed by Stripe', priceCheckThree: 'We do not store card details', licenseLabel: 'Per-device license', singlePayment: 'ONE-TIME PAYMENT', years: 'years', regionalPrice: 'The final amount and currency are confirmed before payment.', deviceCode: 'Device code', codeHelp: 'Find this code on the app activation screen.', continuePayment: 'Continue to secure payment', or: 'or', haveKey: 'Already have an activation key?', haveKeyBody: 'Use the key without paying again.', secureNote: 'Secure connection. Activation happens only after the server confirms the webhook.', invalidCode: 'Enter the 12-character code shown in the app.', validCode: 'Code ready. You will be sent to secure payment.',
    stepsKicker: 'FROM APP TO ACTIVATION', stepsTitle: 'Four steps. A few seconds.', stepOneTitle: 'Open IPTV BURO', stepOneBody: 'The trial starts on first launch and the app creates a private code for this installation.', stepTwoTitle: 'Scan the QR code', stepTwoBody: 'The page opens with the device filled in. You can also copy the code.', stepThreeTitle: 'Confirm with Stripe', stepThreeBody: 'Price, currency, and term are validated by the server, not the browser.', stepFourTitle: 'Return to the app', stepFourBody: 'The purchase is recognized automatically after secure confirmation.',
    faqKicker: 'NO FINE PRINT', faqTitle: 'Important questions.', faqContentQ: 'Does IPTV BURO provide channels or movies?', faqContentA: 'No. IPTV BURO is a player and organizer. You add only media sources you are authorized to use.', faqLifetimeQ: 'Is the license lifetime?', faqLifetimeA: 'No. One payment unlocks one device for 730 days. This supports ongoing maintenance as formats, providers, and systems change.', faqDeviceQ: 'Can I use it on multiple devices?', faqDeviceA: 'Each payment activates one installation. Profiles are people on the same device; they do not multiply the license.', faqPrivateQ: 'Does the site receive my IPTV list?', faqPrivateA: 'No. The payment portal receives only the public installation code. Your list and credentials stay in the app.', faqOfflineQ: 'Can I download movies on my phone yet?', faqOfflineA: 'Offline Vault is being prepared for an upcoming mobile release. Once storage, security, and playback validation pass, eligible downloads will stay inside the app’s private library.', faqRefundQ: 'What happens after a refund?', faqRefundA: 'A confirmed full refund revokes the license linked to the purchase. Refunds and disputes are processed by the server with an audit trail.', closingTitle: 'Your source.<br>Your screen.<br>Your way.', footerBody: 'Player and organizer for user-authorized media sources.', footerProduct: 'Product', footerActivation: 'Activation', footerProject: 'Project', useKey: 'Use a key', releases: 'Releases ↗', footerLegal: 'Does not sell, host, or provide content, lists, or credentials.', videoPause: 'Pause video', videoPlay: 'Play video'
  };

  const de = {
    ...pt,
    skip: 'Zum Inhalt springen', menu: 'Menü öffnen', language: 'Sprache', navDemo: 'Demo', navFeatures: 'Funktionen', navPlatforms: 'Plattformen', navPricing: 'Aktivieren', navFaq: 'Fragen', activate: 'Gerät aktivieren',
    heroEyebrow: 'PREMIUM-PLAYER · DEINE QUELLE, DEINE MEDIATHEK', heroTitle: 'Alles, was du ansiehst.<br>Endlich am richtigen Ort.', heroLead: 'Organisiere Live-TV, Filme und Serien aus Medienquellen, zu deren Nutzung du berechtigt bist — schnell, privat und filmisch.', watchDemo: 'So funktioniert es', startTrial: '7 Tage kostenlos testen', factTrial: 'Tage kostenlos', factTerm: 'Jahre pro Zahlung', factContent: 'Inhalte enthalten', explore: 'Entdecken', trustIntro: 'Für die Quellen, die du bereits nutzt', trustPrivate: 'Geschützte Zugangsdaten',
    demoKicker: 'INTERAKTIVE DEMO', demoTitle: 'Sechs Ansichten. Eine Mediathek.', demoLead: 'Entdecke die aktuellen Funktionen und das nächste große mobile Erlebnis von IPTV BURO.', demoHome: 'Start', demoHomeSub: 'Dein Abend ist bereit', demoLive: 'Live-TV', demoLiveSub: 'Jetzt und danach', demoMovies: 'Filme', demoMoviesSub: 'Cover und Details', demoSeries: 'Serien', demoSeriesSub: 'Geordnete Staffeln', demoMultiSub: 'Bis zu vier Sender', demoOffline: 'Downloads', demoOfflineSub: 'Demnächst mobil', pauseTour: 'Tour pausieren', resumeTour: 'Tour fortsetzen', demoConnected: 'Quelle verbunden', play: 'Abspielen', details: 'Details', downloadAction: 'Herunterladen', offlineLibrary: 'Downloads',
    motionTitle: 'Eine Oberfläche, die den Lärm leiser dreht.', motionLead: 'Sanfte Bewegung, klarer Fokus und Raum für das Wesentliche. Auf dem Sofa, am Computer oder auf einem kleineren Bildschirm.', motionOne: 'Sichtbarer Fokus für Fernbedienung, Tastatur und Maus', motionTwo: 'Adaptive Layouts für TV, Desktop und Mobilgeräte', motionThree: 'Portugiesisch, Englisch, Deutsch und Italienisch', motionCaption: 'Das visuelle Erlebnis von IPTV BURO',
    featuresKicker: 'ECHTE FUNKTIONEN', featuresTitle: 'Weniger Einrichtung.<br>Mehr entdecken.', featuresLead: 'IPTV BURO ist keine neu lackierte Senderliste. Es verwandelt deine Quelle in eine navigierbare Mediathek und trennt die Bereiche übersichtlich.', organizeKicker: 'REDAKTIONELLE ORDNUNG', organizeTitle: 'Aus der Liste wird eine Mediathek.', organizeBody: 'Cover, Kategorien, Suche, Details, Besetzung, Favoriten und Wiedereinstieg — ohne Live-TV, Filme und Serien zu vermischen.', search: 'Suche', favorites: 'Favoriten', continue: 'Weiterschauen', available: 'Verfügbar', preview: 'Vorschau',
    liveTitle: 'Live-TV mit Kontext', liveBody: 'Jetzt und danach, schneller Senderwechsel und Multiview mit bis zu vier Sendern unter Windows.', profilesTitle: 'Profile und Kids', profilesBody: 'Bis zu fünf Profile, getrennte Favoriten und vorsichtiger Schutz vor Erwachsenen-Inhalten.', continueTitle: 'Nahtlos weiterschauen', continueBody: 'Film oder Folge stoppen und im selben Profil an der richtigen Stelle fortsetzen.', privateTitle: 'Privat entwickelt', privateBody: 'Vom System geschützte Zugangsdaten; authentifizierte URLs werden nur im Speicher aufgelöst.', formatsTitle: 'Deine Quelle, keine Bindung', formatsBody: 'M3U/M3U8, Xtream-kompatibel und eine Stalker/Ministra-Basis in einem Erlebnis.', updatesTitle: 'Bereit für Weiterentwicklung', updatesBody: 'Verifizierte Updates, vier Sprachen und eine Architektur für weitere Plattformen.',
    offlineStatus: 'FÜR MOBILE IN VORBEREITUNG', offlineKicker: 'BURO OFFLINE VAULT', offlineTitle: 'Das Internet darf ausfallen.<br>Dein Film muss nicht stoppen.', offlineLead: 'Lade geeignete Filme und Folgen vorher herunter und sieh sie später direkt in IPTV BURO — ideal für Reisen, schwaches Signal oder instabile Verbindungen.', offlineOneTitle: 'Ohne Unterbrechungen', offlineOneBody: 'Eine lokale Kopie hängt während des Films nicht von der Internetgeschwindigkeit ab.', offlineTwoTitle: 'Alles innerhalb der App', offlineTwoBody: 'Downloads, Cover und Fortschritt bleiben in der privaten IPTV-BURO-Mediathek geordnet.', offlineThreeTitle: 'Für mobile Geräte gedacht', offlineThreeBody: 'Nach Sicherheits- und Wiedergabetests wird das Erlebnis für die mobile App vorbereitet.', offlineDisclaimer: 'In öffentlichen Versionen noch ausgeblendet. Freigabe nur für geeignete Medien, nie für Live-TV und nie durch Umgehung von Schutz.',
    screensKicker: 'EINE SPRACHE, VIELE BILDSCHIRME', screensTitle: 'Dieselbe Ruhe,<br>wo immer du bist.', screensBody: 'Navigation, Dichte und Steuerung passen sich an, ohne die Identität des Produkts zu verlieren.', platformsTitle: 'Starte dort, wo es bereits funktioniert.', platformsLead: 'Die aktuellen Versionen sind Vorschauen. Neue Plattformen gelten erst nach echter Validierung als verfügbar.', windowsBody: 'Integrierter Player, vollständiger Katalog, Multiview und verifizierte Updates.', androidBody: 'Adaptive Media3-App mit Profilen, Katalog und Wiedergabe. Offline Vault wird für Mobilgeräte vorbereitet.', previewAvailable: 'Vorschau verfügbar', nextPlatforms: 'Weitere Plattformen', nextPlatformsBody: 'Fire TV, Apple, Samsung, LG und weitere stehen auf der Roadmap, sind aber noch kein aktuelles Versprechen.', planned: 'Geplant', seeReleases: 'Verfügbare Versionen auf GitHub ansehen',
    pricingKicker: 'SICHERE AKTIVIERUNG', pricingTitle: 'Erst testen.<br>Einmal zahlen.', pricingLead: 'Kein automatisches Abo. Der Code verbindet die Zahlung nur mit der im App angezeigten Installation.', priceCheckOne: '7 Tage kostenlos, ohne Karte', priceCheckTwo: 'Zahlungsabwicklung durch Stripe', priceCheckThree: 'Wir speichern keine Kartendaten', licenseLabel: 'Lizenz pro Gerät', singlePayment: 'EINMALIGE ZAHLUNG', years: 'Jahre', regionalPrice: 'Endbetrag und Währung werden vor der Zahlung bestätigt.', deviceCode: 'Gerätecode', codeHelp: 'Diesen Code findest du im Aktivierungsbildschirm der App.', continuePayment: 'Weiter zur sicheren Zahlung', or: 'oder', haveKey: 'Du hast bereits einen Aktivierungsschlüssel?', haveKeyBody: 'Nutze den Schlüssel ohne erneut zu zahlen.', secureNote: 'Sichere Verbindung. Die Aktivierung erfolgt erst nach der Webhook-Bestätigung durch den Server.', invalidCode: 'Gib den 12-stelligen Code aus der App ein.', validCode: 'Code ist bereit. Du wirst zur sicheren Zahlung weitergeleitet.',
    stepsKicker: 'VON DER APP ZUR AKTIVIERUNG', stepsTitle: 'Vier Schritte. Wenige Sekunden.', stepOneTitle: 'IPTV BURO öffnen', stepOneBody: 'Der Test beginnt beim ersten Start und die App erstellt einen privaten Code für diese Installation.', stepTwoTitle: 'QR-Code scannen', stepTwoBody: 'Die Seite öffnet sich mit ausgefülltem Gerätecode. Du kannst ihn auch kopieren.', stepThreeTitle: 'Bei Stripe bestätigen', stepThreeBody: 'Preis, Währung und Laufzeit werden vom Server geprüft, nicht vom Browser.', stepFourTitle: 'Zur App zurückkehren', stepFourBody: 'Der Kauf wird nach der sicheren Bestätigung automatisch erkannt.',
    faqKicker: 'KEIN KLEINGEDRUCKTES', faqTitle: 'Wichtige Fragen.', faqContentQ: 'Stellt IPTV BURO Sender oder Filme bereit?', faqContentA: 'Nein. IPTV BURO ist ein Player und Organizer. Du fügst nur Medienquellen hinzu, zu deren Nutzung du berechtigt bist.', faqLifetimeQ: 'Gilt die Lizenz lebenslang?', faqLifetimeA: 'Nein. Eine Zahlung schaltet ein Gerät für 730 Tage frei und finanziert die laufende Pflege bei Änderungen an Formaten, Anbietern und Systemen.', faqDeviceQ: 'Kann ich mehrere Geräte nutzen?', faqDeviceA: 'Jede Zahlung aktiviert eine Installation. Profile sind Personen auf demselben Gerät; sie vervielfachen die Lizenz nicht.', faqPrivateQ: 'Erhält die Website meine IPTV-Liste?', faqPrivateA: 'Nein. Das Zahlungsportal erhält nur den öffentlichen Installationscode. Liste und Zugangsdaten bleiben in der App.', faqOfflineQ: 'Kann ich Filme schon auf dem Handy herunterladen?', faqOfflineA: 'Offline Vault wird für eine kommende mobile Version vorbereitet. Nach erfolgreicher Speicher-, Sicherheits- und Wiedergabeprüfung bleiben geeignete Downloads in der privaten App-Mediathek.', faqRefundQ: 'Was geschieht bei einer Erstattung?', faqRefundA: 'Eine bestätigte vollständige Erstattung widerruft die mit dem Kauf verknüpfte Lizenz. Erstattungen und Streitfälle verarbeitet der Server nachvollziehbar.', closingTitle: 'Deine Quelle.<br>Dein Bildschirm.<br>Deine Art.', footerBody: 'Player und Organizer für vom Benutzer autorisierte Medienquellen.', footerProduct: 'Produkt', footerActivation: 'Aktivierung', footerProject: 'Projekt', useKey: 'Schlüssel verwenden', releases: 'Versionen ↗', footerLegal: 'Verkauft, hostet oder liefert keine Inhalte, Listen oder Zugangsdaten.', videoPause: 'Video pausieren', videoPlay: 'Video abspielen'
  };

  const it = {
    ...pt,
    skip: 'Vai al contenuto', menu: 'Apri menu', language: 'Lingua', navDemo: 'Demo', navFeatures: 'Funzioni', navPlatforms: 'Piattaforme', navPricing: 'Attiva', navFaq: 'Domande', activate: 'Attiva dispositivo',
    heroEyebrow: 'PLAYER PREMIUM · LA TUA FONTE, LA TUA LIBRERIA', heroTitle: 'Tutto ciò che guardi.<br>Finalmente al posto giusto.', heroLead: 'Organizza TV in diretta, film e serie dalle fonti multimediali che sei autorizzato a usare — in un’esperienza veloce, privata e cinematografica.', watchDemo: 'Scopri come funziona', startTrial: 'Inizia i 7 giorni gratuiti', factTrial: 'giorni gratuiti', factTerm: 'anni per pagamento', factContent: 'contenuti forniti', explore: 'Esplora', trustIntro: 'Pensato per le fonti che già usi', trustPrivate: 'Credenziali protette',
    demoKicker: 'DEMO INTERATTIVA', demoTitle: 'Sei schermate. Una libreria.', demoLead: 'Esplora le funzioni attuali e scopri la prossima grande esperienza mobile di IPTV BURO.', demoHome: 'Home', demoHomeSub: 'La tua serata è pronta', demoLive: 'In diretta', demoLiveSub: 'Ora e dopo', demoMovies: 'Film', demoMoviesSub: 'Copertine e dettagli', demoSeries: 'Serie', demoSeriesSub: 'Stagioni organizzate', demoMultiSub: 'Fino a quattro canali', demoOffline: 'Download', demoOfflineSub: 'In arrivo su mobile', pauseTour: 'Metti in pausa il tour', resumeTour: 'Riprendi il tour', demoConnected: 'Fonte collegata', play: 'Riproduci', details: 'Dettagli', downloadAction: 'Scarica', offlineLibrary: 'Download',
    motionTitle: 'Un’interfaccia che abbassa il rumore.', motionLead: 'Movimenti delicati, messa a fuoco chiara e spazio per ciò che conta. Sul divano, al computer o su uno schermo più piccolo.', motionOne: 'Focus visibile per telecomando, tastiera e mouse', motionTwo: 'Layout adattivi per TV, desktop e mobile', motionThree: 'Portoghese, inglese, tedesco e italiano', motionCaption: 'Esperienza visiva IPTV BURO',
    featuresKicker: 'FUNZIONI REALI', featuresTitle: 'Meno configurazione.<br>Più scoperta.', featuresLead: 'IPTV BURO non è un elenco di canali ridipinto. Trasforma la tua fonte in una libreria navigabile e separa ogni esperienza con chiarezza.', organizeKicker: 'ORGANIZZAZIONE EDITORIALE', organizeTitle: 'L’elenco diventa una libreria.', organizeBody: 'Copertine, categorie, ricerca, dettagli, cast, preferiti e continuità — senza mescolare TV in diretta, film e serie.', search: 'Ricerca', favorites: 'Preferiti', continue: 'Continua a guardare', available: 'Disponibile', preview: 'Anteprima',
    liveTitle: 'TV in diretta con contesto', liveBody: 'Ora e dopo, cambio rapido del canale e Multiview fino a quattro canali su Windows.', profilesTitle: 'Profili e Kids', profilesBody: 'Fino a cinque profili, preferiti separati e protezione prudente per i contenuti per adulti.', continueTitle: 'Continuità reale', continueBody: 'Interrompi un film o un episodio e riprendi dal punto giusto nello stesso profilo.', privateTitle: 'Privato per scelta', privateBody: 'Credenziali protette dal sistema e URL autenticati risolti soltanto in memoria.', formatsTitle: 'La tua fonte, senza vincoli', formatsBody: 'M3U/M3U8, compatibilità Xtream e base Stalker/Ministra in un’unica esperienza.', updatesTitle: 'Cresce con te', updatesBody: 'Aggiornamenti verificati, quattro lingue e un’architettura pronta per nuove piattaforme.',
    offlineStatus: 'IN PREPARAZIONE PER MOBILE', offlineKicker: 'BURO OFFLINE VAULT', offlineTitle: 'Internet può mancare.<br>La visione non deve fermarsi.', offlineLead: 'Scarica prima film ed episodi idonei e guardali poi dentro IPTV BURO — ideale in viaggio, con poco segnale o una connessione instabile.', offlineOneTitle: 'Senza interruzioni', offlineOneBody: 'Una copia locale non dipende dalla velocità di internet durante il film.', offlineTwoTitle: 'Tutto dentro l’app', offlineTwoBody: 'Download, copertine e avanzamento restano organizzati nella libreria privata di IPTV BURO.', offlineThreeTitle: 'Pensato per il mobile', offlineThreeBody: 'L’esperienza viene preparata per l’app mobile dopo i test di sicurezza e riproduzione.', offlineDisclaimer: 'Ancora nascosto nelle versioni pubbliche. Sarà disponibile solo per media idonei, mai per la TV in diretta e senza aggirare protezioni.',
    screensKicker: 'UN LINGUAGGIO, PIÙ SCHERMI', screensTitle: 'La stessa calma,<br>ovunque ti trovi.', screensBody: 'L’esperienza adatta navigazione, densità e controlli senza perdere l’identità del prodotto.', platformsTitle: 'Inizia dove funziona già.', platformsLead: 'Le versioni attuali sono anteprime. Le nuove piattaforme vengono indicate come disponibili solo dopo una vera convalida.', windowsBody: 'Player integrato, catalogo completo, Multiview e aggiornamenti verificati.', androidBody: 'App adattiva Media3 con profili, catalogo e riproduzione. Offline Vault è in preparazione per il mobile.', previewAvailable: 'Anteprima disponibile', nextPlatforms: 'Altre piattaforme', nextPlatformsBody: 'Fire TV, Apple, Samsung, LG e altre sono nella roadmap, non nella promessa attuale.', planned: 'Pianificato', seeReleases: 'Vedi le versioni disponibili su GitHub',
    pricingKicker: 'ATTIVAZIONE SICURA', pricingTitle: 'Prima prova.<br>Poi paga una volta.', pricingLead: 'Nessun abbonamento automatico. Il codice collega il pagamento soltanto all’installazione mostrata nell’app.', priceCheckOne: '7 giorni gratuiti, senza carta', priceCheckTwo: 'Pagamento elaborato da Stripe', priceCheckThree: 'Non conserviamo i dati della carta', licenseLabel: 'Licenza per dispositivo', singlePayment: 'PAGAMENTO UNICO', years: 'anni', regionalPrice: 'Importo finale e valuta sono confermati prima del pagamento.', deviceCode: 'Codice dispositivo', codeHelp: 'Trova questo codice nella schermata di attivazione dell’app.', continuePayment: 'Continua al pagamento sicuro', or: 'oppure', haveKey: 'Hai già una chiave di attivazione?', haveKeyBody: 'Usa la chiave senza pagare di nuovo.', secureNote: 'Connessione sicura. L’attivazione avviene solo dopo la conferma del webhook da parte del server.', invalidCode: 'Inserisci il codice di 12 caratteri mostrato nell’app.', validCode: 'Codice pronto. Verrai indirizzato al pagamento sicuro.',
    stepsKicker: 'DALL’APP ALL’ATTIVAZIONE', stepsTitle: 'Quattro passaggi. Pochi secondi.', stepOneTitle: 'Apri IPTV BURO', stepOneBody: 'La prova inizia al primo avvio e l’app crea un codice privato per questa installazione.', stepTwoTitle: 'Scansiona il QR code', stepTwoBody: 'La pagina si apre con il dispositivo già compilato. Puoi anche copiare il codice.', stepThreeTitle: 'Conferma su Stripe', stepThreeBody: 'Prezzo, valuta e durata vengono convalidati dal server, non dal browser.', stepFourTitle: 'Torna all’app', stepFourBody: 'L’acquisto viene riconosciuto automaticamente dopo la conferma sicura.',
    faqKicker: 'NESSUNA SCRITTA IN PICCOLO', faqTitle: 'Domande importanti.', faqContentQ: 'IPTV BURO fornisce canali o film?', faqContentA: 'No. IPTV BURO è un player e organizzatore. Aggiungi soltanto fonti multimediali che sei autorizzato a usare.', faqLifetimeQ: 'La licenza è a vita?', faqLifetimeA: 'No. Un pagamento sblocca un dispositivo per 730 giorni e sostiene la manutenzione continua quando cambiano formati, provider e sistemi.', faqDeviceQ: 'Posso usarlo su più dispositivi?', faqDeviceA: 'Ogni pagamento attiva una sola installazione. I profili sono persone sullo stesso dispositivo e non moltiplicano la licenza.', faqPrivateQ: 'Il sito riceve la mia lista IPTV?', faqPrivateA: 'No. Il portale di pagamento riceve soltanto il codice pubblico dell’installazione. Lista e credenziali restano nell’app.', faqOfflineQ: 'Posso già scaricare film sul telefono?', faqOfflineA: 'Offline Vault è in preparazione per una prossima versione mobile. Dopo i test di archiviazione, sicurezza e riproduzione, i download idonei resteranno nella libreria privata dell’app.', faqRefundQ: 'Cosa succede con un rimborso?', faqRefundA: 'Un rimborso totale confermato revoca la licenza collegata all’acquisto. Rimborsi e contestazioni sono elaborati dal server in modo verificabile.', closingTitle: 'La tua fonte.<br>Il tuo schermo.<br>A modo tuo.', footerBody: 'Player e organizzatore per fonti multimediali autorizzate dall’utente.', footerProduct: 'Prodotto', footerActivation: 'Attivazione', footerProject: 'Progetto', useKey: 'Usa una chiave', releases: 'Versioni ↗', footerLegal: 'Non vende, ospita o fornisce contenuti, liste o credenziali.', videoPause: 'Metti in pausa il video', videoPlay: 'Riproduci video'
  };

  const translations = { pt, en, de, it };
  const metadata = {
    pt: { title: 'IPTV BURO — Sua biblioteca, finalmente organizada', description: 'Organize TV, filmes e séries das suas fontes autorizadas e conheça o Offline Vault que está chegando ao app móvel.' },
    en: { title: 'IPTV BURO — Your library, finally organized', description: 'Organize live TV, movies, and series from your authorized sources and discover Offline Vault, coming to the mobile app.' },
    de: { title: 'IPTV BURO — Deine Mediathek, endlich organisiert', description: 'Organisiere Live-TV, Filme und Serien aus deinen autorisierten Quellen und entdecke Offline Vault für die kommende mobile App.' },
    it: { title: 'IPTV BURO — La tua libreria, finalmente organizzata', description: 'Organizza TV, film e serie dalle tue fonti autorizzate e scopri Offline Vault, in arrivo nell’app mobile.' }
  };

  const demoCopy = {
    pt: {
      home: ['CONTINUE ASSISTINDO', 'No limite do horizonte', 'Retome exatamente do ponto onde parou, dentro do perfil certo.', ['Em destaque', 'Novas séries', 'Agora ao vivo', 'Kids'], 'assets/category-series.webp'],
      live: ['AGORA AO VIVO', 'Sua programação em contexto', 'Veja o que está passando, o que vem depois e troque de canal sem perder o ritmo.', ['Agora', 'Esportes', 'Notícias', 'A seguir'], 'assets/category-live.webp'],
      movies: ['CINEMA', 'Uma noite para descobrir', 'Explore capas, gêneros, detalhes e elenco sem percorrer uma lista infinita.', ['Destaques', 'Ação', 'Drama', '4K'], 'assets/category-cinema.webp'],
      series: ['PRÓXIMO EPISÓDIO', 'Temporadas no lugar certo', 'Encontre cada episódio, acompanhe o progresso e continue quando quiser.', ['Continuar', 'Novidades', 'Drama', 'Família'], 'assets/category-series.webp'],
      multiview: ['MULTIVIEW · WINDOWS', 'Quatro sinais. Um só foco.', 'Acompanhe até quatro canais compatíveis ao mesmo tempo e escolha o áudio principal.', ['Canal 1', 'Canal 2', 'Canal 3', 'Canal 4'], 'assets/category-sports.webp'],
      offline: ['OFFLINE VAULT · EM PREPARAÇÃO', 'Baixe agora. Assista quando quiser.', 'Filmes e episódios elegíveis ficam dentro do app para uma reprodução local, sem travar quando a internet oscila.', ['Baixando', 'Salvos', 'Para viajar', 'Assistidos'], 'assets/category-cinema.webp']
    },
    en: {
      home: ['CONTINUE WATCHING', 'At the edge of the horizon', 'Resume exactly where you stopped, inside the right profile.', ['Featured', 'New series', 'Live now', 'Kids'], 'assets/category-series.webp'],
      live: ['LIVE NOW', 'Your schedule in context', 'See what is on, what comes next, and switch channels without losing the flow.', ['Now', 'Sports', 'News', 'Up next'], 'assets/category-live.webp'],
      movies: ['CINEMA', 'An evening of discovery', 'Explore artwork, genres, details, and cast without scrolling through an endless list.', ['Featured', 'Action', 'Drama', '4K'], 'assets/category-cinema.webp'],
      series: ['NEXT EPISODE', 'Seasons in the right place', 'Find every episode, track progress, and continue whenever you want.', ['Continue', 'New', 'Drama', 'Family'], 'assets/category-series.webp'],
      multiview: ['MULTIVIEW · WINDOWS', 'Four signals. One focus.', 'Watch up to four compatible channels together and choose the main audio.', ['Channel 1', 'Channel 2', 'Channel 3', 'Channel 4'], 'assets/category-sports.webp'],
      offline: ['OFFLINE VAULT · IN PREPARATION', 'Download now. Watch whenever you want.', 'Eligible movies and episodes stay inside the app for local playback when the connection becomes unstable.', ['Downloading', 'Saved', 'For travel', 'Watched'], 'assets/category-cinema.webp']
    },
    de: {
      home: ['WEITERSCHAUEN', 'Am Rand des Horizonts', 'Setze im richtigen Profil genau dort fort, wo du aufgehört hast.', ['Empfohlen', 'Neue Serien', 'Jetzt live', 'Kids'], 'assets/category-series.webp'],
      live: ['JETZT LIVE', 'Dein Programm im Kontext', 'Sieh, was läuft, was danach kommt, und wechsle ohne Unterbrechung den Sender.', ['Jetzt', 'Sport', 'Nachrichten', 'Danach'], 'assets/category-live.webp'],
      movies: ['KINO', 'Ein Abend zum Entdecken', 'Entdecke Cover, Genres, Details und Besetzung ohne endlose Listen.', ['Highlights', 'Action', 'Drama', '4K'], 'assets/category-cinema.webp'],
      series: ['NÄCHSTE FOLGE', 'Staffeln am richtigen Ort', 'Finde jede Folge, verfolge deinen Fortschritt und mache jederzeit weiter.', ['Weiter', 'Neu', 'Drama', 'Familie'], 'assets/category-series.webp'],
      multiview: ['MULTIVIEW · WINDOWS', 'Vier Signale. Ein Fokus.', 'Verfolge bis zu vier kompatible Sender gleichzeitig und wähle den Hauptton.', ['Sender 1', 'Sender 2', 'Sender 3', 'Sender 4'], 'assets/category-sports.webp'],
      offline: ['OFFLINE VAULT · IN VORBEREITUNG', 'Jetzt laden. Später ansehen.', 'Geeignete Filme und Folgen bleiben für lokale Wiedergabe in der App, auch bei instabiler Verbindung.', ['Lädt', 'Gespeichert', 'Für Reisen', 'Angesehen'], 'assets/category-cinema.webp']
    },
    it: {
      home: ['CONTINUA A GUARDARE', 'Ai confini dell’orizzonte', 'Riprendi esattamente da dove ti eri fermato, nel profilo giusto.', ['In evidenza', 'Nuove serie', 'Ora in diretta', 'Kids'], 'assets/category-series.webp'],
      live: ['ORA IN DIRETTA', 'Il palinsesto nel giusto contesto', 'Guarda cosa c’è ora, cosa viene dopo e cambia canale senza perdere il ritmo.', ['Ora', 'Sport', 'Notizie', 'A seguire'], 'assets/category-live.webp'],
      movies: ['CINEMA', 'Una serata da scoprire', 'Esplora copertine, generi, dettagli e cast senza scorrere un elenco infinito.', ['In evidenza', 'Azione', 'Dramma', '4K'], 'assets/category-cinema.webp'],
      series: ['PROSSIMO EPISODIO', 'Le stagioni al posto giusto', 'Trova ogni episodio, segui i progressi e continua quando vuoi.', ['Continua', 'Novità', 'Dramma', 'Famiglia'], 'assets/category-series.webp'],
      multiview: ['MULTIVIEW · WINDOWS', 'Quattro segnali. Un solo focus.', 'Segui fino a quattro canali compatibili insieme e scegli l’audio principale.', ['Canale 1', 'Canale 2', 'Canale 3', 'Canale 4'], 'assets/category-sports.webp'],
      offline: ['OFFLINE VAULT · IN PREPARAZIONE', 'Scarica ora. Guarda quando vuoi.', 'Film ed episodi idonei restano nell’app per la riproduzione locale quando la connessione è instabile.', ['In download', 'Salvati', 'In viaggio', 'Visti'], 'assets/category-cinema.webp']
    }
  };

  let currentLanguage = 'pt';
  let currentDemo = 'home';
  let demoPaused = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let demoTimer;

  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

  function normalizeLanguage(value) {
    const code = String(value || '').toLowerCase().slice(0, 2);
    return translations[code] ? code : 'pt';
  }

  function setRichText(element, value) {
    const parts = String(value).split('<br>');
    element.replaceChildren();
    parts.forEach((part, index) => {
      if (index) element.append(document.createElement('br'));
      element.append(document.createTextNode(part));
    });
  }

  function applyLanguage(language) {
    currentLanguage = normalizeLanguage(language);
    const copy = translations[currentLanguage];
    document.documentElement.lang = languageNames[currentLanguage];
    document.documentElement.dataset.lang = currentLanguage;
    $$('[data-i18n]').forEach((element) => {
      const value = copy[element.dataset.i18n];
      if (value !== undefined) setRichText(element, value);
    });
    $('[data-language]').value = currentLanguage;
    const meta = metadata[currentLanguage];
    document.title = meta.title;
    $('meta[name="description"]').content = meta.description;
    $('meta[property="og:title"]').content = meta.title;
    $('meta[property="og:description"]').content = meta.description;
    $('meta[name="twitter:title"]').content = meta.title;
    $('meta[name="twitter:description"]').content = meta.description;
    $$('[data-activation-link], [data-footer-key]').forEach((link) => {
      link.href = `${WORKER_ORIGIN}/ativar?lang=${currentLanguage}`;
    });
    try { localStorage.setItem('iptvburo-site-lang', currentLanguage); } catch (_) { /* storage may be disabled */ }
    const url = new URL(window.location.href);
    url.searchParams.set('lang', currentLanguage);
    history.replaceState({}, '', url);
    renderDemo(currentDemo);
    updateTourButton();
    const video = $('[data-brand-video]');
    $('[data-video-toggle]').setAttribute('aria-label', copy[video.paused ? 'videoPlay' : 'videoPause']);
  }

  function renderDemo(name, restart = true) {
    currentDemo = demoCopy[currentLanguage][name] ? name : 'home';
    const [badge, title, description, rail, backdrop] = demoCopy[currentLanguage][currentDemo];
    const stage = $('[data-demo-stage]');
    stage.dataset.demoStage = currentDemo;
    $('[data-stage-badge]').textContent = badge;
    $('[data-stage-title]').textContent = title;
    $('[data-stage-description]').textContent = description;
    $('[data-stage-primary]').textContent = translations[currentLanguage][currentDemo === 'offline' ? 'downloadAction' : 'play'];
    $('[data-stage-secondary]').textContent = translations[currentLanguage][currentDemo === 'offline' ? 'offlineLibrary' : 'details'];
    $('[data-stage-backdrop]').style.backgroundImage = `url('${backdrop}')`;
    $$('[data-content-rail] article').forEach((card, index) => { $('span', card).textContent = rail[index]; });
    $$('[data-demo-tab]').forEach((tab) => {
      const active = tab.dataset.demoTab === currentDemo;
      tab.classList.toggle('active', active);
      tab.setAttribute('aria-selected', String(active));
      tab.tabIndex = active ? 0 : -1;
    });
    if (restart) restartTour();
  }

  function updateTourButton() {
    const button = $('[data-tour-toggle]');
    button.setAttribute('aria-pressed', String(demoPaused));
    $('[data-tour-icon]').textContent = demoPaused ? '▶' : 'Ⅱ';
    $('[data-tour-label]').textContent = translations[currentLanguage][demoPaused ? 'resumeTour' : 'pauseTour'];
    $('[data-demo-progress]').classList.toggle('paused', demoPaused);
  }

  function restartTour() {
    clearInterval(demoTimer);
    const progress = $('[data-demo-progress]');
    progress.classList.remove('running');
    void progress.offsetWidth;
    if (!demoPaused) {
      progress.classList.add('running');
      demoTimer = window.setInterval(() => {
        const names = ['home', 'live', 'movies', 'series', 'multiview', 'offline'];
        renderDemo(names[(names.indexOf(currentDemo) + 1) % names.length], false);
        progress.classList.remove('running');
        void progress.offsetWidth;
        progress.classList.add('running');
      }, 6000);
    }
    updateTourButton();
  }

  function formatDeviceCode(value) {
    const compact = String(value).toUpperCase().split('').filter((char) => DEVICE_ALPHABET.includes(char)).slice(0, 12).join('');
    return compact.match(/.{1,4}/g)?.join('-') || '';
  }

  function validateDevice(showMessage = true) {
    const input = $('#device-code');
    const valid = DEVICE_PATTERN.test(input.value);
    $('.code-field').classList.toggle('valid', valid);
    input.setAttribute('aria-invalid', String(!valid && input.value.length > 0));
    $('[data-code-status]').textContent = showMessage && input.value ? translations[currentLanguage][valid ? 'validCode' : 'invalidCode'] : '';
    return valid;
  }

  function setupPayment() {
    const input = $('#device-code');
    const params = new URLSearchParams(window.location.search);
    input.value = formatDeviceCode(params.get('code') || params.get('device') || '');
    validateDevice(false);
    input.addEventListener('input', () => {
      const start = input.selectionStart;
      const before = input.value;
      input.value = formatDeviceCode(before);
      if (start !== null && input.value.length > before.length) input.setSelectionRange(start + 1, start + 1);
      validateDevice(true);
    });
    $('[data-payment-form]').addEventListener('submit', (event) => {
      event.preventDefault();
      if (!validateDevice(true)) {
        input.focus();
        return;
      }
      const destination = new URL('/comprar', WORKER_ORIGIN);
      destination.searchParams.set('device', input.value);
      destination.searchParams.set('lang', currentLanguage);
      window.location.assign(destination.toString());
    });
  }

  function setupNavigation() {
    const toggle = $('[data-nav-toggle]');
    const nav = $('[data-nav]');
    toggle.addEventListener('click', () => {
      const open = !nav.classList.contains('open');
      nav.classList.toggle('open', open);
      toggle.setAttribute('aria-expanded', String(open));
    });
    $$('a[href^="#"]').forEach((link) => link.addEventListener('click', () => {
      nav.classList.remove('open');
      toggle.setAttribute('aria-expanded', 'false');
    }));
    const updateHeader = () => $('[data-header]').classList.toggle('scrolled', window.scrollY > 24);
    window.addEventListener('scroll', updateHeader, { passive: true });
    updateHeader();
  }

  function setupDemo() {
    $$('[data-demo-tab]').forEach((tab) => {
      tab.addEventListener('click', () => renderDemo(tab.dataset.demoTab));
      tab.addEventListener('keydown', (event) => {
        if (!['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(event.key)) return;
        event.preventDefault();
        const tabs = $$('[data-demo-tab]');
        const delta = ['ArrowDown', 'ArrowRight'].includes(event.key) ? 1 : -1;
        const next = tabs[(tabs.indexOf(tab) + delta + tabs.length) % tabs.length];
        next.focus();
        renderDemo(next.dataset.demoTab);
      });
    });
    $('[data-tour-toggle]').addEventListener('click', () => {
      demoPaused = !demoPaused;
      restartTour();
    });
    renderDemo('home');
  }

  function setupVideo() {
    const video = $('[data-brand-video]');
    const button = $('[data-video-toggle]');
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) video.pause();
    const update = () => {
      button.textContent = video.paused ? '▶' : 'Ⅱ';
      button.setAttribute('aria-label', translations[currentLanguage][video.paused ? 'videoPlay' : 'videoPause']);
    };
    button.addEventListener('click', async () => {
      if (video.paused) {
        try { await video.play(); } catch (_) { /* browser may block autoplay */ }
      } else video.pause();
      update();
    });
    video.addEventListener('play', update);
    video.addEventListener('pause', update);
    update();
  }

  function setupReveal() {
    const elements = $$('.reveal');
    if (!('IntersectionObserver' in window) || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      elements.forEach((element) => element.classList.add('visible'));
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          observer.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });
    elements.forEach((element) => observer.observe(element));
  }

  function setupFaq() {
    $$('.faq-list details').forEach((item) => item.addEventListener('toggle', () => {
      if (!item.open) return;
      $$('.faq-list details').forEach((other) => { if (other !== item) other.open = false; });
    }));
  }

  const params = new URLSearchParams(window.location.search);
  let storedLanguage = '';
  try { storedLanguage = localStorage.getItem('iptvburo-site-lang') || ''; } catch (_) { /* storage may be disabled */ }
  const initialLanguage = normalizeLanguage(params.get('lang') || storedLanguage || navigator.language);

  $$('[data-year]').forEach((element) => { element.textContent = String(new Date().getFullYear()); });
  $('[data-language]').addEventListener('change', (event) => applyLanguage(event.target.value));
  setupNavigation();
  setupPayment();
  setupDemo();
  setupVideo();
  setupReveal();
  setupFaq();
  applyLanguage(initialLanguage);
})();
