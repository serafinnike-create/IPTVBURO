# Prompt — melhorar a tela de carregamento do Windows

Este arquivo é o enunciado da tarefa. Leia-o inteiro antes de escrever código, e leia
`CLAUDE.md` e `docs/status/CURRENT_IMPLEMENTATION.md` antes dele.

## O problema, como o usuário o vê

A tela de abertura mostra o logo, o texto "Baixando a lista de filmes…" e uma barra que
**para em 80% e fica lá**. Numa lista de quarenta mil títulos isso dura dezenas de segundos,
e durante esse tempo a tela não distingue "está trabalhando" de "travou". O usuário não sabe
o que está sendo baixado, quanto falta, nem se vale esperar.

Duas frases resumem o pedido:

- *"user precisa saber o que está carregando"*
- *"barra de carregamento precisa ficar carregando, não ficar parada em 80%"*
- *"velocidade de download na tela de carregamento quando está baixando também ajuda o user
  a identificar a demora"*

## Por que ela para

Em `DesktopAppState.loadDailyHome` (por volta da linha 4487) o progresso é reportado em
**três marcos fixos**:

```kotlin
onCatalogueStage(0.75f, "Baixando a lista de filmes…")
latestSummary = xtreamRepository.loadCatalog(XtreamContentType.MOVIE)

onCatalogueStage(0.88f, "Baixando a lista de séries…")
latestSummary = xtreamRepository.loadCatalog(XtreamContentType.SERIES)

onCatalogueStage(0.96f, "Montando a tela inicial…")
```

`loadCatalog` é síncrono e não reporta nada enquanto roda. O salto de `0.75` para `0.88`
cobre o download inteiro do catálogo de filmes — a parte mais lenta de todo o arranque —
e é por isso que a barra congela. O comentário no código já reconhece a intenção ("They
report themselves so the splash does not sit on one number for the whole wait"), mas a
granularidade de três marcos não a cumpre.

## O que a tarefa deve entregar

### 1. Progresso real, não marcos

`SessionXtreamRepository.loadCatalog` precisa aceitar um callback de progresso e chamá-lo
enquanto lê. O parser já é streaming (`packages/playlist-parser`, e
`XtreamCategoryStreamParser` no cliente), então há pontos naturais para reportar: itens
processados, bytes lidos, ou ambos.

Prefira **contagem de itens** quando o total for conhecido e **bytes** quando não for. Se
nenhum dos dois for confiável, veja o item 4.

### 2. Dizer o que está acontecendo

A mensagem deve nomear a etapa **e o seu tamanho**, não só a etapa. Algo como:

```
Baixando a lista de filmes…  12.480 de 41.698
```

O usuário deve conseguir responder "quanto falta?" olhando a tela.

### 3. Velocidade de transferência

Mostrar a taxa enquanto baixa — `2,4 MB/s`, formatada como o resto do app formata tamanhos
(veja `formatBytes` em `CachePanel.kt`, que já existe e deve ser reusado).

Calcule sobre uma janela deslizante curta, não sobre a média desde o início: a média torna-se
insensível e deixa de refletir uma queda de rede, que é exatamente o momento em que o usuário
quer saber. Não mostre a taxa quando não houver transferência em curso.

**Não estime tempo restante** a menos que a taxa esteja estável — um "faltam 3 minutos" que
oscila é pior que nenhum número.

### 4. A barra nunca pode ficar imóvel

Esta é a exigência central, e vale mesmo quando não há progresso mensurável.

Quando a etapa souber o seu tamanho, a barra é determinada. Quando **não** souber, ela deve
virar **indeterminada** — animada, sem número — em vez de ficar parada num valor. Uma barra
que não se move comunica "travou", que é uma mentira sobre o estado do app.

Regra prática: **se o progresso não mudar por mais de ~1,5 s, a barra deve estar animada.**

### 5. Não regredir o que já está certo

- `SplashScreen.kt` já tem uma animação de varredura (`rememberInfiniteTransition`) e um
  fundo de pôsteres. Preserve a identidade visual; a tarefa é sobre informação, não sobre
  redesenhar a marca.
- A tela é traduzida. **Todos** os textos novos vão para as cinco línguas em
  `DesktopStrings.kt` (PT-BR, EN, ES, DE, IT) — há um teste reflexivo,
  `EveryLanguageCompleteTest`, que falha se faltar alguma.
- `DesktopStrings` está perto do limite de 254 parâmetros de construtor da JVM. **Não**
  adicione campos na classe raiz: use uma classe agrupada, como `SplashStrings`. Ver
  `StringsConstructorLimitTest`, que explica por que isso já derrubou uma versão publicada.
- Nada de URL, credencial ou nome de servidor na tela nem em log.

## Restrições

- O callback de progresso é chamado de uma thread de IO. Não toque em estado do Compose
  diretamente lá; siga o padrão que `loadDailyHome` já usa.
- Reportar progresso não pode custar mais que o trabalho reportado. Não emita um evento por
  item num catálogo de 41.698 — limite a frequência (por tempo ou a cada N itens).
- O arranque a frio já é o caminho mais lento do app. Meça antes e depois, e informe os dois
  números: se a instrumentação atrasar o arranque, ela não vale.

## Como saber que ficou pronto

1. Com uma lista real (dezenas de milhares de itens), a barra **avança visivelmente** durante
   o download em vez de saltar de 75% para 88%.
2. A tela nomeia a etapa, mostra a contagem e a velocidade enquanto baixa.
3. Em qualquer etapa sem tamanho conhecido, a barra fica animada, não parada.
4. As cinco línguas passam em `EveryLanguageCompleteTest`.
5. `:apps:desktop:test` passa inteiro.
6. Um teste novo cobre a regra do item 4 — o defeito relatado foi uma barra imóvel, então é
   essa a propriedade que precisa ficar fixada, não a aparência.

## Relatório obrigatório

Ao terminar, informe o que foi realmente implementado, arquivos alterados, testes e builds
executados com resultados exatos, limitações, riscos restantes e o próximo passo — conforme
`CLAUDE.md`.
