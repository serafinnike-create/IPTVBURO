# Implementação atual do IPTV BURO

- Data da auditoria: 31 de julho de 2026
- Branch: `main`
- Commit da implementação: `origin/main@2c9bd5b`
- Tag preparada: `v0.1.0-alpha.1`
- Plataforma implementada: Android TV
- Versão do aplicativo: `0.1.0-alpha.1`

## Estado do repositório

A especificação oficial e a implementação Android descrita abaixo estão na
branch remota `main`; o commit da implementação é `2c9bd5b`. Os GDDs oficiais
foram preservados como fonte de verdade. A tag `v0.1.0-alpha.1` está preparada,
mas a GitHub Pre-release e seu APK ainda serão criados pelo workflow após o
envio da tag.

## Stack e módulos

- Kotlin 2.3.21, JVM target 17, Android Gradle Plugin 9.0.1 e Gradle Wrapper 9.1;
- Compose for TV, Media3 ExoPlayer com HLS e datasource OkHttp;
- Room, DataStore, Hilt e Coroutines/Flow;
- JUnit, Android lint e GitHub Actions.

| Módulo | Responsabilidade |
|---|---|
| `apps/android-tv` | Aplicativo, UI Compose, player, persistência e DI |
| `packages/domain-model` | Modelos de fonte, categoria, canal e capacidades |
| `packages/playlist-parser` | Parser M3U streaming e redaction de warnings |
| `packages/test-fixtures` | Fixtures sintéticas e públicas, somente em testes |

## Vertical funcional

- splash e onboarding legal;
- importação de arquivo M3U/M3U8 pelo seletor do Android;
- fontes, categorias e canais persistidos com Room;
- parser streaming com limites, transação atômica e escrita em lotes;
- player Media3 com HLS, loading, primeiro frame, erro, play/pause e seek
  condicionado à capacidade real da mídia;
- português do Brasil, inglês, alemão e italiano;
- navegação por controle remoto/D-pad.

## BURO Cinematic Foundation

Esta milestone substitui a antiga sidebar e o dashboard técnico pela primeira
fundação da experiência descrita no GDD 2.0:

- **BURO Ribbon** com oito destinos: Início, Ao Vivo, Filmes, Séries, Descobrir,
  Minha BURO, Pesquisa e Perfil;
- **Living Home** com hero cinematográfico, fileiras e estados tratados;
- fileiras visuais sintéticas identificadas como **DEMO**, sem mídia ou URL
  reproduzível embutida;
- fileira separada para fontes reais importadas, também sem copiar URLs para os
  modelos visuais da Home;
- Story demonstrativa sem playback, com ação contextual para adicionar fonte;
- placeholders explícitos para destinos ainda não implementados;
- Configurações acessíveis pelo Perfil;
- restauração mínima do último foco da Home e contrato `Back → Ribbon`;
- design system com tokens semânticos, componentes focáveis e tiers
  `Auto`, `Eco`, `Balanced` e `Cinematic`;
- preferências estruturadas para reduced motion, high contrast e reduced
  transparency.

O modo `Auto` escolhe uma política visual local. As preferências e tiers já fazem
parte da fundação, mas os tokens novos ainda não foram aplicados integralmente a
todas as telas legadas.

## Dados, player e segurança

`Source`, `Category` e `Channel` são persistidos no Room. O importador roda no
dispatcher de I/O, rejeita catálogo vazio e não envia playlists ou credenciais
para backend. O player é liberado em `onDispose`, observa primeiro frame/erro e
só expõe seek quando Media3 declara o item pesquisável.

Há redaction de URLs, queries, tokens, credenciais, cookies e IPs nos logs.
`Authorization` e `Cookie` não são persistidos pelo importador, e backup e
transferência de dados do aplicativo estão desabilitados.

URLs de stream, `Referer` e `Origin` ainda ficam no sandbox do Room em texto
simples. Fontes privadas exigirão proteção adicional por Keystore/criptografia
de campos antes de uma versão de produção.

## Validação reproduzida

- `./gradlew test lint assembleDebug`: passou;
- 55 testes JVM: 55 passaram, 0 falhas, 0 erros e 0 ignorados;
- lint: 0 erros e 18 warnings não bloqueantes;
- APK debug local: 25.433.893 bytes;
- SHA-256 do APK local:
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`;
- instalação e execução no Redmi A5, Android 15;
- fluxo E2E com a playlist HLS pública Apple BipBop: importação, navegação por
  fonte/categoria/canal, abertura do player e primeiro frame com áudio/vídeo,
  sem crash;
- onboarding responsivo e navegação compacta em paisagem validados no aparelho.

## Lacunas e riscos

- o GDD 2.0 não está integralmente concluído;
- tokens semânticos ainda não cobrem toda a Home e todas as telas legadas;
- não há testes instrumentados de D-pad, screenshots ou goldens;
- restauração de scroll/estado por rota ainda não é completa;
- Filmes, Séries, Descobrir, Minha BURO, Pesquisa e Perfis reais continuam como
  destinos tratados, sem catálogo funcional;
- a Story atual é demonstrativa e não inicia playback;
- não há EPG/XMLTV, Xtream, Kids, catálogo enriquecido ou recomendação real;
- não há modelos temporais do GDD 3.0;
- não há Resilience Engine, `RetryBudget` ou `ConnectionBudget` do GDD 4.0;
- URLs de stream continuam em texto simples no Room;
- a Release ainda depende da tag e da conclusão do workflow;
- tamanho e SHA-256 do APK reconstruído pelo CI ainda não estão confirmados;
- o aplicativo é Android TV; o telefone é aparelho de validação nesta fase.
