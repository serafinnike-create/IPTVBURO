# IPTV BURO — GDD 8.0: Media SuperHub & Audio Experience

**Versão:** 8.0  
**Data:** 4 de agosto de 2026  
**Status:** especificação oficial para implementação incremental  
**Escopo inicial:** Android/Android TV e Windows; domínio preparado para Android mobile, iOS, iPadOS, macOS e TVs  
**Dependências:** GDDs 1.0 a 7.0, ADRs vigentes, `docs/status/CURRENT_IMPLEMENTATION.md`

## 1. Visão

O IPTV BURO deve evoluir de um aplicativo focado em TV, filmes e séries para um **Media SuperHub**: uma experiência única para consumir e organizar mídia autorizada pelo usuário.

O produto final poderá reunir:

- TV ao vivo;
- filmes;
- séries e episódios;
- músicas e álbuns;
- artistas;
- playlists;
- rádios online;
- podcasts;
- audiobooks;
- fotos e nuvens pessoais em fases futuras;
- cloud gaming em fase futura separada.

A expansão não cria outro aplicativo nem duplica perfis, favoritos, busca, histórico, downloads ou design system. Ela amplia o núcleo atual por meio de novos tipos de mídia, adaptadores de fonte e experiências de player especializadas.

## 2. Regra de legalidade e produto

O IPTV BURO é um player e organizador de fontes fornecidas pelo usuário. A implementação deve aceitar somente integrações e formatos que possam ser usados legitimamente.

Regras obrigatórias:

- não fornecer catálogo protegido, credenciais compartilhadas ou listas piratas;
- não contornar DRM, paywall, autenticação ou restrição territorial;
- não extrair URLs ocultas de Spotify, Apple Music, Deezer, YouTube Music ou serviços equivalentes;
- não baixar conteúdo quando a fonte ou licença não autorizar;
- não incluir endpoints privados ou segredos em código, teste, log, telemetria ou documentação;
- deixar claro no onboarding que o usuário é responsável pelas fontes adicionadas;
- preferir bibliotecas pessoais, rádios públicas, podcasts RSS e servidores autorizados.

Fontes tecnicamente compatíveis não são automaticamente fontes autorizadas. Capacidade técnica e permissão devem permanecer separadas.

## 3. Resultado desejado

Uma lista de áudio simples deve poder ser transformada em uma interface semelhante a um serviço moderno de música, sem expor a estrutura técnica ao usuário.

Exemplo conceitual:

```text
Fonte M3U / pasta / servidor autorizado
               ↓
      detecção e normalização
               ↓
 artista → álbum → faixa → metadados → artwork
               ↓
 Home de Música / Biblioteca / Pesquisa / Player / Fila
```

O usuário não precisa manipular URLs depois da importação. A interface apresenta artistas, álbuns, músicas, rádios e programas, preservando uma seção técnica apenas em configurações e diagnóstico.

## 4. Auditoria do repositório atual

A implementação existente já fornece bases importantes:

- projeto Kotlin/Gradle modular;
- aplicativos `apps/android-tv` e `apps/desktop`;
- domínio compartilhado em `packages/domain-model`;
- parser M3U streaming em `packages/playlist-parser`;
- clientes Xtream e Stalker separados;
- Android Media3 e players desktop;
- perfis, Kids, favoritos e continuidade;
- catálogo paginado e importação em lotes;
- design system BURO Nocturne;
- proteção local por Android Keystore e Windows DPAPI;
- adaptação para TV, toque, teclado e mouse.

Lacunas confirmadas para áudio:

- `CatalogContentType` cobre somente `LIVE`, `MOVIE`, `SERIES`, `EPISODE` e `UNKNOWN`;
- `ContentKind` não possui faixa, álbum, artista, rádio, podcast ou audiobook;
- o modelo `Channel` está acumulando responsabilidades de item universal;
- o parser M3U produz `ParsedChannel`, mesmo quando o item é áudio;
- o player, progresso e Home são predominantemente orientados a vídeo;
- não existem modelos canônicos para artista, álbum, faixa, programa, capítulo ou fila de áudio;
- não há extração padronizada de ID3, Vorbis, FLAC ou MP4 metadata;
- não há suporte RSS/Atom para podcasts;
- não há política de gapless, crossfade, ReplayGain ou audio focus.

O GDD 8 deve corrigir essas lacunas incrementalmente, sem reescrever o vertical de vídeo.

## 5. Princípios arquiteturais

1. **Extensão, não reinício.** Preservar o código funcional e migrar por etapas.
2. **Domínio universal, UI especializada.** A identidade e a biblioteca são compartilhadas; cada tipo possui apresentação própria.
3. **Source adapters.** Toda fonte externa entra por um contrato comum.
4. **Capability-driven UI.** A interface só mostra ações realmente suportadas.
5. **Local-first.** Biblioteca, favoritos, histórico e fila funcionam localmente.
6. **Segredos fora do catálogo.** Tokens e credenciais ficam em cofres da plataforma.
7. **Streaming por fluxo.** Nenhuma importação grande exige manter o catálogo inteiro na memória.
8. **Sem suposições por extensão.** MIME, headers, probe limitado e metadados determinam o tipo.
9. **Compatibilidade gradual.** Android é a referência de áudio inicial; Windows alcança paridade por fases.
10. **Métricas honestas.** Recursos planejados nunca aparecem como implementados.

## 6. Taxonomia universal de mídia

Adicionar um modelo canônico sem remover imediatamente os enums antigos.

```kotlin
enum class MediaKind {
    LIVE_TV,
    MOVIE,
    SERIES,
    VIDEO_EPISODE,
    MUSIC_TRACK,
    ALBUM,
    ARTIST,
    AUDIO_PLAYLIST,
    RADIO_STATION,
    PODCAST_SHOW,
    PODCAST_EPISODE,
    AUDIOBOOK,
    AUDIOBOOK_CHAPTER,
    PHOTO,
    GAME_STREAM,
    UNKNOWN,
}
```

Durante a migração:

- `CatalogContentType` continua disponível para o código legado;
- criar mapper explícito entre tipos antigos e `MediaKind`;
- não reutilizar `LIVE` para rádio;
- não representar artista ou álbum como canal;
- não quebrar schema Room existente sem migration testada.

## 7. Modelo canônico

Criar uma entidade leve `MediaItem` para indexação e telas genéricas.

Campos mínimos:

```text
id
sourceId
providerItemId opcional
stableIdentity
kind
title
subtitle opcional
parentId opcional
collectionId opcional
artworkUri opcional
backdropUri opcional
playbackLocator opcional
mimeType opcional
durationMs opcional
year opcional
rating opcional
explicitFlag opcional
isLive
isSeekable
capabilities
createdAt
updatedAt
```

`playbackLocator` não deve conter credencial em texto simples. Para fontes autenticadas, ele referencia uma identidade interna resolvida somente no momento da reprodução.

Modelos especializados:

```text
Artist
Album
MusicTrack
AudioPlaylist
RadioStation
PodcastShow
PodcastEpisode
Audiobook
AudiobookChapter
```

### 7.1 Artist

- `artistId`;
- nome canônico;
- sort name;
- imagem;
- biografia opcional;
- identificadores externos opcionais;
- contagem de álbuns/faixas;
- gêneros.

### 7.2 Album

- `albumId`;
- título;
- artista principal;
- artistas adicionais;
- ano/data de lançamento;
- tipo: álbum, single, EP, compilação, desconhecido;
- artwork;
- número de discos;
- número de faixas;
- duração total;
- gênero.

### 7.3 MusicTrack

- título;
- artistas;
- álbum;
- número da faixa e disco;
- duração;
- gênero;
- ano;
- codec, bitrate, sample rate, bit depth e canais quando conhecidos;
- ISRC opcional;
- ReplayGain track/album quando disponível;
- explicit flag quando a fonte fornecer;
- letra não sincronizada ou sincronizada quando autorizada.

### 7.4 RadioStation

- nome;
- stream locator;
- homepage opcional;
- país, idioma e gêneros;
- codec e bitrate quando detectados;
- artwork;
- metadados ICY atuais;
- `isLive = true`;
- `isSeekable = false`, salvo timeshift explicitamente suportado.

### 7.5 PodcastShow e PodcastEpisode

- feed URL protegida quando privada;
- título, autor, descrição e artwork;
- categorias;
- episódio, temporada, data de publicação;
- duração;
- enclosure URL;
- capítulos e transcript quando fornecidos;
- posição de reprodução por perfil.

### 7.6 Audiobook e capítulo

- título, autor e narrador;
- coleção/série;
- capítulos ordenados;
- duração total;
- velocidade de reprodução;
- sleep timer;
- posição por capítulo e posição agregada do livro.

## 8. Identidade estável

Ampliar `ContentIdentity` sem alterar as chaves de vídeo já persistidas.

Namespaces mínimos:

```text
track:{artist}:{album}:{disc}:{track}:{title}:{durationBucket}
album:{albumArtist}:{album}:{year}
artist:{artist}
radio:{normalizedHost}:{normalizedPath}
podcast:{normalizedFeedUrl}
podcastEpisode:{feedIdentity}:{guid-or-enclosure-hash}
audiobook:{author}:{title}:{year}
chapter:{bookIdentity}:{chapterIndex}:{title}
```

Regras:

- retirar parâmetros de autenticação antes de gerar identidade;
- nunca usar token, cookie ou username na chave;
- usar GUID RSS quando estável;
- evitar deduplicar músicas diferentes somente pelo título;
- manter variantes distintas quando duração, artista ou álbum divergirem;
- registrar estratégia e versão da identidade para migrações futuras.

## 9. Contrato de fontes

Criar um SPI compartilhado:

```kotlin
interface MediaSourceAdapter {
    val sourceType: MediaSourceType
    suspend fun validate(config: SourceConfig): SourceValidation
    fun scan(config: SourceConfig): Flow<MediaImportEvent>
    suspend fun resolve(locator: PlaybackLocator): ResolvedMedia
    suspend fun capabilities(config: SourceConfig): SourceCapabilities
}
```

Eventos de importação:

```text
Started
CollectionDiscovered
ItemDiscovered
Progress
Warning
Completed
Failed
```

A importação deve suportar backpressure, cancelamento, limites defensivos e redaction.

## 10. Fontes suportadas por fase

### P0 — obrigatório

1. **M3U/M3U8 de áudio fornecido pelo usuário**
   - streams HTTP/HTTPS autorizados;
   - arquivos MP3, AAC, M4A, OGG, OPUS, FLAC e WAV quando o player da plataforma suportar;
   - `#EXTINF` e atributos extras;
   - grupos convertidos em playlists/coleções;
   - detecção de rádio por stream contínuo e metadata ICY.

2. **Arquivos/pastas locais no Android mobile e Windows**
   - acesso via seletor do sistema;
   - nunca varrer armazenamento sem consentimento;
   - persistir permissões quando a plataforma permitir;
   - leitura de tags, não alteração automática dos arquivos.

3. **Podcast RSS 2.0 / Atom com enclosure**
   - feed público ou privado autorizado;
   - ETag e Last-Modified;
   - atualização incremental;
   - capítulos/transcripts somente quando declarados pelo feed.

4. **Rádios online adicionadas pelo usuário**
   - URL direta;
   - M3U/PLS/XSPF simples quando permitido;
   - ICY metadata;
   - favoritos e histórico.

### P1 — recomendado

- Subsonic/OpenSubsonic;
- Navidrome via API compatível;
- Jellyfin autorizado;
- WebDAV;
- SMB somente onde houver API segura e suporte de plataforma;
- armazenamento S3 compatível configurado pelo usuário;
- OPML para importação/exportação de podcasts.

### P2 — futuro

- Plex quando a API e a conta do usuário autorizarem;
- nuvens pessoais;
- fotos;
- integração com serviços comerciais somente por SDK/API oficial e dentro dos termos.

## 11. Detecção de mídia

Pipeline:

1. ler declaração da fonte;
2. analisar `Content-Type` e extensão;
3. usar metadados M3U/PLS/RSS;
4. realizar probe curto e limitado quando necessário;
5. classificar confiança;
6. pedir correção manual apenas quando ambíguo.

O probe:

- não baixa o arquivo inteiro;
- respeita timeout e orçamento de bytes;
- não segue redirects ilimitados;
- não imprime a URL completa;
- não tenta burlar autenticação.

Resultado:

```text
mediaKind
mimeType
codecHints
isLive
isSeekable
confidence
warnings
```

## 12. Metadados

### 12.1 Embutidos

Suportar leitura, por fases, de:

- ID3v2/ID3v1;
- Vorbis Comments;
- FLAC metadata blocks;
- MP4/M4A atoms;
- embedded artwork;
- ReplayGain;
- chapters quando disponíveis.

A extração deve ocorrer fora da thread principal e possuir limites para imagens, tags e arquivos malformados.

### 12.2 Remotos opcionais

Enriquecimento remoto deve ser opt-in e desacoplado.

Permitidos quando configurados corretamente:

- MusicBrainz;
- Cover Art Archive;
- APIs de letras licenciadas;
- provedores de podcast autorizados.

Regras:

- respeitar termos, rate limits e atribuição;
- armazenar provenance de cada campo;
- nunca substituir silenciosamente tag local confiável;
- permitir corrigir correspondência;
- funcionar sem enriquecimento remoto.

## 13. Biblioteca e navegação

Destinos principais do SuperHub:

```text
Home
TV
Filmes
Séries
Música
Rádio
Podcasts
Audiobooks
Minha BURO
Busca
Downloads
Configurações
```

A Ribbon deve adaptar quantidade e prioridade por tamanho de tela. Em telefone, destinos menos usados podem ir para `Mais`; em TV, o foco deve permanecer previsível.

### 13.1 Música

Subdestinos:

- Início;
- Músicas;
- Artistas;
- Álbuns;
- Playlists;
- Favoritos;
- Downloads;
- Pastas/fontes em configurações.

Fileiras da Home de Música:

- Continuar ouvindo;
- Tocadas recentemente;
- Adicionadas recentemente;
- Artistas favoritos;
- Álbuns recentes;
- Mais tocadas;
- Playlists do usuário;
- Rádios favoritas.

Nenhuma fileira pode afirmar “Em alta” sem dados reais que sustentem a afirmação.

### 13.2 Rádio

- favoritas;
- recentes;
- por país;
- por idioma;
- por gênero;
- agora tocando quando ICY fornecer;
- reconexão limitada e estado de buffering honesto.

### 13.3 Podcasts

- novos episódios;
- continuar ouvindo;
- baixados;
- inscrições;
- episódios por programa;
- marcar como reproduzido;
- velocidade e silêncio inteligente somente quando implementados de verdade.

### 13.4 Audiobooks

- continuar ouvindo;
- biblioteca;
- autores;
- coleções;
- capítulos;
- progresso agregado;
- sleep timer.

## 14. Busca global

A busca deve retornar seções tipadas, nunca uma lista sem contexto.

Tipos pesquisáveis:

- canal;
- filme;
- série;
- episódio;
- música;
- artista;
- álbum;
- playlist;
- rádio;
- podcast;
- episódio de podcast;
- audiobook;
- capítulo.

Requisitos:

- consulta local instantânea para biblioteca indexada;
- paginação;
- normalização de acentos;
- suporte a aliases de artista;
- filtros por tipo e fonte;
- resultados Kids filtrados antes da UI;
- nenhuma consulta remota automática contendo histórico privado, salvo opt-in.

## 15. Player Universal e Audio Session

Não criar um player monolítico. Manter um `PlaybackCoordinator` e adapters por plataforma/modo.

Contratos:

```text
PlaybackRequest
ResolvedMedia
PlaybackCapabilities
PlaybackState
PlaybackCommand
QueueState
NowPlayingMetadata
```

Modos:

- vídeo;
- música;
- rádio ao vivo;
- spoken audio: podcast/audiobook;
- multiview permanece separado.

### 15.1 Player de música

- capa grande;
- título, artista e álbum;
- play/pause;
- anterior/próxima;
- seek;
- shuffle;
- repeat off/one/all;
- favorito;
- adicionar à playlist;
- fila;
- qualidade técnica;
- letra quando disponível;
- mini-player persistente.

### 15.2 Gapless

Obrigatório para álbuns contínuos quando a engine suportar.

- preparar próxima faixa;
- não inserir silêncio artificial;
- preservar ordem do álbum;
- capability deve informar quando indisponível.

### 15.3 Crossfade

P1, desativado por padrão.

Opções: `0`, `1`, `2`, `3`, `5`, `8` e `10` segundos.

Não aplicar em:

- podcasts;
- audiobooks;
- rádio;
- faixas marcadas para gapless estrito, quando houver conflito.

### 15.4 ReplayGain e normalização

- ler tags ReplayGain quando presentes;
- permitir `off`, `track` e `album`;
- limitar ganho para evitar clipping;
- não confundir com compressão dinâmica;
- não alterar o arquivo original.

### 15.5 Audio focus e background

Android:

- MediaSession;
- notificação de mídia;
- headset/Bluetooth;
- noisy intent;
- audio focus com duck/pause configurável;
- background playback somente com foreground service válido.

Windows:

- teclas de mídia;
- integração SMTC quando implementada;
- player interno preferencial;
- nenhum comando expõe URL privada em processo externo ou linha de comando.

## 16. Fila

A fila é por perfil e por sessão.

Ações:

- tocar agora;
- tocar em seguida;
- adicionar ao final;
- reordenar;
- remover;
- limpar;
- salvar como playlist;
- restaurar última fila opcionalmente.

Regras:

- rádio substitui a fila por sessão ao vivo;
- podcast pode ter fila própria de episódios;
- audiobook não mistura capítulos com músicas por padrão;
- a fila nunca guarda token ou URL resolvida;
- persistir identidades e resolver no playback.

## 17. Playlists

Tipos:

- manual;
- importada M3U;
- inteligente;
- fila salva;
- sistema, como favoritos e mais tocadas.

Operações:

- criar;
- renomear;
- excluir;
- duplicar;
- ordenar;
- adicionar/remover faixa;
- importar/exportar M3U apenas com autorização e aviso sobre URLs sensíveis.

Playlists inteligentes iniciais:

- favoritas;
- tocadas recentemente;
- mais tocadas;
- nunca tocadas;
- adicionadas recentemente;
- por gênero;
- por década.

“Mix diário” e recomendação algorítmica ficam fora do P0 até existir modelo real e explicável.

## 18. Favoritos, histórico e progresso

O sistema universal deve reutilizar perfis e isolamento existentes.

Progresso:

- música: contagem de reprodução após limiar configurado; posição normalmente não é retomada;
- podcast: posição persistente;
- audiobook: posição persistente por capítulo e livro;
- rádio: histórico, sem posição;
- vídeo: permanece regido pelo GDD 7.

Campos de listening history:

```text
profileId
mediaIdentity
kind
startedAt
lastPlayedAt
playCount
completedCount
lastPositionMs opcional
durationMs opcional
sourceId
```

Kids:

- respeitar explicit flag quando fornecida;
- não assumir que conteúdo sem classificação é infantil;
- permitir políticas familiares específicas para áudio;
- podcasts e rádios podem ser bloqueados por fonte/categoria.

## 19. Downloads e offline

O GDD 6 continua sendo a autoridade para offline autorizado.

Áudio:

- música local já disponível não precisa ser duplicada;
- podcast pode ser baixado quando o enclosure autorizar;
- audiobook pode ser baixado quando a fonte autorizar;
- downloads permanecem dentro do app quando exigido;
- permitir Wi-Fi only, limite de espaço, pausa, retomada e remoção;
- validar checksum quando fornecido;
- nunca transformar stream não autorizado em arquivo exportável.

## 20. Banco e indexação

Não converter imediatamente todas as tabelas atuais para uma tabela universal gigante.

Estratégia:

1. manter tabelas legadas de vídeo;
2. adicionar schema de áudio especializado;
3. criar uma projeção/repositório universal para Home e busca;
4. migrar somente quando testes demonstrarem benefício.

Tabelas conceituais:

```text
media_sources
artists
albums
tracks
track_artists
playlists
playlist_items
radio_stations
podcast_shows
podcast_episodes
audiobooks
audiobook_chapters
listening_history
audio_queue
metadata_provenance
```

Índices mínimos:

- identidade estável única por source/type quando aplicável;
- artista normalizado;
- álbum + artista + ano;
- playlist + posição;
- podcast + data de publicação;
- perfil + lastPlayedAt;
- FTS para títulos, artista, álbum e programa.

Migrations Android devem usar `MigrationTestHelper`. Windows precisa de versão explícita do store e testes de upgrade.

## 21. Estrutura modular proposta

Não criar todos os módulos de uma vez. A direção alvo é:

```text
packages/domain-model             # ampliar tipos e contratos universais
packages/playlist-parser          # preservar M3U streaming, neutralizar nomenclatura
packages/media-source-spi         # contratos de adapters
packages/audio-metadata           # tags e artwork
packages/podcast-client           # RSS/Atom/OPML
packages/open-subsonic-client     # P1
packages/library-index            # consultas e FTS compartilháveis
apps/android-tv                   # Android adaptativo, incluindo mobile
apps/desktop                      # Windows preview
```

Antes de criar um módulo, confirmar que ele reduz dependências e não apenas move arquivos.

## 22. Refatorações obrigatórias

Antes da primeira vertical de áudio:

- extrair regras de domínio de `MainViewModel`;
- impedir que `Channel` se torne o modelo universal definitivo;
- criar `MediaSourceAdapter` sem alterar o comportamento dos adapters atuais;
- separar `VideoPlaybackCoordinator` e `AudioPlaybackCoordinator` sob contrato comum;
- manter `PlayerScreen` e composables sem acesso direto a DAO/clientes;
- criar estados tipados por feature;
- adicionar capability contracts por plataforma.

Não é obrigatório concluir toda a refatoração para mostrar a primeira tela, mas nenhuma dívida nova deve aumentar os arquivos monolíticos existentes.

## 23. Segurança e privacidade

- credenciais no Keystore/DPAPI ou equivalente;
- URLs assinadas resolvidas tardiamente;
- artwork remoto sem persistir URL sensível quando aplicável;
- logs com redaction;
- feeds privados tratados como segredo;
- exportação de playlist exige confirmação quando contiver URL autenticada;
- histórico permanece local por padrão;
- enriquecimento remoto e scrobbling são opt-in;
- telemetria nunca contém título, URL, artista, feed ou hábitos sem consentimento específico.

## 24. Performance

Metas P0:

- importar 100 mil faixas sem `OutOfMemoryError`;
- importação streaming e em lotes;
- Home não carrega biblioteca inteira;
- primeira página da biblioteca em até 500 ms após store aquecido em hardware de referência;
- busca local incremental com debounce curto;
- troca de faixa iniciada em até 300 ms para arquivo local quando a plataforma permitir;
- pré-carregar somente próxima faixa, não fila inteira;
- artwork com limites de dimensão e memória;
- background sem loop agressivo.

Testes de escala:

- 500 mil entradas M3U de áudio sintéticas;
- 100 mil faixas indexadas;
- 10 mil álbuns;
- 5 mil artistas;
- 2 mil feeds e 200 mil episódios em fixture controlada;
- filas e playlists com 10 mil itens.

## 25. Acessibilidade e controles

- D-pad completo em TV;
- teclado e mouse no Windows;
- touch no mobile;
- labels acessíveis em controles;
- foco visível;
- contraste respeitando configurações;
- tamanho de texto sem corte;
- progresso anunciado;
- atalhos de mídia não capturados quando o app não possui sessão ativa.

## 26. Localização

Manter PT-BR, inglês, alemão e italiano.

Novos textos não podem ficar hardcoded em composables ou classes desktop. Criar contratos equivalentes nas duas plataformas e testes para chaves ausentes.

## 27. Fases de implementação

### Fase 0 — contrato e proteção contra regressão

- adicionar `MediaKind` e capabilities universais;
- criar mappers para enums legados;
- definir `MediaSourceAdapter`;
- documentar ADR;
- testes de compatibilidade para vídeo existente;
- nenhum destino Música visível ainda.

### Fase 1 — rádio vertical

Menor vertical funcional:

- adicionar uma estação por URL/M3U autorizado;
- persistir estação;
- listar favoritas/recentes;
- tocar em background no Android;
- ICY metadata quando disponível;
- player compacto e tela completa;
- Windows com capability honesta.

### Fase 2 — biblioteca de música local/M3U

- tracks, artistas e álbuns;
- importação streaming;
- metadados embutidos;
- Música na Ribbon;
- Home de Música;
- fila e playlists manuais;
- busca;
- mini-player.

### Fase 3 — podcasts

- RSS/Atom;
- inscrições;
- atualização incremental;
- continuar ouvindo;
- downloads autorizados;
- velocidade e sleep timer.

### Fase 4 — audiobooks

- livros e capítulos;
- progresso agregado;
- velocidade;
- sleep timer;
- offline autorizado.

### Fase 5 — servidores pessoais

- OpenSubsonic/Navidrome;
- Jellyfin;
- WebDAV;
- sincronização opcional.

### Fase 6 — SuperHub avançado

- recomendações locais explicáveis;
- fotos e nuvem pessoal;
- sincronização multi-dispositivo;
- APIs oficiais adicionais.

## 28. Critérios de aceitação P0/P1

1. O vertical atual de TV, filmes e séries continua compilando e passando testes.
2. `MediaKind` não altera chaves persistidas existentes sem migration.
3. Uma M3U de áudio autorizada gera faixas ou estações, não canais de TV genéricos.
4. Música aparece organizada por artista e álbum quando metadados existirem.
5. Itens sem metadados continuam reproduzíveis em `Desconhecidos`.
6. Fila, favorito e histórico são isolados por perfil.
7. Rádio não cria progresso seekable falso.
8. Podcast e audiobook retomam posição.
9. Nenhum segredo aparece no banco genérico, logs ou linha de comando.
10. Android suporta background audio corretamente.
11. UI só exibe gapless, crossfade, offline e letras quando a capability existir.
12. Importação de escala não carrega o catálogo inteiro na memória.
13. PT-BR, inglês, alemão e italiano não possuem chaves faltantes.
14. Kids aplica política antes da renderização.
15. README/status não marca recurso como pronto antes de build, testes e hardware.

## 29. Testes obrigatórios

### Domínio

- mapping entre tipos legados e universais;
- identidade estável de faixa, álbum, rádio e podcast;
- deduplicação sem colisão óbvia;
- capabilities;
- regras de progresso por tipo.

### Parser/importação

- M3U de música;
- M3U de rádio;
- lista mista;
- MIME incorreto;
- redirects limitados;
- arquivo Windows-1252;
- linhas longas;
- 500 mil itens;
- cancelamento e rollback.

### Metadados

- ID3, Vorbis, FLAC e M4A fixtures públicas/sintéticas;
- artwork excessivo;
- tags malformadas;
- múltiplos artistas;
- ReplayGain;
- ausência completa de tags.

### Podcast

- RSS e Atom;
- GUID duplicado;
- enclosure ausente;
- feed privado redigido;
- ETag/304;
- paginação quando suportada;
- capítulos/transcript opcionais.

### Player

- audio focus;
- headset removido;
- background;
- fila;
- gapless capability;
- crossfade desativado por padrão;
- rádio sem seek;
- retomada spoken audio;
- erro de rede e reconexão limitada.

### UI

- TV, retrato, paisagem e Windows;
- mini-player;
- foco D-pad;
- teclado;
- leitores de tela;
- biblioteca vazia;
- metadata parcial;
- artwork ausente;
- estado offline.

## 30. Fora de escopo inicial

- catálogo musical fornecido pelo IPTV BURO;
- compartilhamento de contas/credenciais;
- bypass de DRM;
- captura de streams de serviços comerciais;
- geração automática de listas piratas;
- reconhecimento de música por microfone;
- letras obtidas por scraping sem licença;
- social feed público;
- upload automático da biblioteca;
- IA generativa na primeira vertical;
- cloud gaming junto do áudio P0.

## 31. Ordem obrigatória para Claude/Codex

1. Ler `CLAUDE.md` na raiz.
2. Ler `docs/GDD_IPTV_BURO.md`.
3. Ler este GDD.
4. Ler `docs/status/CURRENT_IMPLEMENTATION.md`.
5. Auditar o código real antes de criar qualquer módulo.
6. Preservar a branch e a implementação atual.
7. Executar `docs/PROMPT_CLAUDE_IMPLEMENT_GDD8.md` por fases.
8. Atualizar documentação somente com evidência de build/teste.

## 32. Definição de sucesso

O GDD 8 é bem-sucedido quando o IPTV BURO consegue transformar fontes de áudio autorizadas em uma biblioteca premium, organizada e multiplataforma, mantendo a mesma identidade visual, perfis, segurança, desempenho e honestidade técnica do produto de vídeo.

O objetivo não é copiar o Spotify. É entregar a experiência **BURO** para a mídia que pertence ao usuário ou que ele está autorizado a reproduzir.