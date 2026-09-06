# Validação em hardware — Samsung Tizen

**Status:** abertura, onboarding, D-pad, reprodução HLS, controles e seleção nativa de faixas validados parcialmente no emulador; TV física pendente.
**Data:** 24 de agosto de 2026

Os 55 grupos Samsung posteriores ao gate de versão passam; o gate global ainda
falha somente pela divergência preexistente Android 3.0.1/Windows 3.3.0. A
maioria roda contra simulações das APIs da TV. Este documento existe porque essa diferença importa: os testes
provam que o código faz o que pretende, não que a TV aceita o que ele pede.

## O que os testes não podem provar

As áreas abaixo dependem de APIs da plataforma. O caminho
HLS `open → prepareAsync → play`, os controles principais e a seleção de faixas
já foram exercitados no emulador. A persistência segura TMDb/OMDb no KeyManager
foi confirmada após reinstalação; USB e os demais fluxos de identidade segura
continuam pendentes.

| Área | O que está simulado |
| --- | --- |
| Reprodução | Abrir/preparar/tocar HLS, pausar, retomar, `+30 s/−10 s` e selecionar outra faixa de áudio/legenda passaram no AVPlay real da VM; codecs, som/imagem percebidos e integração com fonte do usuário continuam pendentes |
| Download USB | `tizen.download` e `tizen.filesystem` |
| Licença | `tizen.keymanager`, mais a rede real até o Worker |
| Controle remoto | Setas, ENTER e RETURN passaram no shell; `MediaPlayPause`, setas direita/esquerda, `MediaFastForward` e `MediaRewind` passaram durante playback AVPlay real no emulador |
| Link recebido | Associação do esquema, entrega quente e abertura fria de `iptvburo://title` passaram no emulador; fonte ativa, visibilidade e correspondência positiva no IndexedDB ainda precisam ser repetidas em hardware |

Um mock responde o que foi programado para responder. A TV responde o que ela
faz — e a diferença entre os dois é exatamente onde moram os defeitos que
sobraram.

---

## 1. Preparar o ambiente

Há dois caminhos. O emulador é suficiente para quase tudo; a TV física é
obrigatória antes de publicar.

### 1.1 Emulador (nesta máquina)

O SDK tem a imagem `tv-samsung-10.0-x86_64` instalada e a VM
`T-samsung-10.0-x86_64` funciona via WHPX.

Estado medido nesta máquina em 20/08/2026:

```text
WHPX                  operacional
VM TV Samsung 10.0    inicia e responde pelo SDB
Pacote 3.0.1          compila, assina, instala e abre
Runner                inicia a VM fechada e conclui o ciclo em 70,3 s com -SkipTests
AVPlay HLS            PLAYING → PAUSED → PLAYING → +30/−10 s por setas e teclas de mídia
```

O WHPX usa o próprio Hyper-V como motor e convive com WSL2/Docker. Não é
necessário desabilitar o Hyper-V nesta máquina.

Habilitar, num PowerShell **como administrador**:

```powershell
Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All
```

Reinicie. Isso **não** desliga WSL2 nem Docker — ao contrário de
`bcdedit /set hypervisorlaunchtype off`, que era a única alternativa antes.

Depois, num PowerShell **como administrador**:

```powershell
cd "C:\tizen-studio\tools\emulator\bin"
.\emulator-manager.exe list-vm
```

O Emulator Manager (interface) cria a VM: **Tools → Emulator Manager → Create
→ TV**, escolhendo a imagem 10.0.

### 1.2 TV física

1. TV e PC na mesma rede.
2. Na TV: **Apps**, digite **12345** no controle → painel de Developer Mode.
3. Ligue **Developer mode** e informe o **IP do seu PC**.
4. **Tire a TV da tomada por 15 segundos.** Só desligar não abre a porta do
   `sdb`.
5. No PC:

```powershell
sdb connect <IP-DA-TV>
sdb devices
```

Para instalar numa TV física o certificado distributor precisa conter o
**DUID** do aparelho. O perfil atual (`iptvburo`) usa o distributor público,
que serve para emulador e para submissão à loja, mas não para carga direta.
O DUID é adicionado pelo Certificate Manager.

### 1.3 Instalar

```powershell
cd "d:\CURSOR APPS\IPTVBURO"
.\scripts\run-samsung-emulator.ps1
```

Não é necessário abrir a VM antes. O runner usa
`T-samsung-10.0-x86_64`, espera no máximo 180 segundos pelo alvo, estabiliza
os serviços da TV, testa, compila, assina com `burocertificate-emulator`,
instala e abre. Para um alvo já conectado, use `-DeviceSerial` quando houver
mais de um dispositivo. A abertura só é aprovada depois de cinco segundos e de
uma segunda ativação devolver o mesmo PID; `-LaunchStabilitySeconds` ajusta a
janela entre 3 e 60 segundos.

---

## 2. O que verificar

A ordem importa: cada bloco depende do anterior funcionar.

### 2.1 Abertura

- [x] O app abre sem tela preta.
- [x] O processo conserva o mesmo PID durante a janela automática de estabilidade.
- [x] A tela de carregamento aparece e progride no smoke visual de produção em 1920×1080.
- [x] O seletor de idioma responde ao D-pad.
- [ ] Nenhum erro no console (`sdb dlog` ou o Web Inspector).

A revisão atual da abertura possui mosaico cinematográfico com até 12 capas
públicas (atlas local enquanto ainda não há arte suficiente), marca circular
BURO, seis fases reais e progresso determinístico. Catálogo, varredura, cache da
Home, prateleiras TMDb configuradas e capas do primeiro quadro seguram a abertura
antes de revelar a interface. O primeiro Hero também é enriquecido ainda sob a
cobertura, para a Home não surgir com sinopse genérica. O painel é uma região de status atômica e movimento
reduzido desliga marca, mosaico e transição da barra. O quadro final foi
inspecionado em 1920×1080 e está inteiro, legível e sem overflow; animação e
framebuffer nativo ainda devem ser conferidos numa TV física.

O pacote 3.0.1 instalado no emulador contém um marcador de prontidão único e
sanitizado. A suíte confirma que ele só é emitido depois de o shell terminar
renderização, semântica, foco e bindings, e que novas renderizações não o
repetem. O lançamento normal (`debug 0`) não encaminhou o `console.info` inicial
ao log do emulador; portanto a verificação do marcador no Web Inspector, após
recarregar a página, continua pendente e não foi marcada como evidência visual.

**Se a tela abrir preta:** quase sempre é o fundo opaco cobrindo a camada de
vídeo da AVPlay, ou um erro de sintaxe que o motor da TV recusa e o simulador
aceitou. O `engine-compatibility.test.js` cobre a segunda hipótese, mas só
para os padrões que ele conhece.

### 2.2 Controle remoto

- [x] Setas movem o foco, e o foco é visível de longe no emulador.
- [x] ENTER seleciona no emulador.
- [x] RETURN volta; na raiz, fecha o app no emulador.
- [ ] ENTER longo bloqueia/desbloqueia os controles durante AVPlay e RETURN ainda sai.
- [x] A tecla de mídia Play/Pause pausa e retoma durante AVPlay no emulador.
- [x] Seta direita avança 30 s durante AVPlay no emulador.
- [x] Seta esquerda retrocede 10 s durante AVPlay no emulador.
- [x] Teclas dedicadas de avançar/retroceder executam `+30 s/−10 s` no emulador.
- [ ] Segurar uma seta não trava a interface.

A suíte automatizada também percorre a Ribbon inteira e confirma que **Fontes** e
**Configurações**, mesmo fora da largura inicial, recebem foco em ordem e são
reveladas por rolagem programática. A confirmação visual dessa rolagem no painel
OpenGL do emulador/TV continua fazendo parte da validação de hardware.

**Descobrir:** a suíte confirma a mesma mão finita de 15 cartas do
Android/Windows, com somente a carta atual e a próxima no DOM, contador, Pular,
Guardar e Detalhes por D-pad. Guardar persiste o favorito antes de avançar;
Pular não persiste; RETURN restaura carta e foco; uma nova rodada não repete o
que foi julgado na sessão. TV ao vivo, fonte inativa, categorias ocultas ou
bloqueadas, favoritos e histórico ficam fora. O gosto e os ids julgados são
somente memória de sessão. Aparência, fluidez e leitura a distância ainda
precisam ser observadas no emulador/TV.

**Lembrete de título externo:** o detalhe de Assinaturas agora oferece a mesma
ação de marcar/remover existente no Android e no Windows mesmo quando a estreia
ainda não possui linha no catálogo local. A suíte confirma identidade por
tipo/nome/ano, data de lançamento, pôster público, estado `aria-pressed`, remoção
sem sair da tela e ausência de URL de stream, chave TMDb ou credencial no registro.
Persistência e foco reais ainda precisam ser observados no emulador/TV física.

**Catálogo amplo do serviço:** cada prateleira real de Assinaturas termina em
**Ver mais**. A grade abre imediatamente com os 20 títulos já presentes e o
cliente percorre no máximo oito páginas/100 títulos, deduplica ids e para na
primeira página vazia. A suíte cobre loading não bloqueante, detalhe/volta,
RETURN, falha com conteúdo preservado, cancelamento e resposta atrasada ignorada.
O conjunto “Em breve” não recebe o botão porque não representa um provedor.

**Títulos semelhantes:** filmes combinam a coleção/franquia em ordem de
lançamento com as recomendações TMDb, sem duplicar nem repetir o título atual;
`/similar` é somente o fallback de rede. Séries usam recomendações. O card tenta
primeiro a fonte local ativa e, se não houver correspondência, abre
Assinaturas/Onde assistir. No emulador, “Duelo com Bobby Flay” abriu a tela
externa com ofertas atribuídas e **Voltar** restaurou “The Voice Brasil” na
mesma altura, com o primeiro card novamente focado. Uma tentativa anterior
coincidiu com o aviso nativo de remoção de USB e recarregou o shell; a repetição
não reproduziu o evento. A suíte cobre também fallback local, isolamento entre
fontes, pilha encadeada, foco e rolagens. Repetir esse fluxo numa TV física.

**Atalhos de serviço em Filmes/Séries:** com TMDb configurado, as duas abas
agora exibem antes dos filtros o diretório regional com até 12 logos e nomes,
como no Android. Cada alvo abre diretamente a grade completa do serviço e
mantém Filmes/Séries como tipos independentes. No emulador real da revisão
`20260824-182522`, Filmes mostrou Netflix, Amazon Prime Video, Apple TV, Google
Play Movies, Disney Plus e outros; Netflix abriu `Tudo em Netflix` com 100
capas, e RETURN recompôs as prateleiras de Assinaturas no filtro Filmes. A
suíte cobre consulta leve sem `discover`, loading vazio, tipo correto, D-pad e
geometria 1920×1080. Repetir o mesmo fluxo numa TV física e conferir latência e
leitura pelo Voice Guide.

**Abertura pela página Lembretes:** a suíte monta 14 lembretes para comprovar que
a página não herda o limite de 12 cards da Home. A linha cuja identidade encontra
um filme/série local oferece uma ação D-pad e abre os detalhes reais; RETURN volta
à mesma página e restaura o foco nessa linha. Títulos ainda ausentes continuam
visíveis, informam que não estão na biblioteca e preservam a ação Remover, sem
criar um botão que não leva a lugar algum. Foco, escala e leitura a distância
ainda precisam de observação no emulador/TV física.

**Os códigos de tecla são o risco aqui.** `RETURN` é 10009 e as teclas de
mídia precisam de `registerKey` — se um modelo usar outro código, aquela tecla
simplesmente não faz nada, sem erro nenhum.

### 2.3 Reprodução — o maior risco

- [x] Um stream HLS público abre, prepara, toca e avança o relógio no emulador.
- [ ] O áudio acompanha o vídeo.
- [x] Pausar e retomar funciona com `MediaPlayPause` no emulador.
- [x] Avançar 30 s funciona com seta direita no emulador.
- [x] Retroceder 10 s funciona com seta esquerda e `MediaRewind` no emulador.
- [x] O AVPlay enumera e aceita trocar faixa de áudio e legenda no emulador.
- [ ] Sair da reprodução volta para a lista, sem tela preta.
- [ ] Um stream inválido mostra erro em vez de travar.

Em 20/08/2026, `run-samsung-playback-smoke.ps1` instalou um package separado
(`BUROSMK001.PlaybackSmoke`) e reutilizou byte a byte o `js/player.js` do app.
Com o HLS público BipBop da Apple, a VM reportou `READY` em 1.757 ms,
`PAUSED` em 3.266 ms e retomada. Depois confirmou direita em 34.784 ms, esquerda
em 26.128 ms, Fast Forward em 57.072 ms e Rewind em 50.045 ms. Os eventos foram
injetados pelo controle ECP usando os códigos das skins Samsung (`244`, `244`,
`106`, `105`, `208`, `168`); o pipeline nativo confirmou retomada, procura de
I-frame e conclusão dos quatro seeks. No PID 8149, uma execução adicional
enumerou 2 faixas de áudio e 8 de legenda, selecionou a segunda de cada tipo e
registrou download e buffers WebVTT da legenda escolhida. As 20.244 linhas do
processo não contêm erro JavaScript/runtime.
O runner agora desinstala esse package no `finally`, inclusive quando o smoke
falha. O ciclo real terminou em 100% de desinstalação e a listagem posterior
confirmou zero ocorrência de `BUROSMK001` antes de reabrir o app normal.
A captura HMP permaneceu preta porque a camada de vídeo do AVPlay não aparece
nesse framebuffer, então som/imagem percebidos continuam sem prova visual.

A revisão mais recente inclui detalhes de filme em tela cheia, filtros e prateleiras de serviço, prateleiras públicas de Assinaturas no fim da Home, painel completo de legendas, central de avisos alinhada ao Android/Windows, abertura, Descobrir, guia e selos OMDb e Assinaturas ampliadas com marcas públicas TMDb,
lembretes externos, o catálogo **Ver mais**, abertura de títulos locais pela
página Lembretes, entrada compartilhada, credenciais TMDb v3/v4 e cache diário seguro das prateleiras
públicas de Assinaturas, além do retorno de detalhes locais à mesma Home, rolagem
e foco, da ação principal Assistir/Continuar nas séries, da atualização visual
imediata ao retornar do player e da representação correta de episódios pela série
na continuidade da Home. A carta em profundidade do Descobrir agora conserva
somente arte e volume, ocultando texto e semântica até chegar à frente; isso
elimina a sobreposição encontrada na inspeção visual. A revisão foi compilada,
assinada e instalada; abriu pelo PID 27134 e conservou o mesmo PID na segunda ativação, 30 segundos depois.
O pacote está em
`workspace/tizen-emulator-20260820-233011/.buildResult/IPTVBURO.wgt`. O pacote
tem 35 entradas, duas assinaturas e 648.682 bytes, SHA-256
`14B4BA239AE7A44727DA7FE1041321EF0649FE637DF7B08B4D4EF513D4C32413`; as 33
entradas do payload coincidem por SHA-256 com os fontes atuais e não há
teste/fixture de diagnóstico no WGT.

Evidência atual de 24/08/2026: a revisão com abertura bloqueante, cache completo,
revelação progressiva de capas, paginação remota Stalker e registro de licença
interoperável está em
`workspace/tizen-emulator-20260824-153120/.buildResult/IPTVBURO.wgt`. Foi
instalada em 100%, abriu no PID 15253 e manteve o mesmo PID após 30 segundos. O
pacote possui 39 entradas, duas assinaturas, 712.364 bytes e SHA-256
`BF5F9C489B829CFF0ACA0FB20C0405A3C71D4A228043D727B0BE2312B94763CC`; os
37/37 payloads coincidem com os fontes e não há entrada de credencial. A
inspeção posterior da janela real mostrou Home, hero, continuidade e capas
renderizadas. Série manteve `Ver detalhes`; após a rotação, filme mostrou
`Assistir` e `Ver detalhes` sem recorte. O TMDb real carregou prateleiras regionais, marcas e capas, e o
OMDb real retornou uma nota IMDb no detalhe. As credenciais permaneceram no
KeyManager, e o OMDb só é salvo depois da validação real. O topo mostrou
`Faltam 7 dias` depois que o nonce e a prova ECDSA passaram a usar Base64URL sem
padding, substituindo o estado anterior `Dispositivo não registrado`. Nenhum
valor foi colocado no fonte, log, staging ou WGT. Os 55 grupos posteriores ao
gate de versão passaram no mesmo snapshot; visual 266/266 e D-pad 343/343. A
ficha usa títulos locais do mesmo gênero quando o TMDb não responde e rejeita
explicitamente cards de outra fonte antes de desenhá-los.

O smoke Chromium executou também 960 trocas de tela em 1.356 ms. Depois de
coleta de lixo, o DOM voltou de 181 para 181 nós, sem crescimento de nós CDP,
listeners ou documentos e com 297 KiB adicionais de heap coletado. Isso reduz
o risco de degradação numa sessão longa, mas a medição ainda precisa ser repetida
no Web Engine de uma TV física.

O catálogo de escala grava 12.000 títulos pelo mesmo `replaceSourceCatalogue`
usado por M3U/Stalker. No Chromium, a escrita levou 1.059 ms, o event loop recebeu
90 ticks com intervalo máximo de 35 ms, a Home levou 190 ms e manteve somente
101 itens editoriais em memória. A categoria de 1.000 títulos abriu 200 registros,
retomou o índice em blocos de 200 sem duplicação e manteve no máximo 200 cards no
DOM. Esses números são referência de regressão no PC, não promessa
de tempo idêntico no processador de uma TV.

**Retorno de Assinaturas:** abrir um título ou **Ver mais** depois de rolar uma
prateleira agora começa no topo com Voltar focado. RETURN restaura o provedor,
o card, a rolagem vertical e a rolagem horizontal exatos, mesmo quando o mesmo
título aparece em dois serviços. Isso impede que um índice de foco antigo abra
o detalhe no fim da página e esconda Ribbon, título e ações.

**Stalker e guia do player:** a inspeção ampliada encontrou a explicação dos
campos opcionais Stalker sobreposta ao título e o rodapé do guia ao vivo exibindo
`undefined` no lugar do canal. O bloco Stalker agora empilha título e explicação;
o modelo de reprodução conserva o nome do item. O programa atual focado também
recuperou fundo marfim e texto escuro, porque a regra de “atual” sobrescrevia o
contraste do foco. As três condições possuem regressão visual e o nome do canal
tem também regressão D-pad.

A navegação horizontal não depende mais apenas do objeto moderno de opções de
`scrollIntoView`: ao focar um destino fora da largura, o app mede o card e desloca
explicitamente a Ribbon ou prateleira correspondente. A regressão simula um engine
antigo no qual `scrollIntoView` não move a linha e confirma que o décimo segundo
card público fica visível e focado depois de RETURN.

**Retorno dos detalhes locais à Home:** filmes, séries e canais abertos por qualquer
prateleira local agora conservam em memória o mesmo modelo da Home, sua rolagem
vertical, a posição horizontal da prateleira e o card de origem. RETURN restaura
esses quatro elementos sem percorrer novamente o IndexedDB. A regressão integrada
também atravessa falha/retry assíncrono, trailer, QR, pessoa e crédito externo antes
de voltar, para proteger o fluxo mais profundo usado por Android e Windows.

**Ação principal de série:** o topo do detalhe agora oferece Assistir/Continuar
com temporada e episódio. A escolha percorre a ordem da série, prefere o primeiro
episódio com retomada e inicia diretamente na posição exibida; sem retomada usa o
primeiro episódio. Série vazia não ganha ação inerte. Quatro regressões cobrem a
seleção, o fallback, o seek real entregue ao AVPlay e os cinco idiomas.

**Atualização ao fechar o player:** depois de RETURN ou do fim natural, filmes,
episódios, Home, categorias e downloads VOD são recompostos a partir do checkpoint
recém-gravado. O percentual, a marca de assistido e o próximo destino de Continuar
mudam imediatamente, preservando o controle focado, `scrollTop` e `scrollLeft` da
linha. Ao vivo não dispara recomposição porque não possui checkpoint.

O cache de Assinaturas foi exercitado sem rede na segunda visita: a resposta do
mesmo dia reaparece imediatamente e é separada por região, filtro e idioma.
Data vencida e JSON corrompido viram miss silencioso; resposta vazia não apaga
uma válida. O registro contém somente id/título/tipo/ano/data/pôster público e a marca
do serviço restrita a `image.tmdb.org/t/p/w92`; formato antigo é descartado e
nunca chave TMDb, oferta, sinopse ou URL externa.

As mesmas fontes Web agora são inspecionadas por um smoke reproduzível em Chromium
na área lógica 1920×1080. Duzentas e cinquenta e oito verificações e 78 capturas percorrem
idioma, carregamento, aviso legal, criação de perfil, Home demonstrativa e real,
categorias e detalhes de Ao Vivo, catálogo, detalhes de filme e série, episódios,
Descobrir, Pesquisa, Minha BURO, Continuar, Histórico, Lembretes, Notificações,
Fontes, Perfis, Configurações, edição de perfil, gerenciamento e formulário
Xtream, PIN parental, categorias, TMDb/OMDb e seus guias até o fim,
armazenamento, downloads, Assinaturas/Ver mais/detalhe, pessoa e
compartilhamento, M3U remoto/USB, Stalker, foto USB, retomada, download em lote,
licença, cache de capas em andamento com Pausar, player VOD/ao vivo, menus de
faixas/velocidade/guia, erro e trailer. Uma
matriz adicional repete Configurações, Stalker, retomada e erro do player em
PT-BR, EN, DE, IT e ES, e
medem viewport, overflow, área segura, alvos,
foco, interseções e quadros PNG não vazios. Aviso, sino e selo ficam sem
interseção. A Home sem fonte apresenta os mesmos três trilhos demonstrativos e
14 conceitos fictícios do Android, com progresso, paisagem/pôster e detalhe
individual sem reprodução. A inspeção também exige Ribbon/topbar visíveis no
detalhe e `scrollTop` zero no painel fixo. O shell usa limites absolutos para
Ribbon, topbar e conteúdo; o CDP captura somente o viewport, sem recompor camadas
fixas numa superfície maior que a TV. Isso valida a
composição HTML/CSS estática; animação, vídeo e percepção de imagem/som no
framebuffer nativo continuam pendentes. O painel de legendas foi renderizado
separadamente pelas mesmas fontes em 1920×1080; ficou entre `x=64` e `x=1817`,
com 1753 px de largura, e exibiu as quatro dimensões, cinco cores e fundo sem recorte.

Codecs variam por modelo e ano. Um stream que toca numa TV de 2024 pode ser
recusado numa de 2018.

### 2.4 Download USB

Requer um pendrive ou HD conectado à TV.

- [ ] Sem USB, a seção Downloads explica que falta um dispositivo.
- [ ] Sem USB, o botão **Baixar** não aparece nos detalhes.
- [ ] Com USB conectado, o botão aparece.
- [ ] Baixar um filme cria a pasta `IPTV BURO` na raiz do dispositivo.
- [ ] O arquivo é gravado como `.part` e renomeado ao concluir.
- [ ] O nome do arquivo vem da identidade (`movie-42.mp4`), sem credenciais.
- [ ] **Arrancar o pendrive durante o download** pausa em vez de acumular
      falhas.
- [ ] Reconectar permite continuar.
- [ ] Canal ao vivo não oferece download.

**Verifique o arquivo num computador depois.** É o teste final de que a
gravação foi íntegra.

### 2.5 Licença

- [ ] Numa TV nunca registrada, o app se registra sozinho e ganha o período de
      teste.
- [ ] O código do aparelho aparece em Configurações → Licença.
- [ ] Uma chave de ativação válida ativa a assinatura.
- [ ] Uma chave inválida mostra erro claro.
- [ ] Com a TV sem internet, o app abre e explica que precisa revalidar.

**O `tizen.keymanager` é o risco.** Se ele recusar o privilégio, a identidade
do dispositivo não é criada e nada de licença funciona — o app deve continuar
abrindo, com a licença indisponível.

### 2.6 Catálogo grande

O motivo de existir a escrita em lote. Use uma lista sua com dezenas de
milhares de canais.

- [ ] A importação termina.
- [ ] Durante a importação, o D-pad continua respondendo.
- [ ] A Home aparece sem esperar o catálogo inteiro.
- [ ] Navegar entre seções não engasga.
- [ ] Depois de meia hora navegando, a interface continua fluida.

**O último item é o teste de memória.** Se a TV ficar lenta com o tempo, algum
cache está crescendo sem teto — o `memory-ceiling.test.js` cobre os de arte,
mas só esses.

---

### Evidência do emulador — revisão 20260824-184638

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 5187 na abertura e após 30 segundos.
- Home: Hero, backdrop, sinopse e capas do primeiro quadro visíveis, sem estado
  de carregamento residual.
- Pacote: 716.077 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; zero padrão de chave de
  metadados embutida.
- SHA-256:
  `3BBD2EC7683EE1F5528D9C6D697A35AA77F103ADC90C4F7CDBBDD3631008AC5C`.
- Evidência visual local:
  `workspace/evidence-samsung-emulator-20260824-1848.png`.

Isso confirma a VM, mas não substitui os itens de TV física acima.

### Evidência do emulador — revisão 20260824-190924

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 6674 na abertura e após 30 segundos.
- Catch-up Xtream: contrato, ficha, guia sobre o player, D-pad e URL tardia
  aprovados com fixtures sintéticas; validar o fuso com um provedor autorizado
  em TV física.
- Pacote: 718.439 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; zero literal com forma de
  segredo embutido.
- SHA-256:
  `7FAF3532D4889E2B658FF3008C775D1CFB0E07124CBA9B2B9B6849677E73B82C`.

Isso confirma a VM, mas não substitui os itens de TV física acima.

### Evidência do emulador — revisão 20260824-192911

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 7219 na abertura e após 30 segundos.
- Continuar/Histórico: remoção individual, busca normalizada, confirmação e
  limpeza isolada por perfil aprovadas com IndexedDB real e D-pad.
- Regressões: 55/55 grupos pós-gate; D-pad 358/358; visual 274/274;
  biblioteca 23/23; armazenamento 12/12.
- Pacote: 720.662 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero literal com forma de chave embutido.
- SHA-256:
  `2416D7417DEF28BCB0E80D918735033631ED07045F797CD9DB8C3DC0C690A826`.

O gate `version-parity.test.js` permanece 4/5 porque Android/Samsung estão em
3.0.1 e Windows em 3.3.0. Isso confirma a VM, mas não substitui os itens de TV
física acima.

### Evidência do emulador — revisão 20260824-195253

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 8268 na abertura e após 30 segundos.
- Comparação visual: janelas Samsung e Windows inspecionadas lado a lado; o Hero
  Samsung passou a 51,6% da área útil, com três linhas de sinopse e degradê até
  a primeira prateleira.
- Primeiro quadro: Hero, título longo, `Assistir`, `Ver detalhes` e primeiro
  trilho permaneceram visíveis, sem scroll inicial nem overflow.
- Boot bloqueante: 14/14 etapas/progresso e 12/12 preparação da Home; shell só
  aparece após catálogo, TMDb configurado, Hero e capas visíveis resolverem.
- Regressões: 55/55 grupos pós-gate; D-pad 361/361; compatibilidade antiga
  38/38; idiomas 21/21; visual 286/286.
- Pacote: 721.287 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero literal com forma de chave embutido.
- SHA-256:
  `5577C5156B6AD4CEBDB0709472B1877EBF327A781F027AC6ECC7E44D6A8EF9A2`.

O gate `version-parity.test.js` permanece 4/5 porque Android/Samsung estão em
3.0.1 e Windows em 3.3.0. Isso confirma a VM e a janela do emulador, mas não
substitui a validação de overscan, contraste e desempenho numa TV física.

### Evidência do emulador — revisão 20260824-200843

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 9318 na abertura e após 30 segundos.
- Comparação visual: fichas Windows/Samsung abertas lado a lado com catálogo
  real; a sinopse Samsung deixou o tamanho padrão e passou a 24 px com
  entrelinha 1,45 e largura máxima de 1.040 px.
- Primeiro quadro real: pôster, backdrop, fatos, nota, sinopse, progresso,
  `Assistir`, ações, créditos e elenco permaneceram visíveis sem recorte.
- Regressões: 55/55 grupos pós-gate; D-pad 361/361; compatibilidade antiga
  38/38; idiomas 21/21; visual 287/287.
- Pacote: 721.285 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero literal com forma de chave embutido.
- SHA-256:
  `26F94865E593191E09F64AC31774C744BD16664B75FEFF07B7E896DF0F021962`.

O gate `version-parity.test.js` permanece 4/5 porque Android/Samsung estão em
3.0.1 e Windows em 3.3.0. A inspeção local confirma a janela do emulador, mas
Voice Guide, overscan e distância real de leitura ainda exigem TV física.

### Evidência do emulador — revisão 20260824-202626

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 10312 na abertura e após 30 segundos.
- Comparação real: Windows e Samsung foram abertos em Filmes; a Samsung agora
  exibe `Buscar neste catálogo…` antes de ano, nota, gênero e serviço, mantendo
  capas e primeiro bloco no quadro 1920×1080.
- Busca: texto sem acentos, debounce de 300 ms, composição com os demais filtros,
  paginação IndexedDB e estado vazio sem retorno indevido às categorias.
- Regressões: 55/55 grupos pós-gate; D-pad 364/364; compatibilidade antiga
  38/38; idiomas 21/21; visual 288/288.
- Pacote: 722.213 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero literal com forma de chave embutido.
- SHA-256:
  `BE0185710DCC939F832F51620DBE7379E9E70740E92549907EB7770C2180DCF1`.

O gate `version-parity.test.js` permanece 4/5 porque Android/Samsung estão em
3.0.1 e Windows em 3.3.0. Digitação com o teclado virtual Samsung, Voice Guide,
overscan e desempenho de busca no hardware ainda precisam de TV física.

### Evidência do emulador — revisão 20260824-203834

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 10851 na abertura e após 30 segundos.
- Comparação real: a faixa de Filmes agora mostra `Ao vivo`, `Filmes`, `Séries`
  e `Buscar neste catálogo…` na mesma linha, como no Windows. Atalhos de serviço,
  filtros, contador e capas permanecem visíveis no primeiro quadro 1920×1080.
- Semântica e D-pad: três tabs, exatamente uma com `aria-selected=true`; trocar
  pela aba atualiza também a seleção correspondente da Ribbon.
- Regressões: 55/55 grupos pós-gate em 156,5 segundos; D-pad 366/366;
  compatibilidade antiga 38/38; idiomas 21/21; visual 288/288.
- Pacote: 722.728 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero credencial literal embutida.
- SHA-256:
  `D945B270EAC8870F4F4B7BE1150F2C50D650628F8608B5B7A606753323865EB1`.

O gate `version-parity.test.js` permanece 4/5 porque Android/Samsung estão em
3.0.1 e Windows em 3.3.0. Teclado virtual, Voice Guide, overscan, desempenho e
AVPlay com fontes autorizadas ainda precisam ser repetidos numa TV física.

### Evidência do emulador — revisão 20260824-205359

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 11855 na abertura e após 30 segundos.
- App-control real: o remetente isolado da revisão `20260824-205512` encontrou o
  filtro `iptvburo`, entregou uma identidade pública sintética ao pacote de
  produção e recebeu o callback real de `launchAppControl`.
- Consumidor real: o app aberto mostrou `Título compartilhado não encontrado`,
  seta direita moveu o foco de `Procurar novamente` para `Fechar aviso` e ENTER
  fechou o painel sem reiniciar a sessão.
- Regressão corrigida: após sete segundos fora de foco — além do limite antigo
  de cinco segundos — o app foi refocado e o pedido antigo não reapareceu.
- Limpeza: o pacote temporário `BUROCTL001.AppControlSmoke` foi desinstalado em
  100% no bloco de encerramento.
- Regressões: 55/55 grupos pós-gate em 154,7 segundos; link recebido 12/12;
  remetente 9/9; D-pad 366/366; visual 288/288; compatibilidade antiga 38/38;
  idiomas 21/21.
- Pacote: 723.104 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero credencial literal embutida.
- SHA-256:
  `2A29565FBF5E5D55A417BF2CCA1B528F2FCC80FF0ED0B959474C1F9C3540C6A7`.

A associação e a entrega quente deixaram de ser pendência do emulador; a prova
fria registrada logo abaixo fechou a outra metade. Ainda é necessário repetir
ambas em TV física e entregar um link que corresponda positivamente a um título
da fonte autorizada do aparelho.

### Evidência de App Control frio — revisão 20260824-210854

- O remetente foi instalado antes do ciclo e não contém fonte, stream ou segredo.
- O runner enviou `KEY_POWER=116`, extraído da skin Samsung instalada; o serial
  `emulator-26101` ficou offline e o processo da VM encerrou sem force stop.
- A mesma VM foi relançada por `em-cli launch`, voltou com o mesmo serial e sem
  resetar o disco. A janela do emulador mudou de id, confirmando o novo processo.
- Somente depois do boot o remetente foi iniciado. O filtro `iptvburo` abriu o
  pacote de produção frio e o primeiro quadro do app já mostrou o aviso do título
  público recebido.
- Seta direita e ENTER fecharam o aviso; o app preservou perfil, fonte, catálogo,
  chaves do KeyManager e a tela Filmes existentes antes do Power.
- O pacote temporário foi desinstalado ao final. O IPTV BURO iniciado pelo link
  manteve o PID 3690 por 30 segundos.
- Runner estático: 10/10; consumidor: 12/12; regressão completa: 55/55 grupos em
  157,6 segundos. O WGT de produção continua sendo a revisão assinada
  `20260824-205359`, pois o modo frio altera somente automação/testes.

A abertura fria deixa de ser pendência do emulador. A prova ainda deve ser
repetida no Smart Hub de uma TV física e com correspondência positiva de um
título pertencente à fonte autorizada do próprio aparelho.

### Evidência positiva e recuperação do player — revisão 20260824-220051

- Um título já visível na fonte autorizada foi transformado somente em
  identidade pública (`id`, título e ano), sem stream, fonte ou credencial.
- O App Control abriu os detalhes corretos tanto com o app quente
  (`20260824-220349`) quanto após Power/boot completo (`20260824-220437`). Os
  dois fluxos retornaram `APPCONTROL_LAUNCH_PASS`; o modo frio também registrou
  `STAGE_STARTED` e `STAGE_FILTER_FOUND`.
- O runner agora escolhe uma porta livre, sincroniza o `access origin` do WGT
  temporário e usa um receptor Node efêmero. A URI completa não é impressa e o
  pacote remetente é removido no `finally`.
- O player Samsung ganhou uma tentativa automática de 1,5 segundo somente para
  `PLAYBACK_CONNECTION`. A segunda falha abre o painel manual; fonte ausente e
  formato incompatível nunca entram no automático. Sair do player cancela o
  timer e o bloqueio por ENTER longo é removido durante a recuperação.
- Regressão: 56/56 grupos pós-gate em 138,7 segundos; recuperação 8/8,
  integração 17/17, D-pad 366/366, visual 288/288, compatibilidade antiga
  39/39 e AVPlay 13/13.
- WGT: `workspace/tizen-emulator-20260824-220051/.buildResult/IPTVBURO.wgt`,
  724.157 bytes, 40 entradas, 38 payloads + duas assinaturas, 38/38 idênticos
  ao staging, nenhum arquivo inseguro e zero literal de credencial detectado.
- SHA-256:
  `543E1E0C6AE3805249C040B44C4CF9EC38AC8551D1BDAC3A4F5A8FE0E0AEACE0`.
- A instalação manteve PID 4565 por 30 segundos. Depois da abertura fria por
  App Control, o processo de produção manteve PID 3652 por outros 30 segundos.

Essa evidência fecha a correspondência positiva no emulador, mas não substitui
imagem, som, codec, USB, KeyManager e desempenho percebido em TV física.

### Paridade do relógio 12/24 horas — revisão 20260824-224036

- Configurações passou a mostrar `24 horas` e `12 horas` como escolhas diretas,
  focáveis e com `aria-pressed`; 24 horas permanece o padrão.
- O modo de 12 horas usa `h:mm AM/PM`, como o Windows, atualiza o cabeçalho no
  mesmo ENTER e fica salvo depois de fechar e abrir o aplicativo.
- O teste comportamental registrou primeiro a falha esperada por controles
  ausentes e depois passou 10/10. Idiomas passaram 21/21, armazenamento 27/27,
  D-pad 366/366 e Chromium visual 288/288.
- Regressão completa: 57/57 grupos posteriores ao gate de versão em 140,8
  segundos. O gate global continua 4/5 pela diferença conhecida Android/Samsung
  3.0.1 versus Windows 3.3.0.
- WGT: `workspace/tizen-emulator-20260824-224036/.buildResult/IPTVBURO.wgt`,
  724.763 bytes, 40 entradas, 38 payloads + duas assinaturas, 38/38 idênticos ao
  staging, nenhum arquivo inseguro e zero padrão literal de credencial.
- SHA-256:
  `00A7E0C6634419932C05E91C86B2CF6E0048A713CAF7426C79DCDDA4D1AEB194`.
- Instalado no `emulator-26101`; duas consultas separadas por 30 segundos
  devolveram o mesmo PID 3983.

O formato está provado no Web Runtime do emulador. Legibilidade, overscan e
resposta do D-pad continuam pendentes numa TV Samsung física.

### Catálogo sem cópias repetidas — revisão 20260824-230918

- Configurações ganhou a mesma preferência do Windows para mostrar um só card
  por título; ela nasce ligada, é focável, expõe `aria-pressed`, persiste e pode
  devolver todas as variantes sem apagar o catálogo.
- Categoria e prateleira principal usam a mesma regra. Qualidade, codec,
  dublagem e rótulo de serviço são decoração; tipo, sequência, subtítulo real e
  ano estruturado evitam unir obras diferentes e remakes homônimos.
- O teste TDD passou 14/14 após quatro REDs esperados. A regressão que revelou o
  seletor de anos passou 15/15; idiomas 21/21, armazenamento 27/27, motor antigo
  39/39 e D-pad 366/366.
- Regressão completa: 58/58 grupos posteriores ao gate em 143,3 segundos. O
  gate global permanece 4/5 somente pela versão Android/Samsung 3.0.1 versus
  Windows 3.3.0.
- WGT: `workspace/tizen-emulator-20260824-230918/.buildResult/IPTVBURO.wgt`,
  726.789 bytes, 40 entradas, 38 payloads + duas assinaturas, 38/38 idênticos ao
  staging, nenhum caminho inseguro e zero padrão de atribuição literal de
  credencial.
- SHA-256:
  `B3B64A7EBF41BF8C10E7600921A0DB06356DBB9570C13EAD1B4148FC3561FB77`.
- Instalado no `emulator-26101`; o PID 5239 permaneceu igual após 30 segundos.

Essa prova cobre o Web Runtime e a paginação IndexedDB do emulador. Volume real
de catálogo, legibilidade e resposta percebida continuam pendentes na TV física.

### Região de streaming por perfil — revisão 20260824-233124

- Configurações mostra BR, PT, US, DE e IT por D-pad, com seleção visual e
  `aria-pressed`; cada perfil conserva sua própria escolha.
- Preferências antigas migram a região global para o perfil ativo. Perfis sem
  escolha começam em BR e excluir um perfil remove também sua entrada regional.
- Troca de perfil/região invalida Home pública, diretórios, Assinaturas e índice
  de serviço. Respostas antigas não podem substituir o país novo; Filmes e
  Séries possuem identidades de índice distintas.
- O teste dedicado passou 16/16. Armazenamento passou 27/27, idiomas 21/21,
  compatibilidade ES5 39/39 e D-pad 366/366.
- Regressão completa: 59/59 grupos posteriores ao gate em 150,4 segundos. O
  gate global permanece 4/5 pela diferença conhecida Android/Samsung 3.0.1
  versus Windows 3.3.0.
- WGT: `workspace/tizen-emulator-20260824-233124/.buildResult/IPTVBURO.wgt`,
  728.268 bytes, 40 entradas, 38 payloads + duas assinaturas, 38/38 idênticos ao
  staging, nenhum caminho inseguro e zero atribuição literal de credencial com
  alta entropia.
- SHA-256:
  `21302894D10A607BD65124AA409CF5CBFE1812584AF6F526337D974E969456E4`.
- Instalado no `emulator-26101`; o PID 6251 permaneceu igual após 30 segundos.

Essa prova cobre o estado e as transições no emulador. Conferir ofertas reais de
cada região, overscan, legibilidade e resposta percebida continua pendente numa
TV Samsung física.

### Receber título por pareamento — revisão 20260824-234859

- Configurações mostra “Receber título” no primeiro quadro 1920×1080 e a ação é
  focável pelo D-pad.
- O código solicita `open_title`; QR/endereço continuam disponíveis e a terceira
  etapa explica link de título, não chave de API.
- Link público oficial e URI privada registrada são validados e resolvidos no
  IndexedDB completo da fonte/perfil ativos. URL externa e identidade inválida
  falham fechadas; nenhum payload entra nas preferências e nenhuma URL de stream
  ou credencial é transportada.
- Teste dedicado: 11/11. Visual Chromium: 289/289. D-pad: 366/366. ES5: 39/39.
  Idiomas: 21/21 com 704 chaves por idioma. Regressão: 60/60 grupos posteriores
  ao gate em 166,8 segundos. Servidor de licença/relay: 204/204.
- Gate global: 4/5; única falha é Android/Samsung 3.0.1 versus Windows 3.3.0.
- WGT: `workspace/tizen-emulator-20260824-234859/.buildResult/IPTVBURO.wgt`,
  729.350 bytes, 40 entradas, 38 payloads + duas assinaturas, 38/38 idênticos ao
  staging, nenhum caminho inseguro e zero atribuição de credencial com padrão de
  chave/token.
- SHA-256:
  `9E979C5CAE40A0CE901EDE562E49C431077A83175DD2224277C9B822BD861C19`.
- Instalado no `emulator-26101`; o PID 6827 permaneceu igual após 30 segundos.

A captura sintética de Configurações foi guardada em
`workspace/visual-20260824-234859/settings.png`. A automação visual nativa do
emulador não estava disponível nesta sessão; por isso essa evidência Chromium
não substitui inspeção e teste fim a fim numa TV Samsung física. A copy móvel
especializada também requer deploy do Worker antes de aparecer em produção.

### Consulta prévia da chave — revisão 20260825-001055

- Digitar seis ou mais caracteres aguarda 600 ms e consulta sem resgatar.
- A tela mostra `available`, `yours`, `in_use`, `expired` e `unknown` com texto
  localizado; somente `available`/`yours` habilitam “Usar chave”.
- Editar a chave invalida timer e callback anteriores. A resposta é limitada,
  não entra no KeyManager e não altera a decisão de acesso.
- TDD: cliente 9/9 e interface 9/9. Contrato Worker: 4/4. ES5: 39/39.
  Idiomas: 21/21 com 711 chaves. Acessibilidade: 15/15. D-pad: 366/366.
- Visual Chromium: 291/291; captura 1920×1080 em
  `workspace/visual-20260825-000645/licence-key-info.png`.
- Regressão: 62/62 grupos pós-gate em 167,1 segundos. Gate global: 4/5 pela
  diferença Android/Samsung 3.0.1 versus Windows 3.3.0.
- WGT: `workspace/tizen-emulator-20260825-001055/.buildResult/IPTVBURO.wgt`,
  731.877 bytes, 40 entradas, 38 payloads + duas assinaturas, 38/38 idênticos ao
  staging, nenhum caminho inseguro e zero atribuição de credencial com dígitos.
- SHA-256:
  `9B4086D0A5FC347AF91FFC317915102E8253DE44C97D5C39D525456163EAF1A5`.
- Instalado no `emulator-26101`; o PID 7424 permaneceu igual após 30 segundos.

Essa evidência cobre contrato, composição e estabilidade no emulador. Repetir
os cinco estados e um resgate real numa TV física antes de release.

### Evidência do emulador — revisão 20260824-194551

- Build, assinatura e instalação: concluídos.
- VM: `emulator-26101`.
- PID: 7762 na abertura e após 30 segundos.
- Continuar assistindo: continuar da posição, assistir do início e remover são
  ações independentes por D-pad; séries reproduzem o episódio persistido, não o
  card pai.
- Segurança: uma linha de outro perfil é recusada e a resolução Xtream continua
  tardia, sem credencial no DOM ou no armazenamento comum.
- Regressões: 55/55 grupos pós-gate; biblioteca 29/29; D-pad 361/361;
  compatibilidade antiga 38/38; idiomas 21/21; visual multilíngue 284/284.
- Pacote: 721.248 bytes, 39 entradas, 37 payloads + duas assinaturas.
- Integridade: 37/37 payloads idênticos ao staging; nenhum teste/diagnóstico;
  zero literal com forma de chave embutido.
- SHA-256:
  `C02FB322897616F813D132A5BAC84D4DA2291247EC81B333A8D6F2D18B24BB9D`.

O gate `version-parity.test.js` permanece 4/5 porque Android/Samsung estão em
3.0.1 e Windows em 3.3.0. Isso confirma a VM, mas não substitui os itens de TV
física acima.

### XMLTV e Home sazonal — revisão 20260827-095303

- XMLTV M3U: `url-tvg`/`x-tvg-url` e `tvg-id` passam pelo cofre e pelo parser
  limitado; falha do guia conserva o canal reproduzível.
- Home sazonal: busca o catálogo inteiro na varredura existente, limita 18
  correspondências, mostra selo localizado e não some quando o item também é
  Hero.
- Testes: sazonal 11/11, integração 5/5, ES5 41/41, Chromium 295/295 em
  1920×1080 e 67 grupos funcionais restantes aprovados em 149,3 segundos.
- Gate global: 4/5; Android/Samsung 3.6.0 e Windows 3.6.2.
- WGT: 743.487 bytes, 42 entradas, duas assinaturas, 40/40 payloads idênticos,
  zero caminho inseguro, zero artefato de teste e zero atribuição de credencial.
- SHA-256:
  `52FCEE8535771F33F1D42FE6EDF738D92DA719C6D3E94AEFE6D8D869BB65DA9A`.
- Instalado e aberto no `emulator-26101`; PID 4039 permaneceu estável.

A captura sintética está em
`workspace/visual-20260827-seasonal/seasonal-home.png`. Repetir XMLTV, overscan,
Voice Guide, D-pad e reprodução em TV física antes de alterar a declaração
`hardware: NOT_VALIDATED`.

## 3. Como registrar o que achar

Para cada falha, anote:

1. Modelo e ano da TV (Configurações → Suporte → Sobre).
2. O que você fez, na ordem.
3. O que aconteceu, e o que era esperado.
4. A saída de `sdb dlog | Select-String "IPTVBURO"` no momento.

Sem o modelo, um defeito de codec parece um defeito do app. São coisas
diferentes e se resolvem de formas diferentes.

---

## 4. O que fazer com o resultado

Enquanto este roteiro não for executado, `platforms.json` deve continuar
declarando `hardware: NOT_VALIDATED` para `samsung-tizen`, e as capabilities
não medidas continuam `false`.

Uma capability só passa a `true` depois de medida no aparelho. `dash`, `seek`,
`hdr`, `pip` e `offline` estão desligadas por essa razão, não por não terem
sido implementadas.

### Evidência de carregamento — 28 de agosto de 2026

- versão Samsung: 3.7.0;
- carregamento: 12 capas locais, duas fileiras móveis e porcentagem sincronizada
  dentro da barra;
- regressão específica: 19/19;
- compatibilidade ES5/CSS antigo: 44/44;
- matriz Chromium 1920×1080: 299/299;
- suíte integral: 65 grupos e 1.769 verificações em 185,4 segundos;
- WGT: `workspace/tizen-emulator-20260828-192135/.buildResult/IPTVBURO.wgt`;
- pacote: 783.513 bytes, 45 arquivos, duas assinaturas, zero arquivo de
  desenvolvimento e 43/43 payloads idênticos aos fontes;
- SHA-256: `F3043FAD7A8C6099111F2EC3BA2FF53EB108E3BC7BFEB769557CD4C54393596B`;
- emulador: `emulator-26101`, app aberto no PID 3921.

A medição final de fluidez, overscan e uso de GPU permanece pendente na TV
Samsung física, por isso o status de hardware continua `NOT_VALIDATED`.

### Evidência do seletor de serviços — 28 de agosto de 2026

- diretório de até 12 serviços fechado por padrão num único seletor;
- lista vertical sobreposta, ancorada e navegável por D-pad;
- ano, nota, diretório, gênero e serviço na mesma faixa compacta;
- filtro/âncora: 12/12 + 15/15; D-pad: 367/367; visual: 302/302;
- suíte integral: 65 grupos, 1.772 verificações em 174,2 segundos;
- capturas: `workspace/visual-20260828-service-selector/movie-categories.png`
  e `workspace/visual-20260828-service-selector/movie-service-selector.png`;
- WGT: `workspace/tizen-emulator-20260828-231921/.buildResult/IPTVBURO.wgt`;
- pacote: 784.087 bytes, 45 arquivos, duas assinaturas, zero arquivo de
  desenvolvimento e 43/43 payloads idênticos aos fontes;
- SHA-256: `4B36F1E44A86E4A60B6D46FA6D2DC24347BAAF69E79835EFCF53EE0A04582244`;
- emulador: `emulator-26101`, PID estável 6965.

### Qualidade visual FHD, 4K e 8K — 29 de agosto de 2026

- A superfície da aplicação permanece em 1920×1080 e 16:9, como exige o
  runtime Samsung TV; mudar a geometria para 3840×2160 quebraria o contrato do
  AVPlay e não produziria uma interface 4K nativa.
- O app consulta primeiro `tizen.systeminfo`/`PANEL`, depois `DISPLAY`, e usa
  `devicePixelRatio` apenas como fallback. O resultado é exposto em
  `data-display-quality=fhd|uhd|8k` para inspeção, sem dados pessoais.
- Em FHD, pôster/fundo TMDb permanecem em `w342`/`w1280`. Em UHD/8K, passam
  para `w780`/`original`; marcas de serviço passam a `w185` e retratos do elenco
  a `w342`. Os ícones da interface continuam SVG e não rasterizam ao ampliar.
- Em 8K, o teto de imagens é o mesmo da UHD porque a superfície Web já foi
  composta em 1920×1080. Pedir bitmaps maiores aumentaria memória sem criar
  pixels novos na composição final.
- Regressão dedicada: 13/13; compatibilidade ES5/CSS antigo: 45/45; cliente
  TMDb: 44/44; D-pad: 368/368; matriz visual isolada: 305/305. A suíte integral
  final passou com código 0 em 189,5 segundos.
- Uma execução visual paralela ao D-pad ultrapassou uma vez o teto sintético de
  listeners (320); repetida isoladamente, terminou com zero listener retido.
- WGT instalado:
  `workspace/tizen-emulator-20260829-093834/.buildResult/IPTVBURO.wgt`;
  794.451 bytes, 46 entradas, duas assinaturas, 44/44 payloads idênticos aos
  fontes e SHA-256
  `49BA82FD9ADE42EF42D3A2050E5A23FF66FCE8431A07E8787529BAD02515AFE6`.
- Emulador `emulator-26101`: PID 4529 permaneceu igual após 10 segundos.

Ainda é obrigatório conferir numa TV física FHD, 4K e, se suportada, 8K:
nitidez a 2–3 m, fluidez das capas, overscan e pressão de memória. A qualidade
do vídeo é independente da interface e depende do stream, codec e decoder do
modelo; esta rodada não habilita `hdr` nem altera capabilities sem medição.

### Auditoria final local do candidato 3.7.0 — 29 de agosto de 2026

- A faixa de ações do player foi compactada para que todos os botões na tela,
  inclusive `Leituras`, caibam no primeiro quadro sem depender das teclas
  coloridas do controle.
- O botão de download de temporada ganhou largura estável e não quebra em duas
  linhas; temporadas continuam antes de elenco e títulos parecidos.
- O cabeçalho deixa de repetir a versão quando já há contagem de fontes/itens,
  reduzindo truncamento sem retirar a versão completa de Configurações.
- Foram removidas duas cópias redundantes de três helpers internos do player.
- Suíte integral: código de saída 0 em 192,1 s; visual 309/309; D-pad 372/372;
  downloads USB 51/51; compatibilidade ES5/CSS 45/45.
- WGT: `workspace/tizen-emulator-20260829-195008/.buildResult/IPTVBURO.wgt`,
  797.209 bytes, 46 entradas, duas assinaturas, 44/44 payloads idênticos ao
  staging e nenhum arquivo de desenvolvimento, certificado privado ou segredo.
- SHA-256:
  `530188FD7BF725A29EE4DC289F1B3B177F6B02EEFFBFF02617BFED0036656418`.
- Instalado no `emulator-26101`; PID 7345 permaneceu estável por 15 segundos.

O candidato está pronto localmente para o Auto Pre-Test, mas não está certificado:
validação em TV física, USB real, fonte de QA, PPTX oficial, escolhas comerciais
e envio autenticado ao Seller Office continuam externos a esta auditoria.
