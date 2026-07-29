# GDD 2.0 — 04. Arquitetura de experiência, desempenho e dados

## 1. Regra principal de continuidade

O Codex deve continuar sobre o código existente.

Antes de alterar arquitetura:

1. listar módulos e arquivos atuais;
2. identificar o que já implementa o GDD 1.0;
3. executar build e testes existentes;
4. registrar lacunas entre estado atual e GDD 2.0;
5. criar ADR para qualquer mudança estrutural grande;
6. preservar funcionalidade útil;
7. migrar de forma incremental;
8. não substituir uma implementação funcional apenas por preferência estética.

---

## 2. Camadas recomendadas para Android TV

A versão Android TV continua sendo a primeira implementação de referência.

```text
apps/android-tv/
├─ app/
├─ core/
│  ├─ common/
│  ├─ model/
│  ├─ database/
│  ├─ network/
│  ├─ playback/
│  ├─ telemetry/
│  ├─ security/
│  ├─ designsystem/
│  ├─ navigation/
│  ├─ metadata/
│  ├─ search/
│  └─ sync/
├─ feature/
│  ├─ bootstrap/
│  ├─ onboarding/
│  ├─ profiles/
│  ├─ home/
│  ├─ live/
│  ├─ guide/
│  ├─ movies/
│  ├─ series/
│  ├─ details/
│  ├─ search/
│  ├─ myburo/
│  ├─ player/
│  ├─ sources/
│  ├─ parental/
│  ├─ settings/
│  └─ diagnostics/
└─ benchmark/
```

Se a estrutura atual for diferente, adaptar sem reescrever tudo. O objetivo é separar domínio, player, design system e features.

---

## 3. Design system como código

Criar módulo `core:designsystem` com:

- cores semânticas;
- tipografia;
- spacing;
- shapes;
- elevation;
- motion tokens;
- focus tokens;
- breakpoints;
- performance tiers;
- componentes TV;
- componentes mobile futuramente;
- preview/catálogo de componentes;
- testes de screenshot.

### 3.1 Componentes mínimos

- `BuroScreen`;
- `BuroRibbon`;
- `BuroHero`;
- `BuroPosterCard`;
- `BuroLandscapeCard`;
- `BuroChannelTile`;
- `BuroEditorialFeature`;
- `BuroButton`;
- `BuroIconButton`;
- `BuroChip`;
- `BuroProgressBar`;
- `BuroTimeline`;
- `BuroFocusRing`;
- `BuroDialog`;
- `BuroBottomSheet` ou equivalente TV;
- `BuroSnackbar`;
- `BuroSkeleton`;
- `BuroEmptyState`;
- `BuroErrorState`;
- `BuroMetadataRow`;
- `BuroAudioSubtitleSelector`;
- `BuroPerformanceBadge` apenas em diagnóstico.

Nenhuma feature deve criar cores, animações e tamanhos arbitrários fora dos tokens sem justificativa.

---

## 4. Navigation State

A navegação deve preservar:

- seção atual;
- índice da fileira;
- índice do card;
- offset de scroll;
- item selecionado;
- filtro;
- origem da navegação;
- estado do player quando overlay;
- perfil atual.

### 4.1 Focus Restoration Contract

Cada tela navegável implementa:

```text
FocusRestorationState
- routeId
- sectionId
- itemId
- rowIndex
- columnIndex
- scrollOffset
- timestamp
```

Ao retornar:

1. tentar `itemId`;
2. fallback para posição;
3. fallback para primeiro item válido;
4. nunca deixar foco nulo.

### 4.2 Deep links

Preparar rotas para:

- conteúdo;
- canal;
- programa EPG;
- perfil;
- ativação;
- pareamento;
- configurações;
- diagnóstico;
- pagamento conforme plataforma.

---

## 5. Universal Content Graph

O catálogo deve abandonar a ideia de que cada item da playlist é uma entidade isolada.

### 5.1 Entidades

```text
Profile
Source
SourceCredentialRef
CatalogItem
CanonicalWork
ContentVersion
Channel
Program
Movie
Series
Season
Episode
Person
Genre
Collection
PlaybackProgress
Favorite
WatchHistory
Reminder
Artwork
TrailerRef
AudioPreference
SubtitlePreference
StreamObservation
DeviceCapability
RecommendationReason
```

### 5.2 Relações principais

- `Source` possui `CatalogItem`;
- `CatalogItem` pode apontar para `CanonicalWork`;
- `CanonicalWork` possui múltiplas `ContentVersion`;
- `Channel` possui programas EPG;
- `Episode` pertence a temporada e série;
- perfil possui progresso, favoritos, histórico e preferências;
- dispositivo possui capacidades e observações;
- recomendação aponta para motivo explicável.

### 5.3 Identificadores

- UUID interno estável;
- ID da fonte isolado;
- IDs externos guardados com namespace;
- hash normalizado para deduplicação;
- nunca usar URL completa como chave pública;
- mudanças na URL não devem apagar histórico quando a obra canônica permanece.

---

## 6. Pipeline de catálogo

```text
Import → Parse → Normalize → Classify → Match → Deduplicate → Enrich → Index → Publish
```

### 6.1 Import

- streaming parser;
- cancelável;
- progresso observável;
- limite de tamanho;
- encoding seguro;
- sem bloquear UI.

### 6.2 Normalize

- nome de exibição;
- título original;
- tags;
- idioma;
- país;
- qualidade declarada;
- número de temporada/episódio;
- grupo;
- classificação adulta provável;
- tipo provável.

### 6.3 Match

- EPG;
- metadados licenciados;
- regras locais;
- confiança;
- possibilidade de correção manual.

### 6.4 Publish

Somente após uma versão consistente estar pronta, publicar para a UI. Atualizações grandes devem usar transação ou snapshot para evitar home parcialmente vazia.

---

## 7. Home Composition Architecture

A home não deve ser montada diretamente por Composables consultando repositórios separados.

Criar:

```text
HomeComposer
- observeHome(profileId, context): Flow<HomeDocument>
```

`HomeDocument`:

```text
HomeDocument
- hero
- modules[]
- generatedAt
- sourceVersion
- personalizationLevel
- staleState
```

Tipos de módulo:

- ContinueWatching;
- LiveNow;
- StartingSoon;
- EditorialRow;
- RecommendationRow;
- FavoritesRow;
- QuickActions;
- MoodSelector;
- CollectionFeature;
- SourceHealthNotice;
- KidsSafeRow.

### 7.1 Regras de estabilidade

- IDs de módulo estáveis;
- diff incremental;
- não reconstruir todas as fileiras por um evento pequeno;
- hero não troca durante interação ativa;
- atualizações de EPG não podem mover foco;
- cache do último documento;
- fallback offline.

---

## 8. Recommendation Core

### 8.1 Fase local

Sinais:

- conclusão;
- abandono precoce;
- repetição;
- favorito;
- lista;
- gênero;
- idioma;
- horário;
- duração;
- recência;
- disponibilidade;
- perfil;
- diversidade.

Modelo inicial:

- regras + pontuação ponderada;
- conteúdo-based ranking;
- decay temporal;
- limite de repetição;
- exploração controlada;
- explicação gerada a partir dos sinais;
- sem embeddings remotos obrigatórios.

### 8.2 Contrato

```text
RecommendationCandidate
- canonicalWorkId
- score
- reasons[]
- sourceAvailability
- parentalAllowed
- diversityGroup
```

### 8.3 Controle do usuário

- menos disso;
- não recomendar este título;
- ocultar gênero;
- limpar aprendizado;
- desativar personalização;
- escolher entre leve, equilibrada e alta.

---

## 9. Search Architecture

### 9.1 Índice local

Opções devem ser avaliadas por ADR:

- SQLite FTS5;
- Lucene compatível no Android;
- índice customizado compacto;
- combinação de FTS + fuzzy matching.

Campos:

- título;
- título original;
- aliases;
- canal;
- programa;
- gênero;
- pessoa;
- idioma;
- ano;
- descrição;
- categoria;
- tags normalizadas.

### 9.2 Pipeline

```text
Query → Normalize → Detect filters → Retrieve → Rank → Group → Explain
```

### 9.3 Busca por voz

- usar API da plataforma;
- deixar claro quando áudio é enviado ao provedor do sistema;
- não manter gravação;
- fallback para texto;
- interpretar filtros localmente quando possível.

---

## 10. Playback Orchestrator

O `PlayerAdapter` do GDD 1.0 permanece. Adicionar uma camada superior:

```text
PlaybackOrchestrator
- resolveVersion(contentId, profile, device)
- buildPlaybackPlan(source, capabilities)
- execute(plan)
- observeState()
- recover(error)
- reportObservation()
```

### 10.1 PlaybackPlan

```text
PlaybackPlan
- selectedVersion
- protocol
- headersRef
- playerMode
- bufferProfile
- decoderPreference
- audioPreference
- subtitlePreference
- seekModel
- fallbackPlans[]
- reason
```

### 10.2 State machine

```text
Idle
Resolving
Connecting
Preparing
Playing
Paused
Seeking
Buffering
Recovering
Ended
Failed
Released
```

Transições devem ser explícitas, testáveis e sem estados impossíveis.

### 10.3 Recovery policy

- no máximo quantidade limitada de tentativas;
- retry diferente para HTTP, timeout, codec e autenticação;
- não tentar senha repetidamente;
- não trocar áudio/qualidade silenciosamente quando altera intenção do usuário;
- guardar causa raiz e ação tomada;
- oferecer modo estável;
- liberar recursos ao sair.

---

## 11. Trailer e preview pipeline

### 11.1 Fontes permitidas

- trailer informado pela fonte;
- APIs oficiais;
- links autorizados de metadados;
- arquivos locais do usuário.

### 11.2 Regras de carregamento

- atraso de foco;
- cancelar ao mover foco;
- máximo de um preview ativo;
- mudo;
- resolução limitada;
- cache curto;
- respeitar plano de dados;
- fallback para backdrop;
- sem autoplay em perfil infantil por padrão.

### 11.3 Isolamento

Preview usa player/instância controlada para não interferir no player principal. Em aparelhos com decoder limitado, usar imagem animada ou nenhum vídeo.

---

## 12. Sync Architecture

### 12.1 Local-first

Cada ação grava localmente primeiro. A sincronização publica eventos:

```text
ProgressUpdated
FavoriteAdded
FavoriteRemoved
WatchlistChanged
ProfilePreferenceChanged
ReminderChanged
CollectionChanged
```

### 12.2 Conflitos

- progresso: posição mais avançada recente, com proteção contra replay;
- favorito/lista: conjunto por evento;
- preferências: último write válido;
- exclusão: tombstone com expiração;
- relógio local não confiável: usar tempo do servidor quando disponível.

### 12.3 Segurança

- TLS;
- tokens curtos e refresh seguro;
- keychain/keystore;
- dispositivo revogável;
- credenciais da fonte separadas de dados de perfil;
- criptografia ponta a ponta quando credenciais forem sincronizadas;
- logs redigidos.

---

## 13. Performance tiers

### Tier 0 — Eco

- TV antiga/baixa memória;
- imagens menores;
- sem blur em tempo real;
- sem trailer automático;
- menos itens pré-carregados;
- animações simples;
- uma instância de player.

### Tier 1 — Balanced

- gradientes dinâmicos;
- crossfade;
- prefetch leve;
- previews sob demanda;
- efeitos moderados.

### Tier 2 — Cinematic

- blur controlado;
- parallax;
- shared transitions;
- trailer mudo;
- prefetch adicional;
- suporte a multiview se decoders permitirem.

### 13.1 Detecção

Usar:

- memória disponível;
- versão Android;
- GPU/decoder;
- resultado de benchmark curto;
- dropped frames de UI;
- temperatura não deve ser inferida sem API confiável;
- opção manual do usuário.

---

## 14. Budgets de desempenho

### 14.1 UI

- evitar trabalho pesado no main thread;
- frame budget de 16,6 ms em 60 Hz;
- tolerância limitada a frames perdidos em transições pesadas;
- listas virtualizadas;
- keys estáveis;
- recomposição medida;
- imagens redimensionadas para o destino;
- limite de cache por tier.

### 14.2 Memória

Metas iniciais para aparelho 1080p de referência:

- app em home: ideal ≤ 300 MB, teto a validar por dispositivo;
- player + UI: ideal ≤ 450 MB;
- nenhum cache de bitmap sem limite;
- limpar previews ao perder foco;
- trim memory implementado;
- multiview com orçamento separado.

### 14.3 Rede

- home deve usar cache;
- EPG incremental;
- imagens com tamanhos responsivos;
- trailers não pré-carregados em massa;
- backoff;
- cancelamento de requests;
- deduplicação de chamadas;
- compressão HTTP quando suportada;
- não fazer health check agressivo em milhares de streams.

### 14.4 Banco

- índices para busca, progresso, EPG e relações canônicas;
- paginação;
- migrações testadas;
- import em batches;
- transações;
- política de limpeza de EPG antigo;
- vacuum fora do caminho crítico.

---

## 15. Observabilidade

### 15.1 Métricas técnicas

- cold/warm start;
- home time-to-interactive;
- D-pad latency aproximada;
- dropped UI frames;
- playback start;
- channel switch;
- buffering ratio;
- recovery success;
- crash/ANR;
- memória;
- falha de imagem;
- duração de importação;
- busca latency.

### 15.2 Privacidade

- telemetria opcional e explicada;
- URLs, usernames, passwords e nomes sensíveis redigidos;
- IDs pseudônimos;
- retenção mínima;
- possibilidade de exportar/apagar dados;
- painel “Dados e privacidade”.

### 15.3 Telemetria local

Mesmo sem consentimento de analytics remoto, métricas locais podem alimentar Quality Autopilot, desde que permaneçam no aparelho e possam ser apagadas.

---

## 16. Feature flags

Toda função arriscada entra atrás de flag:

- trailer automático;
- Ambient Color Engine avançado;
- Mood parser;
- MultiView;
- sync de credenciais;
- artwork personalizado;
- Moments;
- prefetch de canal;
- novo motor de recomendação.

Flags precisam de defaults seguros e não podem depender de backend para o app iniciar.

---

## 17. Estratégia de testes

### Unitários

- normalização;
- deduplicação;
- ranking;
- filtros parentais;
- state machines;
- recovery policy;
- conflict resolution;
- home composition.

### Integração

- import M3U/Xtream/XMLTV;
- banco e migrações;
- player com fixtures legais;
- sync;
- portal pairing;
- deep links.

### UI

- focus map;
- voltar;
- rolagem;
- restauração;
- estados vazio/erro/loading;
- perfis;
- acessibilidade;
- screenshot golden.

### Performance

- macrobenchmark de start;
- scroll de home;
- troca de tela;
- memória;
- channel switch;
- importação grande;
- busca em catálogo grande.

### Dispositivos

Matriz mínima:

- Android TV fraco 720p/1080p;
- Google TV intermediário;
- Fire TV compatível;
- Sony/Android TV real quando disponível;
- emulador somente como apoio;
- rede rápida, lenta, instável e offline.

---

## 18. Definition of Done técnica

Uma feature só está pronta quando:

- possui estados loading, vazio, sucesso e erro;
- funciona com controle remoto;
- restaura foco;
- possui acessibilidade;
- respeita performance tier;
- não vaza credenciais;
- possui testes adequados;
- não bloqueia main thread;
- tem analytics/telemetria redigida quando aplicável;
- documentação atualizada;
- screenshots ou gravação de validação;
- critérios funcionais e visuais aceitos.
