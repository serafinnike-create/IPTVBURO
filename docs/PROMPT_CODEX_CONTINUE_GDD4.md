# Prompt para o Codex — Continuação com GDD 4.0

Continue o projeto atual do IPTV BURO sem reiniciar, apagar ou substituir código útil.

## 1. Leitura obrigatória

Leia integralmente, nesta ordem:

1. `docs/GDD_IPTV_BURO.md`
2. todos os capítulos em `docs/gdd/`
3. `docs/GDD_2_REVOLUTIONARY_EXPERIENCE.md`
4. todos os capítulos em `docs/gdd-v2/`
5. `docs/GDD_3_CATALOG_RELEASE_INTELLIGENCE.md`
6. `docs/PROMPT_CODEX_CONTINUE_GDD3.md`
7. `docs/GDD_4_RELIABILITY_FAILURE_RECOVERY.md`
8. todos os capítulos em `docs/gdd-v4/`

O GDD 4.0 é a fonte de verdade para falhas, retries, recuperação, limite de conexões, integridade de importação, diagnóstico e testes de caos.

---

## 2. Missão

Construir a fundação do **BURO Resilience Engine**, para que o aplicativo:

- diferencie problemas de rede, fonte, autenticação, playlist, EPG, codec, decoder, seek, live e banco;
- nunca use spinner ou retry infinito;
- respeite o limite de conexões da fonte;
- preserve catálogo válido quando uma atualização falhar;
- recupere playback apenas quando a ação for segura;
- explique o erro ao usuário;
- gere diagnóstico sem vazar credenciais.

Não implemente bypass de DRM, autorização, bloqueio regional, certificados ou limites contratuais.

---

## 3. Antes de programar: auditoria

Examine o código atual e crie:

```text
docs/adr/ADR-RELIABILITY-ERROR-MODEL.md
docs/audits/PLAYBACK_FAILURE_AUDIT.md
docs/audits/DATA_IMPORT_FAILURE_AUDIT.md
docs/audits/SECURITY_LOGGING_AUDIT.md
```

A auditoria deve responder:

- onde o player é criado e liberado;
- quais adaptadores existem;
- como exceções são tratadas;
- onde existem retries;
- quais retries podem virar loop;
- onde URLs, headers ou credenciais entram em logs;
- como playlist e EPG substituem dados antigos;
- se parsing ocorre no main thread;
- como troca de canal cancela sessão anterior;
- como surface/lifecycle são tratados;
- se trailers, prefetch, probes ou multiview abrem conexões;
- quais testes existem;
- quais partes dos GDDs já estão implementadas.

Não reescreva o app antes de concluir a auditoria.

---

## 4. Primeira implementação obrigatória

### 4.1 Modelo de falhas

Crie ou adapte:

```text
FailureCode
FailureCategory
FailureStage
FailureSeverity
Retryability
FailureEvidence
SafeFailureContext
NormalizedFailure
FailureEventNormalizer
FailureClassifier
UserMessageMapper
```

Requisitos:

- códigos estáveis;
- sem exceção crua na UI;
- confiança da classificação;
- código de plataforma preservado;
- HTTP status preservado;
- contexto seguro e redigido;
- unit tests.

### 4.2 Eventos iniciais P0

Suportar no primeiro incremento:

```text
AUTH_INVALID_CREDENTIALS
ACCESS_FORBIDDEN
SOURCE_RATE_LIMIT
SOURCE_SERVER_ERROR
SOURCE_CONNECTION_LIMIT
NETWORK_OFFLINE
NETWORK_UNSTABLE
DNS_FAILURE
TLS_CERTIFICATE_FAILURE
CLEARTEXT_HTTP_BLOCKED
HTTP_UNEXPECTED_STATUS
PLAYLIST_PARSE
EPG_PARSE
MEDIA_FORMAT
MEDIA_CODEC
DECODER_INIT_FAILED
SEEK_UNSUPPORTED
SEEK_OUT_OF_RANGE
LIVE_WINDOW_EXPIRED
INITIAL_BUFFER_TIMEOUT
REBUFFER_LOOP
MAIN_THREAD_BLOCKED
UNKNOWN
```

---

## 5. Segurança de logs

Implemente redaction antes do logger.

Proibido registrar:

- URL completa;
- query string;
- username;
- password;
- token;
- authorization;
- cookies;
- playlist content;
- chaves;
- Device Key completa.

Crie testes com canary secrets. O build/test deve falhar se os segredos aparecerem em logs ou diagnostic bundle.

---

## 6. RetryBudget

Crie:

```text
RetryBudget
RetryKey
RetryPolicy
BackoffStrategy
RecoveryAttempt
```

Regras iniciais:

- 401 inválido: não repetir;
- 403: não repetir;
- 429: respeitar `Retry-After`;
- 5xx/timeouts: retry limitado com jitter;
- parser/format determinístico: não repetir;
- troca de conteúdo cancela retry antigo;
- nenhuma operação supera budget global da sessão;
- testes provam ausência de loop.

---

## 7. ConnectionBudgetManager

Crie:

```text
ConnectionPolicy
ConnectionLease
ConnectionLeaseType
ConnectionBudgetManager
ConnectionBudgetSnapshot
```

Tipos mínimos:

```text
PLAYBACK_PRIMARY
PLAYBACK_SECONDARY
PREFETCH
PROBE
PLAYLIST_REFRESH
EPG_DOWNLOAD
TRAILER
MULTIVIEW_TILE
THUMBNAIL_PREVIEW
```

Regras:

- padrão conservador de uma conexão de mídia por fonte;
- playback principal preempta trailer/prefetch;
- trocar canal libera/cancela o anterior;
- lease possui owner, priority e timeout;
- probes não rodam no limite;
- multiview verifica budget;
- testes simulam limite 1.

---

## 8. Failure Lab mínimo

Crie estrutura semelhante a:

```text
tools/failure-lab/
├─ server/
├─ fixtures/
├─ scenarios/
└─ README.md
```

O laboratório mínimo deve simular:

- 401;
- 403;
- 429 com `Retry-After`;
- 500/502/503/504;
- timeout;
- conexão resetada;
- redirect loop;
- HTML no lugar de playlist;
- M3U malformada;
- XMLTV truncado;
- HLS com segmento 404;
- manifesto live que para de avançar.

Use tecnologia compatível com o repositório atual. Não introduza framework pesado sem ADR.

---

## 9. Primeiro fluxo vertical completo

Implemente ponta a ponta:

### Cenário A — erro 503 transitório

1. servidor retorna 503 duas vezes;
2. erro é normalizado;
3. retry budget permite tentativas;
4. backoff é aplicado;
5. terceira resposta funciona;
6. timeline registra recuperação;
7. UI permanece responsiva;
8. nenhum segredo aparece.

### Cenário B — limite de uma conexão

1. playback principal está ativo;
2. trailer tenta abrir;
3. manager nega/preempta trailer;
4. playback continua;
5. evento seguro é registrado;
6. teste comprova que não houve segunda conexão de mídia.

### Cenário C — atualização vazia

1. snapshot válido existe;
2. fonte responde HTML ou lista vazia;
3. importação falha validação;
4. snapshot anterior continua ativo;
5. UI informa falha de atualização;
6. relatório não expõe URL.

---

## 10. Integração com player

Não espalhe lógica pela UI.

Criar fronteiras:

```text
PlayerAdapter
→ platform error mapper
→ normalized failure
→ recovery planner
→ recovery executor
```

Regras:

- cada sessão possui `sessionId`;
- cada preparação possui `generationId`;
- callbacks atrasados são descartados;
- apenas uma recuperação por sessão;
- cancelamento libera lease e recursos;
- player é acessado na thread correta;
- não chamar API em estado inválido.

---

## 11. Importação transacional

Se o pipeline já existe, adapte; se não existe, documente plano antes de construir.

Fluxo obrigatório:

```text
download temp
→ verify
→ stream parse
→ validate
→ staging
→ verify staging
→ atomic swap
```

Nunca substituir snapshot válido por:

- resposta vazia;
- HTML;
- JSON de erro;
- gzip truncado;
- parsing parcial não aprovado;
- queda extrema não confirmada.

---

## 12. UX inicial

Crie mensagens mapeadas para:

- credenciais inválidas;
- acesso recusado;
- servidor temporariamente indisponível;
- limite de conexões provável;
- rede offline;
- formato incompatível;
- seek indisponível;
- fora da janela DVR;
- atualização de catálogo falhou.

Cada mensagem deve possuir:

- título curto;
- explicação;
- ação principal;
- ação secundária opcional;
- código seguro;
- severidade.

Não usar somente “Erro de reprodução”.

---

## 13. Testes obrigatórios

### Unit

- classifier;
- message mapper;
- retry budget;
- circuit breaker;
- connection leases;
- redaction;
- state/generation guard.

### Integration

- failure lab;
- import staging;
- snapshot preservation;
- recovery timeline;
- cancellation.

### Regression

- build atual;
- navegação por D-pad;
- player básico;
- parser existente;
- banco/migrations.

---

## 14. Commits

Use commits pequenos e descritivos, por exemplo:

```text
docs: audit current reliability architecture
feat: add normalized playback failure model
test: add secret redaction canaries
feat: add retry budget and backoff
feat: add source connection lease manager
test: add deterministic failure lab scenarios
feat: preserve catalog snapshot on failed refresh
feat: recover transient playback server errors
```

Não misture refatoração ampla, design visual e reliability no mesmo commit.

---

## 15. Relatório ao finalizar o incremento

Entregue:

```text
1. resumo do que existia;
2. riscos encontrados;
3. arquivos alterados;
4. decisões de arquitetura;
5. testes executados e resultados;
6. cenários Failure Lab implementados;
7. falhas que agora recuperam;
8. falhas que agora terminam com mensagem clara;
9. limitações restantes;
10. próximos três incrementos recomendados;
11. SHAs dos commits.
```

---

## 16. Proibições

- não reiniciar o projeto;
- não apagar código útil sem ADR;
- não adicionar retry infinito;
- não desabilitar TLS;
- não implementar bypass de DRM/geo/auth;
- não registrar credenciais;
- não criar probes paralelos sem budget;
- não limpar banco como fallback genérico;
- não usar lista vazia como atualização válida;
- não afirmar que uma causa foi confirmada sem evidência;
- não declarar pronto sem fixtures e testes.
