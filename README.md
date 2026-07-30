# IPTV BURO

IPTV BURO é um player OTT/IPTV premium, local-first e multiplataforma, projetado para transformar fontes de mídia configuradas legalmente pelo usuário em uma experiência moderna para TV, celular e computador.

> **Aviso legal:** este projeto é somente um reprodutor de mídia. Ele não fornece canais, filmes, séries, listas, assinaturas ou qualquer conteúdo protegido. O usuário deve possuir autorização legal para acessar as fontes adicionadas.

## Estado do projeto

Planejamento e fundação técnica. O primeiro alvo de desenvolvimento é Android TV/Google TV, com arquitetura preparada para Android, Fire TV, Windows, macOS, iOS/tvOS, Samsung Tizen e LG webOS.

O produto está sendo desenvolvido em quatro camadas complementares:

- **GDD 1.0:** fundação técnica, reprodução, fontes, segurança, licenciamento e plataformas;
- **GDD 2.0:** identidade cinematográfica, experiência premium, descoberta inteligente, TV ao vivo reinventada e continuidade entre dispositivos;
- **GDD 3.0:** inteligência temporal para separar lançamentos reais de conteúdos antigos adicionados recentemente;
- **GDD 4.0:** confiabilidade, diagnóstico, retry controlado, limite de conexões, integridade de importação e recuperação de falhas.

## Documentação

- [Índice geral dos GDDs](docs/GDD_IPTV_BURO.md)
- [GDD 2.0 — Revolutionary Entertainment Experience](docs/GDD_2_REVOLUTIONARY_EXPERIENCE.md)
- [GDD 3.0 — Catalog Intelligence & Release Integrity](docs/GDD_3_CATALOG_RELEASE_INTELLIGENCE.md)
- [GDD 4.0 — Reliability, Failure Recovery & Playback Integrity](docs/GDD_4_RELIABILITY_FAILURE_RECOVERY.md)
- [Prompt mestre inicial para o Codex](docs/PROMPT_MESTRE_CODEX_IPTV_BURO.md)
- [Prompt para o Codex continuar com o GDD 2.0](docs/PROMPT_CODEX_CONTINUE_GDD2.md)
- [Prompt para o Codex implementar o GDD 3.0](docs/PROMPT_CODEX_CONTINUE_GDD3.md)
- [Prompt para o Codex implementar o GDD 4.0](docs/PROMPT_CODEX_CONTINUE_GDD4.md)

## Produto

- BURO Cinematic System com identidade visual própria.
- Living Home contextual, detalhes cinematográficos e Minha BURO.
- BURO Pulse para TV ao vivo, mini-guia, zapping e catch-up.
- BURO Lens para busca universal e BURO Catalog Brain para organização e deduplicação.
- BURO Temporal Intelligence para separar data de lançamento, data de entrada na lista, anos e décadas.
- BURO Resilience Engine para classificar erros, controlar retries e recuperar playback com segurança.
- Connection Budget Manager para impedir que trailers, probes, prefetch ou multiview ultrapassem o limite da fonte.
- Importação transacional para preservar a biblioteca quando playlist, Xtream ou EPG falharem.
- Failure Test Lab para reproduzir erros de rede, HTTP, HLS, codec, playlist, EPG e banco.
- BURO Quality Autopilot e Stream Health Engine para reprodução resiliente.
- Perfis, controle parental, modo infantil, áudio, legendas e acessibilidade.
- Continuidade entre aparelhos e controle pelo celular em fases posteriores.
- Importação de M3U/M3U8, Xtream-compatible APIs e XMLTV/EPG.
- Teste gratuito de 7 dias e licença vitalícia de € 9,99 por dispositivo.
- Credenciais protegidas e processamento local-first.

## Continuação atual

O Codex deve preservar o trabalho existente e implementar progressivamente:

1. a fundação do **BURO Cinematic System**;
2. o **BURO Temporal Intelligence**;
3. o **BURO Resilience Engine**;
4. modelo normalizado de falhas e mensagens acionáveis;
5. `RetryBudget`, `SourceCircuitBreaker` e `ConnectionBudgetManager`;
6. importação transacional que nunca substitui um snapshot válido por resposta vazia ou corrompida;
7. Failure Test Lab com cenários reproduzíveis;
8. fileiras separadas para `Lançamentos {ano atual}`, `Adicionados recentemente` e `Clássicos que chegaram agora`;
9. filtros por ano e década;
10. Story Page mostrando separadamente a data real de lançamento e a data em que o item entrou na biblioteca.
