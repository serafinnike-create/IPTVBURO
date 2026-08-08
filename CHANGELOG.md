# Changelog

Todas as mudanças relevantes do IPTV BURO serão registradas neste arquivo.

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

### Changed

- O CI da `main` mantém `test`, `lint` e `assembleDebug` como gate, enquanto os
  APKs para download ficam centralizados em GitHub Releases para não depender da
  cota temporária de artefatos do Actions.

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
