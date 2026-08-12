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

Verificado nesta máquina (atualizado em 11/08/2026):

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

**O ambiente está completo.** O app já compila e é assinado com sucesso.
O que falta é executá-lo (seção 5).

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

**CONCLUÍDO.** O perfil `iptvburo` existe e está ativo.

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
tizen security-profiles list   # 'iptvburo' deve aparecer com Active = O
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
Cobre renderização, movimento do foco, o limite nas bordas e o caminho de
erro quando a AVPlay não existe. **8 testes, todos passando.**

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

O **TV Emulator** é uma máquina virtual com Tizen real. Reproduz vídeo e
aceita `sdb`. Exige VT-x ativo e Hyper-V desligado.

Criar: **Tools → Emulator Manager → Create → TV** e escolher a imagem 10.0
(64-bit).

#### Estado verificado desta máquina (11/08/2026)

```text
CPU .................. Intel Core i7-12700H (14 núcleos)  → suficiente
RAM .................. 15,7 GB                            → suficiente
HypervisorPresent .... True                               → BLOQUEIA o emulador
```

**Conclusão: o emulador Tizen não vai abrir nesta máquina hoje.** O Hyper-V
está ativo e toma o VT-x para si — é por isso que
`VirtualizationFirmwareEnabled` aparece como `False`: o hipervisor já
capturou o recurso.

Duas saídas:

**(a) Use o simulador** — recomendado agora. Não exige virtualização e é
suficiente para aprender navegação, foco e layout, que é o objetivo desta
primeira fase.

**(b) Desligue o Hyper-V** — só se realmente precisar do emulador. Requer
PowerShell **como administrador** e reinício:

```powershell
bcdedit /set hypervisorlaunchtype off
```

> Isso **desativa WSL2, Docker Desktop e sandboxes do Windows** enquanto
> estiver desligado. Se você usa qualquer um deles, prefira o simulador
> agora e valide o vídeo direto na TV física quando ela chegar.
>
> Para reverter: `bcdedit /set hypervisorlaunchtype auto` + reinício.

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
tizen package -t wgt -s iptvburo -- .buildResult
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
