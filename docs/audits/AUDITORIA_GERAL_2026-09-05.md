# Auditoria Geral do Repositório — IPTV BURO

**Data:** 2026-09-05
**Branch auditada:** `codex/windows-clean-release` (HEAD `e2ade08` — "Versao 4.0.0")
**Tipo:** auditoria somente leitura (nenhum arquivo de código modificado)
**Método:** 4 frentes paralelas (apps Android/Desktop, packages/, Tizen/site/serviços, higiene/CI/docs) + execução real dos testes dos pacotes.

---

## Resumo executivo

O projeto está em estado **maduro e incomumente disciplinado em segurança**: nenhum segredo commitado no histórico, redação de credenciais em `toString()`/logs é consistente e **enforçada por testes**, `network_security_config` bem configurado, license-server com MFA/TOTP, rate limiting e verificação de assinatura Stripe. A base de testes é forte: **1.343 testes dos pacotes executados nesta auditoria, 0 falhas**.

Os problemas concentram-se em 5 blocos:

1. **Chaves privadas de assinatura de licença em texto claro no disco** (dentro do diretório do repo, embora não commitadas).
2. **Dívida arquitetural**: `MainViewModel` (5.693 linhas, com clientes de rede construídos dentro) e `DesktopAppState` (8.582 linhas) — violação direta das regras do AGENTS.md.
3. **Limites de memória aplicados tarde demais** em parsers/clientes (gzip bomb no EPG, linha M3U, corpos WebDAV/TMDb sem teto, WebDAV sem `callTimeout`).
4. **CI inconsistente**: branch atual não dispara CI; `android-ci.yml` roda bare `test` que o próprio release workflow diz que quebra em runner headless.
5. **Documentação desatualizada**: docs citam v3.3.0/branch `agent/...`/Fase 0 pendente; realidade é v4.0.0, branch `codex/...`, Fase 0 concluída.

---

## Verificação executada nesta auditoria

```
./gradlew :packages:domain-model:jvmTest :packages:playlist-parser:test \
  :packages:xtream-client:test :packages:stalker-client:test \
  :packages:webdav-client:test :packages:metadata-client:test \
  :packages:media-source-spi:test
```

**Resultado: BUILD SUCCESSFUL.**

| Pacote | Testes | Falhas |
|---|---|---|
| domain-model | 1.035 | 0 |
| metadata-client | 120 | 0 |
| xtream-client | 78 | 0 |
| playlist-parser | 72 | 0 |
| webdav-client | 18 | 0 |
| stalker-client | 15 | 0 |
| media-source-spi | 5 | 0 |
| **Total** | **1.343** | **0** |

Amostra dos testes Tizen (fora do `npm test`) também passou: network-redirect-pinning 4/4, licence-gate 16/16, guide-clock 12/12, detail-play-label 15/15.

**Não executado:** testes de UI Compose do desktop (precisam de display), testes instrumentados Android (precisam de emulador), build completo `assembleDebug`.

---

## 🔴 Crítico

### C1 — Chave privada ECDSA de produção em texto claro no disco
`services/license-server/ecdsa-private.txt` contém a chave privada real cuja SPKI confere **byte a byte** com a chave pública compilada no app Tizen (`apps/samsung-tizen/js/license.js:45-47`). Quem obtiver o arquivo forja licenças válidas para todas as TVs instaladas.
- Nunca foi commitada (`.gitignore:80-81` cobre; histórico limpo), mas o próprio `.gitignore:75` diz que esses arquivos "are meant to be deleted after use" — estão no disco desde 13/08.
- Contradiz `services/license-server/src/signing.js:122-123`: *"The private key is never in this repository and never leaves the Worker"*.
- **Ação:** apagar após confirmar que `SIGNING_KEY_ECDSA` está no Worker; considerar rotação se o diretório puder ter sido sincronizado/copiado.

### C2 — Segunda chave privada não identificada no mesmo local
`services/license-server/ed25519-private.txt` em texto claro. A SPKI derivada **não** corresponde à chave Ed25519 de produção compilada no Android/desktop (`AndroidLicenseClient.kt:923`, `LicenseEndpoints.kt:119`) — chave antiga ou de outro ambiente, impossível dizer auditando. **Ação:** deletar.

### C3 — Lógica de rede dentro do ViewModel (violação do AGENTS.md)
`MainViewModel.kt` (android-tv): 5.693 linhas, 24 dependências no construtor (`:130-153`), injeta `OkHttpClient` (`:145`) e constrói `TmdbClient`/`CriticScoresClient` dentro do ViewModel (`:3424-3440`, `:3463-3468`). UI layer tocando rede diretamente — violação explícita da regra "UI não acessa DAO ou cliente de rede diretamente".

### C4 — CI: `android-ci.yml` provavelmente quebrado
`.github/workflows/android-ci.yml:34` roda bare `./gradlew test lint assembleDebug` em `ubuntu-latest`. Mas `preview-release.yml:58-61` afirma que bare `test` roda os testes de UI Compose do desktop, que **falham em runner headless** — e `apps/desktop/build.gradle.kts:345` confirma `compose.desktop.uiTestJUnit4` no classpath. Um dos dois está errado. **Ação:** verificar o último run no GitHub e alinhar (usar tasks explícitas como o release workflow faz).

---

## 🟠 Alto

### A1 — `DesktopAppState` god class
`DesktopAppState.kt`: 8.582 linhas, ~535 membros, agregando repositórios, clientes TMDb/OMDb/adulto, licença, música, downloads, cast, multiview (`:197-228`). É a "classe de shell com toda a lógica" que o AGENTS.md proíbe.

### A2 — Gzip bomb no EPG (XMLTV)
`XmltvParser` descomprime GZIP **sem teto de bytes descomprimidos** (`XmltvParser.kt:51, 93-94`); o limite de 512 MB do `xmltvGuide` é sobre bytes **comprimidos** (`XtreamClient.kt:345`). Um `.xml.gz` hostil amplifica para gigabytes em memória. **Ação:** aplicar `MaximumBytesInputStream` **após** o `GZIPInputStream`.

### A3 — WebDavClient sem `callTimeout` e sem teto de corpo
`WebDavClient.kt:32-36` — só connect 15s/read 30s; é exatamente a lacuna slow-loris que Xtream (`XtreamClient.kt:38-45`) e Stalker documentaram e fecharam. `list()` bufferiza o corpo inteiro sem limite (`:73`) e engole qualquer erro como `emptyList()` (indistinguível de pasta vazia).

### A4 — Metadata clients sem teto de corpo e sem `callTimeout`
`TmdbClient.kt:43-47,1074`, `CriticScoresClient.kt:128`, `AdultArtworkClient.kt:111` — corpos de resposta lidos sem limite de tamanho.

### A5 — Branch de trabalho não dispara CI
`android-ci.yml:5` dispara push apenas em `main` e `agent/**`. A branch ativa é `codex/windows-clean-release` — pushes nela não rodam CI. O gate de testes está desligado no fluxo principal de trabalho.

### A6 — Strings PT-BR hardcoded no Android TV sem teste de proteção
`MovieDetailsScreen.kt:267,318,449,452-454,513`; `SeriesDetailsScreen.kt:477`. O desktop tem `NoHardcodedTextTest`; o android-tv **não tem equivalente**. O projeto exige PT-BR/EN/DE/IT.

### A7 — Música exposta na UI do desktop em divergência com o GDD 8
AGENTS.md diz que áudio "ainda não está liberada" e a Fase 0 "não deve expor Música/Rádio na interface" — mas o desktop já expõe: `audio.supported: true` em `packages/platform-capabilities/windows-preview.json:25-27`, destino navegável em `DesktopApp.kt:1674-1681` e `MusicWorkspace` em `DesktopApp.kt:537-542`. Doc e código precisam ser reconciliados (decidir: esconder atrás de capability ou atualizar a doc).

### A8 — Docs estruturalmente desatualizadas
- `AGENTS.md`: diz que a branch é `agent/iptv-buro-0.2-preview` (real: `codex/windows-clean-release`) e que a Fase 0 do GDD 8 é a "próxima tarefa" (já executada, conforme `docs/audits/GDD8_PHASE0_ARCHITECTURE_AUDIT.md`).
- `CURRENT_IMPLEMENTATION.md:6-10`: declara "Tag mais recente: v3.3.0" — o repo tem `v4.0.0` no HEAD. Tabela de módulos (`:932-939`) omite `stalker-client`, `metadata-client`, `media-source-spi`, `webdav-client`, `android-tv-baselineprofile`, `samsung-tizen` e `license-server`.

---

## 🟡 Médio

| # | Achado | Evidência |
|---|---|---|
| M1 | 24 arquivos de teste Tizen fora do `npm test` (passam, mas não rodam no harness) | `apps/samsung-tizen-tests/package.json` |
| M2 | M3U: check de tamanho de linha **após** a linha estar em memória (linha de ~200 MB materializada antes do throw) | `M3uParser.kt:69-73` |
| M3 | M3U: URI opaca `http:foo` aceita como stream (falta exigir `uri.host != null`) | `M3uParser.kt:556-565` |
| M4 | M3U: `epgUrls` do header sem validação de scheme (`file:`/`javascript:` repassados ao fetcher de EPG) | `M3uParser.kt:440-447` |
| M5 | Stalker: portal sem scheme vira `http://` (MAC+token em claro sem aviso); artwork não sanitizado; sem retry transitório | `StalkerClient.kt:164,318` |
| M6 | Schemas de capacidades não validados no Gradle (`windows-preview.json`, `android-adaptive.json`); `design-tokens/tokens.json` espelhado à mão no tema desktop sem teste de paridade | `contracts/platform-capabilities.schema.json` |
| M7 | Hero 3D do site é código morto shipado: `setupHero3d()` nunca chamado, CSS esconde o mount, mas `hero3d.js` (305 linhas) + `three.module.min.js` (655 KB) vão no deploy — e o diff não commitado investe +100 linhas nele | `site/site.js:340,554-556`, `styles.css:337` |
| M8 | ~60 `println` de debug no desktop main (um dentro da composição, `DesktopApp.kt:556`); degradação silenciosa via `runCatching{}.getOrNull()` (~39 ocorrências) sem telemetria | `DesktopAppState.kt` |
| M9 | `apps/iptvburo/` (scaffold Tizen C de template) é código morto rastreado | `apps/iptvburo/src/iptvburo.c:7` |
| M10 | Diretórios locais não ignorados: `.codex-tmp/`, `.backup-original-site-assets/`, `.claude/` — um `git add -A` os commita | `git check-ignore` |
| M11 | Sem varredura de segredos no CI — dívida assumida no `SECURITY_LOGGING_AUDIT.md` (2/ago) como gate obrigatório de release, ainda não implementada | `.github/workflows/` |
| M12 | `DemoStreamingDiscoveryProvider` em `src/main` usado só por testes | `apps/desktop/src/main/.../data/` |
| M13 | Xtream permite HTTP puro sem aviso ao usuário (credenciais em query string são inerentes à API, mas merece aviso na UI) | `XtreamEndpointParser.kt:82` |

---

## 🟢 Baixo

- `println` em código de biblioteca (`XtreamClient.kt:499,518`).
- Duplicação: `MaximumBytesInputStream` quase idêntico em xtream e stalker; helpers JSON flexíveis triplicados; `parseProviderIdList` duplicado dentro do xtream-client.
- UTF-16 sem BOM decodifica como UTF-8 com NULs, sem warning (`M3uParser.kt:326-359`).
- `WebDavListing`: parsing XML por regex; padrão `COLLECTION` casa `<collectionfoo>` (`WebDavListing.kt:105`).
- `catch (_: Exception)` engole `InterruptedException` sem restaurar flag (`TmdbClient.kt:1079`).
- `LanguageSelectionScreen.kt:85`: rodapé só em PT na tela pré-locale.
- `usesCleartextTraffic="true"` redundante com o NSC (`AndroidManifest.xml:60-61`).
- `benchmark = "1.5.0-rc01"` (RC) contradiz o comentário adjacente que prega stable (`libs.versions.toml:28-33`).
- Lacuna de criptografia M3U no Room permanece aberta (`credential-handling.md:47-48`) com v4.0.0 já tagueada como final — revisar se "versão estável" chegou sem fechá-la.
- E-mail pessoal do autor publicado em `apps/samsung-tizen/config.xml:9` (vai para a loja Samsung).
- Lixo local na raiz (não rastreado): `MediaDeviceSalts`, 3× `hs_err_pid*.log` (OOM de memória nativa — `-Xmx3g` pode estar apertado), 2× `replay_pid*.log`, `build-validation.*.log`.
- `local.properties:3` contém chave TMDb real em texto claro (não rastreada; considerar rotação se a máquina for compartilhada).
- Baseline profiles commitados em caminho `generated/` (1,5 MB ×2) — confirmar se deliberado.
- Assets de marca duplicados byte-a-byte em `assets/brand/` e `apps/desktop/src/main/resources/brand/` (2,7 MB ×2).
- `apps/apple` é apenas README stub — coerente com "planejado", mas qualquer claim de suporte Apple seria incorreto.
- Docs de auditoria antigas com afirmações hoje falsas (`SAMSUNG_ANDROID_PARITY_AUDIT.md` diz que licença está desabilitada sem chave real — há chave real compilada).

---

## Pontos fortes verificados (não presumidos)

- **Zero segredos commitados** no histórico; `.gitignore` acima da média (keystores, `.env*`, chaves de assinatura, `*.part`).
- Redação de credenciais consistente e **testada** (`AdultArtworkKeyTest`, `StalkerCatalogueRepositoryTest`, `StructuralCredentialGuardsJvmTest`); `CredentialVault` zera CharArrays.
- Credenciais de fonte em AES-GCM no Keystore (Android) e DPAPI (Desktop); resolução tardia de URLs autenticadas.
- Timeouts presentes na maioria dos clientes; `CancellationException` re-lançada corretamente.
- Parser M3U com streaming real, limites de bytes/canais/warnings, allowlist de schemes e headers; XMLTV endurecido contra XXE.
- Xtream client: retry com backoff e orçamento, tetos separados buffer/stream, sanitização de artwork, redirects desligados.
- Tizen: 294 usos de `escapeHtml`/`attr`, nenhuma interpolação de dados de provedor sem escape encontrada; `pinHost` anti-redirect.
- License server: Stripe signature antes do payload, token admin em tempo constante, MFA TOTP, rate limiting no edge, segredos só via `wrangler secret`.
- Site: CSP restritiva por rota em `_headers`; i18n via `createTextNode`.
- i18n desktop madura: 5 idiomas com 3 testes de proteção (`EveryLanguageCompleteTest`, `NoHardcodedTextTest`, `StringsConstructorLimitTest`).
- 1.343 testes de pacotes verdes nesta auditoria.

---

## Plano de ação recomendado (ordem)

1. **Hoje:** apagar `ecdsa-private.txt` e `ed25519-private.txt` de `services/license-server/` (após confirmar secrets no Worker); avaliar rotação.
2. **Curto prazo (dias):** adicionar `codex/**` aos gatilhos do `android-ci.yml`; resolver a contradição do bare `test` (C4); religar os 24 testes Tizen no `package.json`; adicionar `.codex-tmp/`, `.backup-original-site-assets/`, `.claude/` ao `.gitignore`.
3. **Curto prazo:** teto de bytes descomprimidos no XMLTV (A2), `callTimeout` + teto de corpo no WebDAV (A3) e metadata clients (A4) — mudanças pequenas e testáveis.
4. **Médio prazo:** portar `NoHardcodedTextTest` para android-tv e corrigir as strings hardcoded (A6); extrair clientes de metadata do `MainViewModel` para repositório injetável (C3); fatiar `DesktopAppState` por domínio (split por feature, não big-bang).
5. **Governança:** atualizar `AGENTS.md` e `CURRENT_IMPLEMENTATION.md` (A8); reconciliar exposição de Música no desktop com o GDD 8 (A7); implementar secret-scanning no CI (M11); decidir destino de `apps/iptvburo/` (M9) e do hero 3D (M7).

---

## Limitações desta auditoria

- Testes de UI Compose do desktop e testes instrumentados Android **não executados** (requerem display/emulador).
- Build completo `assembleDebug`/`assembleRelease` não executado.
- Auditoria de código somente leitura; nenhuma correção foi aplicada.
- Modificações não commitadas (2.928 inserções em 14+ arquivos) foram inspecionadas por amostragem, não exaustivamente.
