# GDD 4.0 — 03. Integridade de playlist, Xtream, EPG e persistência

## 1. Objetivo

Impedir que uma fonte incompleta, malformada ou temporariamente vazia destrua um catálogo que estava funcionando.

Princípio central:

> Atualização nova só substitui o snapshot anterior depois de download, parsing, validação e persistência concluídos com sucesso.

---

## 2. Pipeline transacional de importação

```text
SCHEDULED / USER_TRIGGERED
→ ACQUIRE_REFRESH_LEASE
→ DOWNLOAD_TO_TEMP
→ VERIFY_SIZE_AND_TYPE
→ DECOMPRESS_IF_NEEDED
→ STREAM_PARSE
→ NORMALIZE
→ VALIDATE
→ BUILD_DIFF
→ WRITE_STAGING_TABLES
→ VERIFY_STAGING
→ ATOMIC_SWAP
→ REINDEX
→ CLEAN_TEMP
→ PUBLISH_RESULT
```

Qualquer falha antes de `ATOMIC_SWAP` mantém o snapshot anterior.

---

## 3. Download seguro

### 3.1 Limites

Configurações por tipo:

- tamanho máximo de playlist;
- tamanho máximo de EPG comprimido;
- tamanho máximo descomprimido;
- tempo de conexão;
- tempo total;
- taxa mínima observada;
- número máximo de redirects;
- host permitido após redirect;
- limite de expansão para evitar zip bomb.

### 3.2 Arquivos temporários

- nome aleatório;
- diretório privado;
- permissões restritas;
- cleanup após cancelamento/crash;
- não usar nome da fonte com credenciais;
- checksum opcional;
- nunca ler arquivo inteiro na memória por padrão.

### 3.3 Conteúdo inesperado

Detectar:

- HTML de login;
- JSON de erro;
- texto de bloqueio;
- arquivo vazio;
- gzip truncado;
- charset inválido;
- conteúdo muito menor que o histórico sem motivo.

Uma lista vazia não substitui automaticamente uma lista válida. Exigir confirmação ou múltiplas atualizações consistentes.

---

## 4. Parser M3U tolerante

### 4.1 Requisitos

- streaming/line-by-line;
- UTF-8 e BOM;
- finais de linha diferentes;
- linhas muito longas com limite;
- atributos em ordem arbitrária;
- aspas ausentes ou inconsistentes;
- `#EXTGRP` opcional;
- headers globais e por item;
- URLs relativas apenas quando base confiável existir;
- cancelamento cooperativo;
- progresso por bytes, não por número total desconhecido.

### 4.2 Estratégia por item

```text
VALID
VALID_WITH_WARNINGS
SKIPPED_INVALID
QUARANTINED
```

Um item ruim não deve invalidar toda a lista, exceto quando o documento inteiro não é uma playlist.

### 4.3 Quarentena

Guardar apenas dados seguros e redigidos:

- número da linha;
- tipo de problema;
- atributos sem segredo;
- hash estável do item;
- amostra limitada sem URL completa.

O usuário pode ver “27 itens ignorados” e abrir relatório.

---

## 5. Xtream-compatible resiliente

### 5.1 Adaptador por capacidade

Não assumir que todos os servidores respondem exatamente igual.

```text
XtreamCapabilities
- supportsUserInfo
- supportsLiveCategories
- supportsVodCategories
- supportsSeriesCategories
- supportsPagination
- supportsEpg
- supportsCatchup
- supportsShortEpg
- schemaFingerprint
```

### 5.2 Schema drift

- aceitar números como string quando seguro;
- campos opcionais ausentes não quebram parser;
- campos essenciais inválidos colocam item em quarentena;
- manter fixtures por variação conhecida;
- registrar fingerprint do schema;
- nunca executar código ou HTML recebido.

### 5.3 Atualização parcial

Live, VOD, séries e EPG são domínios independentes. Se séries falham, canais e filmes válidos podem ser atualizados, desde que a UI mostre o estado parcial.

### 5.4 Paginação

- detectar repetição de página;
- detectar cursor que não avança;
- limitar páginas;
- deduplicar IDs;
- preservar snapshot anterior em loop ou inconsistência.

---

## 6. Identidade estável de conteúdo

Não depender somente de URL, porque tokens mudam.

### 6.1 Chaves

```text
sourceItemId
providerStableId
tvgId
normalizedTitle
seasonEpisode
releaseYear
contentType
sourceId
```

### 6.2 Matching

Ordem recomendada:

1. ID estável confirmado;
2. ID da fonte + tipo;
3. URL canonicalizada sem segredo variável, quando seguro;
4. fingerprint de metadados;
5. matching probabilístico conservador.

Não mesclar automaticamente quando confiança for baixa.

---

## 7. Diff de catálogo

Cada refresh produz:

```text
ADDED
UPDATED_METADATA
UPDATED_STREAM
UNCHANGED
MISSING_ONCE
REMOVED_CONFIRMED
CONFLICTED
```

### 7.1 Remoção em duas fases

- primeira ausência: `MISSING_ONCE`;
- múltiplas ausências ou confirmação explícita: `REMOVED_CONFIRMED`;
- manter favoritos, histórico e correções manuais;
- item removido pode ser ocultado, não necessariamente apagado imediatamente.

### 7.2 Proteção contra fonte vazia

Se a nova quantidade cai drasticamente:

- calcular percentual;
- comparar histórico;
- verificar resposta de erro disfarçada;
- manter snapshot anterior;
- pedir confirmação antes de aplicar queda extrema.

---

## 8. XMLTV/EPG

### 8.1 Parsing

- parser streaming;
- proteger contra entidades externas/XXE;
- limitar profundidade e tamanho;
- suportar `.xml` e `.xml.gz`;
- validar datas sem exigir que todo campo opcional exista;
- preservar timezone original;
- normalizar para `Instant` internamente;
- guardar zona/offset recebido como evidência.

### 8.2 Regras de tempo

O XMLTV permite timezone explícito e define comportamento quando ausente. O app deve:

- seguir o formato;
- detectar padrões que sugerem fonte incorreta;
- não aplicar timezone do aparelho duas vezes;
- considerar horário de verão;
- oferecer offset manual por fonte;
- exibir quando um horário foi corrigido manualmente.

### 8.3 `programme` sem `stop`

Política:

1. usar `stop` quando válido;
2. senão, usar início do próximo programa do mesmo canal;
3. senão, duração informada;
4. senão, duração padrão apenas para UI, marcada como estimativa;
5. nunca persistir estimativa como dado original.

### 8.4 Overlap e gaps

- intervalos são tratados como `[start, stop)`;
- detectar sobreposição;
- não apagar programas automaticamente;
- selecionar programa “agora” por política documentada;
- mostrar conflito no diagnóstico;
- gap pode aparecer como “Sem programação”.

### 8.5 Mapeamento de canais

Camadas:

```text
manual override
exact tvg-id
exact provider ID
alias table
normalized name + country + language
logo fingerprint opcional
fuzzy match com aprovação
unmapped
```

Correção manual sempre vence automação.

### 8.6 EPG stale

Métricas:

- cobertura inicial/final;
- percentual de canais mapeados;
- idade do arquivo;
- programas no passado/futuro;
- conflitos;
- timezone suspeito.

A UI deve mostrar:

```text
Guia atualizado há 3 horas
Cobertura: hoje até domingo
89% dos canais associados
```

---

## 9. Banco e migração

### 9.1 Tabelas de staging

Importações escrevem em tabelas de staging ou em versão de snapshot.

```text
catalog_snapshot
catalog_item
source_item
stream_variant
epg_snapshot
epg_channel
epg_program
import_run
import_warning
manual_override
```

### 9.2 Migração

- backup lógico antes de migração destrutiva;
- migration tests com bancos de versões antigas;
- rollback ou modo read-only;
- nunca resetar banco automaticamente em produção;
- credenciais em storage separado e criptografado;
- índices criados de forma controlada.

### 9.3 Consistência

Constraints:

- IDs internos não dependem de título;
- foreign keys;
- timestamps em UTC/Instant;
- conteúdo original separado de valor normalizado;
- override manual separado do dado da fonte;
- flags de inferência e confiança.

---

## 10. Cache

### 10.1 Tipos

```text
DISPOSABLE_IMAGE
DISPOSABLE_METADATA
PLAYLIST_TEMP
EPG_TEMP
CATALOG_DERIVED
USER_STATE_CRITICAL
```

Somente caches descartáveis são apagados automaticamente por falta de espaço.

### 10.2 Imagens

- limite por tier de dispositivo;
- tamanhos apropriados para TV;
- progressive loading;
- placeholder estável;
- retry separado de playback;
- URL de imagem nunca bloqueia catálogo;
- invalidar por ETag/Last-Modified quando disponível.

---

## 11. Offline e atualização

- app abre com último snapshot válido;
- refresh ocorre em background;
- falha de refresh não remove biblioteca;
- usuário pode cancelar;
- estado mostra “dados salvos”;
- ações que exigem rede ficam claras;
- progresso e favoritos continuam locais;
- fila de sincronização é idempotente.

---

## 12. Relatório de integridade

```text
Fonte: Sala
Última atualização válida: 30/07/2026 15:42
Itens recebidos: 18.412
Itens válidos: 18.201
Itens ignorados: 34
Duplicados agrupados: 177
Queda em relação ao snapshot anterior: 0,8%
EPG mapeado: 86%
Timezone suspeito: não
Estado: saudável
```

Não mostrar host completo ou credenciais.

---

## 13. Fixtures obrigatórias

- M3U válida pequena;
- M3U com BOM;
- CRLF/LF mistos;
- `#EXTINF` quebrado;
- HTML no lugar de M3U;
- lista de 100 mil itens gerada;
- URL gigante;
- itens duplicados;
- Xtream com campos numéricos como strings;
- Xtream parcial;
- paginação em loop;
- XMLTV válido;
- XMLTV gzip;
- XML truncado;
- timezone ausente;
- horário de verão;
- programas sobrepostos;
- `stop` ausente;
- channel IDs divergentes;
- EPG vazio após snapshot válido;
- zip bomb simulada dentro de limite de teste.

---

## 14. Critérios de aceitação

- lista malformada parcialmente importa itens válidos;
- HTML nunca vira catálogo vazio;
- refresh vazio não apaga snapshot válido;
- parsing de lista grande não causa ANR;
- importação pode ser cancelada;
- EPG com timezone é normalizado uma única vez;
- ausência de `stop` é inferida e marcada;
- correção manual de canal é preservada;
- banco antigo migra em teste;
- falha de staging não altera produção;
- relatório não contém segredo.

---

## 15. Referências

- XMLTV DTD: https://github.com/XMLTV/xmltv/blob/master/xmltv.dtd
- Android ANR guidance: https://developer.android.com/topic/performance/anrs/diagnose-and-fix-anrs
- RFC 8216 HLS playlists: https://www.rfc-editor.org/rfc/rfc8216
