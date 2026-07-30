# GDD 4.0 — 01. Taxonomia de falhas e matriz de sintomas

## 1. Objetivo

Criar uma linguagem comum para erros de Android Media3, Samsung AVPlay, webOS, iOS/tvOS, HTTP, parsers, banco e backend.

O app não deve usar somente a mensagem da exceção como regra de negócio. Cada evento bruto precisa ser convertido em um `NormalizedFailure`.

```kotlin
data class NormalizedFailure(
    val code: FailureCode,
    val category: FailureCategory,
    val stage: FailureStage,
    val severity: FailureSeverity,
    val retryability: Retryability,
    val confidence: Float,
    val sourceId: String?,
    val sessionId: String?,
    val platformCode: String?,
    val httpStatus: Int?,
    val occurredAt: Instant,
    val evidence: List<FailureEvidence>,
    val safeContext: SafeFailureContext
)
```

Credenciais, tokens, URLs completas e headers sensíveis são proibidos em `safeContext`.

---

## 2. Estágios

```text
SOURCE_VALIDATION
AUTHENTICATION
PLAYLIST_DOWNLOAD
PLAYLIST_PARSE
EPG_DOWNLOAD
EPG_PARSE
CATALOG_INDEX
METADATA_ENRICHMENT
PLAYER_OPEN
PLAYER_PREPARE
INITIAL_BUFFER
DECODER_INIT
PLAYING
SEEK
TRACK_CHANGE
LIVE_RECOVERY
BACKGROUND_RESUME
DATABASE_READ
DATABASE_WRITE
CACHE_READ
CACHE_WRITE
LICENSE_CHECK
UNKNOWN_STAGE
```

---

## 3. Severidade

- `INFO`: evento esperado, sem impacto direto;
- `DEGRADED`: reprodução continua com qualidade ou função reduzida;
- `RECOVERABLE`: sessão interrompida, mas recuperação é provável;
- `ACTION_REQUIRED`: depende do usuário;
- `FATAL_SESSION`: a sessão atual precisa terminar;
- `FATAL_SOURCE`: a fonte deve ser desativada até correção;
- `FATAL_APP`: risco de corrupção ou crash geral.

---

## 4. Taxonomia por domínio

## 4.1 Fonte e autenticação

### `AUTH_INVALID_CREDENTIALS`

**Sintomas:** 401, resposta de login inválido, lista vazia após autenticação, endpoint de usuário nega acesso.

**Não fazer:** repetir senha automaticamente, testar variações, registrar username/password.

**Resposta:** encerrar retry, preservar formulário e solicitar revisão das credenciais.

### `AUTH_EXPIRED_TOKEN`

**Sintomas:** sessão antes válida começa a retornar 401/403; endpoint de renovação existe e foi configurado legalmente.

**Resposta:** uma renovação controlada; se falhar, pedir nova autenticação.

### `ACCESS_FORBIDDEN`

**Sintomas:** 403, mensagem de acesso negado, restrição da conta ou origem.

**Resposta:** não tentar contornar; explicar que a fonte recusou o acesso.

### `ACCOUNT_DISABLED_OR_EXPIRED`

**Sintomas:** Xtream retorna status de conta expirada/desativada ou data de expiração passada.

**Resposta:** mostrar estado da conta; não insistir no player.

### `SOURCE_CONNECTION_LIMIT`

**Sintomas possíveis:** mensagem explícita, 403/429 durante segunda sessão, primeiro stream cai quando outro abre, respostas genéricas após prefetch ou multiview.

**Resposta:** liberar leases não essenciais, interromper prefetch/trailer, manter apenas playback principal e informar que a causa é provável, não absoluta, se a fonte não confirmar.

### `SOURCE_RATE_LIMIT`

**Sintomas:** 429, `Retry-After`, bloqueio temporário após muitas requisições.

**Resposta:** respeitar `Retry-After`, abrir circuit breaker e pausar atualizações automáticas.

### `SOURCE_SERVER_ERROR`

**Sintomas:** 500, 502, 503, 504, conexão fechada pelo servidor, segmentos indisponíveis.

**Resposta:** retry limitado com jitter; não pedir credenciais.

### `SOURCE_NOT_FOUND`

**Sintomas:** 404/410 para endpoint, playlist, manifesto ou segmento.

**Resposta:** diferenciar recurso removido de segmento live expirado; atualizar manifesto uma vez quando aplicável.

---

## 4.2 Rede e transporte

### `NETWORK_OFFLINE`

**Sintomas:** sem rede validada, perda de conectividade, modo avião.

**Resposta:** pausar retries até evento de rede; manter posição e tela.

### `NETWORK_UNSTABLE`

**Sintomas:** timeouts intermitentes, resets, grande variação de throughput, rebuffer repetido.

**Resposta:** modo estável, bitrate menor, buffer maior para VOD, live offset mais conservador para live.

### `DNS_FAILURE`

**Sintomas:** host não resolvido.

**Resposta:** retry curto somente após mudança de rede ou TTL; sugerir verificar DNS/rede sem alterar DNS do usuário automaticamente.

### `TLS_CERTIFICATE_FAILURE`

**Sintomas:** handshake, cadeia inválida, certificado expirado, hostname incompatível.

**Resposta:** falhar de modo seguro; nunca aceitar qualquer certificado.

### `CLEARTEXT_HTTP_BLOCKED`

**Sintomas:** plataforma bloqueia HTTP sem TLS.

**Resposta:** informar que a fonte usa conexão não protegida; seguir decisão de produto/ADR para modo legado, sem fingir que HTTP é seguro.

### `REDIRECT_LOOP`

**Sintomas:** redirecionamentos repetidos ou cadeia acima do limite.

**Resposta:** parar; exibir erro de origem mal configurada.

### `CROSS_PROTOCOL_REDIRECT`

**Sintomas:** HTTPS redireciona para HTTP ou mudança incompatível com política.

**Resposta:** bloquear downgrade inseguro por padrão; não reenviar headers sensíveis para host diferente.

### `CAPTIVE_PORTAL`

**Sintomas:** resposta HTML de login no lugar de M3U/HLS, redirecionamento para portal da rede.

**Resposta:** informar que a rede exige login; não tentar parsear HTML como playlist.

### `CLOCK_SKEW`

**Sintomas:** TLS, token ou EPG falha por relógio incorreto.

**Resposta:** comparar horário do dispositivo com headers de servidor quando disponível e sugerir correção automática do relógio.

---

## 4.3 HTTP e conteúdo recebido

### `HTTP_UNEXPECTED_STATUS`

Guardar status, estágio e host anonimizado.

Política inicial:

| Status | Retry automático | Ação |
|---|---:|---|
| 400 | não | validar URL/parâmetros |
| 401 | no máximo após refresh válido | autenticação |
| 403 | não | acesso recusado |
| 404 | contextual | atualizar manifesto ou marcar removido |
| 408 | sim, limitado | backoff |
| 409 | contextual | estado da fonte |
| 410 | não | recurso removido |
| 416 | não igual | corrigir range/seek |
| 429 | sim, respeitando `Retry-After` | circuit breaker |
| 500 | sim, limitado | retry com jitter |
| 502/503/504 | sim, limitado | servidor indisponível |

### `UNEXPECTED_CONTENT_TYPE`

**Exemplos:** HTML no lugar de JSON, página de proteção no lugar de vídeo, imagem no lugar de playlist.

**Resposta:** inspecionar magic bytes/primeiros bytes com limite seguro; não confiar apenas na extensão.

### `TRUNCATED_RESPONSE`

**Sintomas:** gzip incompleto, XML termina abruptamente, segmento menor que esperado.

**Resposta:** descartar atualização parcial e manter snapshot anterior.

---

## 4.4 Playlist M3U/Xtream

### `PLAYLIST_INVALID_HEADER`

Arquivo não começa como esperado, contém HTML ou formato diferente.

### `PLAYLIST_MALFORMED_ENTRY`

`#EXTINF` incompleto, URL ausente, aspas quebradas, atributos inválidos.

**Resposta:** parser tolerante por entrada; rejeitar somente o item ruim, não toda a lista, quando for seguro.

### `PLAYLIST_UNSUPPORTED_ENCODING`

BOM, encoding legado ou bytes inválidos.

**Resposta:** detectar UTF-8/BOM e fallback controlado; registrar perda de caracteres.

### `PLAYLIST_TOO_LARGE`

Lista excede limite de memória, armazenamento ou tempo.

**Resposta:** parsing streaming, lotes, progresso, cancelamento e limites configuráveis.

### `PLAYLIST_DUPLICATE_ITEM`

Itens iguais, URLs iguais, nomes normalizados iguais ou variantes.

**Resposta:** preservar origem, deduplicar no catálogo e integrar com Source Fusion.

### `XTREAM_PARTIAL_RESPONSE`

JSON válido, porém incompleto, paginação inconsistente ou categorias sem itens.

**Resposta:** atualização transacional; manter dados anteriores para seções não confirmadas.

### `SOURCE_SCHEMA_DRIFT`

Campos mudaram de tipo, nome ou formato.

**Resposta:** adaptadores versionados e fixtures; não quebrar todo o importador por um campo opcional.

---

## 4.5 EPG/XMLTV

### `EPG_INVALID_XML`

XML malformado, entidades inválidas ou arquivo truncado.

### `EPG_GZIP_CORRUPT`

Arquivo comprimido não pode ser aberto ou termina incompleto.

### `EPG_CHANNEL_ID_MISMATCH`

`tvg-id` da lista não corresponde ao `channel id` do XMLTV.

**Resposta:** matching em camadas: ID exato, alias manual, nome normalizado, país/idioma; nunca substituir associação manual silenciosamente.

### `EPG_TIMEZONE_MISSING`

XMLTV sem timezone explícito deve seguir a regra do formato, mas o app deve sinalizar risco de deslocamento quando a fonte parece usar horário local.

### `EPG_TIMEZONE_INCONSISTENT`

Programas alternam offsets de forma impossível ou ignoram horário de verão.

**Resposta:** detectar anomalia, manter valores originais, permitir offset manual por fonte.

### `EPG_PROGRAM_OVERLAP`

Programas do mesmo canal se sobrepõem.

**Resposta:** preservar dados, marcar conflito e escolher política de apresentação sem reescrever silenciosamente a fonte.

### `EPG_MISSING_STOP`

Usar início do próximo programa como fim inferido quando seguro; marcar como inferência.

### `EPG_STALE`

Guia terminou no passado ou não cobre a janela configurada.

**Resposta:** mostrar última atualização e não substituir guia válido por arquivo vazio.

---

## 4.6 Manifesto, segmentos e HLS

### `MANIFEST_UNRECOGNIZED`

Formato não reconhecido, MIME incorreto ou conteúdo inválido.

### `HLS_MISSING_SEGMENT`

Segmento referenciado retorna 404/410.

**Resposta live:** atualizar playlist; se segmento saiu da janela, reposicionar perto do live edge.

**Resposta VOD:** falhar de forma acionável; não pular silenciosamente conteúdo sem política explícita.

### `HLS_DISCONTINUITY_MISMATCH`

Sequências ou timestamps inconsistentes entre variantes.

**Resposta:** excluir variante problemática temporariamente; não alternar entre variantes incompatíveis.

### `HLS_PLAYLIST_STALLED`

Manifesto live não avança.

**Resposta:** detectar ausência de novos segmentos, recarregar com limites e classificar servidor congelado.

### `HLS_VARIANT_INCOMPATIBLE`

Variante anuncia codec/bitrate incompatível ou incorreto.

**Resposta:** excluir track e selecionar representação suportada.

### `HLS_AUDIO_ONLY_MIXED`

Algumas TVs webOS falham quando playlists combinam variantes de vídeo e áudio-only sem sinalização correta de codecs.

**Resposta:** detectar estrutura, evitar variante incompatível e registrar limitação da plataforma.

### `SEGMENT_DECRYPTION_FAILURE`

Chave ausente, inválida ou método não suportado.

**Resposta:** não buscar chaves fora da fonte; classificar como proteção/manifesto incompatível.

---

## 4.7 Codec, container e decoder

### `UNSUPPORTED_CONTAINER`

Container não suportado ou sem extractor adequado.

### `UNSUPPORTED_VIDEO_CODEC`

Codec, profile, level, resolução ou frame rate excede o dispositivo.

### `UNSUPPORTED_AUDIO_CODEC`

Vídeo aparece sem áudio ou preparação falha por DTS/TrueHD/codec ausente.

**Resposta:** selecionar track compatível; software decoder opcional somente quando licenciamento, desempenho e tamanho permitirem.

### `DECODER_INIT_FAILED`

Falha ao criar MediaCodec/decoder da plataforma.

**Resposta:** uma tentativa com decoder alternativo conhecido, redução de resolução ou desativação de recurso específico; registrar workaround por modelo.

### `DECODER_RUNTIME_FAILURE`

Decoder inicia e falha durante playback.

**Resposta:** liberar completamente o pipeline antes de tentar fallback; evitar loop no mesmo decoder.

### `VIDEO_BLACK_WITH_AUDIO`

Possíveis causas: superfície, decoder, HDR, codec, resolução, estado da plataforma.

**Resposta:** verificar frames renderizados, superfície e codec; não assumir que a URL está quebrada.

### `VIDEO_WITHOUT_AUDIO`

Possíveis causas: track incompatível, passthrough, áudio desabilitado, codec não suportado.

### `HDR_RENDER_FAILURE`

Imagem preta, lavada ou cores incorretas.

**Resposta:** detectar capacidade real do display/decoder; oferecer SDR/track alternativa quando disponível.

---

## 4.8 Reprodução e tempo

### `INITIAL_BUFFER_TIMEOUT`

Tempo até primeiro frame excedeu o budget.

### `REBUFFER_LOOP`

Múltiplos ciclos de buffering em janela curta.

### `LIVE_EDGE_DRIFT`

Playback fica muito atrás do ao vivo sem intenção do usuário.

### `LIVE_WINDOW_EXPIRED`

Posição desejada saiu da janela DVR.

### `SEEK_UNSUPPORTED`

Container/stream não oferece índice ou janela adequada.

**Resposta:** desabilitar controle ou oferecer seek aproximado quando suportado; nunca fingir precisão.

### `SEEK_OUT_OF_RANGE`

Posição fora da duração ou DVR.

### `AV_SYNC_DRIFT`

Áudio e vídeo divergem além do limite.

**Resposta:** medir, tentar ressincronização suportada e registrar dispositivo/stream.

### `SUBTITLE_OUT_OF_SYNC`

Legenda atrasada/adiantada, timestamps ruins ou fuso indevido.

**Resposta:** offset manual por conteúdo e persistência local.

---

## 4.9 Aplicativo, memória, armazenamento e estado

### `MAIN_THREAD_BLOCKED`

Parsing, banco, rede ou imagem bloqueou UI/D-pad.

**Resposta:** mover para dispatcher apropriado, adicionar tracing e limite de lotes.

### `PLAYER_WRONG_THREAD`

Player acessado por thread incorreta.

**Resposta:** contrato único de thread/application looper.

### `LOW_MEMORY`

Capas, catálogos ou múltiplos players excedem memória.

**Resposta:** reduzir cache, liberar players secundários, usar paginação e performance tier.

### `STORAGE_FULL`

Banco/cache não pode gravar.

**Resposta:** limpar somente cache descartável; nunca apagar credenciais ou favoritos sem consentimento.

### `DATABASE_MIGRATION_FAILED`

**Resposta:** backup, rollback e modo somente leitura quando possível.

### `CACHE_CORRUPT`

**Resposta:** invalidar item afetado e reconstruir; não apagar todo o catálogo como primeira medida.

### `SURFACE_LOST`

Troca de atividade, HDMI, background ou recriação remove superfície.

**Resposta:** reanexar surface sem reiniciar fonte quando possível.

### `AUDIO_FOCUS_LOST`

Pausar/reduzir áudio conforme política da plataforma.

### `APP_BACKGROUND_KILLED`

Restaurar checkpoint sem duplicar sessão de rede.

---

## 5. Matriz de sintomas visíveis

| Sintoma | Causas prováveis | Evidências mínimas | Primeira resposta |
|---|---|---|---|
| tela preta sem áudio | rede, manifesto, decoder, surface | HTTP, tracks, frames, decoder | classificar antes de retry |
| tela preta com áudio | surface, vídeo codec, HDR | áudio renderizado, frames zero | fallback visual/decoder |
| vídeo sem áudio | track, codec, passthrough | tracks e renderer | selecionar track compatível |
| spinner infinito | retry sem limite, servidor parado | tempo e tentativas | cancelar e diagnosticar |
| canal abre e fecha | limite de conexões, auth, servidor | leases e HTTP | liberar conexões extras |
| trava ao avançar | stream não pesquisável ou range ruim | capabilities e 416 | desabilitar/seek aproximado |
| EPG deslocado | timezone/DST | offset e fonte | correção por fonte |
| app congela ao importar | parsing no main ou lista gigante | trace e tamanho | streaming parser/lotes |
| alguns canais falham só nesta TV | codec/profile/plataforma | capability profile | variante/fallback |
| live fica atrasado | buffer excessivo/live offset | live offset | aproximar do live edge |
| legenda não aparece | track não sinalizada/encoding | tracks e parser | seleção/sideload seguro |
| erro após alguns minutos | token, segmento, decoder, servidor | timeline dos eventos | refresh/fallback contextual |

---

## 6. Confidence scoring

A classificação deve incluir confiança:

- `0.90–1.00`: evidência explícita;
- `0.70–0.89`: múltiplas evidências consistentes;
- `0.40–0.69`: causa provável;
- abaixo de `0.40`: desconhecida.

Mensagens ao usuário devem refletir isso:

- alta confiança: “A fonte recusou as credenciais.”
- média: “Parece que o limite de conexões foi atingido.”
- baixa: “A reprodução foi interrompida e não foi possível identificar a causa com segurança.”

---

## 7. Referências técnicas

- Android Media3 troubleshooting: https://developer.android.com/media/media3/exoplayer/troubleshooting
- Android Media3 supported formats: https://developer.android.com/media/media3/exoplayer/supported-formats
- Android Media3 live streaming: https://developer.android.com/media/media3/exoplayer/live-streaming
- Samsung AVPlay API: https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
- LG webOS HLS troubleshooting: https://webostv.developer.lge.com/faq/streaming-http-live-streaming-hls-troubleshooting
- RFC 8216 HLS: https://www.rfc-editor.org/rfc/rfc8216
- XMLTV DTD: https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd
