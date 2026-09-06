# Verificação Profunda com Execução Real — IPTV BURO

**Data:** 2026-09-06
**Branch:** `codex/windows-clean-release`
**Escopo:** suítes completas de teste (incl. UI Compose), app desktop aberto de verdade, emulador Samsung Tizen real, investigação de falha nova.
**Complementa:** `AUDITORIA_GERAL_2026-09-05.md` e `CORRECOES_AUDITORIA_2026-09-06.md`.

---

## Resumo executivo

| Verificação | Resultado |
|---|---|
| Desktop — suíte COMPLETA `:apps:desktop:test` (incl. UI Compose) | **1.156 testes, 0 falhas, 0 erros, 0 skipped** (189 classes) |
| Desktop — app aberto de verdade | Home renderiza completa e viva, hero rotacionando, log sem exceções |
| Tizen — emulador real `T-samsung-10.0-x86_64` | Build + assinatura + install + launch OK, **PID estável** (sem crash) |
| Tizen — playback smoke (AVPlay real) | **`AVPLAY_SMOKE_PASS`**: play, pause/resume, seek ±, FF/RW, 2 faixas de áudio, 8 de legenda |
| Tizen — suíte `npm test` completa (105 arquivos) | **2.215 verificações ok, 0 falhas** |
| Android — emulador | **Não executado**: nenhum AVD configurado na máquina (criar exige baixar ~1 GB de system image) |

**2 bugs reais encontrados nesta rodada, ambos corrigidos** (detalhes abaixo).

---

## 1. Desktop — suíte completa + app real

### Testes
`./gradlew :apps:desktop:test` → `BUILD SUCCESSFUL in 1m 3s`: **1.156/1.156 verde**, incluindo UI Compose (a máquina tem display real; nenhum fallback de renderização foi necessário).

### Bug real nº 1 — encontrado e corrigido
- **Falha:** `MultiviewReachableTest > dropped tiles are logged` (1 falha na primeira rodada).
- **Causa raiz:** a correção de hoje cedo converteu `println("multiview: channel no longer in catalogue")` para `DebugLog.d` (desligado por padrão). O teste é pino de regressão de um modo de falha real reportado por usuário ("apertei o botão e nada aconteceu" — overlay abre vazio e a causa fica invisível). Com a conversão, a causa voltava a ficar invisível.
- **Correção mínima:** `DesktopAppState.kt:4479` — revertida apenas essa linha para `println` incondicional (restaura também o guarda de segurança do teste, que proíbe interpolar URL/credenciais nesse log). Suíte re-rodada: 1.156/1.156 verde.

### App aberto (`:apps:desktop:run`)
- Boot limpo: `BURO home: movies=18 series=18 live=14 hero=true`; **zero exceções** no log da sessão.
- Primeira tela renderiza **completa sem interação**: sidebar BIBLIOTECA, cabeçalho "8 fontes · 72373 itens · v4.0.0", hero "SELEÇÃO DIÁRIA" **rotacionando entre capturas** (prova de UI viva/recomposição), linha "Continuar assistindo" com posters carregados. Sem crash dialog, sem tela em branco, sem layout quebrado.
- **Observação de ambiente (não é bug do app):** o Firewall do Windows exibiu prompt sobre "OpenJDK Platform binary (JetBrains)" — listener loopback do JCEF/TrailerHostServer do ambiente de dev. O app renderizou normalmente atrás do diálogo. Em produção (MSI empacotado), o binário é o launcher próprio, não o JBR.
- **Encerramento:** apenas o PID do app foi morto; `jps`/`tasklist` confirmam que só restaram os daemons legítimos do Gradle. Nenhum processo residual.

### Evidências
- Screenshots: `.codex-tmp/desktop-app-screenshot-{1..7}*.png`
- Logs: `app-run.log`, `.codex-tmp/desktop-test.log`, `.codex-tmp/desktop-test-2.log`

## 2. Samsung Tizen — emulador real

- VM `T-samsung-10.0-x86_64` bootada; app `IPTVBUROxx.IPTVBURO` compilado, **assinado** (profile `burocertificate-emulator`), instalado e lançado: **pid 4221 estável** após verificação de 5 s (sem crash/restart).
- **Playback smoke com AVPlay real** (`run-samsung-playback-smoke.ps1`): `PREPARING → BUFFERING → PLAYING` (READY em 1.014 ms), posição avançando, **PAUSED → RESUMED** via tecla Play/Pause, **SEEK +30 s / −10 s** pelas setas, **FAST_FORWARD/REWIND** pelas teclas dedicadas, **2 faixas de áudio e 8 de legenda enumeradas e selecionadas**. Resultado: `AVPLAY_SMOKE_PASS` (position=57336). Pacote de diagnóstico desinstalado ao final pelo próprio script.
- Runner completo inicialmente falhou **antes de tocar no emulador** por causa do bug nº 2 abaixo; após a correção, a suíte inteira passa.
- Emulador **desligado** ao final (QEMU quit; `sdb devices` vazio; nenhum processo qemu/emulator ativo).

### Bug real nº 2 — `seasonal-app.test.js` intermitente (pré-existente)
- **Sintoma:** "a Home continua limitada e não materializa os 32 títulos no DOM (32/32)" — falhou hoje, passou ontem.
- **Causa raiz provada por instrumentação:** o teste criava 32 itens em laço sem `addedAt`; `createItem` atribui `Date.now()` por item ([domain.js:218]). Quando o laço cruza uma fronteira de milissegundo, a fileira `recent` (ordena por `addedAt` desc) escolhe um grupo de 12 diferente das fileiras ordenadas por `sortOrder` ([app.js:2987-2995, 3038-3050]); as sobras alimentam a fileira `movies` e a Home materializa até 32/32 — **corrida de relógio na massa de dados**, não regressão do app nem dependência de calendário.
- **Correção (só no teste):** um único `addedAt = Date.now()` capturado antes do laço e passado aos 32 itens → ordenação determinística, resultado estável em **25/32**, preservando a força da verificação. `app.js` intacto.
- **Reprova:** isolado 3× seguidas (25/32 nas três) + suíte completa `npm test` verde (2.215 ok, 0 falhas, 4 min 20 s).

### Evidências
- `.codex-tmp/tizen-emulator-run.log` (falha inicial), `.codex-tmp/tizen-emulator-run-skiptests.log` (install/launch OK), `.codex-tmp/tizen-playback-smoke.log` (AVPLAY_SMOKE_PASS), `.codex-tmp/tizen-emulator-screenshot.png`, `.codex-tmp/npm-test-after-seasonal-fix.log`

### Emulador vs. TV física (conforme `docs/VALIDACAO_TV_SAMSUNG.md`)
Validado hoje em emulador: boot, install, D-pad, playback HLS via AVPlay, pause/seek/teclas de mídia, troca de faixas, suíte completa verde. **Pendente de TV física:** HDR, desempenho real de decoders/OpenGL, persistência TMDb/OMDb no KeyManager, deep link com fonte ativa no IndexedDB.

## 3. Android — não executado (bloqueio de ambiente)

Nenhum AVD configurado (`emulator -list-avds` vazio). Criar um exige baixar system image (~1 GB) — não autorizado implicitamente; fica como próximo passo opcional. Os **310 testes unitários** do módulo (`testDebugUnitTest`) já estão verdes e a compilação release/debug também.

## 4. Estado final consolidado do working tree

| Suíte | Resultado |
|---|---|
| Pacotes compartilhados | 1.370 testes, 0 falhas |
| Desktop (completa, incl. UI) | 1.156 testes, 0 falhas |
| Android TV (unit) | 310 testes, 0 falhas |
| Tizen (completa) | 2.215 verificações, 0 falhas |
| **Total** | **~5.051 verificações automatizadas, 0 falhas** |

- Nada foi commitado; tudo aguarda revisão/commit do dono.
- Mudanças desta rodada: `DesktopAppState.kt` (1 linha, revertendo log multiview), `seasonal-app.test.js` (addedAt determinístico), `contracts/platform-capabilities.schema.json` (seção `audio`).

## Riscos restantes

- A fragilidade "laço sem `addedAt` explícito" pode existir em outros testes Tizen (não auditados um a um).
- O prompt de Firewall do Windows no dev é recorrente; considerar regra de firewall permanente para o JBR ou aceitar o prompt por sessão.
- Validação em TV Samsung física continua pendente (itens listados acima).
- Testes instrumentados Android seguem sem cobertura de emulador nesta máquina.
