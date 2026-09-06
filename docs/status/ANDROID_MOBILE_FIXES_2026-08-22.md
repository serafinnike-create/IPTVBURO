# Correções aplicadas — apps/android-tv (celular) — 22 de agosto de 2026

Auditoria de segurança e de código/performance solicitada pelo usuário, seguida de correção de
todos os achados de código. Segurança não teve achados críticos/altos (ver relato completo na
conversa; não repetido aqui).

| # | Severidade | Problema | Arquivo |
|---|---|---|---|
| 1 | ALTA | Importação Stalker/Ministra truncava categorias silenciosamente | `RoomCatalogRepository.kt` |
| 2 | ALTA | `actionJob` compartilhado cancelava fluxos não relacionados (ficha travava em "carregando") | `MainViewModel.kt` |
| 3 | MÉDIA | Corrida no carregamento de logos de provedor sem controle de Job | `MainViewModel.kt` |
| 4 | MÉDIA | Threads ilimitadas por conexão no `CastReceiver` | `CastReceiver.kt` |
| 5 | MÉDIA/BAIXA | Corrida no cancelamento de download finalizava arquivo mesmo assim | `AndroidDownloadManager.kt` |
| 6 | BAIXA | Três loops de polling redundantes no player | `PlayerScreen.kt` |
| 7 | BAIXA | Checkpoint podia gravar posição 0 no primeiro segundo | `PlayerScreen.kt` |
| 8 | BAIXA | Coroutine relançada a cada delta de arraste no Discover | `DiscoverScreen.kt` |

Todos os 8 itens foram corrigidos e validados por `:apps:android-tv:testDebugUnitTest`
(45 suítes, 0 falhas, 0 erros) e `:apps:android-tv:compileDebugKotlin`, em múltiplas rodadas ao
longo desta sessão.

## Cifragem de URLs/headers M3U — implementada

`ChannelEntity.streamUrl`, `userAgent`, `referer`, `origin` (fontes M3U locais) eram gravados em
texto simples no Room, diferente das credenciais Xtream/Stalker, já cifradas via
`AndroidKeystoreSourceConnectionStore`.

**Desenho**: cifra na escrita (importação), decifra apenas no ponto exato de resolução de
playback — nunca em `ChannelEntity.toDomain()`, que a listagem de catálogo também chama por
página (evita decifrar em massa só para mostrar nome/logo). Chave AES-256-GCM dedicada no Android
Keystore (`iptv_buro_channel_fields_v1`), IV aleatório por valor. Um prefixo (`cf1:`) marca valor
cifrado por este componente; qualquer linha sem esse prefixo (importada antes desta mudança, ou
Xtream/Stalker que resolvem tardiamente) é devolvida sem alteração — sem migration de dados
retroativa, sem risco de tornar uma linha existente ilegível.

Arquivos novos:
- `data/security/ChannelFieldCipher.kt` (interface)
- `data/security/AndroidKeystoreChannelFieldCipher.kt` (implementação)

Arquivos alterados:
- `di/DataBindingsModule.kt` — binding Hilt do novo cipher
- `data/mapper/PlaylistEntityMapper.kt` — cifra os 4 campos ao montar `ChannelEntity` a partir de
  uma playlist M3U
- `data/repository/RoomCatalogRepository.kt` — decifra em `getChannel()`, `findStoredContent()` e
  `findCompatibleMovieAlternative()`, os três pontos que devolvem um `Channel` que pode ir para o
  player ou para instrumentação
- `data/mapper/PlaylistEntityMapperTest.kt` — teste de round-trip cifra/decifra adicionado; testes
  existentes passaram a usar um cipher identidade (testam a lógica do mapper, não a cripto)

Validado por `:apps:android-tv:testDebugUnitTest` (suíte completa): 45 suítes, 0 falhas, 0 erros,
incluindo `PlaylistEntityMapperTest` (5 testes, com o novo round-trip de cripto).

## Limitações declaradas

- **Sem migration retroativa**: linhas M3U já importadas antes desta mudança continuam em texto
  simples até a próxima reimportação da playlist; não há um passo que as re-cifre automaticamente.
- **Não testado com `AndroidKeystoreChannelFieldCipher` real**: a suíte JVM usa fakes/identidade
  porque a implementação real depende do Android Keystore, indisponível fora de um dispositivo/
  emulador. A classe segue exatamente o padrão já em produção de
  `AndroidKeystoreSourceConnectionStore` (mesma chave AES-256-GCM, mesmo
  `setRandomizedEncryptionRequired(true)`), mas não foi exercitada em aparelho físico nesta sessão.
- Xtream e Stalker não passam por este cipher: Xtream resolve a URL tardiamente em memória (já
  documentado como seguro) e o Stalker guarda o `command` opaco do portal, não uma URL assinada
  convencional — ambos fora do escopo desta mudança, que cobre especificamente fontes M3U locais.

## Prateleira "Você também pode gostar" — implementada

Abaixo do elenco, na ficha de Filme e de Série: franquia (Superman 1, 2, 3…) e títulos parecidos,
via TMDB `recommendations` (com fallback para `similar` quando vazio).

- `packages/metadata-client/.../TmdbClient.kt` — novo `similarTitles(tmdbId, isSeries, limit)`.
- `ui/AppUiState.kt` — novo `openTitleSimilarTitles: List<PersonCreditUi>`, reaproveitando o tipo já
  usado pela filmografia de ator (mesma navegação: abre "Onde assistir").
- `ui/MainViewModel.kt` — novo `loadSimilarTitles()`, chamado após `loadMovieDetails`/
  `loadSeriesDetails`, mesmo padrão assíncrono não bloqueante de `loadProviderLogo`/
  `loadCriticScores`.
- `ui/screens/CastComponents.kt` — novo `SimilarTitlesShelf`/`SimilarTitleCard`.
- `ui/screens/MovieDetailsScreen.kt`, `SeriesDetailsScreen.kt`, `AppShellScreen.kt` — integração.
- Strings novas (`details_similar_titles`) nos 5 idiomas.

**Bug corrigido durante a implementação**: faltava `import androidx.compose.foundation.layout.Spacer`
em `CastComponents.kt`, quebrando a compilação do módulo inteiro. Corrigido.

## Filmes/Séries — filtros na frente (em andamento)

A pedido do usuário: ao abrir Filmes/Séries, pular a grade de categorias cruas (Ação, 4K, etc.) e
abrir direto "todos os títulos" com a barra de filtro já visível (gênero, ano, ordem, layout — o
mesmo padrão do Windows). Acima da barra: chip "Lançamentos [ano atual]" e uma fileira de logos de
provedores reais (Netflix, Amazon, etc.), vindos do TMDB (não do nome de categoria do provedor
IPTV, que raramente bate com um serviço — causa raiz confirmada do sumiço dos atalhos de provedor
relatado pelo usuário).

Implementado:
- `ui/MainViewModel.kt` — `openPrimaryCatalog()`: Filmes/Séries agora abrem `AppContent.Channels`
  com `categoryId = null` diretamente, sem passar por `AppContent.Categories`. TV ao vivo inalterada.
- `ui/screens/CatalogueFilterBar.kt` — chip "Lançamentos {ano}" que alterna `filter.year`.
- `data/discovery/ProviderLogoCatalogue.kt` — novo `discoveredProviders: StateFlow<List<DiscoveredProvider>>`
  (label + logoUrl + tmdbProviderId), ao lado do `logosByLabel` já existente.
- `ui/AppUiState.kt` — novo `discoveredProviders` no estado.
- `ui/MainViewModel.kt` — novo `openProviderShortcut()`, que abre o catálogo completo de um serviço
  na tela Assinaturas (reaproveita a lógica de `expandService`, refatorada para uma função privada
  compartilhada `openExpandedService`).
- `ui/screens/AppShellScreen.kt` — novo `ProviderShortcutRow`, exibido via `headerAction` do
  `ChannelsContent` só quando `categoryId == null` e `contentType` é MOVIE/SERIES.
- `MainViewModel.kt`/teste — `MainViewModelNavigationTest.kt` atualizado: o teste que esperava
  `AppContent.Categories` para `AppSection.MOVIES` agora espera `AppContent.Channels`; o teste de
  artwork de categoria nomeada passou a usar `AppSection.LIVE` (rota que ainda passa por Categories).

**Pendência de verificação**: uma segunda sessão do Claude Code está simultaneamente migrando
`java.time` → `kotlinx.datetime`/`kotlin.time.Instant` em arquivos não relacionados
(`AndroidLicenseClient.kt`, `ReminderWorker.kt`, `ReminderRepository.kt`, `RealHomeCatalog.kt`,
`RemindersScreen.kt`), deixando o módulo `apps/android-tv` temporariamente sem compilar por motivos
alheios a esta mudança. A suíte completa de testes ainda não foi confirmada verde para esta feature
— aguardando a outra sessão terminar antes de validar.
