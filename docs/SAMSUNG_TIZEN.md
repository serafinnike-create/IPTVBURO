# IPTV BURO para Samsung Tizen

Aplicação Web Tizen própria para TV Samsung. O Android TV é a referência de
produto, mas a reprodução e o ciclo de vida usam Samsung AVPlay.

## Estado atual da preview

Implementado e coberto pela suíte automatizada; o pacote compila e assina para Tizen TV:

- aviso legal e perfis, inclusive perfil Kids;
- shell BURO Nocturne navegável por D-pad;
- abertura cinematográfica alinhada ao Windows: mosaico com até 12 capas públicas já resolvidas (ou atlas local enquanto ainda não há arte suficiente), marca circular BURO e seis etapas reais (`perfis → catálogo → arte → varredura → Home → pronto`). A Home, as prateleiras públicas TMDb configuradas, o enriquecimento do primeiro Hero e as capas do primeiro quadro terminam antes de o shell ser revelado; porcentagem, detalhe da etapa e barra determinística mostram o trabalho real, sem spinner genérico. A região é anunciada de forma atômica e todas as animações param com movimento reduzido;
- revelação progressiva de arte alinhada às referências: capas tardias permanecem sobre placeholder dourado discreto até a decodificação, entram sem flash branco e conservam fallback backdrop→pôster. A regra cobre Home, catálogo, detalhes, retomada, pessoas e Assinaturas; movimento reduzido elimina shimmer e transição;
- Hero alinhado ao Windows: filmes e canais oferecem `Assistir` direto mais `Ver detalhes`, séries abrem detalhes para escolher o episódio, e a rotação pausa em toda a linha de ações. Título longo, sinopse e botões permanecem contidos no painel;
- fontes M3U por URL ou arquivo M3U/M3U8 autorizado em USB, além de Xtream-compatible;
- catálogo Ao Vivo, Filmes, Séries e temporadas/episódios; os filtros de Gênero e Serviço reconhecem as categorias nomeadas pelo provedor, mantêm serviço e gênero separados e conservam fallback explícito quando a lista não organiza por serviço; com TMDb configurado, Filmes e Séries também mostram antes dos filtros até 12 atalhos visuais do diretório regional, com logo/nome e abertura direta da grade completa de até 100 títulos do serviço; a Home acrescenta, depois das prateleiras editoriais, uma prateleira por serviço realmente nomeado pela fonte, com marca e sem repetir o mesmo título em dois trilhos; quando existe chave TMDb, ela também termina com as prateleiras públicas de Assinaturas do Android/Windows, sem transformar títulos externos em reprodução direta e com RETURN restaurando o card de origem; filmes, séries e canais locais também retornam dos detalhes reutilizando o mesmo modelo da Home, a rolagem vertical, a posição da prateleira e o foco exato, sem uma segunda leitura do IndexedDB; Descobrir usa o mesmo baralho finito de 15 títulos do Android/Windows, mostra uma carta e a próxima em profundidade, aprende com Guardar/Pular, grava Guardar nos favoritos e exclui ao vivo, histórico, favoritos e decisões da sessão; a busca local usa substring com debounce de 300 ms, ordem equivalente ao Android e cursor IndexedDB entre páginas;
- detalhes Xtream de filmes e séries e guia curto de programação ao vivo; a série oferece no topo a mesma ação principal do Windows, escolhendo o primeiro episódio retomável ou, sem progresso, o primeiro episódio, e inicia diretamente na posição anunciada; o botão principal da ficha de filme usa o mesmo rótulo do Windows, anunciando “Continuar de {horário}” quando há progresso retomável e “Assistir novamente” quando o título foi concluído, nos cinco idiomas; o guia e a ficha de canal ao vivo acompanham a virada do minuto como no Windows, redesenhando só essas telas e preservando o foco, sem tocar em tela nenhuma durante a reprodução;
- favoritos por perfil, continuidade e histórico locais, ordenados como no Android, limitados a `200/20/60` registros e apresentados em páginas de 40; progresso de episódio abre a série correspondente sem perder a porcentagem assistida; a Home também representa episódios pela série, deduplica várias retomadas do mesmo título e usa o episódio incompleto mais recente para ordem e barra de progresso;
- perfil Kids, categorias ocultas e bloqueio parental por PIN com hash salgado;
- gerenciamento completo das categorias da fonte ativa, em páginas de 40 linhas navegáveis por D-pad, sem o antigo corte após 300 categorias e sem revelar rótulos adultos a perfis Kids;
- idiomas PT-BR, EN, DE, IT e ES;
- Configurações alinhadas à referência Android: versão real do pacote, aviso legal, idiomas com nomes completos e estado atual, preferências acessíveis e atalho para o perfil ativo; o painel OMDb também possui o guia local de quatro etapas do Android/Windows, incluindo a ativação obrigatória pelo e-mail;
- credenciais no Tizen KeyManager e catálogo no IndexedDB;
- resolução de URL de reprodução somente em memória;
- AVPlay com buffering, play, pause, stop, avanço de 30 s/retrocesso de 10 s como Android, retomada, timeline, seleção de faixas, velocidade condicionada à capability, modos de imagem, erros recuperáveis e seleção automática de variante compatível para filmes 4K/HDR/DV/HEVC quando a mesma fonte oferece alternativa; durante a reprodução, ENTER pausa/retoma, vermelho alterna Minha BURO, o topo mostra programa atual/próximo e verde abre a programação completa já carregada, inclusive entradas encerradas ou uma explicação quando o provedor não enviou EPG; RETURN e o fim natural recompõem imediatamente o progresso visível atrás do player, preservando foco, rolagem vertical e posição horizontal da prateleira;
- bloqueio de controles equivalente ao Android durante a reprodução: ENTER longo (0,8 s) bloqueia e desbloqueia, o painel explica a ação nos cinco idiomas, setas/teclas de mídia/STOP ficam inertes e RETURN permanece como saída de segurança;
- tamanho, cor e fundo de legenda aplicados por uma camada segura de cues do AVPlay quando `setSilentSubtitle` existe; Configurações mostra simultaneamente os mesmos quatro tamanhos e cinco cores do Android, com rótulos nos cinco idiomas, amostra real, seleção semântica e escolha direta pelo D-pad; sem essa API, as opções visuais ficam ocultas;
- downloads em USB removível por capability: filmes e episódios individuais, séries/temporadas em lote e filmes M3U que apontam para arquivo direto; a biblioteca prioriza operações ativas/falhas como Android e Windows, pesquisa títulos localmente sem distinguir acentos, filtra Filmes/Séries e limita o DOM a 40 linhas por página; detalhes refletem cancelar/continuar/tentar novamente/assistir/remover, com recuperação da fila após reinício e reprodução offline pelo AVPlay sem nova consulta ao provedor;
- cache opcional de capas em USB, ativado em Configurações: toda arte pública descoberta na varredura entra numa fila limitada a duas transferências, com total, concluídas, falhas, porcentagem real e ações Pausar/Continuar/Atualizar pelo D-pad. Arte autenticada, URL com token, arquivo acima do teto e nome inseguro são recusados; desligar preserva as cópias e apagar continua sendo uma ação explícita;
- TMDb opcional por perfil/casa, aceitando API Key v3 na query ou Read Access Token v4 somente no cabeçalho Bearer, guia local em quatro etapas para obter a credencial gratuita, tela de pessoa, Assinaturas/onde assistir e compartilhamento por QR local, sempre condicionados às capabilities e configurações reais; notas OMDb identificam RT, IMDb e MC por sigla/cor, conservam nome e valor acessíveis e aplicam ao Metascore as faixas `61/40` da mesma referência Windows; a TV também recebe `iptvburo://title` por `app-control`, conserva o pedido durante boot/perfil/fonte e resolve a identidade pública contra o catálogo completo da fonte local ativa, sem transformar arte ou metadado recebido em stream; a marca pública `w92` de cada serviço aparece ao lado do nome, sobre cards, na grade **Ver mais** e nas ofertas, sempre com fallback textual e allowlist exata do CDN TMDb; cada serviço real termina em **Ver mais**, que mostra os 20 títulos já carregados enquanto pagina até 100, conserva o conteúdo inicial na falha e cancela respostas obsoletas; o detalhe de Assinaturas também permite marcar/remover um lembrete para uma estreia ainda ausente do catálogo, persistindo somente identidade pública, título, data e arte segura; as prateleiras públicas são reutilizadas no mesmo dia por região, filtro e idioma, sem guardar chave, oferta, sinopse ou URL externa;
- a ficha usa o mesmo encadeamento de títulos semelhantes do Android/Windows:
  filmes mostram primeiro a franquia em ordem de lançamento e depois as
  recomendações TMDb, recorrendo a `/similar` somente quando necessário;
  séries usam recomendações. O card procura o título na fonte local ativa antes
  de abrir Assinaturas/Onde assistir, e a volta restaura título, card, scroll
  vertical e posição horizontal. Sem resposta TMDb, o mesmo componente recebe
  o fallback local por gênero, isolado por fonte e regras parentais. Nota TMDb e
  nota editorial do provedor não são combinadas;
- foco semântico sincronizado ao D-pad, landmarks, labels, estados de switches/filtros, regiões de loading/erro, diálogos e progressos acessíveis; Ribbon, cards e prateleiras horizontais deslocam explicitamente o contêiner para revelar o item focado mesmo nos engines Tizen anteriores ao suporte completo de `scrollIntoView` com opções; contraste AA automatizado e suporte preservado a alto contraste/movimento reduzido;
- lembretes por perfil, marcados no detalhe de filmes e séries pela identidade de conteúdo, com trilho próprio na Home logo após Continuar assistindo, destino na Ribbon ao lado de Histórico e um aviso na abertura do aplicativo. Um título ainda ausente do catálogo continua aparecendo; o horizonte de 30 dias, a ordem (lançados, depois por proximidade, depois alfabética) e o tratamento de datas seguem a mesma política do Android. A composição também acompanha a referência: o trilho da Home usa até 12 pôsteres com selo fixo e leva à página quando pressionado, enquanto a página mostra todos os lembretes numa lista vertical com arte pequena, estado e Remover em cada linha. Quando a identidade encontra um filme ou série local, a parte principal da linha abre os detalhes e RETURN restaura a mesma linha/foco; quando não encontra, a tela explica que o título ainda não está na biblioteca e não inventa uma ação vazia. Sem pôster guardável — o caso comum, já que a arte com credencial é descartada na entrada do banco — a inicial do título ocupa o lugar, em vez de um símbolo repetido em todas as linhas. A TV não possui o aviso diário do celular — `background-support` está desabilitado no manifesto, então nada roda com o app fechado —, e a página diz isso em vez de oferecer um horário que nunca dispararia;
- central de avisos por perfil alinhada ao Android/Windows: abrir o sino marca e persiste todos como lidos imediatamente, remoção individual permanece disponível e **Limpar tudo** esvazia a coleção pelo D-pad; os rótulos existem nos cinco idiomas;
- adapter Stalker/Ministra ligado à interface, com formulário, validação de MAC, handshake, estado da conta e catálogo sob demanda cobertos por testes; o transporte MAG ainda precisa de um portal autorizado real e de TV física.

Ainda não validado em TV física: codecs, áudio, legendas, HDR e desempenho com
catálogos grandes, Voice Guide e ordem de leitura real. O transporte Stalker/Ministra,
licenciamento em hardware, Cast e multiview continuam condicionados, ocultos ou
desabilitados conforme a capability real; o primeiro registro de licença já foi
validado no emulador contra o Worker real.
Manifestos HLS `.m3u8` não são apresentados como download de arquivo único.

Em 20 de agosto de 2026 a VM `T-samsung-10.0-x86_64` foi validada com o perfil
`burocertificate-emulator`: o pacote compilou, assinou, instalou e iniciou. O fluxo
real passou por idioma, aviso legal, criação de perfil e Home; setas, ENTER e RETURN
também responderam pelo controle virtual. Um pacote de diagnóstico separado, usando
os mesmos `js/player.js` e `js/keys.js` de produção e o HLS público BipBop da
Apple, comprovou no emulador `PREPARING`, `PLAYING`, pausa e retomada, além de
`+30 s/−10 s` tanto pelas setas quanto pelas teclas dedicadas. A sequência medida
foi direita em 34.784 ms, esquerda em 26.128 ms, Fast Forward em 57.072 ms e
Rewind em 50.045 ms; o log nativo registrou a procura de I-frame e a conclusão
dos seeks. Uma execução multifaixa posterior enumerou 2 faixas de áudio e 8 de
legenda, selecionou a segunda de cada tipo pela API real e registrou download e
buffers WebVTT da legenda escolhida. Isso prova AVPlay, controles e troca nativa
de faixas na VM e a persistência TMDb/OMDb no KeyManager, mas não comprova
som/imagem percebidos, USB, os demais fluxos do KeyManager, codecs
variados ou o comportamento de uma TV Samsung física.

## Capabilities

`packages/platform-capabilities/samsung-tizen.json` descreve o que este app
implementa. O arquivo é estático e nada o corrige em execução, então
`platform-capabilities.test.js` o confere contra o código: `seek` contra
`js/player.js`, offline contra `js/downloads.js`, entrada contra `js/app.js` e
`backgroundJobs` contra o `config.xml`.

Declarar uma capability não é dizer que ela funciona numa TV. O manifesto
registra intenção do código; a disponibilidade real continua decidida em tempo
de execução — downloads, por exemplo, só aparecem quando `BuroDownloads.enabled()`
confirma API, feature flag e um USB removível montado.

## Testes

```powershell
cd apps/samsung-tizen-tests
npm install
npm test
```

Os testes ficam fora do diretório do aplicativo para que `node_modules` não
entre no pacote `.wgt`. Em 24/08/2026, 55 grupos Samsung posteriores ao gate de
versão passaram; o gate global continua vermelho porque Android está em 3.0.1
e Windows em 3.2.1. O shell pronto também recebe o atributo
`data-runtime-ready="3.0.1"` e emite uma única mensagem sanitizada
`IPTVBURO_RUNTIME_READY`: o marcador não inclui perfil, fonte, URL ou segredo.

A revisão mais recente inclui detalhes de filme em tela cheia, filtros e prateleiras de serviço, prateleiras públicas de Assinaturas no fim da Home, painel completo de legendas, central de avisos alinhada, abertura, Descobrir, guia e selos OMDb e Assinaturas ampliadas com marcas públicas TMDb,
lembretes externos, o catálogo **Ver mais**, abertura de títulos locais pela
página Lembretes, entrada compartilhada, credenciais TMDb v3/v4 e cache diário seguro das prateleiras
públicas de Assinaturas, além do retorno de detalhes locais à mesma Home, rolagem
e foco, da ação principal Assistir/Continuar nas séries, da atualização visual
imediata ao retornar do player e da representação correta de episódios pela série
na continuidade da Home. A carta em profundidade do Descobrir conserva arte e
volume visual, mas agora oculta texto e semântica até chegar à frente, eliminando
a sobreposição encontrada na inspeção. Os detalhes de Assinaturas agora sempre
abrem no topo, com Voltar focado, e restauram o serviço, o card e as rolagens
vertical/horizontal de origem, inclusive quando dois serviços possuem o mesmo
título. O formulário Stalker mantém a orientação opcional abaixo do título,
sem sobreposição; o guia ao vivo identifica o canal no rodapé e o programa
focado conserva contraste claro/escuro. Um contrato adicional confere diretamente
as 672 mensagens de PT-BR, EN, DE, IT e ES, incluindo chaves, parâmetros e
codificação. O shell fixa Ribbon, topbar e conteúdo ao viewport da TV, e a
captura visual não expande mais a superfície para além de 1920×1080. A revisão
foi compilada, assinada, instalada e aberta
no emulador pelo PID 27134; depois de 30 segundos, uma segunda consulta ao
launcher confirmou o mesmo PID, em vez de aceitar somente a abertura inicial.
O pacote está em
`workspace/tizen-emulator-20260820-233011/.buildResult/IPTVBURO.wgt`. O WGT tem
648.682 bytes, SHA-256
`14B4BA239AE7A44727DA7FE1041321EF0649FE637DF7B08B4D4EF513D4C32413`, 35
entradas, duas assinaturas e nenhum arquivo de teste/diagnóstico. As 33 entradas do payload
coincidem por SHA-256 com os fontes atuais.

Um smoke reproduzível das mesmas fontes Web em Chromium, na área lógica real de
1920×1080, percorre idioma, carregamento, aviso legal, criação de perfil, Home
demonstrativa e real, categorias e detalhes de Ao Vivo, catálogo de filmes,
detalhes de filme e série, episódios, Descobrir, Pesquisa, Minha BURO, Continuar,
Histórico, Lembretes, Notificações, Fontes, Perfis e Configurações, incluindo
edição de perfil, gerenciamento e formulário Xtream, PIN parental, categorias,
TMDb/OMDb e seus guias, armazenamento, downloads, Assinaturas/Ver mais/detalhe,
pessoa, compartilhamento, M3U remoto/USB, Stalker, foto USB, retomada, download
em lote, licença, player VOD/ao vivo, menus de faixas/velocidade/guia, erro e
trailer. A matriz adicional repete Configurações, Stalker, retomada e erro do
player nos cinco idiomas. Suas 260 verificações e 79 capturas medem viewport, overflow,
área segura, alvos, foco,
interseções e quadros PNG não vazios; aviso, sino e selo da plataforma não se
sobrepõem. A Home vazia agora replica os três trilhos demonstrativos Android:
4 cards de continuidade, 4 de programação simulada e 6 pôsteres editoriais.
Cada um abre seu próprio detalhe fictício, sem URL ou reprodução; a rolagem de
foco fica confinada ao conteúdo para não expulsar Ribbon/topbar do quadro. O
mesmo smoke executa ainda 960 trocas de tela: na execução registrada o DOM
voltou de 312 para 312 nós, sem crescimento de nós CDP, listeners ou documentos
após coleta de lixo e com somente 332 KiB adicionais de heap coletado.
Um segundo cenário grava 12.000 títulos sintéticos pelo caminho transacional
real da importação. A execução registrada terminou em 1.868 ms, manteve 156 ticks
do event loop com intervalo máximo de 60 ms, compôs a Home em 11 ms sem manter
o catálogo inteiro em memória e validou busca e categoria de 1.000 títulos. A
categoria abre somente 200 registros do IndexedDB, carrega blocos adicionais de
200 por cursor quando o usuário pede e nunca desenha mais de 200 cards. A
substituição continua atômica, mas enfileira no máximo
1.000 `put()` por evento IndexedDB em vez de bloquear o JavaScript com toda a lista.
Essa inspeção prova a composição HTML/CSS estática, não substitui a observação
da animação e do framebuffer nativo
do emulador ou de uma TV física. A tela de legendas da revisão atual também foi
renderizada pelas fontes de produção em 1920×1080: o painel mediu `x=64`,
`largura=1753`, `direita=1817` e mostrou as nove escolhas sem recorte.

Em 24 de agosto de 2026, a revisão com cache completo de capas, abertura
bloqueante, paginação remota Stalker e licença interoperável foi compilada,
assinada, instalada e aberta na VM `T-samsung-10.0-x86_64`. O processo conservou o PID 13778 por 30
segundos; a inspeção posterior da janela real mostrou a Home com hero,
continuidade e capas renderizadas. Com a fonte local ativa, o primeiro Hero
também apareceu com backdrop, sinopse, ano, duração e nota reais antes da
revelação. O pacote
`workspace/tizen-emulator-20260824-153120/.buildResult/IPTVBURO.wgt` possui
712.364 bytes, SHA-256
`BF5F9C489B829CFF0ACA0FB20C0405A3C71D4A228043D727B0BE2312B94763CC`, 39
entradas, duas assinaturas, nenhum arquivo de teste/diagnóstico/credencial e os
37 arquivos de payload coincidem com os fontes atuais. Os 55 grupos posteriores
ao gate de versão passaram; cache, painel de armazenamento e D-pad somam
28, 25 e 343 verificações, e o smoke visual soma 266 verificações. TMDb e OMDb
continuaram configurados no KeyManager; a chave OMDb é aceita somente após uma
consulta real de validação. O TMDb carregou prateleiras regionais e capas reais;
o OMDb retornou a nota IMDb de um título real. Nenhuma credencial foi embutida nem gravada em
arquivo, log, `localStorage` ou WGT. O nonce e a prova ECDSA agora usam
Base64URL sem padding, conforme o contrato do Worker; a janela real passou de
`Dispositivo não registrado` para `Faltam 7 dias`. Sem resposta de títulos
semelhantes do TMDb, a ficha monta a mesma prateleira com títulos do catálogo
local, restritos à fonte atual, ao tipo e às regras parentais.

## Revisão validada em 24/08/2026 às 18:50

O cache de capas em USB passou a mostrar velocidade real durante o
preenchimento, sem persistir URL ou dados de rede. Pausa, conclusão, desligamento
e limpeza removem imediatamente uma taxa antiga. Cartões sem arte reservam a
mesma caixa 2:3 e trilhos longos de semelhantes permanecem contidos no viewport.

Os 55 grupos posteriores ao gate de versão passaram em 145,5 segundos. O smoke
visual aprovou 269/269 verificações, incluindo a taxa do cache, o placeholder e
o trilho longo; D-pad aprovou 349/349, TMDb 44/44 e compatibilidade antiga 38/38.
O pacote `workspace/tizen-emulator-20260824-184638/.buildResult/IPTVBURO.wgt`
foi assinado, instalado e permaneceu no PID 5187 por 30 segundos. Ele possui
716.077 bytes, 39 entradas, duas assinaturas, 37/37 payloads idênticos e SHA-256
`3BBD2EC7683EE1F5528D9C6D697A35AA77F103ADC90C4F7CDBBDD3631008AC5C`.
A inspeção real da janela mostrou a Home com Hero, backdrop, sinopse e capas já
renderizadas. Nenhuma chave TMDb/OMDb foi incorporada ao WGT.

## Revisão validada em 24/08/2026 às 19:07

O guia Xtream passou a oferecer catch-up quando o canal declara arquivo e o
programa encerrado ainda está dentro da quantidade de dias informada pelo
provedor. `Assistir gravação` aparece na ficha do canal e no guia sobre o
player; ENTER abre a gravação com timeline, seek e velocidade. Linhas sem
arquivo válido continuam informativas, sem prometer uma reprodução que o
servidor não possui.

A URL `timeshift` é formada somente no momento do play com a credencial lida do
Tizen KeyManager. Ela não é gravada no item, progresso, `localStorage` ou
IndexedDB. Nesta primeira etapa a retomada de uma gravação também não persiste
entre sessões; isso evita criar uma entrada órfã na biblioteca antes de existir
um modelo universal de programa gravado.

Os 55 grupos posteriores ao gate de versão passaram em 151,5 segundos. D-pad
aprovou 353/353, compatibilidade antiga 38/38, idiomas 21/21 e o smoke visual
270/270. O pacote
`workspace/tizen-emulator-20260824-190924/.buildResult/IPTVBURO.wgt` foi
assinado, instalado e permaneceu no PID 6674 por 30 segundos. Tem 718.439
bytes, 39 entradas (37 payloads e duas assinaturas), 37/37 payloads idênticos
ao staging, zero literal com forma de segredo embutido e SHA-256
`7FAF3532D4889E2B658FF3008C775D1CFB0E07124CBA9B2B9B6849677E73B82C`.

## Revisão validada em 24/08/2026 às 19:31

Continuar assistindo e Histórico passaram a acompanhar a gestão já disponível
no Windows. Cada card tem uma ação Remover separada: em Continuar, ela marca a
linha como concluída e conserva o título no Histórico; no Histórico, remove
somente a linha de progresso escolhida. O Histórico também ganhou busca local
de até 80 caracteres, tolerante a acentos, ano e decoração de qualidade/idioma,
além de limpeza integral precedida por confirmação explícita.

As mutações são restritas ao perfil ativo. A limpeza percorre o índice
`byProfile` numa única transação e preserva catálogo, favoritos, downloads e os
registros dos demais perfis. A suíte pós-gate passou 55/55 grupos em 146,7
segundos; D-pad passou 358/358, armazenamento 12/12, biblioteca 23/23,
compatibilidade antiga 38/38, idiomas 21/21 e visual 274/274.

O pacote
`workspace/tizen-emulator-20260824-192911/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 7219 por 30 segundos no
`emulator-26101`. Possui 720.662 bytes, 39 entradas (37 payloads e duas
assinaturas), 37/37 payloads idênticos ao staging, nenhum arquivo de
teste/diagnóstico, zero literal com forma de chave e SHA-256
`2416D7417DEF28BCB0E80D918735033631ED07045F797CD9DB8C3DC0C690A826`.

## Revisão validada em 24/08/2026 às 19:46

Continuar assistindo agora oferece no próprio card as três decisões existentes
no Windows: continuar da posição mostrada, assistir do início ou remover da
fila. Uma série continua usando a capa e os detalhes do pai, mas as ações de
reprodução apontam para o episódio exato da linha de progresso. O fluxo não
repete o diálogo de retomada e volta a validar tela, perfil, conclusão, fonte e
tipo antes de entregar a posição ao player.

As ações foram verificadas com filme e episódio Xtream sintéticos, tentativa de
linha forjada de outro perfil, D-pad e as cinco traduções em 1920×1080. A suíte
pós-gate passou 55/55 grupos em 145,8 segundos; biblioteca passou 29/29, D-pad
361/361, compatibilidade antiga 38/38, idiomas 21/21 e visual 284/284.

O pacote
`workspace/tizen-emulator-20260824-194551/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 7762 por 30 segundos no
`emulator-26101`. Possui 721.248 bytes, 39 entradas (37 payloads e duas
assinaturas), 37/37 payloads idênticos ao staging, nenhum arquivo de
teste/diagnóstico, zero literal com forma de chave e SHA-256
`C02FB322897616F813D132A5BAC84D4DA2291247EC81B333A8D6F2D18B24BB9D`.

## Revisão validada em 24/08/2026 às 19:53

A Home instalada foi comparada visualmente, lado a lado, com o aplicativo
Windows em execução. A Samsung já escondia o shell até concluir catálogo,
prateleiras TMDb, enriquecimento do primeiro Hero e capas visíveis; não foi
necessário criar outra espera artificial. A diferença concreta estava na escala
do destaque: o Hero ocupava 390 px, abaixo da proporção de 52% usada pelo
desktop.

O Hero real agora mede 510 px, ou 51,6% da área útil de 1920×1080, conserva até
três linhas de sinopse, usa metadados dourados e um degradê vertical para ligar a
arte à primeira prateleira. Título longo, ações `Assistir`/`Ver detalhes`, Hero e
primeiro trilho continuam inteiros no primeiro quadro e o D-pad não perdeu foco.

Boot passou 14/14 + 12/12, compatibilidade antiga 38/38, D-pad 361/361, visual
286/286 e a regressão pós-gate 55/55 em 157,6 segundos. O pacote
`workspace/tizen-emulator-20260824-195253/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 8268 por 30 segundos no
`emulator-26101`. Possui 721.287 bytes, 39 entradas (37 payloads e duas
assinaturas), 37/37 payloads idênticos ao staging, nenhum diagnóstico, zero
literal com forma de chave e SHA-256
`5577C5156B6AD4CEBDB0709472B1877EBF327A781F027AC6ECC7E44D6A8EF9A2`.

## Revisão validada em 24/08/2026 às 20:09

As fichas foram comparadas lado a lado com o Windows usando títulos reais do
mesmo catálogo. Pôster, backdrop, fatos, nota, progresso, ação principal, barra
de ações, créditos e elenco já estavam alinhados. A diferença encontrada era a
sinopse Samsung: o CSS prometia 24 px, mas apontava para um parágrafo que não
existe como filho direto do Hero, deixando o navegador usar o tamanho padrão.

O seletor agora acompanha o markup real em `.detail-hero-copy`. A ficha instalada
foi reaberta no emulador e confirmou a descrição em 24 px/1,45, inteira sobre o
backdrop e sem tirar `Assistir`, progresso, ações, créditos ou elenco do primeiro
quadro. O teste mede tamanho, entrelinha e largura máxima, além da captura visual.

A regressão pós-gate passou 55/55 grupos em 169,6 segundos; D-pad passou
361/361, compatibilidade antiga 38/38, idiomas 21/21 e visual 287/287. O pacote
`workspace/tizen-emulator-20260824-200843/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 9318 por 30 segundos no
`emulator-26101`. Possui 721.285 bytes, 39 entradas (37 payloads e duas
assinaturas), 37/37 payloads idênticos ao staging, nenhum diagnóstico, zero
literal com forma de chave e SHA-256
`26F94865E593191E09F64AC31774C744BD16664B75FEFF07B7E896DF0F021962`.

## Revisão validada em 24/08/2026 às 20:27

Filmes, Séries e Ao Vivo ganharam a busca contextual que aparece no catálogo do
Windows. O campo fica antes dos filtros, aceita teclado físico/virtual, espera
300 ms pela última tecla e combina a consulta sem acentos com tipo, ano, nota,
gênero e serviço. A leitura continua em blocos de 21 itens no IndexedDB: nenhum
catálogo de dezenas de milhares de títulos é materializado na memória. Uma
consulta sem correspondência mostra estado vazio e `Limpar filtros`; não troca
a prateleira pelos cartões de categoria.

D-pad passou 364/364, visual 288/288, idiomas 21/21, compatibilidade antiga
38/38 e os 55 grupos pós-gate passaram em 158,9 segundos. O gate de versão
continua 4/5 porque Android/Samsung estão em 3.0.1 e Windows em 3.3.0. O pacote
`workspace/tizen-emulator-20260824-202626/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 10312 por 30 segundos no
`emulator-26101`. Possui 722.213 bytes, 39 entradas (37 payloads e duas
assinaturas), 37/37 payloads idênticos ao staging, nenhum diagnóstico, zero
literal com forma de chave e SHA-256
`BE0185710DCC939F832F51620DBE7379E9E70740E92549907EB7770C2180DCF1`.

## Revisão validada em 24/08/2026 às 20:38

A comparação real de Filmes mostrou que a busca contextual já existia, mas a
faixa superior ainda não reproduzia a troca interna `Ao vivo / Filmes / Séries`
do Windows. As três abas agora formam um único `tablist`, mantêm exatamente uma
seleção, usam a mesma ação de seção da Ribbon e dividem a linha com
`Buscar neste catálogo…`. Atalhos de serviço, filtros e capas continuam logo
abaixo, sem rolagem inicial nem overflow em 1920×1080.

D-pad passou 366/366, visual 288/288, idiomas 21/21, compatibilidade antiga
38/38 e os 55 grupos pós-gate passaram em 156,5 segundos. O gate de versão
continua 4/5 porque Android/Samsung estão em 3.0.1 e Windows em 3.3.0. O pacote
`workspace/tizen-emulator-20260824-203834/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 10851 por 30 segundos no
`emulator-26101`. Possui 722.728 bytes, 39 entradas (37 payloads e duas
assinaturas), 37/37 payloads idênticos ao staging, nenhum diagnóstico, zero
credencial literal embutida e SHA-256
`D945B270EAC8870F4F4B7BE1150F2C50D650628F8608B5B7A606753323865EB1`.

## Revisão validada em 24/08/2026 às 20:53

A recepção de `iptvburo://title` foi exercitada pelo shell Tizen real com um
remetente isolado. O app de produção aberto recebeu a identidade pública,
mostrou o aviso de título ausente e permitiu percorrer `Procurar novamente` e
`Fechar aviso` pelo D-pad. Essa prova encontrou uma repetição tardia: a leitura
do último App Control continuava disponível e, após cinco segundos, foco ou
render podiam reapresentar o painel já fechado. A mesma URI agora permanece
deduplicada enquanto o título está pendente; retry explícito e URI nova continuam
funcionando. O aviso permaneceu fechado depois de sete segundos fora de foco.

Os 55 grupos pós-gate passaram em 154,7 segundos; link recebido 12/12,
remetente 9/9, D-pad 366/366, visual 288/288, compatibilidade antiga 38/38 e
idiomas 21/21. O pacote
`workspace/tizen-emulator-20260824-205359/.buildResult/IPTVBURO.wgt` foi
compilado, assinado, instalado e manteve o PID 11855 por 30 segundos. Possui
723.104 bytes, 39 entradas (37 payloads e duas assinaturas), 37/37 payloads
idênticos ao staging, nenhum diagnóstico, zero credencial literal embutida e
SHA-256 `2A29565FBF5E5D55A417BF2CCA1B528F2FCC80FF0ED0B959474C1F9C3540C6A7`.

## App Control frio validado em 24/08/2026 às 21:08

O runner de App Control ganhou `-ColdStart`. Ele instala primeiro o remetente
isolado, desliga a TV pela `KEY_POWER=116` oficial da skin Samsung, exige a saída
do serial SDB e do processo da VM, relança exatamente a mesma VM e espera o boot
com timeout. Não usa fechamento de janela, `Kill`, `reset` ou `delete`.

Na execução `workspace/tizen-appcontrol-smoke-20260824-210854`, o link iniciou o
IPTV BURO sem abertura manual. O primeiro quadro do novo processo já mostrou o
aviso recebido, seta direita e ENTER o fecharam, e perfil, fonte, catálogo e
credenciais protegidas sobreviveram ao Power. O processo de produção permaneceu
no PID 3690 por 30 segundos. O remetente temporário foi desinstalado ao final.
Runner passou 10/10, consumidor 12/12 e os 55 grupos pós-gate passaram em 157,6
segundos. O WGT de produção continua o assinado `20260824-205359`, porque esta
etapa altera somente o runner e seus testes.

## Correspondência positiva e retry limitado — 24/08/2026 às 22:04

O remetente passou a aceitar uma URI pública configurável após validação
estrita de `id`, título e ano. Porta, origem permitida e receptor local são
gerados juntos no staging; nenhum stream, segredo ou valor completo da URI é
impresso. Um título existente na biblioteca abriu a ficha correta no app quente
e depois de Power/boot completo. As execuções `20260824-220349` e
`20260824-220437` terminaram com `APPCONTROL_LAUNCH_PASS` e removeram o pacote
temporário.

O player também ganhou uma tentativa automática limitada para falha transitória
de conexão. Há uma única repetição após 1,5 segundo; uma segunda falha conserva
o painel manual com Retry/Voltar. Erro de fonte e formato incompatível não são
repetidos. O timer é cancelado ao sair do player.

A regressão passou 56/56 grupos pós-gate em 138,7 segundos, recuperação 8/8,
integração 17/17, D-pad 366/366, visual 288/288 e motor antigo 39/39. O WGT
assinado `workspace/tizen-emulator-20260824-220051/.buildResult/IPTVBURO.wgt`
possui 724.157 bytes, 40 entradas, 38/38 payloads idênticos ao staging e
SHA-256 `543E1E0C6AE3805249C040B44C4CF9EC38AC8551D1BDAC3A4F5A8FE0E0AEACE0`.
Ele manteve o PID 4565 por 30 segundos; após o boot frio, o app manteve PID
3652 por outros 30 segundos.

## Relógio 12/24 horas — 24/08/2026 às 22:40

O cabeçalho deixou de ser fixo em 24 horas e passou a repetir a preferência do
Windows. Configurações oferece `24 horas` e `12 horas` como alvos independentes
do D-pad, com seleção acessível. O padrão continua 24 horas; em 12 horas a hora
é exibida como `h:mm AM/PM`. A escolha atualiza a barra no mesmo instante e fica
persistida entre aberturas. Os textos existem nos cinco idiomas do app.

O teste novo registrou RED por ausência dos controles e passou 10/10 após a
implementação. A regressão completa passou 57/57 grupos pós-gate em 140,8
segundos; D-pad 366/366, idiomas 21/21, armazenamento 27/27 e visual 288/288.
O pacote assinado
`workspace/tizen-emulator-20260824-224036/.buildResult/IPTVBURO.wgt` possui
724.763 bytes, 40 entradas, 38/38 payloads idênticos ao staging e SHA-256
`00A7E0C6634419932C05E91C86B2CF6E0048A713CAF7426C79DCDDA4D1AEB194`.
Nenhum arquivo inseguro ou padrão literal de credencial foi encontrado. O app
instalado no `emulator-26101` manteve PID 3983 após 30 segundos.

## Cópias repetidas no catálogo — 24/08/2026 às 23:03

Configurações agora oferece a preferência do Windows “mostrar um só card por
filme”. Ela fica ligada por padrão, responde ao ENTER, anuncia o estado por
`aria-pressed` e persiste. Desligar recupera imediatamente a lista bruta; nenhum
item é removido do IndexedDB.

A identidade descarta qualidade, codec, dublagem e prefixo de serviço, mas
conserva tipo, número de sequência, subtítulo real e ano estruturado. Assim as
variantes de um lançamento formam um card e remakes homônimos continuam
separados. Categoria, contagem e blocos progressivos da aba inteira usam a mesma
regra.

O ciclo TDD passou 14/14 depois dos REDs esperados; a regressão do seletor de
anos passou 15/15. A suíte completa passou 58/58 grupos pós-gate em 143,3
segundos, com idiomas 21/21, armazenamento 27/27, motor antigo 39/39 e D-pad
366/366. O gate de versão permanece 4/5 pela diferença conhecida 3.0.1/3.3.0.

O pacote assinado
`workspace/tizen-emulator-20260824-230918/.buildResult/IPTVBURO.wgt` possui
726.789 bytes, 40 entradas, duas assinaturas, 38/38 payloads idênticos ao
staging, zero caminho inseguro, zero padrão de atribuição literal de credencial
e SHA-256
`B3B64A7EBF41BF8C10E7600921A0DB06356DBB9570C13EAD1B4148FC3561FB77`.
Instalado no `emulator-26101`, manteve PID 5239 por 30 segundos.

## Região de streaming por perfil — 24/08/2026 às 23:23

Configurações oferece BR, PT, US, DE e IT como escolhas independentes do D-pad.
A preferência é persistida por perfil e governa Home, atalhos por serviço,
índice de serviço e Assinaturas. Perfis novos começam em BR; quem já usava a
preferência global conserva sua região no perfil que estava ativo durante a
migração.

Trocar o país ou o perfil aborta consultas públicas ainda em andamento e
invalida seus identificadores. Isso impede que uma resposta lenta da região
anterior apareça depois da troca. A identidade do índice inclui região e tipo,
mantendo Filmes separado de Séries.

O teste dedicado passou 16/16 e a suíte completa passou 59/59 grupos pós-gate
em 150,4 segundos. O WGT assinado
`workspace/tizen-emulator-20260824-233124/.buildResult/IPTVBURO.wgt` possui
728.268 bytes, duas assinaturas, 38/38 payloads idênticos ao staging e SHA-256
`21302894D10A607BD65124AA409CF5CBFE1812584AF6F526337D974E969456E4`.
Instalado no `emulator-26101`, manteve PID 6251 por 30 segundos.

## Receber título de outro aparelho — 24/08/2026 às 23:48

Em Configurações, “Receber título” cria um código de seis dígitos e um QR para
o celular ou computador. O outro aparelho envia um link público de título do
IPTV BURO; a TV procura a mesma identidade somente na lista do perfil ativo. O
fluxo não envia vídeo, fonte, usuário, senha ou URL de reprodução.

O parser aceita somente o esquema privado registrado e a página pública oficial,
com limites de tamanho/campos e identidade. Host externo, caminho diferente,
fragmento e identidade malformada são recusados antes de qualquer navegação. O
payload não é persistido e nenhuma chamada à fonte é feita para resolvê-lo.

O teste dedicado passou 11/11; visual 1920×1080 passou 289/289, incluindo o
cartão completo no primeiro quadro; D-pad passou 366/366, ES5 39/39, idiomas
21/21, regressão 60/60 grupos pós-gate e servidor 204/204. O WGT assinado
`workspace/tizen-emulator-20260824-234859/.buildResult/IPTVBURO.wgt` possui
729.350 bytes, 40 entradas, duas assinaturas, 38/38 payloads idênticos ao
staging, nenhum caminho inseguro e SHA-256
`9E979C5CAE40A0CE901EDE562E49C431077A83175DD2224277C9B822BD861C19`.
Instalado no `emulator-26101`, manteve PID 6827 por 30 segundos.

A página móvel específica para título está implementada no Worker, mas seu texto
novo depende de implantação. Sem esse deploy, o endpoint existente continua
funcional e seguro, porém ainda apresenta o rótulo genérico de chave.

## Consulta da chave antes de ativar — 25/08/2026 às 00:10

A tela de licença consulta a chave 600 ms depois da última tecla e mostra um dos
cinco estados antes do resgate: disponível (com dias), desta TV, em uso noutro
aparelho, expirada ou desconhecida. O botão “Usar chave” só é liberado para os
dois estados aplicáveis. Trocar o texto invalida a consulta anterior, portanto
uma resposta lenta nunca substitui a chave atual.

A consulta é autenticada pela identidade ECDSA da TV, mas não grava licença nem
expõe qual outro dispositivo possui uma chave. Somente o clique explícito chama
o resgate. O rascunho fica na memória da tela; TMDb/OMDb continuam no KeyManager
e nenhuma chave privada foi incluída no pacote.

TDD passou 9/9 + 9/9; Worker 4/4; ES5 39/39; idiomas 21/21 (711 chaves por
idioma); acessibilidade 15/15; D-pad 366/366; visual 291/291. A regressão passou
62/62 grupos pós-gate em 167,1 segundos. O gate segue 4/5 pela diferença de
versão conhecida. O WGT
`workspace/tizen-emulator-20260825-001055/.buildResult/IPTVBURO.wgt` possui
731.877 bytes, 40 entradas, duas assinaturas, 38/38 payloads exatos e SHA-256
`9B4086D0A5FC347AF91FFC317915102E8253DE44C97D5C39D525456163EAF1A5`.
Instalado no `emulator-26101`, manteve PID 7424 por 30 segundos. A captura está
em `workspace/visual-20260825-000645/licence-key-info.png`.

## XMLTV M3U e coleção sazonal — 27/08/2026

O app relaciona `tvg-id` do M3U ao XMLTV informado por `url-tvg` ou
`x-tvg-url`. A referência do guia fica no KeyManager e é lida somente quando a
tela Ao Vivo precisa dela; falha do guia não bloqueia o canal.

A Home usa as mesmas janelas de Natal, Ano Novo, Halloween, Namorados e férias
em família do domínio Kotlin. A busca acontece durante a varredura completa já
necessária para montar a Home, portanto encontra títulos fora da amostra
editorial sem uma segunda leitura do catálogo. O resultado é limitado,
deduplicado, localizado e mostrado acima das escolhas diárias.

Validação: sazonal 11/11, integração 5/5, ES5 41/41, visual 295/295 e 68/68
grupos posteriores ao gate. O WGT assinado da revisão `20260827-095303` foi
instalado no `emulator-26101`, permaneceu no PID 4039 e possui SHA-256
`52FCEE8535771F33F1D42FE6EDF738D92DA719C6D3E94AEFE6D8D869BB65DA9A`.
O gate global permanece 4/5 por Android/Samsung 3.6.0 versus Windows 3.6.2.

## Build e instalação

Com o Tizen Studio instalado e um perfil de certificado Samsung ativo:

```powershell
cd "D:\CURSOR APPS\IPTVBURO"
.\scripts\run-samsung-emulator.ps1
```

Esse comando executa a suíte, inicia automaticamente a VM
`T-samsung-10.0-x86_64` quando não há alvo conectado, aguarda o boot com limite
de 180 segundos, cria uma staging nova somente com os arquivos permitidos,
compila, assina, instala e abre o app. Use `-VmName`, `-ProfileName`,
`-DeviceSerial`, `-BootTimeoutSeconds` ou `-LaunchStabilitySeconds` quando o
ambiente for diferente. Depois da abertura, o runner espera cinco segundos por
padrão, ativa o pacote novamente e exige que o launcher devolva o mesmo PID;
um reinício silencioso transforma a execução em falha.

Para repetir somente a prova nativa de AVPlay com fixture pública autorizada:

```powershell
.\scripts\run-samsung-playback-smoke.ps1 -DeviceSerial emulator-26101
```

O comando gera um WGT de diagnóstico com package id próprio, copia exatamente os
adapters de produção, não inclui credencial/lista do usuário e só aprova quando
o AVPlay toca, pausa, retoma, avança 30 segundos, retrocede 10 segundos e aceita
trocar para outra faixa de áudio e de legenda após enumerar pelo menos duas de
cada tipo. Ao terminar ou falhar, o runner desinstala o pacote de diagnóstico
para não deixar o AVPlay ocupado. Os códigos de injeção `244/106/105/208/168`
vêm das skins Samsung instaladas; dentro do app chegam como `MediaPlayPause`,
setas direita/esquerda, `MediaFastForward` e `MediaRewind`.

Para repetir a entrega com o app já aberto ou em boot frio:

```powershell
.\scripts\run-samsung-appcontrol-smoke.ps1 -ObservationSeconds 20
.\scripts\run-samsung-appcontrol-smoke.ps1 -ColdStart -ObservationSeconds 20
```

O modo frio preserva o disco da VM: usa Power, espera o SDB sair, relança a
mesma VM e só então inicia o remetente. Se houver mais de um processo do emulador
ou se o desligamento não terminar dentro do limite, o runner falha sem forçar.

Os comandos manuais equivalentes são:

```powershell
cd apps/samsung-tizen
C:\tizen-studio\tools\ide\bin\tizen.bat build-web -- .
$profileName = 'nome-do-perfil-certificado'
C:\tizen-studio\tools\ide\bin\tizen.bat package -t wgt -s $profileName -- .buildResult
```

Use `-s <serial>` nos comandos `install-permit`, `install` e `run`. O id do app
é `IPTVBUROxx.IPTVBURO`. Se a CLI antiga não aceitar o espaço em
`IPTV BURO.wgt`, copie o arquivo para `IPTVBURO.wgt` antes de instalar.

Não adicione listas, credenciais, tokens ou URLs privadas ao repositório.

## Resolução e nitidez em painéis FHD/UHD/8K

O layout usa a superfície Samsung recomendada de 1920×1080 e 16:9. O módulo
`js/display-quality.js` detecta a resolução física por `PANEL`/`DISPLAY` e
seleciona fontes TMDb maiores para capas, fundos, logos e elenco em UHD/8K.
Ícones de navegação são SVG. Em 8K, imagens ficam limitadas ao teto UHD para
evitar pressão de memória sem ganho na superfície Web já composta em 1080p.

Isso não muda a resolução do vídeo. AVPlay continua recebendo coordenadas
1920×1080 e a resolução reproduzida depende do manifesto, codec e decoder da TV.
