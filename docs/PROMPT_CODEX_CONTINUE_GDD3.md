# Prompt para o Codex — Continuação com GDD 3.0

Continue o projeto atual do IPTV BURO sem reiniciar, apagar ou substituir código útil.

Leia integralmente antes de alterar o código:

- `docs/GDD_IPTV_BURO.md`
- todos os capítulos em `docs/gdd/`
- `docs/GDD_2_REVOLUTIONARY_EXPERIENCE.md`
- todos os capítulos em `docs/gdd-v2/`
- `docs/GDD_3_CATALOG_RELEASE_INTELLIGENCE.md`

## Objetivo desta continuação

Implementar a fundação do **BURO Temporal Intelligence**, separando de forma definitiva:

- data em que um item apareceu na playlist;
- data em que a obra foi realmente lançada.

Um filme antigo adicionado hoje deve aparecer em `Adicionados recentemente`, mas nunca em `Lançamentos do ano`.

## Regras inegociáveis

1. Não hardcodar `2026`; usar o ano atual do dispositivo e fuso configurado.
2. Não usar `firstSeenAt`, `importedAt`, `sourceAddedAt` ou qualquer data de atualização como se fosse data de lançamento.
3. Itens sem ano confirmado não podem aparecer em `Lançamentos`.
4. Correções manuais têm precedência sobre inferência automática.
5. O app deve continuar funcionando offline após indexação.
6. Não enviar URLs, usernames ou passwords para serviços externos de metadados.
7. Preservar a arquitetura, navegação por D-pad, build e testes existentes.
8. Implementar em commits pequenos e verificáveis.

## Primeira tarefa — auditoria

Antes de implementar:

1. examine o modelo atual de catálogo e banco;
2. identifique todos os campos usados para data, ano, ordenação e “novidades”;
3. encontre qualquer lugar em que data de importação seja tratada como lançamento;
4. registre o resultado em `docs/adr/ADR-TEMPORAL-CATALOG-MODEL.md`;
5. descreva a migração necessária;
6. liste riscos de compatibilidade.

Não comece reescrevendo telas.

## Segunda tarefa — domínio temporal

Crie ou adapte os seguintes conceitos:

```text
TemporalMetadata
TemporalEvidence
ManualTemporalOverride
TemporalMetadataResolver
CatalogTemporalClassifier
ReleasePolicyEngine
YearIndex
RecentlyAddedTracker
TemporalConfidenceScorer
```

Campos mínimos:

```text
firstSeenAt
lastSeenAt
sourceAddedAt
sourceUpdatedAt
originalReleaseDate
regionalReleaseDate
theatricalReleaseDate
digitalReleaseDate
canonicalReleaseDate
canonicalReleaseYear
metadataResolvedAt
releaseConfidence
manualOverride
```

## Terceira tarefa — persistência e migração

1. criar migração Room aditiva;
2. preservar itens existentes;
3. usar data de importação existente apenas como `firstSeenAt` quando apropriado;
4. não inventar ano de lançamento;
5. marcar registros antigos como pendentes de resolução;
6. reprocessar em lotes;
7. criar testes de migração;
8. garantir rollback seguro.

## Quarta tarefa — classificação mínima P0

Implementar:

- `Lançamentos {currentYear}`;
- `Adicionados recentemente`;
- `Clássicos adicionados recentemente`;
- filtro por ano;
- agrupamento por década;
- `Ano desconhecido`;
- índice local por ano.

Critério para `Lançamentos {currentYear}`:

```text
canonicalReleaseYear == currentYear
AND releaseConfidence >= 0.75
AND status != CONFLICTED
```

## Quinta tarefa — interface

Sem desmontar o GDD 2.0, adicione:

1. fileira `Lançamentos {currentYear}` na Living Home;
2. fileira separada `Adicionados recentemente`;
3. fileira `Clássicos que chegaram agora`;
4. seletor horizontal de anos;
5. filtros por década;
6. Story Page mostrando separadamente:
   - `Lançamento`;
   - `Adicionado à sua biblioteca`;
7. estado `Ano desconhecido` sem informação enganosa.

Toda a interface deve funcionar integralmente por controle remoto e D-pad.

## Sexta tarefa — metadados

Crie um contrato `MetadataProvider` desacoplado de fornecedor.

Não integrar diretamente uma marca externa no domínio.

A primeira implementação pode usar um provider configurável, mas deve:

- buscar filme e série;
- resolver identificador exato quando disponível;
- usar título, ano candidato, duração e idioma para matching;
- suportar região;
- guardar evidências e confiança;
- operar com cache;
- respeitar rate limits;
- funcionar sem rede usando dados já resolvidos.

## Casos de teste obrigatórios

Crie testes automatizados para todos:

1. filme de 1998 adicionado hoje não entra em lançamentos;
2. filme de 2026 confirmado entra em lançamentos 2026;
3. filme de 2025 adicionado hoje entra apenas em adicionados recentemente;
4. item sem ano não entra em lançamentos;
5. `1917` não é interpretado automaticamente como ano;
6. `Blade Runner 2049` não recebe ano 2049 pelo título;
7. mudança de token da URL não transforma item em novo;
8. virada do ano atualiza automaticamente o título da fileira;
9. correção manual persiste após refresh;
10. série iniciada em 2015 com temporada de 2026 entra em novas temporadas, não em séries lançadas em 2026;
11. filtros por ano funcionam offline;
12. região altera a política de data quando há evidência válida.

## Ordem de execução

1. auditoria e ADR;
2. modelos e interfaces;
3. migração e persistência;
4. regras de classificação;
5. testes unitários;
6. `YearIndex`;
7. queries do catálogo;
8. interface mínima;
9. provider de metadados;
10. otimização e testes de integração.

## Entrega esperada da primeira iteração

A primeira iteração termina somente quando:

- build está verde;
- migração está testada;
- classificação temporal possui testes;
- existem queries separadas para lançamentos e recém-adicionados;
- nenhum item desconhecido entra em lançamentos;
- a interface mostra pelo menos as duas fileiras distintas;
- o seletor de ano funciona com dados locais;
- documentação foi atualizada;
- o Codex apresenta um resumo objetivo dos arquivos alterados, testes executados e pendências.

Não implemente pagamentos, sincronização em nuvem ou novas plataformas nesta iteração. O foco é corrigir a inteligência temporal do catálogo antes de expandir o produto.
