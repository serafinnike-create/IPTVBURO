# BURO Cinematic System — contrato de implementação

- Data: 2 de agosto de 2026
- Escopo desta revisão: camada visual Windows e unificação de tokens no Android
- Contrato canônico de cor: `packages/design-tokens/tokens.json`

Este documento descreve o que **está implementado**, não o que é desejado. O
alvo completo continua sendo o [GDD 2.0](../gdd-v2/02-cinematic-design-system.md).

## 1. Origem única dos tokens

| Token | Windows | Android |
|---|---|---|
| Cor | `desktop/ui/BuroDesktopTheme.kt` → `BuroColors` | `ui/designsystem/BuroTokens.kt` → `BuroColors` |
| Espaçamento | `BuroSpacing` (base 8) | `BuroSpacing` (base 8) |
| Raio | `BuroRadius` | `BuroShapes` |
| Movimento | `BuroMotion` | `BuroMotionTokens` |
| Foco/hover | `BuroInteraction` | `BuroFocusTokens` |
| Tipografia | `BuroTypography` (Material 3) | `BuroTvTypography` (TV Material 3) |

Regra: nenhuma tela deve declarar `Color(0x…)` literal. Antes desta revisão
existiam três valores diferentes de "cor sobre o dourado"
(`0xFF03201D`, `0xFF08110F`, `0xFF071019`), nenhum deles pertencente à paleta.
Todos foram substituídos por `BuroColors.OnPrimary`.

## 2. Física de foco e hover — Windows

`desktop/ui/BuroInteractive.kt` implementa dois contratos:

- `BuroInteractiveSurface` — cards. Escala 1.045 (1.06 em cards compactos),
  anel luminoso, 160 ms, curva rápida na entrada e suave na saída. Reage a
  **hover e foco de teclado** pela mesma `MutableInteractionSource`, então mouse
  e `Tab` produzem a mesma afordância.
- `BuroInteractiveRow` — listas. Não escala: mover cada linha sob o ponteiro faz
  listas longas parecerem instáveis. Expressa estado por fundo e anel de foco.

Consequência prática: todo card, item de navegação e fonte na barra lateral
agora é focável por teclado. Antes nenhum deles era.

## 3. Grid da Home Windows

`HomeMetrics` resolve uma vez por tamanho de janela:

- `gutter` — **um único valor** usado pelo título da fileira e pelo
  `contentPadding` da `LazyRow`. Antes o título usava 34 dp e os cards 22 dp por
  meio de `Spacer`, e cada fileira ficava visivelmente desalinhada do próprio
  título;
- `heroHeight` — 52% da altura útil, limitado a 300–560 dp, conforme a faixa
  42–58% do GDD;
- `posterWidth` / `landscapeWidth` — 2:3 para filme e série, 16:9 para ao vivo;
- `contentPadding` vertical das fileiras reserva espaço para a escala de hover.
  Sem isso a `LazyRow` corta o topo e a base do card elevado.

## 4. Localização

`desktop/ui/DesktopStrings.kt` é uma `data class` com uma instância por idioma,
exposta por `LocalDesktopStrings`.

Modelada como `data class` e não como `Map<String, String>` de propósito: uma
tradução faltando vira erro de compilação em vez de `NoSuchElementException` em
runtime. A tabela anterior alocava quatro mapas a cada recomposição e cobria
nove strings; o restante da interface Windows era português fixo.

Cobertura atual: shell, Home, estados, diálogos e banners em pt-BR, en, de, it.
**Ainda não cobertos:** `XtreamWorkspace.kt` e `XtreamLoginDialog.kt`.

## 5. Preferências de acessibilidade — Android

`resolveBuroColorScheme(preferences)` aplica alto contraste e transparência
reduzida, e `ProvideBuroDesignSystem` publica o resultado em `LocalBuroColors`.

Os atalhos de cor em `ui/theme/IptvBuroTheme.kt` eram `val` de topo capturados
do esquema padrão na inicialização da classe. O esquema resolvido existia, mas
**não alcançava nenhuma tela**: alto contraste e transparência reduzida não
tinham efeito visual em lugar nenhum do aplicativo.

Agora são getters `@Composable @ReadOnlyComposable` que leem `BuroTheme.colors`.

Os nomes também mudaram, porque `Teal` apontava para o marfim e `Blue` para o
dourado:

| Antes | Agora | Valor |
|---|---|---|
| `Ink` | `BuroCanvas` | `canvas` |
| `InkSoft`, `Surface` | `BuroSurface` | `surface` |
| `SurfaceRaised` | `BuroSurfaceRaised` | `elevated` |
| `Teal` | `BuroAccent` | `brandSecondary` (marfim) |
| `Blue` | `BuroGold` | `brandPrimary` (dourado) |
| `White` | `BuroTextPrimary` | `textPrimary` |
| `Muted` | `BuroTextSecondary` | `textSecondary` |
| `Danger` | `BuroDanger` | `error` |

A renomeação não muda nenhum pixel no tema padrão; muda o que acontece quando o
usuário liga alto contraste.

## 6. Densidade e DPI no Windows

A janela é **per-monitor DPI aware**. Em um monitor a 125%, `containerSize`
medido foi 1920×991 px físicos com `density = 1.25`, ou seja 1536 dp de largura
lógica.

Isso importa para qualquer captura de tela de validação: um processo que não
declara consciência de DPI enxerga a área virtualizada e grava apenas cerca de
80% da largura da janela, sem indicar que houve corte. Capturas de validação
precisam chamar `SetProcessDPIAware` antes de medir a janela.

## 7. Catálogo e ficha Windows

### 7.1 Categorias

O painel lateral fixo de 220 dp foi substituído por um rail horizontal de chips
(`XtreamCategoryRail`). O GDD descarta tanto a aparência de painel administrativo
quanto gastar largura fixa com menu, e o painel custava a mesma largura em todas
as telas para um uso momentâneo. A busca cobre a procura precisa; o rail cobre a
navegação exploratória.

### 7.2 Grid

`XtreamCatalogGrid` usa `GridCells.Adaptive` com mínimo de 172 dp para pôster
2:3 e 250 dp para ao vivo 16:9. O card tem a **mesma forma do card da Home** —
arte, título e fatos abaixo — para que mover-se entre Home e catálogo não pareça
mudar de produto.

O grid e o rail voltam ao início quando o tipo de conteúdo, a categoria ou a
página mudam. Sem isso a lista reaproveita o deslocamento anterior e a página
nova abre já rolada.

### 7.3 Ficha

`XtreamItemDetail` deixou de ser um card centralizado. Pôster à esquerda,
título/fatos/ações à direita, tudo alinhado à esquerda. O layout anterior punha
pôster, título e todas as ações no eixo vertical central, o que lê como caixa de
diálogo e não como página sobre um título.

As ações passaram de botões empilhados em largura total para uma `FlowRow` com
largura natural — largura total fazia uma página sobre um filme parecer
formulário de configuração.

## 8. O que esta revisão não fez

- a barra lateral Windows continua sendo barra lateral; a BURO Ribbon do GDD 2.0
  não foi implementada no desktop;
- `XtreamLoginDialog.kt` e os blocos de elenco, episódios e EPG dentro de
  `XtreamWorkspace.kt` continuam com strings fixas em português;
- `CatalogWorkspace` (o caminho de playlist M3U local, em `DesktopApp.kt`) ainda
  usa o layout de três painéis. Só o caminho Xtream foi redesenhado;
- não há testes de screenshot/golden nem testes instrumentados de foco;
- **a Home, o catálogo e a ficha Windows redesenhados não foram validados com um
  catálogo real.** Foram verificados por compilação, gate de testes e execução do
  aplicativo com sessão vazia. Ver uma fileira preenchida, o hover de um card com
  arte real, o grid paginado e a ficha de um filme exige uma fonte autorizada;
- Android recebeu a correção de tokens e a unificação da cor da ação primária;
  suas telas não foram reestruturadas;
- nenhuma funcionalidade nova dos GDDs 3–7 foi implementada: Descobrir, Minha
  BURO, Pesquisa real, guia de EPG, Cofre Offline e Resilience Engine continuam
  ausentes.
