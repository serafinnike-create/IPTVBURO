# IPTV BURO — GDD 3.0: Catalog Intelligence & Release Integrity

**Versão:** 3.0  
**Data:** 30 de julho de 2026  
**Status:** extensão obrigatória dos GDDs 1.0 e 2.0  
**Objetivo:** impedir que conteúdos antigos adicionados recentemente sejam tratados como lançamentos e criar uma organização temporal confiável, automática e explorável por ano.

---

## 1. Problema de produto

Listas IPTV costumam misturar dois conceitos diferentes:

1. **data de entrada na lista** — quando o provedor adicionou ou atualizou o item;
2. **data real de lançamento** — quando o filme, episódio ou série foi originalmente lançado.

Essa mistura produz uma experiência ruim. Um filme de 1998 adicionado hoje pode aparecer ao lado de um filme lançado em 2026, como se ambos fossem lançamentos atuais.

O IPTV BURO deve separar esses conceitos em toda a arquitetura, persistência, ordenação, busca e interface.

### 1.1 Regra fundamental

> Um conteúdo nunca entra em “Lançamentos 2026” apenas porque foi adicionado à playlist em 2026.

Para entrar em uma fileira de lançamentos do ano, o conteúdo deve possuir uma data real de lançamento confirmada ou inferida com confiança suficiente dentro daquele ano.

### 1.2 Resultado esperado

O usuário deve enxergar categorias claras:

- **Lançamentos 2026** — somente obras realmente lançadas em 2026;
- **Adicionados recentemente** — qualquer item novo na fonte, independentemente do ano;
- **Clássicos adicionados recentemente** — obras antigas que acabaram de entrar na lista;
- **Filmes por ano** — navegação por ano e década;
- **Data desconhecida** — itens sem metadados suficientes, nunca misturados silenciosamente aos lançamentos.

---

## 2. Nome do sistema

O módulo será chamado **BURO Temporal Intelligence**.

Componentes internos:

- `TemporalMetadataResolver`;
- `CatalogTemporalClassifier`;
- `ReleasePolicyEngine`;
- `YearIndex`;
- `TemporalConfidenceScorer`;
- `RecentlyAddedTracker`;
- `TemporalIntegrityReport`.

Nome de produto exibido ao usuário: **Organização inteligente por lançamento**.

---

## 3. Princípios obrigatórios

1. **Lançamento não é importação.**
2. O ano atual deve ser calculado dinamicamente pelo relógio e fuso do dispositivo; nunca fixar `2026` no código.
3. O app deve preservar a data original recebida da fonte e também a data canônica resolvida.
4. Metadados externos são uma fonte de enriquecimento, não uma verdade infalível.
5. Quando houver incerteza, o app deve ser conservador e não classificar como lançamento.
6. O usuário deve conseguir corrigir manualmente o ano.
7. Correções manuais têm precedência sobre inferência automática.
8. Mudanças de metadados devem ser auditáveis e reversíveis.
9. A classificação deve funcionar offline após o enriquecimento inicial.
10. Filmes, séries, temporadas e episódios devem possuir regras temporais próprias.

---

## 4. Vocabulário temporal

### 4.1 `firstSeenAt`

Momento em que o IPTV BURO viu o item pela primeira vez naquela fonte.

### 4.2 `lastSeenAt`

Último momento em que o item ainda estava presente na fonte.

### 4.3 `sourceAddedAt`

Data de adição fornecida pelo provedor, quando existir e for confiável.

### 4.4 `sourceUpdatedAt`

Data informada pela fonte para última atualização do item.

### 4.5 `originalReleaseDate`

Data original de lançamento mundial conhecida da obra.

### 4.6 `regionalReleaseDate`

Data de lançamento relevante para o país/região configurado pelo usuário.

### 4.7 `digitalReleaseDate`

Data de lançamento digital, quando conhecida.

### 4.8 `theatricalReleaseDate`

Data de estreia cinematográfica, quando conhecida.

### 4.9 `canonicalReleaseDate`

Data escolhida pelo `ReleasePolicyEngine` para ordenar e classificar a obra no contexto do usuário.

### 4.10 `canonicalReleaseYear`

Ano extraído de `canonicalReleaseDate`.

### 4.11 `metadataResolvedAt`

Data da última resolução de metadados.

### 4.12 `releaseConfidence`

Valor de `0.0` a `1.0` indicando confiança no ano/data de lançamento.

---

## 5. Modelo de dados

```kotlin
data class TemporalMetadata(
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val sourceAddedAt: Instant?,
    val sourceUpdatedAt: Instant?,
    val originalReleaseDate: LocalDate?,
    val regionalReleaseDate: LocalDate?,
    val theatricalReleaseDate: LocalDate?,
    val digitalReleaseDate: LocalDate?,
    val canonicalReleaseDate: LocalDate?,
    val canonicalReleaseYear: Int?,
    val metadataResolvedAt: Instant?,
    val releaseConfidence: Float,
    val releaseEvidence: List<TemporalEvidence>,
    val manualOverride: ManualTemporalOverride?
)
```

```kotlin
data class TemporalEvidence(
    val source: TemporalEvidenceSource,
    val value: String,
    val confidence: Float,
    val providerRecordId: String?,
    val observedAt: Instant
)
```

```kotlin
data class ManualTemporalOverride(
    val releaseDate: LocalDate?,
    val releaseYear: Int?,
    val editedAt: Instant,
    val reason: String?
)
```

### 5.1 Fontes de evidência

```text
MANUAL_OVERRIDE
PROVIDER_EXPLICIT_DATE
EXTERNAL_METADATA_EXACT_ID
EXTERNAL_METADATA_STRONG_MATCH
EXTERNAL_METADATA_WEAK_MATCH
PLAYLIST_ATTRIBUTE
XTREAM_FIELD
FILENAME_PATTERN
TITLE_PATTERN
PARENT_SERIES_METADATA
UNKNOWN
```

---

## 6. Integração de metadados

Criar uma interface independente de fornecedor:

```kotlin
interface MetadataProvider {
    suspend fun searchMovie(query: MovieMetadataQuery): List<MovieMetadataCandidate>
    suspend fun searchSeries(query: SeriesMetadataQuery): List<SeriesMetadataCandidate>
    suspend fun getMovieDetails(providerId: String, region: String?): MovieMetadataDetails
    suspend fun getSeriesDetails(providerId: String, region: String?): SeriesMetadataDetails
}
```

Uma implementação pode usar TMDB ou outro serviço licenciado/configurado. O código de domínio não pode depender diretamente de uma marca externa.

O fornecedor deve suportar, quando disponível:

- data principal de lançamento;
- datas por região;
- tipo de lançamento: première, cinema, digital, físico ou TV;
- título original;
- títulos alternativos;
- duração;
- gêneros;
- identificadores externos;
- data de estreia de séries;
- data de exibição de episódios.

A API Discover do TMDB aceita filtros por `primary_release_year` e intervalos de data, enquanto o endpoint de release dates distingue estreia, cinema, digital, físico e TV. Essa distinção deve inspirar o modelo, sem acoplar a arquitetura ao fornecedor. 

---

## 7. Política de resolução da data canônica

### 7.1 Ordem de precedência

1. correção manual do usuário;
2. identificador externo exato;
3. correspondência externa forte;
4. data explícita e válida do provedor;
5. correspondência externa moderada;
6. atributo estruturado da playlist;
7. inferência de nome com alta confiança;
8. desconhecido.

### 7.2 Seleção regional

Quando houver região configurada:

1. usar `regionalReleaseDate` correspondente;
2. se ausente, usar `digitalReleaseDate` quando o produto estiver no modo catálogo digital;
3. se ausente, usar `theatricalReleaseDate`;
4. por último, usar `originalReleaseDate`.

### 7.3 Thresholds iniciais

```text
0.90–1.00: confirmado
0.75–0.89: alta confiança
0.55–0.74: provável, não usar em “Lançamentos” sem outra evidência
0.01–0.54: fraco
0.00: desconhecido
```

Para aparecer em **Lançamentos do ano**, exigir:

```text
canonicalReleaseYear == selectedYear
AND releaseConfidence >= 0.75
AND temporalIntegrityStatus != CONFLICTED
```

---

## 8. Classificação temporal

```kotlin
enum class TemporalCatalogClass {
    RELEASED_THIS_YEAR,
    RELEASED_LAST_YEAR,
    RECENT_RELEASE,
    UPCOMING,
    NEWLY_ADDED_CURRENT_YEAR,
    NEWLY_ADDED_OLDER_TITLE,
    CATALOG_CURRENT_YEAR,
    CATALOG_BY_YEAR,
    CLASSIC,
    UNKNOWN_YEAR,
    CONFLICTED_METADATA
}
```

### 8.1 Regras centrais

```text
isNewlyAdded = now - firstSeenAt <= recentlyAddedWindow
isCurrentYearRelease = canonicalReleaseYear == currentYear
isOlderTitle = canonicalReleaseYear != null AND canonicalReleaseYear < currentYear
isClassic = canonicalReleaseYear != null AND canonicalReleaseYear <= currentYear - classicThresholdYears
```

Configurações padrão:

```text
recentlyAddedWindow = 30 dias
recentReleaseWindow = 365 dias
classicThresholdYears = 20 anos
minimumLaunchConfidence = 0.75
```

### 8.2 Matriz de exemplo

| Obra | Ano real | Adicionada | Lançamentos 2026 | Adicionados recentemente | Clássicos adicionados |
|---|---:|---|---|---|---|
| Filme A | 2026 | hoje | sim | sim | não |
| Filme B | 2025 | hoje | não | sim | não |
| Filme C | 1998 | hoje | não | sim | sim |
| Filme D | desconhecido | hoje | não | sim | não; fica em “ano desconhecido” |
| Filme E | 2026 | há 4 meses | sim | não | não |

---

## 9. Fileiras e páginas da interface

### 9.1 Home

A Living Home pode exibir:

1. **Lançamentos 2026** — ano atual dinâmico;
2. **Adicionados esta semana**;
3. **Clássicos que chegaram agora**;
4. **Filmes de 2025**;
5. **Escolha um ano**;
6. **Por década**;
7. **Datas não identificadas** — somente em biblioteca/configuração, não como destaque principal.

### 9.2 Página Filmes

Filtros rápidos:

```text
Todos | Lançamentos | Adicionados agora | 2026 | 2025 | 2024 | Décadas | Ano desconhecido
```

Filtros avançados:

- ano exato;
- intervalo de anos;
- década;
- data de adição;
- data de lançamento;
- gênero;
- idioma;
- país;
- qualidade;
- HDR;
- duração;
- classificação etária;
- confiança dos metadados.

### 9.3 Navegação por ano

Exibir uma timeline horizontal:

```text
2026  2025  2024  2023  2022  ...  2010s  2000s  1990s  Clássicos
```

Ao manter o foco em um ano:

- mostrar quantidade de títulos;
- gêneros predominantes;
- capa mosaico;
- opção “abrir ano”;
- carregamento paginado.

### 9.4 Story Page

Mostrar duas informações diferentes quando relevante:

```text
Lançamento: 14 de março de 1998
Adicionado à sua biblioteca: hoje
```

Nunca rotular o segundo campo como lançamento.

---

## 10. “Lançamentos” versus “Adicionados recentemente”

### 10.1 Lançamentos

Critério baseado em `canonicalReleaseDate`.

Ordenação padrão:

1. data real de lançamento descendente;
2. confiança descendente;
3. popularidade opcional;
4. título.

### 10.2 Adicionados recentemente

Critério baseado em `firstSeenAt` ou `sourceAddedAt` confiável.

Ordenação padrão:

1. data de entrada descendente;
2. data real de lançamento descendente;
3. título.

### 10.3 Clássicos adicionados recentemente

Critério:

```text
isNewlyAdded == true
AND canonicalReleaseYear <= currentYear - classicThresholdYears
AND releaseConfidence >= 0.75
```

### 10.4 Conteúdo antigo sem metadados

Não presumir que seja novo nem clássico. Colocar em:

```text
Adicionados recentemente
Ano desconhecido
```

Nunca em `Lançamentos`.

---

## 11. Inferência segura a partir do nome

O nome do arquivo pode conter ano, mas essa informação é apenas evidência auxiliar.

Exemplos perigosos:

- `Blade Runner 2049` — 2049 faz parte do título, não é ano de lançamento;
- `1917` — o título inteiro é um número;
- `2001 A Space Odyssey` — 2001 faz parte do título;
- remakes com mesmo nome;
- temporadas como `S2026`;
- resolução `2160p`;
- bitrate ou canal contendo números.

### 11.1 Regras

1. ignorar números seguidos de `p`, `i`, `kbps`, `fps`;
2. aceitar ano do título somente entre 1888 e `currentYear + 3`;
3. nunca aceitar apenas um número se ele também puder ser o título;
4. comparar título normalizado com candidatos externos;
5. usar duração, idioma e gênero para desempate;
6. exigir confirmação externa para confiança acima de `0.75` quando o ano veio apenas do nome;
7. manter o valor bruto para auditoria.

---

## 12. Filmes, séries, temporadas e episódios

### 12.1 Filmes

Usar a data canônica de lançamento do filme.

### 12.2 Séries

Não misturar três conceitos:

- ano de estreia da série;
- ano da temporada;
- data do episódio mais recente.

Fileiras possíveis:

- **Séries lançadas em 2026** — `firstAirDate` da série em 2026;
- **Novas temporadas de 2026** — temporada com primeira exibição em 2026;
- **Episódios novos** — episódios adicionados/exibidos recentemente;
- **Séries antigas atualizadas** — série antiga que recebeu temporada/episódio novo.

Uma série iniciada em 2015 com temporada de 2026 não deve ser chamada de “série lançada em 2026”. Deve aparecer em “Novas temporadas de 2026”.

### 12.3 Episódios

Guardar:

- `seriesFirstAirDate`;
- `seasonAirDate`;
- `episodeAirDate`;
- `episodeFirstSeenAt`.

---

## 13. Atualização e reclassificação

Ao atualizar uma fonte:

1. identificar itens por chave estável;
2. atualizar `lastSeenAt`;
3. definir `firstSeenAt` somente para itens realmente novos;
4. não redefinir `firstSeenAt` quando o nome muda levemente;
5. resolver metadados apenas quando necessário;
6. reclassificar itens com conflito;
7. atualizar índices por ano incrementalmente;
8. preservar correções manuais;
9. gerar relatório de mudanças.

### 13.1 Chave estável

Prioridade:

1. ID do provedor;
2. URL normalizada sem credenciais voláteis;
3. identificador externo;
4. fingerprint de título, duração, tipo e fonte;
5. fallback controlado.

### 13.2 Evitar falso “novo”

Se o provedor apenas alterar:

- capitalização;
- prefixo de qualidade;
- domínio do stream;
- token temporário;
- imagem;
- grupo da playlist;

não criar um novo item automaticamente se o fingerprint indicar que é o mesmo conteúdo.

---

## 14. Relatório de integridade temporal

Criar uma tela de diagnóstico:

```text
Integridade do catálogo

18.430 títulos analisados
12.280 datas confirmadas
3.420 datas com alta confiança
1.870 anos desconhecidos
620 conflitos encontrados
240 correções sugeridas
```

Ações:

- revisar conflitos;
- aceitar sugestões;
- corrigir ano;
- ignorar item;
- reprocessar metadados;
- exportar diagnóstico sem credenciais.

---

## 15. Configurações do usuário

### 15.1 Política de lançamentos

```text
Ano atual — somente obras do ano atual
Últimos 12 meses
Últimos 18 meses
Ano selecionado
Personalizado
```

O padrão do IPTV BURO será:

```text
Ano atual
```

Em 2026, a fileira será `Lançamentos 2026`. Em 1º de janeiro de 2027, passará automaticamente para `Lançamentos 2027`.

### 15.2 Região de lançamento

O usuário escolhe:

- automático pelo dispositivo;
- Brasil;
- Alemanha;
- Itália;
- outra região.

### 15.3 Tipo de data preferida

- lançamento digital;
- cinema;
- primeira data conhecida;
- data regional;
- automático.

---

## 16. Desempenho

Requisitos:

- classificação temporal não pode bloquear a interface;
- enriquecimento em background com limites de taxa;
- índices por ano armazenados localmente;
- consultas de fileiras devem ser paginadas;
- atualização incremental;
- cache de metadados;
- deduplicação antes de chamadas externas;
- cancelamento quando a fonte for removida;
- backoff em erro de API;
- nenhum segredo em logs.

Budgets iniciais em biblioteca já indexada:

```text
Abrir filtro de ano: < 150 ms
Trocar entre anos: < 100 ms percebidos
Consulta paginada local: < 80 ms
Reclassificação de um item: < 20 ms sem rede
```

---

## 17. Privacidade e legalidade

1. O IPTV BURO continua sendo apenas um player e organizador de fontes legais fornecidas pelo usuário.
2. Não enviar URL completa, username ou password a provedores de metadados.
3. Consultas externas devem usar somente título normalizado, ano candidato e atributos não sensíveis.
4. Respeitar licenças, atribuições e termos do fornecedor de metadados.
5. Permitir desabilitar enriquecimento externo.
6. Permitir apagar cache e metadados enriquecidos.
7. Correções manuais permanecem locais ou sincronizadas apenas com consentimento.

---

## 18. Critérios de aceitação obrigatórios

### AC-01 — clássico adicionado hoje

Dado um filme lançado em 1998 e adicionado hoje, ele:

- aparece em `Adicionados recentemente`;
- pode aparecer em `Clássicos adicionados recentemente`;
- não aparece em `Lançamentos 2026`.

### AC-02 — lançamento real do ano

Dado um filme com `canonicalReleaseYear = 2026` e confiança `>= 0.75`, ele aparece em `Lançamentos 2026` mesmo que tenha sido adicionado meses depois.

### AC-03 — filme do ano anterior

Um filme de 2025 adicionado hoje não aparece em `Lançamentos 2026`.

### AC-04 — ano desconhecido

Um item sem data confirmada nunca aparece em lançamentos.

### AC-05 — título numérico

`1917` não é automaticamente classificado como filme de 1917 apenas pelo nome.

### AC-06 — título com ano futuro

`Blade Runner 2049` não recebe ano 2049 sem correspondência confirmada.

### AC-07 — atualização da playlist

Uma alteração de token da URL não redefine `firstSeenAt` nem transforma o item em recém-adicionado.

### AC-08 — mudança de ano

Na virada do ano, a fileira muda automaticamente para o novo ano sem atualização do aplicativo.

### AC-09 — correção manual

Uma correção manual persiste após atualização da fonte e nova resolução automática.

### AC-10 — séries

Uma série iniciada em 2015 com temporada em 2026 aparece em `Novas temporadas de 2026`, não em `Séries lançadas em 2026`.

### AC-11 — região

Quando há data regional confirmada, a política configurada utiliza essa data para classificar e ordenar.

### AC-12 — offline

Após indexação, filtros por ano funcionam sem internet.

---

## 19. Testes mínimos

### Unitários

- cálculo de ano atual;
- política de data canônica;
- thresholds de confiança;
- classificação temporal;
- detecção de clássicos;
- janela de recém-adicionados;
- títulos numéricos;
- remakes;
- mudança de região;
- correção manual;
- fingerprint estável.

### Integração

- importação M3U;
- importação Xtream;
- enriquecimento de metadados;
- cache;
- atualização da fonte;
- reconstrução incremental do `YearIndex`;
- migração do banco existente.

### Interface

- fileira de lançamentos;
- fileira de adicionados;
- seletor de ano;
- décadas;
- ano desconhecido;
- Story Page com as duas datas;
- navegação completa por D-pad.

---

## 20. Migração do banco

A implementação deve ser aditiva.

1. criar novas colunas/tabelas temporais;
2. migrar itens existentes com `firstSeenAt = importedAt` quando disponível;
3. não inventar `canonicalReleaseYear`;
4. marcar itens antigos como `NEEDS_TEMPORAL_RESOLUTION`;
5. reprocessar em lotes pequenos;
6. manter app utilizável durante a migração;
7. permitir rollback seguro.

---

## 21. Roadmap de implementação

### Fase 1 — domínio e persistência

- modelos temporais;
- migração Room;
- `RecentlyAddedTracker`;
- `YearIndex`;
- regras de classificação;
- testes unitários.

### Fase 2 — interface básica

- `Lançamentos {ano}`;
- `Adicionados recentemente`;
- filtro por ano;
- década;
- Story Page com datas separadas.

### Fase 3 — resolução inteligente

- `MetadataProvider`;
- matching;
- score de confiança;
- região;
- tipos de lançamento;
- conflitos.

### Fase 4 — séries e episódios

- série, temporada e episódio;
- novas temporadas;
- episódios recentes;
- séries antigas atualizadas.

### Fase 5 — relatório e controles

- integridade temporal;
- correções manuais;
- políticas configuráveis;
- reprocessamento seletivo.

---

## 22. Backlog priorizado

### P0

- separar `firstSeenAt` de `canonicalReleaseDate`;
- `Lançamentos {currentYear}` confiável;
- `Adicionados recentemente`;
- filtro por ano;
- não classificar data desconhecida como lançamento;
- migração segura;
- testes dos critérios AC-01 a AC-10.

### P1

- integração com fornecedor de metadados;
- região;
- décadas;
- clássicos adicionados;
- conflitos;
- correção manual;
- séries e temporadas.

### P2

- relatório avançado;
- múltiplos fornecedores;
- sugestões coletivas opcionais e anônimas;
- enriquecimento local com modelo leve;
- coleções históricas automáticas.

---

## 23. Instrução final ao Codex

Este GDD não substitui os GDDs 1.0 e 2.0. Ele adiciona uma camada obrigatória de inteligência temporal.

O Codex deve:

1. auditar o modelo atual de catálogo;
2. localizar onde a aplicação confunde data de importação com lançamento;
3. criar um ADR para a migração temporal;
4. implementar primeiro o domínio e os testes;
5. preservar código útil existente;
6. não hardcodar 2026;
7. não depender de uma API externa para a navegação básica;
8. não colocar itens de ano desconhecido em lançamentos;
9. manter build, testes e navegação por D-pad funcionando;
10. entregar commits incrementais e documentados.

O objetivo final é simples e obrigatório:

> Quando um provedor adicionar hoje um filme antigo, o IPTV BURO deve reconhecer que ele é novo na biblioteca, mas antigo na história do cinema.
