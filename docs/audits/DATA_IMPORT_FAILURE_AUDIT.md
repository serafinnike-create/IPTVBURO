# Auditoria de falhas de importação de dados

- Data: 2 de agosto de 2026

## Controles existentes

- parser M3U e Xtream por fluxo, com limites defensivos;
- importação Android transacional em lotes e publicação atômica;
- teto de um milhão de entradas Xtream e baseline sintética de 500 mil;
- paginação por cursor no Room e listas virtualizadas;
- cancelamento sem apresentar erro falso;
- catálogo anterior preservado quando a nova importação falha;
- credenciais Xtream fora do catálogo e protegidas no Android/Windows.
- uma única recuperação automática para rede, HTTP 408/429 e 5xx; erros de
  autenticação e 4xx permanentes não consomem retry;
- EPG curto é isolado do catálogo e nunca impede playback quando indisponível.

## Lacunas

- Failure Normalizer completo, Connection Budget e circuit breaker do GDD 4;
- reconciliação incremental e detecção formal de catálogo degradado;
- XMLTV persistido, guia completo e conflitos temporais;
- benchmark E2E repetível com fonte autorizada acima de 305 mil em hardware alvo;
- persistência indexada do catálogo no Windows, que hoje é de sessão.

Conclusão: a arquitetura evita carregar centenas de milhares de objetos na UI e
passa o ensaio sintético, mas a confiabilidade GDD 4 ainda é parcial.
