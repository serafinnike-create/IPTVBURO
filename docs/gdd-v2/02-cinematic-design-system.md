# GDD 2.0 — 02. BURO Cinematic System

## 1. Objetivo visual

O IPTV BURO deve parecer uma plataforma premium criada especificamente para entretenimento na sala. O design precisa ser reconhecível mesmo sem o logotipo.

A identidade será baseada em:

- superfícies escuras profundas, não preto absoluto constante;
- cor ambiente extraída da arte do conteúdo;
- tipografia grande e segura para distância;
- movimento com sensação física;
- foco extremamente claro;
- cards com proporções variadas;
- hierarquia editorial, não apenas fileiras infinitas;
- player que desaparece quando não é necessário;
- detalhes técnicos disponíveis sem poluir a experiência comum.

Nome interno do sistema: **BURO Cinematic System — BCS**.

---

## 2. Linguagem visual

### 2.1 Personalidade

O produto deve transmitir:

- sofisticado;
- confiante;
- tecnológico;
- cinematográfico;
- calmo;
- rápido;
- humano.

Evitar:

- neon gamer excessivo;
- gradientes coloridos em todos os componentes;
- glassmorphism pesado;
- sombras gigantes;
- textos muito pequenos;
- interfaces com aparência de painel administrativo;
- excesso de badges;
- fundos 100% pretos sem profundidade;
- cópia do vermelho Netflix, azul Prime ou linguagem Apple.

### 2.2 Paleta base

Os valores finais devem virar design tokens e ser revisados com contraste WCAG.

```text
buro.bg.canvas          #090A0D
buro.bg.surface         #111319
buro.bg.elevated        #191C24
buro.bg.overlay         rgba(5, 6, 9, 0.72)
buro.text.primary       #F6F7FA
buro.text.secondary     #B6BAC5
buro.text.muted         #7E8492
buro.border.subtle      rgba(255,255,255,0.10)
buro.focus.base         #F6F7FA
buro.success            #4ED59B
buro.warning            #F3BD56
buro.error              #FF6B6B
```

A cor de assinatura do BURO deve ser original. Recomendação inicial:

```text
buro.brand.primary      #8B7CFF
buro.brand.secondary    #55D6C2
```

Essas cores não devem dominar posters ou backdrops. Elas aparecem em foco, ações, progressos, estados e detalhes de marca.

### 2.3 Ambient Color Engine

Cada tela de conteúdo pode extrair de 1 a 3 cores dominantes da arte e gerar um ambiente controlado:

- cor primária com saturação limitada;
- cor secundária para profundidade;
- camada preta para garantir contraste;
- cache da paleta por conteúdo;
- fallback para gradiente da categoria;
- processamento local quando possível;
- desativação em modo econômico ou reduzir movimento.

O efeito nunca pode impedir a leitura ou gerar flashes fortes.

---

## 3. Tipografia

### 3.1 Requisitos

- fonte variável ou família com vários pesos;
- excelente leitura em TV;
- números tabulares para horário e EPG;
- suporte completo a português, alemão, italiano e inglês;
- fallback definido para alfabetos adicionais;
- licenciamento compatível com distribuição comercial.

Sugestões técnicas para protótipo: Inter, Manrope ou família equivalente licenciada. A escolha final exige revisão de marca.

### 3.2 Escala TV de referência

Base 1920 × 1080, respeitando densidade independente:

```text
display.hero       52–64 sp, semibold
title.screen       36–44 sp, semibold
title.section      26–32 sp, semibold
title.card         20–24 sp, medium
body.large         20–22 sp, regular
body.default       17–20 sp, regular
label              15–17 sp, medium
caption            13–15 sp, regular
```

Nenhum texto essencial deve depender de tamanho inferior a 15 sp na TV.

### 3.3 Regras

- máximo de 2 linhas para título de card;
- sinopse do hero: 2–4 linhas;
- não usar texto todo em caixa alta em parágrafos;
- horário e duração devem ser escaneáveis;
- truncamento com tooltip ou expansão quando necessário;
- nomes de canais não devem deslocar permanentemente o layout.

---

## 4. Grid e zonas seguras

### 4.1 TV

- canvas lógico: 1920 × 1080;
- safe area mínima: 64 px laterais e 48 px verticais;
- grid: 12 colunas;
- gutter: 24 px;
- spacing base: 8 px;
- hero: aproximadamente 42–58% da altura útil;
- primeira fileira deve aparecer parcialmente abaixo do hero para sinalizar continuidade;
- nenhum componente interativo encostado na borda física.

### 4.2 Breakpoints

Definir pelo espaço útil, não apenas pela resolução:

- Compact TV: 720p ou baixa memória;
- Standard TV: 1080p;
- Premium TV: 4K e GPU capaz;
- Desktop compact;
- Desktop wide;
- Tablet landscape;
- Mobile portrait;
- Mobile landscape.

Cada breakpoint deve escolher densidade, quantidade de cards, preview e efeitos adequados.

---

## 5. Navegação principal — BURO Ribbon

A navegação primária será uma faixa superior minimalista e contextual, chamada internamente de **BURO Ribbon**.

Itens padrão na TV:

- Início;
- Ao Vivo;
- Filmes;
- Séries;
- Descobrir;
- Minha BURO;
- Pesquisa;
- avatar do perfil.

### 5.1 Comportamento

- fica recolhida durante navegação de conteúdo;
- pressionar Voltar na home move o foco para a Ribbon;
- pressionar Voltar novamente abre confirmação de saída apenas quando necessário;
- item ativo é indicado por peso, opacidade e marcador discreto;
- foco aumenta contraste e escala, sem caixa pesada;
- Ribbon pode receber atalhos dinâmicos, mas nunca mudar a ordem dos itens principais automaticamente;
- em telas menores, vira barra inferior ou menu lateral conforme plataforma.

### 5.2 Por que não usar apenas menu lateral

O menu lateral consome largura e reforça aparência de player tradicional. A Ribbon oferece sensação editorial e mantém a arte dominante, mas deve preservar acesso previsível pelo botão Voltar.

---

## 6. Física de foco

O foco é o cursor da TV. Ele deve ser tratado como componente central do produto.

### 6.1 Estados

Todo elemento focável deve possuir:

- default;
- focused;
- pressed;
- selected;
- disabled;
- loading;
- error quando aplicável.

### 6.2 Animação de foco

Padrão recomendado:

- escala: 1.00 → 1.045 em cards grandes;
- escala: 1.00 → 1.06 em cards pequenos;
- elevação visual moderada;
- borda luminosa de 2–3 px;
- leve aumento de brilho da arte;
- parallax opcional de 2–5 px;
- duração 140–180 ms;
- curva rápida na entrada e suave na saída;
- sem alterar drasticamente o tamanho da linha.

### 6.3 Regras obrigatórias

- nunca haver dois focos visíveis;
- foco restaurado ao voltar;
- listas virtuais devem manter destino previsível;
- movimentos diagonais não podem saltar para áreas inesperadas;
- foco não pode ficar atrás de overlay;
- skeletons não são focáveis;
- rolagem acompanha o foco antes de cortar o elemento.

---

## 7. Movimento

### 7.1 Camadas de movimento

1. **Micro:** foco, botão, progresso — 80–180 ms.
2. **Navegação:** abrir detalhes, trocar seção — 180–320 ms.
3. **Cinematográfico:** hero, backdrop, player expandido — 280–500 ms.

### 7.2 Princípios

- movimento explica relação espacial;
- poster pode expandir para detalhe com transição compartilhada quando o hardware permitir;
- backdrop faz crossfade, nunca corte agressivo;
- trailers começam somente após 1,5–2,5 s de foco estável;
- áudio de trailer sempre começa mudo;
- rolagem rápida cancela carregamentos e previews anteriores;
- modo “Reduzir movimento” remove parallax, zoom e vídeo automático.

### 7.3 Níveis gráficos

- **Eco:** sem blur em tempo real, sem vídeo de fundo, sombras simples;
- **Balanced:** gradiente dinâmico, crossfade e foco premium;
- **Cinematic:** blur, parallax, trailer e transições completas;
- **Auto:** selecionado por benchmark inicial e telemetria local.

---

## 8. Sistema de cards

Não usar uma única proporção para tudo.

### 8.1 Tipos

#### Poster Card — 2:3

Filmes, séries, coleções.

Mostra:

- poster;
- progresso quando existente;
- badge de qualidade apenas se verificado;
- favorito ou estado infantil de forma discreta;
- título fora da arte quando necessário.

#### Landscape Card — 16:9

Continuar assistindo, episódios, programas ao vivo, trailers.

#### Channel Tile — 1:1 ou 4:3

Logo do canal, programa atual, barra de progresso e estado do stream.

#### Editorial Feature — 16:9 largo

Coleções especiais, esportes ao vivo, eventos e recomendações de alto valor.

#### Person Card — 3:4

Elenco, diretor e busca por pessoas quando houver metadados licenciados.

#### Quick Action Card

Último canal, retomar, importar fonte, corrigir EPG, abrir multiview.

### 8.2 Regras de fileiras

Cada fileira precisa ter propósito e título claro. Proibido gerar fileiras vazias ou quase duplicadas.

Exemplos:

- Continue de onde parou;
- Ao vivo agora;
- Começa em breve;
- Seus canais mais vistos;
- Filmes de até 90 minutos;
- Escolhas para esta noite;
- Porque você assistiu…;
- Novidades das suas fontes;
- Conteúdo infantil seguro;
- Streams com melhor estabilidade hoje.

---

## 9. Home — BURO Living Home

A home não é uma lista estática. Ela é uma composição editorial reativa ao contexto.

### 9.1 Estrutura

1. BURO Ribbon;
2. Hero vivo;
3. ações primárias do hero;
4. primeira fileira parcialmente visível;
5. módulos editoriais;
6. áreas contextuais;
7. rodapé técnico apenas em configurações.

### 9.2 Hero vivo

O hero apresenta um conteúdo ou evento de alto valor.

Elementos:

- backdrop edge-to-edge;
- logo do título quando disponível e licenciado;
- título textual como fallback;
- sinopse curta;
- gênero, ano, duração, classificação e qualidade real;
- Play/Continuar como ação principal;
- Detalhes;
- Minha Lista;
- motivo da recomendação opcional;
- indicador “Ao vivo” e horário quando aplicável.

Comportamento:

- trocar conteúdo por navegação, não por carrossel automático agressivo;
- trailer mudo opcional após foco estável;
- manter legibilidade com scrim adaptativo;
- CTA principal sempre visível;
- no máximo três ações visíveis.

### 9.3 Home contextual

A ordem das fileiras pode reagir a:

- horário local;
- perfil;
- progresso recente;
- eventos começando;
- histórico;
- favoritos;
- disponibilidade real das fontes;
- dispositivo;
- estado da rede;
- modo infantil.

A navegação principal e a posição de “Continuar assistindo” não podem mudar de forma caótica.

---

## 10. Tela de detalhes — BURO Story Page

A tela de detalhes deve ajudar a decidir, não apenas listar metadados.

### 10.1 Camadas

- backdrop e cor ambiente;
- título/logo;
- CTA principal;
- progresso;
- resumo editorial;
- fatos rápidos;
- faixas de áudio e legendas disponíveis;
- temporadas e episódios;
- trailers e extras autorizados;
- elenco;
- conteúdos relacionados;
- versões alternativas da mesma obra;
- diagnóstico de fonte dentro de menu avançado.

### 10.2 Decisão rápida

Antes de rolar, o usuário deve enxergar:

- o que é;
- por que pode interessar;
- quanto dura;
- classificação;
- idioma principal;
- se pode continuar;
- se o stream está disponível;
- ação principal.

### 10.3 Versões duplicadas

Quando a mesma obra aparece em múltiplas fontes, exibir uma única página e um seletor “Versões”.

Ordenação sugerida:

1. fonte escolhida pelo usuário;
2. versão já iniciada;
3. maior estabilidade medida;
4. áudio preferido;
5. qualidade suportada pelo aparelho;
6. menor latência.

Nunca afirmar que uma versão é superior usando apenas o texto do nome.

---

## 11. Ao Vivo — BURO Pulse

A área ao vivo deve parecer viva, não uma planilha de EPG.

### 11.1 Tela principal

Módulos:

- último canal;
- favoritos ao vivo;
- eventos começando agora;
- próximos 30 minutos;
- esportes e notícias quando categorizados pela fonte;
- canais recentes;
- categorias;
- guia completo;
- multiview quando suportado.

### 11.2 Guia cinematográfico

O EPG completo continua disponível, mas recebe:

- linha do tempo clara;
- coluna de canal fixa;
- programa atual destacado;
- preview contextual na lateral ou topo;
- salto por dia;
- filtro por favoritos/categoria;
- busca por programa;
- indicador catch-up;
- cores moderadas por gênero, nunca arco-íris intenso;
- carregamento progressivo e virtualização.

### 11.3 Mini-guia no player

Ao pressionar para cima:

- programa atual;
- próximo programa;
- lista curta de canais;
- favoritos primeiro opcional;
- troca sem sair do player;
- estado de buffer discreto;
- relógio e duração.

---

## 12. Player — BURO Flow Player

### 12.1 Filosofia

Quando o usuário assiste, o vídeo é a interface. Controles aparecem apenas quando chamados.

### 12.2 Overlay primário

- título/canal;
- programa ou episódio;
- timeline real;
- tempo atual e restante;
- play/pause;
- avançar/retroceder apenas se suportado;
- próximo episódio/programa quando aplicável;
- áudio;
- legenda;
- qualidade/modo de estabilidade;
- mais opções.

### 12.3 Timeline inteligente

Estados:

- VOD seek preciso;
- VOD seek aproximado;
- live puro;
- live com DVR;
- catch-up;
- conteúdo sem duração;
- indisponível.

A timeline muda visualmente conforme a capacidade. Não simular uma barra completa para live sem DVR.

### 12.4 Scrubber visual

Quando suportado:

- thumbnails fornecidos pela origem;
- sprite VTT;
- geração local temporária apenas quando legal e tecnicamente possível;
- fallback para horário sem thumbnail;
- limites rígidos de memória e cache;
- nunca baixar o conteúdo completo apenas para gerar preview.

### 12.5 Painel técnico avançado

Acessível por “Mais > Diagnóstico”:

- protocolo;
- resolução real;
- codec;
- bitrate estimado;
- decoder;
- dropped frames;
- buffer atual;
- tempo até primeiro frame;
- erros recentes redigidos;
- modo de reprodução;
- opção copiar relatório sem credenciais.

---

## 13. Minha BURO

Centro pessoal do usuário:

- continuar assistindo;
- minha lista;
- favoritos;
- histórico;
- lembretes;
- downloads autorizados quando aplicável;
- canais recentes;
- coleções criadas pelo usuário;
- itens ocultos;
- preferências de recomendação.

A área deve funcionar como memória pessoal, não como uma tela de configurações.

---

## 14. Onboarding premium

### 14.1 Primeira abertura

1. animação curta do logotipo;
2. seleção de idioma;
3. aceitar termos e escopo legal;
4. escolher “Configurar pela TV” ou “Usar celular/computador”;
5. exibir QR code e código curto;
6. adicionar fonte autorizada no portal;
7. TV recebe configuração por sessão temporária segura;
8. importar, analisar e organizar;
9. mostrar relatório simples de saúde da fonte;
10. criar perfil;
11. selecionar interesses opcionais;
12. entrar na home.

### 14.2 Regra

A escolha de interesses pode ser ignorada. O usuário nunca deve ficar preso em onboarding longo.

---

## 15. Acessibilidade

Obrigatório desde a fundação:

- contraste mínimo verificado;
- leitor de tela;
- ordem semântica de foco;
- labels para ícones;
- legendas e preferências persistentes;
- tamanho de texto ampliado;
- reduzir movimento;
- reduzir transparência;
- alto contraste;
- não depender apenas de cor;
- suporte a controle alternativo;
- timeout suficiente em ações temporárias;
- audiodescrição quando a faixa existe.

---

## 16. Entregáveis de design antes de produção

O Codex deve produzir componentes reais, mas a equipe precisa manter estes artefatos no repositório:

- `docs/ux/design-principles.md`;
- `docs/ux/tokens.md`;
- `docs/ux/focus-map.md`;
- `docs/ux/navigation-map.md`;
- `docs/ux/screen-inventory.md`;
- screenshots golden dos estados principais;
- protótipo navegável ou catálogo de componentes;
- matriz de breakpoints;
- matriz de acessibilidade;
- lista de performance tiers.

Nenhuma tela principal é considerada pronta apenas porque compila.
