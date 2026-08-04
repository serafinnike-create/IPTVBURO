# Prompt de implementação — Claude/Codex — GDD 8 Media SuperHub

Você está trabalhando no repositório **IPTVBURO**.

Sua tarefa é implementar incrementalmente o **GDD 8.0 — Media SuperHub & Audio Experience**, sem reiniciar, apagar ou reescrever o vertical funcional existente.

## Documentos obrigatórios

Leia nesta ordem:

1. `CLAUDE.md`;
2. `docs/GDD_IPTV_BURO.md`;
3. `docs/GDD_8_MEDIA_SUPERHUB_AUDIO.md`;
4. `docs/status/CURRENT_IMPLEMENTATION.md`;
5. `docs/reviews/REVIEW_0_2_ARCHITECTURE_AND_NEXT_GATES.md`;
6. `docs/ux/design-system.md`;
7. `docs/security/credential-handling.md`;
8. ADRs relevantes em `docs/adr/`.

Depois, audite o código real. A documentação não substitui a inspeção do repositório.

## Regras não negociáveis

- Preserve Android/Android TV e Windows existentes.
- Não reinicie o projeto e não troque a stack.
- Não elimine código funcional para criar uma arquitetura teórica.
- Não inclua playlists privadas, credenciais, tokens, URLs assinadas ou conteúdo protegido.
- Não implemente bypass de DRM, scraping de serviços comerciais ou downloads sem autorização.
- Não chame recurso de concluído sem build, testes e evidência.
- Não misture toda a lógica em `MainViewModel`, `AppShellScreen`, `DesktopApp`, `XtreamWorkspace` ou composables.
- Não transforme `Channel` no modelo universal definitivo.
- Toda migration Room deve ter teste com `MigrationTestHelper`.
- Toda URL autenticada deve ser resolvida tardiamente e redigida em logs.
- A UI deve ser capability-driven.
- As quatro localizações existentes devem permanecer consistentes: PT-BR, EN, DE e IT.

## Objetivo desta execução

Implemente somente a **Fase 0 — contratos universais e proteção contra regressão**.

Não implemente ainda a tela Música, importação de arquivos, rádio real, RSS, downloads ou novos players. A meta é preparar a arquitetura de forma segura e compilável.

## Fase 0 — entregas obrigatórias

### 1. Auditoria inicial

Antes de editar:

- descreva a árvore de módulos atual;
- localize todos os usos de `CatalogContentType`, `ContentKind`, `Channel`, `PlaybackProgress`, `PlayerScreen`, adapters de fonte e capabilities;
- identifique schemas Room e migrations vigentes;
- registre quais testes protegem o vertical atual;
- confirme branch, commit e estado do worktree;
- não exponha valores privados encontrados localmente.

Adicione o resultado a:

```text
docs/audits/GDD8_PHASE0_ARCHITECTURE_AUDIT.md
```

### 2. MediaKind universal

Em `packages/domain-model`, adicione:

```kotlin
enum class MediaKind {
    LIVE_TV,
    MOVIE,
    SERIES,
    VIDEO_EPISODE,
    MUSIC_TRACK,
    ALBUM,
    ARTIST,
    AUDIO_PLAYLIST,
    RADIO_STATION,
    PODCAST_SHOW,
    PODCAST_EPISODE,
    AUDIOBOOK,
    AUDIOBOOK_CHAPTER,
    PHOTO,
    GAME_STREAM,
    UNKNOWN,
}
```

Requisitos:

- não substituir imediatamente `CatalogContentType` ou `ContentKind`;
- criar mappers explícitos e testados;
- mapeamentos de áudio para enums legados devem retornar `UNKNOWN`, nunca fingir que são vídeo;
- preservar serialização/ordem quando houver risco de persistência;
- documentar qualquer decisão de compatibilidade.

### 3. Capabilities universais

Criar modelos puros de domínio, sem dependência Android/Desktop:

```text
MediaCapabilities
PlaybackCapabilities
SourceCapabilities
```

Cobrir no mínimo:

- playable;
- live;
- seekable;
- downloadable;
- backgroundPlayback;
- gapless;
- crossfade;
- replayGain;
- lyrics;
- chapters;
- multipleAudioTracks;
- subtitles;
- pictureInPicture;
- multiview.

Requisitos:

- defaults conservadores;
- capability ausente significa indisponível;
- testes de composição/interseção quando aplicável;
- UI ainda não precisa consumir os novos modelos nesta fase.

### 4. Identidade universal preparada

Amplie o namespace de `ContentKind` ou crie uma estratégia compatível sem mudar as chaves de vídeo persistidas.

Requisitos:

- chaves atuais de filme, série, episódio e live devem permanecer byte-for-byte iguais;
- adicionar testes de regressão com valores conhecidos;
- adicionar fábricas para track, album, artist, radio, podcast, podcast episode, audiobook e chapter;
- não usar token, query de autenticação, cookie, username ou password na identidade;
- normalizar URLs de rádio/feed removendo parâmetros sensíveis;
- não deduplicar faixa somente por título.

Se ampliar `ContentKind` alterar comportamento de `ContentIdentity.of`, crie API separada para áudio e documente a razão.

### 5. Media source SPI

Criar um novo módulo somente se a auditoria confirmar que reduz acoplamento:

```text
packages/media-source-spi
```

Contrato alvo:

```kotlin
interface MediaSourceAdapter {
    val sourceType: MediaSourceType
    suspend fun validate(config: SourceConfig): SourceValidation
    fun scan(config: SourceConfig): Flow<MediaImportEvent>
    suspend fun resolve(locator: PlaybackLocator): ResolvedMedia
    suspend fun capabilities(config: SourceConfig): SourceCapabilities
}
```

Nesta fase:

- contratos e modelos apenas;
- sem reescrever clientes Xtream/Stalker/M3U;
- criar um adapter de compatibilidade mínimo ou mapper em teste, sem alterar runtime;
- eventos devem suportar progresso, warning, cancelamento e falha redigida;
- `SourceConfig.toString()` nunca pode revelar segredos;
- `ResolvedMedia.toString()` deve redigir URL e headers.

Se criar módulo novo for desproporcional, mantenha os contratos temporariamente em `domain-model` e registre ADR com gatilho para extração.

### 6. ADR

Criar:

```text
docs/adr/ADR-009-UNIVERSAL-MEDIA-DOMAIN-AND-SOURCE-ADAPTERS.md
```

O ADR deve registrar:

- contexto do domínio atual orientado a vídeo;
- decisão de migração incremental;
- por que `Channel` não será universal;
- compatibilidade com Room e código existente;
- source adapter SPI;
- separação entre locator persistido e URL resolvida;
- consequências, riscos e alternativas rejeitadas.

### 7. Testes

Adicionar testes mínimos para:

- todos os mappings de vídeo;
- tipos de áudio retornando `UNKNOWN` no legado;
- chaves antigas de `ContentIdentity` preservadas;
- identidade de áudio sem segredo;
- URL normalization;
- defaults conservadores de capabilities;
- redaction em `toString()`;
- compatibilidade do adapter sem chamada de rede.

Não usar fontes privadas. Fixtures devem ser sintéticas ou públicas e estáveis.

### 8. Build e validação

Execute, conforme o ambiente permitir:

```powershell
.\gradlew.bat test :apps:android-tv:lintDebug :apps:android-tv:assembleDebug :apps:desktop:test
```

Quando disponível e não bloqueado pelo ambiente:

```powershell
.\gradlew.bat :apps:desktop:packageMsi
```

Registre:

- comandos executados;
- resultados;
- testes adicionados;
- limitações do ambiente;
- nenhum resultado presumido.

### 9. Status

Atualize `docs/status/CURRENT_IMPLEMENTATION.md` somente com fatos confirmados.

Não altere badges ou anuncie Música/Rádio como implementados. O estado correto após a Fase 0 é:

```text
Media SuperHub / áudio: fundação de domínio em implementação; nenhuma vertical de usuário liberada.
```

## Fora de escopo desta execução

- botão Música na Ribbon;
- player de áudio novo;
- background service;
- rádio real;
- parser RSS;
- importação local;
- metadata ID3/FLAC;
- playlists do usuário;
- downloads;
- servidor Subsonic/Navidrome/Jellyfin;
- IA e recomendações;
- alterações visuais extensas;
- release/tag.

## Critérios de conclusão da Fase 0

A fase termina somente quando:

1. o código existente continua compilando;
2. os testes existentes continuam passando;
3. os contratos universais estão em código compartilhado;
4. os mappings legados estão testados;
5. identidades de vídeo não mudaram;
6. nenhum segredo pode vazar em identidade ou `toString()`;
7. ADR e auditoria existem;
8. nenhuma função de usuário falsa foi exposta;
9. o diff é focado e revisável;
10. o relatório final diferencia claramente implementado, testado e planejado.

## Formato do relatório final

Use esta estrutura:

```text
Resumo
Arquivos alterados
Decisões arquiteturais
Testes executados
Builds executadas
Riscos restantes
Próxima fase recomendada
Estado: PHASE_0_COMPLETE ou PHASE_0_INCOMPLETE
```

Se qualquer gate essencial falhar, use `PHASE_0_INCOMPLETE` e explique exatamente o motivo. Não crie tag e não publique release.