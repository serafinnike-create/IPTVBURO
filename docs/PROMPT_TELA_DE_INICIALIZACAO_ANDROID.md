# Prompt — tela de inicialização do app Android (celular)

Este arquivo é o enunciado da tarefa. Leia-o inteiro antes de escrever código, e leia
`CLAUDE.md` e `docs/status/CURRENT_IMPLEMENTATION.md` antes dele.

O alvo é **o app de celular** (`apps/android-tv` rodando em telefone). A investigação abaixo
foi feita num aparelho real e cada número aqui foi medido, não estimado.

## Aparelho de referência

```text
Xiaomi 25028RN03Y  ·  Android 15
720x1640 px @ densidade 320  →  360 x 820 dp
```

Esse tamanho lógico importa: quase todo o defeito de performance vem de a parede de pôsteres
ter sido dimensionada para televisão e ser desenhada inteira num telefone.

## O problema, como o usuário o vê

Duas frases do usuário:

- *"tela de inicialização dá umas travadas"*
- *"fundo com capas aparece somente quando o perfil carrega"*

As duas observações estão corretas e são defeitos distintos, com causas distintas.

## Defeito 1 — a tela de boot trava (RESOLVIDO)

### Medição no aparelho, antes de qualquer mudança

```text
Total frames rendered: 80
Janky frames: 80 (100.00%)
Number Slow UI thread: 80
Number Frame deadline missed: 80
Number Missed Vsync: 78

50th percentile: 150ms      (alvo: 16ms  ->  ~6,6 fps)
90th percentile: 350ms
95th percentile: 1150ms
99th percentile: 1650ms

50th gpu percentile: 15ms
90th gpu percentile: 16ms   (GPU saudável)
```

Nenhum frame ficou dentro do orçamento; o mais rápido foi 93ms. O logcat confirma:

```text
Choreographer: Skipped 106 frames!  (também 71, 59, 52, 51, 45, 34)
HWUI: Davey! duration=1694ms  (também 1438, 1298, 1264, 1195, 1067, 1045, 943)
```

`Slow UI thread: 80/80` com GPU em 15–16ms prova que o custo é **composição na thread
principal**, não desenho. Um frame de 1694ms é a tela congelada por quase dois segundos —
é literalmente a travada relatada.

### A causa

`BuroCinematicBackdrop` em
`apps/android-tv/src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/CinematicBackdrop.kt`
desenha uma parede de capas:

- `rowCount = ceil(maxHeight / rowStep) + 2` → **7 linhas** em 820 dp
- cada `PosterStrip` repetia `repeat(6)` ciclos × `POSTERS_PER_CYCLE` (4) → **24 imagens por linha**
- total: **168 `AsyncImage`** compostos por frame

O `Row` do strip **não é lazy**: 6 ciclos × 512 dp = **3072 dp de largura numa tela de 360 dp**,
ou seja ~8,5 telas desenhadas para mostrar 1. Cerca de 88% das imagens nunca são vistas, mas
todas são compostas, medidas e desenhadas — com `rotationZ`, `scale 1.06` e três animações
infinitas rodando o tempo todo.

### O que já foi feito (commit `240350f`)

`posterCyclesFor(widthDp)` dimensiona os ciclos pela largura real, com piso 2 e teto 6, e os
`painterResource` foram içados para fora do laço. Resultado medido:

| | Antes | Depois |
|---|---|---|
| Imagens compostas | **168** | **56** |
| Frames travados | **100%** (80/80) | **79,8%** (95/119) |
| Frame mediano | **150ms** | **85ms** |

Testes novos em
`apps/android-tv/src/test/kotlin/com/lucasserafin94/iptvburo/ui/screens/PosterWallSizingTest.kt`.

### A segunda causa, que era a dominante (commit `40cc2ac`)

Reduzir os ciclos não bastou: a medição limpa depois dela ainda dava **98,3%** de frames
travados. O custo real estava em **onde a animação era lida**.

`firstProgress`, `secondProgress` e `thirdProgress` eram lidos com `by` no corpo do composable.
Ler um valor que anima ali torna **cada frame da animação uma recomposição** — e esta animação
nunca termina. A parede inteira, sete linhas e as ~56 capas, era recomposta a 60 Hz enquanto a
tela estivesse de pé.

O strip já aplicava o deslocamento dentro de um `graphicsLayer`, que só precisa do valor na
fase de desenho. Passar uma função que a própria camada chama tira a leitura da composição: a
parede é composta uma vez e a animação move apenas o desenho.

Medido em três cold starts consecutivos, num aparelho real:

| | Antes | Depois |
|---|---|---|
| Frames travados | **98,3%** (57/58) | **12,1% · 15,7% · 11,9%** |
| Frame mediano | **129ms** | **21 · 28 · 27 ms** |
| Frames desenhados em 6s | 58 | **165 · 153 · 160** |

O alvo de 16ms por frame ainda não é atingido em todos os frames, mas a tela deixou de ser um
slideshow: a mediana está dentro ou perto do orçamento e a proporção de frames travados caiu
por um fator de seis.

### Verificado também em geometria de televisão

O mesmo módulo roda em telefone e em TV, então a parede foi medida nas duas. Sem emulador de
TV à mão, o próprio aparelho foi forçado a reportar dimensões de televisão:

```bash
adb shell wm size 1920x1080   # 1280 x 720 dp, paisagem
adb shell wm density 240
# ... medir ...
adb shell wm size reset && adb shell wm density reset
```

| Geometria | Frames travados | Frame mediano |
|---|---|---|
| Celular, 360x820 dp | **12–16%** | 21–28ms |
| TV, 1280x720 dp | **24,3%** | 32ms |

A TV fica pior porque é 3,5x mais larga e `posterCyclesFor` pede mais ciclos — comportamento
esperado, não regressão. Confirmado por vídeo que a parede cobre a tela inteira nessa
proporção, sem faixas pretas nem lacunas.

### Se for preciso ir além

Nada disso foi verificado, e nenhum é obviamente necessário agora:

- reduzir `rowCount` no celular, aumentando o `rowStep`;
- verificar se o `crossfade(true)` do `ImageLoader` multiplica o custo por imagem;
- desenhar a parede uma vez numa camada e animar só o deslocamento.

Meça antes e depois com `dumpsys gfxinfo`, e use uma janela de 6 segundos: execuções curtas
demais rendem 18–21 frames e números que não se comparam com nada.

## Defeito 2 — capas reais só entram depois do perfil (corrigido)

### A causa

`MainViewModel.loadBootBackdrop` (por volta da linha 4220) recusava carregar sem perfil ativo:

```kotlin
if (sourceId == null || profile == null || profile.isKids) {
    mutableState.update { it.copy(bootBackdropUrls = emptyList()) }
    return
}
```

E só era chamado depois de `observeProfiles` assentar. Enquanto isso a parede caía nas quatro
imagens locais e trocava de golpe quando o perfil resolvia.

### O que já foi feito

`loadEarlyBootBackdrop` resolve o perfil lembrado (`onboardingPreferences.activeProfileId` +
`userLibraryRepository.getProfile`) **em paralelo com a abertura do Room**, via um `Deferred`
lazy iniciado no `init`. Decomposição medida do cold start:

| Fase | Antes | Depois |
|---|---|---|
| `init` → fontes emitidas (Room abrindo) | 2,80s | 2,69s |
| fontes → perfil resolvido | **0,97s** | **0s** (já pronto) |
| perfil → capas no estado | 0,23s | 0,89s |
| **total até as capas** | **4,01s** | **3,58s** |

A garantia de Kids está preservada e testada: sem prova positiva de perfil não-Kids, nada é
carregado — inclusive quando não há perfil lembrado. Ver `mayLoadEarlyBootBackdrop` e
`keepBootBackdropForArrivingProfile`, com 7 testes em `BootBackdropSelectionTest.kt`.

Também foi corrigido um efeito colateral em `observeProfiles`: a primeira emissão trocava
`activeProfile` de `null` para o perfil real, e a comparação por id tratava isso como troca de
perfil, **apagando** as capas recém-carregadas.

O gargalo restante são os ~2,7s de abertura do Room, anteriores a qualquer coisa que esta
correção controle. Se for atacado, será outra tarefa.

## Defeito 3 — RESOLVIDO: a parede nunca exibia as capas reais

### A causa

O Coil serializa o acesso ao cache de disco por uma **única via**
(`DiskLruCache` usa `limitedParallelism(1)`). A parede pede todas as suas capas de uma vez —
**58 requisições entraram no loader em menos de um sexto de segundo** — e elas enfileiravam
atrás dessa via única. Nenhuma completava antes de a tela de boot acabar.

Medição do interceptor do `ImageLoader`, no aparelho:

| | requisições que entram | que saem |
|---|---|---|
| Antes | **58** | **0** |
| Com cache de disco desligado (experimento) | 61 | 46 |
| Depois da correção | **58** | **41** |

Foi assim que a causa ficou provada: desligar o cache de disco fazia as capas aparecerem na
hora. A tela Home nunca teve o problema porque pede poucas imagens por vez — medido: 3 entram,
3 saem.

### A correção

`rememberWallRequest` em `CinematicBackdrop.kt` constrói a requisição **uma vez por URL**
(`remember`) e marca `diskCachePolicy(CachePolicy.DISABLED)`. A arte da parede é decorativa e
já vive no cache de memória enquanto a tela está de pé; as telas que navegam o catálogo
continuam cacheando normalmente.

Verificado por vídeo: a parede mostra capas reais do catálogo (*Louder Than Fear*,
*Maddie's Secret*, *O Convite*, *Monsters of God*) no lugar das quatro imagens locais.

### Duas teorias descartadas no caminho — não repita

1. **A URL espelhada não é o problema.** `http://file.gstaticontent.com//t/p/...` é legítima e
   responde **HTTP 200** (`type=image/webp size=39786`). O Coil 3.5.0 preserva a URL intacta.
   Documentado em `apps/desktop/src/test/kotlin/.../ArtworkUrlTest.kt`.

2. **O `OkHttpClient` não era o culpado.** Passar o cliente configurado do app para o
   `OkHttpNetworkFetcherFactory` não mudou nada: continuou 58 entrando e 0 saindo.

## Restrições

- Preserve a garantia de Kids. Um perfil infantil **nunca** pode ver capas do catálogo adulto
  na tela de carregamento, em nenhuma janela de tempo, nem por um frame.
- Não regrida o vertical de vídeo nem a experiência de TV. `apps/android-tv` roda em telefone
  **e** em televisão; a parede precisa continuar correta nas duas.
- Nada de segredo, playlist privada, URL assinada ou credencial no repositório. As URLs de
  capa citadas aqui são públicas e não carregam credencial.
- Mantenha PT-BR, EN, DE e IT.
- Mudanças pequenas, testáveis e reversíveis.

## Estado da árvore ao escrever este prompt

```text
234 testes, 1 skipped, 0 falhas, 0 erros
```

As correções desta investigação estão nos commits `240350f` e `40cc2ac`, que tocam:

- `apps/android-tv/src/main/kotlin/com/lucasserafin94/iptvburo/MainActivity.kt`
- `apps/android-tv/src/main/kotlin/com/lucasserafin94/iptvburo/ui/MainViewModel.kt`
- `apps/android-tv/src/main/kotlin/com/lucasserafin94/iptvburo/ui/screens/CinematicBackdrop.kt`
- `apps/android-tv/src/test/kotlin/com/lucasserafin94/iptvburo/ui/BootBackdropSelectionTest.kt`
- `apps/android-tv/src/test/kotlin/com/lucasserafin94/iptvburo/ui/screens/PosterWallSizingTest.kt` (novo)

Há outras mudanças na árvore, anteriores e alheias a esta investigação (`LivingHomeScreen.kt`,
`AppShellScreen.kt`, `RatingStrip.kt`, `strings.xml`, `settings.gradle.kts`,
`packages/webdav-client/`). Não as misture com este trabalho.

## Como medir

```bash
# frames
adb shell dumpsys gfxinfo com.lucasserafin94.iptvburo.debug reset
adb shell am start-activity -W -n com.lucasserafin94.iptvburo.debug/com.lucasserafin94.iptvburo.MainActivity
adb shell 'sleep 6; dumpsys gfxinfo com.lucasserafin94.iptvburo.debug'

# a tela, quadro a quadro (captura isolada engana numa tela animada)
adb shell 'screenrecord --time-limit 12 --size 720x1640 /sdcard/boot.mp4 & sleep 0.4; \
  am start-activity -n com.lucasserafin94.iptvburo.debug/com.lucasserafin94.iptvburo.MainActivity; wait'
adb pull /sdcard/boot.mp4
ffmpeg -i boot.mp4 -vf "fps=2,crop=720:420:0:200" fr_%02d.png
```

No Git Bash do Windows, prefixe comandos `adb` que usam caminhos `/sdcard/...` com
`MSYS_NO_PATHCONV=1`, senão o shell converte o caminho e o `adb pull` falha.

Cuidado com duas armadilhas que custaram tempo nesta investigação:

- **capturas isoladas enganam.** A tela é animada e o boot dura ~7s; um `screencap` pode cair
  no splash nativo do Android, no meio da transição, ou já na Home. Use vídeo.
- **o buffer do logcat rotaciona.** Se for correlacionar log com tela, capture tudo numa única
  execução e grave em arquivo, em vez de rodar `adb logcat -d` em comandos separados.

## Relatório obrigatório

Ao terminar, informe: o que foi realmente implementado; arquivos alterados; testes e builds
executados; resultados exatos (números de `gfxinfo` antes e depois); limitações e falhas;
riscos restantes; próximo passo recomendado.

Não afirme que a tela de carregamento está corrigida sem um vídeo mostrando as capas reais na
parede e um `gfxinfo` com a proporção de frames travados.
