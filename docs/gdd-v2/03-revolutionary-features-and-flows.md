# GDD 2.0 — 03. Funções revolucionárias e jornadas

## 1. Visão geral

O diferencial do IPTV BURO não deve depender de uma única “função mágica”. A revolução virá da combinação coordenada de descoberta, organização, TV ao vivo, reprodução, continuidade e controle.

Cada função abaixo deve ser classificada como:

- **P0:** necessária para o produto parecer premium;
- **P1:** diferencial forte após a fundação;
- **P2:** expansão ambiciosa;
- **Experimental:** somente atrás de feature flag.

---

## 2. BURO Home Engine — P0

Motor que compõe a home usando sinais locais e regras editoriais.

### 2.1 Entradas

- perfil atual;
- hora e dia;
- histórico;
- progresso;
- favoritos;
- itens adicionados à lista;
- programas começando;
- qualidade e disponibilidade das fontes;
- categorias do catálogo;
- preferências explícitas;
- idioma e classificação etária;
- dispositivo e capacidade gráfica.

### 2.2 Saídas

- hero relevante;
- ordem controlada das fileiras;
- módulos ao vivo;
- recomendações;
- alertas úteis;
- atalhos de retomada;
- coleções temporárias.

### 2.3 Regras

- “Continuar assistindo” deve permanecer estável;
- módulos podem mudar, mas a navegação principal não;
- item indisponível não pode ser promovido;
- repetir o mesmo título em várias fileiras deve ser limitado;
- recomendações precisam de diversidade;
- usuário pode ocultar uma fileira ou indicar “menos disso”;
- sem dados suficientes, usar editorial por gênero e popularidade da própria fonte;
- não enviar histórico para terceiros por padrão.

---

## 3. BURO Pulse — P0

Camada que transforma TV ao vivo em uma experiência contextual.

### 3.1 Funções

- “Ao vivo agora” na home;
- programas começando em 5, 15 e 30 minutos;
- favoritos com programa atual;
- último canal e canal anterior;
- mini-guia sobre o vídeo;
- troca rápida por canal, categoria e histórico;
- lembretes locais;
- catch-up quando a fonte informa suporte;
- indicador de progresso do programa;
- preview informativo sem iniciar dezenas de streams;
- opção de zap silencioso;
- visualização rápida de “agora e próximo”.

### 3.2 Smart Zapping

Ao trocar canal:

1. cancelar a preparação anterior;
2. preservar o frame atual por poucos milissegundos ou mostrar arte do canal;
3. iniciar próximo stream;
4. carregar EPG em paralelo;
5. mostrar título e progresso;
6. medir tempo até primeiro frame;
7. registrar falha sem URL completa;
8. permitir retorno instantâneo ao canal anterior.

O prefetch pode ser usado apenas em aparelhos capazes, com limite de rede e decoder.

### 3.3 Channel Recall

Pressionar duas vezes um atalho configurável alterna entre os dois últimos canais, semelhante a “voltar ao canal anterior” de televisores tradicionais.

### 3.4 Event Hub

Quando o EPG ou metadados identificarem eventos:

- agrupar transmissões relacionadas;
- mostrar horário local;
- permitir lembrete;
- exibir fontes alternativas autorizadas;
- priorizar versão estável e idioma preferido;
- não inventar placares nem dados esportivos sem fonte licenciada.

---

## 4. BURO Lens — P0/P1

Busca universal por intenção, não apenas texto exato.

### 4.1 Escopo pesquisável

- canais;
- programas EPG;
- filmes;
- séries;
- temporadas;
- episódios;
- gêneros;
- atores e diretores quando licenciados;
- categorias;
- coleções;
- favoritos;
- histórico.

### 4.2 Tipos de consulta

- título exato;
- erro ortográfico;
- sinônimo;
- nome parcial;
- “filme curto de ação”;
- “desenho para criança”;
- “algo que comecei ontem”;
- “jornal ao vivo”;
- “filmes com áudio em italiano”;
- “canal que eu vi antes deste”.

### 4.3 Arquitetura econômica

Fase 1:

- índice local;
- normalização;
- stemming e fuzzy search;
- filtros estruturados;
- ranking por histórico e disponibilidade;
- dicionário multilíngue pequeno.

Fase 2 opcional:

- parser de intenção local;
- modelo pequeno on-device quando viável;
- serviço remoto opcional com consentimento e orçamento;
- cache de interpretações.

### 4.4 Experiência

- resultados aparecem durante digitação;
- voz quando a plataforma oferece API;
- filtros rápidos;
- agrupamento por tipo;
- nenhuma tela de “zero resultados” sem sugestões;
- histórico de busca por perfil;
- opção limpar histórico.

---

## 5. BURO Catalog Brain — P0

Pipeline que transforma listas brutas em um catálogo coerente.

### 5.1 Normalização

- limpar prefixos e sufixos repetidos;
- reconhecer idioma, país, qualidade e grupo;
- preservar nome original;
- gerar nome de exibição separado;
- corrigir capitalização sem destruir marcas;
- detectar conteúdo adulto;
- separar TV, filme, série, rádio e desconhecido;
- não alterar a origem sem confirmação quando houver ambiguidade.

### 5.2 Deduplicação

Construir identidade canônica usando:

- título normalizado;
- ano;
- temporada/episódio;
- EPG ID;
- duração;
- metadados externos licenciados;
- fingerprints leves quando legal e necessário;
- origem e categoria.

Resultado:

- uma página por obra;
- várias versões/fontes;
- histórico compartilhado por obra;
- escolha automática ou manual da versão.

### 5.3 Metadata Confidence

Cada correspondência recebe confiança:

- alta;
- média;
- baixa;
- não encontrada.

Correspondência de baixa confiança não deve substituir silenciosamente título ou arte. O usuário avançado pode corrigir e fixar.

### 5.4 Playlist Health Report

Após importação:

- quantidade de itens;
- categorias encontradas;
- EPG associado;
- duplicados;
- entradas inválidas;
- conteúdos sem arte;
- streams testados apenas quando autorizado e sem sobrecarregar a fonte;
- recomendações de correção;
- tempo estimado de indexação;
- privacidade do processamento.

O relatório deve ser simples para usuário comum e detalhado sob demanda.

---

## 6. BURO Quality Autopilot — P0

Evolução do Stream Health Engine para uma experiência automática e transparente.

### 6.1 Objetivos

- iniciar mais rápido;
- reduzir buffering;
- escolher estratégia por conteúdo;
- recuperar erros comuns;
- explicar limitações;
- aprender preferências do aparelho, não do usuário apenas.

### 6.2 Decisões possíveis

- buffer baixo para live;
- buffer estável para VOD;
- decoder hardware/software;
- faixa de áudio preferida;
- legenda preferida;
- variante HLS adequada;
- fallback de protocolo quando a fonte oferece alternativas;
- retry curto;
- pausa antes de nova tentativa;
- desativar preview em rede ruim;
- reduzir qualidade gráfica do app, nunca a qualidade do vídeo sem regra clara.

### 6.3 “Por que está carregando?”

Estados amigáveis:

- conectando à fonte;
- preparando vídeo;
- ajustando reprodução;
- tentando modo estável;
- fonte temporariamente indisponível;
- formato não suportado neste aparelho;
- credenciais precisam ser verificadas.

Detalhes técnicos ficam em painel separado.

### 6.4 Memória por aparelho

O app pode armazenar localmente:

- codecs problemáticos;
- decoder preferido;
- tamanho de buffer que funcionou;
- fontes lentas;
- tempo médio de troca;
- resolução máxima estável.

Esses dados não devem incluir URL completa ou senha.

---

## 7. BURO Continuity Mesh — P1

Continuidade criptografada entre dispositivos.

### 7.1 Dados sincronizáveis

- progresso;
- favoritos;
- minha lista;
- histórico opcional;
- perfis;
- preferências de áudio/legenda;
- coleções;
- lembretes;
- dispositivo principal;
- configurações não sensíveis.

### 7.2 Credenciais de fontes

Opções:

1. local somente;
2. sincronização criptografada ponta a ponta;
3. entrada separada por dispositivo;
4. token revogável quando a fonte suporta.

Nunca sincronizar senha em texto puro.

### 7.3 Handoff

Exemplo:

- usuário pausa na TV;
- abre o celular;
- aparece “Continuar na sala — 42 min”;
- reproduz no celular ou usa o celular como controle;
- ao retornar à TV, progresso é conciliado.

Conflitos usam timestamp, duração e confirmação quando a diferença for grande.

---

## 8. BURO Companion — P1

Celular ou portal como segundo controle da TV.

### 8.1 Funções

- digitar buscas no celular;
- colar URL e credenciais;
- navegar catálogo;
- enviar item para a TV;
- controlar play/pause/seek;
- trocar áudio e legenda;
- abrir teclado;
- gerenciar perfis;
- editar categorias;
- diagnosticar aparelho;
- escanear QR para parear.

### 8.2 Pareamento

- código curto com expiração;
- QR code;
- confirmação visível na TV;
- token por dispositivo;
- revogação no portal;
- mesma rede como sinal adicional, não única autenticação.

---

## 9. BURO Mood — P1

Descoberta controlada por intenção ou momento.

### 9.1 Exemplos

- Quero rir;
- Algo rápido;
- Noite de cinema;
- Para assistir em família;
- Tensão e suspense;
- Ao vivo agora;
- Música de fundo;
- Conteúdo em alemão;
- Continue algo conhecido;
- Surpreenda-me.

### 9.2 Regras

- funciona com filtros e ranking local antes de IA generativa;
- mostra filtros usados;
- permite refinar;
- nunca inventa disponibilidade;
- não cria uma conversa longa na TV;
- escolha final continua com o usuário.

---

## 10. BURO Shorts / Moments — P2 experimental

Feed visual de trailers ou clipes **somente quando os vídeos forem autorizados**.

### 10.1 Propósito

Ajudar descoberta rápida no celular e, de forma limitada, na TV.

### 10.2 Regras

- trailers oficiais por API ou metadados autorizados;
- sem scraping de vídeo;
- mudo por padrão;
- botão assistir, adicionar à lista e detalhes;
- limite de pré-carregamento;
- desativável;
- não transformar a home TV em feed vertical;
- respeitar quotas de API e termos de uso.

---

## 11. BURO MultiView — P1/P2

Reprodução simultânea de 2 a 4 fontes quando hardware e origem suportarem.

### 11.1 Modos

- 2 × 1;
- 2 × 2;
- principal + três pequenos;
- áudio segue janela selecionada;
- troca de posição;
- salvar layout temporário;
- eventos favoritos.

### 11.2 Proteções

- verificar quantidade de decoders;
- limitar resolução por janela;
- avisar sobre uso de banda;
- impedir em hardware incapaz;
- parar streams ao sair;
- não prometer em todas as TVs.

---

## 12. BURO Kids — P0

Perfil infantil deve ser um produto dentro do produto.

### 12.1 Funções

- PIN de saída;
- classificação máxima;
- allowlist de categorias e títulos;
- bloquear conteúdo sem classificação por padrão configurável;
- sem acesso a fontes, pagamento ou diagnóstico;
- recomendações separadas;
- histórico separado;
- timer opcional;
- modo alto contraste e ícones maiores;
- temas infantis discretos sem perder identidade BURO;
- busca limitada ao catálogo permitido.

### 12.2 Segurança

Filtros automáticos ajudam, mas responsáveis precisam de controle manual. Conteúdo não classificado não pode ser assumido como seguro.

---

## 13. Perfis inteligentes — P0

Tipos:

- adulto;
- infantil;
- convidado;
- privado local;
- perfil administrador.

Preferências por perfil:

- idioma;
- áudio;
- legenda;
- autoplay;
- movimento;
- conteúdo oculto;
- recomendações;
- lista e favoritos;
- layout de home dentro de limites;
- modo de dados;
- acessibilidade.

O perfil convidado pode apagar automaticamente o histórico ao sair.

---

## 14. Coleções inteligentes — P1

Usuário cria coleções manuais e o app sugere coleções dinâmicas.

Exemplos:

- filmes para viagem;
- canais de notícias;
- desenhos da família;
- campeonatos;
- filmes em italiano;
- episódios pendentes;
- filmes com menos de duas horas;
- conteúdo 4K confirmado;
- streams mais estáveis.

Toda coleção dinâmica mostra a regra e pode ser fixada.

---

## 15. Lembretes e agenda — P1

- lembrar programa ao vivo;
- lembrar nova temporada quando metadados permitem;
- notificação no aparelho e portal;
- calendário interno;
- não enviar spam;
- respeitar fuso horário;
- opção “abrir canal automaticamente” apenas com confirmação e suporte da plataforma;
- lembrete deve funcionar mesmo sem telemetria remota quando o EPG está local.

---

## 16. Personalização de arte — P2 experimental

Quando houver múltiplas artes licenciadas para a mesma obra, o app pode escolher a que melhor combina com o perfil ou contexto.

Regras:

- não gerar ou editar posters protegidos sem direito;
- apenas selecionar entre assets permitidos;
- fallback estável;
- experimentos A/B somente com consentimento de telemetria;
- evitar mudança excessiva que prejudique reconhecimento.

---

## 17. Jornada completa — novo usuário

1. instalar;
2. escolher idioma;
3. iniciar trial;
4. parear celular/portal;
5. adicionar fonte;
6. app valida estrutura;
7. cria catálogo local;
8. associa EPG;
9. mostra relatório de saúde;
10. usuário cria perfil;
11. escolhe interesses opcionalmente;
12. home aparece com conteúdo real;
13. primeiro Play mede capacidades;
14. Quality Autopilot ajusta aparelho;
15. usuário pode comprar licença no canal permitido pela plataforma.

---

## 18. Jornada completa — usuário recorrente

1. abrir app;
2. home aparece por cache;
3. progresso e EPG atualizam em background;
4. hero e módulos reagem ao perfil;
5. usuário retoma ou entra em ao vivo;
6. player usa configurações aprendidas;
7. progresso sincroniza;
8. home recebe novos sinais sem reorganização caótica.

---

## 19. Jornada completa — administrador da família

1. abre perfil administrador;
2. acessa Fontes e Família;
3. cria perfil infantil;
4. define classificação e categorias;
5. escolhe PIN;
6. testa o perfil;
7. visualiza atividade sem detalhes invasivos;
8. revoga aparelho ou fonte pelo portal quando necessário.

---

## 20. Regras de prioridade

### P0 — versão premium inicial

- BURO Cinematic System;
- Living Home;
- Story Page;
- BURO Pulse básico;
- BURO Flow Player;
- Catalog Brain;
- Quality Autopilot básico;
- busca universal local;
- perfis e Kids;
- onboarding por QR;
- Minha BURO;
- acessibilidade;
- performance tiers.

### P1 — diferenciação comercial

- Continuity Mesh;
- Companion;
- Mood;
- MultiView 2 telas;
- coleções inteligentes;
- lembretes;
- diagnóstico avançado;
- busca por voz;
- versões alternativas inteligentes.

### P2 — expansão

- MultiView 4 telas;
- Moments;
- personalização de artwork;
- watch party quando houver arquitetura econômica;
- integrações com plataformas domésticas autorizadas;
- recomendações federadas opcionais.

---

## 21. O que torna o produto revolucionário

O IPTV BURO será diferenciado quando estas peças funcionarem juntas:

- uma playlist bruta vira catálogo limpo;
- live, VOD e EPG aparecem na mesma linguagem visual;
- o app mostra conteúdo relevante sem esconder o restante;
- o player decide automaticamente, mas explica;
- o usuário pode continuar em outro aparelho;
- a TV pode ser controlada pelo celular;
- problemas de origem viram estados compreensíveis;
- o design se adapta à capacidade da TV;
- o produto permanece local-first e sustentável em compra única.
