# GDD 4.0 — 04. Playback, codecs, HLS, live, seek e compatibilidade

## 1. Objetivo

Garantir que o IPTV BURO trate a mídia como uma combinação de várias camadas:

```text
protocol
→ manifest
→ container
→ video codec/profile/level
→ audio codec/layout
→ subtitles
→ encryption/DRM
→ decoder
→ surface/display
→ audio output
```

A extensão do arquivo ou o nome “4K” não são suficientes para prever compatibilidade.

---

## 2. Device Capability Profile

Na primeira execução e após mudanças relevantes, construir:

```kotlin
data class DeviceCapabilityProfile(
    val platform: Platform,
    val modelHash: String,
    val osVersion: String,
    val appVersion: String,
    val displayModes: List<DisplayMode>,
    val hdrTypes: Set<HdrType>,
    val videoDecoders: List<DecoderCapability>,
    val audioDecoders: List<DecoderCapability>,
    val passthroughFormats: Set<AudioFormat>,
    val maxReliableResolution: Resolution?,
    val maxReliableFrameRate: Int?,
    val availableMemoryTier: PerformanceTier,
    val knownWorkarounds: Set<String>
)
```

### 2.1 Regra

Capacidade anunciada não é garantia de funcionamento. O app deve distinguir:

- suportado pela plataforma;
- anunciado pelo decoder;
- testado com sucesso;
- falhou anteriormente neste dispositivo;
- desconhecido.

---

## 3. Source Capability Profile

Após preparar o conteúdo:

```text
isLive
isDynamic
hasKnownDuration
isSeekable
seekMode
liveWindowStart
liveWindowEnd
targetLiveOffset
protocol
container
videoCodec
videoProfile
videoLevel
resolution
frameRate
hdrType
audioTracks
subtitleTracks
drmScheme
adaptiveVariants
```

A UI usa capacidades reais, não suposições pelo tipo da categoria.

---

## 4. HLS

### 4.1 Validação de master playlist

Verificar:

- variantes com URI;
- `BANDWIDTH` plausível;
- `RESOLUTION` válida;
- `CODECS` quando disponível;
- grupos de áudio e legenda;
- referências existentes;
- protocolos de URL permitidos;
- variantes duplicadas;
- áudio-only misturado;
- redirects seguros.

### 4.2 Media playlist

Monitorar:

- `TARGETDURATION`;
- `MEDIA-SEQUENCE`;
- novos segmentos;
- `DISCONTINUITY` e sequência;
- `PROGRAM-DATE-TIME`;
- `ENDLIST`;
- segmentos removidos cedo demais;
- duração da janela;
- segmentos que retornam 404;
- timestamps regressivos;
- chaves e métodos suportados.

### 4.3 Stalled playlist

Considerar manifesto parado quando múltiplos reloads não apresentam avanço além de uma janela proporcional ao `TARGETDURATION`.

A política não deve usar um timeout fixo universal; calcular pelo manifesto e pelo histórico.

### 4.4 Missing segment

- live: atualizar manifesto e reposicionar dentro da janela;
- VOD: não pular automaticamente sem política explícita;
- variante específica: excluir temporariamente;
- todas as variantes: servidor/source failure.

### 4.5 Discontinuity

Mudanças de codec, timestamp ou encoder devem estar sinalizadas. Se variantes não permanecem sincronizadas:

- parar adaptação entre variantes afetadas;
- fixar representação estável;
- registrar manifesto inconsistente;
- não alternar repetidamente.

### 4.6 webOS

Algumas playlists HLS podem falhar quando misturam variantes de vídeo e áudio-only sem `CODECS` adequado. O adaptador webOS deve:

- inspecionar master playlist;
- excluir variante incompatível quando seguro;
- apresentar diagnóstico específico;
- manter fixtures com casos corretos e incorretos.

---

## 5. Progressive e containers

### 5.1 Seek

Nem todo arquivo progressivo possui índice suficiente.

Modos:

```text
EXACT
KEYFRAME_APPROXIMATE
CONSTANT_BITRATE_APPROXIMATE
LIVE_WINDOW
NOT_SUPPORTED
UNKNOWN_UNTIL_PREPARED
```

### 5.2 Regras

- FLV progressivo pode não ser pesquisável;
- alguns MP3/ADTS/AMR só permitem aproximação por bitrate constante;
- MP4 com metadados/índice no final pode iniciar ou buscar lentamente;
- range HTTP incorreto pode gerar 416;
- não habilitar seek até conhecer capacidade;
- mostrar “aproximado” quando necessário.

### 5.3 Slow seek

Investigar:

- distância até keyframe;
- índice ausente;
- servidor sem range;
- bitrate alto;
- decoder flush lento;
- container malformado;
- rede.

Não resolver sempre recriando o player.

---

## 6. Live playback

### 6.1 Conceitos

- live edge;
- live offset;
- janela DVR;
- posição padrão;
- stream live encerrado;
- progressive live sem janela pesquisável.

### 6.2 UI

Estados:

```text
AO VIVO
ATRASADO 00:42
REPRISE DISPONÍVEL
FORA DA JANELA
TRANSMISSÃO ENCERRADA
```

### 6.3 Drift

Se o usuário não pausou/voltou e o offset cresce:

1. medir buffer e velocidade;
2. ajustar playback speed dentro de limites imperceptíveis quando suportado;
3. reduzir qualidade se rede não sustenta;
4. reposicionar perto do live edge quando necessário;
5. nunca saltar sem informar quando a diferença for perceptível.

### 6.4 DVR

- seek somente entre início e fim atuais;
- janela pode mover enquanto UI está aberta;
- validar novamente no momento do seek;
- em Samsung AVPlay, consultar duração live/DVR antes de `seekTo`;
- posição expirada oferece “Voltar ao vivo”.

---

## 7. Codec e track selection

### 7.1 Vídeo

Avaliar:

- MIME;
- codec;
- profile/level;
- bit depth;
- chroma;
- resolução;
- frame rate;
- HDR;
- adaptive support;
- secure decoder requirement.

### 7.2 Áudio

Avaliar:

- codec;
- canais;
- sample rate;
- passthrough;
- decoder local;
- idioma;
- role flags;
- commentary/descriptive audio.

### 7.3 Seleção automática

Preferências em ordem configurável:

1. faixa suportada;
2. idioma do perfil;
3. faixa principal;
4. canais compatíveis;
5. bitrate adequado;
6. histórico de sucesso.

### 7.4 Falha de áudio

Tentar, quando aplicável:

- outra track;
- desativar passthrough;
- limitar canais;
- decoder alternativo licenciado;
- manter vídeo pausado durante mudança crítica.

Não converter/transcodificar na nuvem sem produto, consentimento e infraestrutura próprios.

---

## 8. Decoder Compatibility Registry

```text
platform
osVersionRange
manufacturer
modelPattern
codec
profile
resolutionRange
symptom
workaround
confidence
source
lastValidatedVersion
```

### 8.1 Workarounds possíveis

- preferir decoder específico;
- desativar tunneling;
- desativar passthrough;
- limitar resolução;
- limitar frame rate;
- evitar variante HDR;
- recriar surface;
- usar software decoder opcional;
- aumentar buffer de inicialização.

Cada workaround deve possuir:

- ID;
- motivo;
- plataforma;
- teste;
- telemetria;
- kill switch;
- versão de expiração/revisão.

Não criar `if (model == ...)` sem registro e teste.

---

## 9. Surface, display e lifecycle

### 9.1 Surface loss

Eventos possíveis:

- activity recriada;
- app vai ao background;
- HDMI/display mode muda;
- overlay fecha/abre;
- troca entre player e detalhes.

Resposta:

- separar sessão de mídia da superfície;
- reanexar quando possível;
- não recarregar stream se pipeline continua válido;
- manter frame/placeholder;
- descartar callbacks de surface antiga.

### 9.2 Refresh rate

Futuro:

- detectar frame rate do conteúdo;
- usar APIs da plataforma para modo de exibição quando apropriado;
- evitar mudanças frequentes;
- restaurar estado ao sair;
- respeitar preferência do usuário.

### 9.3 HDR

- verificar display e decoder;
- diferenciar HDR10, HDR10+, Dolby Vision e HLG;
- não prometer HDR apenas por metadata;
- oferecer variante SDR;
- guardar falha por dispositivo/track.

---

## 10. Subtitles

### 10.1 Formatos

Android Media3 suporta formatos de legenda integrados e side-loaded específicos; outras plataformas possuem matrizes próprias.

### 10.2 Problemas

- encoding incorreto;
- MIME ausente;
- track não sinalizada;
- timestamp fora da duração;
- estilo ilegível;
- texto fora da safe area;
- linguagem errada;
- legenda forçada não selecionada;
- offset.

### 10.3 Regras

- detectar encoding com limites;
- permitir offset por conteúdo;
- persistir idioma preferido;
- respeitar forced/SDH quando identificado;
- manter safe area de TV;
- não bloquear vídeo se legenda falhar;
- informar quando arquivo externo não pode ser lido.

---

## 11. Samsung AVPlay

### 11.1 State guard

AVPlay possui estados e operações válidas específicas. Criar `SamsungAvPlayStateGuard`.

```text
NONE
IDLE
READY
PLAYING
PAUSED
```

### 11.2 Regras

- não chamar método em estado inválido;
- aguardar callback de operação assíncrona antes da próxima ação incompatível;
- `seekTo` live somente dentro do DVR;
- normalizar erros como invalid state, unsupported format, connection failed e seek failed;
- sempre `close/release` em encerramento;
- callbacks antigos verificam generation ID.

---

## 12. Android Media3

### 12.1 Threading

- player criado e acessado no application looper correto;
- listeners não executam parsing pesado;
- operações de UI voltam ao dispatcher apropriado;
- evitar “player accessed on wrong thread”.

### 12.2 Load error policy

Usar política central com fallback de track/location quando realmente disponível. Parser errors e erros determinísticos não devem ser tratados como timeouts transitórios.

### 12.3 Track selection

Configurar preferências de idioma, resolução, bitrate e MIME por perfil e capacidade. Observar `onTracksChanged` antes de construir UI final de faixas.

### 12.4 Testes físicos

Emuladores não representam todos os decoders e problemas de TVs. Playback precisa ser validado em dispositivos reais.

---

## 13. Audio/video synchronization

Métricas:

- timestamp de áudio;
- timestamp de vídeo;
- dropped frames;
- buffer;
- decoder counters;
- discontinuities.

Política:

- tolerância configurável;
- resync suportado pelo player;
- não aplicar seeks repetidos;
- distinguir erro da fonte de carga do dispositivo;
- registrar track e decoder.

---

## 14. Playback quality metrics

```text
timeToFirstFrameMs
joinTimeMs
rebufferCount
rebufferDurationMs
playbackDurationMs
rebufferRatio
droppedFrames
averageVideoBitrate
selectedResolution
liveOffsetMs
seekLatencyMs
fatalErrorCode
recoveryCount
recoveryOutcome
```

O usuário vê linguagem simples. Métricas técnicas ficam no diagnóstico.

---

## 15. Critérios de aceitação

- UI de seek acompanha capacidade real;
- 404 de segmento live atualiza manifesto antes de falhar;
- variante incompatível pode ser excluída sem derrubar todas;
- decoder workaround roda no máximo uma vez por sessão;
- surface recriada não gera playback duplicado;
- live seek nunca sai da janela atual;
- áudio incompatível oferece outra track quando disponível;
- HDR problemático pode cair para SDR disponível;
- webOS fixture de áudio-only é detectada;
- Samsung não recebe chamada em estado inválido;
- callbacks antigos não afetam conteúdo novo;
- Android player é usado na thread correta.

---

## 16. Referências

- Android Media3 supported formats: https://developer.android.com/media/media3/exoplayer/supported-formats
- Android Media3 troubleshooting: https://developer.android.com/media/media3/exoplayer/troubleshooting
- Android Media3 live streaming: https://developer.android.com/media/media3/exoplayer/live-streaming
- Android Media3 track selection: https://developer.android.com/media/media3/exoplayer/track-selection
- Android Media3 supported devices: https://developer.android.com/media/media3/exoplayer/supported-devices
- Apple HLS authoring: https://developer.apple.com/documentation/http-live-streaming/hls-authoring-specification-for-apple-devices
- RFC 8216: https://www.rfc-editor.org/rfc/rfc8216
- Samsung AVPlay: https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
- LG webOS HLS troubleshooting: https://webostv.developer.lge.com/faq/streaming-http-live-streaming-hls-troubleshooting
