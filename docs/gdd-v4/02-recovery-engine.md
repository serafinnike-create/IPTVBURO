# GDD 4.0 — 02. Motor de recuperação, retry e orçamento de conexões

## 1. Objetivo

Implementar recuperação previsível e testável. O sistema não deve espalhar retries, delays e workarounds por telas ou players.

A decisão central é representada por:

```kotlin
sealed interface RecoveryAction {
    data object WaitForNetwork : RecoveryAction
    data class RetrySameRequest(val delay: Duration) : RecoveryAction
    data object RefreshManifest : RecoveryAction
    data object RefreshAuthorizedSession : RecoveryAction
    data class ExcludeVariant(val variantId: String, val duration: Duration) : RecoveryAction
    data class SelectLowerBitrate(val ceiling: Int?) : RecoveryAction
    data class ApplyBufferProfile(val profile: BufferProfile) : RecoveryAction
    data object SeekToLiveEdge : RecoveryAction
    data class SeekInsideLiveWindow(val positionMs: Long) : RecoveryAction
    data class ApplyDecoderWorkaround(val workaroundId: String) : RecoveryAction
    data class SwitchEquivalentSource(val candidateId: String) : RecoveryAction
    data object RecreatePlayerPipeline : RecoveryAction
    data object AskUserForCredentials : RecoveryAction
    data object AskUserToCloseAnotherPlayback : RecoveryAction
    data object StopWithActionableError : RecoveryAction
    data object StopFatal : RecoveryAction
}
```

---

## 2. Arquitetura

```text
Platform error/event
        ↓
FailureEventNormalizer
        ↓
FailureClassifier
        ↓
RecoveryContextBuilder
        ↓
RecoveryPlanner
        ↓
PolicyGuard
        ↓
RetryBudget + ConnectionBudgetManager + CircuitBreaker
        ↓
RecoveryExecutor
        ↓
OutcomeRecorder
```

### 2.1 `RecoveryContext`

```kotlin
data class RecoveryContext(
    val failure: NormalizedFailure,
    val contentType: ContentType,
    val playbackMode: PlaybackMode,
    val sessionState: PlaybackSessionState,
    val networkState: NetworkState,
    val sourceHealth: SourceHealth,
    val deviceProfile: DeviceCapabilityProfile,
    val connectionBudget: ConnectionBudgetSnapshot,
    val retryBudget: RetryBudgetSnapshot,
    val attemptedActions: List<RecoveryAttempt>,
    val equivalentSources: List<EquivalentSourceCandidate>,
    val userPreferences: RecoveryPreferences
)
```

---

## 3. Retry Budget

Cada operação possui uma chave:

```text
sourceId + operationType + sessionId + failureClass
```

### 3.1 Regras padrão

- autenticação inválida: zero retry automático;
- 403: zero retry automático;
- 404 de VOD: uma confirmação opcional, depois fatal;
- 404 de segmento live: atualizar manifesto uma vez;
- 408/reset/timeout: até três tentativas curtas;
- 429: respeitar `Retry-After`; sem polling agressivo;
- 5xx: até três tentativas com jitter;
- progressive live: política mais tolerante, sem exceder budget global;
- decoder init: no máximo um workaround diferente;
- source fallback: cada candidato no máximo uma vez por sessão;
- seek: não repetir no mesmo ponto após falha determinística.

### 3.2 Backoff

```text
baseDelay × 2^(attempt-1) + jitter
```

Limitar o delay por tipo de operação. Não usar o mesmo backoff para troca de canal e atualização de EPG.

### 3.3 Cancelamento

Todo retry deve cancelar quando:

- usuário muda de conteúdo;
- sessão é liberada;
- rede muda e uma estratégia nova é necessária;
- fonte é editada;
- app entra em estado incompatível;
- circuit breaker abre;
- ação perde prioridade para playback principal.

---

## 4. Circuit Breaker

Estados:

```text
CLOSED → OPEN → HALF_OPEN → CLOSED
```

### 4.1 Escopo

Circuitos separados para:

- autenticação;
- playlist;
- EPG;
- metadata;
- playback;
- host/CDN;
- endpoint específico.

Uma falha de EPG não deve impedir playback.

### 4.2 Abertura

Abrir quando houver:

- sequência de 5xx;
- 429;
- falhas de conexão repetidas;
- resposta inválida repetida;
- limite de conexões provável;
- falha determinística de autenticação.

### 4.3 Half-open

Permitir uma tentativa controlada após cooldown. Se funcionar, fechar gradualmente; se falhar, reabrir.

### 4.4 Persistência

Persistir estado curto para evitar hammering após reiniciar app, mas expirar automaticamente.

---

## 5. Connection Budget Manager

### 5.1 Problema

Providers podem limitar sessões simultâneas. Preload, trailer, thumbnails, probes e multiview podem consumir conexões sem o usuário perceber.

### 5.2 Modelo

```kotlin
data class ConnectionPolicy(
    val sourceId: String,
    val maxMediaConnections: Int,
    val maxControlConnections: Int,
    val allowParallelPlaylistAndPlayback: Boolean,
    val allowPrefetch: Boolean,
    val allowMultiview: Boolean,
    val leaseTimeout: Duration
)
```

### 5.3 Lease

```kotlin
data class ConnectionLease(
    val id: String,
    val sourceId: String,
    val ownerSessionId: String,
    val type: ConnectionLeaseType,
    val priority: Int,
    val acquiredAt: Instant,
    val expiresAt: Instant
)
```

### 5.4 Regras de preempção

- playback principal pode cancelar trailer e prefetch;
- ação explícita pode cancelar atualização em background;
- multiview não pode derrubar playback principal;
- probes não rodam enquanto a fonte está no limite;
- trocar canal deve reutilizar/liberar lease anterior antes de abrir outro quando possível;
- encerramento anormal deve liberar lease por timeout.

### 5.5 Detecção de limite

Sinais:

- mensagem explícita da API;
- status conhecido da fonte;
- falha apenas após segunda conexão;
- playback existente cai ao iniciar outro;
- padrão repetido em histórico.

O sistema deve usar `confidence` e dizer “parece que” quando não houver confirmação explícita.

---

## 6. Stream Probe Coordinator

### 6.1 Riscos

Probes podem:

- consumir uma conexão;
- contar como playback;
- acionar limite;
- baixar mídia desnecessária;
- falhar em servidores que não aceitam HEAD;
- enviar credenciais para redirect inseguro.

### 6.2 Política

- não usar `HEAD` como única verdade;
- preferir informações já obtidas pelo player;
- probe somente por ação clara ou durante importação controlada;
- uma probe por fonte por vez;
- limite estrito de bytes e tempo;
- cancelar imediatamente quando playback começa;
- não seguir redirect para host diferente com headers sensíveis;
- não probe DRM ou chaves;
- armazenar resultado com TTL.

---

## 7. Recovery Planner por cenário

## 7.1 Timeout inicial

1. verificar rede;
2. verificar circuit breaker;
3. confirmar lease;
4. retry curto se transitório;
5. reduzir variante se adaptive;
6. aplicar modo estável;
7. fallback equivalente se permitido;
8. falha acionável.

## 7.2 Rebuffer repetido

1. medir throughput e buffer;
2. excluir variante que falhou;
3. reduzir teto de bitrate;
4. aumentar buffer em VOD;
5. aumentar live offset em live;
6. registrar saúde da fonte;
7. oferecer modo estável.

## 7.3 401 no meio do playback

1. verificar suporte autorizado a refresh;
2. realizar uma renovação;
3. reconstruir URL/headers sem logar segredo;
4. retomar do checkpoint;
5. se falhar, solicitar credenciais.

## 7.4 403

- parar;
- não variar headers para enganar servidor;
- não tentar proxy/VPN;
- explicar acesso recusado.

## 7.5 404 de segmento live

1. atualizar manifesto;
2. verificar janela atual;
3. mover para live edge se o segmento expirou;
4. excluir variante se apenas ela estiver quebrada;
5. limitar tentativas.

## 7.6 Decoder init

1. confirmar codec/profile/resolução;
2. consultar registro de incompatibilidade por dispositivo;
3. tentar um decoder alternativo conhecido;
4. limitar qualidade/resolução;
5. desativar passthrough/tunneling somente se relacionado;
6. escolher variante compatível;
7. falhar sem loop.

## 7.7 Seek falha

1. consultar `SourceCapabilities`;
2. validar posição na duração/janela;
3. se formato permitir, usar seek aproximado;
4. em live, limitar ao DVR;
5. se não pesquisável, desabilitar e explicar;
6. não reiniciar o vídeo do começo silenciosamente.

## 7.8 App retorna do background

1. validar sessão e superfície;
2. verificar rede;
3. recuperar checkpoint;
4. em live, decidir entre posição anterior e live edge conforme intenção;
5. não abrir duas sessões concorrentes;
6. restaurar tracks e volume.

---

## 8. Buffer profiles

```text
LOW_LATENCY
BALANCED
STABLE
CINEMA
DATA_SAVER
AUTO
```

### 8.1 Low latency

- esportes e eventos;
- buffer menor;
- recuperação rápida;
- maior risco em rede instável.

### 8.2 Stable

- rede instável;
- bitrate conservador;
- buffer maior;
- live offset mais distante.

### 8.3 Cinema

- VOD;
- prioriza continuidade e seek;
- buffer maior, respeitando memória.

### 8.4 Auto

Decisão baseada em:

- histórico da fonte;
- throughput;
- tipo de conteúdo;
- RAM;
- codec;
- rebuffer recente;
- preferência do usuário.

Mudanças automáticas devem ser discretas e registradas no diagnóstico.

---

## 9. Source Health Registry

Métricas por fonte e por variante:

```text
timeToFirstByte
timeToFirstFrame
rebufferCount
rebufferDuration
averageBitrate
httpErrorRate
manifestErrorRate
decoderErrorRate
successfulSessions
failedSessions
lastSuccessAt
lastFailureAt
lastKnownWorkingProfile
```

### 9.1 Health score

Não criar um único número opaco. Exibir dimensões:

- disponibilidade;
- velocidade;
- estabilidade;
- compatibilidade;
- EPG;
- metadados.

### 9.2 Cuidado causal

Não culpar a fonte quando a evidência aponta para rede local ou decoder. Guardar contexto por rede e dispositivo.

---

## 10. Checkpoints

Salvar de forma leve:

- conteúdo/canal;
- posição;
- live offset/intenção;
- áudio;
- legenda;
- velocidade;
- qualidade selecionada;
- último frame timestamp;
- perfil de buffer;
- source candidate;
- recovery action atual.

### 10.1 Frequência

- VOD: intervalos controlados e eventos importantes;
- live: salvar intenção, não cada posição;
- antes de seek, troca de track e background;
- não escrever banco a cada segundo.

---

## 11. Idempotência e concorrência

- cada sessão possui `sessionId` único;
- cada preparação possui `generationId`;
- callbacks devem verificar ambos;
- resultado atrasado de canal anterior deve ser descartado;
- apenas um `RecoveryExecutor` por sessão;
- operações de DB usam transação;
- refresh de playlist usa single-flight;
- EPG e playlist têm mutex por fonte;
- cancelamento deve liberar player, rede e lease.

---

## 12. User preferences

Configurações avançadas:

- recuperação automática: ligada/desligada;
- modo de estabilidade;
- limite de conexões por fonte;
- permitir fallback para outra versão;
- retornar ao vivo após erro;
- preservar posição VOD;
- diagnóstico detalhado;
- enviar relatório somente com consentimento.

Valores avançados ficam ocultos no modo normal.

---

## 13. Critérios de aceitação

- nenhuma sessão executa retry ilimitado;
- 401 inválido não gera loop;
- 429 respeita espera;
- prefetch nunca impede playback principal;
- trocar canal cancela callbacks antigos;
- source fallback não repete candidato que já falhou;
- seek fora de DVR não é enviado ao player;
- retorno do background não cria sessão duplicada;
- circuit breaker separa EPG e playback;
- logs não contêm segredos;
- todas as recovery actions possuem teste unitário.

---

## 14. Referências

- Media3 LoadErrorHandlingPolicy: https://developer.android.com/reference/androidx/media3/exoplayer/upstream/LoadErrorHandlingPolicy
- Media3 DefaultLoadErrorHandlingPolicy: https://developer.android.com/reference/androidx/media3/exoplayer/upstream/DefaultLoadErrorHandlingPolicy
- Media3 live streaming: https://developer.android.com/media/media3/exoplayer/live-streaming
- Samsung AVPlay API: https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
