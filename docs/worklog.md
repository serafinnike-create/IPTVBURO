# Diário de trabalho — Sprint 1

## 29 de julho de 2026

### Escopo confirmado

- Produto renomeado de Aurora Player para **IPTV BURO**.
- Primeira entrega limitada ao Android TV, conforme o GDD e o prompt mestre.
- O aplicativo também será validado no celular Android conectado.
- No notebook, esta Sprint usa execução Android por espelhamento/emulador; uma
  aplicação Windows nativa permanece fora do escopo.
- Repositório de destino: `lucasserafin94/IPTVBURO`.

### Ambiente auditado

- Android Studio 2025.3.1 instalado.
- Android SDK 36 e build-tools 36.0.0 instalados.
- JDK 21 disponível; o build do projeto usa bytecode Java 17.
- Redmi A5 com Android 15 conectado, autorizado e visível pelo ADB.
- Não existe AVD ou system image local no início da Sprint.
- GitHub CLI instalado; a publicação final foi executada pelo GitHub Actions.

### Plano

1. Criar o monorepo Gradle e os módulos mínimos.
2. Implementar domínio e parser M3U com testes.
3. Implementar onboarding legal, catálogo local e importação por arquivo.
4. Persistir fontes, categorias e canais com Room.
5. Reproduzir HLS com Media3 e refletir corretamente a capacidade de seek.
6. Validar redaction de dados sensíveis.
7. Executar testes, lint e build.
8. Instalar no celular e publicar a primeira versão no GitHub.

### Decisões iniciais

- Sem Kotlin Multiplatform nesta Sprint.
- `applicationId` provisório: `com.lucasserafin94.iptvburo`.
- AGP 9.0.1 foi escolhido por ser a versão estável compatível com o Android
  Studio 2025.3.1 instalado. Kotlin 2.3.21 é o corte estável compatível; versões
  Kotlin 2.4.x exigiriam uma combinação mais nova de AGP/R8.
- Fixtures são dependências de teste e não entram no APK de produção.
- A primeira versão distribuível foi publicada como prévia; uma versão estável
  exigirá chave privada de assinatura de produção.

## 31 de julho de 2026

### Integração com o repositório oficial

- `origin` conectado a `lucasserafin94/IPTVBURO`;
- GDDs oficiais preservados na `main`;
- implementação Android enviada à `main` no commit `2c9bd5b`;
- branch de protótipo remoto auditada, mas não incorporada automaticamente.

### Validação em aparelho

- APK instalado e iniciado no Redmi A5;
- onboarding inicialmente cortava texto em 720p lógico compacto;
- breakpoint responsivo implementado e validado por nova captura;
- fluxo legal acionado somente por D-pad;
- home técnica aberta sem crash.

### Continuação GDD 2.0

#### Milestone BURO Cinematic Foundation

- implementação atual, análise de lacunas e ADR registrados sem alterar os GDDs
  oficiais;
- sidebar substituída pela BURO Ribbon com oito destinos;
- Living Home criada com hero, rails sintéticas marcadas como DEMO e rail
  separado de fontes reais, sem transportar URLs para a camada visual;
- Story demonstrativa adicionada sem playback ou conteúdo embutido;
- placeholders explícitos mantêm Filmes, Séries, Descobrir, Minha BURO, Pesquisa
  e Perfil navegáveis sem fingir funcionalidades concluídas;
- Configurações movidas para acesso contextual pelo Perfil;
- design system ampliado com tokens semânticos, componentes, tiers
  `Auto`/`Eco`/`Balanced`/`Cinematic`, reduced motion, high contrast e reduced
  transparency;
- restauração mínima de foco na Home e contrato `Back → Ribbon` implementados;
- PT-BR, EN, DE e IT preservados;
- vertical de importação M3U, fontes, categorias, canais e player HLS preservado.

#### Validação da milestone

- `./gradlew test lint assembleDebug` concluído com sucesso;
- 55 testes JVM aprovados, sem falhas, erros ou testes ignorados;
- lint com 0 erros e 18 warnings não bloqueantes;
- APK debug local com 25.433.893 bytes e SHA-256
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`;
- Redmi A5 com Android 15 usado no fluxo E2E;
- playlist HLS pública Apple BipBop importada e navegada até o player;
- primeiro frame, áudio/vídeo e retorno ao aplicativo observados sem crash.

#### Publicação da primeira prévia

- implementação confirmada em `main@2c9bd5b`;
- tag `v0.1.0-alpha.1` publicada no commit `7e0b9ec`;
- GitHub Release publicada como pre-release:
  `https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.1.0-alpha.1`;
- workflow `Publish Android preview`, run
  [`30590918504`](https://github.com/lucasserafin94/IPTVBURO/actions/runs/30590918504),
  concluído com sucesso;
- [APK publicado](https://github.com/lucasserafin94/IPTVBURO/releases/download/v0.1.0-alpha.1/IPTV-BURO-v0.1.0-alpha.1-android-debug.apk):
  `IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`, 24.864.542 bytes;
- SHA-256 do APK publicado:
  `179537447d53ef062daf9cd100b5ed52416be796ceedb61cb64601a930965dc6`;
- hash local preservado separadamente do artefato reconstruído pelo CI.
- upload auxiliar de artefato removido do CI da `main` após a cota do Actions
  ser atingida; o workflow continua validando build/test/lint e a distribuição
  oficial permanece centralizada na GitHub Release.

#### Pendências assumidas

- adoção integral dos tokens na Home e em telas legadas;
- testes instrumentados de D-pad e testes de screenshot/golden;
- restauração completa de scroll e estado por rota;
- catálogo, busca, perfis, EPG/XMLTV, Xtream e experiências reais de
  filmes/séries;
- GDD 3.0 e GDD 4.0;
- proteção de URLs atualmente armazenadas em texto simples no Room.
