# GDD 2.0 — 01. Tese, princípios, público e métricas

## 1. Ambição

O IPTV BURO deve ocupar uma categoria própria: **Entertainment Operating System**.

Ele não vende canais, filmes ou séries. Ele é um cliente independente que organiza e reproduz fontes legais configuradas pelo usuário. Sua vantagem comercial é transformar uma experiência normalmente técnica, desorganizada e visualmente fraca em um ambiente premium, simples e confiável.

A comparação correta não é apenas com outros players IPTV. A comparação de qualidade percebida deve incluir:

- Netflix: descoberta, continuidade e personalização;
- Apple TV: acabamento, foco, movimento e clareza;
- Prime Video: agregação de fontes e navegação por tipos de conteúdo;
- Max: apresentação editorial e identidade cinematográfica;
- Google TV: conteúdo unificado e ação rápida;
- players IPTV avançados: EPG, zapping, catch-up, multiview e flexibilidade de fontes.

O resultado final precisa ter identidade própria e não ser uma cópia visual.

---

## 2. Problemas que o produto resolve

### 2.1 Problemas funcionais

- playlists gigantes e mal organizadas;
- nomes duplicados, tags inconsistentes e categorias inúteis;
- EPG ausente ou associado ao canal errado;
- troca de canal lenta;
- seek que falha ou parece travado;
- player que não explica limitações do stream;
- áudio, legenda e qualidade escondidos em menus ruins;
- perda do progresso entre aparelhos;
- configuração difícil por controle remoto;
- ausência de diagnóstico quando uma fonte falha.

### 2.2 Problemas emocionais

- sensação de produto barato;
- excesso de informação na tela;
- usuário não sabe o que assistir;
- medo de mexer nas configurações e quebrar algo;
- frustração por clicar em conteúdo que não abre;
- falta de confiança no aplicativo;
- experiência diferente e inconsistente em cada aparelho.

### 2.3 Oportunidade

O IPTV BURO deve converter complexidade técnica em três sensações:

1. **“Tudo está organizado.”**
2. **“O aplicativo entende o que eu quero fazer.”**
3. **“Mesmo quando a fonte falha, o aplicativo continua no controle.”**

---

## 3. Públicos prioritários

### Persona A — Usuário de sala

- usa TV e controle remoto;
- quer ligar e assistir imediatamente;
- não entende codecs, EPG ou URLs;
- valoriza capas, trailers, continuar assistindo e favoritos;
- abandona o app quando a navegação parece técnica.

### Persona B — Usuário avançado

- possui várias fontes autorizadas;
- quer editar categorias, EPG, buffer, decoder e headers;
- espera diagnóstico de reprodução;
- usa multiview, catch-up, listas e filtros;
- aceita complexidade apenas dentro de uma área avançada.

### Persona C — Família

- vários perfis;
- perfil infantil com PIN;
- histórico separado;
- conteúdos ao vivo e VOD;
- precisa de configuração simples e proteção contra conteúdo adulto.

### Persona D — Usuário multiplataforma

- começa na TV e continua no celular ou notebook;
- quer pesquisar no celular e reproduzir na TV;
- quer gerenciar fontes e aparelhos pelo portal;
- espera que progresso, favoritos e preferências acompanhem a conta.

---

## 4. Princípios de produto

### P1 — Conteúdo primeiro

A interface existe para levar ao conteúdo, não para exibir o trabalho do designer. O visual deve ser premium, mas nunca atrasar a interação.

### P2 — Uma ação óbvia por estado

Em qualquer tela, deve existir uma ação principal clara: assistir, continuar, trocar canal, ver detalhes, corrigir ou tentar novamente.

### P3 — Inteligência explicável

Toda recomendação importante deve poder responder “por quê?”. Exemplos:

- porque você assistiu a dois thrillers recentes;
- porque este canal está entre seus favoritos;
- porque este programa começa em 10 minutos;
- porque esta versão possui melhor áudio e estabilidade.

O usuário deve poder reduzir, aumentar ou desativar personalização.

### P4 — Degradação elegante

Sem trailer, mostrar backdrop. Sem backdrop, mostrar poster. Sem poster, usar arte gerada por gradiente e tipografia. Sem EPG, manter canal utilizável. Sem internet para metadados, usar cache local.

### P5 — Nenhum controle falso

Não mostrar seek preciso quando a fonte não suporta. Não mostrar 4K apenas pelo nome do canal. Não informar “internet ruim” sem evidência. Toda indicação de qualidade deve vir de capacidades e métricas reais.

### P6 — TV não é celular ampliado

A versão TV terá arquitetura de navegação própria, foco por D-pad, textos legíveis a distância e densidade baixa. Componentes podem compartilhar tokens, mas não layouts completos.

### P7 — Premium em hardware modesto

Sombras, blur e vídeo de fundo devem ter níveis de qualidade. O app deve detectar capacidade do aparelho e reduzir efeitos sem destruir a identidade.

### P8 — Configuração fora da sala quando possível

Digitar URL e senha com controle remoto é último recurso. Priorizar QR code, portal web, deep link, clipboard no celular e sincronização segura.

### P9 — Local-first por economia e privacidade

Histórico, indexação, deduplicação, preferências e boa parte das recomendações devem funcionar localmente. Serviços em nuvem serão opcionais e limitados para manter viável o pagamento único de € 9,90 por 730 dias.

### P10 — Legalidade por design

O aplicativo não fornece catálogo, credenciais, canais ou listas. Não contorna DRM, não baixa conteúdo sem autorização e não oculta a origem configurada pelo usuário.

---

## 5. North Star journeys

### Jornada 1 — Abrir e assistir

1. usuário abre o app;
2. perfil é retomado automaticamente quando permitido;
3. home aparece usando cache local;
4. “Continuar assistindo” e “Ao vivo agora” já estão utilizáveis;
5. usuário pressiona uma vez;
6. reprodução começa;
7. metadados secundários carregam em paralelo.

### Jornada 2 — Descobrir algo novo

1. usuário navega por uma home adaptada ao momento;
2. foca em um card;
3. background, sinopse curta e motivo da recomendação aparecem;
4. trailer silencioso opcional começa após atraso configurável;
5. usuário abre detalhes ou reproduz;
6. a home aprende com a ação sem se tornar imprevisível.

### Jornada 3 — TV ao vivo sem fricção

1. usuário entra em “Ao vivo”;
2. vê canais favoritos, programa atual e próximos eventos;
3. foco em um canal abre preview informativo, não reprodução agressiva;
4. Play inicia o canal;
5. canal para cima/baixo troca rapidamente;
6. botão para cima abre mini-guia;
7. botão OK abre controles contextuais;
8. voltar retorna ao ponto exato da navegação.

### Jornada 4 — Fonte com problema

1. playback demora ou falha;
2. Stream Health Engine classifica a causa provável;
3. app tenta ações seguras automaticamente;
4. interface mostra progresso sem termos técnicos desnecessários;
5. se não resolver, oferece opções relevantes: tentar novamente, modo estável, outra faixa, abrir diagnóstico ou voltar;
6. URL e credenciais nunca aparecem em logs ou telas de erro.

---

## 6. Métricas de experiência

As metas abaixo são objetivos de engenharia, não promessas para toda fonte externa.

### 6.1 Inicialização

- cold start até home interativa: P50 ≤ 2,5 s em aparelho de referência;
- warm start: P50 ≤ 1,0 s;
- shell e conteúdo em cache devem aparecer antes de chamadas remotas demoradas;
- nenhuma tela branca durante bootstrap.

### 6.2 Interação

- resposta visual ao D-pad: ≤ 100 ms;
- transição simples: 120–220 ms;
- transição cinematográfica: 240–420 ms;
- foco nunca pode desaparecer;
- retorno deve restaurar foco e posição da lista.

### 6.3 Reprodução

- tempo até primeiro frame em stream saudável: meta P50 ≤ 2,0 s;
- troca de canal saudável: meta P50 ≤ 1,5 s e P95 ≤ 5,0 s;
- travamentos por sessão devem ser medidos por origem, protocolo e aparelho;
- erro recuperável não deve derrubar a tela inteira;
- áudio deve manter foco e lifecycle corretamente.

### 6.4 Busca

- primeiros resultados do índice local: ≤ 300 ms;
- busca completa não pode bloquear digitação;
- resultados devem agrupar canais, programas, filmes, séries, episódios, pessoas e categorias;
- erros ortográficos comuns devem retornar resultado útil.

### 6.5 Confiabilidade

- sessões sem crash: ≥ 99,5% no beta e ≥ 99,8% na versão estável;
- congelamentos de UI devem ser rastreados;
- nenhuma credencial em analytics;
- migração de banco testada antes de release.

### 6.6 Qualidade percebida

Testes com usuários devem medir:

- tempo para encontrar algo para assistir;
- quantidade de cliques até reprodução;
- compreensão do estado do player;
- confiança na área de fontes;
- comparação visual com serviços premium;
- taxa de conclusão do onboarding sem ajuda.

---

## 7. Antimetas

O projeto não deve:

- criar um clone visual de Netflix, Apple TV, Prime Video ou Max;
- usar vídeo automático com áudio na home;
- depender de um LLM caro para funções básicas;
- carregar dezenas de trailers simultaneamente;
- misturar configurações avançadas com a navegação comum;
- mostrar todos os dados disponíveis apenas porque existem;
- reconstruir todo o app a cada nova plataforma;
- sacrificar estabilidade por efeitos visuais;
- prometer corrigir streams estruturalmente quebrados;
- coletar histórico ou telemetria sem transparência.

---

## 8. Referências de pesquisa

Padrões estudados para esta extensão:

- Apple Human Interface Guidelines para tvOS: foco, arte edge-to-edge, gestos do controle e experiência imersiva;
- Amazon Fire TV Design Guidelines: interface de 10 pés, baixa densidade, D-pad e prioridade ao consumo;
- Netflix TV Experience 2025–2026: navegação simplificada, My Netflix, informações ampliadas e recomendações responsivas;
- Netflix mobile 2026: descoberta visual por clips e navegação simplificada;
- sistemas de recomendação Netflix: uso de histórico, metadados e sinais de preferência;
- padrões modernos de agregação de conteúdo e continuidade entre dispositivos.

Essas referências são princípios de qualidade. Os assets, nomes, composições e identidade do IPTV BURO devem ser originais.
