# Fila de bugs — Windows/Desktop

Bugs reproduzidos em teste real. Cada item registra o sintoma observado, a causa
investigada no código e o que ainda falta confirmar.

## Estado atual

**Publicado:** `v2.0.0-alpha.3` — Windows (MSI) e Android (APK de depuração).

Quem tem a `alpha.2` instalada recebe esta versão por **Opções → Buscar
atualização**. O caminho foi verificado antes de publicar, não presumido:

- `isNewerVersion("2.0.0-alpha.3", "2.0.0-alpha.2")` é verdadeiro, e a versão
  atual não se oferece a si mesma — coberto por teste de regressão;
- o `DESKTOP_RELEASE_VERSION` gerado no build é `2.0.0-alpha.3`;
- o nome do MSI casa com o filtro de instalador e o APK **não** casa, então o
  atualizador não pode baixar o pacote errado;
- a URL de download satisfaz as cinco checagens de confiança (https, host
  `github.com`, sem userinfo, porta padrão, caminho do repositório);
- 286 MB contra um teto de 1 GB.

Corrigidos e publicados: BUG-001, BUG-002, BUG-004, BUG-006, BUG-007, BUG-008.

**Falta confirmar em uso real na alpha.3:** capa, sinopse e trailer na tela de
Assinaturas (itens 2 e 3 do BUG-006) — a causa provável foi corrigida junto com
as fotos do elenco, mas não houve confirmação em execução.

---

---

## BUG-008 — Assinaturas: "Séries" e "Esta semana" vazias; "Em breve" vazia; lista parece congelada

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** `v2.0.0-alpha.2`

### Sintomas relatados

1. clicar em **Séries** não mostra nada;
2. **Esta semana** não mostra nada;
3. **Em breve** não mostra nada;
4. a lista de Filmes parece a mesma há dois ou três dias.

### Causa 1 (a principal) — um clique perdido deixa a aba vazia para sempre

`loadStreamingShelves` recusava qualquer carregamento enquanto outro estivesse em
curso, com um booleano único. Abrir Assinaturas dispara o carregamento de Filmes;
clicar em **Séries** durante esses segundos caía em `if (streamingLoading) return`
— e **nada reagendava o pedido**. A aba ficava vazia pelo resto da sessão.

Agora o carregamento em curso é registrado com o *tipo* (`loadingKind`), e só
recusa um pedido duplicado do mesmo tipo. É a mesma classe de falha que o próprio
arquivo já documenta em `DesktopAppState.kt:1680` — a flag que ficava presa.

### Causa 2 — diretório de provedores errado para séries

`watchProviderDirectory` pedia sempre `watch/providers/movie`. Nas primeiras
posições no Brasil isso traz Google Play Movies e Apple TV Store, lojas de filme
que não carregam série; como serviço sem títulos é descartado, gastavam-se slots
à toa. Passou a pedir `watch/providers/tv` para as abas de série.

Medido, não presumido: dos 12 primeiros, o diretório de filmes rende **9** com
séries e o de TV rende **10**. É uma melhora modesta — sozinha **não** explicaria
uma aba totalmente vazia, e por isso a causa 1 é a principal.

### Causa 3 — "Em breve" perguntava algo impossível

A aba pedia, *por provedor*, filmes com data futura. Um filme que ainda não
estreou não está em serviço nenhum, então a resposta era vazia por construção.
Medido por provedor, com janela de um ano: Netflix 1, Prime 0, Disney 0, Apple 0.

Redefinida para a pergunta que o usuário faz — "o que ainda vai entrar no
catálogo": lançamentos de cinema dos últimos 6 meses que **nenhum** serviço
carrega por assinatura, num trilho único (nenhum serviço é dono deles). A
verificação é por título, porque a API não sabe expressar "não está em ninguém".

Confirmado: 305 filmes elegíveis hoje, e *Homem-Aranha: Um Novo Dia* retorna
`flatrate: nenhum` — exatamente o que a aba deve listar.

### Sintoma 4 — a lista parecer congelada

Explicado, sem alteração de código. As prateleiras vêm de `sort_by=popularity` /
`first_air_date.desc` na TMDb e há cache em disco por região e tipo, relido no
mesmo dia. É esperado que mude devagar: o catálogo da Netflix não muda todo dia.
Se ficar preso por muitos dias, aí é bug — vale reportar de novo com a data.

### Efeito colateral corrigido no caminho

`TmdbClient.get()` fazia `.asJsonObject` sem checar. A TMDb responde alguns erros
com um *array*, e isso lançava `ClassCastException` — escapando do `runCatching` e
virando crash em vez do `null` que todo chamador espera. Agora é verificado.

---

## BUG-006 — Assinaturas: "disponível somente no app", sem capa, sinopse, trailer nem foto do elenco

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** `v2.0.0-alpha.2` instalado do GitHub, chave TMDb adicionada pelo usuário

### Sintomas relatados

Depois de adicionar a chave da API do TMDb, a seção **Assinaturas** passou a
aparecer. Ao abrir um filme qualquer nela:

1. aparece "disponível somente no aplicativo TV Guru" — não mostra onde comprar
   ou assinar de verdade;
2. não aparece capa nem sinopse;
3. não aparece trailer;
4. em **Filmes** (catálogo normal), as fotos do elenco não carregam.

### Causa identificada — item 1

`TmdbStreamingCatalogue` responde duas perguntas por caminhos diferentes, e um
deles é falho:

- `pageFor()` usa o **id numérico do TMDb** — correto
  ([TmdbStreamingCatalogue.kt:72-75](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbStreamingCatalogue.kt#L72-L75));
- `detailsFor()` — que responde *onde assistir* — joga o id fora e busca por
  **texto do título + ano**
  ([TmdbStreamingCatalogue.kt:78](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbStreamingCatalogue.kt#L78)).

Pior: `TmdbClient.watchProviders()` chama `findMovieId()` e monta a URL fixa
`movie/$movieId/watch/providers`
([TmdbClient.kt:226-232](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt#L226-L232)),
e `findMovieId` pesquisa em `search/movie`
([TmdbClient.kt:484-496](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt#L484-L496)).

Ou seja:

- **para série, está errado por construção** — procura uma série no endpoint de
  filmes, então acha o filme errado ou nada;
- **para filme, é uma busca por texto** que já tinha o id exato na mão; título
  traduzido, com pontuação diferente ou ano ausente devolve outro filme ou nenhum.

Quando não acha, a lista de ofertas fica vazia e a tela cai no texto de
indisponibilidade — que é exatamente o "disponível somente no TV Guru".

Vale notar que o próprio código já avisa disso: o comentário em
`TmdbStreamingCatalogue.kt:60-62` diz que "null não é o mesmo que indisponível, e
quem chama não deve renderizar assim". A tela está fazendo justamente isso.

### Correção aplicada

**Item 1 — ofertas.** `watchProviders()` passou a receber o **id do TMDb** e um
`isSeries`, usando `tv/{id}/watch/providers` para série. `detailsFor()` para de
jogar fora o id que já tem. `findMovieId()` foi removido: existia só para
recuperar um id que o chamador já possuía.

Verificado contra a API real, não só contra mocks:

| endpoint | resposta para Game of Thrones (id 1399) |
| --- | --- |
| `tv/1399/watch/providers` (correto) | `HBO Max` |
| `movie/1399/watch/providers` (o que o código fazia) | **404 — not found** |

Esse 404 é exatamente o que virava "disponível somente no TV Guru".

**Item 4 — fotos do elenco.** `ensureCastPhoto` guarda `null` quando a busca
falha, para não repetir a consulta a cada recomposição. Só que toda tentativa
feita **antes** da chave existir também é uma falha, e `rebuildMetadataClients()`
não limpava esse cache — então `key in castPhotos` recusava tentar de novo e o
elenco de qualquer filme aberto antes de configurar a chave ficava sem foto para
sempre. O cache agora é descartado junto com o cliente que o produziu.

**Itens 2 e 3 — capa, sinopse e trailer.** `pageFor()` já usava o id correto e
devolve `overview`, `posterUrl` e `youtubeTrailerId`; a tela também já o chama
([DesktopAppState.kt:1783](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L1783)).
A causa mais provável é o mesmo cliente TMDb obsoleto do item 4, resolvido pela
mesma correção. **Não confirmado em execução** — vale reconferir na alpha.3.

### Ainda em aberto

A interface continua sem distinguir **"o TMDb não sabe"** de **"não há oferta
nesta região"**. As duas ainda viram a mesma frase. Com as ofertas voltando
corretamente o caso fica muito mais raro, mas a distinção continua valendo.

---

## BUG-007 — Espaço e clique não pausam o vídeo

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12 (segundo testador, outro computador)
**Ambiente:** `v2.0.0-alpha.2` instalado do GitHub, Windows

### Sintoma

Durante a reprodução, a barra de espaço não pausa, e clicar no vídeo também não.

### Causa provável

O handler existe e está correto
([DesktopPlayerOverlay.kt:216-219](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L216-L219)),
com foco pedido na abertura
([DesktopPlayerOverlay.kt:190-191](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L190-L191)).
O problema é que **a superfície de vídeo do VLC é uma janela nativa embutida**, e
quando ela recebe o clique, o foco de teclado vai para ela — o evento nunca chega
ao Compose.

Isso não é especulação: o próprio código já registra esse comportamento em outro
contexto, em `DesktopPlayerOverlay.kt:227`, explicando que a barra flutuante em
tela cheia existe porque "confiar só no F11 deixava o usuário preso quando a
superfície de vídeo embutida detinha o foco do teclado e a tecla nunca chegava".

Ou seja: o mesmo mecanismo que quebrava o F11 quebra o espaço. E isso também
explica o clique — não há handler de clique **sobre a superfície de vídeo**; os
dois `onClick = controller::togglePlayback` existentes
([:336](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L336),
[:469](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L469))
são dos botões da barra de controles, não do vídeo.

Há ainda uma segunda condição: o espaço só age se `state.ready` for verdadeiro.
`ready` exige que o VLC já tenha reportado `playing`, `paused` ou `stopped`
([VlcDesktopPlayer.kt:784](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt#L784)),
então nos primeiros segundos a tecla é ignorada de propósito. Se o testador
apertou logo no início, esse é um segundo motivo somado ao primeiro.

### Correção aplicada

**Espaço.** O `KeyEventDispatcher` global já existia neste arquivo e já resolvia
exatamente este problema — só tratava `Escape` e `F11`. `VK_SPACE` foi somado a
ele. O dispatcher vê a tecla antes de qualquer componente, então funciona
independentemente de quem detém o foco. Retornar `true` consome o evento, então
os outros dois handlers (Compose e canvas) não disparam junto: **não há duplo
toggle** — verificado contra o contrato do AWT, não presumido.

**Clique.** `createComponent` já aceitava um parâmetro `onClick` — adicionado
para o multiview — e o player normal simplesmente nunca passava um. Agora passa
`togglePlayback`, guardado por `state.ready` como os demais caminhos.

Também alinhei o handler do canvas, que chamava `togglePlayback()` sem checar
`ready`, ao contrário dos outros dois.

---

## BUG-001 — "Catálogo Xtream compatível" aparece com o catálogo já carregado; app às vezes trava

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** instalação limpa a partir do release do GitHub, `IPTV BURO v2.0.0-alpha.1`, Windows

### Sintoma

Após instalar do zero e adicionar a lista, a tela **Início** mostra o card de erro
`O servidor não retornou um catálogo Xtream compatível.` — enquanto o cabeçalho
da mesma tela informa `1 fontes · 2258 itens`. Ou seja, a fonte conectou e um
catálogo carregou, mas a Home reporta incompatibilidade. Em algumas execuções a
janela também congela por vários segundos.

### Causa provável

Duas falhas distintas se somam:

**1. A mensagem de erro é enganosa para qualquer falha não-Xtream.**

`DesktopAppState.toSafeXtreamMessage()`
([DesktopAppState.kt:4333](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L4333))
faz `when ((this as? XtreamClientException)?.reason)`. Qualquer throwable que
**não** seja `XtreamClientException` cai no ramo `null ->`, e um
`XtreamClientException` sem `reason` conhecido cai em `INVALID_RESPONSE`.
A Home usa essa função em
[DesktopAppState.kt:2985](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L2985),
então uma falha de memória, de parsing ou de metadados durante a montagem da
tela inicial é apresentada ao usuário como "catálogo Xtream incompatível" —
diagnóstico errado, que foi exatamente o que confundiu este teste.

**2. `categories()` bufferiza a resposta inteira em memória.**

`XtreamClient.categories()`
([XtreamClient.kt:90](../../packages/xtream-client/src/main/kotlin/com/lucasserafin94/iptvburo/xtream/XtreamClient.kt#L90))
usa o caminho `request()`
([XtreamClient.kt:304](../../packages/xtream-client/src/main/kotlin/com/lucasserafin94/iptvburo/xtream/XtreamClient.kt#L304)),
que lê **todo** o corpo para um `ByteArray`, decodifica para `String` e ainda
monta uma `JsonElement` — três cópias do mesmo conteúdo — com teto de
`DEFAULT_MAXIMUM_RESPONSE_BYTES = 512 MiB`
([XtreamClient.kt:704](../../packages/xtream-client/src/main/kotlin/com/lucasserafin94/iptvburo/xtream/XtreamClient.kt#L704)).
O catálogo em si já usa o parser streaming (`streamCatalog`), mas `categories`
não. Em um provedor grande isso é uma alocação enorme no dispatcher de IO, o que
explica o congelamento; se estourar o heap, o `OutOfMemoryError` resultante volta
pela função do item 1 e vira a mensagem de catálogo incompatível.

A Home ainda dispara `loadCatalog(MOVIE)` e `loadCatalog(SERIES)` de dentro do
build da tela
([DesktopAppState.kt:2849-2856](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L2849-L2856)),
o que concentra o custo todo no primeiro acesso — consistente com o cabeçalho
mostrando apenas os itens de LIVE (2258) enquanto filmes/séries ainda falhavam.

### Correção proposta

1. Separar o mapeamento de erro: só usar a mensagem Xtream quando o erro **for**
   `XtreamClientException`; para o resto, mensagem genérica de falha ao montar a
   Home, e nunca atribuir a causa ao servidor do usuário.
2. Migrar `categories()` para o caminho streaming, como `streamCatalog()` já faz.
3. Reduzir o teto de 512 MiB para um valor defensável para uma resposta de
   categorias.

### Correção aplicada

**1. Mensagem de erro separada por origem.**
`toSafeXtreamMessage()` agora é extensão de `XtreamClientException` — só roda
quando o erro **veio mesmo** do provedor. Todo o resto passa por
`toSafeFailureMessage()`, que atribui a falha ao aplicativo e nomeia o tipo da
exceção (sem a mensagem, que pode conter URL com credencial). Os 7 pontos de
chamada foram migrados.

**2. `categories()` agora é streaming.**
Novo `XtreamCategoryStreamParser`, espelhando o `XtreamCatalogStreamParser` que
já existia. Elimina as três cópias simultâneas da resposta (bytes → String →
árvore JSON) sob o teto de 512 MiB. Limite próprio de 50.000 categorias.

### Testes

6 testes novos em `XtreamClientTest` cobrindo o parser de categorias: array,
objeto indexado, id numérico, entradas inválidas puladas, corpo `false` como
lista vazia, e HTML respondido como `INVALID_RESPONSE`. Antes disso a única
cobertura de `categories()` era o teste de servidor privado, que não roda sem
variáveis de ambiente.

### Ainda não confirmado

O log de diagnóstico (`DiagnosticLog`) da execução que travou não foi coletado,
então não está provado que era exatamente OOM em `categories()`. As duas causas
corrigidas são reais e verificadas por leitura de código; se o congelamento
voltar, o log da execução com erro é o próximo passo.

---

## BUG-002 — `Node has been removed.` após "Redefinir"; o app quebra ao adicionar a lista em seguida

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** Windows, instalação limpa
**Reprodução:** Opções → Redefinir → adicionar lista com chave → erro no splash a 100%

### Sintoma

Caixa de diálogo nativa (Swing, fora do tema do app) com o texto
`Node has been removed.` sobre a tela de splash em `100%`, na etapa
"Organizando a sua lista…". O app não conclui a abertura.

### Causa — confirmada

`DesktopUserStore` guarda **uma única** instância de `Preferences`, resolvida na
construção
([DesktopUserStore.kt:111](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/user/DesktopUserStore.kt#L111)):

```kotlin
private val preferences: Preferences = Preferences.userRoot().node("com/lucasserafin94/iptvburo/user-v1")
```

`resetAll()` chama `removeNode()` nessa mesma instância
([DesktopUserStore.kt:497-500](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/user/DesktopUserStore.kt#L497-L500)):

```kotlin
fun resetAll() {
    preferences.removeNode()
    preferences.flush()
}
```

Pelo contrato de `java.util.prefs.Preferences`, `removeNode()` **invalida o
objeto permanentemente**: qualquer método chamado nele depois — exceto `name()`,
`absolutePath()`, `isUserNode()` e `flush()` — lança
`IllegalStateException("Node has been removed.")`. O store nunca readquire o nó.

Portanto, depois de um "Redefinir", **toda** leitura e escrita de preferência no
processo passa a lançar: perfis, idioma, favoritos, geometria da janela, chave
TMDb. Adicionar a lista logo em seguida grava o estado recém-importado, que é
exatamente onde o erro aparece — no fim do splash. A exceção sobe sem tratamento
até o handler padrão do AWT, o que explica a caixa nativa em inglês em vez de um
erro dentro do tema do app.

Observação: `flush()` na linha 499 é legal (é uma das exceções do contrato), então
o próprio `resetAll()` não falha — o dano só se manifesta na chamada seguinte.

### Correção proposta

1. Tornar o nó re-adquirível em vez de fixo — por exemplo trocar o `val` por um
   acessor que resolve `Preferences.userRoot().node(...)` sob demanda, e fazer
   `resetAll()` descartar a referência após remover.
2. Alternativa mais conservadora, sem mexer no ciclo de vida: em `resetAll()`,
   apagar as chaves (`clear()` mais remoção dos nós filhos) em vez de remover o
   próprio nó. Mantém o objeto válido.
3. Adicionar teste de regressão: `resetAll()` seguido de `load()` e de uma
   escrita deve funcionar. Um teste com `Preferences` em nó temporário cobre isso
   sem tocar no registro real do usuário.
4. Independentemente da correção, o app não deveria mostrar exceção crua em
   caixa nativa. Vale um handler que registre no `DiagnosticLog` e apresente
   mensagem no idioma e no tema do app.

### Correção aplicada

`DesktopUserStore` deixou de guardar o nó como `val`. Agora há um acessor que
verifica a validade (`nodeExists("")` lança exatamente quando o nó foi removido)
e readquire o nó quando necessário; `resetAll()` também substitui a referência
logo após remover. O parâmetro do construtor continua existindo para os testes,
e o caminho de re-aquisição usa `absolutePath()` do nó injetado — então um teste
com nó temporário recria **aquele** nó, não as preferências reais da máquina.

### Testes

Dois testes de regressão em `DesktopUserStoreTest`: `resetAll` seguido de leitura
e escrita, e `resetAll` chamado duas vezes.

**Comprovados contra o código antigo:** revertendo a correção, os dois falham com
`IllegalStateException: Node has been removed.` — o mesmo erro do relato. Com a
correção, os 30 testes do arquivo passam.

O `finally` do helper `withStore` passou a usar `runCatching` ao remover o nó:
um teste que exercita `resetAll` já removeu esse nó, e remover duas vezes lança.

---

## TAREFA-005 — Publicar o site para a prévia do link compartilhado funcionar

**Status:** pendente — depende de deploy manual do Cloudflare Pages
**Registrado em:** 2026-08-12

### O que falta

O código do compartilhamento está no repositório e funciona no aplicativo, mas a
**prévia no WhatsApp ainda não**. Verificado contra o site publicado:

```bash
curl -s "https://iptvburo.pages.dev/t/?id=movie:duna:2024&t=Duna&y=2024" \
  | grep -oE '<meta property="og:[^>]*>'
```

Retorna as tags da **página inicial** (`og:url` = `https://iptvburo.pages.dev/`,
título genérico), não as do título compartilhado. Ou seja: `site/functions/` não
foi publicado no projeto Pages ainda.

Consequência prática: um link compartilhado hoje chega no WhatsApp com a capa e o
texto genéricos do site, em vez da capa e da sinopse do filme. O link continua
funcionando — abre a página, e abre o aplicativo se estiver instalado.

### Como resolver

Publicar `site/` no Cloudflare Pages, garantindo que `site/` seja a raiz do
projeto para que `site/functions/` seja reconhecido como Pages Functions. Ver
`site/functions/README.md`, que documenta isso e traz o comando de verificação.

---

## TAREFA-003 — Republicar o release do GitHub após as correções

**Status:** ✅ CONCLUÍDO em 2026-08-12
**Solicitado em:** 2026-08-12

**Decisão do usuário:** apagar o `v2.0.0-alpha.1` de vez (não rebaixar), depois
de corrigir os bugs. Versão nova: `v2.0.0-alpha.2`.

### Pedido

Depois de corrigir BUG-001 e BUG-002, verificar que tudo funciona, **apagar a
versão publicada hoje no GitHub** e publicar uma nova que funcione.

### Por que não foi feito junto

Três motivos, todos a resolver antes de executar:

1. **`CLAUDE.md` proíbe** criar release ou tag sem instrução explícita. Este
   registro é a instrução — mas ela precisa ser confirmada no momento da
   execução, já que aqui ela chegou como parte de outra tarefa.
2. **Apagar um release publicado é destrutivo e externo.** Quem já baixou o
   `v2.0.0-alpha.1` mantém o binário; quem tiver o link passa a receber 404. Se a
   intenção for substituir o instalador mantendo o histórico, o caminho melhor é
   publicar `v2.0.0-alpha.2` e marcar o anterior como *pre-release*/*deprecated*
   em vez de excluir.
3. **Os bugs ainda não foram corrigidos.** BUG-001 e BUG-002 estão diagnosticados,
   não resolvidos. Publicar agora republicaria as mesmas falhas.

### Pré-condições para executar

- [ ] BUG-001 corrigido e verificado em instalação limpa
- [ ] BUG-002 corrigido, com teste de regressão de `resetAll()`
- [ ] Suíte de testes e `packageMsi` executados com sucesso
- [ ] Teste manual: instalar do zero, adicionar lista, redefinir, adicionar de novo
- [ ] Usuário confirma **excluir** vs **substituir** o release anterior
- [ ] Número da nova versão definido pelo usuário

### O que foi feito

Publicado o `v2.0.0-alpha.2` e **apagado** o `v2.0.0-alpha.1`, incluindo a tag,
conforme a decisão do usuário.

Ordem seguida de propósito: a nova versão foi publicada e verificada **antes** de
apagar a antiga, para nunca existir um intervalo sem download disponível.

Verificações antes de apagar:

- os dois assets subiram com `state=uploaded`;
- a URL de download responde `200`;
- os 8 primeiros bytes do arquivo publicado são idênticos aos do arquivo local e
  correspondem à assinatura de MSI (`d0cf11e0a1b11ae1`) — ou seja, o upload não
  corrompeu nada;
- o `SHA256SUMS.txt` publicado bate com o hash do build local
  (`fcab66edf0e0d63037caa67782a2757dea93c332571751d10671d73dbdf0329f`).

Depois de apagar: `alpha.1` responde `404`, `alpha.2` responde `200`, e a única
tag remota é `v2.0.0-alpha.2`.

**Somente Windows.** A `alpha.1` trazia também um APK de Android; a `alpha.2`
não, porque o trabalho de Android desta sessão não foi commitado nem testado.
Quem quiser o APK precisa da build de Android, que continua pendente.

---

## BUG-004 — `ChildProcessJobTest` falhando (4 testes)

**Status:** ✅ RESOLVIDO em 2026-08-12 — o fixture foi corrigido durante a sessão
**Observado em:** 2026-08-12, ao rodar a suíte do desktop

### Sintoma

`apps/desktop/src/test/kotlin/.../playback/ChildProcessJobTest.kt` falha em 4 de
4 testes:

```text
the job assignment must follow registration, not replace it: both defences apply
the fixture must stay alive; a child that exits immediately proves nothing   (x3)
```

O restante da suíte passa: **601 testes no desktop, 4 falhas — todas aqui** — e
**401 testes em `domain-model`, 0 falhas**.

### Contexto

O arquivo está **não rastreado** no git (`??`), ou seja, é trabalho em andamento
sobre o job object dos processos filhos do VLC, não algo que veio do
`codex/windows-clean-release`. Não tem nenhuma referência ao trabalho de
compartilhamento (verificado: 0 ocorrências de `TitleShareLink`,
`SingleInstance`, `ProtocolRegistration`, `ShareStrings`).

As mensagens sugerem que as falhas são do próprio fixture — o processo filho de
teste termina cedo demais para o teste observar o que pretende observar — e não
necessariamente um defeito no código de produção. Precisa ser confirmado por quem
está escrevendo esse teste.

### Resolução

Era o **fixture**, não o `ChildProcessJob`. O teste iniciava o processo filho com
`java -`, e o JVM responde `Unrecognized option: -` e sai imediatamente — então a
própria asserção de vitalidade do fixture falhava (`the fixture must stay alive`).
Foi trocado pelo *single-file source launcher*, com um `Sleeper.java` temporário
que bloqueia em `Thread.sleep(Long.MAX_VALUE)`.

Vale registrar que a asserção de vitalidade fez o trabalho dela: em vez de os
testes passarem contra um processo morto — que é como uma versão anterior deste
mesmo fixture se enganou — eles falharam alto.

Os 4 testes passam. Suíte completa: **1145 testes, 0 falhas**.
