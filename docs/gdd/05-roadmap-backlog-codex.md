# IPTV BURO — GDD / PRD Técnico

## 29. ROADMAP RECOMENDADO

### Etapa 0 — Descoberta e fundação

Entregáveis:

- monorepo;
- ADRs;
- design tokens;
- modelo de domínio;
- threat model;
- CI;
- fixtures legais;
- protótipo navegável;
- spike de Media3.

### Etapa 1 — Núcleo Android TV

Entregáveis:

- onboarding;
- importação M3U;
- banco local;
- home;
- live TV;
- player;
- EPG;
- favoritos;
- histórico;
- configurações.

### Etapa 2 — VOD premium

Entregáveis:

- filmes;
- séries;
- detalhes;
- TMDb;
- capas;
- trailers;
- continuar assistindo;
- pesquisa;
- perfis.

### Etapa 3 — Licença e portal

Entregáveis:

- Device ID;
- trial;
- portal;
- Stripe;
- entitlement;
- restore;
- transferência;
- suporte.

### Etapa 4 — Qualidade

Entregáveis:

- Stream Health Engine;
- telemetria segura;
- testes de rede;
- performance;
- acessibilidade;
- beta;
- correções.

### Etapa 5 — Android mobile e Fire TV

Entregáveis:

- UI touch;
- compra;
- sincronização opcional;
- build Fire TV;
- testes.

### Etapa 6 — Desktop

Entregáveis:

- Windows;
- macOS;
- atalhos;
- atualização;
- picture-in-picture;
- player desktop.

### Etapa 7 — Apple

Entregáveis:

- iOS;
- tvOS;
- AVPlayer;
- StoreKit;
- aprovação.

### Etapa 8 — Smart TVs

Entregáveis:

- Samsung Tizen/AVPlay;
- LG webOS;
- Samsung Checkout;
- testes em dispositivos reais;
- certificação.

---
## 30. ESTIMATIVA DE COMPLEXIDADE

Um Codex pode produzir grande parte do código, testes, documentação e automação, mas não elimina:

- testes em TVs reais;
- contas de desenvolvedor;
- certificados;
- revisão das lojas;
- validação jurídica;
- testes com streams licenciados;
- suporte;
- design final;
- correções específicas de codecs e firmware.

Estimativa realista para uma primeira versão comercial Android TV bem testada:

- fundação: 1 a 2 semanas;
- núcleo player/catálogo: 4 a 6 semanas;
- portal/licença: 2 a 4 semanas;
- acabamento e QA: 3 a 5 semanas.

Estimativa total orientativa: **10 a 17 semanas** para Android TV + portal, dependendo da qualidade exigida, disponibilidade de hardware e quantidade de retrabalho.

Multiplataforma completo é um programa de produto contínuo, não um único build.

---
## 31. BACKLOG PRIORIZADO

### P0

- [ ] monorepo;
- [ ] CI;
- [ ] Android TV shell;
- [ ] navegação D-pad;
- [ ] parser M3U;
- [ ] Xtream client;
- [ ] XMLTV;
- [ ] Room;
- [ ] cache local;
- [ ] Media3;
- [ ] live TV;
- [ ] VOD;
- [ ] seek capabilities;
- [ ] favoritos;
- [ ] progresso;
- [ ] pesquisa local;
- [ ] perfis;
- [ ] parental;
- [ ] Device ID;
- [ ] trial;
- [ ] pagamento;
- [ ] portal;
- [ ] restore;
- [ ] logs seguros;
- [ ] testes.

### P1

- [ ] TMDb;
- [ ] trailers;
- [ ] recomendações;
- [ ] múltiplas fontes;
- [ ] catch-up;
- [ ] multiview;
- [ ] PIP;
- [ ] sync criptografado;
- [ ] Android mobile;
- [ ] Fire TV.

### P2

- [ ] DVR;
- [ ] timeshift;
- [ ] desktop;
- [ ] iOS/tvOS;
- [ ] Tizen;
- [ ] webOS;
- [ ] controle remoto móvel;
- [ ] IA semântica;
- [ ] pacotes familiares.

---
## 32. REGRAS PARA O CODEX

1. não tentar implementar todas as plataformas de uma vez;
2. começar pelo Android TV;
3. criar ADR antes de decisão arquitetural importante;
4. código em inglês;
5. documentação de produto em português;
6. sem TODO silencioso;
7. sem mocks em produção;
8. testes para parsers, licença e progresso;
9. cada feature deve ter critério de aceitação;
10. cada PR deve ser pequeno;
11. não armazenar credenciais em log;
12. não incluir playlists reais;
13. usar fixtures sintéticas ou conteúdo aberto;
14. medir performance;
15. executar lint, testes e build antes de concluir;
16. registrar limitações;
17. não copiar código sem verificar licença;
18. não copiar interface de concorrente;
19. não adicionar dependência sem justificativa;
20. manter changelog.

---
## 33. PRIMEIRA SPRINT PARA O CODEX

### Objetivo

Criar a fundação executável do Android TV e provar reprodução legal de uma fonte de teste.

### Tarefas

1. inicializar monorepo;
2. criar documentação;
3. criar app Android TV;
4. configurar Compose for TV;
5. configurar Media3;
6. criar navegação básica;
7. criar modelo Source;
8. criar parser M3U mínimo;
9. carregar fixture local;
10. listar canais;
11. reproduzir HLS público autorizado;
12. implementar foco D-pad;
13. implementar logs com redaction;
14. criar testes;
15. configurar CI.

### Definition of Done

- build debug funciona;
- teste unitário funciona;
- fixture aparece;
- canal reproduz;
- D-pad funciona;
- nenhum segredo está no repositório;
- README explica execução;
- ADR-001 documenta arquitetura inicial.
