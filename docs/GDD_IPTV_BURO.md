# GDD / PRD TÉCNICO — IPTV BURO

**Versão:** 1.0 + extensões 2.0 e 3.0  
**Data:** 30 de julho de 2026  
**Status:** documentação obrigatória para desenvolvimento com Codex

Este GDD foi dividido em camadas para facilitar leitura, manutenção e execução pelo Codex. **Todos os documentos fazem parte da especificação obrigatória.**

## GDD 1.0 — Fundação técnica e comercial

1. [Visão do produto, limites, público, benchmark e diferenciais](gdd/01-product-vision.md)
2. [Plataformas, arquitetura, monorepo e experiência visual](gdd/02-platform-architecture.md)
3. [Dados, reprodução, Stream Health Engine, segurança e backend](gdd/03-data-playback-security.md)
4. [Fluxos, configurações, escopo e testes](gdd/04-flows-settings-testing.md)
5. [Roadmap, estimativas, backlog e regras para o Codex](gdd/05-roadmap-backlog-codex.md)
6. [Riscos, decisão final e referências](gdd/06-risks-decisions-references.md)

## GDD 2.0 — Experiência revolucionária

O GDD 2.0 transforma a fundação em uma plataforma premium com identidade própria, design cinematográfico, descoberta inteligente, TV ao vivo reinventada e continuidade entre dispositivos.

- [Índice e regras de precedência do GDD 2.0](GDD_2_REVOLUTIONARY_EXPERIENCE.md)
- [Prompt para o Codex continuar sem reiniciar o projeto](PROMPT_CODEX_CONTINUE_GDD2.md)

## GDD 3.0 — Inteligência temporal do catálogo

O GDD 3.0 separa a data em que um conteúdo entrou na playlist da data real em que a obra foi lançada. Ele impede que filmes antigos adicionados recentemente apareçam como lançamentos atuais e cria filtros confiáveis por ano, década, data de adição e confiança dos metadados.

- [GDD 3.0 — Catalog Intelligence & Release Integrity](GDD_3_CATALOG_RELEASE_INTELLIGENCE.md)
- [Prompt para o Codex implementar o BURO Temporal Intelligence](PROMPT_CODEX_CONTINUE_GDD3.md)

## Instrução ao Codex

Antes de implementar qualquer funcionalidade, leia:

1. este índice;
2. os seis capítulos do GDD 1.0;
3. o índice e todos os capítulos do GDD 2.0;
4. o GDD 3.0 completo;
5. o prompt específico da etapa em execução.

Em caso de conflito:

1. priorize legalidade, segurança e privacidade;
2. preserve a arquitetura-base, migrações e contratos úteis existentes;
3. use o GDD 2.0 como fonte de verdade para design, UX, navegação, descoberta, TV ao vivo e experiência premium;
4. use o GDD 3.0 como fonte de verdade para datas, anos, lançamentos, recém-adicionados, séries, temporadas e organização temporal;
5. não reinicie nem apague o trabalho existente sem auditoria e ADR;
6. nunca trate data de importação como data de lançamento.
