# Análise de lacunas — GDD 2.0

- Data: 31 de julho de 2026
- Base auditada: implementação em `origin/main@2c9bd5b`
- Estado: primeira milestone da BURO Cinematic Foundation implementada
  e publicada na Pre-release `v0.1.0-alpha.1`, tag em `7e0b9ec`; GDD 2.0 ainda
  parcial

| Requirement | Existing | Partial | Missing | Notes / next action |
|---|:---:|:---:|:---:|---|
| BURO Cinematic System |  | ✓ |  | Tokens e componentes existem; adoção integral ainda pendente |
| Escala tipográfica para TV |  | ✓ |  | Aplicar os tokens também às telas legadas |
| Spacing em base 8 |  | ✓ |  | Consolidar valores ainda dispersos |
| Tiers Auto/Eco/Balanced/Cinematic | ✓ |  |  | Fundação e política local implementadas |
| Reduced motion | ✓ |  |  | Política disponível no design system |
| High contrast/reduced transparency |  | ✓ |  | Preferências e tokens estruturados; ampliar cobertura visual |
| Foco sempre visível |  | ✓ |  | Componentes focáveis evoluídos; falta cobertura instrumentada |
| Restauração de foco e scroll |  | ✓ |  | Último item da Home restaurado; scroll/rotas completas pendentes |
| BURO Ribbon | ✓ |  |  | Oito destinos substituem a sidebar |
| Back na Home leva à Ribbon | ✓ |  |  | Contrato mínimo implementado |
| Living Home |  | ✓ |  | Hero e rails existem; personalização real ainda pendente |
| Hero cinematográfico | ✓ |  |  | Arte local por gradiente, sem dependência externa |
| Poster e landscape cards | ✓ |  |  | Componentes e identificadores estáveis |
| Continuar assistindo |  | ✓ |  | Apenas demonstração sintética explicitamente marcada como DEMO |
| Ao vivo agora |  | ✓ |  | Home separa demonstração visual das fontes reais importadas |
| Fileira editorial |  | ✓ |  | Fixture visual sintética, sem URL ou conteúdo reproduzível |
| Loading/empty/error componentizados | ✓ |  |  | Estados tratados na fundação |
| Story Page |  | ✓ |  | Story demonstrativa, sem playback ou metadados reais |
| Player Media3 | ✓ |  |  | HLS funcional preservado |
| Seek por capacidade real | ✓ |  |  | Comportamento funcional preservado |
| Navegação D-pad |  | ✓ |  | Funcional e validada no aparelho; faltam testes instrumentados |
| Acessibilidade semântica |  | ✓ |  | Ampliar auditoria de labels, contraste e leitura |
| Lazy lists e keys estáveis | ✓ |  |  | Mantidas nas fileiras da Home |
| Preview/trailer cancelável |  |  | ✓ | Não há autoplay ou conexão automática nesta milestone |
| Home cacheável/HomeDocument |  |  | ✓ | Contrato local planejado para incremento posterior |
| Perfis/Kids/Minha BURO/Pesquisa |  |  | ✓ | Rotas tratadas; funcionalidades reais em milestones próprias |
| Configurações pelo Perfil | ✓ |  |  | Acesso contextual implementado |
| Métricas de cold/warm start |  |  | ✓ | Registrar baseline após estabilização visual |
| Testes de tiers/reduced motion | ✓ |  |  | Cobertura JVM da política |
| Testes de foco/D-pad |  |  | ✓ | Adicionar testes Compose instrumentados |
| Screenshot/golden |  |  | ✓ | Introduzir quando a base visual estiver estável |

## Entregue nesta milestone

1. tokens semânticos, tiers e preferências visuais/acessíveis;
2. componentes fundamentais do BURO Cinematic System;
3. BURO Ribbon integrada às rotas existentes;
4. Living Home com hero, rails sintéticas DEMO e rail de fontes reais sem URL;
5. Story demonstrativa sem playback;
6. foco/restauração mínima e `Back → Ribbon`;
7. placeholders explícitos para destinos futuros;
8. Configurações acessíveis pelo Perfil;
9. manutenção do vertical M3U → fontes → categorias → canais → player HLS;
10. localização em PT-BR, EN, DE e IT.

## Próximos incrementos do GDD 2.0

- aplicar integralmente tokens e componentes às telas legadas e à Home;
- completar restauração de foco, scroll e estado por rota;
- criar testes instrumentados de D-pad e testes de screenshot/golden;
- evoluir Story e fileiras para dados reais somente após definir os contratos de
  catálogo e playback;
- implementar Busca, Perfis, Minha BURO, Filmes e Séries em milestones próprias;
- medir cold start, recomposição e desempenho por tier.

GDD 3.0 e GDD 4.0 permanecem na sequência. Modelos temporais, retry, circuit
breaker e connection budget não fazem parte desta milestone visual.

## Revisão de 2 de agosto de 2026 — camada visual Windows

Alterações desde a tabela acima. Detalhes em
[`design-system.md`](../ux/design-system.md).

| Requirement | Antes | Agora | Nota |
|---|---|---|---|
| High contrast/reduced transparency | Parcial | Corrigido | A preferência era resolvida mas não chegava a nenhuma tela; os atalhos eram `val` estáticos |
| Foco sempre visível (Windows) | Ausente | Existente | `BuroInteractiveSurface`/`Row`; cards e navegação passaram a ser focáveis por `Tab` |
| Física de foco 1.045/1.06, 140–180 ms | Ausente | Existente | Somente Windows; Android já possuía `FocusSurface` |
| Hero 42–58% da altura (Windows) | Ausente | Existente | Era altura fixa de 330 dp |
| Spacing em base 8 | Parcial | Existente no Windows | `BuroSpacing`; gutter único para título e fileira |
| Loading componentizado (Windows) | Parcial | Existente | Esqueleto com o formato da tela final |
| Escala tipográfica (Windows) | Parcial | Existente | `BuroTypography` completa; telas deixaram de inventar tamanhos |
| Localização da interface Windows | Parcial | Parcial | Shell e Home em 4 idiomas; `XtreamWorkspace` e `XtreamLoginDialog` ainda fixos em português |
| Catálogo sem cara de painel admin | Ausente | Existente no caminho Xtream | Rail de categorias + grid editorial; `CatalogWorkspace` do M3U local ainda tem três painéis |
| Story Page com hierarquia editorial | Parcial | Existente | Ficha alinhada à esquerda com pôster, fatos e ações em linha |
| Cards com proporções por tipo | Parcial | Existente | 2:3 para filme/série, 16:9 para ao vivo, no mesmo formato da Home |
| BURO Ribbon no Windows | Ausente | Ausente | O desktop mantém barra lateral |
| Screenshot/golden | Ausente | Ausente | Não introduzido nesta revisão |
| Testes de foco/D-pad | Ausente | Ausente | Não introduzido nesta revisão |

A Home Windows redesenhada foi verificada por compilação, gate de testes e
execução do aplicativo com sessão vazia. **Não** foi validada com um catálogo
real; essa medição continua pendente.
