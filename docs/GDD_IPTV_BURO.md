# GDD / PRD TÉCNICO — IPTV BURO

**Versão:** 1.0 + extensões 2.0, 3.0, 4.0, 5.0, 6.0 e 7.0  
**Data:** 2 de agosto de 2026  
**Status:** documentação obrigatória para desenvolvimento com Codex

Todos os documentos abaixo fazem parte da especificação oficial. O código existente deve ser preservado e evoluído incrementalmente.

## GDD 1.0 — Fundação técnica e comercial

1. [Visão do produto, limites, público, benchmark e diferenciais](gdd/01-product-vision.md)
2. [Plataformas, arquitetura, monorepo e experiência visual](gdd/02-platform-architecture.md)
3. [Dados, reprodução, Stream Health Engine, segurança e backend](gdd/03-data-playback-security.md)
4. [Fluxos, configurações, escopo e testes](gdd/04-flows-settings-testing.md)
5. [Roadmap, estimativas, backlog e regras para o Codex](gdd/05-roadmap-backlog-codex.md)
6. [Riscos, decisão final e referências](gdd/06-risks-decisions-references.md)

## GDD 2.0 — Experiência revolucionária

- [GDD 2.0 — Revolutionary Entertainment Experience](GDD_2_REVOLUTIONARY_EXPERIENCE.md)
- [Prompt de continuação](PROMPT_CODEX_CONTINUE_GDD2.md)

Fonte de verdade para design, UX, navegação, descoberta, TV ao vivo e experiência premium.

## GDD 3.0 — Inteligência temporal

- [GDD 3.0 — Catalog Intelligence & Release Integrity](GDD_3_CATALOG_RELEASE_INTELLIGENCE.md)
- [Prompt de continuação](PROMPT_CODEX_CONTINUE_GDD3.md)

Fonte de verdade para ano real, lançamentos, recém-adicionados, séries, temporadas e organização temporal.

## GDD 4.0 — Confiabilidade

- [GDD 4.0 — Reliability, Failure Recovery & Playback Integrity](GDD_4_RELIABILITY_FAILURE_RECOVERY.md)
- [Taxonomia de falhas](gdd-v4/01-failure-taxonomy.md)
- [Motor de recuperação](gdd-v4/02-recovery-engine.md)
- [Playlist e EPG](gdd-v4/03-playlist-epg-data-integrity.md)
- [Compatibilidade de playback](gdd-v4/04-playback-device-compatibility.md)
- [Observabilidade e testes](gdd-v4/05-observability-test-lab.md)
- [Roadmap e aceitação](gdd-v4/06-roadmap-acceptance.md)
- [Prompt de continuação](PROMPT_CODEX_CONTINUE_GDD4.md)

Fonte de verdade para falhas, retries, recuperação, conexões, importação transacional e diagnóstico.

## GDD 5.0 — Entrega universal multiplataforma

- [GDD 5.0 — Universal Multiplatform Delivery](GDD_5_UNIVERSAL_MULTIPLATFORM_DELIVERY.md)
- [ADR da arquitetura multiplataforma](adr/ADR-0001-MULTIPLATFORM-DELIVERY-ARCHITECTURE.md)
- [Prompt de continuação](PROMPT_CODEX_CONTINUE_GDD5.md)

Define o IPTV BURO como um produto único com aplicações próprias para Android TV, Fire TV, Android mobile, Apple, Samsung, LG, Titan OS, Windows e portal web.

## GDD 6.0 — BURO Offline Vault

- [GDD 6.0 — Offline Vault](GDD_6_BURO_OFFLINE_VAULT.md)
- [Prompt de continuação](PROMPT_CODEX_CONTINUE_GDD6.md)

Define download e playback offline de filmes, episódios e temporadas elegíveis no Android mobile/tablet e iPhone/iPad. A função não deve ser exibida nas TVs durante o P0.

## GDD 7.0 — Playback Continuity & Watch Progress

- [GDD 7.0 — Continuar assistindo e progresso](GDD_7_PLAYBACK_CONTINUITY_AND_WATCH_PROGRESS.md)
- [Revisão técnica da milestone 0.2](reviews/REVIEW_0_2_ARCHITECTURE_AND_NEXT_GATES.md)
- [Prompt de estabilização e implementação](PROMPT_CODEX_STABILIZE_0_2_AND_IMPLEMENT_GDD7.md)

Define progresso persistente por perfil, barra nos cards, fileira `Continuar assistindo`, retomada de filmes/episódios, conclusão, migrations e paridade entre plataformas compatíveis.

## Ordem obrigatória para o Codex

1. Ler este índice.
2. Ler os GDDs 1.0 a 4.0.
3. Ler o GDD 5.0 e o ADR multiplataforma.
4. Ler o GDD 6.0 quando trabalhar em mobile/offline.
5. Ler o GDD 7.0 antes de modificar player, progresso, Home ou Minha BURO.
6. Ler `docs/status/CURRENT_IMPLEMENTATION.md`.
7. Ler a revisão técnica da milestone em andamento.
8. Auditar o código e os testes existentes.
9. Executar o prompt específico da etapa.

## Regras de precedência

1. legalidade, segurança e privacidade;
2. preservar código funcional, dados e migrações;
3. GDD 2.0 para design e experiência;
4. GDD 3.0 para datas e lançamentos;
5. GDD 4.0 para confiabilidade;
6. GDD 5.0 para plataformas e paridade;
7. GDD 6.0 para offline mobile;
8. GDD 7.0 para progresso, retomada e continuidade por perfil;
9. nenhuma plataforma pode ser chamada de pronta sem build, testes e hardware;
10. Android TV é a referência inicial, não o limite do produto;
11. nenhuma função offline pode exportar arquivos ou ignorar restrições da fonte/plataforma;
12. nenhuma nova preview pode ser publicada com migration não testada ou CI incompleto.
