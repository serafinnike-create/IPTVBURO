# ADR-0001 — Arquitetura multiplataforma do IPTV BURO

**Status:** Aceito  
**Data:** 31 de julho de 2026

## Contexto

Android TV, Tizen, webOS, Titan OS, tvOS, iOS, macOS e Windows usam runtimes, APIs de mídia, lojas e ciclos de vida diferentes. Um único executável não oferece a qualidade necessária.

## Decisão

O IPTV BURO será um produto único, composto por:

- especificação funcional única;
- domínio, schemas, fixtures e testes compartilhados;
- design tokens compartilhados;
- aplicações específicas por família de sistema;
- player e integrações nativas por plataforma;
- manifesto versionado do estado de cada aplicação.

## Estrutura prevista

```text
apps/android-tv
apps/android-mobile
apps/apple
apps/samsung-tizen
apps/lg-webos
apps/titan-tv
apps/windows
apps/web-portal

packages/contracts
packages/design-tokens
packages/fixtures
packages/localization
packages/conformance-tests
packages/release-manifest
```

## Regras

1. Catálogo, datas, falhas, licença e sincronização obedecem a contratos comuns.
2. A UI adapta foco, toque, mouse, teclado e áreas seguras ao sistema.
3. Player, armazenamento seguro, ciclo de vida e loja permanecem nativos.
4. Recursos indisponíveis não aparecem na interface.
5. Toda diferença funcional deve ser documentada como capability ou limitação.
6. Android TV é a referência inicial, não o limite final.
7. Uma plataforma só é considerada pronta após build, testes, hardware e requisitos de distribuição.

## Consequências

A decisão melhora compatibilidade, integração nativa e consistência, mas exige múltiplos pipelines, adapters e testes em dispositivos reais. Esses custos são aceitos como necessários para a qualidade do produto.
