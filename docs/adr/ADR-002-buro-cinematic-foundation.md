# ADR-002 — Fundação cinematográfica incremental

- Status: aceito
- Data: 31 de julho de 2026

## Contexto

A Sprint 1 já possui um fluxo funcional de importação M3U até reprodução Media3.
Entretanto, sua home técnica e sidebar não atendem ao GDD 2.0, que define a
identidade original BURO Cinematic System, BURO Ribbon, Living Home, foco
previsível, performance adaptativa e preferências de acessibilidade.

Uma reescrita completa colocaria parser, banco, player e segurança já validados
em risco. Também não existe justificativa para incorporar o protótipo alternativo
da branch `agent/android-tv-living-home-prototype` como um segundo aplicativo.

## Decisão

Evoluir a aplicação atual em camadas incrementais:

1. criar um pacote de design system dentro do módulo Android;
2. manter `IptvBuroTheme` como entrada compatível e fazê-lo fornecer tokens e
   preferências BURO;
3. evoluir `FocusSurface` sem quebrar seus consumidores;
4. substituir a sidebar pela BURO Ribbon, preservando as rotas de fontes,
   categorias, canais, player e configurações;
5. substituir o dashboard da home por uma Living Home composta por hero e
   fileiras lazy;
6. usar metadados visuais sintéticos e gradientes locais quando o catálogo está
   vazio, sem incluir canal, assinatura ou URL reproduzível no APK;
7. priorizar canais realmente importados quando existirem;
8. guardar foco por identificador estável e seção, com fallback determinístico;
9. não iniciar trailers, probes ou downloads automáticos nesta fundação;
10. manter listas virtualizadas e adaptar motion/transparência ao tier e às
    preferências de acessibilidade.

Os tiers serão:

- `Auto`: resolve conservadoramente conforme capacidade conhecida;
- `Eco`: sem escala animada e com superfícies opacas;
- `Balanced`: movimento curto e gradientes moderados;
- `Cinematic`: movimento completo permitido pelos tokens;

`reducedMotion` sempre tem precedência sobre o tier.

## Navegação

A Ribbon expõe:

```text
Início | Ao Vivo | Filmes | Séries | Descobrir | Minha BURO | Pesquisa | Perfil
```

Rotas ainda não implementadas exibem um estado informativo explícito, nunca uma
tela vazia. Fontes e Configurações continuam acessíveis por ações contextuais
da home e da Ribbon durante a transição.

Na home, Voltar primeiro move o foco para a Ribbon. Um segundo Voltar pode
delegar a saída ao sistema. Ao retornar de detalhes/player, o app tenta restaurar
o item estável; se ele não existir, restaura a posição e então o primeiro item
válido.

## Consequências

### Positivas

- preserva o vertical slice funcional;
- cria identidade visual consistente e testável;
- permite degradação em hardware modesto;
- oferece caminho direto para Story Page, Pulse e busca;
- reduz o risco de misturar mudanças visuais com GDD 3/4.

### Limitações

- a primeira Living Home usa fallbacks e metadados sintéticos limitados;
- não haverá trailer automático nem imagens remotas;
- foco/scroll persistente entre reinícios fica para um incremento posterior;
- rotas de Filmes, Séries, Descobrir, Minha BURO, Pesquisa e Perfil começam com
  estados tratados, não com funcionalidades completas;
- Android mobile e desktop permanecem fora desta fase.

## Alternativas rejeitadas

- substituir o app pelo protótipo remoto: perderia importação, Room, player e
  segurança já validados;
- manter a sidebar: conflita com o contrato explícito da BURO Ribbon;
- adicionar uma biblioteca de design genérica: aumentaria dependências e
  dificultaria a identidade própria;
- carregar arte e trailers externos: cria dependência de rede e risco de
  direitos antes da fundação estar pronta.
