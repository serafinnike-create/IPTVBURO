# Samsung TV Seller Office — candidato 3.7.0

Data do candidato: 29 de agosto de 2026.

## Identidade do pacote

- tipo: Tizen Web Application;
- título padrão: `IPTV BURO` (deve ser idêntico no Seller Office);
- Tizen application ID: `IPTVBUROxx.IPTVBURO`;
- package ID: `IPTVBUROxx`;
- versão: `3.7.0`;
- API mínima: Tizen `6.0` (modelo 2021 ou posterior);
- orientação: paisagem, 1920×1080;
- categoria sugerida: Video / Entertainment;
- publicidade: não;
- compra no aplicativo Samsung: não nesta versão;
- política de privacidade: `https://iptvburo.pages.dev/privacy`;
- suporte/VOC: `iptvburo@gmail.com`.

## Texto da loja

### Português (Brasil)

**Título:** IPTV BURO

**Descrição:** Organize e reproduza suas fontes de mídia autorizadas na Samsung TV. O IPTV BURO reúne TV ao vivo, filmes e séries, favoritos, perfis, pesquisa, histórico e continuidade em uma interface criada para o controle remoto. Compatível com M3U/M3U8, servidores Xtream-compatible e fundação Stalker/Ministra. O aplicativo não fornece conteúdo, listas ou credenciais. Use somente fontes que você tem autorização legal para acessar.

### English

**Title:** IPTV BURO

**Description:** Organize and play your authorized media sources on Samsung TV. IPTV BURO brings live TV, movies and series, favorites, profiles, search, history and watch progress together in a remote-friendly interface. It supports M3U/M3U8, Xtream-compatible servers and a Stalker/Ministra foundation. The application does not provide content, playlists or credentials. Use only sources you are legally authorized to access.

### Deutsch

**Titel:** IPTV BURO

**Beschreibung:** Organisieren und spielen Sie Ihre autorisierten Medienquellen auf dem Samsung TV ab. IPTV BURO vereint Live-TV, Filme und Serien, Favoriten, Profile, Suche, Verlauf und Wiedergabefortschritt in einer für die Fernbedienung optimierten Oberfläche. Unterstützt werden M3U/M3U8, Xtream-kompatible Server und eine Stalker/Ministra-Grundlage. Die App stellt keine Inhalte, Listen oder Zugangsdaten bereit. Verwenden Sie nur Quellen, für deren Zugriff Sie rechtlich autorisiert sind.

### Italiano

**Titolo:** IPTV BURO

**Descrizione:** Organizza e riproduci le tue fonti multimediali autorizzate su Samsung TV. IPTV BURO riunisce TV in diretta, film e serie, preferiti, profili, ricerca, cronologia e avanzamento della visione in un'interfaccia pensata per il telecomando. Supporta M3U/M3U8, server compatibili con Xtream e una base Stalker/Ministra. L'app non fornisce contenuti, playlist o credenziali. Usa solo fonti a cui sei legalmente autorizzato ad accedere.

### Español

**Título:** IPTV BURO

**Descripción:** Organiza y reproduce tus fuentes multimedia autorizadas en Samsung TV. IPTV BURO reúne TV en directo, películas y series, favoritos, perfiles, búsqueda, historial y progreso de reproducción en una interfaz diseñada para el mando a distancia. Es compatible con M3U/M3U8, servidores compatibles con Xtream y una base Stalker/Ministra. La aplicación no proporciona contenido, listas ni credenciales. Utiliza solo fuentes a las que tengas autorización legal para acceder.

## Instruções para a certificação

1. Escolha um dos cinco idiomas na primeira abertura.
2. Aceite os termos e crie um perfil local.
3. Registre/ative o dispositivo com a chave de QA fornecida separadamente.
4. Em `Fontes`, adicione a fonte de teste autorizada fornecida separadamente.
5. Valide Home, TV ao vivo, Filmes, Séries, Pesquisa, Favoritos, Histórico e retomada.
6. Em Filmes/Séries, abra uma ficha e percorra Elenco e Títulos parecidos pelas setas esquerda/direita.
7. Abra um item autorizado e valide play/pause, seek, áudio, legenda, formato de tela e RETURN.
8. O aplicativo não possui login de usuário próprio, publicidade nem compra Samsung nesta versão.

Não anexar credenciais, chaves de licença, URLs privadas ou tokens a este arquivo. Preencher os campos seguros do Seller Office com uma fonte de QA válida durante todo o período de certificação e uma ativação que aceite dispositivos de teste ilimitados.

## Estado do candidato local

- suíte Samsung: todos os grupos aprovados, código de saída 0 em 192,1 s;
- D-pad: 372/372;
- Chromium visual: 309/309 verificações aprovadas em 1920×1080;
- emulador Samsung Tizen 10: instalado e aberto, PID 7345 estável por 15 s;
- WGT: 797.209 bytes;
- entradas: 46 (44 payloads e duas assinaturas);
- integridade: 44/44 payloads do WGT idênticos aos arquivos do staging;
- arquivos de desenvolvimento/diagnóstico/segredo no WGT: zero;
- SHA-256: `530188FD7BF725A29EE4DC289F1B3B177F6B02EEFFBFF02617BFED0036656418`.

## Bloqueios para solicitar certificação

- executar o roteiro `docs/VALIDACAO_TV_SAMSUNG.md` em uma Samsung TV física e manter `hardware: NOT_VALIDATED` até concluir;
- entrar no Samsung Developer/Seller Office e baixar o modelo oficial do Application UI Description;
- preencher esse PPTX sem alterar sua estrutura, incluindo mapa completo, casos de uso, menus, teclas e idiomas;
- fornecer à equipe Samsung uma ativação de QA e uma fonte autorizada que permitam testar todas as funções;
- definir classificação etária, países e grupos de modelos no Seller Office;
- para distribuição fora dos Estados Unidos, obter status Partner Seller e acordo com o Samsung Content Manager;
- enviar o WGT e concluir o Auto Pre-Test; qualquer erro do portal deve ser tratado antes de solicitar a distribuição.

A revisão da política de privacidade foi implantada no Cloudflare Pages e
confirmada com HTTP 200 em `https://iptvburo.pages.dev/privacy` em 29 de agosto
de 2026.

## Referências oficiais

- https://developer.samsung.com/tv-seller-office/application-publication-process.html
- https://developer.samsung.com/tv-seller-office/checklists-for-distribution/launch-checklist.html
- https://developer.samsung.com/tv-seller-office/checklists-for-distribution/application-ui-description.html
- https://developer.samsung.com/smarttv/design/app-icons-and-screenshots.html
