# IPTV BURO

IPTV BURO é um player OTT/IPTV premium, local-first e multiplataforma, projetado para transformar fontes de mídia configuradas legalmente pelo usuário em uma experiência moderna para TV, celular e computador.

> **Aviso legal:** este projeto é somente um reprodutor de mídia. Ele não fornece canais, filmes, séries, listas, assinaturas ou qualquer conteúdo protegido. O usuário deve possuir autorização legal para acessar as fontes adicionadas.

## Estado do projeto

Planejamento e fundação técnica. O primeiro alvo de desenvolvimento é Android TV/Google TV, com arquitetura preparada para Android, Fire TV, Windows, macOS, iOS/tvOS, Samsung Tizen e LG webOS.

O produto está sendo desenvolvido em três camadas complementares:

- **GDD 1.0:** fundação técnica, reprodução, fontes, segurança, licenciamento e plataformas;
- **GDD 2.0:** identidade cinematográfica, experiência premium, descoberta inteligente, TV ao vivo reinventada e continuidade entre dispositivos;
- **GDD 3.0:** inteligência temporal para separar lançamentos reais de conteúdos antigos adicionados recentemente.

## Documentação

- [Índice geral dos GDDs](docs/GDD_IPTV_BURO.md)
- [GDD 2.0 — Revolutionary Entertainment Experience](docs/GDD_2_REVOLUTIONARY_EXPERIENCE.md)
- [GDD 3.0 — Catalog Intelligence & Release Integrity](docs/GDD_3_CATALOG_RELEASE_INTELLIGENCE.md)
- [Prompt mestre inicial para o Codex](docs/PROMPT_MESTRE_CODEX_IPTV_BURO.md)
- [Prompt para o Codex continuar com o GDD 2.0](docs/PROMPT_CODEX_CONTINUE_GDD2.md)
- [Prompt para o Codex implementar o GDD 3.0](docs/PROMPT_CODEX_CONTINUE_GDD3.md)

## Produto

- BURO Cinematic System com identidade visual própria.
- Living Home contextual, detalhes cinematográficos e Minha BURO.
- BURO Pulse para TV ao vivo, mini-guia, zapping e catch-up.
- BURO Lens para busca universal e BURO Catalog Brain para organização e deduplicação.
- BURO Temporal Intelligence para separar data de lançamento, data de entrada na lista, anos e décadas.
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
3. fileiras separadas para `Lançamentos {ano atual}`, `Adicionados recentemente` e `Clássicos que chegaram agora`;
4. filtros por ano e década;
5. Story Page mostrando separadamente a data real de lançamento e a data em que o item entrou na biblioteca.
