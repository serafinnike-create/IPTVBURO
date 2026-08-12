# IPTV BURO — GDD / PRD Técnico

## 9. DECISÃO DE ARQUITETURA

### 9.1 Princípio

Não forçar um único player universal em todas as plataformas.

A melhor arquitetura é:

- **domínio compartilhado;**
- **contratos compartilhados;**
- **design tokens compartilhados;**
- **player nativo/adaptador por plataforma.**

### 9.2 Monorepo recomendado

```text
iptv-buro/
├─ apps/
│  ├─ android-tv/           # Kotlin + Compose for TV + Media3
│  ├─ android-mobile/       # Kotlin + Compose + Media3
│  ├─ ios-tvos/             # SwiftUI + AVPlayer
│  ├─ desktop/              # Flutter ou Tauri, decisão por ADR
│  ├─ samsung-tizen/        # TypeScript + Tizen Web + AVPlay
│  ├─ lg-webos/             # TypeScript/Flutter webOS
│  ├─ web-portal/           # Next.js
│  └─ api/                  # TypeScript/Fastify ou NestJS
├─ packages/
│  ├─ domain-model/         # modelos e schemas
│  ├─ playlist-parser/      # M3U/M3U8
│  ├─ xtream-client/
│  ├─ xmltv-parser/
│  ├─ metadata-matcher/
│  ├─ recommendation-core/
│  ├─ license-sdk/
│  ├─ design-tokens/
│  ├─ telemetry-schema/
│  └─ test-fixtures/
├─ infra/
│  ├─ docker/
│  ├─ migrations/
│  ├─ terraform-or-pulumi/
│  └─ ci/
├─ docs/
│  ├─ adr/
│  ├─ api/
│  ├─ security/
│  ├─ ux/
│  └─ release/
└─ README.md
```

### 9.3 Tecnologias recomendadas para o MVP

#### Android TV

- Kotlin;
- Jetpack Compose for TV;
- AndroidX Media3 ExoPlayer;
- Room;
- DataStore;
- WorkManager;
- Kotlin Coroutines/Flow;
- Ktor Client ou OkHttp;
- Hilt/Koin;
- Coil para imagens;
- SQLCipher ou criptografia de campos sensíveis;
- Crash reporting com redaction.

#### Portal e backend

- TypeScript;
- Next.js para portal;
- Fastify ou NestJS para API;
- PostgreSQL;
- Redis opcional;
- Stripe Checkout no portal;
- webhooks;
- OpenAPI;
- Zod;
- filas somente quando justificadas;
- armazenamento de segredos em serviço gerenciado.

### 9.4 Alternativa Flutter

Flutter pode ser usado em Android, iOS, Windows, macOS, web e, atualmente, webOS TV. Porém, Samsung Tizen exige um caminho separado, e reprodução premium ainda precisa de integração nativa. Portanto, Flutter deve ser considerado para desktop/mobile, não como justificativa para ignorar players nativos.

---
## 10. ARQUITETURA DE REPRODUÇÃO

### 10.1 PlayerAdapter

Toda plataforma implementa o mesmo contrato conceitual:

```text
PlayerAdapter
- prepare(source)
- play()
- pause()
- stop()
- seekTo(position)
- seekBy(delta)
- setAudioTrack(trackId)
- setSubtitleTrack(trackId)
- setPlaybackSpeed(speed)
- setPreferredQuality(profile)
- getCapabilities()
- getMetrics()
- release()
```

### 10.2 SourceCapabilities

Antes de mostrar controles, o player identifica:

- live ou VOD;
- duração conhecida;
- seek disponível;
- DVR window;
- catch-up disponível;
- áudio alternativo;
- legendas;
- HDR;
- resolução;
- codec;
- container;
- bitrate estimado;
- protocolo;
- suporte de hardware;
- headers especiais;
- necessidade de fallback.

### 10.3 Regra de seek

O botão de avançar não deve mentir.

Estados possíveis:

1. **Seek preciso:** usuário escolhe qualquer posição.
2. **Seek aproximado:** player informa “aproximando para o ponto disponível”.
3. **Janela ao vivo:** seek apenas dentro da janela DVR.
4. **Não pesquisável:** controle desabilitado com explicação curta.
5. **Catch-up:** seek depende da API da fonte.
6. **Fallback:** tentar player alternativo somente quando permitido.

### 10.4 Stream Health Engine

Responsabilidades:

- medir tempo até primeiro frame;
- medir buffering;
- detectar erros HTTP;
- classificar falha de rede, codec, autenticação ou servidor;
- retentar com backoff curto;
- manter sessão quando possível;
- alternar decoder hardware/software quando suportado;
- ajustar buffer por categoria;
- registrar métricas sem URL completa;
- evitar loops infinitos;
- oferecer diagnóstico amigável.

### 10.5 Estratégia de buffer

Perfis:

- **Baixa latência:** TV ao vivo e esporte.
- **Equilibrado:** canais comuns.
- **Estável:** internet ruim.
- **Cinema:** VOD.
- **Automático:** decisão pelo Stream Health Engine.

### 10.6 Troca de canal

Fluxo ideal:

1. manter interface responsiva;
2. cancelar preparação anterior;
3. exibir frame/logo do canal;
4. iniciar conexão;
5. mostrar vídeo assim que houver primeiro frame;
6. carregar EPG e metadados em paralelo;
7. salvar métrica;
8. pré-aquecer apenas o próximo canal quando o dispositivo permitir.

### 10.7 Limitação essencial

O aplicativo não consegue transformar qualquer arquivo/stream quebrado em conteúdo pesquisável. Se a origem não contém índice, duração ou segmentos adequados, o seek pode ser impossível. A UX deve explicar isso sem culpar o usuário.

---
## 11. FORMATOS E FONTES

### 11.1 MVP

- M3U;
- M3U8;
- URL remota;
- arquivo local;
- Xtream-compatible API;
- XMLTV por URL;
- múltiplos headers configuráveis;
- User-Agent por fonte;
- atualização automática.

### 11.2 Pós-MVP

- Stalker/Ministra;
- Jellyfin;
- Plex;
- HDHomeRun;
- Tvheadend;
- Enigma2;
- SMB/NAS;
- WebDAV.

### 11.3 Segurança da importação

- validar esquema de URL;
- bloquear protocolos perigosos;
- proteger contra SSRF no backend;
- não buscar playlists privadas pelo servidor;
- redigir username/password em logs;
- limitar tamanho de arquivo;
- parsing streaming, não carregar tudo na memória;
- timeouts;
- cancelamento;
- detecção de encoding.

---
## 12. EXPERIÊNCIA DO USUÁRIO

## 12.1 Princípios de UX

- interface para distância de 3 metros;
- foco sempre visível;
- no máximo 5 ações principais por tela;
- navegação previsível por D-pad;
- botão voltar sempre consistente;
- nenhum carregamento deve bloquear toda a tela;
- cache local primeiro;
- animações curtas;
- posters carregados progressivamente;
- trailers nunca iniciam com som;
- opção de reduzir movimento;
- opção de economizar dados.

### 12.2 Navegação principal

Barra lateral:

- Início;
- TV ao vivo;
- Filmes;
- Séries;
- Guia;
- Pesquisa;
- Favoritos;
- Minha lista;
- Configurações;
- Perfil.

### 12.3 Home

Rails recomendados:

- Continuar assistindo;
- Assistidos recentemente;
- Ao vivo agora;
- Favoritos;
- Filmes adicionados recentemente;
- Novos episódios;
- Recomendado para o perfil;
- Em alta na sua biblioteca;
- Por gênero;
- Por idioma;
- Coleções criadas pelo usuário.

### 12.4 Hero banner

- backdrop;
- logo/título;
- sinopse curta;
- classificação;
- ano;
- duração;
- gêneros;
- botões Reproduzir, Trailer, Minha Lista e Detalhes;
- trailer silencioso após foco de 1,2 a 2 segundos;
- imagem estática em dispositivos fracos.

### 12.5 Tela de detalhes

Filme:

- capa;
- backdrop;
- trailer;
- sinopse;
- elenco;
- direção;
- duração;
- ano;
- gêneros;
- áudio;
- legendas;
- qualidade;
- fonte;
- itens semelhantes;
- progresso.

Série:

- temporadas;
- episódios;
- próximo episódio;
- continuar;
- marcar temporada como assistida;
- pular abertura quando houver marcador confiável;
- sinopse por episódio.

### 12.6 TV ao vivo

Modos:

1. guia em grade;
2. lista rápida;
3. mini EPG sobre o vídeo;
4. zapping;
5. favoritos;
6. últimos canais;
7. canais por categoria;
8. busca por programa;
9. catch-up quando disponível.

### 12.7 Player overlay

Exibir apenas o necessário:

- título;
- progresso;
- horário;
- play/pause;
- avançar/retroceder;
- próximo episódio;
- áudio;
- legenda;
- qualidade;
- proporção;
- informações técnicas;
- adicionar aos favoritos;
- reportar problema;
- fechar.

### 12.8 Pesquisa

Busca unificada em:

- canais;
- programas EPG;
- filmes;
- séries;
- episódios;
- atores;
- gêneros;
- categorias;
- playlists.

Recursos:

- histórico;
- teclado de TV;
- voz onde disponível;
- tolerância a erros;
- normalização de acentos;
- resultados instantâneos locais.

---
## 13. PERFIS E CONTROLE PARENTAL

### 13.1 Perfis

- até 5 perfis;
- nome;
- avatar;
- idioma;
- preferência de áudio;
- preferência de legenda;
- tema;
- histórico;
- favoritos;
- continuar assistindo;
- recomendações;
- perfil infantil.

### 13.2 Perfil infantil

- PIN para sair;
- ocultação total de categorias bloqueadas;
- ocultação de EPG bloqueado;
- limite por classificação;
- horários de uso opcionais;
- bloqueio de configurações;
- sem trailers inadequados;
- sem exposição de credenciais.

### 13.3 Controle parental

- PIN de 4 a 8 dígitos;
- bloqueio por categoria;
- bloqueio por canal;
- bloqueio por título;
- bloqueio por fonte;
- bloqueio de compras;
- timeout de desbloqueio;
- proteção contra brute force.

---
## 14. METADADOS, CAPAS E TRAILERS

### 14.1 Fonte principal

TMDb ou fornecedor equivalente, com:

- chave protegida;
- atribuição obrigatória;
- cache;
- política de atualização;
- respeito aos termos da API.

### 14.2 Matching

Pipeline:

1. limpar nome;
2. remover tags técnicas;
3. identificar ano;
4. identificar episódio/temporada;
5. detectar idioma;
6. consultar cache;
7. consultar API;
8. pontuar candidatos;
9. aceitar somente acima do limiar;
10. permitir correção manual.

### 14.3 Trailers

- usar fonte autorizada;
- preferir links oficiais;
- não baixar nem republicar vídeo;
- autoplay silencioso;
- cancelar imediatamente ao perder foco;
- desabilitar em modo econômico;
- limitar pré-carregamento.

### 14.4 Falhas

Se não houver correspondência confiável:

- manter metadados da própria playlist;
- mostrar arte genérica;
- permitir edição;
- nunca associar filme errado com alta confiança visual.

---
## 15. LICENCIAMENTO E PAGAMENTO

### 15.1 Modelo inicial

- 7 dias grátis;
- sem cartão no início;
- € 9,90 de preço-base;
- compra única;
- validade de 730 dias, sem renovação automática;
- licença por dispositivo;
- restauração vinculada ao recibo/conta;
- futura opção de pacote familiar.

### 15.2 Identificação do dispositivo

Não usar endereço MAC real como única identidade.

Usar:

- Installation ID aleatório;
- par de chaves gerado no dispositivo;
- Device ID curto para digitação;
- Device Key secreta curta;
- armazenamento em Keystore/Keychain;
- assinatura de desafios do backend.

Na interface, pode aparecer:

```text
Device ID: AB12-CD34-EF56
Device Key: 8K2P-7M4Q
```

### 15.3 Portal web

Funções:

- ativar dispositivo;
- pagar;
- visualizar trial;
- verificar licença;
- renomear dispositivo;
- remover dispositivo;
- transferir licença conforme política;
- restaurar compra;
- gerir playlists opcionalmente;
- abrir suporte;
- apagar conta e dados.

### 15.4 Entitlement Service

Deve abstrair:

- Stripe;
- Google Play Billing;
- Apple StoreKit;
- Samsung Checkout;
- possíveis lojas futuras.

Estados:

- trial;
- active;
- grace;
- revoked;
- refunded;
- expired;
- blocked;
- transfer_pending.

### 15.5 Trial

- inicia no primeiro uso real, não apenas instalação;
- registrado no backend;
- cache local assinado;
- tolerância offline limitada;
- relógio do dispositivo não é fonte de verdade;
- reinstalação não deve reiniciar trial;
- sem fingerprint invasivo;
- respeitar privacidade e regras das lojas.

### 15.6 Política de transferência

Sugestão:

- uma transferência gratuita a cada 90 dias;
- exceção manual em caso de defeito;
- revogação do dispositivo anterior;
- logs de auditoria.

### 15.7 Regras de loja

- Android: produto in-app de compra única;
- Apple: compra não consumível quando vendida no app;
- Samsung: Samsung Checkout quando exigido;
- portal externo apenas onde permitido;
- backend valida recibos;
- compra deve poder ser restaurada;
- reembolso revoga entitlement quando aplicável.
