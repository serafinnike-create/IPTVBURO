# IPTV BURO frente à concorrência — novembro de 2026

Pesquisa web (lojas oficiais, changelogs, sites de review) e auditoria do código, feitas em 3 de novembro de 2026.

Concorrentes analisados: TiviMate, IPTV Smarters Pro, OTT Navigator, iMPlayer, Sparkle TV, Purple, XCIPTV, Televizo, UHF, IPTVX, MyIPTV e Kodi (PVR IPTV Simple).

Não consegui ler o Reddit. A opinião dos usuários vem de avaliações nas lojas, Trustpilot e reviews.

## Onde o BURO já ganha

| Ponto | BURO | Concorrência |
|---|---|---|
| Plataformas | Android celular **e** TV, Windows, Mac, Samsung Tizen | Quase todos cobrem 1 ou 2 famílias. O TiviMate nem é feito para celular. |
| Perfis + Kids | Perfis com Kids de verdade, PIN, categorias ocultas e bloqueadas | UHF e IPTVX não têm perfis; o PIN do IPTVX nem esconde canais. |
| Filmes e séries | TMDb + OMDb, trailers, continuar assistindo, marcação de assistido, filmografia, "onde assistir" | Fraco no TiviMate; o IPTVX atualiza o VOD só a cada ~8 h. |
| Downloads offline | Sim, incluindo série inteira | Só UHF e IPTVX. |
| Preço | Licença de 730 dias por € 9,90, sem anúncio, sem bloquear o básico | Assinaturas, anúncios (Purple, XCIPTV) e favoritos pagos (Sparkle). |
| Idiomas | PT-BR, EN, DE, IT, ES | A maioria só em inglês. |
| Extras | Lembretes, catch-up, Cast, multiview (desktop), PiP (Android), linha "Watch Next" na Android TV | — |

## Onde o BURO perde (lacunas)

Ordenadas pelo peso que o mercado dá a cada uma.

| # | Lacuna | Quem tem | Situação no BURO | Tamanho |
|---|---|---|---|---|
| 1 | **Sincronizar entre aparelhos** (favoritos, progresso, perfis) | UHF PRO, IPTVX, iMPlayer, XCIPTV, Televizo | Servidor `/v1/sync/*` e `packages/sync-client` prontos, **mas nenhum app os usa** | Grande |
| 2 | **Gravação (DVR) agendada** | TiviMate, XCIPTV, Sparkle, UHF | Não existe em nenhuma plataforma | Grande |
| 3 | **Guia EPG por XMLTV no Android** e várias fontes de EPG | TiviMate, Sparkle, IPTVX, Kodi | O desktop e a Samsung leem XMLTV; o Android só usa o EPG curto do Xtream | Médio |
| 4 | **Player externo** (VLC, MX, Just Player) | XCIPTV, Smarters, TiviMate | O desktop tem; o Android **não** | Pequeno |
| 5 | **AFR** (taxa de quadros da TV acompanha o vídeo) | Sparkle (grátis), TiviMate | Não existe | Médio |
| 6 | **Multiview na TV** | TiviMate, iMPlayer, Sparkle, IPTVX | Só no desktop | Grande |
| 7 | **Timeshift** (pausar e voltar a TV ao vivo) | TiviMate, iMPlayer, Kodi | Não existe | Médio |
| 8 | **Atualização automática da lista** | TiviMate (agendada) | Só manual | Pequeno |
| 9 | **Backup e restauração das configurações** | TiviMate, XCIPTV | Não existe | Pequeno/médio |
| 10 | **Trakt** | iMPlayer, UHF | Não existe | Médio |
| 11 | **Busca única** em ao vivo + filmes + guia | IPTVX, UHF | Busca só no catálogo importado | Médio |
| 12 | **Buffer ajustável** | XCIPTV e outros | Fixo | Pequeno |
| 13 | **PiP no Windows/Mac** | UHF, IPTVX (Apple) | Só no Android | Médio |

## O que os usuários mais reclamam nos concorrentes

Cada reclamação é uma chance de o BURO se destacar:

- recursos básicos atrás de paywall e pop-ups de compra;
- assinaturas sem opção vitalícia;
- instalação e ativação difíceis (TiviMate);
- clones e golpes com nomes parecidos (Smarters);
- VOD fraco;
- lentidão com listas grandes ou várias listas;
- controle parental raso e falta de perfis;
- anúncio em app pago;
- changelogs vagos com regressões;
- ações destrutivas sem confirmação.

## Pendências que atrapalham vender como "o melhor"

- O APK publicado é **debug**, o MSI não tem assinatura Authenticode e o `assetlinks.json` ainda tem o placeholder `REPLACE_WITH_RELEASE_SHA256_FINGERPRINT`.
- O Google Play Billing está bloqueado: faltam os segredos e o certificado de assinatura.
- A Samsung Tizen ainda não foi testada numa TV física.

## Recomendação de ordem

1. **Player externo no Android.** Pequeno e pedido com frequência.
2. **Sincronização entre aparelhos.** A maior vantagem: uma licença e os mesmos favoritos e progresso no celular, na TV, no PC, no Mac e na Samsung. Nenhum concorrente cobre tantas plataformas, e a base já existe.
3. **XMLTV e várias fontes de EPG no Android**, reaproveitando o que o desktop já tem.
4. **Atualização automática da lista e backup/restauração.**
5. **AFR e timeshift na Android TV.**
6. **DVR e multiview na TV.** São os maiores; ficam por último.

## Fontes

Lojas oficiais (App Store e Google Play via AppGoblin), APKMirror com os changelogs, o README oficial do Kodi PVR IPTV Simple, Trustpilot e TroyPoint. A lista completa de URLs está no relatório da pesquisa desta sessão.
