# Implementação atual do IPTV BURO

- Data da auditoria: 17 de agosto de 2026
- Branch: `codex/windows-clean-release`
- Baseline anterior: `v0.1.0-alpha.1`
- Tag mais recente no repositório atual: `v2.0.0-alpha.12`
- Releases publicadas: `v2.0.0-alpha.7`, `v2.0.0-alpha.11`, `v2.0.0-alpha.12`,
  todas como prévia
- Windows: `v2.0.0-alpha.12` publicada; MSI sem assinatura Authenticode
- Android/Android TV: APK de depuração publicado na mesma página
- Milestone em validação: `0.2`, Android adaptativo e Compose Desktop

## Correções de 17 de agosto de 2026

Auditoria da tela de Configurações e do catálogo no Windows, mais um defeito que
impedia a compilação do Android.

- **Atribuição de nota corrigida.** O painel de avaliações desenhava a marca da
  Netflix ao lado das palavras "Nota TMDb". A imagem vinha de uma constante
  chamada `TMDB_MARK_URL`, documentada como "a marca da própria TMDb", mas aquele
  caminho é de logo de serviço de streaming no CDN da TMDb e o arquivo por trás
  dele é da Netflix. `ScoreAttributionTest` agora falha diante de qualquer
  caminho de CDN fixo naquele arquivo.
- **Configurações inalcançável pela barra lateral.** A coluna de navegação era
  uma lista de altura fixa sem rolagem, e numa área útil de 1536x816 os três
  últimos destinos ficavam abaixo da borda. Terceira superfície do aplicativo a
  dar altura ilimitada a um filho rolável; `SidebarReachableUiTest` fixa as duas
  metades da correção.
- **Rótulos quebrando uma letra por linha.** Duas ocorrências independentes em
  Configurações, ambas causadas por `Row` distribuindo toda a largura ao primeiro
  filho. Corrigidas com `FlowRow`, e `SettingsPill` recusa quebra na própria
  definição.
- **Seletores de Gênero e Serviço** substituem a fileira horizontal de
  categorias em Filmes e Séries. Não se combinam, porque cada título pertence a
  uma única categoria e o cruzamento devolveria grade vazia; a divisão está em
  `packages`-independente `desktop/ui/CategorySplit.kt`, com dez casos de teste.
- **Selos da crítica identificam a fonte** por sigla na cor da marca, e a cor do
  Metascore segue as faixas públicas do Metacritic em vez de um verde fixo.
- **Guia da chave OMDb**, compartilhando a máquina do guia do TMDb.
- **Android voltou a compilar.** Um apóstrofo sem escape em três textos em inglês
  fazia o `aapt` recusar o recurso e derrubava `mergeDebugResources`.

Verificação executada: `:apps:desktop:test` forçada limpa,
`:apps:android-tv:testDebugUnitTest`, as quatro suítes de `packages`,
`packageMsi` e `assembleDebug`.

## Estado da milestone 0.2 (auditoria de 12 de agosto de 2026)

## Estado da milestone 0.2

A milestone `0.2` amplia o vertical funcional publicado como prévia. Esta seção
registra arquitetura, escopo, gates, hashes e as limitações que ainda impedem
classificar o produto como uma versão estável.

### Validação em aparelho, rede e partida — 12 de agosto de 2026

Verificado no Xiaomi 25028RN03Y com build debug instalada. Três defeitos só apareceram no aparelho;
nenhum deles falhava em teste antes destas mudanças.

- **Compartilhar não aparecia.** O botão existia e estava ligado, mas ficava entre Favoritar e
  Baixar: cinco botões não cabem numa linha de telefone, a `FlowRow` quebrava e ele caía na segunda
  linha, fora do olhar. Movido para o fim da fila, ao lado de Trailer — e deliberadamente **fora**
  do bloco do trailer, senão sumiria em todo título sem `youtubeTrailerId`, que é a maioria numa
  lista Xtream;
- **link não abria título acentuado.** `searchFragment` lia a palavra do slug, que é sem acento
  (`O Sítio` → `sitio`), enquanto o SQL procura no `name` cru do provedor, que mantém `Sítio`. O
  `NOCASE` do SQLite dobra caixa mas não acento, então `%sitio%` não casava com nada e a linha nunca
  virava candidata. Passou a ler a maior sequência de letras sem acento do título compartilhado
  (`tio`), que é substring literal do nome escrito de qualquer jeito. Em catálogo português isso
  afetava a maior parte dos títulos;
- **link abria o app vazio.** Sem `launchMode`, o Android respondia cada VIEW empilhando uma
  *nova* `MainActivity` — a task chegou a três — e activity nova constrói `MainViewModel` novo, sem
  perfil e sem catálogo, então a resolução abortava e o app ficava na Home. `onNewIntent` nunca era
  chamado. Corrigido com `singleTask`, e coberto por `SharedLinkManifestTest`;
- **cleartext deixou de ser irrestrito.** `usesCleartextTraffic="true"` valia para *todo* host,
  incluindo servidor de licença e TMDb — caminho de downgrade em rede hostil. Um
  `network_security_config` mantém HTTP para as fontes do usuário (playlists e streams de provedor
  frequentemente são HTTP, e recusá-los recusaria o que o usuário tem) e nega cleartext para os
  domínios do próprio app;
- **composição da home saiu da main thread.** `viewModelScope.launch` despacha para Main; as seis
  consultas já estavam em IO, mas o dedup por regex, a ordenação e o mapeamento — cada um O(n) sobre
  tudo que elas retornam — rodavam na UI. Também havia `Regex(...)` compilado dentro de funções
  chamadas por item: `dailyCatalogTitleKey` compilava três por chamada, e era o seletor de um
  `distinctBy` sobre o catálogo inteiro. Todos içados para constantes;
- **as medições de partida em debug estavam erradas, e a conclusão delas também.** Foram medidos
  ~3,0 s de `TotalTime`, `classloader create took 1152ms` e sequências de 120 frames pulados, tudo
  em APK **debug**. Refeita a medição no `nonMinifiedRelease`, a partida é de **~380 ms**, o log não
  reporta **nenhum** frame pulado e a linha do classloader não aparece. Debug não passa por R8,
  carrega classes de instrumentação e não recebe perfil — não serve para medir partida, e o jank
  relatado antes era artefato do build, não defeito do app;
- **Baseline Profile adicionado** em `:apps:android-tv-baselineprofile` (plugin `androidx.
  baselineprofile` 1.5.0-rc01, exigido pelo AGP 9; a linha estável 1.4.1 recusa o módulo). Gerado
  contra o aparelho real, 15.471 regras. Comparação do mesmo APK com e sem o perfil aplicado, três
  execuções cada: mediana 401 ms → 379 ms, e **primeira** partida 548 ms → 379 ms (−31 %), que é
  onde o perfil age — nada está compilado logo após instalar. Procedimento de regeração e de
  medição em `apps/android-tv-baselineprofile/README.md`;
- memória: 211 MB PSS em debug contra ~177 MB numa medição posterior, mas com o app em estados
  diferentes — **não** trato como ganho comprovado;
- não houve regressão: 1.313 testes, 0 falhas, `lint` e `assembleDebug` aprovados, e o app inicia
  com `Status: ok`, sem crash e sem ANR. A validação final por captura de tela foi interrompida por
  travamento da `SystemUI` do aparelho, não do aplicativo.

### Compartilhar título e abertura por link — 12 de agosto de 2026

- as fichas de filme e de série ganharam **Compartilhar**, ao lado de Favoritar. O botão abre a
  folha de compartilhamento do sistema com título, ano, sinopse e um link `https`;
- o link **não carrega o id da linha do catálogo**. Esse id é gerado na importação da playlist e
  nomeia uma linha do banco de quem enviou — no aparelho de quem recebe ele não existe ou aponta
  para outro filme. O que viaja é o `ContentIdentity` (título normalizado, tipo e ano), e o app de
  destino recalcula essa identidade sobre o catálogo **dele**. Duas pessoas com provedores
  diferentes compartilham o mesmo filme e cada uma abre a própria cópia;
- **nada da fonte do remetente é transmitido**: sem URL de stream, sem endereço de portal, sem
  credencial. A capa só é incluída se estiver em host público de metadados (`image.tmdb.org` e
  afins); pôster hospedado no servidor do assinante é descartado, porque esse endereço costuma
  carregar usuário e senha no caminho. A regra é aplicada na montagem **e** na leitura do link, de
  modo que um link editado à mão não faz o app buscar host arbitrário. A sinopse é limitada a 400
  caracteres para o campo não virar canal de dados;
- quem recebe **com** o app e **com** o filme na lista vai direto para a ficha; quem tem o app mas
  não tem o título vê um aviso de que ele não está na lista, e o link fica pendente — importar a
  lista depois ainda resolve. Quem não tem o app cai na página `/t/`, que mostra capa e sinopse e
  oferece a instalação;
- um link tocado com o app fechado é retido até o catálogo terminar de carregar, senão a resposta
  "não está na sua lista" sairia antes de existir qualquer linha para procurar;
- ano diferente nunca casa: `Duna (1984)` e `Duna (2021)` são identidades distintas, e abrir o filme
  errado é pior do que não abrir nada. Linha sem ano declarado ainda casa, porque a maioria das
  listas Xtream não informa o campo;
- `site/t/` e `site/.well-known/assetlinks.json` foram adicionados, com CSP própria para `/t/*`
  liberando imagem apenas de `image.tmdb.org`;
- **pendência de release**: `assetlinks.json` está com o placeholder
  `REPLACE_WITH_RELEASE_SHA256_FINGERPRINT`. Enquanto não receber a impressão digital real do
  certificado de assinatura, o link abre no navegador em vez de abrir o app instalado. O
  procedimento está em `docs/release/production-signing.md`;
- gate local: 618 testes em `:packages:domain-model` e `:apps:android-tv`, 0 falhas e 0 ignorados;
  `lintDebug` e `assembleDebug` passaram; o manifesto mesclado foi conferido e traz os dois
  `intent-filter`. Não houve verificação em aparelho físico nesta etapa.

### Build Windows limpa e atualização — 12 de agosto de 2026

- a versão do binário e a versão comparada pelo atualizador passaram a ser a
  mesma (`2.0.0-alpha.1`), gerada pelo Gradle; isso evita uma UI anunciar uma
  versão enquanto o cliente compara outra;
- a chave TMDb de `local.properties` deixou de ser embutida por padrão. Existe
  opt-in apenas para execução local, e qualquer tarefa de distribuição falha se
  esse opt-in estiver ativo;
- o pacote é composto somente por código, recursos do produto e runtimes
  verificados. Playlists, fontes, credenciais DPAPI, perfis, histórico,
  downloads e demais dados do usuário não são entradas da build;
- `Verificar atualização` força revalidação no GitHub a cada clique, consulta
  `serafinnike-create/IPTVBURO`, inclui pré-releases, ordena semanticamente e
  recusa MSI sem digest SHA-256 ou hospedado em outro repositório;
- o workflow Windows agora exige os três segredos de assinatura, importa o PFX
  apenas no runner, assina launcher e MSI, verifica Authenticode e remove o
  certificado antes de publicar;
- a compilação revelou que `ChildProcessJob` usava APIs ausentes da interface
  `Kernel32` do JNA e ainda não era chamado. Uma interface Job Object explícita
  foi adicionada e cada VLC iniciado agora é adotado pelo job;
- gate local: 597 testes desktop em 106 suítes, 0 falhas, 0 erros e 0 ignorados;
  o probe real confirmou que a API pública do novo repositório é acessível; o
  CI do PR #1 também passou no runner Linux em 5 min 9 s;
- imagem distribuível limpa: 875 arquivos, 646.516.413 bytes; launcher
  SHA-256 `E5BDB7776E9898F752693C0E0780AF970186B8339C1366E4E7B57C1059E20F7C`.
  A auditoria não encontrou playlist, banco, keystore, chave TMDb embutida nem
  valores exatos da estação;
- **publicação bloqueada corretamente**: esta máquina não possui certificado de
  assinatura, `signtool` ou URL de timestamp configurados. Nenhum MSI sem
  assinatura será enviado ao GitHub.

### Pagamentos, ativação e assinatura — endurecimento de 10 de agosto de 2026

- o botão comercial Android agora usa Google Play Billing 9.1.0 e seleciona somente o produto
  `iptvburo_730_days`, opção de compra consumível `buy-730-days`; não abre Stripe no
  navegador;
- o app envia o token opaco ao Worker com prova P-256 vinculando instalação, nonce, hash do token e
  conta ofuscada. O app não concede nem reconhece a compra localmente;
- o Worker consulta `purchases.productsv2`, valida conta/produto/opção/aluguel/quantidade/estado,
  cifra o token em AES-GCM no D1, concede 730 dias e reconhece a entrega no servidor;
- pendência, restauração no mesmo aparelho, transferência atômica entre identidades da mesma conta,
  cancelamento e reembolso integral são cobertos. Uma reconciliação horária revoga mesmo sem o app
  voltar a abrir; erro temporário do Google não revoga por suposição;
- Stripe agora separa eventos live/test, limita corpos/rotas, mantém idempotência e garante que
  reembolso integral prevaleça sobre disputa. Formulários chunked também têm limite real de stream;
- Android release falha antes de compilar sem keystore protegido. O MSI direto é bloqueado; o script
  de release assina e verifica launcher e MSI com Authenticode e timestamp;
- validação local: 1.137 testes JVM, zero falhas/erros e três ignorados; Android lint com zero erros e
  52 avisos; 142 testes Worker/site, todos aprovados; dry-run do Worker aprovado;
- APK debug atual: 32.599.482 bytes, SHA-256
  `62B9EB4297A852916B16EAB9759590E7A7E92C887C09C9769CEC3658E61D7F8B`;
- aparelho Xiaomi 25028RN03Y: build `0.2.0-alpha.6-debug` instalada e processo em execução. Compra
  Play real não é validável por instalação ADB;
- **produção Android ainda bloqueada**: o Worker remoto ainda não possui os três segredos Google,
  produto/faixa Play e E2E fechado não foram confirmados, Cloudflare Access/MFA não protege
  `/admin*` e os certificados de release não foram fornecidos;
- o Worker endurecido foi implantado em produção como versão
  `dab4d7fe-6fae-4838-ac17-2b2782c86692`; a migration
  `0004_google_play_purchase_ledger.sql` foi aplicada preservando 4 dispositivos, 74 nonces e 3
  pagamentos. Health, preço EUR 9,90/730 dias, chave de assinatura, rejeição de webhook sem
  assinatura e ausência de CORS na rota Google foram confirmados após o deploy.

### Fundação Media SuperHub — Fase 0

- `MediaKind` universal e mappers legados foram adicionados sem substituir os
  tipos de vídeo;
- capabilities de mídia, playback e fonte possuem defaults conservadores e
  composição por interseção;
- `MediaIdentity` preserva as chaves de vídeo e cria namespaces opacos para
  faixa, álbum, artista, rádio, podcast, audiobook e capítulo;
- `packages/media-source-spi` define validação, scan, resolução tardia e eventos
  redigidos, sem alterar os adapters em produção;
- Media SuperHub / áudio: fundação de domínio em implementação; nenhuma
  vertical de usuário liberada.

### Aviso legal, espanhol e marca — 11 de agosto de 2026

- **Aviso legal reforçado**, agora em três parágrafos que respondem a três perguntas distintas:
  o que o aplicativo é, o que o usuário declara ao continuar, e quem responde pelo quê. Além do
  que já havia, o texto passou a afirmar que o app **não indexa nem busca** conteúdo, que **não
  vem com nada instalado**, que o usuário **declara** ter autorização, que **não contorna DRM,
  autenticação, paywall ou proteção técnica**, e que **não há vínculo** com provedores, emissoras
  ou serviços de streaming. A coluna passou a alinhar ao topo: centralizada, um texto mais alto
  que a tela começaria a ser lido pela metade;
- **Espanhol** adicionado como quinto idioma: sete arquivos de recursos traduzidos (217 textos),
  verificados por diferença de chaves contra o português — nenhuma faltando;
- **Rótulos fixos da tela inicial traduzidos.** `RealHomeCatalog` é um objeto puro sem Context e
  trazia os títulos dos trilhos e os selos em português no código, então a Home permanecia em
  português em qualquer idioma. Passaram a chegar por `HomeLabels`, resolvido na tela, que é onde
  há recursos. `HomeLabels.Fallback` mantém o texto antigo para testes e previews;
- **Tela de idioma** passou a empilhar os idiomas em coluna: cinco lado a lado em um telefone
  partiam "Português (Brasil)" em três linhas e "Español" no meio da palavra;
- **Marca real na tela de termos**, no lugar do triângulo genérico: o mesmo vetor do ícone do
  aplicativo, o anel dourado com o "B";
- **"Perfil ativo"**, último texto fixo em português nas telas, virou recurso.

Ressalva registrada: o texto do aviso foi escrito para reduzir risco, não por profissional do
direito. Antes de publicar em loja, convém revisão jurídica.

### Elenco, episódios, filmografia e banner — 11 de agosto de 2026

- **Fotos do elenco em séries.** A ficha de série nunca pedia as fotos, ao contrário da de filme —
  o mesmo `onRequestCastPhotos` foi ligado, e o rótulo "Elenco", fixo em português nos dois lugares,
  virou recurso traduzido;
- **Episódios assistidos** ganham um visto dourado sobre a miniatura quando terminados e uma barra
  de progresso quando parciais. O progresso é buscado com a mesma identidade que o player grava
  (`providerEpisodeId` como `contentId`);
- **Bug de escala encontrado no caminho, não relatado.** `PlaybackProgress.progressPercent` guarda
  uma **fração 0..1**, mas o nome sugere porcentagem e duas telas dividiam por cem outra vez: a
  ficha do filme mostrava "0% assistido" num filme metade assistido, e todo episódio pareceria
  recém-começado. Nada falhava — apenas desenhava o número errado. Corrigido nas duas, o campo
  documentado e um teste adicionado;
- **Séries em Continuar assistindo e no Histórico.** Episódios individuais não são gravados na
  tabela de canais (só séries), então a busca por id de episódio falhava sempre e o item era
  descartado em silêncio. Agora o episódio resolve para a série que o contém, que também é o que
  faz sentido mostrar. Seletor Todos/Filmes/Séries adicionado às duas telas;
- **Filmografia do ator.** As linhas eram inertes: tocar um filme não fazia nada, sem qualquer sinal
  de que não era um botão. `PersonCreditUi` passou a carregar o id do TMDb (que a resposta já
  trazia) e a linha abre "Onde assistir" — verificado com "Mortos S.A.", que listou Claro video,
  Apple TV Store, Amazon Video e Google Play. Créditos sem id continuam listados, mas não fingem
  ser clicáveis;
- **Sinopse real no banner.** O texto era uma frase genérica mandando abrir o título para saber do
  que se trata. As sinopses dos primeiros títulos da rotação são buscadas depois do READY (nunca
  antes: não vale segurar a tela inicial por isso), cobrindo filmes **e** séries — só filmes
  deixaria a maioria com a frase antiga — e cortadas em fim de frase quando possível;
- **Downloads** ganharam seletor Filmes/Séries e modo Compacto. O tipo é lido da própria chave
  (`|s<temporada>e<episódio>`), sem novo campo, para que downloads já existentes não ficassem com
  um valor errado;
- **Lançamentos** passaram a separar filmes e séries do ano corrente, em vez de exibir o ano
  anterior logo abaixo; o ano anterior continua como reserva para fontes com poucos títulos novos;
- **Botão de atualizar** segura o giro por 900 ms. Com prateleiras em cache o trabalho termina em
  menos de um quadro, e um indicador que aparece e some não é um retorno visual.

Limitação declarada: a recomendação por hábito pedida para o banner **não foi implementada**.
`ViewingTaste` existe em `packages/domain-model` com testes, mas a linha do catálogo (`ChannelUi`)
não carrega gênero, então não há do que derivar preferência sem uma consulta por título. O banner
segue com a rotação sazonal e diária.

### Player, guia da chave e auditoria de licença — 11 de agosto de 2026

- **Guia "Como consigo uma chave?"** no cartão TMDB: quatro passos numerados com diagramas
  desenhados (não capturas do site do TMDB, que seriam interface de terceiros, precisariam de
  quatro traduções e ficariam erradas no próximo redesenho deles) e um botão que abre o site;
- **Botão de formato da imagem** no player: Original, Preencher, Esticar e 16:9, alternados por
  toque. O rótulo atual fica no próprio botão;
- **Brilho e volume viraram seletores −/+** numa linha só, com a porcentagem no meio. Antes eram
  dois sliders de largura total, que custavam duas faixas de tela; o "+" do volume ainda saía pela
  borda direita, então a linha é uma `FlowRow` que quebra em vez de cortar;
- **Vídeo voltou a letterbox.** Uma tentativa de fixá-lo em 16:9 no topo cortava as laterais de
  qualquer filme mais largo — dar espaço aos controles se faz reduzindo os controles, não a imagem;
- **Erro de reprodução passou a dizer a verdade.** Um canal ao vivo falhava com "verifique a rede".
  O log revelou `ERROR_CODE_IO_BAD_HTTP_STATUS` com **HTTP 404**: o canal foi removido pelo
  provedor mas continua na lista. Todo erro de I/O virava "conexão"; agora 401/403/404/410 têm
  mensagem própria. O `PlaybackException` também passou a ser registrado (código e status, nunca a
  URL, que carrega credenciais) — antes não deixava rastro algum;
- **Auditoria de licença** (a pedido): o desenho está correto — bloqueio total antes de qualquer
  conteúdo, decisões contra a hora assinada pelo servidor e não a do aparelho, revogação imediata
  ignorando a tolerância offline, identidade com chave no Android Keystore. Tolerâncias offline:
  **14 dias** para licença paga, **2 dias** no teste. **Um furo real foi encontrado e corrigido**:
  o marcador `firstSeen`, que impede que reinstalar renove o teste, era gravado apenas no
  `SharedPreferences` do app — apagado na desinstalação, o que dava mais 7 dias a cada reinstalação.
  Passou a ser gravado também em armazenamento externo, que sobrevive. Não é à prova de tudo (o
  desktop tem a mesma limitação declarada) e pode falhar em Android 11+ por escopo de armazenamento.

### Correções de campo Android — perfis, catálogo e player — 11 de agosto de 2026

Rodada inteiramente reativa, a partir de defeitos relatados e reproduzidos no aparelho.

- **Criar perfil parecia impossível.** "Adicionar" estava numa `LazyRow` centralizada: com dois
  perfis ele era desenhado além da borda direita, sem nada indicando que a linha rolava. Trocado
  por `FlowRow`, que quebra para a linha de baixo. Mesmo defeito, mesma correção que os botões do
  player e da ficha de filme;
- **Criar ou trocar de perfil travava o app** em "Abrindo seu catálogo", indefinidamente.
  `observeProfiles` colocava `bootStage = CATALOGUE`, mas `loadHomeItems` — o único caminho até
  READY — só era chamado pela observação de fontes, que não reemite quando o perfil muda porque as
  fontes não mudaram. A Home passou a ser recarregada na troca de perfil, o que também é correto
  por si só: cada perfil pode apontar para uma lista diferente. A correção anterior (10 de agosto)
  tratava apenas o caso sem perfil ativo e não cobria este;
- **Categoria com contagem certa abrindo vazia.** "Series | Netflix · 1716 itens" mostrava "esta
  fonte não possui canais compatíveis". O `CatalogueFilter` nunca era limpo, então um gênero
  escolhido numa categoria continuava valendo na seguinte e zerava o resultado, enquanto a
  contagem do cartão — que não é filtrada — seguia prometendo mil itens. Limpo em
  `loadInitialChannels`, com teste de regressão;
- **Brilho não funcionava.** `window.attributes.apply { screenBrightness = value }` atribuía ao
  estado do composable, não a `LayoutParams.screenBrightness`: o nome era sombreado e a janela
  recebia os próprios atributos sem alteração;
- **Brilho e volume eram inalcançáveis.** Os dois sliders dividiam uma linha de 360dp com dois
  ícones, sobrando cerca de cem pixels para cada. Agora ocupam uma linha cada, com largura toda;
- **Volume não subia** com o aparelho já no máximo, porque só controlava o volume do sistema.
  Passou a controlar também o ganho do próprio player;
- **O botão de favoritar pulava de linha** ao ser tocado: "Favoritar" e "Favoritado" têm larguras
  diferentes e a `FlowRow` refluía. O rótulo mais longo é desenhado invisível por baixo, fixando a
  largura;
- **O vídeo nunca ficava em tela cheia** — a barra de status permanecia sobre a imagem. O player
  passou a esconder as barras do sistema e a restaurá-las ao sair (não na TV, onde já ficam ocultas
  no app inteiro);
- **Em retrato o player era desorganizado**: imagem estreita no meio e controles por cima dela.
  O vídeo agora é 16:9 ancorado no topo e os controles ficam abaixo, com lugar próprio. Em paisagem
  segue ocupando tudo;
- **Botão de atualizar lista** adicionado à barra de topo da Home. As telas de catálogo já tinham o
  seu; a Home, onde o app abre, não tinha nenhum;
- **Nome do perfil padrão** deixou de ser fixo em português.

### Paridade Android — Configurações, player e defeitos de campo — 10 de agosto de 2026

Rodada verificada no aparelho (Android 16, lista real de 41 mil itens). Tudo abaixo foi visto
funcionando em tela, não apenas compilado.

Novas funções, portadas do Windows:

- **Bloqueio de canais**: PIN de quatro dígitos em `CatalogueGuardPreferences`, sobre o
  `ParentalLock`/`ParentalPin` já existentes em `packages/domain-model` — a mesma regra que o
  Windows usa, então uma categoria tratada como adulta lá é tratada igual aqui. O PIN é guardado
  como sal + hash, nunca em claro. Trocar ou remover exige o PIN atual;
- **Ocultar categorias**: lista completa das 98 categorias da fonte, com switch por categoria.
  A lista de Configurações lê `allCategories` (sem filtro) e não `categories` (filtrada), senão
  ocultar uma categoria também removeria o switch que a traria de volta;
- **Legendas**: `SubtitlePresentation` novo em `packages/domain-model` (tamanho, cor, caixa escura),
  com ids estáveis e teste de round-trip. Aplicado no Media3 via `CaptionStyleCompat` mais
  `setApplyEmbeddedStyles(false)` — sem isso um stream com estilo próprio ignora a escolha;
- **Séries** ganharam favorito e trailer, que só filmes tinham. `AppContent.SeriesDetails` passou a
  carregar `channelId`, que é a chave dos favoritos;
- **Favoritos** ganharam seletor Filmes/Séries/Ao Vivo, oferecido apenas para os tipos presentes;
- **Cache diário das prateleiras de Assinaturas** (`ShelfCache`): o TMDB reconstrói essas listas uma
  vez por dia, então a segunda visita no mesmo dia lê do aparelho. Chaveado por dia, região e tipo;
  lista vazia nunca é gravada, para não fixar uma falha de rede pelo resto do dia. Salvar chave
  força `refresh`, trocar filtro não;
- **Trailer** na ficha "Onde assistir" — o id já era buscado e simplesmente nunca era desenhado.

Defeitos corrigidos:

- **Reprodução de download falhava sempre.** `PlaybackSessionFactory` usava `OkHttpDataSource`
  direto, que só fala HTTP, então um título baixado (`file://`) nunca abria. Agora é embrulhado em
  `DefaultDataSource.Factory`, que mantém o HTTP no OkHttp e adiciona file/content/asset.
  Verificado: série baixada reproduzindo;
- **Excluir o perfil ativo travava o app** em "abrindo seu catálogo". `observeProfiles` colocava
  `bootStage = CATALOGUE` incondicionalmente, então sem perfil ativo a tela de carregamento
  esperava um catálogo que não tinha para quem carregar. Teste de regressão adicionado;
- **Item de prateleira da Home abria o item demonstrativo.** O id `streaming:<serviço>:<id>` não é
  uma linha do catálogo, então caía no placeholder. Agora abre "Onde assistir" com a biblioteca do
  usuário em primeiro lugar;
- **Campos de texto em roxo com texto preto.** `OutlinedTextField` usa a paleta do Material, não a
  do tema. `BuroFieldColors` foi definido uma vez e aplicado aos cinco campos, para que o próximo
  campo adicionado não volte a nascer roxo;
- **Nome do perfil padrão estava fixo em português** (`"Meu perfil"`), então uma instalação em
  inglês, alemão ou italiano abria com esse nome. Passou a vir de `profile_default_name`.

Limitações desta rodada:

- a contagem de itens não é buscada para a lista de categorias em Configurações (seriam três
  consultas sobre centenas de categorias), então a linha é omitida em vez de mostrar "0 itens";
- o botão de trailer só aparece quando o TMDB devolve um trailer para aquele título;
- Ao Vivo não aparece no seletor de Favoritos enquanto não houver canal favoritado.

### Paridade Android — Assinaturas e chave TMDB — 9 de agosto de 2026

- `TmdbStreamingCatalogue` e `TmdbStreamingDiscovery` foram movidos de `apps/desktop` para
  `packages/metadata-client`. Android e Windows passaram a compartilhar a mesma implementação de
  "onde assistir" em vez de duas que podem divergir;
- o Android ganhou a tela **Assinaturas**: prateleiras por serviço, filtros de filmes, séries, esta
  semana e em breve, ficha com sinopse, elenco e ofertas por serviço;
- o destino aparece no ribbon e no menu lateral do telefone **somente quando existe chave TMDB
  configurada**, pela mesma `StreamingDiscoveryCapability` do Windows. `selectSection` repete o
  guard, então nenhuma outra rota abre a tela vazia;
- cada linha de oferta carrega o crédito `Streaming data provided by JustWatch`, exigido por item
  pelos termos deles; a biblioteca do próprio usuário não recebe esse crédito;
- nenhuma oferta recebe preço, porque o TMDB não devolve preço em bucket algum. Disponibilidade
  desconhecida é apresentada como "não podemos dizer", nunca como "indisponível";
- a chave é lida do armazenamento cifrado apenas no ponto de uso, nunca fica no estado de UI e a
  classe do repositório não é `data class`, para não vazar por `toString()`;
- o card de chave TMDB nas Configurações deixou de renderizar um campo desabilitado quando não há
  perfil ativo. Ele agora explica que a chave é cifrada por perfil e oferece o seletor de perfis;
- textos novos em PT-BR, EN, DE e IT;
- **limitação**: o corte passou em teste, lint e APK, mas não foi exercitado contra a API do TMDB
  com uma chave real nem inspecionado em aparelho físico.

#### Correção do fluxo da chave — 10 de agosto de 2026

O relato do usuário foi "coloquei chave TMDB e nada aconteceu". Eram quatro defeitos distintos,
todos corrigidos adotando o modelo que o Windows já usava:

1. **Salvar a chave não recarregava nada.** O Windows chama `rebuildMetadataClients()` ao salvar; o
   Android apenas atualizava um booleano. O destino aparecia e ficava vazio até reiniciar o app.
   `saveTmdbKey` agora limpa o cache da chave anterior e dispara `loadSubscriptionShelves(force)`.
2. **Não havia chave embutida.** O Windows lê `tmdb.apiKey` de `local.properties` em tempo de build,
   e por isso funciona sem configuração. O Android passou a gerar o mesmo `BuildConfig` a partir do
   mesmo arquivo — que continua no `.gitignore`, então a chave não entra no repositório.
3. **A resolução de chave estava duplicada em quatro lugares**, cada um consultando somente a chave
   do perfil. Trailer, elenco, capability e Assinaturas podiam discordar entre si. Agora existe um
   único `StreamingDiscoveryRepository.effectiveKey`: chave do perfil, senão a da build. Limpar o
   campo volta ao padrão em vez de desligar os metadados.
4. **A tela Perfis não falava da chave**, que é onde o usuário foi procurar. Ela ganhou um atalho
   para Configurações; o seletor inicial não o mostra, porque ali ainda não existe perfil onde
   guardar a chave.

O invariante entre capability e resolução de chave está travado por teste: o destino é visível
exatamente quando uma chave será encontrada na hora do pedido.

#### Verificação em aparelho físico — 10 de agosto de 2026

Instalado e exercitado num Xiaomi 25028RN03Y, Android 15, arm64-v8a. Assinaturas abriu com dados
reais do TMDB, sem configuração nenhuma, e a ficha mostrou pôster, nota, duração, gêneros, ofertas
com o crédito JustWatch por item, sinopse e elenco com fotos.

A execução no aparelho revelou dois defeitos que teste, lint e APK não pegam:

- **Nenhuma imagem carregava em todo o app.** O Coil 3 separou a rede em artefato próprio e não
  registra um fetcher sozinho; sem isso, todo modelo `http(s)` falha silenciosamente enquanto os
  locais continuam funcionando. `IptvBuroApplication` passou a implementar
  `SingletonImageLoader.Factory` com `OkHttpNetworkFetcherFactory`, cache de memória proporcional ao
  heap do processo e cache em disco de 128 MB. Isso conserta capas do catálogo, fotos de elenco e as
  prateleiras de Assinaturas de uma vez;
- **O botão "Serviços" ocupava a linha inteira** e empurrava o título para fora da tela.
  `FocusSurface` propaga as constraints mínimas ao conteúdo, então um filho com `fillMaxSize` cresce
  até a largura da linha. Corrigido no botão e nos chips de filtro com `wrapContentWidth`.

#### Segunda verificação em aparelho — 10 de agosto de 2026

Rodada de verificação visual, tela a tela, no mesmo Xiaomi. **Confirmado funcionando**: herói da Home
rotacionando entre destaques; chip de licença no menu lateral com a contagem de dias em vermelho nos
últimos sete; guias Continuar assistindo e Histórico; Assinaturas mostrando `IPTV BURO — Já está na
sua biblioteca` como primeira oferta e abrindo o título dentro do app; editor de perfil com foto real
do usuário **persistida após salvar**, o que exercita a migration 6→7 no aparelho e não apenas em
teste; player com barra de tempo, arraste funcionando e sliders discretos.

Três defeitos de layout foram encontrados e corrigidos nesta rodada:

- **o alternador de formato não mudava nada de visível.** `COMPACT` usava `GridCells.Adaptive(104.dp)`,
  que numa tela de 360 dp ainda resolvia para duas colunas — idêntico a `POSTER`. E `LIST` trocava a
  contagem de colunas enquanto o card impunha proporção 2:3, desenhando um pôster por tela. Agora
  `COMPACT` usa três colunas fixas e `LIST` tem desenho próprio: miniatura à esquerda, nome com
  espaço para ser lido;
- **os menus suspensos de filtro abriam quase brancos**, porque `DropdownMenu` do Material desenha a
  própria superfície. Passaram a usar a paleta do app;
- **faltavam controles no player.** Mesma causa dos botões da ficha de filme: uma `Row` sem quebra,
  com os últimos itens — legendas e favorito — desenhados além da borda direita e inalcançáveis.
  Trocada por `FlowRow`.

#### Progresso de reprodução gravado como concluído — 10 de agosto de 2026

Defeito encontrado inspecionando o banco do aparelho, não por teste. Após assistir quatro minutos de
um filme de 112 minutos, a linha gravada era:

```text
pos=112.2min  dur=112.2min  pct=1.000  completed=YES
```

**Causa.** O ExoPlayer devolve `currentPosition == duration` depois de `stop()` ou de ter chegado ao
fim. Três dos quatro checkpoints do player — `onDispose`, `ON_STOP` e o botão Voltar — liam a posição
exatamente nesse instante. Só o checkpoint periódico, que exige `isPlaying`, estava correto.

**Efeito.** Todo título saía do player marcado como assistido por inteiro, e por isso *Continuar
assistindo* nunca tinha nada. Pior: `SavePlaybackCheckpointUseCase` recusa mover para trás um registro
concluído — regra correta, para um retrocesso nos créditos não desfazer a conclusão — de modo que um
título falsamente concluído fica preso assim permanentemente. O registro observado tinha revisão 562.

**Correção.** O player passou a amostrar a posição a cada segundo *enquanto está realmente tocando* e
os checkpoints de saída usam esse valor, em vez de perguntar ao player num momento em que ele já não
sabe responder. Coberto por `PlaybackCheckpointPositionTest` no domínio.

**Limitação.** A correção está compilada, instalada e coberta por testes, mas **não foi confirmada em
execução**: o teste que a confirmaria exigia estado limpo, e a limpeza apagou a playlist do aparelho.
A confirmação depende de reimportar uma lista e repetir o cenário — assistir dois minutos, sair, e
conferir se o título aparece em *Continuar assistindo* com a porcentagem real.

### Atualização operacional — 9 de agosto de 2026

- `services/license-server` está rastreado no Git e o Worker Cloudflare responde
  `200` em `/health`. A base D1 remota não possui migrations pendentes;
- o contrato comercial vigente está formalizado no ADR-010: sete dias de teste,
  pagamento único por dispositivo e entitlement de 730 dias, com preços-base de
  EUR 9,90, USD 9,90 e BRL 99,90. Não é uma licença vitalícia;
- o cliente Windows registra uma identidade criptográfica, assina cada operação,
  valida a concessão assinada, aplica a política offline e apresenta compra,
  QR code, resgate de chave e nova verificação. A tela bloqueada reutiliza o
  mural cinematográfico de capas e mantém as ações visíveis em 1280 × 780;
- o Worker implementa e testa os cinco eventos necessários no endpoint Stripe:
  `checkout.session.completed`, `checkout.session.async_payment_succeeded`,
  `charge.refunded`, `charge.dispute.created` e `charge.dispute.closed`;
- a identidade Android assina a prova de posse P-256 e o cliente valida a
  concessão Ed25519 também nas APIs antigas pelo provider criptográfico incluído;
  a integração Google Play está no código, mas sua ativação externa/E2E continua
  bloqueada pelos gates listados acima;
- gate Gradle mais recente: 1.137 testes, 0 falhas, 0 erros e 3 ignorados; lint
  Android com 0 erros e 52 avisos;
- Worker e site: 142 testes Node, 142 passaram e nenhum falhou;
- APK debug: 26.482.020 bytes, SHA-256
  `59C4B7C6CA8F1EA02FC17C86F1BFC7ADFD6CBC3544F97391F1525D2B394F4E53`;
- AAB release: 7.530.964 bytes, SHA-256
  `6F25B20D0FB0F546A769767800ACE440F4F97D134C7C86BD63BB80386E72EE3D`;
- MSI Windows 2.0.0: 299.807.971 bytes, SHA-256
  `C794F5E89A7CE4530BB6A049274F9F34894651672CEF0DF0BDF7A3177E65CC0D`.
  O banco MSI é legível, contém 876 arquivos e identifica produto `IPTVBURO`,
  versão `2.0.0` e fabricante `IPTV BURO`;
- o MSI ainda não possui assinatura Authenticode e o APK é uma build debug.
  Ambos são candidatos locais, não artefatos públicos de produção;
- a assinatura do endpoint e o fluxo de disputa possuem cobertura automatizada,
  mas ainda falta confirmar no painel Stripe os cinco eventos e executar o E2E
  sandbox compra → webhook 200 → ativação → reembolso/disputa;
- a rota administrativa exige token, mas Cloudflare Access/MFA ainda precisa de
  uma política de identidade definida antes de ser configurado.

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
- o downloader genérico Windows foi removido. Este item descrevia a política
  anterior, substituída pelo
  [`ADR-008`](../adr/ADR-008-UNRESTRICTED-VOD-DOWNLOAD.md): o proprietário liberou
  download de VOD sem a fonte declarar autorização offline, e a capability
  Android `offline.supported` passou a `true` para telefone. TV ao vivo continua
  recusada, Android TV continua sem o destino, e nenhuma URL assinada vai para o
  disco, log ou nome de arquivo;
- domínio de entitlement e identidade criptográfica de instalação Android foram
  adicionados; o Worker Cloudflare e o Checkout Stripe para Windows/portal estão
  implantados em preview. Google Play Billing está integrado localmente e aguarda
  Play Console, segredos, migration remota e E2E fechado;
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
