# Changelog

Todas as mudanças relevantes do IPTV BURO serão registradas neste arquivo.

## [Unreleased]

### Added

- Fundação do monorepo Android TV.
- Importação local M3U/M3U8, catálogo Room e player HLS com Media3.
- BURO Ribbon com oito destinos.
- Living Home com hero, fileiras sintéticas DEMO e fileira de fontes reais sem
  expor URLs na camada visual.
- Story demonstrativa sem playback e placeholders explícitos para destinos
  futuros.
- BURO Cinematic design system com tokens semânticos, componentes focáveis,
  tiers `Auto`, `Eco`, `Balanced` e `Cinematic`, reduced motion, high contrast e
  reduced transparency.
- Restauração mínima de foco da Home e comportamento `Back → Ribbon`.
- Localização em português do Brasil, inglês, alemão e italiano.
- Documentação de arquitetura, segurança, release e estado real da milestone.

### Changed

- A sidebar e o dashboard técnico foram substituídos pela primeira Living Home.
- Configurações passaram a ser acessadas pelo Perfil.
- Controles de seek permanecem condicionados à capacidade real informada pelo
  Media3.

### Validated

- `./gradlew test lint assembleDebug` aprovado: 55 testes JVM, 0 falhas, lint
  com 0 erros e 18 warnings não bloqueantes.
- APK debug com 25.433.893 bytes e SHA-256
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`.
- Fluxo E2E no Redmi A5 com Android 15 usando a playlist HLS pública Apple
  BipBop: importação, navegação, primeiro frame e áudio/vídeo sem crash.

### Known limitations

- GDD 2.0 ainda parcial; GDD 3.0 e GDD 4.0 não implementados.
- Busca, perfis, catálogo real de filmes/séries, EPG/XMLTV e Xtream pendentes.
- Sem testes instrumentados de D-pad ou testes de screenshot/golden.
- Tokens ainda não cobrem integralmente a Home e telas legadas.
- URLs de stream continuam armazenadas em texto simples no Room.
- Alterações locais ainda sem commit, push ou GitHub Release.
