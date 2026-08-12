# Auditoria de arquitetura — GDD 8 Fase 0

- Data: 9 de agosto de 2026
- Branch: `agent/iptv-buro-0.2-preview`
- Commit auditado antes das alterações: `2c987b2a2ea4c807031dcd9e0a8150e4f15e8553`
- Estado inicial: worktree com alterações locais em Windows, servidor de licença e
  `MusicTidying`; nenhum desses arquivos foi substituído ou descartado por esta fase.

Durante o gate, esse trabalho preexistente foi commitado externamente e o `HEAD`
avançou para `6cf115f`. A validação integrada foi executada sobre esse commit mais
o diff da Fase 0, sem reverter ou incorporar manualmente os arquivos concorrentes.

## Árvore efetiva

- `apps/android-tv`: Android adaptativo, Compose, Media3, Room, Hilt, M3U,
  Xtream e Stalker;
- `apps/desktop`: Compose Desktop, VLC, fontes, licença, download experimental,
  música e áreas editoriais;
- `packages/domain-model`: regras compartilhadas de catálogo, identidade,
  progresso, licença, descoberta e fundação de áudio;
- `packages/playlist-parser`: parser M3U streaming e mapper de música;
- `packages/xtream-client`, `packages/stalker-client`, `packages/metadata-client`:
  integrações externas separadas;
- `packages/contracts`, `packages/platform-capabilities` e
  `packages/release-manifest`: schemas, declarações e estado verificável;
- `services/license-server`: Worker de registro, validação, Stripe e ativação.

## Uso do domínio anterior

Busca por arquivos Kotlin antes da edição:

- `CatalogContentType`: 19 arquivos;
- `ContentKind`: 7 arquivos;
- `Channel`: 48 arquivos;
- `PlaybackProgress`: 20 arquivos;
- `PlayerScreen`: 2 arquivos;
- `PlatformCapabilities`: 11 arquivos.

`Channel` permanece como modelo do vertical de vídeo. A música já possuía
`MusicTrack`, `MusicLibrary`, fila, playlists e histórico próprios; ela não deve
ser rebaixada para `Channel` nem exposta no Android antes dos adapters e gates.

## Persistência e regressão

- Room está no schema 6;
- migrations existentes: 1→2, 2→3, 3→4, 4→5 e 5→6;
- schemas exportados de 1 a 6;
- `PlaybackProgressMigrationTest` usa `MigrationTestHelper` para a migration de
  continuidade;
- esta fase não altera schema, entidades, DAOs ou migrations.

O vertical existente é protegido por testes de domínio, parser, Xtream,
Stalker, catálogo, identidade, perfis, favoritos, progresso, downloads,
player, navegação e redaction. O gate completo continua obrigatório porque o
worktree já continha uma evolução Windows independente.

## Paridade Windows → Android observada

Já presentes nos dois: fontes autorizadas, Home, TV ao vivo, filmes, séries,
detalhes, perfis, Kids, favoritos, continuidade, idiomas e player adaptado.

Lacunas Android confirmadas:

- licença ainda exibia somente identidade/QR, sem validar entitlement assinado;
- compra nativa Google Play e restauração pelo backend não existem;
- Música/Rádio não têm vertical Android liberada;
- Histórico e Onde Assistir/Assinaturas não têm destinos Android completos;
- multiview Android está declarado como indisponível;
- busca e descoberta continuam parciais;
- a matriz de capabilities mantém offline e compra nativa desativados.

Pagamento não pode copiar Stripe do Windows. Pelo ADR-004, a distribuição Google
Play exige Play Billing, verificação do token no backend e só então entitlement
assinado. Produto, conta de serviço e política de renovação de 730 dias ainda
precisam ser configurados; nenhum botão Android deve fingir aprovação.

## Decisão da Fase 0

Adicionar a taxonomia, capabilities, identidade universal e SPI de fontes sem
ligá-los à UI nem reescrever os clientes atuais. O novo módulo
`packages/media-source-spi` depende apenas do domínio e de `Flow`, permitindo que
adapters Android/Windows futuros compartilhem contrato, não segredos ou player.

Nenhuma URL privada, credencial ou dado do catálogo usado em runtime foi copiado
para esta auditoria.
