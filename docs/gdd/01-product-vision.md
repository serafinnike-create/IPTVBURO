# GDD / PRD TÉCNICO — PROJETO IPTV BURO

**Versão:** 1.0  
**Data:** 29 de julho de 2026  
**Status:** Documento-base para desenvolvimento com Codex  
**Codinome:** IPTV BURO  
**Categoria:** Reprodutor OTT/IPTV multiplataforma  
**Modelo comercial inicial:** teste gratuito de 7 dias + licença vitalícia de € 9,99 por dispositivo  
**Princípio jurídico:** o produto é somente um player. Não fornece canais, filmes, séries, listas, assinaturas ou conteúdo protegido.

---
## 1. RESUMO EXECUTIVO

O IPTV BURO será um reprodutor OTT/IPTV premium, leve e multiplataforma, criado para transformar listas e credenciais fornecidas legalmente pelo usuário em uma experiência visual comparável a serviços como Netflix e Prime Video.

O aplicativo não será apenas “mais um IPTV player”. Seu posicionamento será:

> **A experiência de streaming premium para o conteúdo que o usuário já possui.**

O produto combina quatro referências de mercado:

1. **IBO Player:** ativação simples por dispositivo e período de teste.
2. **IPTVX/UHF:** experiência visual premium, metadados, capas, trailers, continuar assistindo e múltiplos perfis.
3. **TiviMate/Sparkle TV:** televisão ao vivo, EPG, troca rápida de canais, favoritos, catch-up, timeshift e navegação por controle remoto.
4. **IPTVnator e projetos open source modernos:** arquitetura multiplataforma, atualização automática de playlists, suporte a diferentes fontes e enriquecimento por metadados.

O diferencial técnico central será um **Stream Health Engine**, responsável por analisar a fonte, selecionar o player adequado, ajustar buffers, detectar se o conteúdo permite seek, recuperar falhas temporárias e manter histórico técnico por stream sem enviar credenciais sensíveis ao servidor.

---
## 2. VISÃO DO PRODUTO

### 2.1 Problema

A maioria dos players IPTV apresenta um ou mais destes problemas:

- interface visual genérica ou antiquada;
- carregamento lento de listas grandes;
- travamentos ao trocar canais;
- busca ruim;
- EPG confuso;
- dificuldade para avançar ou retroceder VOD;
- ausência de perfis;
- controles parentais superficiais;
- falta de sincronização entre dispositivos;
- configurações técnicas pouco compreensíveis;
- reprodução inconsistente entre TV, celular e computador;
- ativação e pagamento pouco transparentes;
- consumo excessivo de memória em Smart TVs mais fracas.

### 2.2 Solução

Criar um player local-first que:

- abre rapidamente usando dados em cache;
- atualiza playlists e EPG em segundo plano;
- apresenta filmes e séries em interface cinematográfica;
- separa claramente TV ao vivo, filmes, séries, esportes, favoritos e histórico;
- usa player nativo de cada plataforma;
- mantém uma camada de domínio compartilhada;
- protege credenciais;
- permite ativação por Device ID/Device Key em um portal web;
- funciona bem com controle remoto, mouse, teclado e toque;
- reduz custo de backend ao não retransmitir vídeos.

### 2.3 Promessa ao usuário

- **Bonito como um streaming premium.**
- **Rápido como um aplicativo nativo.**
- **Simples para configurar.**
- **Privado: suas credenciais permanecem no seu dispositivo.**
- **Sem assinatura mensal do player.**

---
## 3. LIMITES LEGAIS E DE PRODUTO

### 3.1 O produto deve fazer

- reproduzir fontes configuradas pelo usuário;
- importar M3U/M3U8;
- importar Xtream-compatible APIs;
- importar XMLTV/EPG;
- opcionalmente suportar Stalker/Ministra em fase posterior;
- enriquecer metadados por APIs licenciadas;
- oferecer organização, pesquisa, perfis e reprodução;
- informar claramente que não fornece conteúdo.

### 3.2 O produto não deve fazer

- incluir listas públicas ou privadas pré-carregadas;
- vender canais, filmes ou séries;
- recomendar fornecedores não licenciados;
- compartilhar playlists entre usuários;
- retransmitir streams pelo backend;
- esconder a origem do conteúdo;
- contornar DRM, bloqueios geográficos ou autenticação;
- baixar conteúdo sem autorização explícita da fonte;
- usar marca, logotipo, layout ou trade dress da Netflix, Prime Video ou concorrentes;
- armazenar credenciais IPTV em logs, analytics ou crash reports.

### 3.3 Texto obrigatório no onboarding

> O IPTV BURO é apenas um reprodutor de mídia. Ele não fornece canais, filmes, séries, listas ou assinaturas. Você deve possuir autorização legal para acessar todo conteúdo adicionado ao aplicativo.

---
## 4. PÚBLICO-ALVO

### 4.1 Usuário principal

Pessoa que já possui uma fonte legal de TV/VOD e deseja uma experiência melhor em Smart TV, Android TV, celular ou computador.

### 4.2 Perfis secundários

- famílias com perfis separados;
- usuários multilíngues;
- pessoas que usam várias playlists;
- usuários com grandes catálogos;
- usuários que precisam de legendas ou faixas de áudio alternativas;
- pessoas que desejam organizar canais favoritos;
- usuários de Android TV, Google TV, Fire TV, Samsung TV, LG TV, Windows, macOS, iOS e Android.

### 4.3 Idiomas iniciais

- português do Brasil;
- alemão;
- italiano;
- inglês.

Arquitetura de internacionalização deve existir desde o primeiro commit.

---
## 5. BENCHMARK FUNCIONAL

### 5.1 IBO Player — referência de ativação

Elementos a aproveitar conceitualmente:

- teste automático após instalação;
- identificação simples do dispositivo;
- portal web para ativação;
- pagamento único;
- tela inicial que mostra o status da licença.

Melhoria planejada:

- usar **Device ID + Device Key**, e não depender do endereço MAC real;
- permitir restauração de compra;
- portal com conta opcional;
- histórico de dispositivos;
- revogação e transferência controlada;
- segurança criptográfica por dispositivo.

### 5.2 IPTVX — referência de catálogo premium

Elementos a aproveitar conceitualmente:

- detalhes de filmes e séries;
- continuar assistindo;
- recentemente adicionados;
- busca global;
- EPG;
- catch-up;
- múltiplas playlists;
- áudio, legenda e controle parental;
- metadados e artes externas.

Melhoria planejada:

- inicialização imediata usando cache local;
- atualização de catálogo em fases;
- perfis completos;
- menor consumo de memória;
- compatibilidade mais ampla fora do ecossistema Apple.

### 5.3 UHF — referência de acabamento

Elementos a aproveitar conceitualmente:

- interface limpa;
- sensação de produto moderno;
- pesquisa unificada;
- múltiplas fontes;
- foco em reprodução e gestão de conteúdo;
- atualizações frequentes.

Melhoria planejada:

- licença única em vez de dependência de assinatura;
- portal de ativação;
- suporte amplo a TVs;
- arquitetura local-first.

### 5.4 Sparkle TV — referência de Live TV

Elementos a aproveitar conceitualmente:

- EPG completo;
- múltiplas faixas de áudio;
- legendas;
- auto frame rate;
- timeshift;
- DVR;
- multiview;
- múltiplas fontes;
- personalização do controle remoto.

Melhoria planejada:

- VOD com experiência cinematográfica;
- perfis;
- trailers;
- metadados;
- sincronização e portal web.

### 5.5 IPTVnator — referência open source

Elementos a estudar, sem copiar código incompatível:

- atualização automática de listas;
- suporte M3U, Xtream e Stalker;
- players intercambiáveis;
- EPG;
- catch-up;
- histórico;
- continuar assistindo;
- enriquecimento por TMDb;
- arquitetura desktop/PWA.

### 5.6 Regra de design

O IPTV BURO pode aprender padrões de navegação, mas deve possuir identidade própria. Não deve ser um clone visual da Netflix.

---
## 6. PROPOSTA DE VALOR E DIFERENCIAIS

### 6.1 Diferenciais P0

1. abertura instantânea com catálogo em cache;
2. TV ao vivo com troca rápida;
3. VOD com seek confiável quando a fonte permite;
4. interface premium para TV;
5. ativação simples;
6. perfis e modo infantil;
7. atualização silenciosa de playlist e EPG;
8. credenciais criptografadas localmente;
9. diagnóstico de stream compreensível;
10. player nativo por plataforma.

### 6.2 Diferenciais P1

- trailers automáticos com reprodução silenciosa;
- recomendações locais;
- deduplicação de canais e títulos;
- mapeamento automático de EPG;
- sincronização criptografada;
- múltiplas fontes;
- multiview;
- catch-up;
- picture-in-picture;
- temas;
- comando de voz onde disponível.

### 6.3 Diferenciais P2

- DVR local;
- timeshift local;
- download autorizado para uso offline;
- recomendações por IA on-device;
- controle pelo celular;
- integração com a tela inicial do Android/Google TV;
- integração com assistentes e deep links;
- pacotes familiares de licença.

---
## 7. DEFINIÇÃO DE “APLICATIVO COM IA”

A IA não deve ser um chatbot caro colocado no aplicativo sem necessidade.

### 7.1 IA local e barata

A primeira versão pode chamar de “inteligente” um conjunto de recursos locais:

- normalização de nomes;
- remoção de tags como 4K, HEVC, idioma e país;
- correspondência entre canal e EPG;
- correspondência entre VOD e TMDb;
- agrupamento de duplicados;
- recomendação baseada em histórico local;
- seleção automática de idioma de áudio e legenda;
- detecção de provável conteúdo infantil/adulto;
- ordenação de resultados por relevância;
- detecção de streams instáveis;
- sugestão de qualidade baseada no dispositivo e rede.

### 7.2 IA cloud opcional

Somente em versão futura ou plano adicional:

- busca semântica;
- recomendações avançadas;
- resumo de sinopse;
- criação de coleções personalizadas.

### 7.3 Restrição comercial

Uma licença única de € 9,99 não sustenta inferência cloud ilimitada para sempre. Portanto:

- P0 e P1 devem ser local-first;
- metadados devem usar cache;
- o backend nunca deve processar vídeo;
- funções cloud caras devem possuir limite, patrocínio ou plano opcional futuro.

---
## 8. PLATAFORMAS E ORDEM DE LANÇAMENTO

### 8.1 Fase 1 — Android TV / Google TV

Primeiro alvo recomendado.

Motivos:

- cobre TVs e boxes Android;
- cobre muitos modelos Sony BRAVIA com Google TV/Android TV;
- permite usar Media3 ExoPlayer;
- oferece bom ambiente de testes;
- permite publicar no Google Play;
- pode ser adaptado para Fire TV Android existente.

### 8.2 Fase 2 — Android mobile

Compartilha domínio e player com Android TV, mas possui UI própria para toque.

### 8.3 Fase 3 — Windows e macOS

Aplicativo desktop com:

- teclado e mouse;
- janela redimensionável;
- picture-in-picture;
- atalhos;
- player nativo/libmpv conforme necessidade.

### 8.4 Fase 4 — iOS e tvOS

Player com AVPlayer/AVFoundation, StoreKit e integração nativa.

### 8.5 Fase 5 — Samsung Tizen

Aplicativo Web Tizen com adaptador de vídeo AVPlay.

### 8.6 Fase 6 — LG webOS

Aplicativo webOS ou Flutter webOS, validado em hardware real.

### 8.7 Fase 7 — Web/PWA

O navegador deve ser tratado como plataforma limitada. Nem todos os codecs, headers ou streams funcionam no browser. O web app serve principalmente para:

- portal de ativação;
- gestão de dispositivos;
- playlists;
- configurações;
- reprodução apenas quando o navegador suportar a fonte.
