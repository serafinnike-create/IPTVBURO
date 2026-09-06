# Ambiente de desenvolvimento — Samsung Tizen

Guia para montar do zero o ambiente no Windows e rodar o app na TV.
Escrito para quem nunca fez app de TV.

Nada aqui foi validado em TV física ainda. Trate como preview.

---

## 1. Como funciona um app de TV Samsung

Antes de instalar, entenda o modelo — ele explica quase todos os erros que
você vai encontrar.

Um app Tizen de TV **é uma página web**: HTML, CSS e JavaScript. Não é
Kotlin, não é Android. Por isso nada do app Android do IPTV BURO é
reaproveitado como código aqui.

Três diferenças que quebram quem vem da web:

| Web comum | TV Samsung |
| --- | --- |
| Mouse e toque | Somente D-pad (setas + ENTER + RETURN) |
| `<video>` | `AVPlay`, engine nativa da Samsung |
| Layout responsivo | Área lógica fixa de **1920×1080** |

O ponto mais confuso para iniciantes: **o vídeo da AVPlay não é desenhado
pela página**. A TV desenha o vídeo numa camada de hardware *atrás* do
HTML. Se o `body` tiver fundo opaco, você ouve o áudio e vê tela preta.
Por isso o `body` do projeto é transparente, e o fundo fica numa `<div>`
que escondemos ao reproduzir.

O app é empacotado num arquivo `.wgt` (um ZIP assinado), instalado na TV
pela rede via `sdb`.

---

## 2. O que já existe na sua máquina

Verificado nesta máquina (atualizado em 20/08/2026):

| Item | Estado |
| --- | --- |
| Node.js v24.13.1 | OK (só ferramentas; o app não usa) |
| Git | OK |
| Java 8 (1.8.0_501) | OK — atende ao mínimo oficial (JDK 1.8+) |
| Tizen Studio | **Instalado** em `C:\tizen-studio` |
| Tizen CLI 2.5.25 / sdb 4.2.25 | OK — já no PATH do usuário |
| Plataformas tizen-2.4 … 10.0 | OK |
| TV Extensions (Samsung) | OK — `tv-samsung` até Tizen 10.0 |
| Samsung Certificate Extension | OK — Certificate Manager presente |
| Certificado de assinatura | OK — perfil `iptvburo`, ativo |
| **Empacotamento `.wgt`** | **OK — pacote assinado gerado** |

**O ambiente está completo.** O app compila, é assinado e o runner da seção 5
inicia a VM, instala e abre o pacote automaticamente.

> Java 8 satisfaz o requisito. Se o instalador reclamar, instale um JDK 17
> (Temurin) e aponte o Tizen Studio para ele. Evite alterar o Java padrão
> do sistema: o projeto Android/Gradle depende dele.

Requisitos oficiais da Samsung: 4 GB de RAM (mínimo), 6 GB de disco livre,
JDK 1.8+, Windows 64-bit.

**Limitação importante do emulador:** ele exige virtualização por hardware
(Intel VT-x / AMD-V) e **não roda dentro de VM nem por Área de Trabalho
Remota**. Em muitas máquinas Windows o Hyper-V bloqueia o VT-x — se o
emulador não abrir, é quase sempre isso.

---

## 3. Instalação

### 3.1 Tizen Studio — CONCLUÍDO

Instalado em `C:\tizen-studio`.

### 3.2 Extensões de TV — CONCLUÍDO

Instaladas pelo Package Manager (aba **Extension SDK**):

- **Samsung Tizen TV SDK** (TV Extensions)
- **Samsung Certificate Extension**

Confirmado: `C:\tizen-studio\platforms\tizen-10.0\tv-samsung` existe, junto
com as gerações anteriores (2.4 até 10.0). Isso importa para compatibilidade:
a Samsung exige suporte a mais de uma geração de TV.

> **Não use `tizen list rootstrap` para validar isso.** Esse comando lista
> apenas rootstraps **nativos** (C/C++) e vem sem nenhuma linha de TV mesmo
> com tudo instalado — nosso app é web e não usa rootstrap. Verifique a
> pasta `platforms/*/tv-samsung`, como acima.

### 3.3 PATH — CONCLUÍDO

`C:\tizen-studio\tools\ide\bin` e `C:\tizen-studio\tools` já foram
adicionados ao PATH do usuário. Verificado:

```text
Tizen CLI 2.5.25
Smart Development Bridge version 4.2.25
```

> Terminais abertos antes dessa mudança não enxergam o PATH novo.
> Abra um terminal novo se os comandos não forem encontrados.

---

## 4. Certificados (obrigatório)

A TV **recusa** qualquer app não assinado. São dois certificados:

- **Author** — identifica você. Guarde-o: perdê-lo impede atualizar o app.
- **Distributor** — define onde o app pode rodar.

**CONCLUÍDO.** Para o emulador, o runner usa o perfil
`burocertificate-emulator`, que combina o Author Samsung existente com o
Distributor do emulador. O perfil histórico `iptvburo` pode continuar listado,
mas não é o padrão do comando automatizado.

Foi criado pela CLI (mais rápido que a interface). Registrado aqui para você
saber reproduzir — por exemplo em outra máquina:

```powershell
# 1) A pasta precisa existir ANTES; a CLI nao a cria sozinha.
New-Item -ItemType Directory -Force "C:\tizen-studio-data\keystore\author"

# 2) Certificado author.
tizen certificate -a IPTVBURO -p <SENHA> -n "Lucas Serafin" `
  -c BR -e <EMAIL> -f iptvburo-author -- "C:\tizen-studio-data\keystore\author"

# 3) Perfil ligando author + distributor publico.
tizen security-profiles add -n iptvburo `
  -a "C:\tizen-studio-data\keystore\author\iptvburo-author.p12" -p <SENHA>

# 4) Apontar a CLI para o arquivo de perfis.
#    Chame via cmd: o PowerShell perde as aspas do par chave=valor.
cmd /c '"C:\tizen-studio\tools\ide\bin\tizen.bat" cli-config "profiles.path=C:\tizen-studio-data\profile\profiles.xml"'
```

Conferir a qualquer momento:

```powershell
tizen security-profiles list   # confira iptvburo e/ou burocertificate-emulator
```

Arquivos gerados (ambos **fora** do repositório):

```text
C:\tizen-studio-data\keystore\author\iptvburo-author.p12   <- guarde!
C:\tizen-studio-data\profile\profiles.xml
```

> **Faça backup do `.p12` e da senha.** Perder o certificado author impede
> publicar atualizações do app — a loja exige que a assinatura seja a mesma.
>
> `*.p12` está no `.gitignore`. Nunca comite certificado, senha ou DUID.

Para instalar em **TV física** é preciso incluir o **DUID** da TV no
certificado distributor. O perfil atual usa o distributor público, que serve
para simulador, emulador e submissão à loja. Quando a TV chegar, o DUID é
adicionado pelo Certificate Manager.

---

## 5. Rodar sem TV física

Você ainda não tem a TV, então há três caminhos — comece pelo mais rápido.

### 5.0 Testes automatizados (segundos, sem TV nem simulador)

O jeito mais rápido de saber se o app ainda funciona:

```powershell
cd "d:\CURSOR APPS\IPTVBURO\apps\samsung-tizen-tests"
npm install   # so na primeira vez
npm test
```

Carrega o `index.html` num DOM simulado e exercita a navegação por D-pad.
Cobre contratos, armazenamento, fontes, catálogos, renderização, acessibilidade,
movimento do foco, player, licenciamento e caminhos de erro. Também abre as
fontes de produção em Chrome/Edge headless a 1920×1080 e percorre idioma,
carregamento, aviso legal, perfil, Home demonstrativa e real, categorias e
detalhes de Ao Vivo, catálogo, detalhes de filme/série, episódios, Descobrir,
Pesquisa, Minha BURO, Continuar, Histórico, Lembretes, Notificações, Fontes,
Perfis, Configurações, edição de perfil, gerenciamento e formulário Xtream, PIN
parental, categorias, TMDb/OMDb e seus guias, armazenamento, downloads,
Assinaturas/Ver mais/detalhe, pessoa, compartilhamento, M3U remoto/USB, Stalker,
foto USB, retomada, download em lote, licença, player VOD/ao vivo, menus de
faixas/velocidade/guia, erro e trailer, verificando cortes,
overflow, foco, área segura e sobreposição. Configurações, Stalker, retomada e
erro do player também são repetidos nos cinco idiomas; um contrato separado
confere as 672 chaves e seus parâmetros antes do fallback. A suíte completa deve passar antes
de empacotar.

Para guardar as 79 capturas produzidas pelo smoke visual, sem incluí-las no
WGT, defina `TIZEN_VISUAL_ARTIFACT_DIR` antes de executar `npm test`.
O mesmo smoke também faz 960 trocas de tela e mede retenção de DOM, listeners,
documentos e heap do Chromium após coleta de lixo; essa etapa não gera PNG extra.
Depois ele grava 12.000 títulos sintéticos pelo caminho real de importação e
confere responsividade do event loop, Home, busca e paginação; também não gera
captura extra nem usa rede, playlist ou credencial.
São elas: `language`, `boot`, `demo-home`, `demo-detail`, `real-home`,
`movie-categories`, `movie-catalogue`, `movie-detail`, `series-detail`,
`series-episodes`, `settings`, `settings-languages`, `live-categories`,
`live-detail`, `discover`, `search-results`, `my-buro`, `continue-watching`,
`history`, `reminders`, `notifications`, `sources`, `profiles`, `profile-form`,
`source-manage`, `source-choice`, `source-xtream`, `parental-pin`,
`category-settings`, `tmdb-settings`, `tmdb-settings-profile`, `tmdb-guide`,
`tmdb-guide-end`, `omdb-settings`, `omdb-guide`, `omdb-guide-end`,
`storage-settings`, `downloads`, `subscriptions`, `subscription-detail`,
`person`, `share`, `source-m3u`, `source-stalker-top`, `source-stalker`,
`source-usb-m3u`, `profile-photo-picker`, `resume-prompt`,
`bulk-download-confirm`, `licence`, `player-vod`, `player-locked`,
`player-audio-menu`, `player-subtitle-menu`, `player-speed-menu`,
`player-error`, `player-live-guide` e `trailer`, além de
`locale-{pt-br,en,de,it,es}-{settings,stalker,resume,player-error}`.

Rode isto antes de empacotar — é muito mais rápido que abrir o simulador.

> Os testes ficam em `apps/samsung-tizen-tests/`, **fora** da pasta do app,
> porque o `build-web` copia tudo que estiver em `apps/samsung-tizen/` para
> dentro do `.wgt`. Com os testes lá dentro, o `node_modules` inteiro ia
> junto e o pacote saltava de 56 KB para dezenas de MB (isso aconteceu de
> verdade durante a configuração).

### 5.1 Simulador (mais fácil, comece por aqui)

O **TV Simulator** roda o app no Chromium do seu PC. É rápido e ótimo para
ajustar layout, foco e navegação.

> **Os argumentos de linha de comando não lançam o app.** Testei `--file`
> (com `.html` e com `.wgt`) e `--app`: em todos os casos o simulador abre a
> tela inicial da TV ou o *My Apps* vazio. Copiar os arquivos direto para
> `appLauncher/app/` também não basta — o simulador guarda a lista de apps
> instalados no localStorage, alimentada só pela instalação de verdade.
>
> Cuidado com o falso positivo: ver a tela da Samsung com papel de parede e
> o botão APPS significa que o **simulador** subiu, não que o seu app
> carregou.

#### Opção 1 — pelo Tizen Studio (recomendado)

O IDE cuida de compilar, assinar, instalar e abrir:

```powershell
cmd /c '"C:\tizen-studio\ide\TizenStudio.bat"'
```

> Use o **`.bat`**, não o `TizenStudio.exe`. Nesta instalação o `.exe` sai em
> silêncio sem abrir janela nenhuma (o `eclipse.ini` está vazio, 0 KB). O
> `.bat` monta os parâmetros da JVM por conta própria e funciona. A primeira
> abertura leva de 30 a 60 segundos.

1. Na primeira execução ele pede um **workspace** — aceite o padrão.
2. **File → Open Projects from File System…**
3. Em *Import source*, aponte para `d:\CURSOR APPS\IPTVBURO\apps\samsung-tizen`
   e clique **Finish**. O projeto aparece no *Project Explorer* como
   **IPTVBURO** (já existem `.project` e `.tproject` no repositório).
4. Clique com o botão direito no projeto → **Run As → Tizen Web Application**.
5. Se ele perguntar o destino, escolha o **TV Simulator**.

O IDE reconstrói e reinstala a cada Run — é o ciclo de trabalho normal.

#### Opção 2 — pela interface do simulador

```powershell
Start-Process "C:\tizen-studio\tools\sec-tv-simulator\simulator.exe"
```

1. **APPS** (canto inferior esquerdo).
2. **Option** (canto inferior direito).
3. **Install**.
4. Selecione `apps\samsung-tizen\.buildResult\IPTV BURO.wgt`.
5. O app aparece em **My Apps** — clique para abrir.

Depois de editar o código, refaça o `.wgt` (seção 6) e reinstale.

Limitação decisiva: o simulador **não reproduz vídeo via AVPlay de verdade**
— `webapis.avplay` não existe nele. Nosso `player.js` detecta isso e mostra
"AVPlay indisponível" em vez de travar. Ou seja: o simulador valida **UI,
foco e navegação**, nunca playback.

### 5.2 Emulador (mais fiel)

O **TV Emulator** é uma máquina virtual com Tizen real, aceita `sdb` e usa
WHPX nesta máquina. Não desligue o Hyper-V: a configuração atual já funciona.

Criar: **Tools → Emulator Manager → Create → TV** e escolher a imagem 10.0
(64-bit).

#### Estado verificado desta máquina (20/08/2026)

```text
VM ................... T-samsung-10.0-x86_64
Imagem ............... tv-samsung-10.0-x86_64
Motor ................. WHPX operacional
Resultado ............. pacote compila, assina, instala e abre
```

**Conclusão atual:** o emulador Tizen abre nesta máquina usando WHPX. Em
20/08/2026 o fluxo real chegou até a Home e respondeu a setas, ENTER e RETURN.
Um smoke isolado também reutilizou os adapters AVPlay/teclas de produção e
comprovou um HLS público em `PLAYING → PAUSED → PLAYING`, seguido por avanço e
retrocesso via setas e teclas dedicadas. A sequência chegou a 34.784, 26.128,
57.072 e 50.045 ms, com conclusão dos seeks no log nativo. A fixture multifaixa
enumerou 2 áudios e 8 legendas; o AVPlay aceitou a segunda faixa de cada tipo e
o pipeline registrou segmentos e buffers WebVTT da legenda selecionada.

Para repetir o ciclo completo, mesmo com a VM fechada, use:

```powershell
cd "d:\CURSOR APPS\IPTVBURO"
.\scripts\run-samsung-emulator.ps1
```

O runner inicia a VM Samsung padrão, espera o SDB e os serviços da TV com
timeout, executa os testes, cria uma staging fechada, assina, instala e abre o
app. Em seguida espera cinco segundos, ativa o pacote novamente e exige que o
launcher retorne o mesmo PID; um processo que caiu e foi recriado reprova o
ciclo. A janela pode ser ajustada entre 3 e 60 segundos com
`-LaunchStabilitySeconds`. Em 20/08/2026 esse caminho foi medido desde zero dispositivos até o app
aberto em 70,3 segundos usando `-SkipTests`; sem essa opção ele também executa
a suíte completa. O simulador continua útil para layout rápido, mas não
substitui o emulador nem uma TV física para AVPlay e APIs nativas.

Em 24/08/2026, a revisão `tizen-emulator-20260824-153120` repetiu o ciclo com
`-LaunchStabilitySeconds 30`: instalação 100%, PID 15253 preservado e 37/37
arquivos do payload idênticos aos fontes. A janela real do emulador mostrou a
Home com hero, continuidade e capas carregadas; filmes exibiram `Assistir` e
`Ver detalhes` dentro do painel, enquanto séries exibiram apenas detalhes até a
escolha do episódio. O cache opcional de arte em USB
agora possui preenchimento completo, progresso, pausa/retomada e atualização em
Configurações. A validação real carregou prateleiras regionais e capas pelo TMDb,
uma nota IMDb pelo OMDb e o Hero enriquecido antes de revelar a Home. TMDb e
OMDb continuaram configurados no KeyManager. Capas carregadas depois do primeiro
quadro agora mantêm placeholder e fallback até a decodificação; os valores
nunca entram na staging ou no WGT. A correção do nonce e da prova ECDSA para
Base64URL sem padding também concluiu o primeiro registro real: o topo mudou de
`Dispositivo não registrado` para `Faltam 7 dias`. A ficha também oferece
títulos semelhantes do próprio catálogo enquanto o TMDb não responde, sempre
restritos à mesma fonte e às regras do perfil. Quando a resposta pública chega,
filmes mostram franquia por lançamento antes das recomendações e usam
`/similar` apenas como fallback; séries usam recomendações. Um resultado
presente na fonte abre a ficha local, enquanto um ausente abre Assinaturas/Onde
assistir. A volta recompõe a ficha anterior com a mesma rolagem e o mesmo card
focado, inclusive em navegação encadeada.

A revisão posterior `tizen-emulator-20260824-182522` adicionou os atalhos
visuais do diretório TMDb ao topo de Filmes/Séries. O ciclo com
`-LaunchStabilitySeconds 30` instalou o pacote e conservou o PID 3969. O WGT tem
714.718 bytes, 39 entradas, duas assinaturas, 37/37 payloads idênticos aos
fontes e SHA-256
`F4E45B1A1753BB650384211C9DF7E0C1BBB72AF7846C32BE4B0E6260353882CC`.
Na janela real, Netflix abriu diretamente a grade de 100 títulos e RETURN
voltou ao filtro Filmes de Assinaturas. Os 55 grupos pós-gate passaram em 156,7
segundos; TMDb 44/44, D-pad 349/349 e visual 267/267. Nenhuma chave entrou no
staging ou no pacote.

Para repetir a prova AVPlay sem adicionar lista ou URL ao WGT de produção:

```powershell
.\scripts\run-samsung-playback-smoke.ps1 -DeviceSerial emulator-26101
```

O smoke usa um package id separado, fixture HTTPS pública e callback local. Ele
falha por timeout ou erro nativo e só imprime `AVPLAY_SMOKE_PASS` depois de tocar,
pausar, retomar, avançar 30 segundos, retroceder 10 segundos por eventos ECP e
selecionar faixas alternativas reais de áudio e legenda.
No `finally`, ele desinstala `BUROSMK001.PlaybackSmoke`, inclusive após timeout
ou erro, para liberar os recursos nativos antes de abrir o aplicativo normal.
Na imagem 10.0, o ECP usa os códigos das skins (`244` Play/Pause, `106/105`
direita/esquerda e `208/168` Fast Forward/Rewind), não os códigos DOM
`10252/39/37/417/412` observados pelo JavaScript. Ele não substitui a confirmação
de imagem, áudio e codecs numa TV física.

---

## 6. Empacotar e instalar

**Já validado nesta máquina** — os comandos abaixo foram executados com
sucesso e geram um `.wgt` assinado.

São **dois passos**: `build-web` prepara uma cópia do app em `.buildResult`,
e `package` assina essa cópia. Empacotar a pasta do projeto direto
(`package -- .`) inclui lixo de desenvolvimento no pacote.

```powershell
cd "d:\CURSOR APPS\IPTVBURO\apps\samsung-tizen"

# 1) Preparar a copia
tizen build-web -- .

# 2) Assinar (gera o .wgt dentro de .buildResult)
tizen package -t wgt -s burocertificate-emulator -- .buildResult
```

Resultado esperado:

```text
Package File Location: ...\apps\samsung-tizen\.buildResult\IPTV BURO.wgt
```

Conferir o que entrou no pacote:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z=[System.IO.Compression.ZipFile]::OpenRead(".buildResult\IPTV BURO.wgt")
$z.Entries | Select-Object -Expand FullName; $z.Dispose()
```

Deve conter apenas `config.xml`, `index.html`, `css/`, `js/`, `icon.png` e as
duas assinaturas (`author-signature.xml`, `signature1.xml`).

> A documentação **não fica** em `apps/samsung-tizen/`, e sim em `docs/`.
> O `build-web` usa uma lista fixa de exclusões e **ignora `.tizenignore`**,
> então qualquer arquivo solto na pasta do app vai parar dentro do `.wgt`.

Instalar (quando houver emulador ou TV conectada):

```powershell
sdb devices                                        # lista dispositivos
tizen install -n "IPTV BURO.wgt" -- .buildResult   # instala
```

### Quando você tiver a TV física

1. TV e PC **na mesma rede**.
2. Na TV: abra **Apps**, digite **12345** no controle → painel de
   Developer Mode.
3. Ligue **Developer mode** e informe o **IP do seu PC**.
4. **Reinicie a TV** (tire da tomada por ~15s — só desligar não abre a
   porta do `sdb`).
5. No PC:

```powershell
sdb connect <IP-DA-TV>
sdb devices
```

---

## 7. Erros comuns

### Compatibilidade: escreva para navegador ANTIGO

Este é o erro mais caro de descobrir tarde. O simulador usa o user-agent do
**Chrome 55**, e TVs em uso hoje vão de ~Chrome 47 (2015) a M130 (2024). O
código precisa atender a mais antiga que você pretende suportar.

Já corrigido neste projeto:

| Recurso | Exige | Use no lugar |
| --- | --- | --- |
| `inset: 0` | Chromium 87+ | `top/right/bottom/left: 0` |
| `gap` em flex | Chromium 84+ | `margin` nos filhos |
| `user-select` | prefixo no WebKit | `-webkit-user-select` + padrão |

A mesma regra vale para JavaScript. O app é **ES5 puro**: sem arrow function,
`const`/`let`, template string, spread ou optional chaining. Um único `=>`
numa TV de 2015 gera *SyntaxError* e a tela fica preta — sem mensagem.

Conferir antes de empacotar:

```powershell
Select-String -Path "css\style.css" -Pattern "inset:|gap:|clamp\(|aspect-ratio"
Select-String -Path "js\*.js" -Pattern "=>|\bconst\b|\blet\b|\?\."
```

Nenhuma saída = compatível.

### Outros problemas

| Sintoma | Causa provável |
| --- | --- |
| Áudio toca, tela preta | `body` com fundo opaco cobrindo a camada de vídeo |
| App não instala | Certificado ausente, expirado ou DUID errado |
| `sdb connect` falha | Developer Mode desligado, IP errado, ou TV não reiniciada |
| Emulador não abre | Hyper-V ligado / VT-x desativado na BIOS |
| Setas não respondem | `keydown` não registrado, ou foco só via CSS `:focus` |
| Tecla de mídia ignorada | Falta `tizen.tvinputdevice.registerKey()` |

---

## 8. Próximo passo

Depois que o app abrir no simulador e você entender o ciclo
*empacotar → assinar → instalar*, o passo seguinte é ligar o app ao
domínio compartilhado do IPTV BURO (catálogo, perfis, favoritos) em vez
das fontes de teste.

Ver `docs/PLANO_SMART_TV.md`.
