# Validação em hardware — Android TV

**Status:** app instalado e exercitado em um celular Android real via ADB; nenhuma
Android TV Box/Google TV física foi usada ainda. `platforms.json` declara
`android-tv` como `hardware: TV_PENDING` por isso.
**Data:** 2026-08-28

O app é um único APK que serve celular/tablet e Android TV: o manifesto marca
`android.software.leanback` como `required="false"` e declara os dois
launchers (`LAUNCHER` e `LEANBACK_LAUNCHER`), então a mesma build abre tanto
num telefone quanto numa TV/box com launcher próprio de TV. `MainViewModel`
lê `Configuration.UI_MODE_TYPE_TELEVISION` para decidir capabilities restritas
a TV (por exemplo downloads ficam fora em TV, ver ADR-008) — esse ramo de
código só é exercitado de verdade rodando num dispositivo que o Android
classifica como televisão, o que um celular nunca faz mesmo com D-pad USB
conectado.

## O que os testes não podem provar

`testDebugUnitTest` roda em JVM pura com Robolectric/fakes; não há hardware,
não há launcher de TV real, não há controle remoto físico, não há decodificador
de vídeo real. Onde a suíte simula `Configuration.UI_MODE_TYPE_TELEVISION`
diretamente (como em `MainViewModelNavigationTest`), ela prova que o código
*decide* certo dado esse sinal — não que o sinal chega correto vindo de uma TV
real, nem que o resultado é utilizável a três metros de distância com um
controle remoto genérico infravermelho/Bluetooth.

| Área | O que já foi exercitado | O que continua pendente de hardware |
| --- | --- | --- |
| Detecção de TV | `Configuration.UI_MODE_TYPE_TELEVISION` simulado em teste unitário | Ler o sinal real de uma Android TV Box/Google TV; confirmar que o launcher de TV mostra o banner (`@drawable/tv_banner`) corretamente |
| Media3/ExoPlayer | Testes unitários dos adapters e da política de retomada | Decodificação de hardware real (H.264/H.265/HEVC conforme o SoC), HDMI-CEC, latência de um controle remoto de TV genérico |
| D-pad | Navegação testada com fakes de foco em Compose | Foco visível a três metros, ordem de foco em telas densas, `DPAD_CENTER`/`BACK` reais de um controle Bluetooth/IR |
| Instalação | Um celular Xiaomi via `adb install -r` | Sideload numa Android TV Box real (Play Store não é a via de instalação nesta fase) |
| Licença | Fluxo completo testado em emulação de rede (MockWebServer) | Comportamento real numa TV atrás de um roteador doméstico comum, incluindo Wi-Fi fraco típico de sala de estar |

Um teste unitário responde o que foi programado para responder. A TV responde
o que o SoC, o launcher do fabricante e o controle remoto realmente fazem — e
é exatamente aí que os defeitos que sobram costumam morar.

---

## 1. Preparar o ambiente

### 1.1 Emulador Android TV (nesta máquina)

O Android SDK já usado para o celular também serve imagens de sistema
`android-tv`. Não há evidência registrada nesta máquina de uma AVD de TV ter
sido criada ainda — confirmar com:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -list-avds
```

Se não houver uma AVD `*-tv-*`, criar uma pelo Android Studio (Device Manager
→ Create Device → categoria **TV**, imagem `google_apis` ou `google_apis_atd`
na API mais recente com Play Store disponível) ou por linha de comando com
`avdmanager`. O emulador de TV expõe D-pad e controle remoto virtual na sua
própria janela de ferramentas — use-o em vez do mouse ao validar navegação.

### 1.2 Dispositivo físico

Duas rotas, dependendo do que houver disponível:

**Android TV Box / Google TV genérica**

1. TV/box e PC na mesma rede.
2. Nas Configurações do dispositivo: **Sobre** → tocar na versão do build
   repetidas vezes até habilitar **Opções do desenvolvedor**.
3. Em Opções do desenvolvedor: ligar **Depuração USB** e, se disponível,
   **Depuração via rede**/**Depuração sem fio**.
4. Por rede: `adb connect <IP-DO-DISPOSITIVO>:5555` (a porta pode variar por
   fabricante). Por cabo: um adaptador USB-C/USB-A OTG costuma ser necessário
   em boxes sem porta USB-A exposta.
5. `adb devices` deve listar o serial; a primeira conexão pede autorização na
   tela da própria TV — sem aceitar ali, o serial aparece como `unauthorized`,
   exatamente como já observado neste projeto ao conectar o celular de teste.

**Fire TV** (Android bifurcado — fora do escopo Google TV, mas o mesmo APK
deve abrir por não depender de serviços Google)

1. No controle remoto Fire TV: **Configurações** → **Minha Fire TV** →
   **Sobre** → clicar no nome do dispositivo repetidamente até habilitar o
   modo desenvolvedor.
2. Ligar **Depuração ADB**.
3. `adb connect <IP-DA-FIRE-TV>:5555`.

### 1.3 Instalar

```powershell
cd "d:\CURSOR APPS\IPTVBURO"
./gradlew :apps:android-tv:assembleDebug
adb -s <serial> install -r apps/android-tv/build/outputs/apk/debug/android-tv-debug.apk
```

Confirme o timestamp do APK antes de instalar — um `assembleDebug` que só
imprime `UP-TO-DATE` pode não ter recompilado de verdade; force com
`--rerun` se houver dúvida, como já documentado neste projeto para o build
Gradle deste mesmo módulo.

---

## 2. O que verificar

A ordem importa: cada bloco depende do anterior funcionar.

### 2.1 Abertura e detecção de TV

- [ ] O launcher de TV (Google TV Home, Fire TV Home, launcher do fabricante)
      mostra o banner do app (`@drawable/tv_banner`), não apenas o ícone de
      celular cortado.
- [ ] O app abre pelo `LEANBACK_LAUNCHER`, não só pelo ícone comum.
- [ ] `Configuration.UI_MODE_TYPE_TELEVISION` é detectado — confirmar
      indiretamente pelo comportamento: a seção **Downloads** deve ficar
      **indisponível** em TV (ADR-008 reverteu a regra para celular, mas TV
      continua de fora), o oposto do que `downloads destination stays
      rejected on a television` já prova em unit test.
- [ ] Nenhuma tela aparece cortada ou com overscan nas bordas em 1920×1080
      (a maioria das TVs ainda reserva uma margem de segurança que emuladores
      de desktop não simulam).
- [ ] O app abre sem tela preta e sem ANR na primeira execução.

### 2.2 Controle remoto / D-pad

- [ ] Setas movem o foco de forma visível a três metros de distância.
- [ ] `DPAD_CENTER`/ENTER seleciona.
- [ ] `BACK` volta corretamente pela pilha de navegação e fecha o app na raiz.
- [ ] Segurar uma seta (repetição automática do controle) não trava a
      interface nem pula itens de forma imprevisível.
- [ ] Teclas de mídia do controle (Play/Pause, avançar, retroceder), quando o
      controle as tiver, funcionam durante a reprodução.
- [ ] Navegar pela Ribbon/menus principais nunca deixa o foco "perdido" (sem
      destaque visual em nenhum elemento).

**Risco conhecido:** controles remotos de terceiros e de fabricantes diferentes
mapeiam teclas de mídia de formas inconsistentes; um código de tecla que
funciona num controle Google TV pode não existir num controle genérico
infravermelho universal.

### 2.3 Reprodução — o maior risco

- [ ] Um canal ao vivo real (Xtream/M3U autorizado) abre, decodifica e toca.
- [ ] Um VOD (filme/episódio) real abre, decodifica e toca.
- [ ] Pausar/retomar funciona pelo controle remoto.
- [ ] Avançar/retroceder (seek) funciona sem travar o player.
- [ ] Trocar de faixa de áudio/legenda funciona quando o stream oferece mais
      de uma.
- [ ] Sair do player volta para a lista sem tela preta residual.
- [ ] Um stream inválido/indisponível mostra erro claro em vez de travar o
      app.
- [ ] HDMI-CEC (se a TV suportar): ligar a TV pelo controle da TV também
      resume o app corretamente, se o app estava em segundo plano.

Codecs e perfis de decodificação variam por SoC. Um stream H.265 que toca
numa Android TV Box de 2024 pode falhar silenciosamente numa de 2019 com
decodificador só por software.

### 2.4 Licença

- [ ] Num dispositivo nunca registrado, o app se registra sozinho e mostra o
      período de teste.
- [ ] O código do dispositivo aparece em Configurações → Licença.
- [ ] `fetchAssignedPlaylist()` funciona numa TV atrás de um roteador
      doméstico comum: se o dono do produto atribuiu uma lista pelo painel
      admin antes desta TV nunca ter aberto o app, a lista deve chegar
      sozinha no primeiro boot com licença válida.
- [ ] Uma chave de ativação válida ativa a assinatura.
- [ ] Uma chave inválida mostra erro claro.
- [ ] Sem internet, o app abre e explica que precisa revalidar, sem travar.

### 2.5 Provisionamento remoto (feature nova, 2026-08-25/27)

Este é o fluxo mais recente e ainda não tem nenhuma evidência de hardware:

- [ ] Um dispositivo de teste real (não emulador) recebe uma playlist
      atribuída pelo painel `/admin` e a importa sozinho no primeiro boot com
      licença válida, sem o usuário digitar nada.
- [ ] O dispositivo confirma a aplicação ao servidor (`/v1/provisioning/confirm`)
      e o painel deixa de mostrar "aguardando" para esse dispositivo.
- [ ] Uma chave TMDb/OMDb enviada junto aparece configurada em Configurações
      sem sobrescrever uma que o usuário já tivesse colocado manualmente antes.
- [ ] Trocar a lista pelo painel depois de já aplicada uma vez, e reabrir o
      app, importa a nova lista (senha diferente ⇒ reimporta, como a lógica
      de `hasXtreamSource` prevê).

### 2.6 Catálogo grande

- [ ] A importação de uma lista com dezenas de milhares de canais termina sem
      travar.
- [ ] Durante a importação, o D-pad continua respondendo.
- [ ] Navegar entre seções não engasga com o catálogo carregado.
- [ ] Depois de meia hora navegando, a interface continua fluida (teste de
      memória — um SoC de TV tem bem menos RAM disponível que um celular
      atual).

---

## 3. Como registrar o que achar

Para cada falha, anote:

1. Fabricante e modelo do dispositivo (Configurações → Sobre).
2. Versão do Android e se é Google TV, Fire TV ou outra bifurcação.
3. O que você fez, na ordem.
4. O que aconteceu, e o que era esperado.
5. `adb logcat -s IPTVBURO` (ou o tag real do `AppLogger`) no momento da
   falha.

Sem o modelo, um defeito de decodificação de vídeo parece um defeito do app.
São coisas diferentes e se resolvem de formas diferentes.

---

## 4. O que fazer com o resultado

Enquanto este roteiro não for executado num dispositivo de TV real,
`platforms.json` deve continuar declarando `hardware: TV_PENDING` para
`android-tv`, exatamente como já faz.

Capabilities específicas de TV (comportamento de Downloads, foco de D-pad em
telas densas, HDMI-CEC) só devem ser declaradas como validadas depois de
observadas neste roteiro — não apenas por passarem no teste unitário que
simula `UI_MODE_TYPE_TELEVISION`.
