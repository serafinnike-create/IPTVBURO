# Implementação atual do IPTV BURO

- Data da auditoria: 2 de agosto de 2026
- Branch: `agent/iptv-buro-0.2-preview`
- Baseline anterior: `v0.1.0-alpha.1`
- Tag da prévia atual: `v0.2.0-alpha.5`
- Release:
  `https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.2.0-alpha.5`
- Versão pública atual: `0.2.0-alpha.5`, Android/Android TV e Windows x64
- Milestone em validação: `0.2`, Android adaptativo e Compose Desktop

## Estado da milestone 0.2

A milestone `0.2` amplia o vertical funcional publicado como prévia. Esta seção
registra arquitetura, escopo, gates, hashes e as limitações que ainda impedem
classificar o produto como uma versão estável.

- cliente Xtream compartilhado para autenticação, categorias, TV ao vivo,
  filmes, séries e episódios;
- catálogo Xtream estruturado, separado por tipo de conteúdo;
- metadados e locators Xtream sem credenciais persistidos no Room;
- credenciais Android cifradas com AES-GCM e chave de 256 bits protegida pelo
  Android Keystore;
- URL Xtream final resolvida em memória somente para detalhes ou reprodução;
- consultas Android paginadas por fonte, tipo e categoria;
- interface Android sem bloqueio de orientação, com layouts para retrato
  compacto, paisagem compacta e janelas expandidas;
- preview Compose Desktop para M3U local e Xtream, com catálogo em sessão e
  credenciais opcionais cifradas pelo DPAPI do usuário Windows;
- paginação e carregamento sob demanda no desktop;
- reprodução desktop embutida pelo executável oficial do VLC, iniciado sem URI
  privada na linha de comando e controlado apenas por loopback autenticado.

### Continuação de 2 de agosto de 2026

- português do Brasil, inglês, alemão e italiano podem ser escolhidos e
  persistidos no Android; o Windows aplica os quatro idiomas ao shell principal;
- até cinco perfis de família, inclusive Kids, podem ser criados e selecionados;
- favoritos são isolados por perfil e Minha BURO exibe a biblioteca no Android;
- a Home real cria fileiras de lançamentos do ano atual/anterior, recentes,
  filmes e séries sem carregar o catálogo completo na interface;
- categorias Android receberam iconografia semântica e episódios exibem prévia
  de artwork quando a fonte fornece imagem;
- o player Android ganhou controles reais de volume, brilho, velocidade,
  retrocesso/avanço, bloqueio e PiP mobile, mantendo áudio/legenda do Media3;
- o preview Windows reproduz H.264, H.265/HEVC, AAC, MP4, MKV e HLS pelo VLC
  oficial incluído, com play/pause, seek, volume, velocidade e tela cheia;
- `Verificar atualização` consulta pré-releases e releases do GitHub, exige uma
  versão mais nova e valida o digest SHA-256 do MSI antes de abrir o instalador;
- o play Windows agora abre diretamente e fica acima da sinopse na ficha para
  continuar acessível em escala de 125%; detalhes, ator e filmografia permanecem
  dentro da mesma janela;
- o downloader genérico Windows foi removido. Download permanece oculto em
  Android, TV e Windows até a fonte/backend autorizar o item e a implementação
  cumprir integralmente o Cofre Offline do GDD 6;
- domínio de entitlement e identidade criptográfica de instalação Android foram
  adicionados; pagamento real aguarda backend, Stripe e Google Play Billing;
- ADRs de licenciamento/player e auditorias de playback, importação e logging
  documentam os gates que ainda bloqueiam a primeira versão Windows estável.
- EPG curto Xtream agora é consultado sob demanda e apresenta **Agora/A seguir**
  no player Android e no painel de TV ao vivo do Windows; EPG ausente nunca
  bloqueia a reprodução;
- respostas transitórias de rede, HTTP 408/429 e HTTP 5xx possuem uma tentativa
  automática limitada; autenticação e demais erros 4xx não são repetidos;
- perfis Kids ocultam categorias e itens explicitamente adultos nas duas
  plataformas. A lista local é conservadora e não afirma que conteúdo sem
  classificação seja apropriado;
- o contrato EPG e a fonte privada autorizada passaram no teste de
  compatibilidade sem persistir ou imprimir credenciais.
- a Home Windows deixou de reutilizar o catálogo administrativo: agora possui
  destaque e fileiras editoriais que mudam deterministicamente a cada dia,
  respeitam Kids e removem variantes duplicadas de qualidade/idioma da seleção;
- catálogo completo, Home e Favoritos são destinos distintos; sinopse e ficha
  só são carregadas após clique explícito no conteúdo.

### Gate local mais recente

- 139 testes: 0 falhas, 0 erros e 2 ignorados por condição de plataforma;
- Android lint: 0 erros;
- APK debug: 34.253.491 bytes, SHA-256
  `C34C24CFA7DBF49A82E70C3D494D396C6062484E31D40D173FD4735EFC9CD18D`;
- MSI Windows preview: 163.982.391 bytes, SHA-256
  `25A2BFB33C4FE8EC860686992F8988FDF62DA6E0383429A9DC60ED5326C9A846`;
- os três identificadores da fonte privada autorizada tiveram zero ocorrências
  no worktree publicável e em todo o histórico Git;
- a build final foi instalada no Android 15 e iniciou sem crash; a validação
  visual/toque desta rodada aguarda o desbloqueio físico do aparelho;
- o preview Windows restaurou a sessão DPAPI, abriu, respondeu e foi inspecionado
  em escala de 125%; HEVC real foi reproduzido pelo VLC incluído. O MSI permanece
  pré-release até completar a matriz pública de hardware, faixas e HDR.

### Revisão da camada visual — 2 de agosto de 2026

Detalhamento em [`design-system.md`](../ux/design-system.md).

Correções de defeito:

- os atalhos de cor do Android (`Ink`, `Teal`, `Blue`, `White`, `Muted`, …) eram
  `val` de topo capturados do esquema padrão na inicialização da classe. O
  esquema resolvido por `resolveBuroColorScheme` existia e era publicado em
  `LocalBuroColors`, mas nenhuma tela o lia: **alto contraste e transparência
  reduzida não produziam efeito visual em nenhuma parte do aplicativo**. Agora
  são getters `@Composable` que leem `BuroTheme.colors`;
- os mesmos atalhos foram renomeados para nomes semânticos. `Teal` apontava para
  o marfim e `Blue` para o dourado, o que tornava impossível prever o que uma
  tela pintaria. A renomeação não altera nenhum pixel no tema padrão;
- as fileiras da Home Windows alinhavam o título em 34 dp e o primeiro card em
  22 dp, então cada fileira ficava visivelmente deslocada do próprio título.
  Título e `contentPadding` agora usam o mesmo `gutter`;
- havia três valores distintos de "cor sobre o dourado" (`0xFF03201D`,
  `0xFF08110F`, `0xFF071019`), nenhum pertencente à paleta. Todos passaram a
  `BuroColors.OnPrimary`;
- a barra superior Windows não encolhia: uma `Row` não reduz filhos sem peso,
  então em janelas estreitas os controles à direita saíam da tela. Agora são
  descartados por prioridade.

Evoluções:

- `BuroInteractiveSurface`/`BuroInteractiveRow` implementam a física de foco do
  GDD 2.0 no Windows: escala 1.045/1.06, anel luminoso, 160 ms. Hover e foco de
  teclado compartilham a mesma `MutableInteractionSource`, então todo card, item
  de navegação e fonte passou a ser navegável por `Tab`;
- a Home Windows recebeu hero responsivo (52% da altura útil, limitado a
  300–560 dp), scrim duplo, faixa de fatos, ação `Assistir` direta para filme e
  ao vivo, chips de nota e AO VIVO sobre a arte, e esqueleto com o formato da
  tela final em vez de um indicador centralizado;
- `DesktopStrings` substituiu a tabela anterior de nove chaves por uma
  `data class` com pt-BR, en, de e it cobrindo shell, Home, estados, diálogos e
  banners. `XtreamWorkspace` e `XtreamLoginDialog` continuam em português fixo.

Segunda rodada — catálogo e ficha Windows:

- o painel lateral fixo de categorias virou rail horizontal de chips; o catálogo
  virou grid editorial adaptativo com o mesmo card da Home, chip de nota sobre a
  arte e hover/foco por teclado;
- grid e rail voltam ao início quando tipo, categoria ou página mudam. Antes a
  lista reaproveitava o deslocamento e a página nova abria já rolada;
- a ficha deixou de ser card centralizado: pôster à esquerda, título, fatos e
  ações à direita, alinhados à esquerda. As ações passaram de botões empilhados
  em largura total para uma linha com largura natural;
- os três seletores de tipo viraram um controle segmentado. Como três botões
  dourados lado a lado, todo estado lia como "selecionado";
- as últimas duas ocorrências de `Color(0xFF03201D)` foram removidas;
- Android teve a ação primária e a barra de progresso unificadas no dourado da
  marca. A ação primária era marfim no Android e dourada no Windows.

Gate desta revisão:

- `test`, `:apps:android-tv:assembleDebug`, `:apps:android-tv:lintDebug` e
  `:apps:desktop:test` passaram; 25 suítes JVM sem falha;
- o executável Windows foi aberto e inspecionado; captura em
  `artifacts/desktop/buro-cinematic-shell.png`;
- a janela é per-monitor DPI aware: a 125% o `containerSize` medido foi
  1920×991 px físicos com `density = 1.25`. Capturas de validação precisam
  chamar `SetProcessDPIAware`, caso contrário gravam cerca de 80% da largura sem
  sinalizar o corte — as capturas anteriores deste diretório têm esse viés;
- **a Home Windows redesenhada não foi validada com catálogo real.** A execução
  verificada usou a sessão vazia. A validação com fonte autorizada continua
  pendente para esta mudança.

### Continuação de 1 de agosto de 2026

- auditoria honesta dos GDDs 1–5 registrada em
  `GDD_1_TO_5_IMPLEMENTATION_AUDIT.md`;
- direção visual original **BURO Nocturne**, com o mesmo hero e os mesmos
  tokens semânticos no Android e Windows;
- contrato canônico inicial em `packages/design-tokens/tokens.json`;
- parser de catálogo Xtream por fluxo, sem `ByteArray`, `String`, árvore JSON e
  lista de domínio completas coexistindo na heap;
- importação Xtream Android atômica em lotes de 500, com limite defensivo de
  1 milhão de entradas;
- paginação Android por cursor/keyset e índice Room v3, sem custo crescente de
  `OFFSET` ao avançar no catálogo;
- cliente desktop Xtream passou a consumir o catálogo por fluxo, eliminando a
  árvore JSON e a lista intermediária;
- fixture gerada em fluxo com 500.000 entradas passou no teste JVM;
- `testDebugUnitTest`, `assembleDebug`, testes desktop, `lintDebug`,
  `packageMsi` e `createDistributable` passaram;
- executável Windows atual aberto e inspecionado visualmente; captura em
  `artifacts/desktop/buro-nocturne-desktop.png`.

O celular não estava visível no ADB nesta continuação. Portanto, a nova camada
visual e a migração Room 2→3 ainda precisam de validação física móvel antes de
publicação.

A decisão e suas limitações estão em
[`ADR-003`](../adr/ADR-003-xtream-and-desktop-milestone-0.2.md). O tratamento
detalhado de segredos está em
[`credential-handling.md`](../security/credential-handling.md).

## Estado do repositório

A especificação oficial e a implementação Android descrita abaixo estão na
branch remota `main`; o commit da implementação é `2c9bd5b`. Os GDDs oficiais
foram preservados como fonte de verdade. A tag `v0.1.0-alpha.1`, em `7e0b9ec`,
e a GitHub Pre-release com seu APK foram publicadas pelo workflow
**Publish Android preview**.

## Stack e módulos

- Kotlin 2.3.21, JVM target 17, Android Gradle Plugin 9.0.1 e Gradle Wrapper 9.1;
- Compose for TV, Media3 ExoPlayer com HLS e datasource OkHttp;
- Compose Desktop para o preview de notebook;
- Room, DataStore, Hilt e Coroutines/Flow;
- JUnit, Android lint e GitHub Actions.

| Módulo | Responsabilidade |
|---|---|
| `apps/android-tv` | Aplicativo Android adaptativo, player, persistência e DI |
| `apps/desktop` | Preview Compose Desktop com catálogo efêmero |
| `packages/domain-model` | Modelos de fonte, categoria, canal e capacidades |
| `packages/playlist-parser` | Parser M3U streaming e redaction de warnings |
| `packages/xtream-client` | Cliente e modelos Xtream compartilhados |
| `packages/test-fixtures` | Fixtures sintéticas e públicas, somente em testes |

## Vertical funcional

- splash e onboarding legal;
- importação de arquivo M3U/M3U8 pelo seletor do Android;
- fontes, categorias e canais persistidos com Room;
- parser streaming com limites, transação atômica e escrita em lotes;
- player Media3 com HLS, loading, primeiro frame, erro, play/pause e seek
  condicionado à capacidade real da mídia;
- português do Brasil, inglês, alemão e italiano;
- navegação por controle remoto/D-pad.

## BURO Cinematic Foundation

Esta milestone substitui a antiga sidebar e o dashboard técnico pela primeira
fundação da experiência descrita no GDD 2.0:

- **BURO Ribbon** com oito destinos: Início, Ao Vivo, Filmes, Séries, Descobrir,
  Minha BURO, Pesquisa e Perfil;
- **Living Home** com hero cinematográfico, fileiras e estados tratados;
- fileiras visuais sintéticas identificadas como **DEMO**, sem mídia ou URL
  reproduzível embutida;
- fileira separada para fontes reais importadas, também sem copiar URLs para os
  modelos visuais da Home;
- Story demonstrativa sem playback, com ação contextual para adicionar fonte;
- placeholders explícitos para destinos ainda não implementados;
- Configurações acessíveis pelo Perfil;
- restauração mínima do último foco da Home e contrato `Back → Ribbon`;
- design system com tokens semânticos, componentes focáveis e tiers
  `Auto`, `Eco`, `Balanced` e `Cinematic`;
- preferências estruturadas para reduced motion, high contrast e reduced
  transparency.

O modo `Auto` escolhe uma política visual local. As preferências e tiers já fazem
parte da fundação, mas os tokens novos ainda não foram aplicados integralmente a
todas as telas legadas.

## Dados, player e segurança

`Source`, `Category` e `Channel` são persistidos no Room. O importador roda no
dispatcher de I/O, rejeita catálogo vazio e não envia playlists ou credenciais
para backend. O player é liberado em `onDispose`, observa primeiro frame/erro e
só expõe seek quando Media3 declara o item pesquisável.

Há redaction de URLs, queries, tokens, credenciais, cookies e IPs nos logs.
`Authorization` e `Cookie` não são persistidos pelo importador, e backup e
transferência de dados do aplicativo estão desabilitados.

Na milestone `0.2`, credenciais Xtream ficam fora do Room, cifradas com
AES-GCM por uma chave do Android Keystore. As linhas Xtream usam locators locais
sem credenciais; URLs de live, filme e episódio são montadas tardiamente em
memória. URLs de stream, `Referer` e `Origin` originados por M3U ainda ficam no
sandbox do Room em texto simples e exigem proteção adicional antes de uma
versão estável.

## Validação reproduzida da versão pública 0.1

- `./gradlew test lint assembleDebug`: passou;
- 55 testes JVM: 55 passaram, 0 falhas, 0 erros e 0 ignorados;
- lint: 0 erros e 18 warnings não bloqueantes;
- APK debug local: 25.433.893 bytes;
- SHA-256 do APK local:
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`;
- instalação e execução no Redmi A5, Android 15;
- fluxo E2E com a playlist HLS pública Apple BipBop: importação, navegação por
  fonte/categoria/canal, abertura do player e primeiro frame com áudio/vídeo,
  sem crash;
- onboarding responsivo e navegação compacta em paisagem validados no aparelho.

A validação final da milestone `0.2` deve ser registrada somente depois dos
testes completos, smoke tests em Android e desktop, varredura de segredos e
geração dos artefatos. Nenhum resultado dessa milestone é presumido aqui.

## Validação de compatibilidade 0.2 — 1 de agosto de 2026

Uma fonte privada autorizada foi usada somente em runtime e removida ao final;
nenhum identificador, endpoint ou segredo pertence ao repositório. O ensaio
confirmou importação estruturada de TV ao vivo, filmes, séries e episódios,
busca e paginação, player Android, rotação e retomada após background.

- Android persistiu o catálogo em aproximadamente 40 MB de Room e variou de
  cerca de 160 MB para 165 MB de PSS imediatamente após a importação;
- TV ao vivo, filme compatível e episódio reproduziram no player interno sem
  crash ou erro Media3;
- uma variante 4K/HEVC excedeu o decoder do aparelho e agora recebe mensagem
  específica de incompatibilidade em vez de erro genérico;
- Windows importou e navegou o catálogo, carregou episódios e executou busca;
- o formulário seguro Windows foi corrigido para atualizar reativamente a
  habilitação do botão Conectar;
- o catálogo Windows passou a usar índice colunar de sessão, reconstruindo
  somente os objetos da página visível;
- o índice colunar passou a preservar artwork em uma arena UTF-8 compacta, sem
  restaurar uma árvore de objetos completa na memória;
- Android e Windows agora exibem artwork real fornecido pela fonte, com cache
  em memória e cache em disco desabilitado para não persistir URLs assinadas;
- o Windows usa grid editorial adaptativo para o catálogo Xtream, enquanto o
  Android diferencia posters 2:3 de filmes/séries e tiles de TV ao vivo;
- duas novas artes editoriais originais, `Paper Sun` e `Forest Signal`, foram
  incorporadas à demonstração visual junto do hero `BURO Nocturne`;
- parser streaming e índice Windows passaram separadamente com 500.000 itens;
- dados do pacote de teste foram apagados, a build limpa foi reaberta e a
  varredura exata dos três valores privados terminou com zero ocorrências.

## Validação de reconexão — 2 de agosto de 2026

- Android restaurou a fonte cifrada pelo Keystore depois de `force-stop` e abriu
  novamente as 29.967 entradas de filmes;
- Windows restaurou a fonte a partir de um blob DPAPI do usuário atual depois de
  duas reaberturas, sem novo preenchimento do formulário;
- `Encerrar sessão` no Windows apaga esse blob; catálogo e URLs continuam apenas
  em memória;
- Coil/OkHttp carregou capas e backdrops HTTP reais no Windows e no Android;
- ficha de filme exibiu data, duração, gênero, avaliação, sinopse, direção,
  elenco e país nas duas plataformas;
- breakpoint do workspace Windows foi antecipado para manter a ficha visível em
  telas de notebook.

## Publicação

- GitHub Pre-release:
  `https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.1.0-alpha.1`;
- APK: `IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`;
- download:
  `https://github.com/lucasserafin94/IPTVBURO/releases/download/v0.1.0-alpha.1/IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`;
- tamanho do APK do CI: 24.864.542 bytes;
- SHA-256 do APK do CI:
  `179537447d53ef062daf9cd100b5ed52416be796ceedb61cb64601a930965dc6`;
- workflow `Publish Android preview`, run
  [`30590918504`](https://github.com/lucasserafin94/IPTVBURO/actions/runs/30590918504):
  passou.

## Lacunas e riscos

- o GDD 2.0 não está integralmente concluído;
- tokens semânticos ainda não cobrem toda a Home e todas as telas legadas;
- não há testes instrumentados de D-pad, screenshots ou goldens;
- restauração de scroll/estado por rota ainda não é completa;
- Descobrir, Minha BURO, Pesquisa e Perfis reais continuam como destinos
  tratados, sem funcionalidade completa;
- a Story atual é demonstrativa e não inicia playback;
- não há EPG/XMLTV, Kids, catálogo enriquecido ou recomendação real;
- não há modelos temporais do GDD 3.0;
- não há Resilience Engine, `RetryBudget` ou `ConnectionBudget` do GDD 4.0;
- URLs e headers originados por M3U continuam em texto simples no Room;
- o desktop depende de um aplicativo externo para playback e não controla o
  histórico desse aplicativo;
- segurança de transporte depende de a fonte oferecer HTTPS;
- não há ainda ação de exclusão de uma fonte individual na interface Android;
- o desktop ainda precisa de player interno e de nova medição E2E do índice
  colunar com uma fonte real de centenas de milhares de itens.

## Fichas cinematográficas e player — 1 de agosto de 2026

- filmes e séries consultam metadados detalhados sob demanda, sem ampliar o
  índice de catálogo de centenas de milhares de itens;
- Android ganhou ficha de filme em retrato/paisagem com backdrop, sinopse,
  créditos, fatos e trailer opcional; reprodução só é resolvida após `Assistir`;
- fichas de séries Android e Windows receberam backdrop e créditos reais;
- player Android passou ao controlador completo e auto-ocultável do Media3,
  com timeline, seek 15/30, velocidade, seleção de áudio/legenda quando a mídia
  oferece as faixas e botão de fullscreen/rotação;
- paleta Android/Windows convergiu para BURO Nocturne Gold: base grafite,
  tipografia marfim e dourado apenas como acento;
- a fonte privada permanece apenas na sessão Windows aberta, conforme pedido;
  seus valores não foram incluídos no código ou nos artefatos;
- o APK atualizado foi instalado preservando dados no aparelho conectado, mas
  a inspeção visual e a importação no Android aguardam desbloqueio manual.
