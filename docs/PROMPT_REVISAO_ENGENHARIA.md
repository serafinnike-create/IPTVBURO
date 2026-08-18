# Prompt — revisão de engenharia do IPTV BURO

Este arquivo é o enunciado de uma auditoria. Leia `CLAUDE.md` e
`docs/status/CURRENT_IMPLEMENTATION.md` antes dele.

## O que se pede

Uma revisão de engenharia do aplicativo — correção, arquitetura, desempenho e risco — que
produza **achados verificados**, não impressões. O produto está em `3.0.0`, publicado e em
uso por pessoas reais, então o critério é o de um app em produção.

## Regra que vale acima de todas

**Não relate nada que você não tenha verificado.** Um achado sem reprodução é ruído, e ruído
numa auditoria custa mais caro que silêncio: manda o leitor investigar o que não existe.

Para cada achado, entregue:

1. o arquivo e a linha;
2. **o cenário concreto** — quais entradas ou estado produzem a falha;
3. o que acontece de errado, observável;
4. por que o código atual permite isso;
5. o custo real se ficar como está.

Se não souber preencher o item 2, o achado não está pronto. Diga isso em vez de publicá-lo.

## Onde olhar, e o que já se sabe

O repositório tem histórico de defeitos recorrentes. Comece por eles, porque a probabilidade
de haver mais é alta e a de serem novos é baixa.

### Defeitos que já se repetiram

- **Filho rolável com altura ilimitada.** `verticalScroll`/`LazyColumn` que não é `weight(1f)`
  é medido contra infinito, desenha além da janela e não rola. Aconteceu três vezes: painel de
  Configurações (duas) e barra lateral. Ver `ScrollableSettingsUiTest`, `SidebarReachableUiTest`.
- **Barra de rolagem invisível.** A padrão do Compose é quase preta sobre fundo quase preto, e
  uma lista que rola sem indicador lê como lista curta. Três superfícies até agora.
- **`Row` espremendo o último rótulo.** Um `Row` dá a cada filho a largura que ele pede; o
  último fica com o resto, e um `Text` com poucos pixels quebra letra por letra em vez de
  cortar. Ver `SettingsLabelWrapUiTest`.
- **Rolagem aninhada no mesmo eixo.** `LazyColumn` dentro de `verticalScroll` não pode ser
  medido e lança em runtime. Ficou latente meses porque exigia duas playlists.
- **Medição intrínseca de `SubcomposeLayout`.** `DropdownMenu` mede o conteúdo
  intrinsecamente; um `LazyColumn` lá dentro derruba o app ao abrir.
- **Estado `Loading` que nunca é reposto.** Uma corrotina cancelada não chega à linha que
  limpa o status, o guard recusa toda tentativa seguinte, e a tela fica num spinner eterno.
  Encontrado **seis** vezes. Ver `CancelledLoaderResetTest`.

Verifique se cada um desses padrões existe em superfícies ainda não auditadas.

### Caminho quente

`CompactXtreamCatalog.matches` e `SessionXtreamRepository.page` rodam sobre dezenas de
milhares de linhas **a cada tecla digitada** na busca. A regra ali é: teste mais barato
primeiro, retorno antecipado, e nada que construa objeto antes dos filtros de coluna.
`itemAt` aloca um `XtreamCatalogItem` inteiro — 31 ms contra 10 ms em 41.698 linhas.

Procure trabalho por linha que não precisa acontecer, e meça antes de afirmar que melhorou.

### Correção de dados e atribuição

- Notas e selos devem dizer **de quem** é o número. Já houve um caso de a marca da Netflix
  aparecer ao lado de "Nota TMDb", porque a constante apontava para um logo de provedor.
- `0` de um provedor significa "não avaliado", não "zero de dez". Já apareceu como `★ 0,0`.
- Identidade persistida (favoritos, lembretes, progresso) é keyed em `ContentIdentity`, não em
  id de provedor — ids são numeração por lista e mudam quando o usuário troca de fonte.
  Qualquer mudança aí precisa de teste de regressão.

### Segurança e privacidade

O `CLAUDE.md` é explícito, e a auditoria deve confirmar que continua verdadeiro:

- nenhuma URL, token, cookie, usuário ou senha em log, `toString()` ou mensagem de erro;
- URLs autenticadas resolvidas o mais tarde possível, só em memória;
- nada de segredo, playlist privada ou conteúdo protegido no repositório;
- fixtures sintéticas ou públicas estáveis.

Há um precedente instrutivo: `RemotePlaylistSource` reescreve a `IOException` do JDK porque a
original cita a URL — **com a senha dentro**. Procure outros lugares onde uma exceção de
biblioteca possa vazar credencial.

### O que está sabidamente quebrado

`packages/domain-model` foi movido para `src/commonMain` sem converter as APIs de JVM:
`java.time.Duration`, `java.text.Normalizer`, `java.util.Locale`, `Math`, `@JvmInline`,
`putIfAbsent`. São **584 erros** em 17 arquivos, e os alvos iOS mais
`compileCommonMainKotlinMetadata` não compilam. Windows e Android não são afetados porque a
compilação JVM resolve tudo.

**Não** trate isso como achado — já é conhecido. Se for mexer, o padrão correto já existe em
`TextFolding.kt` (`expect`/`actual` com teste em `commonTest` verificando que JVM e iOS
concordam).

## Restrições

- Preserve o trabalho existente. Não reinicie arquitetura, não troque stack, não apague
  verticais funcionais.
- Mudanças pequenas, testáveis e reversíveis.
- `DesktopStrings` está perto do limite de 254 parâmetros de construtor da JVM — que já
  derrubou uma versão publicada. Texto novo vai em classe agrupada. Ver
  `StringsConstructorLimitTest`.
- Cinco idiomas: PT-BR, EN, ES, DE, IT. `EveryLanguageCompleteTest` falha se faltar um.
- Recursos visíveis são guiados por capabilities reais. Planejado não é implementado.

## Formato do relatório

Ordene por **custo para o usuário**, não por gravidade teórica. Um crash que exige duas
playlists é menos urgente que um rótulo ilegível que todos veem.

Para cada achado, diga também **o que não é**: se você não conseguiu reproduzir, ou se
depende de dados que não tem, declare isso na entrada. Uma auditoria que não distingue o
confirmado do suspeito não é utilizável.

Separe em três blocos:

1. **Confirmado** — reproduzido, com o cenário descrito.
2. **Provável** — o código permite, mas não foi reproduzido. Diga o que falta para confirmar.
3. **Observações** — dívidas e riscos que não são defeitos hoje.

Se a auditoria não encontrar nada numa área, diga isso explicitamente. "Varri os caminhos de
IO e não achei stream sem fechar" é informação; silêncio não é.

## Verificação obrigatória

Antes de entregar, execute e informe os resultados exatos:

- `:apps:desktop:test`
- `:apps:android-tv:testDebugUnitTest`
- as suítes de `packages/`
- `apps/samsung-tizen-tests` (`npm test`)

Nunca afirme que uma plataforma, player ou função está pronta sem evidência correspondente.
