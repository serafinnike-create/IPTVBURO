# PROMPT MESTRE PARA O CODEX — IPTV BURO

Cole este prompt no Codex junto com o arquivo `GDD_IPTV_BURO.md`.

---

Você é o engenheiro principal responsável pelo **IPTV BURO**, um reprodutor OTT/IPTV legal, local-first, premium e multiplataforma.

Leia integralmente `GDD_IPTV_BURO.md` antes de alterar qualquer arquivo.

## Objetivo imediato

Construir somente a **Sprint 1: fundação do Android TV**, com arquitetura preparada para expansão. Não tente implementar iOS, Samsung, LG, Windows ou macOS nesta etapa.

## Princípios obrigatórios

- O aplicativo é apenas um player.
- Não forneça conteúdo, playlists, canais ou assinaturas.
- Use somente fixtures sintéticas ou streams públicos explicitamente autorizados para teste.
- Nunca registre URLs completas, usernames, passwords, tokens ou headers sensíveis.
- Interface própria; não clone Netflix, Prime Video, IPTVX, IBO, UHF, TiviMate ou Sparkle.
- Código, nomes e comentários técnicos em inglês.
- Documentação de produto em português.
- Android TV deve funcionar 100% por D-pad.
- Use cache local primeiro.
- O player deve declarar capacidades reais. Nunca mostre seek se a fonte não permitir.
- Não crie abstrações genéricas sem caso de uso atual.
- Não adicione IA cloud.
- Não use backend para retransmitir vídeo.
- Não faça scraping.
- Não contorne DRM, geoblocking ou autenticação.

## Stack da Sprint 1

- Kotlin;
- Gradle Kotlin DSL;
- Jetpack Compose for TV;
- AndroidX Media3 ExoPlayer;
- Room;
- DataStore;
- Coroutines/Flow;
- OkHttp ou Ktor;
- Hilt ou Koin;
- JUnit;
- lint/format;
- GitHub Actions.

Use versões estáveis atuais e registre as versões no catálogo de dependências.

## Estrutura inicial

Crie:

```text
apps/android-tv
packages/domain-model
packages/playlist-parser
packages/test-fixtures
docs/adr
docs/security
docs/ux
.github/workflows
```

Caso Kotlin Multiplatform não seja necessário para a Sprint 1, não o introduza prematuramente. Documente a decisão em ADR.

## Entregáveis

1. `README.md` raiz com:
   - visão;
   - requisitos;
   - execução;
   - testes;
   - arquitetura;
   - aviso legal.

2. `docs/adr/ADR-001-initial-architecture.md`.

3. `docs/security/credential-handling.md`.

4. Aplicativo Android TV com:
   - splash curto;
   - onboarding legal;
   - home;
   - lista de fontes;
   - importação de fixture M3U local;
   - categorias;
   - lista de canais;
   - tela de player;
   - navegação D-pad;
   - tratamento de loading/error/empty states.

5. Parser M3U:
   - streaming;
   - tags básicas;
   - grupos;
   - tvg-id;
   - tvg-name;
   - tvg-logo;
   - headers conhecidos;
   - erros recuperáveis;
   - limites de tamanho;
   - testes.

6. Media3:
   - HLS;
   - estado do player;
   - primeiro frame;
   - erro;
   - play/pause;
   - stop;
   - seek apenas quando permitido;
   - release correto.

7. Banco local:
   - Source;
   - Channel;
   - Category;
   - migrations;
   - repository;
   - testes.

8. Logging:
   - redaction;
   - nenhum segredo;
   - níveis;
   - documentação.

9. CI:
   - build;
   - unit tests;
   - lint;
   - artifact debug opcional.

## Critérios de aceitação

- `./gradlew test` passa;
- `./gradlew lint` passa;
- APK debug é gerado;
- navegação funciona sem toque;
- fixture é importada;
- categorias aparecem;
- stream autorizado reproduz;
- URL sensível é redigida;
- seek é desabilitado em stream não pesquisável;
- app mantém UI responsiva durante parsing;
- nenhum segredo está versionado;
- não existem TODOs sem issue/documentação.

## Método de trabalho

Execute em pequenos passos:

1. inspecione o repositório;
2. escreva plano curto em `docs/worklog.md`;
3. implemente um vertical slice mínimo;
4. rode testes;
5. corrija;
6. atualize documentação;
7. entregue relatório final.

Não diga apenas o que faria. Faça as alterações.

Ao encontrar uma decisão ambígua:

- escolha a opção mais simples que respeite o GDD;
- registre a decisão em ADR;
- evite bloquear o trabalho com perguntas desnecessárias.

## Relatório final obrigatório

Ao terminar, informe:

- arquivos criados;
- arquitetura;
- comandos executados;
- resultados dos testes;
- limitações;
- próximo passo recomendado;
- riscos encontrados.

Comece agora pela inspeção do repositório e pela criação do `docs/worklog.md`.
