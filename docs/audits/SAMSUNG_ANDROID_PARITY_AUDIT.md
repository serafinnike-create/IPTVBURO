# Auditoria de paridade Android TV → Samsung Tizen

Data da auditoria: 2026-08-13  
Referência de produto: Android/Android TV  
Alvo: `apps/samsung-tizen`

## Escopo e regra de paridade

Paridade significa preservar a mesma identidade visual, hierarquia, estados e capacidades sempre que a plataforma permitir. Funções incompatíveis com Samsung TV devem ser ocultadas por capability, não simuladas. Em particular, downloads offline continuam desabilitados em TV e pagamentos exigem uma estratégia própria da loja Samsung.

A comparação foi feita entre o fluxo raiz e as telas Compose do Android, os tokens em `BuroTokens.kt`, os assets originais em `drawable-nodpi` e a implementação HTML/CSS/JavaScript do Tizen. A captura automatizada da janela do emulador ficou indisponível nesta sessão; por isso, a validação visual final em hardware/emulador permanece obrigatória.

## Matriz funcional e visual

| Área | Referência Android TV | Samsung Tizen auditado | Prioridade / próximo passo |
|---|---|---|---|
| Inicialização | Perfis → catálogo → arte → pronto, backdrop cinematográfico, painel central e indicador circular | **Alinhado nesta etapa:** mesmas quatro fases, assets originais, painel, spinner, tempo mínimo de revelação e erro recuperável | P0 — validar animação e legibilidade em TV física |
| Desempenho do boot | Dados locais e catálogo expostos por fluxos/consultas específicas | **Alinhado nesta etapa:** amostra de 120, categorias por índice, favoritos/progresso por ID e pesquisa por cursor | P1 — medir tempo com catálogo grande em hardware |
| Idioma inicial | Seletor PT-BR, EN, DE, IT e ES antes do restante do onboarding | **Alinhado nesta etapa:** gate inicial em lista vertical, D-pad e escolha persistida | P1 — validar textos/overscan em TV física |
| Licença/ativação | Checagem no boot, gate de ativação, estados de erro e recuperação | Ausente | P0 — implementar somente após estabilizar contrato de licença multiplataforma; nunca persistir chave em log/UI além do necessário |
| Aviso legal | Gate obrigatório antes do uso | Presente, com D-pad | P0 — aproximar composição visual do Android |
| Perfis | Seleção, criação, edição, exclusão, Kids, avatar/foto e fonte por perfil | Seleção/criação/Kids; sem edição, exclusão, avatar/foto ou seleção completa de fonte | P1 — portar editor e testes de identidade persistida |
| Shell/navegação | BURO Ribbon, foco marfim, destinos guiados por capabilities | **Alinhado nesta etapa:** Ribbon horizontal, perfil, foco marfim e `RETURN → Ribbon` | P1 — validar rolagem/overscan dos destinos em TV física |
| Home | Hero cinematográfico com arte original e prateleiras | **Alinhado nesta etapa:** Living Home vazia, aviso DEMO, hero/história e cards com assets originais; Home real ainda simplificada | P1 — aproximar prateleiras do catálogo real |
| Ao vivo | Categorias, canais, detalhes, programação e player | Categorias, detalhes, EPG Xtream e AVPlay presentes; filtros/estados incompletos | P1 — skeleton/erro/vazio e navegação por grade |
| Filmes | Catálogo, filtros, layouts, detalhes, enriquecimento e compartilhamento | Categorias e detalhes básicos Xtream | P1 — filtros/layouts, TMDb, elenco/pessoa e compartilhamento |
| Séries | Catálogo, detalhes, temporadas e episódios | Detalhes/episódios parciais | P1 — seletor de temporada, continuidade e estados completos |
| Descobrir | Destino próprio | Ausente | P1 — portar após consultas e filtros compartilhados |
| Minha BURO | Favoritos por perfil | Presente; itens favoritos fora da amostra inicial agora são hidratados por ID | P1 — testar grandes catálogos e estados de item removido |
| Continuar/Histórico | Continuidade por perfil com dados completos | Presente; itens de progresso fora da amostra inicial agora são hidratados por ID | P1 — testar grandes catálogos e progresso órfão |
| Pesquisa | Pesquisa completa no catálogo | Busca assíncrona no IndexedDB inteiro, limitada a 60 resultados sem materializar o catálogo | P1 — paginação e índice normalizado numa futura migration testada |
| Fontes | M3U, Xtream e fundação Stalker/Ministra; estados e gestão | M3U e Xtream ativos; adapter Stalker existe, UI bloqueada até validação de headers MAG em hardware | P1 — validar Stalker em TV física e então habilitar por capability |
| Player | Controles, timeline, faixas, velocidade, estados e retomada | AVPlay, play/pause, seek, áudio, legenda e progresso básicos | P1 — overlay completo, timeline, seletor de faixas e erros equivalentes |
| Assinaturas | Tela/fluxo conforme canal e capability | Ausente | P2 — definir Samsung Checkout/licenciamento antes de expor |
| Downloads | Capability condicionada à plataforma | Fundação USB e seção integradas; ação aparece somente quando `BuroDownloads.enabled()` confirma USB removível | P1 — validar Download/FileSystem APIs em TV física |
| Acessibilidade | D-pad, foco, contraste, movimento reduzido e semântica | D-pad, alto contraste e movimento reduzido presentes; auditoria visual/semântica incompleta | P1 — validar overscan, contraste e ordem de foco em hardware |
| Arte de catálogo | Artwork remoto resolvido em memória e assets próprios | Adapters não persistem artwork; cards ainda sem imagem do provedor | P1 — resolver artwork autenticado tarde, somente em memória, sem cache persistente |

## Diferenças críticas encontradas

1. O boot Samsung não seguia a linguagem Android: usava porcentagens e barra próprias, sem backdrop original.
2. A abertura lia `getAll()` de todos os itens. Em listas grandes isso aumenta memória, tempo de boot e risco de travar o WebView.
3. A pesquisa deixou de depender de `state.items`, mas ainda entrega a primeira página limitada a 60 resultados sem índice textual dedicado.
4. O foco Samsung era dourado, enquanto o design system Android usa superfície/marfin claro; o dourado é reservado à marca e ações principais.
5. O License Gate continua sendo a principal diferença P0 visível antes do catálogo.

## Bloqueio de instalação no emulador

O `.wgt` compila e assina, mas o emulador recusa inclusive um aplicativo mínimo assinado pelo mesmo perfil. Isso isola a falha no certificado/dispositivo, não no código do IPTV BURO. O emulador foi reiniciado, `install-permit` foi reaplicado e o erro persistiu. É necessário gerar um novo **distributor certificate** Samsung marcando o DUID da VM atual, preservando/importando o `author.p12` existente para não quebrar a identidade de atualização.

## Sequência recomendada

1. Renovar o distributor certificate para a VM atual e validar o pacote no emulador.
2. Com contrato estável, portar licença/ativação.
3. Aproximar as prateleiras reais, filtros e detalhes.
4. Completar perfis e controles do player.
5. Validar Stalker, USB e todos os estados em TV Samsung física.
