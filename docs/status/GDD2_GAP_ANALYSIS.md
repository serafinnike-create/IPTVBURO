# Análise de lacunas — GDD 2.0

- Data: 31 de julho de 2026
- Base auditada: `origin/main@8ad495e` mais implementação Android local
- Estado: primeira milestone da BURO Cinematic Foundation implementada
  localmente; GDD 2.0 ainda parcial

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
