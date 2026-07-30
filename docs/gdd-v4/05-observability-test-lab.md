# GDD 4.0 — 05. Observabilidade segura e laboratório de falhas

## 1. Objetivo

Fazer cada erro importante ser reproduzível sem coletar segredos do usuário.

O IPTV BURO deve possuir duas camadas:

- **diagnóstico local:** detalhado, privado e disponível ao usuário;
- **telemetria opcional:** agregada, redigida e enviada somente conforme consentimento e política de privacidade.

---

## 2. Eventos

### 2.1 Estrutura

```kotlin
data class ReliabilityEvent(
    val eventId: String,
    val sessionId: String?,
    val sourceHash: String?,
    val hostHash: String?,
    val contentType: ContentType?,
    val stage: FailureStage,
    val code: FailureCode?,
    val action: RecoveryActionType?,
    val outcome: RecoveryOutcome?,
    val attempt: Int?,
    val durationMs: Long?,
    val deviceTier: PerformanceTier,
    val platform: Platform,
    val appVersion: String,
    val timestamp: Instant,
    val safeAttributes: Map<String, String>
)
```

### 2.2 Proibido

- URL completa;
- query string;
- username;
- password;
- token;
- authorization header;
- cookies;
- Device Key completa;
- nome real escolhido para a fonte quando puder identificar usuário;
- títulos assistidos em telemetria remota sem consentimento específico;
- conteúdo de playlist;
- chaves DRM.

---

## 3. Redaction

### 3.1 Antes do logger

Segredos devem ser removidos antes de chegar ao sistema de log, não somente na exportação.

### 3.2 Regras

- host convertido em hash com salt rotativo;
- path reduzido a padrão estrutural quando necessário;
- IDs externos hasheados;
- headers usam allowlist, nunca blocklist;
- exceções são serializadas por tipo e stack interna, sem mensagem crua quando puder conter URL;
- regex de segurança como segunda barreira;
- testes automáticos com segredos falsos.

### 3.3 Canary secrets

A suíte deve inserir tokens fictícios conhecidos e falhar se eles aparecerem em:

- logs;
- crash reports;
- banco de diagnóstico;
- export bundle;
- analytics payload.

---

## 4. Diagnostic Bundle

Gerado somente por ação do usuário.

Conteúdo permitido:

```text
manifest.json
app-state.json
source-health-redacted.json
playback-timeline.json
failure-events.json
capability-profile.json
import-report.json
epg-report.json
logs-redacted.txt
```

### 4.1 Manifest

```json
{
  "schemaVersion": 1,
  "generatedAt": "...",
  "appVersion": "...",
  "platform": "ANDROID_TV",
  "privacyReview": "PASSED",
  "containsCredentials": false
}
```

### 4.2 UX

Antes de exportar:

- explicar o conteúdo;
- informar que URLs e credenciais serão removidas;
- permitir visualizar resumo;
- gerar arquivo local;
- compartilhar somente após ação explícita.

---

## 5. Timeline de sessão

Exemplo:

```text
00:00.000 session_created
00:00.041 connection_lease_acquired
00:00.080 player_open
00:00.322 manifest_loaded
00:00.481 tracks_known
00:01.102 first_frame
05:12.444 buffer_started
05:14.012 http_503
05:14.015 recovery_planned retry_same_request
05:15.241 playback_resumed
05:15.243 recovery_success
```

Timeline usa relógio monotônico para durações e wall clock apenas para contexto.

---

## 6. Métricas de produto

### 6.1 Playback

- taxa de início bem-sucedido;
- p50/p95 de primeiro frame;
- rebuffer ratio;
- recoveries por hora;
- taxa de recuperação bem-sucedida;
- fatal errors por categoria;
- seek success rate;
- live edge drift;
- troca de canal p50/p95.

### 6.2 Dados

- import success rate;
- tempo de parsing;
- itens por segundo;
- itens ignorados;
- queda de catálogo bloqueada;
- EPG coverage;
- channel mapping rate;
- refresh stale rate.

### 6.3 Aplicativo

- ANR;
- crash-free sessions;
- peak memory;
- database migration success;
- low-storage events;
- focus/navigation failures em testes.

---

## 7. Health dashboard local

Área avançada:

```text
Saúde da fonte

Playback: estável
Primeiro frame médio: 1,4 s
Buffering nas últimas 10 sessões: 0,7%
Erros do servidor: 2
Erros de codec: 0
EPG associado: 91%
Última atualização válida: hoje, 15:42
Limite configurado: 1 conexão
```

Não transformar score em promessa absoluta.

---

## 8. Failure Test Lab

Criar módulo de teste local com servidor controlado.

Estrutura sugerida:

```text
tools/failure-lab/
├─ server/
├─ fixtures/
│  ├─ m3u/
│  ├─ xtream/
│  ├─ xmltv/
│  ├─ hls/
│  ├─ progressive/
│  └─ subtitles/
├─ scenarios/
├─ network-profiles/
└─ README.md
```

### 8.1 Server endpoints

```text
/status/401
/status/403
/status/404
/status/408
/status/429?retryAfter=5
/status/500
/status/502
/status/503
/status/504
/slow/headers
/slow/body
/reset/after-bytes
/redirect/loop
/redirect/cross-host
/content/html-instead-of-m3u
/content/truncated-gzip
/hls/missing-segment
/hls/stalled-playlist
/hls/discontinuity-mismatch
/hls/variant-unsupported
/xmltv/timezone-missing
/xmltv/overlap
/xmltv/truncated
```

### 8.2 Controle

- deterministic seed;
- latência configurável;
- bandwidth cap;
- packet loss quando ambiente permitir;
- resposta por contador de chamadas;
- reset por cenário;
- logs do servidor sem segredo;
- Docker para CI quando possível.

---

## 9. Cenários obrigatórios

## 9.1 Rede

- offline antes de abrir;
- rede cai durante playback;
- rede retorna;
- Wi-Fi muda para Ethernet;
- DNS falha;
- TLS inválido;
- captive portal;
- timeout de headers;
- body lento;
- conexão resetada.

## 9.2 HTTP/auth

- 401 permanente;
- 401 seguido de refresh válido;
- 403;
- 429 com `Retry-After`;
- 5xx transitório que recupera;
- 5xx persistente que abre circuito;
- redirect loop;
- redirect para host diferente;
- HTML no lugar de JSON.

## 9.3 Connection limit

- limite 1 com playback + trailer;
- limite 1 durante troca de canal;
- multiview solicitado sem budget;
- lease vazado simulado;
- app morre antes de liberar lease;
- erro genérico após segunda conexão.

## 9.4 Playlist/EPG

- lista de 100 mil itens;
- entrada malformada no meio;
- arquivo vazio depois de snapshot válido;
- Xtream parcial;
- EPG truncado;
- timezone errado;
- DST;
- channel mismatch;
- programa sem stop;
- overlap.

## 9.5 Playback

- HLS segment 404;
- playlist live parada;
- variante 4K incompatível com HD válida;
- áudio incompatível com segunda track válida;
- video black/audio present simulado;
- seek 416;
- live seek fora da janela;
- decoder init failure;
- surface recreation;
- callbacks atrasados após troca de canal.

## 9.6 App

- low memory;
- storage full;
- DB migration failure;
- cache corrupt;
- background kill;
- main-thread parser guard;
- cancelamento durante import;
- cancelamento durante recovery.

---

## 10. Network profiles

```text
PERFECT: 100 Mbps, 10 ms
GOOD: 25 Mbps, 30 ms
MOBILE: 8 Mbps, 80 ms
POOR: 3 Mbps, 150 ms
UNSTABLE: 12 Mbps variável, resets
LIVE_STRESS: bandwidth próximo ao bitrate
OFFLINE_FLAP: ciclos online/offline
```

Perfis devem ser reproduzíveis. Valores são fixtures de teste, não afirmações universais.

---

## 11. Device matrix

### 11.1 Android/Google TV

Cobrir pelo menos:

- dispositivo de entrada com pouca RAM;
- box intermediário;
- TV Sony/Google TV;
- Fire TV quando suportado;
- diferentes API levels;
- HDR e não HDR.

### 11.2 Samsung

- gerações Tizen distintas;
- AVPlay state tests;
- codec matrix;
- DVR seek.

### 11.3 LG webOS

- versões diferentes;
- HLS mixed audio-only fixture;
- memory pressure;
- remote navigation.

### 11.4 Apple

- tvOS físico;
- HLS variants;
- audio/subtitles;
- lifecycle.

Emulador não substitui teste de decoder em hardware real.

---

## 12. Test pyramid

```text
Unit tests
├─ classifier
├─ retry budget
├─ circuit breaker
├─ connection leases
├─ parsers
└─ redaction

Integration tests
├─ failure lab HTTP
├─ database staging
├─ player fake adapter
├─ import pipelines
└─ diagnostic bundle

Platform tests
├─ Media3
├─ AVPlay
├─ webOS media
└─ AVPlayer

Real-device tests
└─ codec, HDR, surface, lifecycle, remote
```

---

## 13. Property-based e fuzz tests

Aplicar em:

- parser M3U;
- XMLTV dates;
- URL canonicalization;
- redaction;
- state machine;
- retry budget;
- playlist attributes;
- corrupted gzip.

Regras:

- parser nunca entra em loop;
- uso de memória possui limite;
- segredo nunca aparece no output;
- uma linha inválida não corrompe itens anteriores;
- máquina de estados nunca faz transição proibida.

---

## 14. Chaos tests

Durante execução:

- matar processo;
- remover rede;
- encher disco simulado;
- alterar relógio;
- recriar activity/surface;
- trocar conteúdo rapidamente;
- pressionar D-pad repetidamente;
- retornar callback fora de ordem;
- corromper cache;
- provocar resposta parcial.

O resultado esperado deve ser definido antes do teste.

---

## 15. Gates de CI

Bloquear merge se:

- teste de segredo falhar;
- parser crashar em fixture;
- retry exceder budget;
- state machine aceitar transição ilegal;
- import vazio substituir snapshot válido;
- diagnostic bundle contiver URL completa;
- lint/test/build falhar;
- migration test falhar.

Testes de hardware podem ser gate de release, não necessariamente de todo commit.

---

## 16. Runbooks

Criar documentos:

```text
docs/runbooks/playback-401.md
docs/runbooks/playback-403.md
docs/runbooks/hls-segment-404.md
docs/runbooks/decoder-init.md
docs/runbooks/epg-timezone.md
docs/runbooks/connection-limit.md
docs/runbooks/database-migration.md
```

Cada runbook contém:

- sintoma;
- código interno;
- evidências;
- recuperação;
- ação do usuário;
- logs seguros;
- fixture de teste;
- owner do módulo.

---

## 17. Critérios de aceitação

- 100% dos códigos P0 possuem fixture;
- cada recovery gera timeline;
- diagnostic bundle passa redaction test;
- servidor de falhas roda localmente;
- CI cobre 401, 403, 429, 5xx, timeout e reset;
- teste de connection budget impede segunda mídia;
- import vazio mantém snapshot anterior;
- callback fora de ordem é descartado;
- teste físico é obrigatório antes de declarar codec compatível;
- métricas distinguem rede, fonte e decoder.

---

## 18. Referências

- Android Media3 troubleshooting: https://developer.android.com/media/media3/exoplayer/troubleshooting
- Android supported devices/testing note: https://developer.android.com/media/media3/exoplayer/supported-devices
- Android ANR: https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs
- Samsung AVPlay errors: https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html
