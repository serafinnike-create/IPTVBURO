# Prompt obrigatório para o Codex — estabilizar 0.2 e implementar GDD 7

Trabalhe sobre a branch `agent/iptv-buro-0.2-preview` atualizada.

Antes de modificar código, leia integralmente:

1. `docs/reviews/REVIEW_0_2_ARCHITECTURE_AND_NEXT_GATES.md`;
2. `docs/GDD_7_PLAYBACK_CONTINUITY_AND_WATCH_PROGRESS.md`;
3. `docs/status/CURRENT_IMPLEMENTATION.md`;
4. `docs/status/GDD_1_TO_5_IMPLEMENTATION_AUDIT.md`;
5. GDDs 1 a 6 e ADRs vigentes.

## Objetivo

Estabilizar a milestone 0.2 e implementar continuidade de reprodução para filmes e episódios, preservando o trabalho existente e sem gerar tag antes dos gates.

## Regras inegociáveis

- Não reinicie o projeto.
- Não substitua Android/Xtream/desktop funcional por protótipos vazios.
- Não publique release ou tag durante a execução.
- Não adicione downloader genérico, bypass de DRM, captura ou exportação.
- Não persista URL resolvida, token, senha, cookie, `Referer`, `Origin` sensível ou credencial em progresso/histórico.
- Não use fallback destrutivo do Room.
- Não declare Windows estável com matriz de player incompleta.
- Não concentre nova lógica em `MainViewModel`, `PlayerScreen`, `DesktopAppState` ou composables.
- Preserve dados da versão 0.1.
- Use mídia pública, sintética ou autorizada.

## Fase 1 — baseline

1. Atualize a branch e registre SHA inicial.
2. Execute:
   - testes Android;
   - lint Android;
   - assembleDebug;
   - testes desktop;
   - packageMsi/createDistributable;
   - varredura de segredos.
3. Inspecione GitHub Actions.
4. Corrija e documente qualquer falha.

## Fase 2 — estabilização arquitetural

### Android state/domain

Extraia sem mudar comportamento:

- `CatalogCoordinator`;
- `HomeCoordinator`;
- `ProfileCoordinator`;
- `PlaybackCoordinator`;
- `XtreamImportCoordinator`;
- `DetailsCoordinator`.

Use interfaces injetadas para plataforma. Converta Toast, navegação externa e efeitos de janela em `UiEffect` consumido pela UI.

### UI

Divida `AppShellScreen`, `DesktopApp`, `XtreamWorkspace` e telas gigantes por feature:

- composables stateless quando possível;
- estados/eventos explícitos;
- previews/testes;
- nenhuma consulta direta de repository em composable.

### Dados

Divida `RoomCatalogRepository` em:

- importação;
- consultas paginadas;
- detalhes;
- locators de playback;
- EPG;
- manutenção/transações.

Preserve atomicidade, keyset pagination e limites de escala.

## Fase 3 — migrations

1. Adicione `MigrationTestHelper`.
2. Teste rotas até schema 4.
3. Crie schema 5 somente depois.
4. Teste:
   - 1→2→3→4→5;
   - 2→3→4→5;
   - 3→4→5;
   - 4→5;
   - fontes, categorias, canais, perfis e favoritos preservados;
   - índices/foreign keys;
   - nenhuma perda destrutiva.

## Fase 4 — domínio de continuidade

Implemente:

- `PlaybackProgressRepository`;
- `PlaybackProgressPolicy`;
- `ObserveContinueWatchingUseCase`;
- `GetResumeDecisionUseCase`;
- `SavePlaybackCheckpointUseCase`;
- `MarkPlaybackCompletedUseCase`;
- `ClearPlaybackProgressUseCase`;
- `PlaybackProgressUiMapper`.

Modelo conforme GDD 7: perfil, fonte, ID estável, tipo filme/episódio, série/temporada/episódio, posição, duração, percentual, última reprodução, conclusão, revisão e updatedAt.

TV ao vivo nunca cria progresso.

## Fase 5 — Android

1. Emitir checkpoint:
   - a cada 10–15 segundos;
   - pause;
   - seek relevante;
   - back;
   - `ON_STOP`;
   - dispose;
   - ended.
2. Usar throttle/debounce.
3. Aguardar timeline/duração antes de retomar.
4. Oferecer:
   - `Continuar de {tempo}`;
   - `Assistir do início`;
   - remover progresso.
5. Criar fileira real `Continuar assistindo` por perfil.
6. Adicionar barra âmbar/laranja BURO nos cards.
7. Aplicar conclusão do GDD 7.
8. Preservar D-pad, foco, touch e TV.

## Fase 6 — Windows

1. Definir persistência local por ADR.
2. Não ligar progresso a URL/segredo.
3. Expor posição/duração pelo adapter JavaFX.
4. Aplicar critérios de retomada/conclusão onde suportado.
5. Player externo não gera rastreamento preciso sem retorno verificável.

## Fase 7 — proteção M3U

- separar segredo de locator;
- Keystore/criptografia equivalente Android;
- migration segura;
- redaction completa;
- testes que falham se segredo aparecer em Room/logs.

## Fase 8 — testes

Além do GDD 7:

- force-stop/reabertura;
- reinício de processo;
- troca de perfil;
- filmes/episódios distintos;
- conclusão;
- duração alterada;
- catálogo reimportado;
- migration com dados 0.1;
- barras 25/50/90%;
- D-pad/touch;
- Kids;
- Windows e fallback declarado;
- 500 mil itens sem regressão relevante.

## Fase 9 — documentação

Atualize honestamente:

- `docs/status/CURRENT_IMPLEMENTATION.md`;
- auditoria dos GDDs;
- ADRs;
- `packages/release-manifest/platforms.json`;
- README somente com evidência.

Registre testes, hardware, codecs, limitações e hashes apenas do CI final.

## Gate de saída

A tarefa termina com código, testes e documentação na branch. Não crie tag.

Relatório final obrigatório:

1. arquivos alterados;
2. responsabilidades extraídas;
3. migrations/testes;
4. continuar assistindo;
5. barras de progresso;
6. diferenças Android/Windows;
7. segurança M3U;
8. comandos/resultados;
9. riscos restantes;
10. `READY_FOR_PREVIEW` ou `NOT_READY_FOR_PREVIEW` com justificativa.
