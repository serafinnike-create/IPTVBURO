# Validação em hardware — Samsung Tizen

**Status:** nada neste roteiro foi executado ainda.
**Data:** 13 de agosto de 2026

O app tem 541 testes automatizados passando, mas todos rodam contra simulações
das APIs da TV. Este documento existe porque essa diferença importa: os testes
provam que o código faz o que pretende, não que a TV aceita o que ele pede.

## O que os testes não podem provar

Quatro áreas dependem de APIs que só existem no aparelho. Em nenhuma delas uma
linha de código foi exercitada contra a plataforma real.

| Área | O que está simulado |
| --- | --- |
| Reprodução | `webapis.avplay` inteiro — abrir, preparar, tocar, faixas, seek |
| Download USB | `tizen.download` e `tizen.filesystem` |
| Licença | `tizen.keymanager`, mais a rede real até o Worker |
| Controle remoto | Códigos de tecla e `tizen.tvinputdevice` |

Um mock responde o que foi programado para responder. A TV responde o que ela
faz — e a diferença entre os dois é exatamente onde moram os defeitos que
sobraram.

---

## 1. Preparar o ambiente

Há dois caminhos. O emulador é suficiente para quase tudo; a TV física é
obrigatória antes de publicar.

### 1.1 Emulador (nesta máquina)

O SDK já tem a imagem `tv-samsung-10.0-x86_64` instalada. O que falta é
virtualização.

Estado medido nesta máquina:

```text
HAXM (Intel)     bloqueado — o Hyper-V tomou o VT-x para si
WHPX             suportado pelo hardware (check-whpx retorna 0)
Hypervisor Platform   DESABILITADO  <- é isto que falta
```

O WHPX é a saída: em vez de disputar o VT-x com o Hyper-V, ele usa o próprio
Hyper-V como motor. O Tizen Studio o suporta desde a versão 5.0, e ao
contrário do HAXM ele convive com Hyper-V — WSL2 e Docker continuam
funcionando.

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
cd "d:\CURSOR APPS\IPTVBURO\apps\samsung-tizen"
tizen build-web -- .
tizen package -t wgt -s iptvburo -- .buildResult
sdb devices
tizen install -n "IPTV BURO.wgt" -- .buildResult
```

---

## 2. O que verificar

A ordem importa: cada bloco depende do anterior funcionar.

### 2.1 Abertura

- [ ] O app abre sem tela preta.
- [ ] A tela de carregamento aparece e progride.
- [ ] O seletor de idioma responde ao D-pad.
- [ ] Nenhum erro no console (`sdb dlog` ou o Web Inspector).

**Se a tela abrir preta:** quase sempre é o fundo opaco cobrindo a camada de
vídeo da AVPlay, ou um erro de sintaxe que o motor da TV recusa e o simulador
aceitou. O `engine-compatibility.test.js` cobre a segunda hipótese, mas só
para os padrões que ele conhece.

### 2.2 Controle remoto

- [ ] Setas movem o foco, e o foco é visível de longe.
- [ ] ENTER seleciona.
- [ ] RETURN volta; na primeira tela, fecha o app.
- [ ] Teclas de mídia (play/pause, avançar, retroceder) respondem durante a
      reprodução.
- [ ] Segurar uma seta não trava a interface.

**Os códigos de tecla são o risco aqui.** `RETURN` é 10009 e as teclas de
mídia precisam de `registerKey` — se um modelo usar outro código, aquela tecla
simplesmente não faz nada, sem erro nenhum.

### 2.3 Reprodução — o maior risco

- [ ] Um stream HLS abre e toca.
- [ ] O áudio acompanha o vídeo.
- [ ] Pausar e retomar funciona.
- [ ] Avançar e retroceder 30s funciona (ou falha com mensagem, se o stream
      não permitir).
- [ ] Trocar faixa de áudio e legenda funciona.
- [ ] Sair da reprodução volta para a lista, sem tela preta.
- [ ] Um stream inválido mostra erro em vez de travar.

**O que pode dar errado:** a ordem `open → setDisplayRect → prepareAsync →
play` foi escrita a partir da documentação e nunca exercitada. Se a AVPlay
recusar algum passo, o sintoma é áudio sem imagem, ou nada.

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
