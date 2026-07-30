# IPTV BURO — GDD 4.0: Reliability, Failure Recovery & Playback Integrity

**Versão:** 4.0  
**Data:** 30 de julho de 2026  
**Status:** extensão obrigatória dos GDDs 1.0, 2.0 e 3.0  
**Objetivo:** transformar falhas comuns de IPTV em estados diagnosticáveis, recuperáveis e compreensíveis, evitando travamentos, telas pretas, tentativas infinitas e mensagens genéricas.

---

## 1. Relação com os GDDs anteriores

Este documento não reinicia o projeto e não substitui a arquitetura existente.

- **GDD 1.0:** fundação técnica, fontes, player, segurança e licenciamento;
- **GDD 2.0:** experiência cinematográfica e diferenciais premium;
- **GDD 3.0:** integridade temporal e organização correta de lançamentos;
- **GDD 4.0:** confiabilidade, diagnóstico, recuperação automática e laboratório de falhas.

Em caso de conflito, legalidade, segurança, privacidade e integridade dos dados têm prioridade.

---

## 2. Tese do produto

Um player IPTV comum trata quase qualquer problema da mesma forma:

- mostra uma tela preta;
- exibe um spinner infinito;
- reinicia o stream várias vezes;
- apresenta “erro de reprodução” sem explicar a causa;
- culpa a internet mesmo quando o problema é codec, autenticação, EPG, servidor ou limite de conexões.

O IPTV BURO deve agir de forma diferente:

1. detectar o sintoma;
2. coletar evidências locais;
3. classificar a causa provável;
4. escolher uma recuperação segura;
5. limitar tentativas;
6. preservar a interface e a sessão;
7. explicar o resultado em linguagem simples;
8. registrar diagnóstico sem expor credenciais.

Nome do sistema: **BURO Resilience Engine**.

Nome exibido ao usuário: **Reprodução inteligente e recuperação automática**.

---

## 3. Princípios inegociáveis

1. **Nunca exibir spinner infinito.**
2. **Nunca repetir indefinidamente a mesma tentativa.**
3. **Nunca desativar validação TLS para fazer um stream funcionar.**
4. **Nunca contornar DRM, bloqueio geográfico, autorização ou limite contratual.**
5. **Nunca registrar URL completa, username, password, token ou chave de stream.**
6. **Não classificar todo erro como problema de internet.**
7. **Não abrir mais conexões do que a fonte permite.**
8. **Não usar prefetch, multiview ou probes sem respeitar o orçamento de conexões.**
9. **Não executar parsing pesado, importação ou XML no thread principal.**
10. **Não apagar dados antigos antes de uma atualização nova ser validada.**
11. **Não esconder limitações reais do formato, como stream não pesquisável.**
12. **Toda recuperação deve ser observável, cancelável e testável.**
13. **Quando a causa não for conhecida, comunicar incerteza em vez de inventar.**
14. **O usuário deve conseguir exportar um diagnóstico seguro.**
15. **Falhas de uma fonte não podem travar toda a biblioteca.**

---

## 4. Componentes obrigatórios

```text
BURO Resilience Engine
├─ FailureEventNormalizer
├─ FailureClassifier
├─ RecoveryPlanner
├─ RetryBudget
├─ SourceCircuitBreaker
├─ ConnectionBudgetManager
├─ NetworkStateObserver
├─ StreamCapabilityProfiler
├─ StreamProbeCoordinator
├─ PlaybackHealthHistory
├─ SourceHealthRegistry
├─ PlaylistIntegrityScanner
├─ EpgIntegrityScanner
├─ DecoderCompatibilityRegistry
├─ SessionCheckpointStore
├─ SafeDiagnosticBundleBuilder
├─ UserMessageMapper
└─ RecoveryAnalytics
```

### 4.1 `FailureEventNormalizer`

Transforma exceções específicas de Android Media3, Samsung AVPlay, webOS, iOS/tvOS, HTTP, parsers e banco em um modelo comum.

### 4.2 `FailureClassifier`

Classifica cada falha em uma categoria e atribui nível de confiança.

### 4.3 `RecoveryPlanner`

Escolhe a próxima ação permitida com base em:

- categoria do erro;
- tipo de conteúdo;
- histórico da fonte;
- recursos do dispositivo;
- número de tentativas;
- orçamento de conexões;
- rede atual;
- estado de autenticação;
- existência de fonte alternativa;
- ações já tentadas na sessão.

### 4.4 `RetryBudget`

Impede loops. Cada operação possui limite de tentativas, janela de tempo e política de backoff.

### 4.5 `SourceCircuitBreaker`

Interrompe temporariamente chamadas repetidas para uma origem que está falhando, evitando sobrecarga, bloqueio e experiência ruim.

### 4.6 `ConnectionBudgetManager`

Controla quantas conexões simultâneas podem ser abertas por fonte e por dispositivo.

### 4.7 `SessionCheckpointStore`

Mantém posição, canal, áudio, legenda, qualidade e estado da sessão para recuperação após erro ou reinício do app.

---

## 5. Categorias de falha

O sistema deve utilizar códigos estáveis e independentes da plataforma.

```text
SOURCE_AUTH
SOURCE_ACCESS
SOURCE_RATE_LIMIT
SOURCE_CONNECTION_LIMIT
SOURCE_SERVER
NETWORK_OFFLINE
NETWORK_UNSTABLE
DNS_FAILURE
TLS_FAILURE
CLEARTEXT_BLOCKED
HTTP_REDIRECT
HTTP_RESPONSE
PLAYLIST_PARSE
PLAYLIST_TOO_LARGE
PLAYLIST_PARTIAL
EPG_PARSE
EPG_MAPPING
EPG_TIMEZONE
MEDIA_FORMAT
MEDIA_CODEC
MEDIA_CONTAINER
MEDIA_MANIFEST
MEDIA_SEGMENT
DRM_UNSUPPORTED
DECODER_INIT
DECODER_RUNTIME
VIDEO_RENDER
AUDIO_RENDER
AUDIO_TRACK
SUBTITLE_TRACK
SEEK_UNSUPPORTED
SEEK_FAILED
LIVE_WINDOW
LIVE_EDGE_DRIFT
BUFFER_STALL
AV_SYNC
APP_MAIN_THREAD
APP_MEMORY
APP_STORAGE
DATABASE_CORRUPTION
CACHE_CORRUPTION
DEVICE_CAPABILITY
PLATFORM_STATE
UNKNOWN
```

Os detalhes completos ficam em `docs/gdd-v4/01-failure-taxonomy.md`.

---

## 6. Máquina de estados de reprodução

```text
IDLE
→ VALIDATING_SOURCE
→ ACQUIRING_CONNECTION_LEASE
→ PREPARING
→ BUFFERING_INITIAL
→ PLAYING
→ BUFFERING_RECOVERY
→ RECOVERING
→ PAUSED
→ ENDED
→ FAILED_ACTIONABLE
→ FAILED_FATAL
→ RELEASED
```

### Regras

- transições devem ser explícitas;
- ações assíncronas devem possuir ID de sessão;
- callbacks antigos não podem alterar uma sessão nova;
- somente uma recuperação ativa por sessão;
- trocar de canal cancela preparação e recuperação anteriores;
- o player não pode receber chamadas incompatíveis com seu estado;
- cada plataforma implementa o contrato por meio do `PlayerAdapter`.

---

## 7. Escada de recuperação

O motor tenta somente ações compatíveis com a causa provável.

```text
Nível 0 — preservar UI e coletar evidências
Nível 1 — repetir operação transitória com backoff curto
Nível 2 — atualizar URL, token ou manifesto quando suportado
Nível 3 — escolher outra representação, bitrate ou track
Nível 4 — mudar perfil de buffer e alvo de live edge
Nível 5 — aplicar workaround de decoder conhecido para o dispositivo
Nível 6 — usar outra versão equivalente cadastrada pelo usuário
Nível 7 — parar com mensagem clara e ações úteis
```

A escada não deve ser percorrida automaticamente quando:

- credenciais estão incorretas;
- acesso foi negado;
- DRM não é suportado;
- conteúdo não está autorizado;
- URL é inválida;
- formato é definitivamente incompatível;
- o usuário cancelou;
- o orçamento de conexões acabou;
- o circuito da fonte está aberto.

---

## 8. UX de falha

### 8.1 Regra do tempo

Valores iniciais recomendados, configuráveis e sujeitos a testes reais:

- até 2 segundos: estado visual discreto;
- após 2 segundos: mostrar “Conectando…”;
- após 8 segundos: mostrar progresso e ação “Cancelar”;
- após 15 segundos: iniciar diagnóstico ou recuperação compatível;
- após o limite da política: apresentar falha acionável.

### 8.2 Mensagens

Mensagens devem explicar o que o app sabe, sem expor detalhes sensíveis.

Exemplos:

```text
A fonte recusou o acesso. Verifique os dados da conta.
```

```text
Este canal excedeu o número de conexões permitido pela fonte.
Feche outra reprodução ou tente novamente mais tarde.
```

```text
O vídeo usa um formato que esta televisão não consegue decodificar.
Tente outra qualidade ou versão disponível.
```

```text
O programa saiu da janela de reprise disponível.
Volte ao ao vivo ou escolha um horário dentro da janela.
```

```text
O servidor respondeu, mas parou de enviar vídeo.
Estamos tentando uma recuperação segura.
```

### 8.3 Ações possíveis

- tentar novamente;
- verificar fonte;
- editar credenciais;
- reduzir qualidade;
- usar modo estável;
- voltar ao ao vivo;
- escolher outra versão;
- abrir diagnóstico;
- copiar código de erro seguro;
- reportar problema;
- voltar ao catálogo.

---

## 9. Orçamento de conexões

Muitas fontes limitam conexões simultâneas. O aplicativo deve possuir um `ConnectionBudgetManager` central.

### 9.1 Tipos de lease

```text
PLAYBACK_PRIMARY
PLAYBACK_SECONDARY
PREFETCH
PROBE
EPG_DOWNLOAD
PLAYLIST_REFRESH
TRAILER
MULTIVIEW_TILE
THUMBNAIL_PREVIEW
```

### 9.2 Prioridade

```text
PLAYBACK_PRIMARY > ação explícita do usuário > recuperação > EPG/refresh > prefetch > trailer
```

### 9.3 Regras

- o usuário pode configurar o limite conhecido da fonte;
- padrão conservador: uma conexão de mídia por fonte;
- download de playlist e EPG não deve ocorrer no meio de uma troca de canal crítica;
- trailers e prefetch devem ceder imediatamente ao playback principal;
- multiview só é ativado após verificar o orçamento;
- leases abandonados devem expirar;
- toda conexão deve ter proprietário e motivo;
- o app deve detectar sintomas de limite excedido sem afirmar certeza quando o servidor usa mensagem genérica.

---

## 10. Segurança e limites legais

O sistema pode diagnosticar e recuperar erros técnicos, mas não pode:

- quebrar DRM;
- buscar chaves não fornecidas legalmente;
- falsificar autorização;
- contornar bloqueio regional;
- ocultar excesso de conexões;
- rotacionar IP para evitar limites;
- desativar certificados;
- reescrever tokens de terceiros;
- tentar credenciais automaticamente.

Quando houver `403`, DRM incompatível ou restrição geográfica, o app deve explicar que o acesso não foi autorizado e encerrar a recuperação automática.

---

## 11. Documentos obrigatórios

1. [Taxonomia de falhas e matriz de sintomas](gdd-v4/01-failure-taxonomy.md)
2. [Motor de recuperação, retry, circuit breaker e conexões](gdd-v4/02-recovery-engine.md)
3. [Integridade de playlist, Xtream, EPG e persistência](gdd-v4/03-playlist-epg-data-integrity.md)
4. [Playback, codecs, HLS, live, seek e compatibilidade por dispositivo](gdd-v4/04-playback-device-compatibility.md)
5. [Observabilidade segura, laboratório de falhas e testes](gdd-v4/05-observability-test-lab.md)
6. [Roadmap, backlog e critérios de aceitação](gdd-v4/06-roadmap-acceptance.md)
7. [Prompt de continuação para o Codex](PROMPT_CODEX_CONTINUE_GDD4.md)

---

## 12. Fontes técnicas principais

- Android Media3 — troubleshooting, formatos, live streaming, seleção de tracks e políticas de erro;
- Apple — especificação de autoria HLS;
- RFC 8216 — HTTP Live Streaming;
- Samsung AVPlay — estados, callbacks, seek e códigos de erro;
- LG webOS TV — troubleshooting HLS;
- XMLTV DTD — regras de programas, horários, timezone e intervalos.

Links completos e decisões derivadas aparecem nos capítulos específicos.

---

## 13. Instrução ao Codex

O Codex deve implementar primeiro a infraestrutura de diagnóstico e os contratos, não uma coleção de `try/catch` espalhados.

A ordem inicial obrigatória é:

1. auditar o código atual;
2. criar ADR da arquitetura de falhas;
3. normalizar erros do player e rede;
4. implementar `RetryBudget`;
5. implementar `ConnectionBudgetManager`;
6. adicionar telemetria segura local;
7. criar fixtures e testes de falha;
8. integrar recuperação incrementalmente.

Nenhuma recuperação é considerada concluída sem teste reproduzível.