# Prompt para o Codex — Continuação com GDD 5.0

Continue o projeto atual do IPTV BURO sem reiniciar, apagar ou substituir trabalho útil.

## Leitura obrigatória

Leia, nesta ordem:

1. `docs/GDD_IPTV_BURO.md`;
2. GDDs 1.0 a 4.0 e seus capítulos;
3. `docs/GDD_5_UNIVERSAL_MULTIPLATFORM_DELIVERY.md`;
4. `docs/adr/ADR-0001-MULTIPLATFORM-DELIVERY-ARCHITECTURE.md`;
5. `packages/contracts/PLAYER_ADAPTER_CONTRACT.md`;
6. `packages/contracts/platform-capabilities.schema.json`;
7. `packages/release-manifest/platforms.json`;
8. `docs/status/CURRENT_IMPLEMENTATION.md`;
9. código e testes existentes.

## Missão

Evoluir o IPTV BURO como produto multiplataforma sem prejudicar a versão Android TV que já compila e possui prévia publicada.

## Auditoria inicial obrigatória

Antes de criar novas telas ou aplicações:

- registre o estado atual da `main`;
- identifique contratos já existentes no domínio Android;
- localize acoplamentos indevidos a Activity, Compose ou Media3;
- identifique regras que precisam ser neutras;
- verifique segurança de URLs e credenciais;
- confirme testes, CI e versão publicada;
- atualize o release manifest apenas com evidências.

Crie ou atualize:

```text
docs/audits/MULTIPLATFORM_CODE_AUDIT.md
docs/audits/PLATFORM_CAPABILITY_AUDIT.md
docs/adr/ADR-SHARED-DOMAIN-BOUNDARIES.md
```

## Regras inegociáveis

1. Não recomeçar o projeto.
2. Não substituir a implementação Android funcional por scaffold.
3. Não prometer um binário universal.
4. Não usar WebView como solução automática para todos os sistemas.
5. Não copiar layouts proprietários de serviços concorrentes.
6. Não duplicar regras de catálogo, datas ou falhas.
7. Não marcar diretório vazio como aplicativo pronto.
8. Não marcar plataforma como pronta sem build, testes e hardware.
9. Não esconder diferenças de capabilities.
10. Não contornar DRM, TLS, autorização ou requisitos de loja.

## Ordem de execução

### Etapa 1 — contratos

- estabilizar `PlayerAdapter`;
- estabilizar `PlatformCapabilities`;
- definir schemas de catálogo, falhas, perfil e sincronização;
- criar fixtures e testes de conformidade;
- documentar versionamento.

### Etapa 2 — preservar Android TV

- manter `test`, `lint` e `assembleDebug` verdes;
- não regredir importação M3U/HLS;
- não alterar package ID ou migrações sem ADR;
- extrair apenas regras realmente portáveis.

### Etapa 3 — segundo aplicativo compilável

Criar uma vertical slice real para Android mobile ou Apple, contendo no mínimo:

- inicialização;
- design tokens;
- catálogo demonstrativo autorizado;
- navegação adequada ao dispositivo;
- PlayerAdapter nativo;
- testes;
- CI próprio.

### Etapa 4 — expansão

Prosseguir por ondas: Android mobile/Fire TV, Apple, Samsung, LG, Titan, Windows e portal.

## Definition of Done

Cada incremento deve entregar:

- código compilável;
- testes;
- documentação atualizada;
- release manifest verdadeiro;
- nenhuma credencial versionada;
- relatório de diferenças de capabilities;
- commit e PR com escopo claro.

Ao terminar cada etapa, informe exatamente o que foi implementado, o que foi apenas documentado e o que continua pendente.
