# IPTV BURO — GDD / PRD Técnico

## 34. RISCOS

### Alto

- rejeição em lojas por descrição ambígua;
- incompatibilidade de codecs;
- streams sem seek;
- custo de suporte;
- fraude de licença;
- TVs com pouca memória;
- dependência de API de metadados;
- fragmentação de Smart TVs.

### Médio

- trailers lentos;
- matching incorreto;
- EPG ruim;
- listas enormes;
- pagamentos regionais;
- restore;
- migrações.

### Mitigação

- comunicação legal clara;
- player adapter;
- matriz de hardware;
- cache;
- feature flags;
- rollout gradual;
- telemetria segura;
- testes reais;
- backend mínimo;
- beta fechado.

---
## 35. DECISÃO FINAL DE PRODUTO

A primeira versão não deve tentar ser “Netflix + IPTV + IA + todas as TVs” simultaneamente.

A sequência correta é:

1. **Android TV excelente;**
2. **portal e licença confiáveis;**
3. **VOD premium;**
4. **qualidade e diagnóstico;**
5. **expansão por plataforma.**

A meta do MVP é provar três coisas:

- o usuário consegue configurar sem ajuda;
- a reprodução é mais estável e transparente;
- a interface é claramente superior aos players genéricos.

---
## 36. FONTES DE REFERÊNCIA DA PESQUISA

Pesquisa realizada em 29 de julho de 2026 com base em documentação e páginas públicas de:

- Apple App Store — UHF e IPTVX;
- Google Play — Sparkle TV;
- IBO Player;
- IPTVnator no GitHub;
- clubTivi no GitHub;
- Android Media3/ExoPlayer;
- Samsung Smart TV AVPlay;
- LG webOS TV;
- Flutter;
- Google Play Billing;
- Apple StoreKit e App Review Guidelines;
- Samsung Checkout;
- TMDb API.

Este documento usa as fontes apenas como benchmark e documentação técnica. Nenhuma interface ou código deve ser copiado sem análise de licença e propriedade intelectual.
