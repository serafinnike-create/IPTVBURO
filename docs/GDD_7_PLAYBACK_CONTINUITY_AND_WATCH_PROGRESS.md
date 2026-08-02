# IPTV BURO — GDD 7.0: Playback Continuity & Watch Progress

**Versão:** 7.0  
**Data:** 2 de agosto de 2026  
**Status:** extensão obrigatória dos GDDs 1.0 a 6.0  
**Escopo inicial:** Android/Android TV e Windows preview, com domínio compartilhável para Android mobile e Apple

## 1. Objetivo

Permitir que cada perfil continue um filme ou episódio exatamente do ponto em que parou, mesmo depois de fechar o aplicativo ou reiniciar o aparelho.

O sistema deve:

- persistir posição e duração por perfil;
- exibir uma fileira `Continuar assistindo`;
- desenhar uma barra de progresso nos cards;
- oferecer `Continuar de {tempo}` e `Assistir do início`;
- marcar conteúdo concluído com regras previsíveis;
- remover itens concluídos da fileira de continuidade;
- preservar o progresso após atualização de catálogo quando a identidade do item continuar estável;
- funcionar sem backend na primeira versão;
- preparar sincronização futura entre aparelhos sem acoplar a UI ao mecanismo de sync.

TV ao vivo nunca utiliza este sistema. O recurso se aplica somente a VOD seekable: filmes e episódios.

## 2. Experiência do usuário

### 2.1 Card com progresso

Todo card de filme ou episódio com progresso válido deve mostrar uma barra horizontal na base:

- trilho discreto usando token de superfície secundária;
- preenchimento em âmbar/laranja BURO, compatível com `BURO Nocturne`;
- altura entre 3 e 5 dp, adaptada por plataforma;
- cantos arredondados;
- preenchimento proporcional a `positionMs / durationMs`;
- acessibilidade anunciando `Assistido {percentual}%`;
- conteúdo concluído pode mostrar ícone de conclusão, sem barra incompleta.

A barra deve aparecer em:

- Living Home;
- fileira `Continuar assistindo`;
- Filmes;
- Séries e episódios;
- Minha BURO;
- pesquisa e resultados quando houver espaço.

### 2.2 Continuar assistindo

A Home deve possuir fileira real `Continuar assistindo`, ordenada por `lastWatchedAt` decrescente.

Regras:

- somente itens não concluídos com progresso elegível;
- consulta pequena e paginável;
- sem carregar o catálogo completo na UI;
- ocultar a fileira quando vazia;
- respeitar o perfil ativo;
- Kids só mostra itens permitidos;
- episódio identifica série, temporada e número.

### 2.3 Retomada

Ao abrir filme ou episódio com progresso elegível, exibir:

- botão principal `Continuar de 42:17`;
- ação secundária `Assistir do início`;
- opção de remover progresso na ficha ou Minha BURO.

O aplicativo não deve pular silenciosamente para o ponto salvo. Retomada automática só é permitida mediante preferência explícita do perfil.

### 2.4 Conclusão

Um item é concluído quando:

- percentual assistido é igual ou superior a 90%; ou
- faltam no máximo 5 minutos para o final;
- somente com duração total conhecida e positiva.

Usar regra conservadora para conteúdos curtos.

Quando concluído:

- remover de `Continuar assistindo`;
- manter histórico de conclusão;
- oferecer `Assistir novamente`;
- episódios podem habilitar `Próximo episódio`, sem autoplay obrigatório.

## 3. Persistência

Criar entidade conceitual compartilhável `PlaybackProgress`.

Campos mínimos:

```text
profileId
sourceId
contentId
contentType (MOVIE | EPISODE)
seriesId opcional
seasonNumber opcional
episodeNumber opcional
positionMs
durationMs
progressPercent
lastWatchedAtEpochMillis
completedAtEpochMillis opcional
updatedAtEpochMillis
revision
```

Chave primária recomendada:

```text
(profileId, sourceId, contentId)
```

O `contentId` deve ser identidade estável. URL resolvida, token e credencial nunca fazem parte da chave.

Índices mínimos:

- `(profileId, completedAtEpochMillis, lastWatchedAtEpochMillis)`;
- `(profileId, contentType, lastWatchedAtEpochMillis)`;
- `(sourceId, contentId)`;
- opcional `(profileId, seriesId, seasonNumber, episodeNumber)`.

## 4. Política de gravação

Persistir checkpoint:

- a cada 10 a 15 segundos enquanto reproduz;
- ao pausar;
- após seek relevante;
- ao navegar para trás;
- em `ON_STOP`/background;
- antes de liberar o player;
- no estado `ENDED`.

Evitar escrita excessiva:

- throttle/debounce;
- não gravar mudança insignificante;
- I/O fora da thread principal;
- flush do último checkpoint no encerramento controlado.

Não criar progresso elegível quando:

- duração desconhecida ou zero;
- mídia não seekable;
- reprodução inferior a 30 segundos;
- percentual inferior a 2%;
- TV ao vivo;
- identidade instável.

## 5. Retomada segura

Antes de `seekTo(savedPositionMs)`:

1. confirmar item e perfil;
2. aguardar timeline/duração válidas;
3. limitar posição ao intervalo reproduzível;
4. ignorar registro concluído;
5. ignorar posição próxima do início;
6. se a duração atual divergir muito, pedir confirmação ou reiniciar;
7. registrar erro sem URL, token ou credencial.

## 6. Arquitetura obrigatória

A lógica não deve ser colocada integralmente em `MainViewModel`, `PlayerScreen`, `DesktopAppState` ou composables.

Criar:

```text
PlaybackProgressRepository
ObserveContinueWatchingUseCase
GetResumeDecisionUseCase
SavePlaybackCheckpointUseCase
MarkPlaybackCompletedUseCase
ClearPlaybackProgressUseCase
PlaybackProgressPolicy
PlaybackProgressUiMapper
```

Android:

- Room local;
- migration explícita para novo schema;
- `MigrationTestHelper` para todas as rotas suportadas;
- player emite checkpoints para coordinator/use case;
- `PlayerScreen` recebe estado/callbacks e não acessa DAO.

Windows preview:

- interface equivalente;
- persistência local definida por ADR;
- nenhum segredo ou URL resolvida no progresso;
- JavaFX player publica posição/duração pelo contrato.

Futuro mobile/Apple:

- reaproveitar domínio e critérios;
- mecanismo nativo apenas adapta eventos e comandos.

## 7. Sincronização futura

O modelo deve suportar:

- `revision` monotônica ou equivalente;
- `updatedAtEpochMillis`;
- conflito `last meaningful progress wins`;
- conclusão não revertida por checkpoint antigo;
- isolamento por perfil;
- nenhuma URL, token, cookie ou credencial.

Backend não faz parte deste incremento.

## 8. Testes obrigatórios

### Unidade

- percentual;
- limiar de 30 segundos/2%;
- conclusão por 90%;
- conclusão por últimos 5 minutos;
- conteúdo curto;
- duração desconhecida;
- posição acima da duração;
- concluído não retorna à fileira;
- Kids filtra bloqueados;
- conflito de revisões;
- episódio e filme não colidem.

### Room/migração

- migration sem perda de fontes, categorias, canais, perfis e favoritos;
- índices e foreign keys;
- atualização de catálogo preserva progresso válido;
- remoção de perfil remove apenas seu progresso.

### Player

- checkpoint ao pausar, sair e background;
- retomada após force-stop/reabertura;
- `Assistir do início` ignora posição;
- término marca concluído;
- live não cria progresso;
- falha não corrompe último checkpoint.

### UI

- barra em 0%, 25%, 50%, 90% e concluído;
- foco D-pad;
- acessibilidade;
- layout compacto, expandido e TV;
- fileira vazia sem espaço morto;
- golden/screenshot tests quando disponíveis.

## 9. Critérios de aceitação

1. Filme fechado e reaberto oferece retomada correta.
2. Cada episódio mantém posição separada.
3. Progresso permanece após reiniciar o aparelho.
4. Barra aparece nos cards definidos.
5. `Continuar assistindo` é real e por perfil.
6. Concluído sai da fileira e oferece `Assistir novamente`.
7. Migration Room passa.
8. Nenhuma credencial/URL privada em registros ou logs.
9. Android TV, touch e Windows mantêm comportamento consistente onde suportado.
10. Build, testes e lint passam.

## 10. Fora de escopo

- sincronização em nuvem;
- recomendações por histórico;
- download offline;
- gravação de TV ao vivo;
- retomada de canal ao vivo;
- autoplay obrigatório;
- envio de histórico a terceiros.
