# IPTV BURO — GDD / PRD Técnico

## 16. BACKEND

### 16.1 O backend deve armazenar

- contas opcionais;
- dispositivos;
- licenças;
- transações;
- recibos verificados;
- trial;
- configurações não sensíveis;
- chaves públicas de dispositivos;
- consentimentos;
- tickets;
- dados de telemetria agregados.

### 16.2 O backend não deve armazenar por padrão

- username IPTV;
- password IPTV;
- URL completa com credenciais;
- lista completa;
- histórico detalhado associado à identidade;
- conteúdo de vídeo;
- frames;
- áudio.

### 16.3 Sincronização opcional

Se o usuário habilitar sync:

- criptografia ponta a ponta;
- chave derivada no cliente;
- servidor armazena blob cifrado;
- recovery key exibida ao usuário;
- backend não consegue ler playlists.

### 16.4 API inicial

```text
POST   /v1/devices/register
POST   /v1/devices/challenge
POST   /v1/devices/verify
GET    /v1/devices/:id/entitlement
POST   /v1/trials/start
POST   /v1/licenses/activate
POST   /v1/licenses/transfer
POST   /v1/purchases/stripe/checkout
POST   /v1/webhooks/stripe
POST   /v1/webhooks/google
POST   /v1/webhooks/apple
POST   /v1/webhooks/samsung
POST   /v1/purchases/restore
DELETE /v1/account
POST   /v1/sync/upload
GET    /v1/sync/download
```

### 16.5 Banco de dados

Tabelas:

- users;
- devices;
- device_keys;
- trials;
- products;
- purchases;
- entitlements;
- entitlement_events;
- license_transfers;
- webhook_events;
- support_tickets;
- consent_records;
- sync_blobs;
- audit_logs.

---
## 17. MODELO DE DADOS LOCAL

Principais entidades:

```text
Source
Playlist
Category
Channel
Program
Movie
Series
Season
Episode
Profile
Favorite
WatchProgress
WatchHistory
AudioPreference
SubtitlePreference
ParentalRule
MetadataMatch
StreamCapability
StreamMetric
LicenseState
AppSetting
```

### 17.1 Regras

- IDs locais estáveis;
- namespace por fonte;
- deduplicação sem destruir origem;
- migrations versionadas;
- cache invalidável;
- atualização incremental;
- parsing em lotes;
- índices para busca;
- full-text search local.

---
## 18. ATUALIZAÇÃO DE PLAYLIST E EPG

### 18.1 Estratégia phased loading

1. abrir catálogo local;
2. carregar favoritos e continuar assistindo;
3. validar licença em paralelo;
4. consultar mudança da playlist;
5. baixar incrementalmente;
6. parsear em background;
7. atualizar banco em transação;
8. atualizar UI por diff;
9. baixar imagens sob demanda;
10. atualizar EPG.

### 18.2 Frequência

Configurável:

- ao abrir;
- 6 horas;
- 12 horas;
- 24 horas;
- manual;
- somente Wi-Fi;
- nunca em reprodução.

### 18.3 Falha de atualização

- nunca apagar catálogo funcional;
- manter última versão válida;
- mostrar data da última atualização;
- permitir tentar novamente;
- registrar erro sanitizado.

---
## 19. DESEMPENHO

### 19.1 Metas MVP

- cold start em Android TV médio: menor que 3 segundos;
- warm start: menor que 1,5 segundo;
- home interativa antes de terminar a atualização;
- primeiro frame VOD: meta mediana abaixo de 2,5 segundos;
- troca de canal: meta mediana abaixo de 2 segundos;
- rolagem de catálogo: 60 fps onde o hardware permitir;
- memória alvo em TV: menor que 300 MB;
- nenhuma lista completa mantida duplicada em memória;
- banco local preparado para 100 mil itens;
- imagens em tamanhos adaptados;
- crash-free sessions acima de 99,5%.

### 19.2 Dispositivos fracos

Modo automático:

- desabilitar trailer background;
- reduzir blur;
- reduzir resolução de imagem;
- limitar rails;
- limitar prefetch;
- reduzir animação;
- descarregar telas anteriores;
- usar paginação;
- limitar multiview.

---
## 20. ACESSIBILIDADE

- contraste adequado;
- tamanho de fonte;
- suporte a leitor de tela;
- foco explícito;
- redução de movimento;
- legendas;
- descrição de áudio quando presente;
- atalhos;
- navegação completa sem toque;
- feedback sonoro opcional;
- tempo suficiente para leitura.

---
## 21. PRIVACIDADE E SEGURANÇA

### 21.1 Credenciais

- criptografadas no dispositivo;
- nunca em texto puro no banco;
- nunca em analytics;
- nunca em screenshots automáticos;
- opção de ocultar na tela;
- clipboard limpo opcionalmente após importação.

### 21.2 Logs

Redigir:

- query strings;
- Authorization;
- username;
- password;
- tokens;
- URLs assinadas;
- IPs quando não necessários.

### 21.3 Rede

- TLS;
- certificate pinning somente se operação permitir rotação segura;
- timeouts;
- limitação de redirecionamentos;
- bloqueio de esquemas perigosos;
- proteção SSRF no backend;
- rate limiting;
- validação de webhooks;
- idempotência;
- replay protection.

### 21.4 GDPR

- minimização;
- consentimento;
- exportação de dados;
- exclusão de conta;
- política de retenção;
- DPA com fornecedores;
- registro de sub-processadores;
- telemetria opt-in onde necessário;
- privacy policy clara.

---
## 22. TELEMETRIA

### 22.1 Eventos permitidos

- app_open;
- screen_view;
- import_started;
- import_completed;
- playback_started;
- playback_failed;
- first_frame_ms;
- rebuffer_count;
- seek_failed;
- purchase_started;
- purchase_completed;
- trial_started;
- crash;
- feature_used.

### 22.2 Proibições

Não enviar:

- título adulto associado ao usuário;
- URL de stream;
- credenciais;
- lista de canais;
- histórico individual completo;
- conteúdo do EPG.

### 22.3 Métricas de produto

- ativação do trial;
- importação concluída;
- tempo até primeiro conteúdo;
- conversão trial → pago;
- retenção D1/D7/D30;
- falhas de playback por plataforma;
- tempo de troca;
- uso de busca;
- uso de favoritos;
- restore success;
- refund rate.

---
## 23. TELAS DO MVP

1. Splash;
2. seleção de idioma;
3. aviso legal;
4. Device ID/Device Key e status do trial;
5. adicionar fonte;
6. M3U URL;
7. M3U arquivo;
8. Xtream login;
9. progresso de importação;
10. seleção/criação de perfil;
11. Home;
12. TV ao vivo;
13. guia EPG;
14. filmes;
15. séries;
16. detalhes;
17. pesquisa;
18. player;
19. favoritos;
20. continuar assistindo;
21. configurações;
22. áudio/legenda;
23. controle parental;
24. diagnóstico;
25. ativar licença;
26. restauração;
27. privacidade;
28. sobre.
