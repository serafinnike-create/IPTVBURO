# ADR-009 — Domínio universal de mídia e adapters de fonte

- Estado: aceito para migração incremental
- Data: 9 de agosto de 2026

## Contexto

O domínio original nasceu para vídeo: `CatalogContentType`, `ContentKind` e
`Channel` sustentam TV, filmes, séries e episódios, além de chaves já persistidas
em favoritos e progresso. O Windows passou a experimentar música por modelos
especializados. Reutilizar `Channel` para artista, álbum, podcast ou audiobook
apagaria diferenças importantes e faria o Android herdar um contrato incorreto.

## Decisão

1. Adicionar `MediaKind` como taxonomia universal sem remover os enums legados.
2. Manter mappers explícitos. Tipos não-vídeo voltam como `UNKNOWN` no legado.
3. Preservar byte a byte as chaves de vídeo de `ContentIdentity` e usar
   `MediaIdentity` para os novos namespaces. Toda identidade nova começa em
   `v1`; as chaves legadas de vídeo permanecem sem versão para não mudar dados
   persistidos.
4. URLs remotas usadas em identidade perdem user-info e query; host/caminho são
   mantidos somente em digest não reversível.
5. Adicionar capabilities conservadoras de mídia, playback e fonte. Suporte
   efetivo é a interseção entre fonte, mídia e plataforma.
6. Criar `packages/media-source-spi` para validação, scan em `Flow`, resolução
   tardia e capabilities, sem migrar ainda M3U/Xtream/Stalker do runtime.
7. `SourceConfig`, `PlaybackLocator` e `ResolvedMedia` redigem locators, URI e
   valores de headers em diagnóstico. `PlaybackLocator` aceita somente uma
   referência opaca, nunca uma URL já resolvida.

## Compatibilidade

`UNKNOWN` foi acrescentado ao fim de `ContentKind`; nomes e ordem dos quatro
valores anteriores permanecem intactos. Room schema 6, entidades e migrations
não mudam. `PlaybackCapabilities` mantém os campos antigos e recebe somente
novos parâmetros com defaults conservadores.

## Consequências

- Android e Windows podem implementar as mesmas regras sem compartilhar UI ou
  player inadequado;
- nenhuma função aparece apenas porque o tipo existe;
- os clientes atuais continuam funcionais durante a migração;
- haverá adaptação temporária entre contratos antigos e o SPI;
- capabilities precisam ser medidas e testadas por plataforma.

## Riscos

- dois modelos coexistem durante várias versões;
- um mapper esquecido pode esconder mídia legítima ou expor ação indisponível;
- digest de URL preserva estabilidade, mas uma mudança real de caminho altera a
  identidade e requer política de migração futura.

## Alternativas rejeitadas

- transformar `Channel` em entidade universal: quebra semântica e persistência;
- substituir todos os enums e tabelas numa única migration: risco excessivo;
- usar a URL completa como identidade: vaza segredo e expira com tokens;
- usar WebView/player único para todas as plataformas: perde integrações nativas.
