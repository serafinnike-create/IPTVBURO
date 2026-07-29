# PROMPT DE CONTINUAÇÃO — CODEX — IPTV BURO GDD 2.0

Você está trabalhando no repositório **IPTV BURO**.

O projeto já possui uma implementação em andamento baseada no GDD 1.0. **Não reinicie, não apague e não substitua o projeto inteiro.** Sua tarefa é auditar o estado atual e continuar incrementalmente usando o GDD 2.0 como especificação de experiência premium.

## Documentos obrigatórios

Leia integralmente, nesta ordem:

1. `docs/GDD_IPTV_BURO.md`
2. todos os arquivos em `docs/gdd/`
3. `docs/GDD_2_REVOLUTIONARY_EXPERIENCE.md`
4. todos os arquivos em `docs/gdd-v2/`

## Regras de precedência

- legalidade, segurança, privacidade, fontes e arquitetura-base do GDD 1.0 permanecem obrigatórias;
- design, UX, navegação, descoberta, TV ao vivo e experiência premium do GDD 2.0 substituem as partes equivalentes do GDD 1.0;
- preserve código funcional;
- mudanças estruturais grandes exigem ADR;
- não implemente catálogo, canais, credenciais ou conteúdo pirata;
- não copie visual, assets, logos, sons ou trade dress de Netflix, Apple TV, Prime Video, Max ou qualquer concorrente;
- crie a identidade original **BURO Cinematic System**.

---

## Missão imediata

Execute a **Fase 0 — Auditoria** e depois implemente a **Fase 1 — BURO Cinematic Foundation** como uma entrega vertical compilável.

### Parte A — Auditoria obrigatória

1. Verifique branch e working tree.
2. Liste a estrutura atual do repositório.
3. Identifique stack, módulos, arquitetura e convenções existentes.
4. Execute os builds e testes disponíveis.
5. Identifique o que já foi implementado do GDD 1.0.
6. Compare o código atual com o GDD 2.0.
7. Não faça suposições sobre arquivos que não leu.
8. Crie:

```text
docs/status/CURRENT_IMPLEMENTATION.md
docs/status/GDD2_GAP_ANALYSIS.md
docs/adr/ADR-XXXX-buro-cinematic-foundation.md
```

`CURRENT_IMPLEMENTATION.md` deve conter:

- data;
- branch e commit;
- stack;
- módulos;
- telas;
- player;
- parsers;
- banco;
- testes;
- build status;
- riscos;
- limitações.

`GDD2_GAP_ANALYSIS.md` deve usar uma tabela:

```text
Requirement | Existing | Partial | Missing | Conflicting | Planned action
```

### Parte B — Plano antes do código

Após a auditoria, produza um plano de implementação curto e ordenado. Não peça confirmação para tarefas normais e não pare apenas na documentação.

O plano deve escolher o menor conjunto de mudanças que entregue uma demonstração premium real:

- design tokens;
- tipografia;
- foco;
- motion;
- performance tier;
- BURO Ribbon;
- hero;
- poster card;
- landscape card;
- uma home demonstrativa navegável;
- estados loading, empty e error;
- restauração de foco;
- testes.

---

## Implementação obrigatória da primeira continuação

### 1. Design system

Crie ou adapte um módulo de design system com:

- cores semânticas;
- typography scale para TV;
- spacing de 8 px;
- shapes;
- focus tokens;
- motion tokens;
- performance tiers `Eco`, `Balanced`, `Cinematic` e `Auto`;
- reduced motion;
- contraste e semântica de acessibilidade.

Componentes mínimos:

```text
BuroScreen
BuroRibbon
BuroHero
BuroPosterCard
BuroLandscapeCard
BuroButton
BuroIconButton
BuroChip
BuroFocusRing
BuroProgressBar
BuroSkeleton
BuroEmptyState
BuroErrorState
```

Use os padrões e nomes da stack atual. Se o projeto já possui componentes equivalentes, evolua-os em vez de duplicar.

### 2. Focus system

Implemente foco previsível por D-pad:

- foco sempre visível;
- estados default/focused/pressed/selected/disabled;
- escala e borda conforme tokens;
- restauração ao voltar;
- sem saltos diagonais inesperados;
- skeleton não focável;
- listas com keys estáveis;
- testes de navegação.

### 3. BURO Ribbon

Implemente a navegação principal:

```text
Início | Ao Vivo | Filmes | Séries | Descobrir | Minha BURO | Pesquisa | Perfil
```

Regras:

- visual original;
- acesso previsível pelo botão Voltar na home;
- não copiar menus de concorrentes;
- preservar estado e foco da seção;
- adaptar a estrutura existente.

### 4. Home demonstrativa

Crie uma vertical slice com dados fake/local fixtures, sem depender de fontes ilegais ou externas.

A tela deve conter:

- Ribbon;
- hero cinematográfico;
- CTA principal e detalhes;
- fileira “Continuar assistindo”;
- fileira “Ao vivo agora”;
- fileira editorial de filmes/séries;
- cards em proporções diferentes;
- progresso;
- estados sem imagem;
- Ambient Color Engine básico ou fallback por gradiente;
- animações por performance tier;
- reduced motion.

A home deve parecer uma plataforma premium, não uma grade IPTV.

### 5. Navegação

- card → tela de detalhe demonstrativa ou rota existente;
- voltar → restaura card e scroll;
- Ribbon → troca seção sem perder estado;
- nenhuma rota deve produzir tela vazia sem tratamento;
- deep links existentes não podem ser quebrados.

### 6. Testes

Adicionar os testes adequados à stack:

- tokens e tiers;
- focus restoration;
- navegação por D-pad;
- loading/empty/error;
- reduced motion;
- screenshots/golden quando suportado;
- build de debug;
- lint/static analysis;
- testes existentes.

### 7. Performance

- nenhum parsing ou I/O pesado no main thread;
- listas virtualizadas;
- imagens dimensionadas;
- cancelamento de preview ao perder foco;
- sem trailer automático nesta primeira entrega, a menos que já exista infraestrutura segura;
- medir ou documentar limitações do hardware de teste.

---

## Restrições importantes

Não faça nesta primeira continuação:

- reescrever player funcional sem necessidade;
- implementar IA remota paga;
- implementar payment real antes de testes da fundação;
- adicionar trackers sem consentimento;
- baixar posters/trailers sem direitos;
- criar dependência obrigatória de uma API externa;
- implementar MultiView antes da fundação;
- usar blur pesado em todos os aparelhos;
- deixar TODOs genéricos no lugar de estados essenciais;
- declarar sucesso apenas porque o app compila.

---

## Critérios de aceitação desta execução

A tarefa estará concluída quando:

1. o estado atual estiver documentado;
2. o gap analysis estiver documentado;
3. houver ADR da fundação visual;
4. o projeto compilar;
5. a home vertical slice estiver navegável apenas por controle remoto;
6. foco estiver sempre visível;
7. o botão Voltar restaurar posição;
8. existirem tiers Eco/Balanced/Cinematic;
9. reduced motion funcionar;
10. loading, vazio e erro estiverem implementados;
11. testes novos e existentes tiverem resultado registrado;
12. nenhuma funcionalidade existente relevante tiver sido removida;
13. o diff estiver organizado em commits coerentes.

---

## Formato do relatório final do Codex

Ao terminar, informe:

### Estado inicial

- branch/commit;
- build anterior;
- principais componentes existentes.

### Mudanças realizadas

- arquivos;
- arquitetura;
- componentes;
- telas;
- testes;
- performance.

### Validação

- comandos executados;
- resultados;
- screenshots ou gravação quando possível;
- limitações do ambiente.

### Pendências

- blockers reais;
- decisões ainda necessárias;
- próximo milestone recomendado conforme `docs/gdd-v2/05-roadmap-backlog-and-acceptance.md`.

Não responda apenas com um plano. Faça a auditoria, implemente a vertical slice, execute os testes e entregue o resultado atual no repositório.
