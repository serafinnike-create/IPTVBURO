# Correções da Auditoria Geral — Relatório de Implementação

**Data:** 2026-09-06
**Branch:** `codex/windows-clean-release`
**Base:** achados de `docs/audits/AUDITORIA_GERAL_2026-09-05.md`
**Estado:** todas as mudanças no working tree, **nada commitado** (aguardando revisão/commit do dono).

---

## Resumo executivo

**38 dos ~40 achados da auditoria foram corrigidos e verificados com testes.** Verificação final cruzada (após todas as frentes):

| Suíte | Resultado |
|---|---|
| Pacotes compartilhados (7 módulos) | **1.370 testes, 0 falhas** (27 novos testes adicionados) |
| Android TV — `compileDebugKotlin` + `testDebugUnitTest` | **310 testes, 0 falhas** (55 suítes) |
| Desktop — `compileKotlin`/`compileTestKotlin` + testes das áreas tocadas | **BUILD SUCCESSFUL** (18+ testes verdes, incl. 2 testes novos) |
| Samsung Tizen — `npm test` (105 arquivos, antes 81) | **2.215 verificações ok, 0 falhas** |
| Workflows YAML + `.gitleaks.toml` | sintaxe validada |

Dois achados ficaram **intencionalmente fora** (decisão do dono necessária) e um conjunto de **deleções aguarda confirmação** — ver seções finais.

---

## O que foi implementado, por frente

### 1. Pacotes compartilhados (agente: packages)

| Achado | Correção | Arquivos |
|---|---|---|
| Gzip bomb no EPG (A2) | Teto de 512 MB de bytes **descomprimidos** via `DecompressedBytesLimitedInputStream` aplicado após o `GZIPInputStream`; nova `XmltvDecompressedLimitExceededException`; parâmetro injetável com default retrocompatível | `XmltvParser.kt`, `XmltvParserTest.kt` |
| WebDAV sem callTimeout/teto (A3) | `callTimeout(2 min)` com justificativa slow-loris; teto de 32 MB no corpo de `list()` (Content-Length + cap de streaming); **mudança de comportamento**: `list()` não engole mais erros como `emptyList()` — HTTP não-2xx → `WebDavHttpException`, rede → `IOException` (nenhum chamador fora do pacote existia) | `WebDavClient.kt`, `WebDavClientTest.kt` |
| Metadata sem teto/timeout (A4) | `callTimeout` (60s TMDb, 30s OMDb/ThePornDB) + teto de 32 MB via novo `BoundedResponseBody.kt`; `InterruptedException` restaura o flag em `TmdbClient` | `TmdbClient.kt`, `CriticScoresClient.kt`, `AdultArtworkClient.kt`, `MetadataResponseBoundsTest.kt` (novo) |
| Linha M3U materializada antes do check (M2) | Novo `BoundedLineReader` (buffer 8 KB, falha no caractere limite+1); teste prova que linha de 10 MB com limite 64 falha com < 2 MB lidos | `M3uParser.kt`, `M3uParserTest.kt` |
| URI opaca `http:foo` (M3) | Exige `uri.host != null` para http/https | `M3uParser.kt` |
| `epgUrls` sem validação (M4) | `url-tvg`/`x-tvg-url` filtrados para http/https com host; novo warning `UNSAFE_EPG_URL` (aditivo); mensagem não repete a URL | `M3uParser.kt`, `M3uModels.kt` |
| Stalker: http default, artwork, retry (M5) | (a) callback `onInsecurePortal` (1× por client, sem host/MAC/token); (b) `sanitizeArtworkUrl()` espelhando Xtream; (c) retry transitório 408/429/5xx com backoff e orçamento | `StalkerClient.kt`, `StalkerClientTest.kt` |
| `println` em biblioteca (B) | Removidos os 2 de `XtreamClient.kt` | `XtreamClient.kt` |
| Regex `COLLECTION` (B) | Lookahead `(?=[\s/>])` exige boundary de tag; `<d:collectionfoo/>` não casa mais | `WebDavListing.kt`, `WebDavListingTest.kt` |
| Capabilities sem validação (M6) | Novo `PlatformCapabilitiesContractTest.kt` (metadata-client, sem dependência nova): valida `windows-preview.json` e `android-adaptive.json` contra `required` + tipos do schema, recursivamente | `PlatformCapabilitiesContractTest.kt` (novo) |

### 2. Android TV (agente: android-tv)

| Achado | Correção |
|---|---|
| Strings PT hardcoded (A6) | **Todos** os literais substituídos por recursos nos 5 idiomas (EN/PT-BR/DE/ES/IT): `MovieDetailsScreen` (7), `SeriesDetailsScreen` (2, incl. plural `series_episode_count`), `AppShellScreen` (1), rails editoriais do `MainViewModel` (7), fallback de sinopse em `RealHomeCatalog` — inclui achados extras além da lista da auditoria ("Gênero", "País", "Lançamento", "N episódios") |
| Sem teste i18n (A6) | Novo `NoHardcodedTextTest.kt` no android-tv (2 testes): varre `src/main/kotlin`, detecta prosa PT, allowlist com teto (`PENDING_CEILING = 2`) que impede crescimento silencioso |
| Rodapé pré-locale só PT (B) | `LanguageSelectionScreen` agora exibe título e rodapé **nos 5 idiomas** (correto para tela pré-locale) |
| Rede no ViewModel (C3) | Novo `data/metadata/MetadataClientsProvider.kt` (`@Singleton @Inject`); `MainViewModel` não injeta mais `OkHttpClient` nem constrói `TmdbClient`/`CriticScoresClient` (7 construções removidas); teste de navegação atualizado (32 testes verdes) |
| `usesCleartextTraffic` redundante (B) | Removido após confirmar que o `network_security_config.xml` garante o comportamento (NSC prevalece; zero mudança de comportamento) |

### 3. Desktop (agente: desktop)

| Achado | Correção |
|---|---|
| `DemoStreamingDiscoveryProvider` em src/main (M12) | Movido para `src/test` (mesma package); removido da lista de pendências do `NoHardcodedTextTest` (teto baixado para 0) |
| `println` na composição (M8) | Removido de `DesktopApp.kt` (executava a cada recomposição) |
| Tokens sem teste de paridade (M6) | Novo `DesignTokensParityTest.kt`: paridade exata de cores de acento/status/motion/focus; **drift conhecido de 7 neutros pinado** (desktop e Android driftaram juntos do GDD na rebuild 0.2 — realinhar é decisão de produto, o teste impede que o drift cresça) |
| ~60 printlns (M8) | Novo `DebugLog.kt` (flag desligada por padrão, `-Diptvburo.debug=true` liga); 22 printlns de rotina convertidos; falhas/avisos mantidos como println (vão ao log de suporte) |
| **Bônus de segurança** | `SessionXtreamRepository.kt:819/829` logavam `failure.message` — OkHttp embute a URL completa (com credenciais Xtream) nas mensagens de IOException. `.message` removido das duas linhas |

### 4. Repo / CI / Docs / Tizen (agente: repo)

| Achado | Correção |
|---|---|
| Branch sem CI (A5) | `android-ci.yml`: push agora em `main`, `agent/**`, **`codex/**`** |
| Bare `test` contraditório (C4) | `android-ci.yml` agora usa as mesmas tasks explícitas do `preview-release.yml` (`testDebugUnitTest`, `jvmTest`, `metadata-client:test`, `lintDebug`, `assembleDebug`) — UI tests do desktop fora do runner headless |
| Sem secret scanning (M11) | Novo `.github/workflows/secret-scan.yml` (gitleaks-action@v2) + `.gitleaks.toml` com allowlist conservadora só de fixtures sintéticas reais |
| 24 testes Tizen fora do harness (M1) | `package.json` agora roda **105 arquivos** `*.test.js`; 3 bugs de teste corrigidos no processo (2 testes que nunca encerravam o processo; 1 flake de GC no chromium-visual; 1 flake de virada de minuto no guide-clock via novo hook `stopGuideClock()` em `app.js`) |
| Scaffold Tizen C morto (M9) | `git rm -r apps/iptvburo` (10 arquivos, staged, reversível via git) |
| `.gitignore` (M10) | Adicionados `.codex-tmp/`, `.backup-original-site-assets/`, `.claude/` |
| AGENTS.md (A8) | Branch corrigida; Fase 0 marcada como executada, próxima = **Fase 1 — rádio vertical** (GDD 8 §27); estado do áudio corrigido para "preview somente no desktop" (verificado nos JSONs de capabilities) |
| CURRENT_IMPLEMENTATION.md (A8) | Tag/versões → `v4.0.0`; tabela de módulos completa (stalker, metadata, media-source-spi, webdav, baselineprofile + nota sobre Tizen/license-server) |
| Parity audit desatualizado (B) | Nota datada corrigindo a afirmação sobre licença desabilitada (há chave ECDSA real compilada) |

---

## Não implementado (decisão do dono necessária)

1. **Realinhamento das 7 cores neutras** com o GDD (`tokens.json` vs. tema desktop/Android) — muda a paleta shipped nas duas plataformas; o teste novo pinha o drift para não crescer.
2. ~~**Divergência `audio` no schema de capabilities**~~ — **RESOLVIDO em 2026-09-06**: `audio` adicionado a `contracts/platform-capabilities.schema.json` (objeto opcional, `supported` obrigatório, `additionalProperties: false`), consistente com o estado documentado (áudio em preview só no desktop). `PlatformCapabilitiesContractTest` verde após a mudança.
3. **Hero 3D do site** (`hero3d.js` + 655 KB de Three.js no deploy, código morto) — **não removido** porque há trabalho ativo não commitado nele (+100 linhas no diff). Decidir: remover do deploy ou finalizar a feature.
4. **Fatiamento do `DesktopAppState`** (8.582 linhas) — refatoração multi-dia; não cabe em mudança pequena/reversível. Recomendado como projeto separado por domínio.
5. **Criptografia M3U no Room** — lacuna conhecida pré-v1 estável, documentada em `credential-handling.md:47-48`; feature, não correção.
6. **E-mail pessoal em `config.xml:9`** — precisa do contato dedicado para substituir.
7. **`benchmark = "1.5.0-rc01"`** — bump requer verificação de compatibilidade; não alterado.
8. Mensagens de `require()` em PT em `UserLibraryRepository.kt` (camada de dados, fora do alcance da regex de UI).

## Deleções executadas em 2026-09-06 (com aprovação explícita do dono)

- `services/license-server/ecdsa-private.txt` — chave privada ECDSA de produção. **Ressalva:** não foi possível verificar via `wrangler secret list` que `SIGNING_KEY_ECDSA` está no Worker (CLI não disponível neste ambiente); o dono aprovou a deleção ciente disso. Evidências de que a chave já vive no Worker: licenciamento em produção (`STRIPE_MODE = "live"`), comentário do `.gitignore` ("meant to be deleted after use") e afirmação de `signing.js` de que a chave "never leaves the Worker". Se a rotação for necessária um dia, gerar par novo e atualizar o secret + a pública compilada nos apps.
- `services/license-server/ed25519-private.txt` — chave não identificada (não correspondia à produção).
- `MediaDeviceSalts`, `MediaDeviceSalts-journal`, 3× `hs_err_pid*.log`, 2× `replay_pid*.log`, `build-validation.*.log` — lixo local da raiz (~2,6 MB).
- `apps/iptvburo/Debug/` — artefatos de build do scaffold removido.

## Riscos restantes

- Mudança de comportamento público em `WebDavClient.list()` (erros agora propagam) — sem chamadores fora do pacote hoje, mas consumidores futuros devem saber.
- O workflow de secret scan só será validado de verdade no primeiro run no GitHub Actions.
- Suite completa `:apps:desktop:test` (com UI tests Compose) não foi rodada — foram rodadas todas as classes das áreas tocadas.
- `detail-play-label.test.js` e `guide-clock.test.js` (arquivos novos do dono, não rastreados) receberam fixes que só entram no histórico quando forem adicionados.

## Próximo passo recomendado

1. Confirmar as deleções acima (principalmente as chaves privadas).
2. Revisar o diff (`git status` / `git diff`) e commitar em commits temáticos.
3. Push para validar o novo CI (android-ci + secret-scan) na branch `codex/windows-clean-release`.
4. Decidir os 8 itens pendentes acima.
