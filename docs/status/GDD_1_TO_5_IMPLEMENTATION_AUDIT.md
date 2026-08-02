# Auditoria de implementação — GDD 1 a 6

- Data: 2 de agosto de 2026
- Base documental e branch: `main@eeb8990` mais a milestone 0.2 não commitada
- Regra: `EXISTING` significa código executável; texto, placeholder e scaffold não contam

## Conclusão

Não, os seis GDDs ainda não estão integralmente implementados. A versão local
é um vertical funcional com catálogo real, player Android, player Windows
embutido de compatibilidade limitada, importação em escala, idiomas, favoritos
por perfil e uma fundação visual comum. O domínio temporal do GDD 3 e o motor
do GDD 4 continuam incompletos; o GDD 5 ainda não alcançou paridade nativa; e o
Cofre Offline do GDD 6 permanece P0 somente no Android mobile elegível.

## Matriz

| GDD | Estado | Evidência existente | Lacuna bloqueante |
|---|---|---|---|
| 1 — produto, dados e player | `PARTIAL` | M3U streaming; Room; Media3; D-pad; perfis/favoritos; quatro idiomas; segurança básica | EPG/XMLTV, conta/sincronização e cobertura de dispositivos |
| 2 — experiência revolucionária | `PARTIAL` | Ribbon, Home real, tokens, foco, hero, artes/capas, Minha BURO, perfis e detalhes nas duas UIs | Story/Pulse reais, goldens, fotos licenciadas do elenco e consistência integral |
| 3 — inteligência temporal | `PARTIAL` | `year`/`addedAt`, índices e fileiras 2026/2025 | proveniência, confiança, classificação canônica e correção manual |
| 4 — confiabilidade | `MISSING` | redaction, limites básicos, alguns estados de erro | Failure Normalizer, Recovery Planner, Retry/Connection Budget, circuit breaker e Failure Lab |
| 5 — multiplataforma | `PARTIAL` | Android adaptativo; Compose Desktop; tokens, perfis, idiomas e favoritos; player JavaFX embutido | adapter nativo Windows, persistência paginada, manifest real e auditoria de paridade |
| 6 — Cofre Offline | `FOUNDATION` | contrato de capacidade e armazenamento privado experimental | autorização/licença, Media3 DownloadManager/Index, retomada/expiração/Kids e testes E2E; recurso corretamente oculto |

## Gates reais de um produto AAA

| Gate | Estado | Evidência ou bloqueio |
|---|---|---|
| Identidade visual própria e consistente | `PARTIAL` | BURO Nocturne, três artes originais, tokens comuns e cards editoriais; telas legadas ainda precisam convergir |
| Catálogo acima de 305 mil itens | `PASS SYNTHETIC` | parser e índice validados com 500 mil itens; falta novo ensaio E2E Windows com uma fonte real dessa escala |
| Playback Android | `PARTIAL` | live, VOD e episódio compatíveis validados; matriz ampla de codecs/dispositivos ainda incompleta |
| Playback Windows | `PARTIAL/BLOCKED` | JavaFX embutido cobre H.264/AAC MP4 e HLS compatível; adapter nativo amplo, áudio/legenda e HEVC/HDR ainda bloqueiam paridade |
| EPG/XMLTV e BURO Pulse | `MISSING` | sem guia, agora/próximo, lembretes, catch-up ou mini-guia |
| Perfis, Kids e controle parental | `PARTIAL` | até cinco perfis e favoritos isolados existem; PIN, política Kids e sincronização ainda faltam |
| BURO Temporal Intelligence | `PARTIAL` | fileiras/consulta anual existem; faltam evidências, confiança, correção manual e índice anual canônico |
| BURO Resilience Engine | `MISSING` | mensagem de codec melhorou, mas não há RetryBudget, ConnectionBudget, circuit breaker ou planner |
| Acessibilidade e foco | `PARTIAL` | D-pad, reduced motion/contrast/transparency e semântica básica; faltam leitor de tela e goldens completos |
| Segurança | `PARTIAL` | redaction e Keystore para Xtream; M3U ainda pode persistir URL/header sensível em texto no Room |
| Observabilidade e Failure Lab | `MISSING` | não há laboratório de caos nem diagnóstico seguro exportável |
| Licenciamento, portal e sincronização | `FOUNDATION` | contrato de entitlement, identidade Android e ADR existem; backend, lojas, portal e cobrança real faltam |

## Catálogo grande

### Antes desta correção

- M3U: parser por fluxo e lotes Room, limite de 500 mil itens.
- Xtream Android: resposta inteira em `ByteArray`, depois `String`, árvore JSON,
  lista de domínio e lista Room. Um catálogo de 305 mil podia multiplicar seu
  tamanho várias vezes na heap.
- Android: páginas de 200 itens, mas ainda com `OFFSET`.
- Windows: catálogos e índice de busca mantidos em listas Kotlin durante a
  sessão; paginação da UI não eliminava o catálogo completo da memória.

### Baseline de engenharia

- capacidade mínima verificável: 500 mil registros sintéticos;
- teto defensivo inicial: 1 milhão de registros Xtream;
- importação M3U e Xtream item a item;
- escrita transacional em lotes de 500;
- shell visível durante importação e publicação atômica do catálogo;
- listas virtualizadas e páginas pequenas;
- substituir paginação profunda por cursor/keyset;
- pesquisa executada no índice local, nunca filtrando 500 mil objetos na UI;
- Windows possui agora índice colunar de sessão validado com 500 mil registros,
  mas ainda precisa de player interno e medição E2E para ser declarado paridade.

## Visual e consistência

A crítica visual procede. A Home anterior usava gradiente e formas abstratas
genéricas, enquanto o desktop usava outra paleta, outra hierarquia e outro shell.
A correção adota a direção original **BURO Nocturne**:

- arte cinematográfica própria, sem copiar interface ou trade dress de concorrente;
- mesma paleta semântica em Android e Windows;
- hero editorial com área de leitura protegida;
- capas e logos da fonte em memória nas duas plataformas, com fallback de marca;
- grid de catálogo Windows no lugar da lista estreita de aparência administrativa;
- posters 2:3 para filmes/séries e tiles 16:9 para TV ao vivo no Android;
- movimento curto, foco inequívoco e caminho reduced-motion;
- layout adaptado ao dispositivo sem trocar a identidade do produto.

O contrato canônico está em `packages/design-tokens/tokens.json`. Os masters
originais estão em `assets/brand/buro-nocturne-hero-master.png`,
`assets/brand/buro-paper-sun-master.png` e
`assets/brand/buro-forest-signal-master.png`.

## Conflitos documentais encontrados

- `packages/release-manifest/platforms.json` do GDD 5 ainda declara Windows como
  `NOT_STARTED`; o worktree local contém um preview Windows não publicado.
- `docs/status/CURRENT_IMPLEMENTATION.md` descreve a milestone 0.2, mas também
  registra que vários testes finais ainda estavam pendentes; isso não equivale a
  paridade aprovada.
- O GDD 5 foi adicionado no remoto depois da base local. Ele deve ser integrado
  sem apagar ou sobrescrever as alterações locais da milestone 0.2.

## Critério para publicação

Nenhum APK/MSI novo deve ser publicado enquanto Android e Windows não passarem
por build limpo, testes de catálogo grande, inspeção visual, varredura de
segredos e validação em hardware. O preview simples atual não é release final.

## Incremento validado em 1 de agosto de 2026

- `get_vod_info` passou a fornecer sinopse, elenco, direção, gênero, duração,
  data, país, avaliação, pôster, backdrop e referência segura de trailer;
- detalhes de séries passaram a incluir backdrop, elenco, gênero, lançamento,
  avaliação e trailer, além das temporadas e episódios existentes;
- Android abre uma ficha adaptativa antes de resolver a URL privada;
- Windows carrega a ficha do item sob demanda e exibe os mesmos metadados;
- a paleta roxa/ciano foi substituída por grafite, marfim e dourado contido;
- o player Android usa o controlador Media3 auto-ocultável, com timeline, seek
  15/30, velocidade, áudio, legendas e fullscreen quando suportados pela mídia;
- downloads offline e Cast permanecem bloqueados por requisitos de segurança,
  autorização e infraestrutura. Nenhum dos dois é apresentado como pronto.
