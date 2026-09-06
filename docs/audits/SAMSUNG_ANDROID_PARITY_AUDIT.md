# Auditoria de paridade Android TV → Samsung Tizen

Data da auditoria: 2026-08-13  
Referência de produto: Android/Android TV  
Alvo: `apps/samsung-tizen`

## Escopo e regra de paridade

Paridade significa preservar a mesma identidade visual, hierarquia, estados e capacidades sempre que a plataforma permitir. Funções incompatíveis com Samsung TV devem ser ocultadas por capability, não simuladas. Em particular, download interno continua desabilitado em TV (a extensão Samsung usa apenas USB removível confirmado) e pagamentos exigem uma estratégia própria da loja Samsung.

A comparação foi feita entre o fluxo raiz e as telas Compose do Android, os tokens em `BuroTokens.kt`, os assets originais em `drawable-nodpi` e a implementação HTML/CSS/JavaScript do Tizen. As mesmas fontes Web são percorridas automaticamente em Chromium a 1920×1080 para inspecionar a composição estática; a captura da janela OpenGL do emulador continuou branca e o `screendump` HMP preto. Por isso, animação, vídeo e validação visual final no framebuffer nativo/hardware permanecem obrigatórios.

## Matriz funcional e visual

> **Nota de atualização — 2026-09-06:** a linha "Licença/ativação" abaixo afirma que a licença "permanece desabilitada sem chave pública/configuração real". Isso não é mais verdade: `apps/samsung-tizen/js/license.js` (linhas 45-47) já compila uma chave pública ECDSA P-256 real de produção (`SERVER_PUBLIC_KEY_ECDSA`), e o comentário no próprio arquivo registra que trocá-la invalidaria as licenças das TVs instaladas. A afirmação histórica da auditoria é mantida abaixo para registro, mas deve ser lida como superada.

| Área | Referência Android TV | Samsung Tizen auditado | Prioridade / próximo passo |
|---|---|---|---|
| Inicialização | Perfis → catálogo → arte → pronto, backdrop cinematográfico, marca BURO, painel central e indicador circular | **Alinhado nesta etapa:** seis etapas reais (`perfis → catálogo → arte → varredura → Home → pronto`), mosaico de até 12 capas públicas com atlas local de reserva, marca circular, painel e barra determinística, sem spinner genérico. A Home, as prateleiras TMDb configuradas, o primeiro Hero enriquecido e as artes do primeiro quadro bloqueiam a revelação; erro é recuperável, a região de status é atômica e movimento reduzido interrompe as animações | P0 — validar animação, progresso e legibilidade no framebuffer de uma TV física |
| Desempenho do boot | Dados locais e catálogo expostos por fluxos/consultas específicas | **Alinhado nesta etapa:** amostra de 120 no boot, categorias por índice, favoritos/progresso por ID e pesquisa por cursor. A Home e Descobrir percorrem o IndexedDB por cursor sem materializar todo o catálogo; um mapa de visibilidade evita procurar a categoria novamente para cada item. Categorias extensas abrem 200 registros, retomam o índice ordenado em blocos de 200 sob demanda e limitam o DOM a 200 cards, igual ao `PAGE_SIZE` Android. A hidratação Xtream roda depois do shell, uma categoria por vez e prioriza filmes/séries | P1 — medir rede, tempo, memória, composição da Home e troca de página com catálogo grande em hardware |
| Idioma inicial | Seletor PT-BR, EN, DE, IT e ES antes do restante do onboarding | **Alinhado nesta etapa:** gate inicial em lista vertical, D-pad e escolha persistida | P1 — validar textos/overscan em TV física |
| Licença/ativação | Checagem no boot, gate de ativação, estados de erro e recuperação | Fundação integrada: identidade assinada, validação criptográfica, período de teste, janela offline, revogação e tela de ativação; permanece desabilitada sem chave pública/configuração real | P0 — integrar endpoint e chave pública reais somente no processo seguro de release |
| Aviso legal | Gate obrigatório antes do uso | **Alinhado nesta etapa:** composição lateral com marca real, card de superfície, três parágrafos equivalentes ao Android, proteção das credenciais em destaque e um único botão de aceite inicialmente focado. O texto cobre ausência de conteúdo, responsabilidade da fonte, DRM/autenticação/paywall e ausência de vínculo com provedores nos cinco idiomas | P0 — revisão jurídica e validação de legibilidade/overscan em TV física |
| Perfis | Seleção, criação, edição, exclusão, Kids, avatar/foto e fonte por perfil | **Alinhado nesta etapa:** seleção, criação/edição, cinco avatares, Kids, fonte preferida, limite de cinco perfis e exclusão em duas etapas. Foto personalizada usa um seletor próprio para USB montado: enumera no máximo 60 JPG/PNG/WebP de até 5 MB e profundidade dois, recorta o centro, reduz para no máximo 320×320, converte para JPEG pequeno e persiste somente a data URL privada no IndexedDB. O caminho do USB é transitório e nunca entra no perfil | P1 — validar leitura, memória, persistência e legibilidade dos cinco perfis em hardware |
| Shell/navegação | BURO Ribbon, foco marfim, destinos guiados por capabilities | **Alinhado nesta etapa:** Ribbon horizontal, perfil, foco marfim e `RETURN → Ribbon` | P1 — validar rolagem/overscan dos destinos em TV física |
| Home | Hero cinematográfico com arte original e prateleiras | **Alinhado nesta etapa:** Living Home vazia com aviso DEMO/assets originais e o mesmo catálogo fictício Android: 4 cards de continuidade, 4 de programação simulada e 6 pôsteres editoriais, todos com título/subtítulo nos cinco idiomas e detalhes próprios sem playback. O aviso participa do fluxo flexível da barra superior; a medição em 1920×1080 confirma zero interseção com sino/selo e que foco em detalhes não rola Ribbon/topbar. Com catálogo real, o cache aparece imediatamente e uma composição assíncrona consulta todo o IndexedDB, respeita fonte/perfil Kids/categorias protegidas e produz os mesmos trilhos editoriais do Android: continuidade, lançamentos do ano atual/anterior, clássicos recém-chegados, adicionados recentemente, melhores avaliações, filmes e séries. Na continuidade, progresso de episódio é projetado na série pai; várias retomadas da mesma série formam um único card, ordenado e preenchido pelo episódio incompleto mais recente. O Hero prioriza coleção sazonal, lançamentos e nota, gira até dez destaques a cada dez segundos, pausa com foco ou movimento reduzido e conserva o foco exato do D-pad. A comparação de janelas reais Samsung/Windows levou o destaque a 51,6% da área útil, dentro do contrato desktop de 42–58%, com três linhas de sinopse, metadados dourados e degradê vertical até a primeira prateleira. Para Xtream, os dados do provedor têm prioridade; o TMDb completa somente sinopse, fatos ou arte ausentes e assume o fallback se a consulta Xtream falhar. M3U e Stalker usam diretamente esse fallback quando há credencial TMDb válida. A fila continua serial, transitória, limitada a dez candidatos e cache LRU de 20 itens, não persiste URLs/segredos, tolera falhas individuais, cancela trabalho obsoleto e enriquece o primeiro Hero ainda durante o boot. O fallback visual é backdrop → pôster → asset local. Falha da composição mantém cache com Retry. `addedAt`/ordem vêm do provedor/importação e sobrevivem à reconciliação. Categorias Xtream nunca abertas entram numa fila serial retomável; a Home mostra progresso, pausa/continua pelo D-pad e se recompõe uma única vez sobre o banco completo ao final | P1 — validar rolagem, rotação e consumo de memória em hardware; TMDb/CORS e imagens reais já passaram no emulador |
| Ao vivo | Categorias, canais, detalhes, programação e player | Categorias, detalhes, EPG Xtream e AVPlay presentes; loading/skeleton, erro com retry, vazio e descarte de resposta atrasada são compartilhados com o restante do catálogo. **Alinhado nesta etapa:** `AGORA` é derivado do intervalo real início/fim, programa atual aparece no hero com progresso temporal e programas encerrados são atenuados sem desaparecer | P1 — validar relógio/fuso, EPG e navegação em hardware |
| Filmes | Catálogo, filtros, layouts, detalhes, enriquecimento, trailer, pessoa e compartilhamento | Categorias, três layouts, seis ordenações, filtros derivados de gênero/ano, detalhes Xtream e estados próprios de loading/erro/retry/vazio. **Alinhado nesta etapa:** hero exibe fatos equivalentes ao Android, progresso assistido, créditos separados, país e elenco deduplicado. O detalhe prioriza backdrop e usa pôster como fallback, ambos somente em memória. Trailer da fonte ou do TMDb é reduzido a ID público e reproduzido em `youtube-nocookie`. Com chave TMDb, a tela recebe fallback de sinopse/fatos/arte, fotos e personagens do elenco; selecionar uma pessoa abre retrato, biografia e até 24 créditos, cruzados por cursor com todo o IndexedDB para tornar títulos locais acionáveis. Créditos externos abrem o detalhe de Assinaturas e retornam à mesma pessoa. **Compartilhar** gera localmente um QR do mesmo `TitleShareLink` público do Android, com identidade/título/ano, imagem apenas de host público permitido e sinopse limitada; fonte, stream e credenciais não são aceitos pelo contrato. Dados válidos da fonte têm precedência. O cache de detalhes TMDb é transitório e limitado a 40 títulos; somente os cartões públicos das prateleiras de Assinaturas possuem cache diário separado | P1 — validar TMDb, trailer, QR e CORS em hardware |
| Séries | Catálogo, detalhes, temporadas, episódios, trailer, pessoa, compartilhamento e download em lote | Detalhes Xtream, episódios agrupados por temporada, progresso/identificação T/E e estados de loading/erro/vazio. **Alinhado nesta etapa:** fatos, contagem de temporadas/episódios, créditos, elenco, backdrop, trailer e QR público para compartilhar; enriquecimento TMDb compartilha o mesmo contrato seguro de filmes, inclusive fotos do elenco, filmografia e ligação aos detalhes externos de Assinaturas. A ação principal do topo replica o Windows: prefere o primeiro episódio retomável, mostra temporada/episódio e inicia diretamente na posição anunciada; sem progresso usa o primeiro episódio e série vazia não cria botão inerte. Reabertura atualiza detalhes/episódios em transação única, mantém cache utilizável na falha e limpa apenas referências de episódios removidos. A temporada expandida revela episódios em lotes de 40 com foco D-pad determinístico. Com Xtream, USB montado e capability oficial, Baixar série/temporada pede confirmação, calcula somente transferências efetivas e enfileira por T/E | P1 — validar fila em lote, limites da plataforma, retirada do USB e reconciliação após reinício em hardware |
| Descobrir | Baralho personalizado de 15 filmes/séries, uma carta e a próxima em profundidade, Guardar/Pular, contador e nova rodada; favoritos/histórico e decisões da sessão formam o gosto | **Alinhado nesta etapa:** o antigo conjunto de cinco prateleiras foi substituído pelo mesmo contrato puro `DiscoveryDeck`/`SessionTaste`. A TV limita o pool a 400 filmes + 400 séries, exclui ao vivo, fonte inativa, categorias ocultas/bloqueadas, favoritos, histórico e ids julgados. Guardar usa o favorito IndexedDB existente antes de avançar; Pular só ensina a sessão; Detalhes/RETURN preservam carta e foco. A tela desenha somente duas cartas e os cinco idiomas/movimento reduzido estão cobertos | P1 — validar arte, fluidez, legibilidade e tempo do cursor com catálogo grande em emulador/TV física |
| Minha BURO | Favoritos por perfil, separados por tipo quando necessário, carregados por inclusão decrescente com limite 200 | **Alinhado nesta etapa:** referências fora da amostra inicial são hidratadas por ID, inclusão mais recente vem primeiro e o mesmo limite 200 do Android é respeitado. Chips `Todos`, `Filmes`, `Séries` e `Ao Vivo` filtram sem alterar persistência; o Tizen limita o DOM a 40 cards por página, recalcula a última página quando a coleção muda e conserva foco D-pad | P1 — validar tempo de abertura, imagens e rolagem com 200 favoritos em TV física |
| Continuar/Histórico | Continuidade por perfil, limites 20/60, ordem por atividade e resolução de episódio para a série | **Alinhado nesta etapa:** referências fora da amostra inicial são hidratadas, `updatedAt` decrescente e limites Android `20/60` são aplicados antes da apresentação. Episódio aponta para o pai Série, abre seus detalhes e mantém a porcentagem do episódio mais recente numa cópia transitória. A Home reutiliza essa identidade: deduplica vários episódios pela série e reage à conclusão removendo ou reordenando o card. O pai ausente da amostra inicial é buscado no IndexedDB durante o boot. Histórico pagina 40+20, filtra por tipo, pesquisa, remove linhas e permite limpeza confirmada somente do perfil ativo. Continuar oferece no card `Continuar`, `Assistir do início` e `Remover`; para séries, as duas primeiras ações reproduzem diretamente o episódio real da linha, sem confundir a identidade projetada do pai | P1 — validar tempo de boot, foco e progresso de várias temporadas em TV física |
| Pesquisa | Pesquisa completa no catálogo | Busca assíncrona local por substring, paginada em 40 itens sem materializar todos os resultados; possui estados inicial/loading/vazio/erro/retry, anterior/próxima e descarta respostas obsoletas. Teclado virtual/físico publica somente a última consulta após os mesmos 300 ms do Android; o botão explícito permanece disponível para D-pad. **Alinhado nesta etapa:** a migration IndexedDB v1→v2 preserva cada item/locator, grava título normalizado sem acentos e cria índice composto por prioridade de tipo, ordem do provedor e id. O resultado passa a seguir Android (`Filmes → Séries → demais`) e cada página guarda um cursor opaco para continuar no B-tree sem reavaliar os resultados anteriores. Consultas de um caractere são recusadas antes do cursor, como no repositório Android | P1 — medir tempo da migration única, tamanho do índice, consulta e teclado virtual com catálogo real em hardware |
| Fontes | Arquivo M3U/M3U8, Xtream e fundação Stalker/Ministra; estados e gestão | **Alinhado nesta etapa para M3U/Xtream:** M3U por URL e por arquivo autorizado no USB, seleção por D-pad, inclusão e atualização transacional, identidade estável preservando favoritos/progresso, limpeza seletiva e segredos/seletor opaco somente no KeyManager. Xtream completa/atualiza categorias em fila serial depois do boot, com prioridade editorial, checkpoint de 24 h inclusive para categoria vazia, retry limitado, cancelamento real do XHR, retomada após reinício e atualização forçada. O checkpoint contém somente ids/contagens/datas; a fonte mostra progresso e contagem final. Abrir a categoria que está na fila reutiliza a requisição em curso. Seleção/renomeação/exclusão têm cascata segura. Stalker possui transporte, estados, importação e paginação servidor+IndexedDB de 200 itens protegidos por cenário sintético 200+200+50; headers MAG e portal real ainda exigem hardware/servidor autorizado | P1 — validar FileSystem/USB, atualização com catálogos grandes, throttling real de provedor e Stalker em TV física |
| Player | Controles, timeline, faixas, velocidade, estados, formato e retomada | AVPlay, play/pause, seek, progresso e overlay com timeline/tempo; áudio e legenda têm menus explícitos por D-pad, nomes derivados do stream e opção de desligar legenda. Velocidade aparece por capability apenas em conteúdo sob demanda e oferece 1×/2×. A retomada usa a mesma política do domínio Kotlin, pergunta Continuar/Recomeçar e aplica `seekTo` antes do play; ao vivo não grava progresso. **Alinhado nesta etapa:** preparo/buffering possui carregamento central; falhas distinguem conexão, formato e fonte inacessível com causa sanitizada; Retry recebe foco inicial. ENTER agora cumpre o próprio rótulo e pausa/retoma; esquerda/direita usam os mesmos −10/+30 s do Android; vermelho alterna Minha BURO sem recompor a tela escondida. RETURN e fim natural recompõem o shell a partir do checkpoint recém-gravado, atualizam percentual/marca de assistido/Continuar e preservam foco, rolagem vertical e posição horizontal. Em canal ao vivo, o topo identifica programa atual/próximo e verde abre até 12 programas atuais/futuros já carregados. A tecla azul percorre os três modos documentados do AVPlay — proporção preservada, preencher e automática — e desaparece quando o firmware não expõe a API | P1 — validar retomada, Retry, guia, favorito, modos de imagem, troca de faixas, 2× por protocolo e formatos AVPlay em hardware |
| Assinaturas | Catálogo externo “onde assistir”, condicionado a TMDb/chave e região | **Implementado nesta etapa:** destino aparece apenas com chave TMDb efetiva no KeyManager; quatro filtros (`Filmes`, `Séries`, `Esta semana`, `Em breve`), cinco regiões, diretório limitado a 12 serviços e 20 títulos por prateleira, loading/progresso/erro/retry/vazio e cancelamento. Nome textual e marca pública `w92` do TMDb aparecem juntos no cabeçalho, cards, grade **Ver mais** e ofertas; ausência/falha da imagem conserva o texto. As prateleiras públicas do mesmo dia são reutilizadas por região/filtro/idioma e abrem sem rede; até oito respostas ficam no cache, contendo somente id, título, tipo, ano, data, pôster e marca de serviço saneados. Chave, oferta, sinopse e URL externa não são persistidas; data vencida/corrupção/formato antigo viram miss e vazio não substitui resposta válida. O detalhe combina sinopse, fatos, arte, trailer, elenco, correspondência com a biblioteca inteira e buckets assinatura/anúncios/grátis/aluguel/compra. Cada oferta externa exibe literalmente `Streaming data provided by JustWatch`; nenhum preço é inventado. A saída usa `ApplicationControl` apenas para HTTPS e hosts explicitamente permitidos. Disponibilidade desconhecida não é apresentada como indisponibilidade global. No emulador, a consulta real BR carregou serviços, prateleiras, marcas e capas | P1 — validar latência e retorno do aplicativo externo em hardware; CORS/cache/imagens reais já passaram no emulador e Samsung Checkout/licenciamento continua separado |
| Downloads | Capability condicionada à plataforma, filtro Filmes/Séries, lote, recuperação, progresso e layout compacto | Fundação USB e seção integradas; ação aparece somente quando `BuroDownloads.enabled()` confirma API, feature flag oficial e USB removível. **Alinhado nesta etapa:** a fila expõe apenas o tipo não sensível necessário à UI, oferece `Todos`, `Filmes`, `Séries` quando ambos existem e alterna uma lista compacta sem modificar a fila. Operações ativas vêm primeiro, depois falhas/cancelamentos e concluídas, como Android/Windows; busca local sem acentos e páginas de 40 impedem uma fila grande de inflar o DOM. Uma mudança assíncrona conserva visível a linha focada mesmo quando sua nova prioridade a move para outra página. Série/temporada segue a seleção Android: ordena por T/E, ignora concluídos, mantém ativos elegíveis para deduplicação atômica no `start` e informa sucessos/falhas parciais. A transferência ativa mostra porcentagem, velocidade recente suavizada e tempo restante, sem inventar taxa na primeira amostra. Pausa, retomada, retrocesso e reinício limpam essa telemetria. Um snapshot local limitado conserva somente metadados não sensíveis; no boot, o adapter consulta `getState`, reinstala `setListener`, conclui o rename de `.part` quando necessário e marca operação nativa perdida como interrompida | P1 — validar Download/FileSystem APIs, taxa real, encerramento/retomada, busca, foco e densidade em TV física |
| Acessibilidade | D-pad, foco, contraste, movimento reduzido e semântica | **Alinhado no que pode ser verificado sem hardware:** o foco DOM acompanha o foco visual do D-pad; Ribbon/landmarks/título corrente, labels de formulário, checkbox Kids, switches, filtros selecionados, temporadas expandidas, estados busy/status/alert, diálogos e progressos de boot/player/trailer expõem nome/papel/valor. O boot anuncia cada fase numa única região atômica e desliga spinner, marca e transição com movimento reduzido. O app deixa de anunciar a árvore inteira a cada render, o fundo fica oculto semanticamente durante player/trailer e mouse/touch são ligados aos elementos recém-renderizados. A auditoria automatizada mede contraste mínimo de 4,89:1, indicador de foco, movimento reduzido e alvos compactos de pelo menos 46 px | P1 — validar ordem de leitura, Voice Guide, overscan e foco numa TV Samsung física |
| Arte de catálogo | Artwork remoto resolvido em memória, assets próprios e cache controlado | Artwork M3U/Xtream é resolvido tarde e exibido em cards, hero e detalhes. Em Configurações, o usuário pode ativar cópia opcional de toda arte pública descoberta para USB, com limite, duas transferências paralelas, progresso real, Pausar/Continuar/Atualizar e exclusão explícita. URLs autenticadas ou com token nunca entram no IndexedDB/localStorage/USB | P1 — validar CORS, retirada/reinserção do USB, limite e desempenho em hardware |

## Diferenças críticas encontradas

1. O boot Samsung não seguia a linguagem Android: usava porcentagens e barra próprias, sem backdrop original. Agora preserva as quatro fases reais, usa os assets cinematográficos, marca circular BURO, spinner e progresso semântico; não inventa tempo restante.
2. A abertura lia `getAll()` de todos os itens. Em listas grandes isso aumenta memória, tempo de boot e risco de travar o WebView.
3. A pesquisa deixou de depender de `state.items` e agora pagina por cursor; ainda não possui índice textual dedicado, portanto páginas distantes percorrem o store desde o início.
4. O foco Samsung era dourado, enquanto o design system Android usa superfície/marfin claro; o dourado é reservado à marca e ações principais.
5. A fundação de licença existe e é protegida por assinatura, mas a configuração real de endpoint/chave pública continua deliberadamente ausente do pacote de desenvolvimento.
6. A atualização de fontes não existia e a inclusão persistia as tabelas separadamente. M3U e Xtream agora reconciliam uma fotografia atômica; uma falha remota ou de armazenamento conserva o catálogo anterior, e mudança de grupo M3U não apaga favorito/progresso de uma identidade ainda presente.
7. A Home Samsung usava somente a amostra inicial e misturava atalhos de Minha BURO/Ao Vivo no lugar dos trilhos editoriais. Agora consulta toda a fotografia local e segue a composição Android.
8. A fonte Xtream era preguiçosa: categorias nunca abertas não possuíam itens locais para Home, Descobrir ou Pesquisa. Agora uma fila serial completa a fonte em segundo plano, registra cada transação confirmada, retoma sem repetir trabalho e pode ser pausada; falta medir o comportamento com limites reais de provedores e hardware Samsung.
9. O Hero Samsung mostrava uma sinopse genérica mesmo quando Xtream oferecia detalhes reais. Agora os dez candidatos recebem enriquecimento limitado e transitório; Xtream continua prioritário e o TMDb completa apenas campos ausentes ou assume a falha, enquanto M3U/Stalker podem usar o TMDb diretamente. O primeiro destaque bloqueia a revelação da Home, sem aumentar o banco ou expor credenciais. Sinopse, fatos, nota e backdrop reais passaram no emulador; legibilidade e consumo ainda precisam de TV física.
10. O aviso legal Samsung resumia em uma frase e exigia checkbox + botão, enquanto o Android já separava as três responsabilidades e aceitava por uma única ação. O fluxo e a composição agora seguem a referência Android; a redação ainda exige revisão jurídica antes da loja.
11. Filmes e séries ignoravam o `youtube_trailer` e o backdrop devolvidos por Xtream. Os detalhes agora usam backdrop → pôster → fundo local e oferecem trailer incorporado apenas após reduzir a referência a um ID YouTube seguro. Mensagens do iframe são aceitas somente da janela criada e de origens YouTube exatas; nenhum URL de provedor, segredo ou estado do trailer é persistido.
12. O Samsung não possuía a chave TMDb por perfil/household, fotos do elenco nem tela de pessoa existentes no Android. Agora Configurações valida a chave diretamente no endpoint fixo do TMDb antes de gravá-la no KeyManager; a chave própria do perfil precede a compartilhada, a exclusão do perfil remove seu segredo e falhas de rede/chave nunca substituem os dados Xtream. URLs de imagem são construídos apenas sobre `image.tmdb.org`, respostas ficam em memória limitada e a filmografia consulta o catálogo local por cursor, sem materializar a biblioteca inteira.
13. “Assinaturas/Onde assistir” não existia no Samsung. Agora a capability segue a chave TMDb real, replica os quatro modos Android e a região, consulta os serviços em série com limites e cancelamento, distingue disponibilidade desconhecida, cruza a biblioteca local e apresenta cada bucket sem inferir preço. Links de serviços saem somente por `ApplicationControl` com operação `view`, privilégio declarado e allowlist exata; uma URL maliciosa é recusada antes de chegar à plataforma. O detalhe também ganhou o lembrete de estreias externas descrito no item 42 e o catálogo amplo por serviço do item 43.
14. A share sheet Android não tem equivalente útil numa TV. Filmes e séries agora oferecem um fluxo Samsung real: QR vetorial gerado inteiramente no aparelho, com o mesmo domínio `TitleShareLink` e a página pública `/t/`. O encoder suporta byte mode/M/versões 1–10 e teve os campos BCH de versão 7–10 corrigidos também no domínio Kotlin. Um decodificador independente lê o link longo de versão 10 nos testes. Se imagem ou sinopse excederem a capacidade, o QR remove primeiro dados opcionais e conserva identidade/título; nenhum serviço remoto de QR recebe o texto.
15. A foto de perfil Android dependia do picker de documentos, inexistente como experiência equivalente numa TV. O Samsung agora usa a capability real disponível: USB removível. A seleção é navegável por D-pad, o rascunho sobrevive à ida ao seletor, a imagem é recortada/comprimida localmente e aparece no card, editor e Ribbon. Só o JPEG limitado é persistido; referências `file:` e caminhos físicos permanecem dentro do adapter USB e são invalidados quando o volume sai.
16. Minha BURO, Continuar, Histórico e Downloads misturavam tipos numa lista única, embora o Android já oferecesse seleção contextual. O Samsung agora deriva os tipos realmente presentes e mostra os mesmos chips sem criar destinos vazios. Continuar/Histórico também deixaram de seguir a ordem acidental de `state.items` e passam a respeitar a atividade mais recente; Downloads transporta apenas `MOVIE`/`EPISODE` no snapshot público e ganhou densidade compacta.
17. O Samsung não oferecia controle de formato e tratava toda falha de `prepareAsync` como conexão. Agora a tecla azul usa somente os três valores realmente aceitos por `setDisplayMethod`, começando por letterbox para não recortar a imagem. `InvalidAccessError` e arquivo ausente recebem uma orientação própria, enquanto rede, formato não suportado e erro desconhecido permanecem separados. Como o AVPlay não garante o status HTTP exato nessa callback, a interface não inventa 401/403/404.
18. O Android permitia baixar uma temporada ou a série inteira, mas o Samsung exigia abrir cada episódio e nem verificava a feature flag oficial de Download. Agora as ações existem somente com Xtream + USB + API/capability reais, exibem o número efetivo antes de iniciar, pulam arquivos já concluídos, ordenam T/E e deixam o próprio `start` deduplicar operações ativas. URLs continuam resolvidas somente no callback e nunca entram no snapshot da fila. Ao reiniciar, somente id/nome/tipo/arquivo/progresso/id da operação são restaurados; o estado vem novamente do DownloadManager. Se a operação terminou fechada, o `.part` é finalizado; se o runtime perdeu o id, a UI mostra interrupção em vez de progresso inventado. Essa reconciliação ainda exige confirmação em hardware.
19. O Android resolve o arquivo VOD de uma fonte M3U no momento do download, mas o Samsung restringia a ação a Xtream. Filmes M3U que apontam diretamente para `MP4`, `MKV`, `AVI`, `MOV`, `WebM`, `TS` ou `M2TS` agora usam a mesma extensão USB: a lista é relida do segredo no KeyManager, o item é reencontrado por identidade estável mesmo se a ordem mudou e a URL vai diretamente para `DownloadRequest`. A URL não entra no item, IndexedDB, fila pública ou snapshot. Manifestos HLS `.m3u8` continuam sem ação offline, pois não são um arquivo único e exigiriam um downloader consciente de segmentos; essa capability não é simulada.
20. Tamanho, cor e fundo de legenda eram opções persistidas no Samsung sem efeito sobre a imagem, enquanto Android aplica as três escolhas no player. Quando o firmware expõe `setSilentSubtitle`, o adapter agora mantém a camada nativa silenciosa, recebe os cues por `onsubtitlechange` e os desenha numa camada HTML própria com os mesmos quatro tamanhos, cinco cores e fundo opcional. O texto passa somente por `textContent`, markup do stream é removido, o cue expira pelo tempo informado e desligar legendas limpa a camada imediatamente. Em firmware sem essa API, as preferências visuais ficam ocultas e a seleção nativa de faixas permanece disponível.
21. O Android reproduzia uma cópia offline concluída sem consultar novamente o provedor, mas Downloads no Samsung só permitia remover a entrada. O Tizen agora oferece `Assistir` e `Remover` para itens concluídos: no clique, procura o nome seguro em cada USB realmente montado, transforma o `File` encontrado em URI `file:` e entrega essa URI diretamente ao AVPlay. A fila e o snapshot continuam sem caminho ou URI. Se o rename final falhou, um `.part` já concluído permanece reproduzível; se o arquivo saiu do USB, o fluxo falha como fonte indisponível sem recorrer à rede. A mesma identidade de conteúdo preserva a decisão `Continuar/Recomeçar` e o progresso do título.
22. O Android evita abrir diretamente uma variante de filme marcada como `4K`, `UHD`, `HDR`, `DV`, `HEVC` ou `H.265` quando a mesma fonte possui uma alternativa compatível do mesmo título. O Samsung agora aplica o mesmo contrato: antes do AVPlay, percorre somente o índice de filmes daquela fonte, mantém memória O(1), prefere uma variante sem marcador de alto risco e usa ordem/id como desempate. A URL continua resolvida somente depois dessa escolha; título, favorito, retomada e progresso permanecem ligados ao item originalmente selecionado. Se não houver alternativa ou a consulta local falhar, o item solicitado continua sendo reproduzido normalmente.
23. Os detalhes Android/Windows mantêm a ação de download no mesmo lugar e mudam seu significado conforme o estado, além de oferecer uma ação individual em cada episódio. O Samsung mostrava `Baixar` no detalhe do filme mesmo com a transferência em andamento e só oferecia série/temporada em lote. Agora filmes e episódios refletem a fila real sem duplicá-la: `Baixar` vira `Cancelar`, `Continuar`, `Assistir`/`Remover` ou `Tentar novamente`; falha/cancelamento terminal é removido antes de uma nova operação real. Cada episódio expandido recebe controles próprios por D-pad, enquanto as ações em lote continuam calculando somente os candidatos efetivos. O observador da fila recompõe o detalhe sem exigir sair e voltar, preservando a temporada aberta e sem colocar URL no DOM, fila ou snapshot.
24. Android e Windows permitem importar um documento M3U/M3U8 local, mas o Samsung aceitava apenas URL. Com USB montado, `Adicionar fonte` agora oferece um seletor próprio por D-pad: enumera até 40 arquivos M3U/M3U8 de no máximo 16 MB e profundidade dois, mostra somente nome/tamanho e lê UTF-8 pelo FileSystem da TV. O catálogo passa pelo mesmo parser limitado e pela mesma transação das fontes remotas. O caminho e o conteúdo nunca entram em IndexedDB/localStorage; o KeyManager conserva somente token opaco, nome e tamanho para reencontrar a seleção. Mudança do rótulo do volume usa fallback apenas se nome+tamanho identificarem exatamente um arquivo; ambiguidade ou USB ausente falham fechados. Atualização, arte, reprodução e arquivo VOD direto reutilizam a resolução local tardia.
25. A busca Android publica o texto automaticamente 300 ms depois da última tecla, enquanto o Samsung exigia selecionar um segundo botão depois de digitar. O campo Tizen agora usa o mesmo debounce: cancela a consulta pendente em cada nova tecla, executa somente a última contra o IndexedDB local e continua descartando respostas obsoletas. A ação explícita `Pesquisar` permanece como alternativa natural para D-pad e cancela o timer para não duplicar a leitura.
26. O Samsung desenhava foco próprio, mas não movia `document.activeElement`; leitores de tela e teclado recebiam uma posição diferente da mostrada. Além disso, `#app` inteiro era uma região `aria-live`, controles customizados não publicavam estado e o bind de clique ocorria antes de descobrir os novos elementos após cada render. Agora o roving `tabindex` move foco real, labels apontam para seus campos, Ribbon usa destino corrente, checkbox/switch/filtros/temporadas publicam estado, carregamentos e erros usam status/alert e player/trailer expõem diálogo/progresso e ocultam o fundo. O bind foi reordenado para os elementos atuais, restaurando mouse/touch. Uma suíte estática mantém contraste AA, foco visível, movimento reduzido e alvos mínimos; Voice Guide e ordem de leitura ainda exigem hardware.
27. A busca Samsung usava a ordem acidental da chave primária e `offset`: abrir a segunda página relia a primeira, e o custo crescia a cada avanço. O Android ordena filmes, séries e depois os demais tipos por `sort_order/id`, e recusa uma única letra. O IndexedDB Samsung agora está na versão 2: uma migration cursor-a-cursor acrescenta somente `searchName`, `searchRank` e `searchSort`, preservando identidade, locator e referências, e cria `bySearchOrder`. A busca continua sendo substring sem acento, mas pagina com a chave composta opaca da última linha; avançar salta no B-tree e não reavalia resultados anteriores. `put`, fotografia completa de fonte e reconciliação de categoria geram os mesmos campos derivados, cobertos por teste de migration v1 real.

28. O Android virtualiza todas as categorias disponíveis no gerenciamento, mas o Samsung descartava silenciosamente tudo depois da posição 300 e misturava categorias de fontes inativas. A tela Tizen agora considera somente a fonte do perfil atual, ordena Ao Vivo → Filmes → Séries e pagina 40 linhas por vez. Qualquer categoria continua alcançável para ocultar ou bloquear, inclusive além da antiga barreira; página, foco e estados semânticos sobrevivem à alteração. Se a quantidade mudar, a página é limitada novamente a uma posição válida. Perfis Kids não recebem nomes de categorias adultas nessa tela. A suíte cobre 326 categorias ativas, uma segunda fonte, persistência e as nove páginas.

29. Minha BURO, Continuar e Histórico materializavam todos os cards conhecidos, seguiam a ordem de `state.items` nos favoritos e exibiam episódios isolados. A referência Android ordena favoritos por inclusão, limita favoritos/continuidade/histórico a `200/20/60` e resolve progresso episódico para o pai Série. O Tizen agora aplica esses contratos antes de paginar 40 cards, preserva o filtro, limita páginas obsoletas e mantém o progresso do episódio somente na cópia transitória usada pelo card. O boot ganhou uma segunda hidratação seletiva: se um episódio referenciado entrou na amostra inicial mas seu pai não, o pai é buscado por ID sem ler o catálogo inteiro. A suíte usa 230 favoritos, 76 históricos e um pai deliberadamente fora dos primeiros 120 itens.

30. A biblioteca de Downloads ainda materializava a fila inteira e preservava a ordem acidental do snapshot, diferente de Android e Windows, que elevam trabalho ativo e falhas. O Tizen agora aplica a mesma prioridade visual sem reescrever a ordem persistida, filtra e pesquisa localmente por título sem distinguir acentos e renderiza no máximo 40 transferências por página. Paginação explícita não é anulada por um foco antigo; quando uma callback real altera o estado e a posição da linha focada, somente então a tela acompanha sua nova página. A consulta permanece apenas em memória e o snapshot continua sem URL, caminho físico ou metadados privados de ordenação. A suíte cobre 95 entradas, três páginas e conclusão assíncrona.

31. O player Samsung anunciava `ENTER reproduzir/pausar`, mas só tratava as teclas multimídia; além disso, as ações Android de favoritar durante a reprodução e consultar a programação ao vivo desapareciam ao abrir o AVPlay. ENTER agora pausa e retoma de verdade. A tecla vermelha adiciona/remove o item atual em Minha BURO, atualiza apenas o overlay e não move foco para a árvore oculta atrás do vídeo. Em canal ao vivo, a tecla verde abre um menu D-pad com o programa atual e até 11 próximos já presentes no snapshot EPG; programas encerrados são descartados e nenhuma nova chamada ou credencial é necessária. Sem EPG, a capability não é anunciada; em VOD, verde conserva o atalho secundário de legendas.

32. A referência Android usa retrocesso de 10 segundos e avanço de 30, mas o Samsung anunciava e executava 30 segundos nos dois sentidos. O atalho esquerdo/rewind e o texto do overlay agora seguem `−10/+30`, sem restringir o adapter genérico usado por outras ações. O PlayerTopBar Android também mostra o programa atual e o próximo; o Tizen agora deriva ambos do snapshot EPG transitório, atualiza o resumo sempre que os controles reaparecem e usa `textContent`, de modo que uma virada de horário não exige rede nem permite markup do provedor. PT-BR, EN, DE, IT e ES possuem textos próprios.

33. Configurações no Samsung começava por cinco códigos técnicos de idioma e deixava versão, aviso legal e perfil ativo fora da hierarquia usada no Android. A tela agora abre com a versão consultada pela API do aplicativo Tizen (com fallback igual ao manifesto) e o aviso explícito de que o IPTV BURO não fornece conteúdo. Licença, metadados, proteção, legendas por capability e acessibilidade continuam funcionais. Os cinco idiomas aparecem em linhas verticais com nomes nativos, código secundário, indicação traduzida de uso e `aria-pressed`; o perfil ativo, inclusive foto/Kids, aparece em um card que abre o seletor pelo mesmo D-pad. Todos os novos textos existem em PT-BR, EN, DE, IT e ES.

34. O Android explica como obter uma chave TMDb gratuita, mas no Samsung os campos apareciam sem orientação. A configuração Tizen agora oferece o mesmo guia em quatro etapas: conta, menu API, pedido de chave Developer e cópia da credencial. Tanto a API Key v3 quanto o Read Access Token v4 são aceitos: a v3 segue somente no parâmetro `api_key`, enquanto o v4 é reconhecido pelos três segmentos e enviado somente como `Authorization: Bearer`, sem entrar na URL. Os diagramas são formas locais, sem screenshots ou recursos remotos, e ficam fora da árvore acessível. O único link externo é a constante HTTPS `https://www.themoviedb.org/signup`, que passa pela allowlist existente e pelo `ApplicationControl`; nenhum endereço vindo do usuário é aceito. RETURN restaura os dois campos e conserva eventual rascunho somente em memória, sem `localStorage`, IndexedDB ou texto da tela do guia. Todo o fluxo é navegável por D-pad e traduzido nos cinco idiomas.

35. No player Android, Programação permanece disponível para todo canal ao vivo e abre uma explicação quando a fonte não possui EPG. O Samsung escondia GREEN nesse caso e, pior, desviava a mesma tecla para o menu de legendas. Agora GREEN tem significado estável: em conteúdo ao vivo sempre abre o guia; em VOD conserva o atalho de legendas. Com EPG, o painel apresenta até 100 entradas do snapshot já carregado, incluindo programas encerrados atenuados, horário, título, descrição e marca semântica do programa atual, em vez de descartar o passado e limitar a 12 linhas. Sem EPG, abre um estado vazio traduzido que esclarece que não é falha do aplicativo ou da conexão e fecha por ENTER/RETURN sem interromper o AVPlay. Nenhuma chamada adicional ou credencial é criada durante a reprodução.

36. A referência Android mostra a velocidade da transferência e o Windows também estima o tempo restante, mas a linha Samsung informava somente a porcentagem. O adapter Tizen agora mede uma janela recente entre callbacks nativos, ignora intervalos inferiores a 500 ms e suaviza amostras posteriores para não fazer o valor oscilar a cada buffer. A primeira callback continua sem taxa — não existe intervalo medido — e o tempo restante só aparece quando total e velocidade são conhecidos. Pausa, retomada, rebobinamento da contagem e restauração do app começam com amostra limpa. Velocidade, relógio de amostragem e estimativa são estritamente transitórios: não entram em `localStorage`, fila restaurada, URL, caminho ou log. A linha navegável por D-pad apresenta `estado · porcentagem · taxa · duração`, usando unidade binária e separador decimal do idioma.

37. O Android ganhou lembretes — marcar um título, um trilho próprio na Home, uma página dedicada e uma notificação diária — e o Samsung não tinha nada equivalente. O Tizen agora porta tudo o que é honesto numa TV: a marca é feita no detalhe de filmes e séries pela identidade de conteúdo, sobrevive a nova importação e a reinício, é isolada por perfil e sai junto quando o perfil é excluído. O trilho fica logo depois de Continuar assistindo e é montado a partir dos próprios lembretes, não das linhas do catálogo — o sentido de um título que ainda vai sair é justamente não estar no catálogo, e filtrá-lo contra os itens já consumidos descartaria exatamente o que vale mostrar. Um lembrete sem item correspondente aparece com a contagem no lugar da ação; um item escondido pelo controle parental é descartado, para que um lembrete não vire porta lateral num perfil Kids. A política de datas é a mesma do Kotlin: horizonte de 30 dias, lançado hoje continua sendo anunciado depois, contagem por dias inteiros e não por meses, e a ordem desempata por título — alfabética como no Android, e não pela data de marcação, senão a TV e o celular mostrariam a mesma lista em ordens diferentes. A composição visual foi conferida contra as telas Compose e corrigida em cinco pontos após a primeira versão deste porte: o trilho da Home usa selo fixo com a data/estado na linha de baixo, em vez de embutir a contagem no selo; pressionar um card leva à página de Lembretes — antes o card sem item removia a marca, de modo que um toque acidental apagava o lembrete sem confirmação; a página é uma lista vertical com Remover por linha, e não a grade de pôsteres que pertence à Home; sem arte guardável a inicial do título ocupa o lugar, porque esse é o caso comum e um símbolo fixo deixaria todas as linhas idênticas; e os textos de estado passaram a usar as traduções já existentes no Android (`Disponível`, `Já lançado`, `Faltam N dias`) nos cinco idiomas. O espaçamento usa margem e não `gap`, que só existe a partir do Chromium 84 enquanto as TVs suportadas começam no Chrome 47. A regra de arte também acompanhou a correção do Android — o teste passou a ser pela credencial (userinfo, query string, caminhos `/live/`, `/movie/`, `/series/`) em vez de exigir host TMDb, porque a regra estrita deixava todo lembrete de uma lista Xtream comum sem imagem nenhuma sem haver credencial a proteger. **O que não foi portado, e por quê:** o interruptor de notificação diária e o seletor de horário. O `config.xml` declara `background-support="disable"`, então nada roda com o app fechado e uma TV desligada não avisa ninguém; um horário que não dispara seria uma promessa falsa. No lugar deles a TV mostra um aviso na abertura, uma vez por sessão, silencioso quando não há nada dentro do horizonte, e a página explica esse comportamento. O IndexedDB subiu para a versão 3 acrescentando somente o store `reminders`, sem migration de dados: quem vem da v2 ganha um store vazio e nenhum registro existente é lido ou reescrito.

38. O Android trocou as seis pílulas rotuladas do detalhe por `BuroActionBar`: Assistir conserva o botão com rótulo e as ações secundárias viram glifo com legenda curta. O Samsung ainda usava as pílulas, e o mesmo defeito existia aqui por outro caminho — o hero tem 1076 px úteis, as seis pílulas somam cerca de 1265 px, e `.action-row` não quebra linha nem rola, de modo que Trailer e Compartilhar ficavam fora do alcance; numa série, com `Baixar temporada` e `Baixar série`, sobrava ainda menos. O Tizen agora usa a mesma composição e a mesma ordem — Favoritar, Lembrete, Baixar, Trailer, Compartilhar —, com as proporções da TV, alvo maior pela distância de leitura e o mesmo indicador de foco do restante do app. A legenda fica fora da árvore acessível e o nome do controle vive no `aria-label`, senão o leitor anunciaria a mesma palavra duas vezes. Nada foi escondido atrás de um menu, pela mesma razão registrada no Android. Os controles de download continuam sendo pílulas, porque ali um mesmo lugar muda de significado conforme a fila e carrega porcentagem. O detalhe de canal ao vivo não mudou: tem apenas Assistir e Favoritar, que cabem sem disputa.

39. O Cast "receber de um computador" do Android **não foi portado**, e a razão é a mesma já registrada para o envio: receber exige abrir sockets TCP/UDP, o Web Runtime não os expõe e a extensão oficial precisa de WebAssembly com `emcc`, ausente no ambiente. Portar a tela produziria um botão sem destino real. A capability continua oculta.

40. O player Android possui bloqueio contra toques acidentais, enquanto o Samsung aceitava seek, pausa, teclas coloridas e STOP durante toda a reprodução. A adaptação para controle remoto usa o único comando universal sem sacrificar uma tecla curta: segurar ENTER por 0,8 s. O primeiro gesto esconde os controles e mostra um painel lateral com instrução traduzida; setas, play/pause, STOP e teclas coloridas ficam inertes. Outro ENTER longo desbloqueia sem pausar ou reiniciar o AVPlay. RETURN continua fechando o player mesmo bloqueado, para que uma falha de `keyup` ou de repetição do modelo nunca prenda o usuário. ENTER curto conserva exatamente o contrato anterior de pausar/retomar. Uma falha de AVPlay também remove o bloqueio antes de mostrar Retry/Voltar, evitando um diálogo de recuperação inacessível. A suíte cobre o tempo de pressão, bloqueio de D-pad/mídia, desbloqueio, recuperação de erro e os cinco idiomas; o gesto real ainda precisa ser confirmado no controle da TV física.

41. Descobrir no Samsung era uma segunda Home: cinco prateleiras estáticas, inclusive Ao Vivo, sem decisão ou personalização. Android e Windows compartilham outro produto — uma mão terminável de 15 cartas. O domínio ES5 agora porta os pesos exatos (favorito 2, histórico 1, teto 10), nota como desempate, duas vagas de surpresa, feedback imediato `+2/−1` limitado a `±6` e ordenação reproduzível. A tela mostra uma carta e apenas a próxima em profundidade, contador, Pular, Guardar, Detalhes e Nova rodada. Guardar passa pelo mesmo caminho de favoritos; ids julgados e gosto da rodada vivem apenas na memória. A integração restaura carta/foco depois dos detalhes e respeita fonte, Kids, categorias ocultas/protegidas, favoritos e progresso. Quinze vetores puros espelham o Kotlin e a suíte D-pad cobre o fluxo completo.

42. Android e Windows permitem marcar um lembrete diretamente no detalhe de “Onde assistir”, que é o único caminho normal para uma estreia ainda ausente da lista IPTV. O Samsung mostrava trailer e ofertas, porém dependia de uma linha local para criar o lembrete. O detalhe externo agora oferece um botão D-pad com estado semântico `aria-pressed`; ele cria a mesma identidade estável por tipo/nome/ano usada pelos títulos locais, portanto uma importação futura reconhece a marca em vez de duplicá-la. O registro conserva somente título, tipo, data e pôster público aprovado pelo filtro já existente; URL de oferta, stream, chave TMDb e credenciais não são persistidos. Um segundo clique remove a marca sem fechar a tela. Três regressões cobrem título realmente ausente do catálogo, persistência mínima e remoção.

43. As prateleiras de Assinaturas no Samsung terminavam no primeiro lote de 20, enquanto Android e Windows oferecem “Ver mais” e uma grade do catálogo amplo do serviço. O cliente ES5 agora replica o contrato sob demanda: percorre páginas de 20, deduplica por tipo/id, para na primeira vazia e impõe os limites de oito páginas e 100 títulos. A grade abre imediatamente com a prateleira já disponível, então a rede não a transforma em spinner vazio; falha conserva esses títulos com explicação visível. Detalhes retornam à mesma grade, RETURN volta às prateleiras e fechar aborta a chamada; uma resposta atrasada é descartada. “Em breve” não recebe o botão porque não possui provedor real. Testes cobrem paginação, cancelamento, loading, limite de DOM, falha e ida/volta por D-pad.

44. A página Lembretes do Samsung usava a coleção reduzida da Home e só oferecia Remover, enquanto Android resolve a identidade contra o catálogo ao tocar e Windows torna a entrada local acionável. A resolução Samsung já existente foi separada da apresentação: o trilho continua limitado a 12, mas a página lista todos os registros. Uma linha com filme/série local recebe uma ação de detalhes; RETURN restaura página e foco sem interpolar o id da fonte num seletor CSS. Uma linha futura ou órfã permanece visível, explica que ainda não está na biblioteca e conserva Remover, sem botão inerte. A checagem é repetida no momento da ação e continua sujeita a fonte ativa, perfil Kids e categorias ocultas/bloqueadas. Quatro regressões cobrem lista de 14 registros, correspondência única, detalhes reais e retorno D-pad.

45. Android e Windows já abriam um `TitleShareLink` recebido e procuravam a identidade estável na lista do destinatário; o Samsung apenas gerava o QR de saída. O `config.xml` agora registra somente `iptvburo://title` para a operação pública `view`, com `reload="disable"`, e o runtime cobre tanto o pedido frio consultado por `getRequestedAppControl()` quanto o evento quente `appcontrol`. O pedido espera legal/perfil/fonte, permanece apenas em memória e percorre todo o store `items` com acumulador O(1), em vez da amostra da Home. A correspondência reutiliza a normalização que remove ano e marcadores como `[4K]`/`DUAL`, exige fonte ativa e visibilidade parental e abre somente o item local; imagem, descrição e URL recebidas nunca viram stream ou consulta de rede. Se nada corresponder, um `alertdialog` persistente oferece Procurar novamente e Fechar por D-pad. As regressões cobrem inicialização fria/quente, outra fonte, categoria oculta, catálogo fora da memória, retry, descarte depois de resolver e URI hostil. Associação e entregas quente/fria passaram posteriormente no emulador; correspondência positiva ainda exige fonte autorizada/hardware.

46. Android e Windows conservam as prateleiras públicas de Assinaturas entre visitas, mas o Samsung repetia o diretório e uma consulta por serviço toda vez que a pessoa voltava à área. O Tizen agora grava por um dia até oito respostas, chaveadas por região, filtro e idioma. O formato é reconstruído por allowlist e limites antes de gravar e novamente ao ler: somente id TMDb, título, tipo, ano, data de lançamento, pôster `w342` e marca de serviço `w92` no host fixo entram no `localStorage`; chave, oferta, URL externa, sinopse e campos inesperados são descartados. Uma resposta vazia nunca apaga a válida. Mudança de dia, dimensão, versão ou formato vira miss silencioso, e salvar/remover a chave limpa o cache. A segunda visita foi exercitada sem nova chamada no teste integrado; os testes puros cobrem vazamento, corrupção e expiração.

47. Android e Windows separam gênero de serviço quando a playlist usa categorias como “Filmes | Netflix” ou “Séries | Prime Video”, mas o Samsung mostrava todos os nomes numa única fileira. O novo adapter ES5 porta a mesma lista de serviços, normalização de prefixos e regra de palavra inteira para `Max`; deduplica variantes como Netflix/Netflix 4K, alcança todas as categorias do serviço e mantém um seletor desabilitado com explicação quando a fonte não organiza por provedor. Os filtros de gênero e serviço se excluem para não produzir uma interseção impossível e conservam o estado ao ir/voltar da categoria. Trinta e quatro testes puros e a integração D-pad cobrem rótulos, marcas, cores, deduplicação, filtro e fallback.

48. As referências passaram a exibir a marca real de cada serviço nas prateleiras de Assinaturas; o Samsung ainda mantinha apenas texto. O cliente Tizen agora lê `logo_path` tanto do diretório quanto dos buckets de disponibilidade e constrói somente `https://image.tmdb.org/t/p/w92/...`. O nome nunca é substituído: a imagem fica ao lado dele, sobre cada card e na oferta, com fallback textual. O cache subiu para a versão 2 e aceita somente esse host/tamanho/caminho sem travessia; uma URL hostil é descartada antes do DOM e do armazenamento. As regressões cobrem transporte, cache, prateleira, grade ampla, cards, ofertas e ausência de preço inventado.

49. Android e Windows explicam como obter a chave OMDb em quatro passos, mas o Samsung parava na frase “Gratuita em omdbapi.com”. O painel da crítica agora oferece o mesmo fluxo local nos cinco idiomas: abre a página correta de emissão, distingue o plano `FREE`, explica o formulário por e-mail e destaca o link de ativação que precisa ser aberto antes do uso. Quatro diagramas locais evitam depender da interface mutável de terceiros. O rascunho digitado fica apenas na pilha de navegação em memória, nunca entra no texto do guia ou no `localStorage`, e RETURN restaura o campo. A saída usa `ApplicationControl` somente para o endpoint fixo `https://www.omdbapi.com/apikey.aspx`; esse host não foi adicionado à allowlist de ofertas de streaming. Seis regressões D-pad cobrem rota, conteúdo, rascunho, persistência, abertura externa e retorno.

50. Android e Windows distinguem visualmente as fontes das notas, enquanto o Samsung ainda desenhava três caixas praticamente iguais. O adapter OMDb agora publica uma única identidade visual consumida pela tela: `RT` em `#FA320A`, `IMDb` em `#F5C518` e `MC` nas faixas públicas do Metascore — verde a partir de 61, amarelo de 40 a 60 e vermelho abaixo de 40. São siglas e cores, não cópias de logotipos registrados. O nome e o valor continuam visíveis; cada célula é um grupo acessível com rótulo completo e o selo decorativo fica fora da árvore semântica. Quatro testes puros fixam marcas, faixas e fallback; duas regressões integradas cobrem DOM, cores e semântica.

51. O filtro global de gênero/serviço já ciclava por ENTER, mas zerava o índice de foco a cada escolha. Na TV isso obrigava a percorrer novamente a Ribbon e os chips para chegar ao próximo valor. A renderização agora reencontra o mesmo controle por sua identidade estável; somente “Limpar filtros”, que remove o próprio botão do DOM, volta de propósito ao primeiro chip. Duas regressões integradas percorrem o foco pelo D-pad e confirmam dois ENTER consecutivos sem salto.

52. O Windows encerra a Home com uma prateleira para cada serviço nomeado pela lista, mas o Samsung só usava essa identidade nos filtros e cards de categoria. A mesma varredura O(1) que compõe os trilhos editoriais agora acumula até 24 títulos por serviço, sem segunda passagem pelo IndexedDB. As prateleiras ficam depois das editoriais, carregam monograma/cor do serviço e participam da hidratação de arte. O conjunto global de ids/títulos consumidos impede que uma obra reapareça em dois trilhos. Dez regressões integradas cobrem buckets Netflix/HBO, exclusão de gênero, ordem, marca, hidratação e deduplicação.

53. Android e Windows deixam as quatro dimensões e cinco cores de legenda visíveis, enquanto o Samsung expunha apenas enums internos como `medium`/`white` e exigia ciclar um card. A TV agora mostra as nove escolhas simultaneamente, com os mesmos rótulos PT-BR/EN/DE/IT/ES, amostras reais, seleção visual e `aria-pressed`; escolher um destino pelo D-pad persiste diretamente o valor permitido. Fundo, alto contraste e movimento reduzido também deixaram de exibir `ON/OFF` cru. A renderização Chromium de produção em 1920×1080 mediu o painel entre `x=64` e `x=1817`, com 1753 px de largura e sem recorte. Quatro regressões integradas e as verificações de engine/alvo mínimo protegem o porte.

54. Android e Windows tratam a abertura do sino como leitura e oferecem uma ação para limpar a central inteira; o Samsung mantinha o marcador não lido depois que a pessoa já estava olhando a lista e exigia remover cada linha. A ação do sino agora marca e persiste a coleção antes de renderizar a central, portanto contador, classe visual e estado semântico somem juntos sem uma segunda decisão. Remover individual continua disponível e **Limpar tudo** usa o contrato puro `BuroNotifications.clear()`, conserva o estado vazio explicativo e é navegável por D-pad. Cinco novas regressões integradas cobrem leitura na abertura, persistência, ausência da ação redundante, remoção individual, limpeza total e rótulos nos cinco idiomas.

55. Android e Windows encerram a Home real com as prateleiras públicas de Assinaturas, abaixo do conteúdo autorizado do usuário; o Samsung só expunha o mesmo catálogo dentro do destino Assinaturas. A Home Tizen agora reutiliza o cache diário regional de `MOVIES` e, quando necessário, o carrega em segundo plano sem bloquear hero ou prateleiras locais. Falha externa permanece silenciosa na Home e não entra em ciclo de retry. Cada serviço ganha uma prateleira distinta depois do catálogo local, ids repetidos são deduplicados, pôsteres e marcas passam pelas allowlists existentes e os cards nunca recebem ação de reprodução direta. ENTER abre a ficha “onde assistir”; RETURN restaura a mesma Home e o foco exato do card. Sem chave TMDb a interface não promete essa capability. Treze regressões integradas cobrem ordem, loading não bloqueante, cache, saneamento, deduplicação, ação, detalhe e retorno.

56. Android e Windows deslocam suas `LazyRow` explicitamente conforme o foco; o Samsung confiava somente em `scrollIntoView({ inline: 'nearest' })`. Os primeiros Web Engines Tizen aceitam a função, mas não o objeto completo de opções, portanto um card podia receber foco fora do recorte de `overflow: hidden` e continuar invisível. O foco agora procura o contêiner horizontal mais próximo — Ribbon, `card-row`, `subscription-row` ou linha demo — e corrige `scrollLeft` pela caixa real do item, com margem de 12 px, depois do fallback nativo. A regra vale para catálogo local e externo e não muda a geometria direcional. Uma regressão simula `scrollIntoView` inerte, coloca o décimo segundo card além de 1920 px e confirma deslocamento da prateleira e restauração do mesmo foco após o detalhe.

57. Android e Windows preservam a lista e a posição da Home ao abrir e fechar detalhes; o Samsung apagava `screenData`, recompunha a Home por uma nova consulta IndexedDB e voltava ao primeiro foco. O shell agora captura somente em memória a referência do modelo local, `scrollTop`, `scrollLeft` da prateleira e o par ação/id do card antes de abrir filme, série ou canal. RETURN reutiliza o modelo original e restaura posição e foco exatos, inclusive depois de loading com falha/retry, trailer, QR, pessoa e crédito externo. Nenhum catálogo, URL ou segredo novo é persistido. Três regressões D-pad verificam identidade do modelo, rolagem vertical e foco de origem.

58. O detalhe de série do Windows oferece no topo uma ação que procura o primeiro episódio retomável e, se não houver, abre o primeiro episódio; o Samsung só permitia descer, expandir uma temporada e escolher uma linha. A TV agora deriva texto e posição da mesma `ResumeDecision`, mostra temporada/episódio nos cinco idiomas e inicia diretamente o alvo anunciado sem uma segunda pergunta de retomada. Episódio concluído não sequestra a escolha e série vazia não recebe botão inerte. Quatro regressões integradas cobrem preferência pelo retomável, seek entregue ao AVPlay, fallback para o primeiro e ausência da ação no vazio.

59. Android e Windows reagem ao checkpoint salvo e atualizam as telas quando o player fecha; o Samsung persistia o registro, removia o overlay e deixava o DOM antigo atrás dele. Assim, uma barra podia continuar em 37% após chegar a 50%, um episódio concluído não ganhava a marca e o botão da série não recalculava Continuar. RETURN e `onstreamcompleted` agora capturam a posição visual, persistem o progresso e fazem uma única recomposição do shell. O foco é restaurado por ação/id, `scrollTop` pelo contêiner e `scrollLeft` pelo índice estável da prateleira; TV ao vivo continua sem recomposição porque não grava checkpoint. Quatro regressões cobrem atualização parcial, conclusão, foco e as duas rolagens.

60. A continuidade da Home Samsung tratava progresso de episódio como um card reproduzível do episódio, enquanto Android e Windows projetam essa atividade na série e abrem seus detalhes. A composição agora resolve o pai já hidratado, cria somente uma cópia transitória com o id do episódio usado pela barra e deduplica pelo id da série. Como os registros são lidos por `updatedAt` decrescente, o episódio incompleto mais recente determina ordem e porcentagem; ao concluí-lo, o anterior reaparece e pode reordenar a série, e o card some quando não resta episódio incompleto. Pais ausentes ou inválidos não produzem ação enganosa. Quatro regressões integradas protegem identidade, porcentagem sem mutação, reordenação e remoção.

61. O runner do emulador considerava sucesso assim que `tizen run` devolvia um PID, portanto uma queda logo depois da abertura não era distinguida de uma sessão estável. O perfil TV 10 auditado anuncia `appid2pid_support: enabled`, mas bloqueia shell e log (`intershell_support: disabled`, `log_enable: disabled`). A verificação passou a usar o sinal que o launcher realmente oferece: depois de uma janela validada de 3–60 segundos, ativa o mesmo pacote novamente e exige o mesmo PID. No emulador real, a revisão atual devolveu `22882` nas duas consultas separadas por cinco segundos. Quatro regressões verificam parâmetro/limite, extração do PID, segunda consulta e falha quando a identidade muda; isso prova sobrevivência do processo, não substitui inspeção visual ou Web Inspector.

62. A inspeção visual em Chromium era uma medição manual, portanto podia deixar regressões de onboarding voltarem sem falhar a suíte. Um smoke CDP agora abre diretamente o `index.html` e o CSS de produção numa sessão limpa de Chrome/Edge em 1920×1080, sem servidor nem cópia dos assets. Ele percorre idioma, o carregamento cinematográfico real, aviso legal, criação do primeiro perfil, Home vazia e detalhe demonstrativo. Vinte e cinco verificações protegem viewport, overflow global, área segura, cinco alvos de idioma, foco inicial, progresso e quatro etapas do boot, sobreposição, runtime ready, chrome fixo e quadros PNG não vazios. Por padrão não grava captura nem dados do perfil; uma pasta explícita de artefatos pode receber os quatro PNGs para inspeção humana. Isso torna composição e fluxo repetíveis, mas continua sem equivaler ao Web Engine Tizen, framebuffer OpenGL, AVPlay ou TV física.

63. A Home demonstrativa Samsung tinha apenas três cards genéricos num único trilho; a referência Android possui 14 itens em três trilhos e abre o detalhe do item escolhido. O Tizen agora conserva os mesmos ids e a composição 4/4/6: continuidade e programação simulada mostram progressos fictícios, o editorial usa seis pôsteres, e todos têm título/subtítulo traduzidos. Cada card abre seu próprio detalhe local, que declara explicitamente não conter trailer, URL, stream, imagem remota ou playback. A inspeção da primeira implementação encontrou ainda um efeito colateral: `scrollIntoView` rolava o ancestral `.main-pane` com `overflow: hidden`, fazendo Ribbon/topbar sumirem. O foco do shell agora ajusta somente o `scrollTop` da `.content.scrollable`; o chrome fixo permanece no quadro. Dez regressões novas — cinco funcionais e cinco visuais — cobrem composição, traduções, detalhe/retorno, PNG, largura e rolagem confinada.

64. Assinaturas reutilizava o índice de foco e a posição visual da tela anterior ao abrir um título ou **Ver mais**. Em prateleiras roladas, o detalhe podia nascer no fim da página e esconder Ribbon, título e ações; no retorno, títulos iguais em provedores diferentes podiam apontar para o card errado. A navegação agora captura provedor, card, `scrollTop` do conteúdo e `scrollLeft` da linha, abre a nova rota no topo com Voltar focado e restaura exatamente a origem. Regressões D-pad cobrem detalhe, grade ampliada, duas prateleiras com o mesmo título e as duas rolagens.

65. O smoke visual de produção foi ampliado para as rotas profundas que ainda não tinham quadro verificável: edição de perfil, gerenciamento/escolha/formulários Xtream/M3U/Stalker, M3U e foto por USB, PIN parental, categorias, TMDb e OMDb com guias até o fim, armazenamento, downloads, retomada, confirmação em lote, licença, Assinaturas/Ver mais/detalhe, pessoa, compartilhamento, player VOD/ao vivo, bloqueio, menus de áudio/legenda/velocidade/guia, erro e trailer. São 158 verificações e 58 PNGs em 1920×1080; os dados continuam sintéticos, artes HTTPS são interceptadas localmente e o contrato de compartilhar é inspecionado para impedir campos privados. A prova continua sendo do HTML/CSS de produção em Chromium, não do framebuffer Tizen.

66. A inspeção das novas rotas encontrou três defeitos que os testes funcionais não revelavam. No formulário Stalker, `.form-optional` herdava o `display:flex` horizontal de `.section-heading`; o parágrafo longo começava ao lado de “Opcional” e atravessava sua linha. No guia do player, `currentPlayback.title` não existia e o rodapé imprimia `undefined`; depois, a regra visual de programa atual, declarada mais tarde, substituía o fundo marfim do foco e deixava texto escuro sobre fundo escuro. O bloco opcional agora é vertical, toda reprodução carrega o título público do item e o foco do guia tem prioridade explícita de fundo/borda. Geometria, cores calculadas, captura e D-pad protegem as três correções.

67. A cobertura profunda ainda exercitava quase toda a interface somente em PT-BR. Um contrato novo inspeciona antes do fallback as 583 chaves de cada um dos cinco idiomas, exige o mesmo conjunto, preserva parâmetros de interpolação, recusa vazio/mojibake e confirma todas as referências literais usadas pelos módulos. A matriz visual repete Configurações, o formulário Stalker rolado, retomada e erro do player em PT-BR, EN, DE, IT e ES. A inspeção humana dos novos quadros revelou que `Page.captureScreenshot` podia expandir a superfície rolável e recompor Ribbon/topbar em offsets que não correspondem ao viewport; a captura agora fixa `captureBeyondViewport:false`, enquanto o shell de produção usa limites absolutos e camadas opacas para conservar o chrome em 1920×1080. O smoke soma 218 verificações e 78 PNGs.

68. Os tetos LRU protegiam os caches de arte, mas ainda não havia prova de que a WebView suportava uma sessão longa de recomposições sem reter DOM, listeners ou documentos. O smoke de produção agora executa 960 trocas entre catálogo, biblioteca, downloads, perfis, fontes, configurações, pesquisa, formulário Stalker/M3U e retomada, alternando também os cinco idiomas e o foco por D-pad. O CDP força coleta antes e depois e mede DOM, `Nodes`, `JSEventListeners`, `Documents` e `JSHeapUsedSize`; a execução registrada terminou em 1.356 ms, voltou de 181 para 181 nós DOM, reteve zero nó/listener/documento e cresceu 296 KiB de heap coletado, sem exceção não tratada nem overlay residual. O smoke soma 229 verificações; o limite generoso de 30 s/8 MiB evita transformar variação da máquina em falso positivo.

69. O teste de escrita em lote protegia `putBatch`, mas a importação M3U/Stalker não usa esse método: `replaceSourceCatalogue` enfileirava todos os itens numa única rajada síncrona de `put()`. Cinco mil itens produziram 5.041 chamadas consecutivas antes de devolver o JavaScript; numa TV isso congela o D-pad mesmo que o commit IndexedDB posterior seja assíncrono. A substituição agora mantém uma única transação atômica, porém o último request de cada bloco de mil agenda o seguinte enquanto a transação permanece ativa. A regressão mede uma transação, snapshot completo e no máximo 1.050 writes por tarefa. No Chromium de produção, 12.000 títulos foram gravados em 889 ms com 80 ticks e intervalo máximo de 27 ms; a Home terminou em 182 ms com 101 itens editoriais em memória, a busca alcançou o fim do índice e uma categoria de mil títulos manteve 200 cards por página. O smoke soma 241 verificações e a suíte completa 1.274 em 42 grupos.

70. A paginação visual limitava a categoria a 200 cards, mas `openCategory` ainda executava `getAll` no índice e mantinha os 1.000/10.000 registros da categoria na memória. O IndexedDB subiu da v3 para a v4 com o índice composto `sourceId/categoryId/searchSort/id`; a migration cria somente o índice, preserva stores, locators e referências. A abertura busca 200 itens e a ação **Carregar mais** retoma exclusivamente da última chave, acrescenta outro bloco de 200, move para a página nova e conserva páginas anteriores acessíveis pelo D-pad. Filtros continuam incidindo sobre o conjunto carregado, igual ao repositório Android. A regressão v1→v4 percorre 200+200+50 sem repetição; no Chromium, uma categoria de 1.000 registros começou com 200 em memória, chegou aos cinco blocos sem duplicação e nunca excedeu 200 cards no DOM. O smoke soma 246 verificações e a suíte completa 1.288 em 42 grupos.

71. A abertura ainda revelava o shell antes de a Home estar realmente pronta e
depois mostrava “Montando sua Home”, divergindo do Windows. O boot agora prepara
o cache editorial ainda sob a cobertura, aguarda prateleiras públicas TMDb quando
há credencial válida, pré-carrega as capas visíveis e só então revela a Home.
Se houver pelo menos nove capas públicas, até 12 compõem o mosaico; antes disso,
um atlas local evita repetição artificial. A porcentagem deriva das seis etapas
reais e a varredura informa o avanço por categoria. Dez regressões bloqueantes
protegem a ordem e impedem persistência da chave; o smoke visual soma 258
verificações em 1920×1080.

72. Stalker/Ministra agora pagina também no servidor: páginas remotas de 200
itens são anexadas ao IndexedDB, o cursor local continua da última chave e uma
página remota inválida composta só por duplicatas encerra **Carregar mais** para
não criar laço infinito. O cenário sintético percorreu 200+200+50, totalizou 450
identidades únicas e nunca desenhou mais de 200 cards.

73. O Samsung já copiava para USB as capas que chegavam a ser renderizadas, mas
não percorria toda a arte pública descoberta nem mostrava o andamento do cache.
A varredura agora alimenta uma fila deduplicada, limitada a duas transferências,
com total, concluídas, falhas, porcentagem e ações Pausar/Continuar/Atualizar.
Callbacks antigos são invalidados ao desligar ou limpar; arte autenticada, token,
arquivo grande e id inseguro são recusados. Vinte e oito testes do módulo, 25 do
painel, 328 de D-pad e 258 verificações visuais passaram. A revisão
`20260824-142703` foi instalada e permaneceu no PID 10017 por 30 segundos; seu WGT
tem 708.629 bytes, SHA-256
`2E06C08F5C9BB87056D10C06C7738A495DEA5C4C4587D2834DA92D51A26E7A78`, duas
assinaturas e 37/37 payloads idênticos aos fontes. USB físico continua gate.

74. A comparação direta com a Home Windows mostrou que a TV ainda revelava um
Hero com texto genérico, embora capa e nota já estivessem disponíveis. O boot
agora aguarda o enriquecimento do primeiro destaque; Xtream mantém precedência
e o TMDb preenche somente lacunas ou funciona como fallback cancelável para
Xtream, M3U e Stalker. A fila continua serial, limitada e somente em memória. A
mesma inspeção encontrou um defeito independente no mosaico: o preload guardava
no máximo quatro artes, mas a abertura exigia nove para abandonar o atlas local.
O limite foi corrigido para 12. No emulador instalado, o TMDb real carregou
prateleiras BR, marcas, capas e um Hero com sinopse/backdrop/fatos; o OMDb real
carregou uma nota IMDb. Nenhum valor saiu do KeyManager. As regressões passaram
com 18/18 no enriquecimento, 12/12 no boot e 328/328 na integração D-pad.

75. Depois do primeiro quadro, uma capa remota ainda podia surgir de uma vez ou
mostrar o retângulo quebrado enquanto o Web Engine decodificava a resposta. O
Samsung agora mantém placeholder animado, revela apenas após `load` e conserva
o fallback backdrop→pôster no mesmo estado, em Home, catálogo, detalhes,
retomada, pessoas e Assinaturas. Movimento reduzido desliga shimmer e transição;
falha final remove somente a imagem, sem apagar texto ou ação. A regressão D-pad
simula loading, erro e fallback, a acessibilidade protege movimento reduzido e o
smoke 1920×1080 exige todas as capas do primeiro quadro resolvidas antes da
captura. Os 55 grupos passaram; D-pad soma 328, acessibilidade 15 e visual 258.
A revisão `20260824-142703` permaneceu no PID 10017 por 30 segundos. O WGT tem
708.629 bytes, SHA-256
`2E06C08F5C9BB87056D10C06C7738A495DEA5C4C4587D2834DA92D51A26E7A78`, duas
assinaturas e 37/37 payloads idênticos aos fontes.

76. A inspeção direta no emulador encontrou uma regressão criada pela nova linha
de ações do Hero: a pausa da rotação reconhecia apenas um botão filho direto do
Hero. Como `Assistir` e `Ver detalhes` agora vivem em `.home-hero-actions`, o
título podia girar durante o foco e ENTER abrir outro destaque. A detecção agora
percorre os ancestrais sem depender de `Element.closest`, preservando ES5. No
emulador, `The Voice Brasil` permaneceu com o mesmo título e foco por 12 segundos
e ENTER abriu o loading e o detalhe do próprio título. Os 55 grupos passaram em
uma única execução de 153,5 segundos; D-pad soma 333, acessibilidade 15 e
visual 259. A revisão `20260824-144336` permaneceu no PID 11463 por 30 segundos. O WGT
tem 709.030 bytes, SHA-256
`C076DF5A0CAC004DBD8F5D30D97F5273EF6B5838CAB0844CCF0FBC7B184FF878`, duas
assinaturas, 37/37 payloads idênticos aos fontes e nenhum segredo ou rastreamento
de teste.

77. A comparação lado a lado com o Windows mostrou que a Home Samsung ainda
obrigava filmes a abrir detalhes antes de reproduzir. O Hero agora usa o mesmo
contrato: filme/canal oferece `Assistir` direto e `Ver detalhes`; série conserva
somente detalhes até resolver o episódio. A ação direta reutiliza decisão de
retomada, resolução tardia da URL, AVPlay e retorno visual já exercitados pelos
cards, sem outro pipeline ou URL no DOM. A inspeção do WGT instalado revelou
também que título real em duas linhas mais sinopse TMDb empurrava as ações para
fora do painel; padding, título e sinopse foram limitados sem aumentar os 390 px
do Hero. A regressão visual usa deliberadamente um título longo e exige os dois
botões dentro do painel. Downloads deixou de flutuar na suíte ao aguardar o fim
real do bootstrap de perfis antes de montar a seção sintética; passou cinco vezes
seguidas. Os 55 grupos Samsung passaram; D-pad soma 333, acessibilidade 15 e o
smoke visual 260. A revisão final `20260824-145026` permaneceu no PID 12763 por 30
segundos. Seu WGT tem 709.064 bytes, SHA-256
`C86E4749DBCE745AB60975C8EDA40537B2F184144A79007DCFA293DEFE2DB571`, duas
assinaturas, zero caminho inseguro e 37/37 payloads idênticos aos fontes.
Uma ativação intermediária da revisão `20260824-144825` mudou do PID 11934 para
12317 durante a janela automática e foi reprovada pelo runner. O mesmo pacote
instalado permaneceu depois no PID 12317 por 30 segundos, e a compilação limpa e
independente `20260824-145026` confirmou a estabilidade no PID 12763. O evento
intermediário fica registrado como possível corrida de reinstalação/launcher a
ser observada novamente em hardware físico.

## Capacidades Android ainda não portadas

| Capacidade Android | Situação Samsung | Decisão correta |
|---|---|---|
| TMDb por perfil, elenco com fotos e filmografia de pessoa | **Implementado nesta etapa:** chave de perfil + fallback da casa no KeyManager, enriquecimento não bloqueante, fotos/personagens, biografia e filmografia com correspondência local | Validar endpoint, CORS, imagens, foco e desempenho em TV física; manter cache transitório limitado |
| “Onde assistir”/Assinaturas baseado em TMDb/JustWatch | **Implementado nesta etapa:** capability por chave válida, quatro filtros, região, prateleiras com cache diário público seguro, Ver mais/grade de até 100 títulos, detalhe, biblioteca local, trailer, lembrete para título externo, buckets e atribuição por oferta | Validar paginação/foco/scroll, cache e respostas reais, persistência do lembrete, links regionais, CORS, desempenho e retorno de `ApplicationControl` em TV física; continuar separado de billing |
| Trailer | Portado para referências fornecidas pela fonte | Não tentar abrir um aplicativo de terceiros por ID confidencial; usar embed HTML permitido e validar em TV |
| Compartilhar via share sheet Android | **Implementado nesta etapa como adaptação de TV:** botão em filmes/séries, tela por D-pad, QR local/link público e recepção fria/quente de `iptvburo://title`, resolvida somente contra a fonte local ativa. Associação, entregas quente/fria e aviso D-pad passaram no emulador | Validar leitura a distância, contraste, correspondência positiva e página pública em celulares/TV; repetir as entregas em hardware e publicar `assetlinks.json` com certificado Android de produção antes do release |
| Enviar para outro dispositivo (descoberta/cast Android) | O protocolo atual exige broadcast UDP na porta 45517 e TCP bruto; o Web Runtime oferece Fetch/WebSocket, mas não esses sockets diretamente. A extensão oficial Tizen Sockets exige WebAssembly/Worker e o toolkit `emcc`, ausente no ambiente auditado | Manter a capability oculta. Portar somente com a extensão oficial instalada, transporte compatível com o receptor atual e validação em rede doméstica/hardware; não simular botão sem destino real |
| Foto personalizada de perfil | **Implementado nesta etapa como adaptação de TV:** seletor de JPG/PNG/WebP em USB, D-pad, recorte/compressão local, persistência privada e remoção no editor | Validar `FileSystem`, decodificação WebP/JPEG/PNG, consumo de memória, retirada do USB e aparência na TV física |
| Download interno | O próprio Android TV oculta offline por capability | Samsung possui extensão USB separada e condicionada ao volume removível; cobre Xtream VOD/episódios e arquivos VOD diretos de M3U, mas não manifesta HLS como arquivo único; validar APIs no aparelho |
| Billing/loja | Específico de cada loja | Implementar somente no fluxo de release Samsung, separado do catálogo “onde assistir” |

A decisão de incorporar trailers segue o [FAQ de multimídia para Smart TV](https://developer.samsung.com/smarttv/develop/faq/multimedia.html), sem depender de um identificador confidencial de aplicativo de terceiro. Os modos de imagem e a taxonomia limitada dos erros de preparo seguem a [API oficial AVPlay](https://developer.samsung.com/smarttv/develop/api-references/samsung-product-api-references/avplay-api.html): `setDisplayMethod` documenta exatamente letterbox, full screen e auto aspect ratio; `prepareAsync` informa classes de erro, mas não promete o status HTTP bruto. Downloads seguem a [API oficial Download](https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/download-api.html) e o [guia Samsung de download](https://developer.samsung.com/smarttv/develop/guides/data-handling/downloading-data.html): a feature `http://tizen.org/feature/download` é consultada antes da UI e cada episódio cria sua própria operação assíncrona. Para ofertas externas, a [`Application API`](https://developer.samsung.com/smarttv/develop/api-references/tizen-web-device-api-references/application-api.html) documenta `ApplicationControl`/`launchAppControl`: o Samsung usa apenas a operação pública `view`, URL HTTPS saneada, allowlist de hosts e o privilégio mínimo `application.launch` no manifesto. O [guia oficial de Public Preview](https://developer.samsung.com/smarttv/develop/guides/smart-hub-preview/implementing-public-preview.html) documenta `reload="disable"`, consulta inicial por `getRequestedAppControl()` e o evento `appcontrol` para uma aplicação Web já aberta; a entrada de título usa esse mesmo ciclo de vida, restrito ao esquema privado no manifesto. A decisão sobre Cast é derivada das [especificações do Web Engine](https://developer.samsung.com/smarttv/develop/specifications/web-engine-specifications.html), que expõem Fetch/WebSocket, e da [Tizen Sockets Extension](https://developer.samsung.com/smarttv/develop/extension-libraries/webassembly/tizen-sockets-extension/tizen-sockets-extension.html), que documenta sockets TCP/UDP via WebAssembly e execução em Worker. O ambiente atual não possui `emcc` nem o toolkit da extensão, portanto o transporte não pode ser afirmado como implementado.

## Execução no emulador

O bloqueio anterior deixou de se reproduzir em 20 de agosto de 2026. Com a VM `T-samsung-10.0-x86_64`, o perfil `burocertificate-emulator` e uma staging limpa, `build-web`, assinatura, `install-permit`, instalação e abertura terminaram com sucesso. O runner também foi exercitado com a VM totalmente desligada e nenhum alvo SDB: abriu a VM, aguardou o boot, instalou e iniciou o app em 70,3 segundos com `-SkipTests`, sem resetar o disco. O fluxo visual passou por idioma, aviso legal, criação de perfil e Home; setas, ENTER e RETURN responderam pelo controle virtual. A suíte também protege a Ribbon estreita: percorre os destinos até Fontes/Configurações e exige a rolagem programática do item focado. Nesta etapa o shell ganhou ainda um marco de prontidão único, emitido somente depois de render, semântica, foco e bindings, com versão e sem dados do usuário. A revisão atual foi instalada no PID 13363: `workspace/tizen-emulator-20260820-183907/.buildResult/IPTVBURO.wgt` tem 34 entradas, 615.857 bytes, SHA-256 `88A7331755E548BF9FA3A8B7EBFACE7849973BC12A4A0450C7BECC453E8B0A8F`, nenhuma fixture de diagnóstico e 32 arquivos de payload idênticos aos fontes atuais por bytes. A suíte atual soma 893 verificações, incluindo detalhes em tela cheia, cache diário de Assinaturas, entrada compartilhada e credenciais TMDb v3/v4. As mesmas fontes Web foram percorridas em Chromium a 1920×1080; a inspeção encontrou o aviso da Home vazia cobrindo sino/selo e a medição posterior comprovou zero interseção após a correção. Isso prova a composição HTML/CSS estática, não o framebuffer OpenGL nativo. Como o modo normal da TV não encaminha o console inicial, a observação do marcador no Web Inspector ainda é pendência, não prova visual concluída. Um segundo WGT, estritamente de diagnóstico e com package id próprio, copiou os mesmos `js/player.js` e `js/keys.js` de produção e abriu o HLS público BipBop da Apple no AVPlay real da VM. Ele comprovou Play/Pause, retomada, seta direita/esquerda e `MediaFastForward`/`MediaRewind`; os callbacks mediram direita em 34.784 ms, esquerda em 26.128 ms, Fast Forward em 57.072 ms e Rewind em 50.045 ms, enquanto o log nativo registrou procura de I-frame e conclusão dos seeks. No PID 8149, o manifesto multifaixa enumerou 2 áudios e 8 legendas, a API aceitou a segunda faixa de cada tipo e o pipeline baixou e empurrou buffers WebVTT, sem erro de runtime nas 20.244 linhas associadas. O runner agora desinstala `BUROSMK001.PlaybackSmoke` no bloco `finally`; o ciclo real concluiu a remoção em 100% e a listagem posterior confirmou zero pacote de diagnóstico antes de abrir o app normal. A captura HMP permanece preta, coerente com a camada de vídeo separada do AVPlay, portanto isso prova decodificação, progresso, controles e seleção nativa de faixas, não imagem/som percebidos. USB, KeyManager, codecs variados e TV física continuam gates antes de release.

Atualização posterior da evidência de emulador: o pacote anterior citado acima foi substituído pela revisão `20260820-212817`, que inclui `js/providers.js`, filtros e prateleiras de serviço com foco estável, continuidade da Home projetando episódios na série pai, prateleiras públicas de Assinaturas no fim da Home, deslocamento horizontal explícito compatível com engines Tizen antigos, painel completo de legendas, central de avisos alinhada, marcas públicas TMDb em Assinaturas, guia OMDb em quatro etapas, selos RT/IMDb/MC, retorno dos detalhes locais à mesma Home, ação principal Assistir/Continuar nas séries, atualização visual imediata depois do AVPlay, smoke visual reproduzível e a Home demonstrativa 4/4/6 completa. Ela foi compilada, assinada e instalada em 100%; abriu no PID 22882 e o launcher confirmou o mesmo PID cinco segundos depois. `workspace/tizen-emulator-20260820-212817/.buildResult/IPTVBURO.wgt` possui 35 entradas, 645.546 bytes, SHA-256 `6EBF1882C4FD8070E89A6C4C1D0256D89588293136E724A62FE177D54B1948F0`, duas assinaturas, zero diagnóstico e 33/33 arquivos de payload idênticos às fontes por bytes. A suíte correspondente soma 1.029 verificações em 41 grupos, sem falhas.

Nova atualização de evidência: a revisão `20260820-214107` amplia o smoke das
fontes de produção para a Home real, categorias, catálogo com pôsteres, detalhes
de filme e série, lista de episódios e Configurações. O catálogo de teste é
inteiramente sintético, persiste pelos contratos reais de IndexedDB e recebe
artes HTTPS interceptadas localmente, sem playlist, segredo ou rede. As 12
capturas de 1920×1080 foram inspecionadas; 50 verificações visuais passaram.
A suíte completa soma 1.054 verificações em 41 grupos, sem falhas. O WGT foi
compilado, assinado e instalado em 100%; abriu no PID 23421 e conservou o mesmo
PID após cinco segundos. O pacote
`workspace/tizen-emulator-20260820-214107/.buildResult/IPTVBURO.wgt` possui 35
entradas, 645.546 bytes, SHA-256
`E340444E5831A4D854B3E1663A6341292C1ECB8415CEF203C8B6FF8D894926BD`, duas
assinaturas, nenhum diagnóstico e 33/33 arquivos de payload idênticos às fontes
por bytes.

Atualização atual: a revisão `20260820-215236` amplia a mesma inspeção para
categorias e detalhes de Ao Vivo, Descobrir, Pesquisa, Minha BURO, Continuar,
Histórico, Lembretes, Notificações, Fontes e Perfis. São 78 verificações visuais
e 23 capturas de 1920×1080, todas com catálogo sintético e artes HTTPS
interceptadas localmente. A inspeção revelou que o texto da próxima carta do
Descobrir atravessava visualmente a carta corrente; o card de profundidade agora
conserva arte e volume, mas oculta copy e semântica (`aria-hidden`) até chegar à
frente. A suíte completa soma 1.083 verificações em 41 grupos, sem falhas. O WGT
foi compilado, assinado e instalado em 100%; abriu no PID 23933 e conservou o
mesmo PID após cinco segundos. O pacote
`workspace/tizen-emulator-20260820-215236/.buildResult/IPTVBURO.wgt` possui 35
entradas, 645.723 bytes, SHA-256
`6B63847899D9F40CE37113BA46B89A56310DA254B8F361B073773ECFD82424D3`, duas
assinaturas, nenhum diagnóstico e 33/33 arquivos de payload idênticos às fontes
por bytes. A prova visual continua sendo do HTML/CSS de produção em Chromium,
não do framebuffer nativo do emulador nem de uma TV física.

Atualização atual da evidência: a revisão `20260820-221048` corrige a abertura e
o retorno de títulos e grades ampliadas de Assinaturas, preservando o provedor,
o card e as rolagens exatas, e amplia a inspeção das rotas profundas para 123
verificações e 42 capturas de 1920×1080. A suíte completa soma 1.132
verificações em 41 grupos, sem falhas. O WGT foi compilado, assinado e instalado
em 100%; abriu no PID 24457 e conservou o mesmo PID após cinco segundos. O
pacote `workspace/tizen-emulator-20260820-221048/.buildResult/IPTVBURO.wgt`
possui 35 entradas, 646.440 bytes, SHA-256
`8B39A4F6682D284FEF8144D1AEB457F2DAF6CD5A3B011928E8BFE0C530A12CC9`, duas
assinaturas, nenhum diagnóstico e 33/33 arquivos de payload idênticos às fontes
por bytes. Framebuffer nativo, imagem/som percebidos e TV física continuam gates.

Nova atualização da evidência: a revisão `20260820-222557` inclui as três
correções visuais de Stalker e do guia do player, além da inspeção dos estados
restantes de fonte, confirmação, licença, player e trailer. A suíte completa
soma 1.168 verificações em 41 grupos, sem falhas; o smoke visual soma 158
verificações e 58 capturas de 1920×1080. O WGT foi compilado, assinado e
instalado em 100%; abriu no PID 25033 e conservou o mesmo PID após cinco
segundos. `workspace/tizen-emulator-20260820-222557/.buildResult/IPTVBURO.wgt`
possui 35 entradas, 646.484 bytes, SHA-256
`1C1B652D3B621AE41FB807E9C02066DC2F9DD7A8DCDBB129E0BEF9E82D4395B0`, duas
assinaturas, nenhum diagnóstico e 33/33 arquivos de payload idênticos às fontes
por bytes. Framebuffer nativo, imagem/som percebidos e TV física continuam gates.

Atualização da evidência de escala: a revisão `20260820-233011` acrescenta o
contrato das 585 mensagens em cinco idiomas, a matriz visual de quatro superfícies
por idioma e o enquadramento estrito do shell/captura em 1920×1080. A suíte
completa soma 1.288 verificações em 42 grupos, sem falhas; o smoke visual soma
246 verificações e 78 capturas, incluindo 960 recomposições, métricas de retenção
e catálogo sintético de 12.000 itens. O WGT foi compilado, assinado e instalado
em 100%; abriu no PID 27134 e conservou
o mesmo PID após 30 segundos.
`workspace/tizen-emulator-20260820-233011/.buildResult/IPTVBURO.wgt` possui 35
entradas, 648.682 bytes, SHA-256
`14B4BA239AE7A44727DA7FE1041321EF0649FE637DF7B08B4D4EF513D4C32413`, duas
assinaturas, nenhum diagnóstico e 33/33 arquivos de payload idênticos às fontes
por bytes. Framebuffer nativo, imagem/som percebidos e TV física continuam gates.

Atualização de 24 de agosto de 2026: a revisão com abertura bloqueante, Hero
enriquecido, cache de arte e paginação Stalker foi compilada, assinada,
instalada e aberta no emulador. O PID 12763 permaneceu estável por 30 segundos. O
pacote `workspace/tizen-emulator-20260824-145026/.buildResult/IPTVBURO.wgt`
possui 39 entradas, 709.064 bytes e SHA-256
`C86E4749DBCE745AB60975C8EDA40537B2F184144A79007DCFA293DEFE2DB571`; duas
entradas são assinaturas, as 37 restantes coincidem por hash com os fontes e não
há teste, diagnóstico ou credencial. Os 55 grupos Samsung posteriores ao gate
de versão passaram; `version-parity.test.js` permanece vermelho apenas pela
divergência Android 3.0.1/Windows 3.2.1. Após a reinstalação, a tela real do
emulador mostrou TMDb e OMDb configurados. O TMDb real carregou prateleiras BR,
marcas, capas e Hero enriquecido; o OMDb passou por validação de rede e exibiu
uma nota IMDb real. Nenhum valor foi embutido, registrado em log ou persistido
fora do cofre seguro.

78. A inspeção lado a lado mostrou uma divergência funcional real no topo:
Windows exibia a licença vigente, enquanto Samsung permanecia em `Dispositivo
não registrado`. O contrato do Worker aceita nonce Base64URL de 22 caracteres e
prova ECDSA P-256 Base64URL de 86 caracteres, ambos sem padding; a identidade
Tizen enviava Base64 tradicional. `identity.js` agora converte apenas nonce e
prova para Base64URL, preservando SPKI/PKCS8 em Base64 padrão. Regressões validam
formato, tamanho, assinatura, primeiro boot e ausência de chave privada no
pedido. No pacote instalado, a janela real passou a `Faltam 7 dias`, confirmando
registro e concessão assinada. A revisão final
`workspace/tizen-emulator-20260824-151127/.buildResult/IPTVBURO.wgt` abriu no
PID 13778, conservou-o por 30 segundos e possui 712.069 bytes, SHA-256
`2FCD9468E755985625ABFB8125031CE809C132B57623EAA342081EC221F20A9F`, 39
entradas, 37/37 payloads idênticos aos fontes e zero entrada insegura. Os 55
grupos posteriores ao gate de versão passaram no snapshot estável; visual soma
263/263 e D-pad 340/340.

79. O novo trilho de títulos semelhantes ainda sumia quando o TMDb não
respondia, apesar de o catálogo autorizado já conter filmes/séries do mesmo
gênero. A ficha agora converte até 12 candidatos locais para o mesmo card
navegável usado pelo TMDb, abre a ficha real sem nova busca global e conserva
uma pilha de retorno com rolagens e foco. A auditoria encontrou e corrigiu um
limite ausente: candidatos de outra fonte podiam aparecer e só eram recusados
depois do ENTER. O ranking agora exige a mesma `sourceId`, além de tipo,
categoria visível/PIN e gênero comum. Regressões cobrem fallback, abertura,
RETURN encadeado, isolamento entre fontes e captura 1920×1080. Os 55 grupos
passaram no snapshot estável; visual soma 266/266 e D-pad 343/343. O WGT
`workspace/tizen-emulator-20260824-153120/.buildResult/IPTVBURO.wgt` abriu no
PID 15253, conservou-o por 30 segundos e possui 712.364 bytes, SHA-256
`BF5F9C489B829CFF0ACA0FB20C0405A3C71D4A228043D727B0BE2312B94763CC`, 39
entradas, 37/37 payloads idênticos aos fontes e zero entrada insegura.

80. A comparação com Android/Windows mostrou que o fallback local não cobria a
paridade completa: faltavam a franquia TMDb, as recomendações públicas e a
transição segura para um título ausente da biblioteca. O cliente Samsung agora
ordena filmes por coleção/franquia e lançamento, acrescenta `recommendations` e
consulta `/similar` somente quando a recomendação falha ou vem vazia; séries
ignoram coleção. O merge exclui o título atual, deduplica e limita a 16. O card
procura primeiro toda a fonte local ativa; se não encontrar, abre
Assinaturas/Onde assistir. Uma pilha em memória restaura ficha, foco, scroll
vertical e fileira horizontal em retornos simples ou encadeados. Nota TMDb e
nota editorial da fonte permanecem campos distintos. No emulador real, a ficha
“The Voice Brasil” exibiu a fileira com capas, “Duelo com Bobby Flay” abriu
Assinaturas com ofertas atribuídas e **Voltar** recompôs a mesma ficha e focou o
mesmo card. Uma tentativa intermediária coincidiu com `dispositivo USB
removido` e recarregou o shell; a repetição foi estável. `tmdb.test.js` passou
42/42, D-pad 343/343, visual 266/266 e os 55 grupos pós-gate passaram em 159,1
segundos. O WGT `20260824-153120` já contém esse fluxo junto das correções de
identidade e isolamento por fonte: 712.364 bytes, 39 entradas, 37/37 payloads,
zero marcador inseguro e SHA-256
`BF5F9C489B829CFF0ACA0FB20C0405A3C71D4A228043D727B0BE2312B94763CC`.

81. A comparação da entrada de Filmes/Séries encontrou uma diferença visível
restante: Android mostrava uma fileira de marcas do diretório TMDb antes dos
filtros e abria o catálogo integral do serviço, enquanto Samsung escondia a
descoberta no seletor de Serviço. O Samsung agora consulta apenas o endpoint de
provedores correspondente a filme ou série, limita a 12, conserva id/nome/logo
públicos somente em memória e desenha alvos D-pad de 60 px antes das barras. O
ENTER abre a grade existente de até 100 títulos com loading explícito, sem
obrigar uma visita anterior a Assinaturas; RETURN abre as prateleiras do mesmo
tipo. A consulta leve não dispara `discover` para desenhar marcas e respostas
atrasadas são isoladas por perfil, região, idioma e tipo. Compatibilidade ES5
passou 38/38, TMDb 44/44, D-pad 349/349, visual 267/267 e os 55 grupos pós-gate
passaram em 156,7 segundos. No emulador real, Filmes mostrou serviços regionais,
Netflix abriu `Tudo em Netflix` com 100 capas e RETURN recompôs Assinaturas no
filtro Filmes. O WGT
`workspace/tizen-emulator-20260824-182522/.buildResult/IPTVBURO.wgt` manteve o
PID 3969 por 30 segundos, possui 714.718 bytes, SHA-256
`F4E45B1A1753BB650384211C9DF7E0C1BBB72AF7846C32BE4B0E6260353882CC`, 39
entradas, duas assinaturas, 37/37 payloads idênticos e zero chave de metadados
embutida.

82. A comparação seguinte encontrou uma diferença mensurável no cache opcional
de capas: Android/Windows mostravam a velocidade do preenchimento, enquanto a
Samsung mostrava somente quantidade e porcentagem. `artwork-cache.js` agora
agrega apenas os deltas de bytes dos callbacks `onprogress`, publica uma janela
de taxa no máximo duas vezes por segundo e descarta o valor ao pausar, retomar,
concluir, desligar ou limpar. A UI reutiliza as unidades já usadas em Downloads
e não persiste URL ou telemetria. Durante a matriz visual também foram fechados
dois defeitos de composição: títulos sem arte passam a reservar o placeholder
2:3, e uma fileira longa de semelhantes não expande o bloco pai além de 1920
px. O teste de rolagem agora espera a recomposição do DOM antes de medir, sem
afrouxar o critério de viewport. Cache passou 32/32, armazenamento 27/27,
compatibilidade antiga 38/38, D-pad 349/349, TMDb 44/44, visual 269/269 e os 55
grupos pós-gate passaram em 145,5 segundos. O WGT
`workspace/tizen-emulator-20260824-184638/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 5187 por 30 segundos. Possui 716.077 bytes,
SHA-256 `3BBD2EC7683EE1F5528D9C6D697A35AA77F103ADC90C4F7CDBBDD3631008AC5C`,
39 entradas, duas assinaturas, 37/37 payloads idênticos e zero padrão de chave
TMDb/OMDb embutida. A captura real confirmou a Home já revelada com Hero e
capas do primeiro quadro prontas.

83. O catálogo Xtream da Samsung ignorava `tv_archive` e
`tv_archive_duration`: o guia mostrava programas passados, mas ENTER apenas
fechava o painel. A TV agora preserva a quantidade válida de dias no locator do
canal e transforma somente programas encerrados, ainda dentro da janela e com
horário local/duração válidos em `Assistir gravação`. A ação existe tanto na
ficha do canal quanto no guia sobre o player e abre o endpoint Xtream
`timeshift` somente no instante do play. Usuário, senha e URL não entram no
catálogo, progresso ou armazenamento comum; gravações indisponíveis permanecem
linhas informativas. O fluxo usa timeline, seek e velocidade de VOD durante a
sessão, e o fim natural agora limpa também sessões que não persistem progresso.
Compatibilidade antiga passou 38/38, D-pad 353/353, idiomas 21/21, visual
270/270 e os 55 grupos pós-gate passaram em 151,5 segundos. O WGT
`workspace/tizen-emulator-20260824-190924/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 6674 por 30 segundos. Possui 718.439 bytes,
SHA-256 `7FAF3532D4889E2B658FF3008C775D1CFB0E07124CBA9B2B9B6849677E73B82C`,
39 entradas, duas assinaturas, 37/37 payloads idênticos e zero literal com
forma de segredo embutido. A retomada persistente de um catch-up ainda não é
exposta: por segurança, o alvo permanece transitório nesta primeira vertical.

84. A comparação com o Windows encontrou uma diferença funcional na biblioteca:
a Samsung exibia Continuar assistindo e Histórico, mas não permitia remover uma
entrada, pesquisar ou limpar o histórico. A TV agora preserva as mesmas
semânticas do desktop: Remover em Continuar marca a linha concluída e a mantém
no Histórico; Remover no Histórico apaga somente aquela linha; Limpar histórico
exige confirmação e remove todas as linhas do perfil ativo. Busca e comparação
ignoram acentos, ano e decoração conhecida de qualidade/idioma. A auditoria de
segurança confirmou guarda pelo perfil ativo e preservação de itens, favoritos,
downloads e outros perfis. A limpeza usa um cursor `byProfile` numa única
transação, sem N operações que poderiam travar a TV. Biblioteca passou 23/23,
armazenamento 12/12, compatibilidade antiga 38/38, idiomas 21/21, D-pad 358/358,
visual 274/274 e os 55 grupos pós-gate passaram em 146,7 segundos. O gate de
versão permanece 4/5 pela divergência Android/Samsung 3.0.1 e Windows 3.3.0. O
WGT `workspace/tizen-emulator-20260824-192911/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 7219 por 30 segundos. Possui 720.662 bytes,
SHA-256 `2416D7417DEF28BCB0E80D918735033631ED07045F797CD9DB8C3DC0C690A826`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero literal com forma de chave embutido.

85. A segunda comparação da continuidade encontrou outra diferença em relação
ao Windows: o card Samsung abria os detalhes e só permitia Remover, enquanto o
desktop oferece também Continuar e Assistir do início diretamente na linha. As
duas decisões foram portadas sem duplicar o diálogo do player. O identificador
da ação é o da linha de progresso e não o do card projetado; assim, uma série
mantém a capa do pai, mas reproduz o episódio real, na posição salva ou em zero.
Antes do AVPlay, o app exige a tela Continuar, revalida o perfil ativo, recusa
linha concluída/forjada e confirma item e fonte reproduzíveis. A URL Xtream só é
resolvida pelo pipeline existente no instante do play e não aparece no DOM nem
no `localStorage`. Biblioteca passou 29/29, D-pad 361/361, compatibilidade
antiga 38/38, idiomas 21/21, visual multilíngue 284/284 e os 55 grupos pós-gate
passaram em 145,8 segundos. O gate de versão segue 4/5 pela divergência
Android/Samsung 3.0.1 e Windows 3.3.0. O WGT
`workspace/tizen-emulator-20260824-194551/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 7762 por 30 segundos. Possui 721.248 bytes,
SHA-256 `C02FB322897616F813D132A5BAC84D4DA2291247EC81B333A8D6F2D18B24BB9D`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero literal com forma de chave embutido.

86. A inspeção simultânea das janelas instaladas mostrou uma diferença visual
que o contrato anterior não media: embora a Samsung já esperasse catálogo,
TMDb, Hero e capas antes de revelar a Home, o destaque media só 390 px e parecia
baixo ao lado do Windows. O desktop calcula 52% da altura disponível, limitado a
42–58% pelo GDD; a TV agora usa 510 px, equivalentes a 51,6% da área útil em
1920×1080. O texto aceita três linhas de sinopse, metadados ganham o acento
dourado da referência e um segundo degradê ancora a arte na primeira prateleira.
As ações, título longo, primeiro trilho, foco e scroll inicial continuam dentro
do viewport. Boot passou 14/14 + 12/12, compatibilidade antiga 38/38, D-pad
361/361, idiomas 21/21, visual 286/286 e os 55 grupos pós-gate passaram em 157,6
segundos. O WGT
`workspace/tizen-emulator-20260824-195253/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 8268 por 30 segundos. Possui 721.287 bytes,
SHA-256 `5577C5156B6AD4CEBDB0709472B1877EBF327A781F027AC6ECC7E44D6A8EF9A2`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero literal com forma de chave embutido. A janela do emulador foi recapturada
depois da instalação e confirmou visualmente o Hero ampliado com primeiro trilho
ainda acessível.

87. A comparação seguinte abriu fichas reais no Windows e no Samsung. A
estrutura já correspondia — backdrop, pôster, fatos, avaliações, progresso,
ação principal, ações secundárias, créditos e elenco —, mas a sinopse Tizen
continuava pequena. A regra documentada de 24 px usava `.detail-hero > p`; o
markup real coloca o parágrafo dentro de `.detail-hero-copy`, portanto a regra
nunca casava e o Chromium aplicava o tamanho padrão. O seletor agora segue a
árvore real, com 24 px, entrelinha 1,45 e máximo de 1.040 px. Um teste de estilo
computado impede que a ficha pareça correta apenas por conter texto, e a captura
real após instalar confirmou sinopse, nota, progresso, `Assistir`, barra de
ações, créditos e elenco no primeiro quadro. Compatibilidade antiga passou
38/38, idiomas 21/21, D-pad 361/361, visual 287/287 e os 55 grupos pós-gate
passaram em 169,6 segundos. O WGT
`workspace/tizen-emulator-20260824-200843/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 9318 por 30 segundos. Possui 721.285 bytes,
SHA-256 `26F94865E593191E09F64AC31774C744BD16664B75FEFF07B7E896DF0F021962`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero literal com forma de chave embutido.

88. A comparação real seguinte abriu Filmes no Windows e na Samsung. A TV já
abria a prateleira direta com capas, filtros e diretório de serviços, mas faltava
o campo `Buscar neste catálogo…` do desktop; procurar exigia mudar para a seção
Pesquisa e perder o contexto de tipo/filtros. O novo campo compartilha o mesmo
predicado paginado da prateleira, normaliza acentos e combina consulta, fonte,
tipo, visibilidade, ano, nota, gênero e serviço. Um debounce de 300 ms evita
percorrer o IndexedDB a cada tecla. A inspeção do pacote instalado ainda revelou
que uma consulta vazia recaía nos cartões de categoria; essa ramificação foi
corrigida para manter o catálogo e oferecer `Limpar filtros`, e ganhou regressão
própria. D-pad passou 364/364, visual 288/288, idiomas 21/21, compatibilidade
antiga 38/38 e os 55 grupos pós-gate passaram em 158,9 segundos. O WGT
`workspace/tizen-emulator-20260824-202626/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 10312 por 30 segundos. Possui 722.213 bytes,
SHA-256 `BE0185710DCC939F832F51620DBE7379E9E70740E92549907EB7770C2180DCF1`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero literal com forma de chave embutido.

89. A busca contextual aproximou o comportamento, mas a composição ainda não
igualava o catálogo Windows: no desktop, `Ao vivo / Filmes / Séries` ficam na
mesma faixa da busca; na Samsung, a troca dependia apenas da Ribbon lateral. A
TV agora desenha exatamente três tabs antes do campo, expõe `tablist`/`tab` e
mantém uma única seleção. Cada ativação reaproveita `data-action="section"`,
sincronizando a aba e a Ribbon sem criar uma segunda máquina de navegação.
Atalhos de serviço, filtros e primeiro bloco de capas continuam visíveis em
1920×1080. D-pad passou 366/366, visual 288/288, idiomas 21/21,
compatibilidade antiga 38/38 e os 55 grupos pós-gate passaram em 156,5 segundos.
O WGT `workspace/tizen-emulator-20260824-203834/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 10851 por 30 segundos. Possui 722.728 bytes,
SHA-256 `D945B270EAC8870F4F4B7BE1150F2C50D650628F8608B5B7A606753323865EB1`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero credencial literal embutida.

90. A entrega real de um `TitleShareLink` deixou de ser apenas um contrato
simulado. Um pacote Tizen isolado, com id próprio e somente os privilégios
`internet`/`application.launch`, encontrou o filtro `iptvburo` e entregou uma
identidade pública sintética ao app de produção já aberto. O callback nativo
retornou `APPCONTROL_SMOKE_PASS`; a janela real exibiu o aviso de título ausente,
o D-pad alcançou `Fechar aviso` e ENTER o removeu. A observação revelou então um
defeito que os mocks não cobriam: `getRequestedAppControl()` conserva o último
pedido e, depois que a deduplicação de cinco segundos expirava, foco ou render
faziam o aviso fechado reaparecer. O consumidor agora considera a mesma URI o
mesmo pedido enquanto ele permanece pendente e só resolve novamente por retry
explícito ou URI nova. Após fechar, trocar o foco por sete segundos e voltar, o
aviso permaneceu fechado. O remetente temporário foi desinstalado em 100%.
Link recebido passou 12/12, remetente 9/9, D-pad 366/366, visual 288/288,
compatibilidade antiga 38/38, idiomas 21/21 e os 55 grupos pós-gate passaram em
154,7 segundos. O WGT
`workspace/tizen-emulator-20260824-205359/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve o PID 11855 por 30 segundos. Possui 723.104 bytes,
SHA-256 `2A29565FBF5E5D55A417BF2CCA1B528F2FCC80FF0ED0B959474C1F9C3540C6A7`,
39 entradas, duas assinaturas, 37/37 payloads idênticos, nenhum diagnóstico e
zero credencial literal embutida.

91. A abertura fria também foi retirada da lista de suposições do emulador. O
runner ganhou um modo opcional que instala o remetente, envia a `KEY_POWER=116`
declarada pela própria skin Samsung, exige SDB offline e encerramento do único
processo Tizen sob o SDK, relança a mesma VM e aguarda o boot antes de disparar o
App Control. O caminho tentado inicialmente por fechamento da janela exibiu um
aviso de force stop; ele foi cancelado, reprovado e removido do código. O runner
atual proíbe `CloseMainWindow`, `Stop-Process`, `Kill`, `reset` e `delete`.
Na revisão `20260824-210854`, o emulador voltou com novo id de janela e o mesmo
serial; sem abrir manualmente o consumidor, o primeiro quadro do IPTV BURO já
mostrou o aviso da identidade pública. D-pad fechou o painel, e perfil, fonte,
catálogo e KeyManager sobreviveram ao Power. O PID de produção 3690 permaneceu
igual após 30 segundos; o remetente foi desinstalado. Runner passou 10/10,
consumidor 12/12 e os 55 grupos pós-gate passaram em 157,6 segundos. O WGT de
produção continua a revisão assinada `20260824-205359`, sem alteração de payload.

92. A correspondência positiva deixou de ser pendência do emulador. O runner
aceita uma URI pública configurável somente depois de validar esquema, host,
identidade, título, ano, duplicidade e lista fechada de parâmetros. O valor é
escrito apenas no staging e omitido do log. Um título existente na fonte
autorizada abriu sua ficha correta no fluxo quente (`20260824-220349`) e depois
de Power/boot completo (`20260824-220437`); ambos retornaram
`APPCONTROL_LAUNCH_PASS`, e o remetente foi removido. O player Samsung também
ganhou uma tentativa automática única após 1,5 segundo somente para falha de
conexão; a segunda falha mostra Retry/Voltar, e fonte/formato não são repetidos.
O timer é cancelado ao sair e a recuperação remove o bloqueio de controles.
TDD registrou RED por módulo ausente e RED por ausência da segunda preparação;
depois da integração, recuperação passou 8/8, integração 17/17, D-pad 366/366,
visual 288/288 e os 56 grupos pós-gate passaram em 138,7 segundos. O WGT
`workspace/tizen-emulator-20260824-220051/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve PID 4565 por 30 segundos. São 724.157 bytes, 40
entradas, duas assinaturas, 38/38 payloads exatos, nenhum arquivo inseguro,
zero literal de credencial e SHA-256
`543E1E0C6AE3805249C040B44C4CF9EC38AC8551D1BDAC3A4F5A8FE0E0AEACE0`.

93. Configurações ainda tinha uma diferença pequena, mas visível, em relação ao
Windows: o cabeçalho Samsung era fixo em 24 horas, enquanto o desktop permite
escolher 24 ou 12 horas e persiste a decisão. A Samsung agora conserva 24 horas
como padrão e oferece dois alvos D-pad com seleção visual e `aria-pressed`. O
modo de 12 horas segue exatamente o padrão desktop `h:mm AM/PM`; a mudança
recompõe o cabeçalho imediatamente e sobrevive a uma nova abertura. Os quatro
textos necessários existem em PT-BR, EN, DE, IT e ES. O teste comportamental
falhou primeiro porque os controles não existiam e passou 10/10 após a mudança.
Idiomas passaram 21/21, armazenamento 27/27, D-pad 366/366, visual 288/288 e os
57 grupos pós-gate passaram em 140,8 segundos. O WGT
`workspace/tizen-emulator-20260824-224036/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve PID 3983 após 30 segundos. Possui 724.763 bytes,
40 entradas, duas assinaturas, 38/38 payloads exatos, nenhum arquivo inseguro,
zero padrão literal de credencial e SHA-256
`00A7E0C6634419932C05E91C86B2CF6E0048A713CAF7426C79DCDDA4D1AEB194`.

94. O Windows já oferecia “mostrar um só card por filme”, mas o Samsung ainda
desenhava todas as cópias de qualidade/dublagem na categoria e na prateleira
completa. A Samsung agora liga essa visualização limpa por padrão e oferece um
switch D-pad persistente e acessível para recuperar a lista bruta. A identidade
replica a normalização de prateleira do domínio compartilhado para qualidade,
codec, idioma e rótulo de serviço, acrescentando tipo e ano estruturado para não
esconder sequência, subtítulo real ou remake homônimo. O IndexedDB não é
alterado; contagem e página criam predicados independentes para não compartilhar
o conjunto de chaves consumidas. TDD registrou RED por função ausente, RED na
aba integral, RED para remakes e RED para `Remaster`; a suíte dedicada passou 14/14 e o seletor de
anos 15/15. Idiomas passaram 21/21, armazenamento 27/27, motor antigo 39/39,
D-pad 366/366 e a regressão pós-gate 58/58 em 143,3 segundos. O WGT
`workspace/tizen-emulator-20260824-230918/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve PID 5239 por 30 segundos. São 726.789 bytes, 40
entradas, duas assinaturas, 38/38 payloads exatos, zero caminho inseguro, zero
padrão de atribuição literal de credencial e SHA-256
`B3B64A7EBF41BF8C10E7600921A0DB06356DBB9570C13EAD1B4148FC3561FB77`.

95. A auditoria de Configurações encontrou uma diferença funcional em relação
ao Windows: a região de disponibilidade era global na Samsung e só podia ser
alterada dentro de Assinaturas, embora Home e os atalhos de Filmes/Séries também
dependessem dela. Configurações agora mostra BR, PT, US, DE e IT como cinco
alvos D-pad acessíveis. A escolha pertence ao perfil ativo, perfis novos começam
em BR e o valor global legado migra para o perfil anteriormente ativo. Trocar
perfil ou região cancela Home pública, diretórios, Assinaturas e índice de
serviço pendentes; um contador de geração impede que o `fold` do IndexedDB ou
uma resposta antiga republique dados do país anterior. A identidade desse
índice ganhou também o tipo, impedindo reutilização entre Filmes e Séries.
O TDD registrou as falhas esperadas por controles, mapa e migração ausentes e
depois passou 16/16. A regressão completa passou 59/59 grupos pós-gate em 150,4
segundos; armazenamento passou 27/27, idiomas 21/21, ES5 39/39 e D-pad 366/366.
O gate global continua 4/5 somente pela diferença 3.0.1/3.3.0. O WGT
`workspace/tizen-emulator-20260824-233124/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve PID 6251 por 30 segundos. São 728.268 bytes, 40
entradas, duas assinaturas, 38/38 payloads exatos, zero caminho inseguro, zero
atribuição literal de credencial com alta entropia e SHA-256
`21302894D10A607BD65124AA409CF5CBFE1812584AF6F526337D974E969456E4`.

96. Android/Windows já apresentavam a TV como destino receptor, enquanto a
Samsung só enviava um título para fora e recebia links por AppControl. A seção
Configurações agora oferece “Receber título”: cria um código `open_title`, mostra
QR/endereço, instrução específica e aguarda o celular/computador. Como o Web
Runtime Tizen não expõe um socket servidor equivalente ao receptor LAN do
Windows, o relay temporário já protegido por expiração, limite de tentativas e
entrega única é o adapter de plataforma. O retorno aceita somente
`iptvburo://title` ou `https://iptvburo.pages.dev/t/`, rejeita host/caminho,
fragmento, controles, identidade ou tamanho inválidos e resolve apenas contra o
catálogo local completo visível ao perfil. Não há consulta ao provedor,
persistência do link nem transporte de stream/credencial. A página `/parear`
também ganhou copy específica sem JavaScript para `kind=open_title`; publicá-la
depende de deploy separado do Worker. TDD passou 11/11, visual 289/289, D-pad
366/366, ES5 39/39, idiomas 21/21, regressão Samsung 60/60 grupos pós-gate em
166,8 segundos e relay/licença 204/204. O gate geral permanece 4/5 pela versão
3.0.1/3.3.0. O WGT
`workspace/tizen-emulator-20260824-234859/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve PID 6827 por 30 segundos. Possui 729.350 bytes,
40 entradas, duas assinaturas, 38/38 payloads exatos, zero caminho inseguro,
zero atribuição de credencial com padrão de chave/token e SHA-256
`9E979C5CAE40A0CE901EDE562E49C431077A83175DD2224277C9B822BD861C19`.

97. Android e Windows consultavam `/v1/key-info` durante a digitação, mas a
Samsung chamava `/v1/redeem` sem mostrar previamente se a chave estava
disponível, expirada, em uso ou já pertencia ao aparelho. O cliente Tizen agora
assina a consulta com a prova `validate`, não envia chave pública/installationId,
aceita somente cinco estados e duração limitada, converte 404 em `unknown` e
nunca persiste a resposta. A tela aguarda 600 ms, conserva o rascunho em memória,
descarta callback antigo e só habilita “Usar chave” para `available`/`yours`.
Falha de rede oferece nova verificação sem transformar consulta em concessão.
TDD passou 9/9 no módulo e 9/9 na integração; o Worker passou 4/4 para o contrato
existente. ES5 passou 39/39, idiomas 21/21 com 711 chaves, acessibilidade 15/15,
D-pad 366/366 e visual 291/291. Os 62 grupos pós-gate passaram em 167,1 segundos;
o gate segue 4/5 somente por 3.0.1/3.3.0. O WGT
`workspace/tizen-emulator-20260825-001055/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e manteve PID 7424 por 30 segundos. Possui 731.877 bytes,
40 entradas, duas assinaturas, 38/38 payloads exatos, zero caminho inseguro,
zero atribuição de credencial com dígitos e SHA-256
`9B4086D0A5FC347AF91FFC317915102E8253DE44C97D5C39D525456163EAF1A5`.

98. A fonte M3U Samsung já importava canais, mas ignorava a referência XMLTV que
Android/Windows usam para formar o guia. O parser agora reconhece
`url-tvg`/`x-tvg-url` e `tvg-id`; a URL autenticada fica somente no KeyManager e
é resolvida tarde. Um parser XML seguro e limitado relaciona programas por
canal, aceita URLs alternativas, mantém cache transitório e degrada para “guia
indisponível” sem impedir playback. Os testes puros e integrados passaram e o
WGT auditado não contém URL privada ou atribuição de credencial.

99. A Home Android/Windows já usa `SeasonalCollections`, mas a primeira versão
Samsung procurava somente nas 24 escolhas editoriais e podia apagar a fileira
quando seu único item também virava Hero. A varredura única do catálogo agora
acumula no máximo 18 correspondências, respeita perfil/Kids/categorias ocultas,
deduplica títulos e desenha a coleção antes das fileiras comuns com selo
localizado. TDD passou 11/11 + 5/5, ES5 41/41 e visual 295/295. Os 68 grupos
pós-gate passaram; o gate segue 4/5 apenas por 3.6.0/3.6.2. O WGT da revisão
`20260827-095303` tem 743.487 bytes, 42 entradas, duas assinaturas, 40/40
payloads exatos e SHA-256
`52FCEE8535771F33F1D42FE6EDF738D92DA719C6D3E94AEFE6D8D869BB65DA9A`.
Instalado no `emulator-26101`, conservou PID 4039.

## Sequência recomendada

1. Repetir a validação numa TV Samsung física; a VM Tizen TV atual já instala e abre o pacote.
2. Repetir em TV real a validação já passada no emulador para backdrop/Hero/TMDb/OMDb; validar trailer incorporado, fila Xtream, rotação e consumo de memória no hardware.
   Incluir um canal autorizado com catch-up para confirmar fuso do EPG, seek e
   expiração da janela no painel real.
3. Validar “onde assistir” e compartilhamento em hardware: respostas por região, prateleiras horizontais, trailer, links externos, leitura do QR em vários celulares, repetir a abertura fria/quente de `iptvburo://title`, comprovar correspondência positiva e retorno ao app. Incluir o novo fluxo Configurações → Receber título com correspondência presente, ausente e link recusado.
4. Validar modos de imagem, menus de faixas/velocidade e estados assíncronos numa TV real.
5. Repetir em TV física o registro/licença já aprovado no emulador, incluindo
   reinstalação, janela offline, consulta dos cinco estados, expiração e resgate
   de chave.
6. Validar Stalker, downloads USB individuais/em lote, retirada/reinício, seletor de foto USB e todos os estados em TV Samsung física.

100. A tela de carregamento Samsung mantinha uma única imagem local estática e
uma barra de 4 px, embora o progresso já calculasse porcentagem real. A abertura
agora usa 12 capas locais organizadas em apenas duas camadas móveis, com fileiras
em sentidos opostos e suporte a movimento reduzido. A barra tem 30 px e apresenta
a mesma porcentagem usada na largura e em `aria-valuenow`. O teste específico
passou 19/19, compatibilidade antiga 44/44, visual 299/299 e a suíte integral
65 grupos/1.769 verificações. O WGT 3.7.0
`workspace/tizen-emulator-20260828-192135/.buildResult/IPTVBURO.wgt` foi assinado,
instalado e aberto no PID 3921; possui 783.513 bytes, 45 arquivos, duas assinaturas,
43/43 payloads exatos, zero artefato de desenvolvimento e SHA-256
`F3043FAD7A8C6099111F2EC3BA2FF53EB108E3BC7BFEB769557CD4C54393596B`.

101. A aba de Filmes/Séries mantinha 12 serviços numa fileira horizontal e
repetia “Serviço” numa segunda linha de filtros, consumindo a altura destinada
às capas. O diretório agora é um seletor vertical ancorado, fechado por padrão,
com contagem; filtros de ano, nota, gênero e serviço
ocupam a mesma faixa. Diretório público e filtro da lista preservam semânticas
distintas. D-pad passou 367/367, visual 302/302 e a suíte integral 65 grupos/
1.772 verificações. O WGT 3.7.0
`workspace/tizen-emulator-20260828-231921/.buildResult/IPTVBURO.wgt` foi instalado
e abriu no PID 6965; possui 784.087 bytes, 45 arquivos, duas assinaturas, 43/43
payloads exatos, zero artefato de desenvolvimento e SHA-256
`4B36F1E44A86E4A60B6D46FA6D2DC24347BAAF69E79835EFCF53EE0A04582244`.

102. A ficha Samsung exibia o elenco em chips pequenos e as faixas de elenco e
títulos parecidos não comunicavam que havia itens além da borda. O elenco agora
usa cartões com retratos de 82×88 px, uma única faixa horizontal e texto mais
legível. As duas faixas têm indicador dourado de rolagem; a resolução do
container horizontal foi ampliada para que o D-pad desloque ambas. D-pad passou
368/368 e o teste visual passou 304/304, comprovando `scrollLeft > 0` nas duas
faixas com dez itens sintéticos.

103. A preparação do Seller Office agora possui candidato reproduzível em
`workspace/samsung-seller-3.7.0-20260829`: WGT 3.7.0 assinado, hash, ícones nas
três variantes exigidas, quatro screenshots 1920×1080, ficha localizada e roteiro
de certificação. A política pública foi implantada e o manifesto Tizen ganhou a
feature de tamanho de tela. A suíte integral passou 65 grupos em 187,3 segundos;
o pacote de 784.333 bytes foi instalado no emulador e abriu duas vezes no mesmo
PID 4032. O estado permanece `HARDWARE_VALIDATION`: não houve validação em TV
física nem submissão no portal, e o PPTX oficial depende de login Samsung.

104. A nova comparação com o Windows encontrou três diferenças executáveis. O
cabeçalho Samsung atualizava somente Xtream, embora M3U e Stalker já possuíssem
reconciliação transacional escondida em Gerenciar fonte; o mesmo atalho agora
atualiza a fonte ativa e conserva tela/foco. A instrução fixa do controle cobria
o fim das capas e fichas; ganhou uma faixa exclusiva fora do viewport rolável.
Por fim, `BuroTrailer` construía um embed sem API para widgets `file://`, mas
`available()` o recusava antes de abrir; o fallback agora usa controles próprios
do vídeo e ajuda localizada, mantendo o D-pad completo quando existe origem
HTTP. Atualização passou 21/21, trailer 17/17, ES5/CSS 44/44, idiomas 21/21,
D-pad 368/368, visual 305/305 e a suíte integral 65 grupos em 193,2 segundos. O
WGT `workspace/tizen-emulator-20260829-005035/.buildResult/IPTVBURO.wgt` possui
788.690 bytes, duas assinaturas, 45 entradas, zero artefato de teste e SHA-256
`3B974809D5674EE6085BD3E3FF9CB17A411530B563A278D4E6C909453803E8D9`; instalado
no `emulator-26101`, conservou PID 4915.

105. A revisão das alterações mais recentes do Windows encontrou três melhorias
que ainda não estavam completas nas outras telas. Samsung e Android agora só
revelam o trailer automático depois de reprodução confirmada; página carregada
sem `PLAYING`, erro do provedor ou timeout de 10 segundos preservam a capa. O
Android passou a usar o mesmo assentamento compartilhado de 3 segundos e destrói
o WebView que falhou. O Descobrir Android ganhou a terceira decisão, Detalhes,
com textos em EN, PT-BR, DE, IT e ES. Cards de continuidade focados em Android e
Samsung ganharam uma indicação central de reprodução sem alterar o alvo do
card. A adaptação preservou os padrões próprios de D-pad/touch de cada plataforma
em vez de copiar literalmente menus secundários do desktop. Os testes focados
Samsung passaram 19/19 e 15/15; a suíte Samsung integral passou seus 79 grupos
com código 0 em 181,6 segundos. No Android, compilação Kotlin, teste focado e a
suíte completa `testDebugUnitTest` passaram (`BUILD SUCCESSFUL`, 47 tarefas).
Permanece necessária a validação visual e de reprodução em aparelhos físicos.

106. O Descobrir Samsung mantinha um pôster 2:3 de 470×705 px dentro de um
palco de apenas 500 px. As três decisões existiam no DOM, mas eram desenhadas
atrás do card; sinopse e trailer dependiam ainda de o mesmo título ter sido
enriquecido anteriormente pela Home. A tela agora segue a hierarquia do Windows:
pôster estável de 360×540 à esquerda e, à direita, uma janela 16:9 para
backdrop/trailer, sinopse e Pular/Guardar/Detalhes. O card atual dispara seu
próprio enriquecimento limitado, usa provedor com fallback TMDb e conserva tudo
somente em memória. A imagem de referência 1920×1080 está em
`workspace/visual-20260831-discover-parity/discover.png`. D-pad passou 372/372;
trailer 20/20; idiomas 21/21 com 799 chaves por idioma; compatibilidade antiga
45/45. A matriz visual aprovou a composição do Descobrir e 313 verificações no
total, mas terminou 1/314 por crescimento global de listeners após 960 trocas de
rota, fora do fluxo corrigido. O WGT de 810.969 bytes foi assinado, instalado e
permaneceu no PID 4660; SHA-256
`0FBDB55305C899964FE86F39872F60C9E1E2D8FAABBBB32E765CB74535E614E1`.
A inspeção com o catálogo real ficou bloqueada pelo gate legítimo de assinatura
vencida do emulador; nenhum resgate ou alteração de licença foi realizado.

107. Revisão das mudanças do Windows (corte 3.8.0) encontrou o rótulo do botão
principal da ficha que o Samsung não tinha: o desktop rotula o toque com a
decisão de retomada — “Continuar de 12:03” quando existe progresso retomável,
“Assistir novamente” quando o título foi concluído, “Assistir” quando nunca foi
aberto — em vez de um genérico que esconde se a posição será conservada. A ficha
de filme do Tizen agora usa o mesmo contrato, decidido pela mesma
`BuroDomain.resumeDecision` do player, então o rótulo nunca promete uma posição
que o toque não vai cumprir. O prompt de retomada no toque foi mantido: é a
adaptação de TV contra o ENTER acidental e os testes de D-pad o cobrem. As duas
chaves novas (`detailResumeFrom`, `detailWatchAgain`) existem nos cinco idiomas
com as mesmas traduções do Windows. O teste `detail-play-label.test.js` cobre os
três estados, o limiar de retomada e a série intacta: 15/15 verificações. O
contrato multilíngue passou 21/21 com 801 chaves por idioma; lembretes e
contrato do player passaram sem regressão. Ainda pendente do mesmo corte
Windows: a validação visual e de reprodução em TV física.

108. O tique do guia alinhado ao minuto foi portado do Windows
(`rememberGuideClock`). O “agora” do guia, a barra do programa em curso e a
ficha de canal ao vivo eram calculados só quando a tela era desenhada; quem
ficava parado via o relógio da barra avançar sem a linha “agora” se mover. Um
tique de um segundo confere se o minuto virou e só então redesenha, e apenas
sobre o guia (`GUIDE`) ou a ficha de canal ao vivo (`LIVE` com detalhe de
canal): uma troca de minuto em qualquer outra tela não reconstrói nada, e
durante a reprodução o tique não atua, porque o shell está em `aria-hidden`
sob o player. O `render` conserva o foco pela identidade do elemento, então a
linha escolhida no guia continua escolhida depois do redesenho. O teste
`guide-clock.test.js` sobe o app inteiro com banco semeado e cobre com
sentinelas de DOM: redesenho do guia no minuto seguinte, imobilidade no mesmo
minuto, imobilidade na busca, redesenho da ficha ao vivo e proteção durante a
reprodução — 12/12 verificações. Guia 15/15, grade 5/5 e prévia 8/8 passaram
sem regressão. Pendências do corte 3.8.0 do Windows: rótulos de tipo
localizados na busca (no Samsung os cards usam fallback próprio deliberado) e
a validação visual em TV física.
