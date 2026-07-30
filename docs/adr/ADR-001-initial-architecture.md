# ADR-001 — Arquitetura inicial do IPTV BURO

- Status: aceito
- Data: 29 de julho de 2026

## Contexto

O produto pretende atender diversas plataformas, mas a primeira Sprint precisa
provar uma experiência Android TV funcional, segura e navegável por D-pad. Uma
camada multiplataforma ou vários aplicativos agora aumentariam o custo sem
validar o risco principal: importar uma fonte legal, organizar os canais e
reproduzir HLS com comportamento honesto de seek.

## Decisão

Adotar um monorepo Gradle com quatro módulos:

- `apps/android-tv`: aplicação Android com Compose for TV, Media3, Room,
  DataStore, Hilt e OkHttp;
- `packages/domain-model`: modelos de domínio sem dependência do Android;
- `packages/playlist-parser`: parser M3U streaming sem dependência do Android;
- `packages/test-fixtures`: dados sintéticos ou amostras públicas autorizadas
  usados apenas em testes.

O módulo Android usa arquitetura em camadas:

```text
Compose UI → ViewModel → Repository → Room/DataStore
                              ↘ M3U parser
Media3 Player ← Playback screen ← Channel
```

O catálogo é local-first. A importação é feita no dispositivo; nenhuma playlist
ou credencial passa por backend. Media3 é usado diretamente nesta Sprint, sem
uma abstração multiplataforma prematura.

## Consequências

### Positivas

- build e depuração simples;
- parser e domínio testáveis na JVM;
- credenciais permanecem no dispositivo;
- caminho claro para separar novos aplicativos no monorepo;
- APK Android TV também pode ser instalado em um telefone para smoke test.

### Limitações

- a interface desta Sprint é otimizada para TV, não para celular;
- não há aplicação Windows/macOS;
- não há Xtream, XMLTV, perfis, licença ou pagamento nesta entrega;
- compatibilidade final de codecs depende do aparelho e da fonte.

## Alternativas rejeitadas

- Kotlin Multiplatform agora: nenhum segundo consumidor justifica o custo.
- Flutter universal: não elimina integrações nativas de reprodução.
- Web/PWA: codecs e headers de IPTV não são confiáveis no navegador.
- Backend proxy: aumentaria custo e risco jurídico, além de violar o GDD.
