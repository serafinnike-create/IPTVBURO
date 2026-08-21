# Changelog

Todas as mudanças relevantes do IPTV BURO serão registradas neste arquivo.

## [3.0.6] - 2026-08-22 (Windows)

### Windows

#### Corrigido

- **A tela inicial podia ficar montando o esqueleto para sempre.** Mesmo defeito
  corrigido na 3.0.5 para a ficha do filme e para o guia ao vivo, agora na tela
  que todo mundo vê: o estado *ocioso* dividia o ramo com o *carregando*, e o
  efeito que carrega a Home observa o perfil, a fonte e o contador de
  atualização — nunca o estado. Uma carga cancelada voltava para ocioso e nada
  rodava de novo.
- **As últimas frases em português da tela de detalhes** — guia indisponível,
  sem programação, "Carregar episódios", "Carregando episódios…" e dois botões
  "Tentar novamente" — passaram a respeitar o idioma escolhido.

## [3.0.5] - 2026-08-21 (Windows)

### Windows

#### Corrigido

- **A ficha do filme ficava "carregando" para sempre.** Era o problema relatado
  ao abrir um título pela tela Descobrir. A causa estava no desenho da tela, não
  na busca: o estado *ocioso* dividia o mesmo ramo com o estado *carregando*.
  Quando a busca era cancelada — o que acontece sempre que o efeito que a
  iniciou é recomposto, e de forma previsível por aquele caminho — o estado
  voltava corretamente para ocioso, mas a tela continuava mostrando o mesmo
  círculo girando e o mesmo "Carregando ficha do filme…", sem nada em
  andamento e sem nada que disparasse uma nova tentativa.

  Agora a tela pede de novo sozinha, já que você pediu ao abrir o filme, e
  mostra um botão "Tentar novamente" desde o início — porque a situação que
  esse botão existe para resolver é justamente aquela em que a tentativa
  automática não chega.
- **O guia "agora e próximo" dos canais ao vivo tinha exatamente o mesmo
  defeito**, e recebeu o mesmo tratamento.

## [3.0.4] - 2026-08-21 (Windows)

### Windows

#### Corrigido

- **A senha do controle interno do VLC saía na linha de comando.** No Windows,
  qualquer processo rodando com o seu usuário consegue ler a linha de comando de
  outro. Essa senha abre a interface de controle do VLC, cujo relatório de estado
  nomeia o vídeo em reprodução — que, numa lista de provedor, carrega o seu
  usuário e a sua senha. O endereço do vídeo já era protegido; a senha que o
  guardava, não. Agora ela vai num arquivo de configuração que só a sua conta lê,
  criado com as permissões restritas **antes** de o segredo ser escrito, apagado
  ao fechar o player e registrado para remoção mesmo se o aplicativo travar.

## [3.0.3] - 2026-08-21 (Windows)

Versão de correção urgente. **A 3.0.2 foi retirada do ar**: atualizar por ela
apagava o aplicativo.

### Windows

#### Corrigido

- **Atualizar pelo aplicativo apagava o IPTV BURO.** O download terminava, o
  instalador rodava e o aplicativo sumia — sem versão antiga, sem versão nova,
  sem entrada em "Adicionar ou remover programas". O registro de eventos do
  Windows mostrou uma única transação, sem remoção, terminando em 1603 por causa
  do erro 1316. O script passava `REINSTALLMODE=amus`, que pede ao Windows para
  *reparar um produto instalado*; como cada build recebe um ProductCode novo,
  ele apontava para um produto que não existia, e a transação voltou atrás
  levando o aplicativo junto. O MSI já é uma atualização completa por si só, e
  agora é instalado como tal.
- **O sucesso passou a ser verificado no disco.** Um código de saída zero não
  prova nada: uma transação revertida também termina em silêncio. Agora o
  executável é procurado antes de qualquer coisa ser apagada ou declarada
  concluída, e se ele não estiver lá o usuário é avisado e recebe o instalador
  para tentar de novo.
- **Os números do relógio saíam em outro alfabeto.** Num Windows configurado
  para Egito, Irã, Bangladesh ou Mianmar, o tempo de reprodução aparecia como
  `١:٠٥:٠٩` em vez de `1:05:09`, dentro de uma interface em português. O mesmo
  valia para avaliações, contagens e tamanhos.
- **O player mais bufferizado recebia a menor tolerância antes de reconectar**,
  por um estouro de inteiro. Não era alcançável pelos valores que o aplicativo
  usa, mas a conta estava invertida.

#### Melhorado

- **A tela de atualização mostra velocidade e tempo restante.** Antes havia
  apenas a porcentagem e os megabytes, e num download de 322 MB não dava para
  distinguir uma transferência lenta de uma travada. Agora lê
  "Baixando 61% (197 / 322 MB) · 4,0 MB/s · 31s", e a velocidade cai em segundos
  quando a transferência para de verdade.

## [3.0.2] - 2026-08-21 (Windows) — RETIRADA

> [!CAUTION]
> Esta versão foi despublicada. Atualizar por ela apagava o aplicativo: o
> instalador passava `REINSTALLMODE=amus`, o Windows revertia a transação e o
> produto ficava sem registro. Use a 3.0.3. Se você ficou sem o aplicativo,
> baixe e instale o MSI da 3.0.3 manualmente.


Uma versão só de tradução. Nenhuma mudança de comportamento no vídeo, no
catálogo ou no player — apenas as mensagens que ainda estavam presas em
português.

### Windows

#### Corrigido

- **As telas diziam algumas frases sempre em português, qualquer que fosse o
  idioma escolhido.** Eram 93 no total, terminadas nesta versão: a falha ao
  importar uma lista, as duas recusas do player externo, o estado vazio da lista
  de canais, os avisos de cabeçalhos HTTP, o título do login Xtream, a busca na
  filmografia e as três falhas do próprio motor de vídeo. Um espectador alemão
  cujo stream morria recebia uma frase em português.
- **As falhas do motor de vídeo eram `const val` num companion**, que é o único
  lugar onde um idioma não chega. O player, a superfície de multiview e o
  atualizador passaram a receber o texto por construtor, e o player é
  reconstruído quando o idioma muda — antes continuaria respondendo no idioma
  anterior.
- **A falha genérica de importação não tinha tradução nenhuma**, nem sequer uma
  entrada; ganhou uma.

#### Interno

- A guarda `NoHardcodedTextTest` cobre agora todas as telas menos uma: um
  catálogo de demonstração alcançável apenas por um teste, que nenhum usuário lê.
  A verificação foi confirmada plantando um literal em português e observando o
  teste falhar — essa mesma guarda já passou por cima de uma isca porque `"\b"`
  numa string Kotlin é um backspace, não uma fronteira de palavra.

#### Nota sobre o nome do arquivo

O MSI da 3.0.2 se chama `IPTV-BURO-v3.0.2-windows-x64.msi`, sem o sufixo
`-unsigned` que as versões anteriores traziam. **O arquivo continua sem
assinatura Authenticode.**

O atualizador embutido decide pelo nome: uma build estável recusa qualquer
instalador cujo nome contenha `-unsigned` ([`GitHubReleaseUpdater.kt`][updater],
linha 101). Como a 3.0.1 é estável, ela nunca ofereceria a 3.0.2, e o fluxo de
atualização pelo app não podia ser testado. O nome foi encurtado para permitir
esse teste.

A regra em si não foi alterada, e o teste `stable installs ignore unsigned
stable assets` continua valendo — apenas este arquivo deixou de casar com ela.
A correção de verdade é assinar o MSI; enquanto isso não acontece, quem instalar
a 3.0.2 deve conferir o `SHA256SUMS.txt`.

[updater]: apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/update/GitHubReleaseUpdater.kt

## [3.0.1] - 2026-08-18 (Windows e Android)

As duas aplicações passam a compartilhar a numeração. O Android continua em
prévia — o APK é de depuração e não é uma versão de loja.

### Windows

#### Corrigido

- **O filtro por serviço agora funciona numa lista que não nomeia serviços.**
  O seletor lia os nomes das categorias, e uma lista que arquiva filmes como
  "Filmes | Ação" não lhe dava nada — medido na lista do relator, todas as 31
  categorias de filmes eram gêneros, contra 6 serviços em Ao vivo. A versão
  anterior apenas explicava o problema em vez de resolvê-lo. Agora a pergunta é
  feita à TMDb, e ao contrário: cada serviço é perguntado sobre o que **ele**
  carrega, e esses títulos são casados com a biblioteca por nome normalizado
  mais ano. A contagem ao lado de cada serviço mostra quantos títulos casaram,
  para que a cobertura fique visível em vez de subentendida.
- **Voltar de um título aberto em Descobrir devolvia ao catálogo.** Abrir um
  título sempre leva ao catálogo, porque é a única tela que sabe carregar a
  ficha; a rota agora lembra de onde veio.
- **Os padrões de segurança do perfil Kids eram compilados a cada título.**
  `isAllowedForKids` roda para o título e para cada categoria de cada linha, e a
  paginação a chama para dezenas de milhares de itens — uma virada de página
  compilava os mesmos dois padrões dezenas de milhares de vezes.

#### Melhorado

- **A barra de carregamento se move enquanto o catálogo baixa.** Antes ela
  anunciava três marcos fixos, e o salto do primeiro para o segundo cobria todo
  o download — em uma lista real, dezenas de segundos parada em 80%, que lê como
  travamento. Agora a contagem e a velocidade aparecem, e se o progresso parar
  por um instante a barra deixa de afirmar uma porcentagem e passa a uma
  animação, que é honesto sobre não saber em vez de mentir sobre estar preso.

### Android

- **A tela de carregamento mostra as capas do próprio catálogo** em vez de um
  fundo genérico, e informa a velocidade de chegada da lista.
- **A tela de carregamento deixou de recompor sessenta vezes por segundo.**

## [3.0.0] - 2026-08-18 (Windows)

Primeira versão do Windows publicada sem o rótulo de prévia. O que mudou desde
a `2.0.0-alpha.12` são correções de uso diário, quase todas relatadas por quem
estava usando o aplicativo de verdade.

### Corrigido

- **Voltar de um filme devolvia à primeira página.** Avançar para a segunda
  página do catálogo, abrir um título e voltar levava de volta ao início, com a
  posição de rolagem perdida. Em quarenta mil títulos, tudo além dos primeiros
  oitenta só se alcança paginando, então essa caminhada tinha de ser refeita a
  cada filme. Eram três causas: a busca reaplicava-se e forçava a página zero, a
  posição guardada era lida com a chave da lista errada, e o salto para o topo
  descartava o que tinha acabado de ser restaurado.
- **A secção FONTES parecia não existir.** A barra lateral rolava, mas sem
  qualquer barra visível — e rolagem que não se anuncia é, para quem usa, o
  mesmo que não rolar. Só aparecia ao pôr a janela em ecrã inteiro.
- **Um travamento à espera de acontecer.** A lista de fontes era uma lista
  preguiçosa dentro de outra rolagem no mesmo eixo, o que o Compose não
  consegue medir. Ninguém tinha esbarrado porque é preciso ligar uma segunda
  playlist para chegar lá.

### Melhorado

- **Virar página ficou mais barato.** O catálogo construía um objeto completo
  para cada linha só para ler um campo — inclusive para linhas que a categoria
  já tinha excluído — e descodificava a lista de categorias de todas as linhas
  quando, no caso normal, ninguém a lê. Uma página é virada a cada tecla
  digitada na busca, portanto é o custo mais pago do aplicativo.

## [2.0.0-alpha.12] - 2026-08-17 (Windows)

### Corrigido

- **Um rótulo espremido quebrava uma letra por linha.** Em Configurações, um
  `Row` entrega toda a largura ao primeiro texto e deixa o resto para o último,
  e um texto com poucos pixels não corta com "…": ele quebra, caractere por
  caractere, numa coluna vertical na borda do painel. Aconteceu em dois lugares
  independentes — o link "Não sabe como obter?" ao lado da dica do TMDb, e o
  último tamanho de cache, com "16 GB" virando `1/6/G/B`. Ambos agora usam
  `FlowRow`, e as pilhas de opção recusam quebra na própria definição, para que
  uma opção adicionada depois não reintroduza o problema.
- **A nota do público era atribuída à Netflix.** O painel de avaliações buscava
  uma imagem de uma constante chamada `TMDB_MARK_URL`, documentada como "a marca
  da própria TMDb". Não era: aquele caminho é de logo de **serviço de streaming**
  no CDN da TMDb, e o arquivo por trás dele é a marca da Netflix. O resultado era
  o logo da Netflix ao lado das palavras "Nota TMDb" — uma média de usuários da
  TMDb apresentada como veredito de uma empresa que não participou dela.
- **As Configurações não podiam ser abertas pela barra lateral.** A coluna de
  navegação tem quinze destinos e era um `Column` de altura fixa sem rolagem:
  numa tela de 1536x816 — um notebook comum — Assinaturas, Perfil e
  Configurações ficam abaixo da borda inferior, sem qualquer forma de alcançá-los.
- **A cor do Metascore contradizia a nota.** O selo era verde fixo, então
  anunciava "favorável" ao lado de um 32. Agora segue as faixas públicas do
  Metacritic: verde a partir de 61, amarelo de 40 a 60, vermelho abaixo disso.

### Alterado

- **Filmes e Séries trocaram a fileira de categorias por dois seletores.** A
  fileira mostrava todas as categorias da lista num único trilho horizontal —
  trinta e tantas em uma assinatura real, misturando "Acao" e "Aventura" com
  "Netflix" e "Amazon" — de modo que duas perguntas diferentes eram respondidas
  no mesmo lugar e responder uma exigia rolar pela outra. Agora há **Gênero** e
  **Serviço**, cada um nomeando a pergunta que responde, com a marca de cada
  serviço ao lado do nome.
- **Os selos da crítica agora trazem a sigla da fonte.** Eram três bolinhas
  coloridas, que não identificam nada para quem não decorou o código; agora são
  `RT`, `MC` e `IMDb` na cor de cada marca. Desenhados no aplicativo, não
  baixados: as marcas do Rotten Tomatoes e do Metacritic são licenciadas e não
  têm endereço público de onde buscá-las.

### Adicionado

- **Guia passo a passo para a chave OMDb.** A dica dizia apenas "Pegue a sua em
  omdbapi.com", o que pressupõe saber que o site pede um e-mail em vez de uma
  conta, que o plano gratuito é um botão "FREE" e — o passo que as pessoas de
  fato perdem — que a chave chega por e-mail atrás de um link de ativação. Sem
  abrir esse link a chave não funciona e nada na tela explica por quê. Mesmo
  mecanismo do guia do TMDb, deliberadamente compartilhado com ele.

## [2.0.0] - 2026-08-07 (Windows)

### Corrigido

- **O atualizador oferecia a própria versão em execução como atualização.** A
  versão publicada é escrita com dois números (`1.1`, `2.0`) e o comparador
  exigia três, então a versão atual não era reconhecida e qualquer release
  parecia mais recente. Afeta quem já tem a 1.1 instalada.
- **O índice de plugins do VLC era descartado a cada abertura.** Ele era gerado
  durante o build e o instalador copiava os plugins para outro diretório com
  novas datas, o que invalidava cada entrada — 363 erros `stale plugins cache`
  numa instalação real. Agora é gerado uma vez, na primeira execução, onde os
  plugins ficam de facto. Verificado: 0 erros na instalação da 2.0.
- **Um filme começado e abandonado deixava a tela presa a carregar.** Seis
  carregadores tinham a mesma falha: uma requisição cancelada não repunha o
  estado, e a proteção contra requisições duplicadas recusava toda tentativa
  seguinte. Dois deles eram novos (importação de lista e a tela inicial) e um
  impedia a app de se ligar a qualquer conta depois de uma ligação cancelada.
- **Um download vazio era guardado como concluído.** Um servidor que respondia
  200 sem conteúdo produzia um ficheiro de 0 bytes que a biblioteca listava como
  baixado e oferecia para assistir.
- **Buscar no histórico ignorava acentos.** "chefao" não encontrava
  "O Poderoso Chefão".
- **Uma categoria oculta não podia ser reexibida.** A lista de opções mostrava
  apenas as categorias visíveis, então ocultar uma removia-a do próprio
  interruptor que a ocultava.
- **A chave da API do TMDb podia ser impressa na consola.** O TMDb recebe a
  chave como parâmetro de URL e o OkHttp inclui o URL completo nas mensagens de
  erro; uma linha de log imprimia essa mensagem.
- **A tela de histórico travava a interface por mais de quatro segundos.** A
  busca por título percorria o catálogo inteiro construindo um objeto por linha
  — 41.698 objetos por consulta, 200 consultas por tela. Medido: 4238 ms antes,
  56 ms na primeira abertura e 0 ms depois.
- **Trocar de filtro em Assinaturas enquanto as prateleiras carregavam deixava
  a seção vazia pelo resto da sessão.** A marca de "carregando" não era limpa no
  caminho que sai cedo, e a proteção contra carregamentos duplicados recusava
  todos os seguintes.
- **O cache de prateleiras era um mapa comum partilhado entre threads.** Escrito
  pelo carregador em segundo plano e lido pela interface; um `HashMap` sob acesso
  concorrente pode corromper-se, e o sintoma clássico é uma consulta que nunca
  retorna.
- **Requisições ao TMDb continuavam em voo depois de fechar a janela**, segurando
  o processo aberto até expirarem.

### Adicionado

- Controle parental completo: PIN de 4 dígitos ao abrir uma categoria
  bloqueada, e o conteúdo bloqueado deixa de aparecer na busca e nas listagens
  gerais — não apenas na barra de categorias.
- Histórico como galeria de capas, com busca e "apagar tudo".
- Tela de preparação na primeira execução, com percentagem, aviso de que só
  desta vez demora mais, e explicação de como obter a chave gratuita do TMDb.
  Traduzida nos quatro idiomas.

### Alterado

- O instalador ficou 7 MB menor: o player JavaFX, substituído pelo VLC há
  várias versões, foi removido junto com as quatro dependências que arrastava.
- A tela de opções já não escurece a aplicação por trás.
- Paginar o catálogo ficou três vezes mais barato: os testes que descartam uma
  linha passaram a ler duas colunas em vez de construir o objeto inteiro para
  depois deitá-lo fora. Medido sobre 41.698 itens: 31 ms antes, 10 ms depois.
- Verificar quais categorias estão bloqueadas passou a ler as preferências uma
  vez por página, em vez de duas vezes por categoria.

## [Unreleased]

### Added

- Candidato Windows `2.0.0-alpha.1` com versão única compartilhada pela UI e
  pelo atualizador.
- Gate de distribuição que recusa uma build quando a chave TMDb da estação de
  desenvolvimento estiver habilitada.
- Pipeline de release Windows que importa o certificado apenas no runner,
  assina launcher e MSI, valida Authenticode e remove o certificado ao terminar.

### Fixed

- O botão de atualização passou a consultar sem cache o repositório atual
  `serafinnike-create/IPTVBURO`; URLs de instaladores pertencentes a outro
  repositório GitHub são recusadas.
- A proteção contra processos VLC órfãos agora usa uma interface nativa Job
  Object compatível com JNA e é aplicada a cada processo iniciado pelo player.

### Changed

- O CI da `main` mantém `test`, `lint` e `assembleDebug` como gate, enquanto os
  APKs para download ficam centralizados em GitHub Releases para não depender da
  cota temporária de artefatos do Actions.
- Builds desktop não incorporam mais automaticamente a chave TMDb de
  `local.properties`; cada utilizador configura a própria chave por perfil.

## [0.1.0-alpha.1] - 2026-07-31

### Added

- Fundação do monorepo Android TV.
- Importação local M3U/M3U8, catálogo Room e player HLS com Media3.
- BURO Ribbon com oito destinos.
- Living Home com hero, fileiras sintéticas DEMO e fileira de fontes reais sem
  expor URLs na camada visual.
- Story demonstrativa sem playback e placeholders explícitos para destinos
  futuros.
- BURO Cinematic design system com tokens semânticos, componentes focáveis,
  tiers `Auto`, `Eco`, `Balanced` e `Cinematic`, reduced motion, high contrast e
  reduced transparency.
- Restauração mínima de foco da Home e comportamento `Back → Ribbon`.
- Localização em português do Brasil, inglês, alemão e italiano.
- Documentação de arquitetura, segurança, release e estado real da milestone.

### Changed

- A sidebar e o dashboard técnico foram substituídos pela primeira Living Home.
- Configurações passaram a ser acessadas pelo Perfil.
- Controles de seek permanecem condicionados à capacidade real informada pelo
  Media3.

### Validated

- `./gradlew test lint assembleDebug` aprovado: 55 testes JVM, 0 falhas, lint
  com 0 erros e 18 warnings não bloqueantes.
- APK debug local com 25.433.893 bytes e SHA-256
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`.
- Fluxo E2E no Redmi A5 com Android 15 usando a playlist HLS pública Apple
  BipBop: importação, navegação, primeiro frame e áudio/vídeo sem crash.

### Release status

- Implementação enviada à `main` no commit `2c9bd5b`.
- Tag `v0.1.0-alpha.1` publicada no commit `7e0b9ec`.
- [GitHub Pre-release](https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.1.0-alpha.1)
  publicada pelo workflow
  [`Publish Android preview` — run 30590918504](https://github.com/lucasserafin94/IPTVBURO/actions/runs/30590918504).
- [APK publicado](https://github.com/lucasserafin94/IPTVBURO/releases/download/v0.1.0-alpha.1/IPTV-BURO-v0.1.0-alpha.1-android-debug.apk):
  `IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`, 24.864.542 bytes, SHA-256
  `179537447d53ef062daf9cd100b5ed52416be796ceedb61cb64601a930965dc6`.

### Known limitations

- GDD 2.0 ainda parcial; GDD 3.0 e GDD 4.0 não implementados.
- Busca, perfis, catálogo real de filmes/séries, EPG/XMLTV e Xtream pendentes.
- Sem testes instrumentados de D-pad ou testes de screenshot/golden.
- Tokens ainda não cobrem integralmente a Home e telas legadas.
- URLs de stream continuam armazenadas em texto simples no Room.

[Unreleased]: https://github.com/lucasserafin94/IPTVBURO/compare/v0.1.0-alpha.1...HEAD
[0.1.0-alpha.1]: https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.1.0-alpha.1
