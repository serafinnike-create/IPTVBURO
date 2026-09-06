# AGENTS.md — IPTV BURO

Este arquivo é o ponto de entrada obrigatório para Codex, Codex e outros agentes de implementação.

## Missão do produto

O IPTV BURO é um player e organizador multiplataforma para fontes de mídia autorizadas pelo usuário. O produto combina TV ao vivo, filmes, séries e, pelo GDD 8, uma futura experiência de música, rádio, podcasts e audiobooks.

O projeto não fornece conteúdo, listas, credenciais ou mecanismos para contornar DRM, autenticação, paywall ou restrições de serviço.

## Estado atual

- Android/Android TV: aplicação Kotlin + Compose + Media3 em preview.
- Windows: Compose Desktop em preview.
- Fontes atuais: M3U/M3U8, Xtream-compatible e fundação Stalker/Ministra.
- Domínio compartilhado: `packages/domain-model`.
- Parser streaming: `packages/playlist-parser`.
- Clientes: `packages/xtream-client` e `packages/stalker-client`.
- Perfis, Kids, favoritos, catálogo, detalhes e continuidade já possuem implementação parcial/real conforme o status documentado.
- GDD 8: a Fase 0 (contratos universais) já foi executada. A experiência de áudio está em preview somente no desktop — `packages/platform-capabilities/windows-preview.json` declara `audio.supported: true` e a `MusicWorkspace` está ligada em `DesktopApp.kt` — e não está liberada no Android nem no Samsung Tizen (os respectivos arquivos de capabilities não declaram `audio`).

Não confie somente neste resumo. Leia `docs/status/CURRENT_IMPLEMENTATION.md` e audite o código.

## Ordem de leitura

1. `docs/GDD_IPTV_BURO.md`
2. `docs/status/CURRENT_IMPLEMENTATION.md`
3. GDD relacionado à tarefa
4. ADRs relacionados
5. auditorias e reviews relacionados
6. código e testes existentes

Para Media SuperHub/áudio:

1. `docs/GDD_8_MEDIA_SUPERHUB_AUDIO.md`
2. `docs/PROMPT_CLAUDE_IMPLEMENT_GDD8.md`
3. `docs/security/credential-handling.md`
4. `docs/ux/design-system.md`

## Regras de implementação

- Preserve todo trabalho útil existente.
- Não reinicie a arquitetura, não troque a stack e não apague verticais funcionais.
- Faça mudanças pequenas, testáveis e reversíveis.
- Antes de alterar schema Room, crie migration e teste de migration.
- Antes de alterar identidade persistida, adicione testes de regressão.
- Não coloque toda a lógica em ViewModels, composables ou classes de shell.
- UI não acessa DAO ou cliente de rede diretamente.
- Recursos visíveis devem ser guiados por capabilities reais.
- Planejado não significa implementado.
- Não crie release/tag sem instrução explícita.
- Não inclua segredo, playlist privada, URL assinada ou conteúdo protegido no repositório.
- Redija URLs, headers, tokens, cookies, username e password em logs e `toString()`.
- Resolva URLs autenticadas o mais tarde possível, somente em memória.
- Use fixtures sintéticas ou públicas estáveis.
- Mantenha PT-BR, EN, DE e IT.
- Preserve navegação por D-pad, touch, teclado e mouse conforme a plataforma.
- Execute testes e builds relevantes antes de declarar conclusão.

## Branch de trabalho atual

A branch de trabalho real é:

```text
codex/windows-clean-release
```

Antes de trabalhar, confirme a branch real e o estado do repositório (`git branch --show-current`). Não suponha que este texto esteja atualizado se a branch mudou.

## Próxima tarefa oficial do GDD 8

A **Fase 0 — contratos universais e proteção contra regressão** já foi executada, conforme:

- `docs/audits/GDD8_PHASE0_ARCHITECTURE_AUDIT.md`;
- a seção "Fundação Media SuperHub — Fase 0" em `docs/status/CURRENT_IMPLEMENTATION.md`.

A próxima fase real é a **Fase 1 — rádio vertical**, conforme `docs/GDD_8_MEDIA_SUPERHUB_AUDIO.md` (seção 27, "Fases de implementação"): adicionar uma estação por URL/M3U autorizado, persistir estação, listar favoritas/recentes, tocar em background no Android, ICY metadata quando disponível, player compacto e tela completa, e Windows com capability honesta. O escopo e o processo de execução seguem `docs/PROMPT_CLAUDE_IMPLEMENT_GDD8.md`.

## Relatório obrigatório

Ao terminar qualquer etapa, informe:

- o que foi realmente implementado;
- arquivos alterados;
- testes e builds executados;
- resultados exatos;
- limitações e falhas;
- riscos restantes;
- próximo passo recomendado.

Nunca afirme que uma plataforma, player ou função está pronta sem evidência correspondente.