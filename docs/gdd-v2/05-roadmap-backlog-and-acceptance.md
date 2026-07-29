# GDD 2.0 — 05. Roadmap, backlog e critérios de aceitação

## 1. Estratégia de execução

O Codex já está trabalhando na fundação do GDD 1.0. Portanto, este plano é incremental.

O desenvolvimento deve seguir uma linha vertical: entregar uma jornada completa e refinada antes de espalhar telas incompletas por todo o produto.

Ordem de prioridade:

1. auditar o que já existe;
2. estabilizar build e arquitetura;
3. criar design system e shell premium;
4. entregar uma home real com dados locais;
5. conectar detalhes e player;
6. revolucionar TV ao vivo;
7. adicionar inteligência e continuidade;
8. otimizar hardware fraco;
9. expandir plataformas.

---

## 2. Fase 0 — Auditoria do trabalho atual

### Objetivo

Entender o que o Codex já criou e evitar retrabalho.

### Tarefas

- executar `git status` e identificar branch;
- listar estrutura do repositório;
- ler GDD 1.0 e 2.0;
- executar build e testes;
- documentar módulos existentes;
- identificar dívida técnica;
- mapear telas já criadas;
- identificar player, parsers e modelos existentes;
- gerar `docs/status/CURRENT_IMPLEMENTATION.md`;
- gerar matriz `Existing / Partial / Missing / Conflicting`;
- registrar ADRs necessários;
- não apagar código funcional nesta fase.

### Critérios de aceitação

- build reproduzível ou blockers documentados;
- inventário completo;
- próximos commits têm escopo claro;
- nenhuma alteração destrutiva sem justificativa.

---

## 3. Fase 1 — BURO Cinematic Foundation

### Objetivo

Criar a identidade visual e o comportamento de navegação que diferenciam o produto.

### Escopo P0

- tokens semânticos;
- tipografia TV;
- spacing e shapes;
- performance tiers;
- foco padrão;
- BURO Ribbon;
- componentes de card;
- hero;
- skeletons;
- estados vazio/erro;
- navegação e restauração de foco;
- reduced motion;
- preview de componentes;
- screenshot tests.

### Entrega vertical

Uma tela demonstrativa com:

- Ribbon;
- hero;
- duas fileiras;
- cards poster e landscape;
- navegação completa por D-pad;
- transições nos três tiers;
- sem dependência de playlist real.

### Critérios de aceitação

- foco visível em 100% dos elementos;
- nenhuma cor/tamanho arbitrário fora de tokens;
- navegação previsível;
- 60 fps no aparelho de referência em Balanced;
- Eco permanece funcional;
- screenshots golden aprovados;
- contraste validado.

---

## 4. Fase 2 — Living Home vertical slice

### Objetivo

Entregar a primeira experiência que já parece um serviço premium.

### Escopo P0

- `HomeComposer`;
- cache local;
- hero real;
- Continuar assistindo;
- Ao vivo agora;
- favoritos;
- filmes e séries;
- módulos loading/empty/error;
- atualização sem perder foco;
- Ambient Color Engine básico;
- preview mudo atrás de flag;
- Minha BURO básica.

### Critérios de aceitação

- home abre com cache mesmo sem rede;
- não existe tela branca;
- hero não troca durante interação;
- atualização de EPG não move foco;
- card leva a detalhes;
- continuar leva à posição correta;
- perfil infantil não recebe item bloqueado;
- P50 de home interativa dentro da meta definida.

---

## 5. Fase 3 — Story Page + Flow Player

### Objetivo

Fazer seleção e reprodução parecerem uma única jornada cinematográfica.

### Escopo P0

- Story Page;
- canonical work + versões;
- CTA Play/Continuar;
- temporadas e episódios;
- áudio e legendas;
- player overlay;
- timeline por capacidade;
- Quality Autopilot básico;
- recuperação de erro;
- diagnóstico;
- progresso persistido;
- autoplay configurável;
- próximo episódio.

### Critérios de aceitação

- nenhum botão de seek falso;
- player mantém lifecycle correto;
- erro recuperável não fecha app;
- versão escolhida possui motivo;
- áudio e legenda persistem por perfil;
- sair e voltar restaura posição;
- URL e senha ausentes de logs;
- controles desaparecem sem capturar foco invisível.

---

## 6. Fase 4 — BURO Pulse

### Objetivo

Criar a melhor experiência de TV ao vivo do produto.

### Escopo P0/P1

- tela Ao Vivo editorial;
- mini-guia;
- guia completo virtualizado;
- agora/próximo;
- favoritos;
- channel recall;
- troca rápida;
- catch-up quando suportado;
- lembretes locais;
- Event Hub básico;
- métricas de channel switch;
- prefetch experimental por tier.

### Critérios de aceitação

- canal anterior em uma ação;
- mini-guia sem interromper vídeo;
- EPG grande permanece fluido;
- catch-up só aparece quando válido;
- canal sem EPG continua acessível;
- troca cancela tentativa anterior;
- recursos são liberados ao sair;
- nenhuma varredura agressiva de streams.

---

## 7. Fase 5 — Catalog Brain + BURO Lens

### Objetivo

Transformar catálogos desorganizados em um sistema pesquisável e coerente.

### Escopo P0/P1

- pipeline de normalização;
- identidade canônica;
- deduplicação;
- confidence score;
- correção manual;
- índice local;
- fuzzy search;
- agrupamento por tipo;
- filtros;
- busca por intenção estruturada;
- Playlist Health Report;
- versões alternativas.

### Critérios de aceitação

- original da fonte é preservado;
- match de baixa confiança não sobrescreve silenciosamente;
- busca retorna durante digitação;
- catálogo grande não congela UI;
- duplicados são agrupados de forma reversível;
- usuário pode desfazer correção;
- conteúdo adulto é sinalizado para revisão parental.

---

## 8. Fase 6 — Perfis, Kids e personalização

### Objetivo

Oferecer uma experiência individual e segura para famílias.

### Escopo P0/P1

- perfis adulto, infantil, convidado e administrador;
- PIN;
- allowlist/denylist;
- classificação máxima;
- histórico separado;
- preferências;
- Recommendation Core local;
- “por que isto?”;
- menos disso;
- limpar aprendizado;
- BURO Mood básico;
- home contextual.

### Critérios de aceitação

- perfil infantil não acessa configurações sensíveis;
- conteúdo sem classificação obedece política escolhida;
- histórico não vaza entre perfis;
- recomendações podem ser desativadas;
- usuário entende o motivo;
- ordem da home não muda caoticamente.

---

## 9. Fase 7 — Continuity Mesh + Companion

### Objetivo

Fazer TV, portal e celular parecerem partes do mesmo produto.

### Escopo P1

- conta opcional;
- pareamento por QR;
- dispositivos;
- progresso sincronizado;
- favoritos e lista;
- preferências;
- revogação;
- controle remoto pelo celular;
- busca pelo celular;
- enviar para TV;
- gerenciamento de fontes no portal;
- criptografia de segredos.

### Critérios de aceitação

- pareamento expira;
- TV pede confirmação;
- aparelho pode ser revogado;
- conflito de progresso é resolvido;
- uso offline continua;
- credenciais não trafegam sem proteção adequada;
- conta não é obrigatória para reprodução local.

---

## 10. Fase 8 — Recursos premium experimentais

### Escopo P1/P2

- MultiView;
- Moments;
- artworks alternativos;
- trailers contextuais;
- Event Hub avançado;
- busca por voz;
- recomendações semânticas locais;
- integrações autorizadas;
- desktop/mobile shell.

Cada item exige feature flag, métricas e fallback.

---

## 11. Backlog de épicos

### EPIC-UX-001 — BURO Cinematic System

- tokens;
- focus physics;
- Ribbon;
- motion;
- cards;
- hero;
- acessibilidade;
- performance tiers.

### EPIC-HOME-001 — Living Home

- HomeDocument;
- composer;
- módulos;
- cache;
- hero;
- ambient color;
- atualizações incrementais.

### EPIC-CATALOG-001 — Universal Content Graph

- entidades;
- canonical work;
- versões;
- deduplicação;
- metadata confidence.

### EPIC-PLAYER-001 — Flow Player

- orchestrator;
- state machine;
- timeline;
- tracks;
- recovery;
- diagnostics.

### EPIC-LIVE-001 — BURO Pulse

- live home;
- mini-guide;
- EPG;
- zapping;
- recall;
- catch-up;
- reminder.

### EPIC-SEARCH-001 — BURO Lens

- index;
- fuzzy;
- ranking;
- voice;
- filters;
- intent parser.

### EPIC-PROFILE-001 — Profiles and Kids

- profile store;
- PIN;
- restrictions;
- history isolation;
- preferences.

### EPIC-REC-001 — Recommendation Core

- signals;
- scoring;
- reasons;
- feedback;
- diversity;
- privacy.

### EPIC-SYNC-001 — Continuity Mesh

- event log;
- sync;
- conflict resolution;
- device auth;
- encryption.

### EPIC-COMPANION-001 — Second screen

- pairing;
- remote;
- keyboard;
- send-to-TV;
- portal.

### EPIC-QUALITY-001 — Quality Autopilot

- observations;
- playback plans;
- device memory;
- recovery policies;
- user explanations.

---

## 12. Histórias P0 essenciais

### HOME-001

Como usuário, quero ver a home em cache imediatamente para não esperar a atualização completa da fonte.

**Aceite:** shell e módulos disponíveis aparecem antes da sincronização remota; indicador discreto informa atualização.

### HOME-002

Como usuário, quero retomar um filme diretamente da home.

**Aceite:** card mostra progresso real e abre na posição salva, respeitando conflito de progresso.

### LIVE-001

Como usuário, quero trocar de canal sem sair do player.

**Aceite:** D-pad muda canal, tentativa anterior é cancelada e mini-overlay mostra agora/próximo.

### PLAYER-001

Como usuário, quero entender por que não consigo avançar.

**Aceite:** timeline representa corretamente a capacidade e explica de forma curta quando seek não existe.

### CATALOG-001

Como usuário, quero ver duplicados como versões da mesma obra.

**Aceite:** uma página agrega versões e permite escolher origem.

### SEARCH-001

Como usuário, quero encontrar conteúdo mesmo digitando o nome errado.

**Aceite:** fuzzy search retorna opções relevantes sem travar digitação.

### KIDS-001

Como responsável, quero impedir acesso a conteúdo não permitido.

**Aceite:** PIN, classificação, categorias e conteúdo não classificado seguem a política configurada.

### QUALITY-001

Como usuário, quero que o app tente recuperar uma falha automaticamente.

**Aceite:** recovery limitado, estado informado e nenhuma repetição infinita.

---

## 13. Critérios visuais obrigatórios

Uma tela principal falha na revisão se:

- parece uma grade genérica;
- usa apenas cards iguais;
- foco é difícil de ver;
- backdrop reduz legibilidade;
- existe informação técnica na área comum;
- loading bloqueia a tela inteira;
- o botão Voltar não é previsível;
- a tela quebra sem poster/trailer/EPG;
- animação causa stutter no tier correto;
- navegação depende de toque;
- não possui versão Eco.

---

## 14. Critérios de produto revolucionário

Antes de declarar “GDD 2.0 implementado”, demonstrar em vídeo ou teste real:

1. abrir app e navegar pela home premium;
2. selecionar um conteúdo, ver detalhes e reproduzir;
3. retomar progresso;
4. entrar em live, abrir mini-guia e trocar canal;
5. lidar com stream sem seek;
6. pesquisar com erro ortográfico;
7. agrupar duplicados;
8. trocar perfil e validar Kids;
9. reduzir efeitos em hardware fraco;
10. parear celular ou portal quando a fase estiver pronta;
11. mostrar diagnóstico sem credenciais;
12. navegar tudo somente com D-pad.

---

## 15. Estimativa orientativa

Para um desenvolvedor principal usando Codex, testes reais e escopo disciplinado:

- auditoria e fundação visual: 2–4 semanas;
- home, detalhes e player premium: 3–5 semanas;
- live e EPG avançados: 2–4 semanas;
- catálogo, busca e perfis: 3–5 semanas;
- sync/companion: 3–6 semanas;
- otimização, testes e publicação: 3–6 semanas.

Total orientativo da versão Android TV premium: **13–24 semanas**, dependendo do estado do código, fontes de metadados, dispositivos de teste e complexidade do backend.

O Codex pode acelerar código e testes, mas não elimina validação em TVs reais, licenciamento de assets, políticas de loja e refinamento de design.

---

## 16. Sequência de commits recomendada

1. `docs: audit current IPTV BURO implementation`
2. `feat(design): add BURO cinematic tokens and focus system`
3. `feat(shell): add BURO Ribbon and navigation restoration`
4. `feat(home): add HomeDocument and Living Home`
5. `feat(details): add canonical Story Page`
6. `feat(player): add Flow Player state machine`
7. `feat(live): add BURO Pulse and mini guide`
8. `feat(catalog): add normalization and canonical graph`
9. `feat(search): add local universal search`
10. `feat(profiles): add profiles and parental controls`
11. `feat(recommendation): add explainable local ranking`
12. `feat(sync): add encrypted continuity foundation`
13. `perf: add TV performance tiers and benchmarks`
14. `test: complete premium journey coverage`

Cada commit deve compilar e manter o projeto utilizável.
