# Revisão técnica — milestone IPTV BURO 0.2

**Data:** 2 de agosto de 2026  
**Base analisada:** `agent/iptv-buro-0.2-preview@4704147`  
**Objetivo:** orientar o Codex antes de gerar nova prévia Android/Windows.

## 1. Conclusão executiva

A milestone 0.2 representa avanço real. Existe código para Xtream, catálogo grande, Android adaptativo, perfis, favoritos, idiomas, EPG curto, detalhes de filmes/séries, segurança de credenciais, Home editorial Windows e preview desktop com player interno limitado.

O CI `Android CI` run 25 concluiu com sucesso. Ainda assim, a milestone não deve ser promovida como estável. O incremento concentrou responsabilidades em arquivos muito grandes, não possui suíte encontrada de migration Room com `MigrationTestHelper`, mantém riscos de segredos M3U em texto simples e o player Windows não alcança paridade de codecs, áudio, legendas e HDR.

Prioridade:

1. estabilizar arquitetura e banco;
2. concluir gates de hardware e segurança;
3. implementar continuidade do GDD 7;
4. consolidar contratos compartilhados;
5. somente depois gerar `0.2.0-alpha`.

## 2. O que o Codex implementou corretamente

### Android/Android TV

- Xtream para live, filmes, séries, episódios e EPG curto;
- importação atômica em lotes e paginação keyset;
- Room schema 4;
- Home real sem carregar catálogo completo na UI;
- detalhes de filmes, séries, episódios e pessoas;
- quatro idiomas;
- até cinco perfis, inclusive Kids;
- favoritos por perfil;
- volume, brilho, velocidade, seek, bloqueio e PiP mobile;
- erro específico para mídia incompatível;
- credenciais Xtream cifradas por Keystore/AES-GCM;
- URL Xtream resolvida somente em memória;
- janela segura onde aplicada;
- identidade de instalação e fundação de entitlement.

### Windows preview

- Compose Desktop com MSI;
- login e catálogo Xtream;
- paginação e índice colunar de sessão;
- galeria e Home editorial rotativa;
- fichas em janela própria;
- sessão protegida por DPAPI;
- player JavaFX para subconjunto MP4/HLS H.264/AAC;
- perfis, idiomas, favoritos e política Kids;
- encerramento de sessão remove blob protegido.

### Escala e segurança

- parsers por fluxo;
- teste sintético com 500 mil itens;
- teto defensivo de 1 milhão Xtream;
- redaction e varredura de segredos;
- 120 testes locais reportados;
- lint Android sem erros;
- APK/MSI gerados;
- downloader genérico removido;
- Offline Vault corretamente oculto enquanto não autorizado/implementado.

## 3. Lacunas P0

### 3.1 Continuar assistindo

Não existe progresso persistente por perfil, fileira `Continuar assistindo`, barra nos cards, retomada após reinício ou conclusão de filme/episódio. Implementar o GDD 7 integralmente.

### 3.2 Migration Room

Existem migrations 1→2, 2→3 e 3→4 e schemas exportados, mas não foi encontrada suíte com `MigrationTestHelper`. O próximo schema deve ser criado somente com testes 1→2→3→4→5, 2→3→4→5, 3→4→5 e 4→5.

### 3.3 Proteção de M3U

URLs, `Referer` e `Origin` de M3U ainda podem permanecer no Room em texto simples. Separar locator de segredo, cifrar campos sensíveis e migrar sem perda.

### 3.4 Hardware

Executar smoke tests em Android TV/Google TV real, Android touch e Windows. Validar D-pad, rotação, PiP, background, migration, playback, retomada e codecs.

### 3.5 Preview

Não criar tag até:

- CI completo e verde;
- migration suite;
- segredo scan;
- catálogo grande;
- validação física;
- manifest/documentação consistentes;
- hashes produzidos pelo CI final.

## 4. Dívida arquitetural P1

### 4.1 `MainViewModel`

Concentra navegação, importação, perfis, favoritos, Home, detalhes, player, EPG e identidade. Extrair:

- `CatalogCoordinator`;
- `HomeCoordinator`;
- `ProfileCoordinator`;
- `PlaybackCoordinator`;
- `XtreamImportCoordinator`;
- `DetailsCoordinator`.

Substituir acesso direto a contexto/Toast/API estática por interfaces injetadas e `UiEffect`.

### 4.2 UI gigante

Dividir `AppShellScreen`, `DesktopApp`, `XtreamWorkspace`, Home e detalhes por feature. Composables devem ser predominantemente stateless e testáveis.

### 4.3 Repository central

Dividir `RoomCatalogRepository` em importação, consultas, detalhes, locators, EPG e manutenção/transações.

### 4.4 Paridade

Criar conformance tests compartilhados para catálogo, perfis, favoritos, progresso, Kids, erros e classificação temporal. Evitar regras duplicadas em `MainViewModel` e `DesktopAppState`.

## 5. Player Windows

JavaFX Media cobre matriz limitada. Definir `WindowsPlayerAdapter` por ADR e validar HLS, MPEG-TS quando aplicável, H.264, HEVC, AAC, AC3/EAC3, legendas e faixas de áudio. Playback externo pode ser fallback explícito, nunca falsa paridade.

## 6. Riscos de crescimento

Arquivos com responsabilidades excessivas incluem:

- `MainViewModel.kt`;
- `AppShellScreen.kt`;
- `DesktopApp.kt`;
- `XtreamWorkspace.kt`;
- `DesktopAppState.kt`;
- `RoomCatalogRepository.kt`.

O crescimento monolítico aumenta regressão, conflito, dificuldade de teste e compreensão por agentes. Refatorar antes de novos módulos grandes.

## 7. Workflow de release

O workflow gera APK, MSI e checksums, o que é positivo. Antes da tag, adicionar/confirmar:

- migration tests;
- testes de domínio compartilhado;
- credential scan;
- validação do release manifest;
- teste controlado de catálogo grande;
- relatório de dependências/licenças;
- APK debug claramente rotulado como preview;
- política de assinatura/atualização antes de beta pública.

## 8. Sequência recomendada

### Incremento A — estabilização

1. confirmar CI;
2. migration tests;
3. refactor de ViewModel/state/shell/repository sem mudar comportamento;
4. conformance tests;
5. proteção M3U;
6. manifest e auditoria honestos.

### Incremento B — GDD 7

1. domínio `PlaybackProgress`;
2. migration 4→5;
3. repository/use cases;
4. checkpoints Android;
5. fileira `Continuar assistindo`;
6. barras nos cards;
7. retomada/conclusão;
8. adapter Windows;
9. force-stop/reinício.

### Incremento C — preview

1. builds limpos;
2. testes/lint;
3. migrations;
4. escala;
5. segredo scan;
6. smoke Android TV/touch/Windows;
7. hashes CI;
8. atualizar status/manifest;
9. tag somente após gates;
10. publicar como pre-release.

## 9. Definição de pronto

- CI verde;
- migrations testadas sem perda da 0.1;
- Xtream/M3U sem segredos expostos;
- continuidade por perfil funcionando;
- Android TV e touch validados;
- MSI abre e limitações do player são explícitas;
- nenhum recurso planejado apresentado como concluído;
- APK/MSI/checksums do workflow;
- documentação corresponde ao artefato.

## 10. Decisão

**Não gerar nova preview imediatamente.** Primeiro estabilizar e implementar o GDD 7. A branch tem valor real, mas continuidade ausente, migrations não comprovadas e dívida arquitetural tornam prematura uma nova publicação neste estado.
