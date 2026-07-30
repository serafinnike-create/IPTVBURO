# GDD 4.0 — 06. Roadmap, backlog e critérios de aceitação

## 1. Estratégia

O GDD 4.0 deve ser implementado como infraestrutura transversal, não como uma única sprint gigante.

Objetivos:

- preservar o código útil existente;
- adicionar contratos antes de workarounds;
- manter build funcionando;
- entregar recuperação incremental;
- medir antes de otimizar;
- criar fixture antes ou junto de cada correção.

---

## 2. Fase R0 — Auditoria obrigatória

### Entregáveis

```text
docs/adr/ADR-RELIABILITY-ERROR-MODEL.md
docs/audits/PLAYBACK_FAILURE_AUDIT.md
docs/audits/DATA_IMPORT_FAILURE_AUDIT.md
docs/audits/SECURITY_LOGGING_AUDIT.md
```

### Tarefas

- mapear `PlayerAdapter` atual;
- encontrar retries existentes;
- localizar `try/catch` que engolem erro;
- localizar logs com URL/header;
- identificar parsing no main thread;
- mapear importação e substituição de snapshot;
- mapear lifecycle e surface;
- identificar prefetch/trailer/multiview;
- levantar testes atuais;
- registrar lacunas sem reescrever tudo.

### Aceitação

- nenhum código produtivo grande antes da auditoria;
- ADR define fronteiras e tipos;
- inventário lista arquivos e riscos reais.

---

## 3. Fase R1 — Modelo normalizado de falhas

### P0

- `FailureCode`;
- `FailureCategory`;
- `FailureStage`;
- `NormalizedFailure`;
- adaptador de erros Media3;
- `UserMessageMapper`;
- redaction central;
- timeline local.

### Testes

- 401;
- 403;
- timeout;
- parser error;
- unsupported codec;
- decoder init;
- seek unsupported;
- unknown.

### Aceitação

- UI não recebe exceção crua;
- cada erro P0 possui código estável;
- mensagens não contêm segredo;
- unknown preserva causa interna redigida.

---

## 4. Fase R2 — RetryBudget e CircuitBreaker

### P0

- `RetryBudget`;
- backoff com jitter;
- cancelamento por sessão;
- circuit breaker por domínio;
- 429/`Retry-After`;
- no retry para auth/403;
- métricas de tentativa.

### Aceitação

- teste prova ausência de loop;
- trocar conteúdo cancela retry anterior;
- 5xx persistente abre circuito;
- EPG circuit não bloqueia playback;
- reiniciar app não martela fonte imediatamente.

---

## 5. Fase R3 — ConnectionBudgetManager

### P0

- leases;
- prioridade;
- preempção;
- timeout;
- limite configurável;
- integração com playback;
- integração com trailer/prefetch;
- diagnóstico de limite provável.

### P1

- multiview;
- thumbnails;
- probes;
- sync entre dispositivos.

### Aceitação

- fonte configurada para uma conexão nunca recebe duas mídias simultâneas;
- trailer é cancelado antes de abrir playback;
- troca de canal não vaza lease;
- crash simulado libera por expiração;
- UI explica quando multiview não cabe no limite.

---

## 6. Fase R4 — Pipeline transacional de dados

### P0

- arquivo temporário;
- streaming parser;
- staging;
- validação;
- atomic swap;
- snapshot anterior;
- relatório;
- cancelamento;
- proteção contra resposta vazia/HTML.

### P1

- remoção em duas fases;
- diff detalhado;
- schema fingerprint;
- quarentena de itens.

### Aceitação

- lista vazia não apaga catálogo;
- item inválido não derruba lista inteira;
- 100 mil itens sem ANR;
- cancelamento deixa banco consistente;
- refresh parcial é explicitamente marcado.

---

## 7. Fase R5 — EPG Integrity

### P0

- streaming XML parser seguro;
- gzip;
- timezone;
- DST;
- mapping por ID;
- manual override;
- stale detection;
- programa sem stop;
- coverage report.

### P1

- fuzzy mapping assistido;
- conflicts UI;
- offset por fonte;
- alias management.

### Aceitação

- timezone não é aplicado duas vezes;
- override manual não é perdido;
- XML truncado mantém snapshot anterior;
- `stop` inferido é marcado;
- EPG inválido não bloqueia canais.

---

## 8. Fase R6 — Playback recovery P0

### Cenários

- rede offline/retorno;
- timeout inicial;
- 5xx;
- HLS segment 404;
- playlist live parada;
- rebuffer loop;
- live edge drift;
- seek fora da janela;
- decoder init;
- audio track incompatível;
- surface recreation;
- background resume.

### Aceitação

- spinner possui limite;
- primeira recuperação não bloqueia D-pad;
- live pode voltar ao edge;
- variante ruim é excluída;
- decoder fallback roda uma vez;
- checkpoint retoma VOD;
- callback antigo é descartado.

---

## 9. Fase R7 — Device Compatibility Registry

### P0

- capability profile;
- known workaround registry;
- kill switch;
- device hash;
- histórico de sucesso;
- fallback de qualidade.

### P1

- Samsung AVPlay guard;
- webOS HLS inspector;
- Apple HLS capability layer;
- remote config assinado para workarounds.

### Aceitação

- workaround tem ID e teste;
- nenhuma condição de modelo solta no player;
- workaround pode ser desativado;
- falha fica associada a track e device profile;
- teste físico valida compatibilidade.

---

## 10. Fase R8 — Failure Test Lab

### P0

- servidor local;
- fixtures;
- Docker/execução local;
- 401/403/429/5xx;
- slow/reset;
- HLS missing segment;
- malformed M3U/XMLTV;
- redaction canary;
- CI.

### P1

- network shaping;
- chaos runner;
- device farm;
- release qualification report.

### Aceitação

- cenário reproduzível por comando;
- seed registrada;
- CI falha em vazamento de segredo;
- relatório associa cenário a recovery outcome.

---

## 11. Fase R9 — UX e suporte

### P0

- mensagens acionáveis;
- cancelamento;
- diagnóstico local;
- código de erro seguro;
- source health;
- última atualização válida.

### P1

- export bundle;
- health dashboard;
- reportar problema;
- runbooks;
- tradução PT/DE/IT/EN.

### Aceitação

- nenhuma mensagem “erro desconhecido” sem código e ação;
- usuário distingue credencial, rede, servidor e codec;
- bundle não contém segredo;
- falha não destrói navegação.

---

## 12. Backlog consolidado

## P0 — antes de beta público

- normalização de falhas;
- retry limitado;
- circuit breaker;
- connection budget;
- import transacional;
- logs redigidos;
- snapshot anterior;
- EPG timezone;
- HLS missing segment;
- decoder init fallback controlado;
- seek capability;
- background/session checkpoint;
- failure lab básico;
- testes de segredo;
- mensagens acionáveis.

## P1 — beta avançado

- source health;
- device workaround registry;
- variant exclusion;
- live drift control;
- schema drift;
- quarantine UI;
- export bundle;
- Samsung guard;
- webOS inspector;
- network shaping;
- runbooks.

## P2 — pós-lançamento

- remote signed workaround config;
- device farm automation;
- advanced CMCD quando aplicável;
- predictive source selection;
- quality autopilot learning local;
- suporte assistido por diagnóstico;
- multiview-aware recovery;
- companion diagnostics.

---

## 13. Definition of Done de uma correção

Uma correção de playback/dados só está concluída quando possui:

1. código de falha normalizado;
2. causa documentada;
3. fixture reproduzível;
4. teste automatizado quando possível;
5. recovery action limitada;
6. mensagem ao usuário;
7. evento seguro;
8. documentação/runbook quando P0;
9. validação de que não vazou segredo;
10. teste de regressão;
11. comportamento de cancelamento;
12. análise de impacto no limite de conexões.

---

## 14. SLOs iniciais de engenharia

Metas internas para avaliação, não garantias comerciais:

- zero retries infinitos;
- zero credenciais em logs;
- zero snapshot válido apagado por resposta vazia;
- 100% de falhas P0 com código normalizado;
- 100% de workarounds com kill switch;
- 100% de migrations com teste;
- 100% de connection leases com owner e timeout;
- redução contínua do p95 de primeiro frame;
- aumento da taxa de recuperação bem-sucedida sem aumentar loops.

Valores de performance devem ser definidos após baseline em dispositivos reais.

---

## 15. Release gates

Uma versão não pode ser promovida quando:

- possui vazamento de segredo;
- ANR regressou;
- retry budget falhou;
- migration falhou;
- import vazio apagou snapshot;
- player chama API em estado inválido;
- callback antigo altera sessão nova;
- fixture P0 falha;
- dispositivo Tier mínimo não consegue navegar durante buffering;
- documentação de erro P0 está ausente.

---

## 16. Ordem para o Codex agora

1. ler GDDs 1–4;
2. auditar implementação atual;
3. criar ADR;
4. implementar modelo normalizado;
5. adicionar redaction tests;
6. criar `RetryBudget`;
7. criar `ConnectionBudgetManager`;
8. montar Failure Lab mínimo;
9. integrar um cenário completo: timeout/5xx;
10. integrar segundo cenário: limite de conexões;
11. integrar import transacional;
12. reportar build, testes, commits e lacunas.

Não começar por todos os codecs ou todas as TVs ao mesmo tempo.
