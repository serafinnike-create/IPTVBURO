<div align="center">

# IPTV BURO

### Uma plataforma universal de entretenimento para TV, celular, tablet e computador

Transforma fontes de mídia autorizadas pelo usuário em uma biblioteca organizada, cinematográfica, resiliente e, no mobile, disponível offline.

![Status](https://img.shields.io/badge/status-em%20desenvolvimento-7c3aed)
![GDD](https://img.shields.io/badge/GDD-1.0%20%E2%86%92%209.0-2563eb)
![Android TV](https://img.shields.io/badge/Android%20TV-v3.0.1-3ddc84)
![Windows](https://img.shields.io/badge/Windows-v3.0.6-e2b458)
![Multiplataforma](https://img.shields.io/badge/escopo-universal-0f766e)
![Offline Mobile](https://img.shields.io/badge/Offline%20Vault-planejado-f59e0b)

</div>

> [!IMPORTANT]
> O IPTV BURO é exclusivamente um reprodutor e organizador de mídia. O projeto não fornece canais, filmes, séries, playlists, assinaturas ou conteúdo protegido. O usuário deve possuir autorização legal para acessar todas as fontes configuradas.

---

## Visão do produto

O IPTV BURO não foi concebido como apenas mais um player IPTV. O objetivo é transformar fontes desorganizadas em uma experiência premium, rápida e confiável, com identidade própria e aplicações adequadas a cada sistema.

O produto possui seis pilares:

1. **BURO Cinematic System** — interface premium para TV, touch, mouse e teclado.
2. **BURO Catalog Brain** — organização, normalização e deduplicação.
3. **BURO Temporal Intelligence** — lançamentos classificados pelo ano real da obra.
4. **BURO Resilience Engine** — diagnóstico e recuperação controlada de falhas.
5. **Universal Multiplatform Delivery** — o mesmo produto em diferentes ecossistemas.
6. **BURO Offline Vault** — filmes, episódios e temporadas elegíveis offline no mobile.

---

## Estado real do desenvolvimento

Legenda: ✅ concluído · 🧪 em teste · 🚧 em implementação · 🧭 planejado

| Entrega | Estado |
|---|---|
| GDDs 1.0 a 9.0 | ✅ Documentados na `main` |
| Aplicação Android/Android TV | 🧪 Prévia `v3.0.1`; APK de depuração, não é versão de loja |
| Aplicação Windows | ✅ `v3.0.6`; MSI ainda sem assinatura Authenticode |
| **Onde assistir** (GDD 9) — prateleira por serviço, com capas | 🧪 Windows, dados reais do TMDb |
| **Já está na sua lista** — o título encontrado no catálogo do usuário | 🧪 Só um casamento confiante produz a linha |
| Redirecionamento ao serviço oficial | ✅ Nunca reproduz stream protegido; recusa endereço com token ou mídia |
| Música, rádio, fila e playlists (GDD 8) | 🧪 Windows; seções 16 a 18 implementadas |
| Trailer dentro do aplicativo | 🧪 Chromium embutido; recuo para o navegador quando indisponível |
| Importação local M3U/M3U8 | ✅ Vertical funcional |
| Xtream: ao vivo, filmes, séries e episódios | ✅ Vertical funcional |
| Room, parser em lotes e transação de catálogo | ✅ Implementados |
| Player HLS Media3 | ✅ Vertical funcional |
| BURO Ribbon, Home real, capas e detalhes | 🧪 Fundação cinematográfica em teste |
| Busca, quatro idiomas, perfis e favoritos | 🧪 Implementados; paridade em evolução |
| Temporal Intelligence no código | 🚧 Filtro por ano com seletor completo; domínio total pendente |
| Resilience Engine no código | 🧭 Pendente |
| Portal Stalker/Ministra (MAC) | 🧪 Cliente e importação prontos; falta a tela de conexão |
| Download de VOD | 🧪 Windows funcional; Android pendente. Diverge do GDD 6 por [ADR-008](docs/adr/ADR-008-UNRESTRICTED-VOD-DOWNLOAD.md) |
| Multiview (até 4 canais) | 🧪 Implementado no Windows |
| Licenciamento e site de ativação | 🧪 App exibe código e QR; site apresenta o produto e encaminha a compra ao Worker/Stripe. Validação de compra real ainda pendente |
| Atualização no Windows | ✅ O botão consulta sempre `serafinnike-create/IPTVBURO` e só aceita MSI mais novo, assinado e com SHA-256 do GitHub |
| Android mobile | 🧪 Mesma build adaptativa instalada em Android 15 |
| Windows | 🧪 Compose Desktop, player compatível e MSI local aprovados |
| Continuidade de reprodução por perfil | 🧪 Android e Windows implementados; migração Android validada em aparelho físico |
| XMLTV/EPG e Offline Vault autorizado | 🧭 Pendentes |
| Samsung Tizen | 🧪 Preview própria compilada e assinada; a imagem Tizen 10 atual recusa até pacote mínimo, e validação em TV física permanece pendente |
| LG, Titan OS e plataformas Apple | 🧭 Planejados |
| Publicação em lojas | 🧭 Não iniciada |

> [!NOTE]
> Diretório, GDD ou scaffold não significam função pronta. O status só deve avançar com build, testes e validação reproduzível.

---

## Implementação Android atual

A primeira vertical slice está na `main` e possui:

- splash e onboarding legal;
- importação de arquivo M3U/M3U8;
- fontes, categorias e canais persistidos com Room;
- parser streaming com limites e escrita em lotes;
- player HLS com Media3;
- loading, primeiro frame, play/pause e seek quando suportado;
- navegação por D-pad;
- BURO Ribbon;
- Living Home alimentada pelo catálogo real, com lançamentos do ano atual e anterior;
- Xtream com credenciais cifradas pelo Android Keystore;
- capas, backdrop, sinopse, avaliação, elenco, temporadas e episódios quando fornecidos;
- até cinco perfis, inclusive Kids, e favoritos isolados por perfil;
- retrato, paisagem, TV e janelas expandidas;
- controles de volume, brilho, velocidade, bloqueio, PiP, áudio e legenda quando disponíveis;
- PT-BR, inglês, alemão e italiano;
- logs com redaction;
- backup e transferência de dados desabilitados;
- 139 testes aprovados no gate local mais recente e 1 teste conectado de migração aprovado em Android físico;
- lint sem erros bloqueantes;
- build debug aprovada e workflow multiplataforma preparado.

O fluxo importação → categoria → canal → primeiro frame foi validado em aparelho Android físico usando uma playlist HLS pública de teste.

### Baixar

As duas aplicações passam a compartilhar a numeração, e estão na mesma página de
versão. O Android continua em prévia: o APK é de depuração e não é uma versão de
loja.

**➜ [Página da versão v3.0.6](https://github.com/serafinnike-create/IPTVBURO/releases/tag/v3.0.6)**
· [todas as versões](https://github.com/serafinnike-create/IPTVBURO/releases)

| Plataforma | Baixar |
|---|---|
| Windows 10/11 (64 bits) | [`IPTV-BURO-v3.0.6-windows-x64.msi`](https://github.com/serafinnike-create/IPTVBURO/releases/download/v3.0.6/IPTV-BURO-v3.0.6-windows-x64.msi) — sem assinatura, veja a nota abaixo |
| Android / Android TV (prévia) | [`IPTV-BURO-v3.0.1-android-debug.apk`](https://github.com/serafinnike-create/IPTVBURO/releases/download/v3.0.1/IPTV-BURO-v3.0.1-android-debug.apk) |

O [`SHA256SUMS.txt`](https://github.com/serafinnike-create/IPTVBURO/releases/download/v3.0.6/SHA256SUMS.txt)
da mesma página permite conferir os dois arquivos.

> [!NOTE]
> O MSI **não é assinado**, então o Windows pode mostrar um aviso de "Editor
> desconhecido" ou do SmartScreen. O APK usa assinatura de desenvolvimento e não
> é uma versão de loja.

**Atualizar pelo app funciona.** Em **Opções → Buscar atualização** o aplicativo
consulta esta página, baixa somente um MSI semanticamente mais novo e confere o
digest SHA-256 antes de executá-lo. Perfis, lista, favoritos e licença são
preservados.

Nenhuma chave de API viaja dentro dos pacotes: a chave do TMDb é configurada pelo
próprio usuário, por perfil, e as credenciais da lista ficam no cofre DPAPI do
Windows, fora do instalador.

### Novidades desta prévia

- **A chave v4 do TMDb funciona.** O TMDb entrega duas credenciais na mesma
  página: a v3, que vai na URL, e o *Read Access Token* v4, que é um JWT e tem de
  ir no cabeçalho. O app só sabia a primeira forma, então um token perfeitamente
  válido era recusado e a tela mandava conferir uma chave que estava certa. Isso
  resolve **Assinaturas** e as **fotos do elenco** de uma vez.
- **Baixar temporada inteira**, no Windows e no Android, mantendo o download de
  cada episódio. Episódios já em disco são pulados, e a confirmação conta o que
  realmente vai ser baixado.
- **Enviar para outra tela** — do celular para o computador **e** do computador
  para outra tela. O que trafega é *qual* título, nunca o vídeo nem as
  credenciais: a outra tela procura na lista dela e reproduz direto da fonte.
- **Trocar o áudio não deixa mais a tela preta.** Mudar o layout de caixas exige
  um motor novo, e a superfície de vídeo não estava sendo recriada junto.
- **Áudio 2.0 / 5.1 / 7.1** e modo binaural para fone.
- **Imagem mais nítida em 4K** — as capas passam a ser pedidas no tamanho do seu
  monitor, em vez de esticadas a partir de 1080p.
- **Reinstalar não perde mais a licença**, e a atualização pelo app deixou de
  apagar o aplicativo.

### Build

Requisitos:

- JDK 17 ou superior;
- Android SDK Platform 36;
- Android Build-Tools 36.0.0;
- Android/Android TV API 23 ou superior.

Windows:

```powershell
.\gradlew.bat test :apps:android-tv:lintDebug :apps:android-tv:assembleDebug
.\gradlew.bat :apps:desktop:clean :apps:desktop:test :apps:desktop:createDistributable
```

O MSI público não pode ser criado diretamente. Com o certificado protegido
configurado, use `./scripts/sign-windows-release.ps1`; o script assina e verifica
o launcher e o instalador antes de produzir o artefato.

Linux/macOS:

```bash
./gradlew test lint assembleDebug
```

APK local:

```text
apps/android-tv/build/outputs/apk/debug/android-tv-debug.apk
```

MSI local:

```text
apps/desktop/build/compose/binaries/main/msi/IPTVBURO-2.0.0.msi
```

Documentação do estado atual:

- [Implementação atual](docs/status/CURRENT_IMPLEMENTATION.md)
- [Análise de lacunas do GDD 2.0](docs/status/GDD2_GAP_ANALYSIS.md)
- [Arquitetura inicial](docs/adr/ADR-001-initial-architecture.md)
- [Fundação cinematográfica](docs/adr/ADR-002-buro-cinematic-foundation.md)
- [Continuidade de reprodução no Windows](docs/adr/ADR-006-windows-playback-progress.md)
- [Player VLC e atualização segura no Windows](docs/adr/ADR-007-windows-vlc-and-release-updater.md)
- [Tratamento de credenciais](docs/security/credential-handling.md)

---

## Diferenciais planejados

### BURO Temporal Intelligence

Separa:

- data de adição à fonte;
- data real de lançamento.

Um filme antigo adicionado hoje nunca deve aparecer em `Lançamentos {ano atual}`.

### BURO Resilience Engine

Planejado para classificar falhas, limitar retries, proteger snapshots válidos, respeitar conexões simultâneas e explicar erros sem expor dados sensíveis.

### BURO Quality Autopilot

Selecionará estratégia de reprodução considerando aparelho, resolução, HDR, codecs, estabilidade, conexão, idioma e limite de sessões.

### BURO Pulse e BURO Lens

TV ao vivo, mini-guia, zapping, EPG, eventos, busca universal e filtros avançados.

### BURO Offline Vault

Diferencial P0 exclusivo de Android mobile/tablet e iPhone/iPad:

- baixar filme ou episódio elegível;
- baixar temporada como fila de episódios;
- escolher qualidade, áudio e legenda;
- pausar, retomar, cancelar e remover;
- biblioteca acessível sem internet;
- reprodução em modo avião;
- progresso local e sincronização posterior;
- Smart Downloads opt-in;
- Wi-Fi por padrão;
- gerenciamento de armazenamento;
- perfil e Kids também offline;
- conteúdo privado dentro do aplicativo, sem exportação.

A função não será exibida nas aplicações de TV durante o P0.

---

## Plataformas do escopo final

| Plataforma | Estratégia | Estado atual |
|---|---|---|
| Android TV / Google TV | Kotlin, Compose for TV e Media3 | 🧪 Alpha funcional |
| Sony e Philips Android/Google TV | mesma aplicação validada por modelo | 🧭 Pendente |
| Fire TV | variante Android | 🧭 Planejado |
| Android mobile/tablet | Kotlin, Compose e Media3 | 🧪 Preview adaptativo |
| Apple TV | SwiftUI e AVPlayer | 🧭 Planejado |
| iPhone/iPad | SwiftUI, AVFoundation e Offline Vault | 🧭 Planejado |
| macOS | SwiftUI/AppKit e AVPlayer | 🧭 Planejado |
| Samsung Tizen | aplicação própria com AVPlay | 🧪 Preview compilada/assinada; instalação bloqueada pela imagem de emulador atual |
| LG webOS | aplicação própria | 🧭 Planejado |
| Philips Titan OS | aplicação compatível com SDK oficial | 🧭 Planejado |
| Windows | Compose Desktop; adapter nativo futuro | 🧪 Preview MSI |
| Portal web | ativação, licença e gerenciamento | 🧭 Planejado |

> Um único produto não significa um único executável. Cada plataforma terá player, lifecycle, armazenamento e distribuição adequados ao sistema.

---

## Arquitetura-alvo

```mermaid
flowchart LR
    A[Fontes autorizadas] --> B[Importação transacional]
    B --> C[Catalog Brain]
    C --> D[Temporal Intelligence]
    D --> E[Universal Content Graph]

    E --> F[Living Home]
    E --> G[BURO Lens]
    E --> H[BURO Pulse]
    E --> I[Story Page]
    E --> O[Offline Eligibility]

    F --> J[PlayerAdapter]
    G --> J
    H --> J
    I --> J

    O --> V[Offline Vault mobile]

    J --> K[Quality Autopilot]
    K --> L[Resilience Engine]
    L --> M[Player nativo]
```

Princípios:

- preservar código funcional;
- domínio e regras compartilhados;
- player nativo por plataforma;
- processamento local-first;
- credenciais protegidas;
- snapshots transacionais;
- nenhum spinner ou retry infinito;
- nenhuma função marcada como pronta sem evidência.

---

## Roadmap resumido

### Agora — fechar a prévia Windows 2.0

- configurar certificado Authenticode e timestamp no GitHub Secrets;
- publicar uma versão com MSI assinado (Authenticode) e `SHA256SUMS.txt`;
- validar instalação e atualização numa conta Windows limpa;
- manter dados pessoais e chaves fora de todos os artefatos.

### Onda 1 — consolidar Android TV

- completar Cinematic System;
- catálogo real de filmes e séries;
- Temporal Intelligence;
- Xtream e XMLTV/EPG;
- Resilience Engine;
- segurança das fontes;
- testes em Android TV real.

### Onda 2 — Android mobile e Fire TV

- aplicação Android mobile dedicada;
- perfis e sincronização;
- primeira vertical do Offline Vault;
- Fire TV.

### Onda 3 — Apple

- Apple TV;
- iPhone e iPad;
- macOS;
- Offline Vault mobile com AVFoundation.

### Onda 4 — fabricantes e desktop

- Samsung Tizen;
- LG webOS;
- Philips Titan OS;
- Windows;
- portal comercial.

---

## Documentação oficial

- [Índice geral dos GDDs](docs/GDD_IPTV_BURO.md)
- [GDD 2.0 — experiência revolucionária](docs/GDD_2_REVOLUTIONARY_EXPERIENCE.md)
- [GDD 3.0 — inteligência temporal](docs/GDD_3_CATALOG_RELEASE_INTELLIGENCE.md)
- [GDD 4.0 — confiabilidade](docs/GDD_4_RELIABILITY_FAILURE_RECOVERY.md)
- [GDD 5.0 — entrega universal](docs/GDD_5_UNIVERSAL_MULTIPLATFORM_DELIVERY.md)
- [GDD 6.0 — BURO Offline Vault](docs/GDD_6_BURO_OFFLINE_VAULT.md)
- [GDD 7.0 — continuidade e progresso de reprodução](docs/GDD_7_PLAYBACK_CONTINUITY_AND_WATCH_PROGRESS.md)
- [ADR multiplataforma](docs/adr/ADR-0001-MULTIPLATFORM-DELIVERY-ARCHITECTURE.md)
- [Prompt Codex GDD 5](docs/PROMPT_CODEX_CONTINUE_GDD5.md)
- [Prompt Codex GDD 6](docs/PROMPT_CODEX_CONTINUE_GDD6.md)

---

## Ordem para o Codex

1. ler `docs/GDD_IPTV_BURO.md`;
2. ler o relatório de implementação atual;
3. auditar antes de reescrever;
4. preservar builds e migrações;
5. implementar vertical slices pequenas;
6. testar e documentar;
7. separar claramente função pronta de planejamento.

---

## Segurança e modelo comercial

- credenciais não aparecem em logs;
- o projeto não fornece conteúdo;
- não haverá bypass de proteção ou autorização;
- conteúdo offline permanece privado e autorizado;
- teste gratuito proposto: 7 dias;
- compra única: preço-base de € 9,90 por dispositivo, válida por 730 dias e sem renovação automática;
- regras finais dependem de validação jurídica, fiscal e das lojas.

<div align="center">

**IPTV BURO — uma experiência, todos os seus aparelhos.**

Projeto privado em desenvolvimento.

</div>
