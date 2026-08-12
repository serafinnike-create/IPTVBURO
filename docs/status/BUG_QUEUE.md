# Fila de bugs — Windows/Desktop

Bugs reproduzidos em teste real. Cada item registra o sintoma observado, a causa
investigada no código e o que ainda falta confirmar.

## Estado atual

**Publicado:** `v2.0.0-alpha.3` — Windows (MSI) e Android (APK de depuração).

Quem tem a `alpha.2` instalada recebe esta versão por **Opções → Buscar
atualização**. O caminho foi verificado antes de publicar, não presumido:

- `isNewerVersion("2.0.0-alpha.3", "2.0.0-alpha.2")` é verdadeiro, e a versão
  atual não se oferece a si mesma — coberto por teste de regressão;
- o `DESKTOP_RELEASE_VERSION` gerado no build é `2.0.0-alpha.3`;
- o nome do MSI casa com o filtro de instalador e o APK **não** casa, então o
  atualizador não pode baixar o pacote errado;
- a URL de download satisfaz as cinco checagens de confiança (https, host
  `github.com`, sem userinfo, porta padrão, caminho do repositório);
- 286 MB contra um teto de 1 GB.

Corrigidos e publicados: BUG-001, BUG-002, BUG-004, BUG-006, BUG-007, BUG-008.

⚠️ **BUG-009 — a atualização pelo app apagava o aplicativo.** Corrigido.

A correção principal está na **versão do MSI**, que viaja dentro do pacote novo —
não no script, que é escrito pela build antiga. Por isso ela vale mesmo para quem
está na `alpha.1`/`alpha.2`/`alpha.3`:

O script antigo tenta `msiexec /i` primeiro e só desinstala se esse passo falhar.
Ele falhava porque as versões eram idênticas (`2.0.0` dos dois lados). Com a
`alpha.4` declarando `2.0.4`, o Windows reconhece um upgrade legítimo, o primeiro
passo conclui e o passo destrutivo nunca é alcançado.

As melhorias no próprio script (mensagem de recuperação, retry visível) só entram
em vigor a partir da próxima atualização, mas são a rede de segurança — não a
correção. Detalhes no BUG-009.

**Auditado depois de publicar:** capa, sinopse e trailer na tela de Assinaturas
(itens 2 e 3 do BUG-006). O caminho está inteiro e correto do cliente até o
composable, e a API devolve os dados — não havia um segundo defeito ali. A única
forma de a tela ficar vazia era o cliente TMDb obsoleto do item 4, já corrigido.
Detalhes no BUG-006.

**O que ainda não foi confirmado por uso real:** todas as correções acima foram
verificadas por teste automatizado e contra a API do TMDb, mas ninguém abriu a
alpha.3 instalada e olhou a tela. Vale um teste de instalação limpa.

---

---

## BUG-017 — "Não foi possível alcançar o servidor" ao conectar a lista

**Status:** aguardando informação — **não reproduzido**
**Reportado em:** 2026-08-13
**Ambiente:** `v2.0.0-alpha.3` instalado do GitHub

### Sintoma

Na tela de conexão da lista: *"Não foi possível carregar a lista — Não foi
possível alcançar o servidor."*

### O que essa mensagem significa exatamente

É `XtreamFailureReason.NETWORK`, e agora que o mapeamento de erros foi separado
(ver AUDITORIA-010) ela é **confiável**: só aparece quando a requisição ao
provedor realmente não completou. Não é mais o balde onde caíam falhas do app.

Ou seja, uma destas: o endereço está incorreto, o provedor está fora do ar, ou
algo na rede local (firewall, antivírus, DNS) bloqueou a chamada.

### O que falta para diagnosticar

Não dá para afirmar mais sem dados. O que resolveria:

1. o `~/.iptvburo/logs/iptvburo.log` da execução que falhou — agora ele registra
   o tipo da exceção e a razão Xtream;
2. se o mesmo endereço/usuário/senha funciona noutro aplicativo ou no navegador;
3. se o servidor usa `http` numa porta alta (comum em painéis Xtream) e se há
   antivírus com inspeção de rede ativa.

**Não peça ao usuário para colar a URL com usuário e senha em lugar público** —
essa combinação é a credencial da assinatura dele.

---

## BUG-018 — "Redefinir" não apaga a lista nem as credenciais

**Status:** ✅ CORRIGIDO em 2026-08-13
**Reportado em:** 2026-08-13

### Sintoma

A opção de redefinir no perfil não zera tudo: nome e lista continuam lá. O
esperado é voltar ao estado de instalação nova.

### Causa

`resetEverything()`
([DesktopAppState.kt:707-715](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L707-L715))
limpa perfis, favoritos, downloads e idioma — mas delega a limpeza da fonte a
`forgetSelectedSource()`, que **sai cedo quando nenhuma fonte está selecionada**:

```kotlin
fun forgetSelectedSource() {
    if (isXtreamSelected) { disconnectXtream(); return }
    val sourceId = selectedCatalog?.source?.id ?: return   // <- sai aqui
    ...
}
```

Só `disconnectXtream()` chama `rememberedXtreamStore.clear()`. Se o app for
redefinido sem uma fonte Xtream ativa no momento, o blob de credenciais nunca é
apagado, e o cache de catálogo em disco não é tocado por caminho nenhum.

Confirmado numa máquina real, depois de um redefinir:

```text
~/AppData/Local/lucasserafin94/IPTVBURO/remembered-source.dpapi   (sobreviveu)
~/.iptvburo/catalog-cache/live.burocat                            (sobreviveu)
```

Isso também explica o BUG-002 de outro ângulo: o app volta com catálogo em disco
e sem sessão coerente, que é exatamente o estado que produz mensagens erradas.

### Correção proposta

`resetEverything()` deve limpar incondicionalmente, sem depender do que está
selecionado:

1. `rememberedXtreamStore.clear()` sempre, não só via `disconnectXtream`;
2. apagar `~/.iptvburo/catalog-cache/` e `~/.iptvburo/shelf-cache/`;
3. limpar o repositório em memória (`xtreamRepository.clearIncludingDiskCache()`,
   que já existe).

**Não apagar** `device-identity.dpapi`: é a identidade da licença. Apagá-la faria
o cliente perder o que pagou — exatamente o BUG-012. Vale um comentário no código
dizendo isso, porque "redefinir tudo" convida alguém a incluir esse arquivo.

### Correção aplicada

`resetEverything()` agora limpa incondicionalmente, sem depender do que está
selecionado: `rememberedXtreamStore.clear()`, `clearIncludingDiskCache()`, a
chave de ativação exibida e o modo de áudio.

**`device-identity.dpapi` continua intocado, de propósito**, com um comentário no
código explicando por quê: é a identidade da licença, e apagá-la daria um código
novo à máquina — exatamente o BUG-012, que custou trinta dias a um cliente.
"Redefinir tudo" convida alguém a incluir esse arquivo; o comentário existe para
recusar o convite.

### Cobertura

`RememberedXtreamStoreTest` já provava que `clear()` apaga o blob — o defeito
nunca esteve no store, e sim em `resetEverything` jamais chamá-lo. Um teste de
`resetEverything` completo exigiria montar um `DesktopAppState` inteiro; a
verificação mais barata e honesta é a manual: redefinir e conferir que
`remembered-source.dpapi` e `catalog-cache/` sumiram e `device-identity.dpapi`
ficou.

---

## BUG-016 — A migration 0005 não aplica num banco antigo

**Status:** diagnosticado, **não corrigido** — é trabalho paralelo, não toquei
**Encontrado em:** 2026-08-13, ao rodar a suíte do worker

### Sintoma

O teste `the fresh schema and the P0 migration both apply cleanly` falha com
`no such column: updated_at`. Foi confirmado que **a falha existe sem as minhas
alterações** (verificado com `git stash`), então é do trabalho em andamento.

### Causa exata

Aplicando as migrations uma a uma sobre a tabela `devices` legada que o teste
monta (só `device_id` e `stripe_session_id`):

```text
ok    0001_stripe_payment_ledger
ok    0002_device_possession
ok    0003_stripe_dispute_lifecycle
ok    0004_google_play_purchase_ledger
FALHA 0005_machine_anchor -> no such column: first_seen_at
ok    0006_device_admin_profile
```

A `0005` termina com:

```sql
CREATE INDEX IF NOT EXISTS devices_machine_anchor
    ON devices (machine_anchor, first_seen_at);
```

`machine_anchor` ela mesma adiciona, mas **`first_seen_at` não é adicionado por
migration nenhuma** — só existe em quem nasceu do `schema.sql` moderno. Confirmado:
nenhum `ADD COLUMN first_seen_at` em todo o diretório de migrations.

Ou seja: um banco criado do zero funciona; um banco **antigo, real, em produção**
quebra ao migrar. É exatamente o caso que a migration existe para atender.

### Correção sugerida

Adicionar `ALTER TABLE devices ADD COLUMN first_seen_at TEXT;` antes do índice na
`0005` — guardado para não falhar quando a coluna já existir, que é como as outras
migrations deste diretório tratam o mesmo problema.

Vale conferir também se `updated_at` (que o `schema.sql` usa no índice
`devices_by_archive`) tem o mesmo buraco.

### Por que não corrigi

O arquivo é de outra frente de trabalho em andamento, e mexer numa migration que
outra pessoa está escrevendo é a forma mais rápida de criar um conflito difícil de
enxergar. Se quiser, corrijo — é uma linha.

---

## BUG-012 — Reinstalar o app perdeu a licença paga e voltou ao teste de 7 dias

**Status:** investigado, correção pendente — **prioridade alta, envolve dinheiro**
**Reportado em:** 2026-08-13
**Ambiente:** Windows, licença de 30 dias ativada e perdida após reinstalar

### Sintoma

O usuário ativou o app por 30 dias, desinstalou e instalou de novo. A chave de
ativação mudou e ele voltou ao teste de 7 dias — perdendo o que pagou.

### Causa — o `deviceId` depende de uma chave gerada por instalação

O id é derivado de **dois** valores
([DeviceFingerprint.kt:305-310](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/license/DeviceFingerprint.kt#L305-L310)):

```kotlin
deviceId = SHA-256(publicKeyDer + installationId)
```

- `installationId` vem do **MachineGuid** do Windows e é estável — sobrevive a
  reinstalar o app, muda só ao formatar. Essa parte está correta.
- `publicKeyDer` é de um par EC **gerado do zero** toda vez que o arquivo de
  identidade não existe
  ([DeviceFingerprint.kt:230-233](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/license/DeviceFingerprint.kt#L230-L233)).

Ou seja: **basta o arquivo sumir para o id mudar, mesmo na mesma máquina.**
O MachineGuid estável não protege nada, porque a chave nova domina o hash.

O arquivo fica em
`%LOCALAPPDATA%\lucasserafin94\IPTVBURO\device-identity.dpapi` — fora da pasta
que o MSI gerencia (`%LOCALAPPDATA%\IPTVBURO`), então o desinstalador **não**
deveria apagá-lo. Confirmado numa máquina real: o arquivo de 8 de agosto
sobreviveu a várias reinstalações. Falta descobrir o que o removeu no caso do
usuário — limpeza manual, um "remover dados do app", ou o DPAPI ter falhado ao
decifrar (nesse caso `load()` devolve null e um id novo é gerado).

### Correção proposta — o id deve depender só da máquina

Derivar o `deviceId` **apenas** do `installationId`, e deixar o par de chaves
como prova de posse (que é o papel dele), não como parte da identidade. Assim,
mesma máquina = mesmo id, sempre, e apagar arquivos não reseta nada — que é
exatamente a propriedade anti-fraude que o comentário do `MachineAnchor` diz
querer.

Precisa de migração: quem já tem id no formato antigo não pode perdê-lo.

### Ainda a confirmar

Pedir ao usuário o `iptvburo.log` da execução em que o id mudou, e verificar se
`device-identity.dpapi` existia naquele momento. Isso separa "arquivo apagado"
de "DPAPI falhou ao decifrar".

---

## TAREFA-013 — Chave reutilizável pelo dono, bloqueada para os outros

**Status:** pendente — desenhado, não implementado
**Solicitado em:** 2026-08-13

### O pedido

O usuário quer que a chave dele:

1. apareça no app, para ele guardar;
2. continue valendo pelo tempo comprado;
3. **não** possa ativar mais de uma instalação.

### O que já existe e funciona

O servidor **já** faz uso único. `redemption_keys` tem `redeemed_by`, e o resgate
é atômico: `UPDATE ... WHERE redeemed_by IS NULL`, com teste de concorrência
provando que duas máquinas simultâneas resultam em `[200, 409]`
([worker.test.mjs](../../services/license-server/test/worker.test.mjs)). Ou seja,
**a parte de "não pode ativar em dois PCs" está pronta.**

### O que falta — e é o que causou o prejuízo

```js
if (key.redeemed_by) return { ok: false, error: 'already_used', status: 409 };
```

Isso bloqueia **inclusive o próprio dono**. Combinado com o BUG-012, o efeito é:
o id muda, a chave dele não serve mais nem para ele, e ele precisaria comprar
outra. É o pior resultado possível para quem pagou.

**Correção:** trocar por "já usada **por outro dispositivo**":

```js
if (key.redeemed_by && key.redeemed_by !== deviceId) return already_used;
```

O mesmo dispositivo reativa quantas vezes precisar; outro continua recusado. Sem
isso, corrigir o BUG-012 sozinho não devolve a licença de quem já perdeu.

### Mostrar a chave no app

Hoje o app não exibe a chave em lugar nenhum — a tela de ativação mostra o
*código do dispositivo*, que é outra coisa. Falta uma área em Opções com a chave,
a data de validade e um botão de copiar, com o aviso de que perdê-la significa
comprar de novo.

### A tela de ativação não diz nada (reportado em 2026-08-13)

Ao digitar uma chave, a tela não informa **o que aquela chave é**. O usuário quer
ver, antes ou depois de colar:

- de quantos dias é a chave (30 dias, 365 dias…);
- se ela está livre, **em uso por este aparelho**, ou já usada por outro;
- até quando vale.

Hoje o servidor sabe tudo isso — `grant_days`, `redeemed_by`, `valid_until` —, e
os erros que ele devolve já distinguem os casos (`already_used`, `key_expired`,
`unknown_key`). O que falta é uma consulta **antes** do resgate e uma tela que
mostre a resposta em vez de só reagir ao sucesso ou à falha.

Cuidado ao implementar: um endpoint que descreve qualquer chave transforma o
servidor num oráculo para adivinhar códigos. Deve ficar atrás do mesmo rate
limiter do resgate e **nunca** revelar o dispositivo dono — apenas "livre", "sua"
ou "em uso", que é o que o usuário precisa saber.

### Reinstalação legítima vs. fraude

Vale registrar o limite: com a chave presa ao dispositivo, alguém que troca de PC
de verdade fica travado. O painel de admin já resolve isso à mão (`/admin/grant`),
e como há um único administrador, atender esses casos é mais barato e mais seguro
que inventar um sistema de transferência automática — que seria justamente o
caminho que um fraudador tentaria abusar.

---

## TAREFA-015 — Enviar para a TV / notebook (celular como controle)

**Status:** ✅ IMPLEMENTADO em 2026-08-13 — protocolo, receptor do Windows,
descoberta e envio no celular, telas de escolha e pareamento.

**Falta testar entre dois aparelhos reais na mesma rede.** Tudo abaixo tem teste
automatizado, incluindo os sockets de verdade no lado do Windows, mas ninguém
ainda enviou um filme do celular para o computador.
**Solicitado em:** 2026-08-13

### O pedido

Estando com o app no celular, tocar num botão e mandar o filme para o notebook ou
para a TV que estiverem com o app aberto na mesma casa. Escolher na lista qual
aparelho recebe. Procurar no celular, que é rápido, e assistir na tela grande.

### É possível, e o app já tem as duas peças

Não precisa de Chromecast, nem de DLNA, nem de servidor na internet:

1. **O que enviar já existe.** `TitleShareLink` já identifica um título de forma
   independente de provedor (título normalizado + ano + tipo). É exatamente o que
   o aparelho receptor precisa para achar o mesmo filme *na lista dele*.
2. **Como entregar já existe em miniatura.** `SingleInstance`
   ([SingleInstance.kt](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/platform/SingleInstance.kt))
   já abre um socket, publica a porta e entrega um link para a instância que está
   rodando. É o mesmo padrão, só que hoje restrito ao *loopback*.

Falta o meio: **descoberta na rede local** (quais aparelhos estão abertos) e um
canal entre máquinas diferentes em vez de dentro da mesma.

### Decisão importante: mandar o *título*, não o *vídeo*

Duas arquiteturas possíveis, e a diferença é enorme:

| | enviar o vídeo (espelhar) | enviar o título (recomendado) |
| --- | --- | --- |
| o que trafega | o stream inteiro | ~200 bytes |
| qualidade | limitada pelo wi-fi do celular | a TV baixa direto da fonte |
| bateria do celular | drena | quase nada |
| credenciais | o celular teria que repassar | **nunca saem do aparelho** |

A segunda é a que as IPTVs boas usam, e é a única compatível com a regra do
projeto de não deixar URL autenticada sair do aparelho. O celular manda "abra
*Duna: Parte Dois (2024)*, no minuto 34"; a TV resolve na própria lista dela e
começa a tocar. Vantagem extra: se a TV já estiver com a mesma lista, funciona
sem o celular ficar ligado.

### Como implementar

1. **Descoberta:** anúncio UDP multicast na rede local (`239.255.x.x`), cada app
   aberto dizendo nome, tipo e porta. Ou mDNS via `jmdns`, se valer a dependência.
2. **Canal:** o receptor abre um `ServerSocket` — igual ao `SingleInstance`, mas
   ligado à rede local em vez do loopback.
3. **Emparelhamento:** o receptor mostra um número de 4 dígitos que o remetente
   digita uma vez. **Sem isso, qualquer um no mesmo wi-fi manda vídeo para a sua
   TV** — inclusive num prédio com rede compartilhada.
4. **Retomada:** mandar junto a posição atual, para continuar de onde parou.
5. **Interface:** botão de TV na tela do filme, listando só o que respondeu ao
   anúncio.

### Riscos que precisam de cuidado

- **Isto abre uma porta de rede real**, ao contrário de tudo que o app faz hoje.
  O emparelhamento e um limite de tamanho na mensagem não são opcionais.
- Muitas redes domésticas bloqueiam multicast entre wi-fi e cabo; precisa de um
  caminho manual (digitar o IP) para quando a descoberta falhar.
- O app de TV **ainda não existe**. Dá para construir e testar tudo entre
  **celular ↔ Windows** primeiro, e a TV entra depois usando o mesmo protocolo.

### Ordem sugerida

Windows recebendo, celular enviando. É o par que já tem os dois apps prontos, e
prova o protocolo inteiro antes de existir uma terceira plataforma.

---

## TAREFA-014 — Seletor de áudio 2.0 / 5.1 / 7.1 na tela do filme

**Status:** ✅ IMPLEMENTADO em 2026-08-13
**Solicitado em:** 2026-08-13

### Resposta curta

**Sim, dá para fazer, e sem depender de nenhuma empresa ou biblioteca nova.** O
VLC que já vem empacotado no app tem tudo. Verificado rodando o binário que o
próprio build gera:

```text
--directx-audio-speaker={Windows default,Mono,Stereo,Quad,5.1,7.1}
--spatialaudio-headphones          (binaural, para fone)
 Headphone virtual spatialization effect (headphone)
   "…feeling that you are standing in a room with a complete 7.1 speaker set
    when using only a headphone"
 Audio Spatializer (spatializer)   --spatializer-width, -roomsize, -wet, -dry
```

Ou seja: o "emular 7.1 num vídeo que não tem 7.1" é um recurso pronto do VLC,
sob licença livre (LGPL/GPL), já dentro do produto. Não precisa de SDK
proprietário nem de acordo com fabricante.

### O que é honesto prometer

Vale separar duas coisas, porque a diferença importa para não enganar o usuário:

- **Fone de ouvido:** o efeito é real e convincente. O `headphone`/binaural
  simula posicionamento espacial de um conjunto 7.1 em dois canais, e é
  perceptível.
- **Caixas de som:** um estéreo 2.0 mandado para 6 ou 8 caixas é *upmix* — o som
  ocupa mais alto-falantes, mas não existe informação nova. Não vira um 7.1 de
  verdade, e chamar isso de "7.1" na interface seria mentira. O rótulo honesto é
  algo como "Ampliar para 5.1" ou "Simular surround".

### Como implementar

1. Ler os canais reais da faixa (o VLC informa) e mostrar na tela do filme o que
   a mídia **tem**: `2.0`, `5.1`, `7.1`.
2. Oferecer um seletor de saída ao lado, com o que o sistema **pode** entregar,
   passando `--directx-audio-speaker` ao iniciar o motor.
3. Um botão separado para fone (`--spatialaudio-headphones`), que é o caso em que
   a simulação realmente entrega.
4. Guardar a escolha por perfil, como as legendas já são guardadas.

### O trabalho real não é o áudio

É o ciclo de vida do player. O VLC monta a cadeia de áudio **quando o motor
inicia**, então trocar de modo exige recriar o player — exatamente como já
acontece hoje com o estilo de legenda
([DesktopPlayerOverlay.kt:84](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L84)).
Trocar no meio do filme significa reabrir o stream e voltar à posição salva. É
factível — a continuidade já existe —, mas é aí que mora o risco, não nas flags.

### Cuidado

Mandar `7.1` para uma placa configurada como estéreo pode **emudecer** o áudio em
vez de melhorá-lo. A lista oferecida deve refletir o que o Windows declara para o
dispositivo de saída, e deve haver caminho de volta para "padrão do sistema" sem
precisar reinstalar nada.

---

## TAREFA-011 — Nitidez em 4K e fluidez de 30 a 360 Hz

**Status:** ✅ IMPLEMENTADO em 2026-08-13
**Solicitado em:** 2026-08-12

### Pedido

Otimizar para monitores de 30, 60, 120, 144, 165, 240 e 360 Hz, e para telas 4K:
imagem lisa, sem serrilhado, tudo fluido.

### O que já foi medido (não é suposição)

**A fluidez em alta taxa já deve funcionar.** As 50 animações do app são baseadas
em *tempo* (`tween`, `animateFloat`, `infiniteRepeatable`), não em contagem de
quadros, e o Compose Desktop sincroniza com o monitor. Uma animação de 220 ms
dura 220 ms em 60 Hz e em 360 Hz — só fica mais suave. **Não há taxa de quadros
fixa em lugar nenhum do código.** Isso precisa ser confirmado num monitor real de
alta taxa, mas por construção não há nada a corrigir aqui.

**O serrilhado em 4K, por outro lado, tem causa concreta e medida.** As imagens
são pedidas ao TMDb em resoluções pensadas para 1080p e ampliadas na tela:

| elemento | pedido ao TMDb | tamanho em 4K @200% | fator |
| --- | --- | --- | --- |
| pôster do detalhe (248 dp) | `w342` | 496 px | **1,45× ampliado** |
| fundo (backdrop, tela cheia) | `w1280` | 3840 px | **3× ampliado** |
| foto do elenco | `w185` | 370 px | **2× ampliado** |

O fundo é o pior caso: além de 3× de ampliação, ele ainda recebe um `scale()` de
Ken Burns até 1,08 ([XtreamWorkspace.kt:1264](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/XtreamWorkspace.kt#L1264)),
que amplia mais ainda o borrão.

Nenhuma configuração de DPI, renderizador (Skia/Direct3D) ou qualidade de
filtragem existe hoje — está tudo no padrão.

### O que fazer

1. **Pedir a imagem no tamanho certo para a tela.** O TMDb oferece `w500`, `w780`,
   `w1280` e `original` para pôster, e `w1280`/`original` para fundo. A escolha
   deve considerar a densidade real (`LocalDensity`), não uma constante — em 1080p
   o `w342` continua correto e baixar `original` seria desperdício de banda e
   memória.
2. **Informar o tamanho de destino ao Coil.** `BuroRemoteArtwork` não passa
   `.size(...)`, então o Coil decodifica no tamanho original e o Compose reescala.
   Passar o tamanho do destino faz a decodificação já sair na medida certa —
   melhora nitidez **e** reduz memória.
3. **Revisar a filtragem de escala.** Verificar se vale `FilterQuality.High` nos
   pôsteres; é mais caro por quadro, então precisa ser medido antes de adotar.
4. ~~**Conferir o DPI awareness do instalador.**~~ **Verificado: não era
   necessário.** Medido em vez de presumido — um programa mínimo em Java reporta
   `scaleX=1.25` nesta máquina, ou seja, a JVM já lê a escala real do Windows e é
   per-monitor DPI aware por padrão desde o JDK 9. A preocupação registrada aqui
   era exagerada.
5. **Testar o vídeo separadamente.** O player é o VLC, fora do Compose: a
   nitidez dele depende do stream e do decodificador, não destas mudanças.

### Cuidado ao implementar

Pedir `original` para tudo é a solução preguiçosa e **piora** o app: uma
prateleira de 20 pôsteres em resolução máxima é dezenas de MB por trilho, contra
um heap de 768 MB — exatamente o tipo de pressão de memória que já causou
travamento (ver AUDITORIA-010). A escolha tem que ser proporcional à tela.

### Como verificar

Comparar capturas em 1080p e em 4K, antes e depois, ampliando a mesma região.
Medir também a memória e o tempo de carregamento de uma prateleira, para provar
que a nitidez não custou fluidez.

---

## AUDITORIA-010 — Segurança, estabilidade e desempenho (varredura completa)

**Status:** ✅ CONCLUÍDA em 2026-08-12
**Escopo:** aplicativo Windows e site

### Segurança — o que foi verificado e estava certo

Vale registrar o que **não** precisou mudar, para a próxima auditoria não repetir
o trabalho:

- nenhum `println` no app ou nos pacotes registra URL, usuário, senha ou token;
- os modelos com dados sensíveis (`Channel`, `ParsedChannel`, `XtreamCredentials`,
  `XtreamCatalogItem`) têm `toString` com redação;
- nenhum `TrustManager` ou `HostnameVerifier` customizado — não há como o app
  aceitar um certificado inválido;
- `local.properties`, que contém a chave TMDb real, está no `.gitignore` e a
  chave **não** aparece no APK gerado (verificado no binário);
- as credenciais Xtream ficam num blob DPAPI, ilegível fora da conta do usuário.

Três "achados" da varredura automática foram falsos positivos, verificados um a
um: `ImportedCatalog` só contém `Channel` (que redige), `PendingEntry` e
`ParsedStream` são `private` e nunca impressos.

### Correções aplicadas

**1. Teto de resposta bufferizada (memória).** `request()` mantém o corpo como
bytes, String e árvore JSON **ao mesmo tempo**, e usava o mesmo teto de 512 MiB
do caminho streaming — contra um heap de 768 MB. Uma resposta grande podia tomar
o heap inteiro e matar o app com `OutOfMemoryError`. Agora o caminho bufferizado
tem teto próprio de 32 MiB, com `require` impedindo que ele ultrapasse o do
streaming. Dois testes fixam isso.

**2. Marcador preso em `ensureCastPhoto`.** O `remove` do marcador "em andamento"
só rodava depois de uma busca bem-sucedida. Uma exceção do TMDb deixava aquele
nome marcado para sempre: a foto nunca mais carregava naquela sessão, e o
conjunto crescia uma entrada por falha, sem nada limpar.

**3. `heroSynopsis` sem limite.** Só era esvaziado ao trocar de fonte. Cada
entrada é um parágrafo de sinopse e o destaque roda, então uma sessão longa
acumulava uma por título já exibido. Limitado como as fotos do elenco.

**4. Polling de link compartilhado.** O tratador acordava uma corrotina **uma vez
por segundo durante toda a sessão** para olhar um slot quase sempre vazio — 3.600
despertares por hora num app ocioso, para um evento que a maioria nunca dispara.
A thread que escuta já sabia a hora exata; agora ela sinaliza. A retentativa para
link que chega antes do catálogo passou a ser keyed no `xtreamSummary`, que é o
único momento em que a resposta pode mudar.

**5. Cabeçalhos do site.** O CSP já era forte, mas faltava **HSTS** — sem ele, a
primeira visita por http pode ser interceptada antes do redirecionamento.
Adicionados `Strict-Transport-Security`, `Cross-Origin-Opener-Policy` e
`Cross-Origin-Resource-Policy` (este último `cross-origin` só em `/t/`, senão
bloquearia a capa do TMDb que a página existe para mostrar). O
`assetlinks.json` passou a ser servido como `application/json` com CORS aberto,
que é o que o Android exige.

### Verificado e já correto (não mexido)

- `CompactXtreamCatalog` é realmente colunar (`IntArray` para numéricos), não
  aloca um objeto por item;
- `page()` só materializa a página visível, não o catálogo;
- o executor do player tem `shutdownNow` e threads daemon;
- o `shutdownHook` do VLC **não** é daemon — e está certo assim, um hook daemon
  não roda;
- as flags da JVM (`-Xmx768m`, G1 com pausa de 100 ms, devolução de heap ao SO)
  estão bem escolhidas e documentadas.

---

## BUG-009 — A atualização pelo app APAGOU o aplicativo

**Status:** ✅ CORRIGIDO em 2026-08-12 — o mais grave reportado até aqui
**Reportado em:** 2026-08-12
**Ambiente:** `v2.0.0-alpha.1` instalado do GitHub, atualizado pelo próprio app

### Sintoma

Atualizar por **Opções → Buscar atualização** funcionou aparentemente: barra de
progresso, pedido para reiniciar. Depois de reiniciar, **o aplicativo não existia
mais** — sumiu da máquina. Nada foi instalado no lugar.

### Causa raiz — `windowsPackageVersion` era a constante `"2.0.0"`

Em `apps/desktop/build.gradle.kts`, a versão que o Windows Installer enxerga era
fixa:

```kotlin
val windowsPackageVersion = "2.0.0"   // alpha.1, alpha.2 e alpha.3, todas iguais
```

Com o mesmo `upgradeUuid` e o **mesmo ProductVersion**, o Windows não vê upgrade
nenhum a fazer. A sequência que o script executa era então:

1. `msiexec /i ... REINSTALLMODE=amus` — não faz nada útil, retorna erro;
2. cai no fallback: `msiexec /x {ProductCode}` — **desinstala o app**;
3. `msiexec /i ...` de novo — falha pelo mesmo motivo;
4. `goto :failed`, que tenta relançar um app que **acabou de ser removido**.

Resultado: máquina sem aplicativo. E como o script roda com `/min` (janela
minimizada), o usuário não via mensagem alguma — o app simplesmente sumia.

O comentário no próprio código já dizia *"doing it first is what once deleted the
app outright"*, ou seja, essa classe de falha já tinha sido vista antes; o que
faltava era a causa real, que é a versão constante.

### Correção aplicada

**1. A versão do MSI passa a crescer a cada preview.** O número do preview vira o
campo de patch:

| versão do app | ProductVersion do MSI |
| --- | --- |
| 2.0.0-alpha.2 | 2.0.2 |
| 2.0.0-alpha.3 | 2.0.3 |
| 2.0.0-alpha.4 | 2.0.4 |

Assim o Windows reconhece um upgrade de verdade e a primeira tentativa (`/i`)
resolve, sem nunca chegar ao passo que desinstala.

Quem está com uma build antiga (ProductVersion `2.0.0`) atualiza normalmente,
porque `2.0.4 > 2.0.0`.

**2. O caminho de falha deixou de ser silencioso e destrutivo.** Se a remoção
chegar a acontecer e a instalação falhar, o script agora:

- tenta instalar de novo, e depois mais uma vez **sem** `/passive`, para o Windows
  poder mostrar o que está bloqueando;
- se ainda assim falhar, vai para `:removed_and_failed` — um caminho novo, que
  **não** tenta relançar um app inexistente;
- explica na tela que a versão anterior foi removida, informa onde está o
  instalador, abre a pasta dele no Explorer e usa `pause` para a mensagem não
  desaparecer.

**3. Feedback melhor durante a atualização** (pedido junto):

- a porcentagem agora vem acompanhada do tamanho: `45%  (129 / 286 MB)` — numa
  linha lenta, um percentual sozinho não distingue "baixando" de "travado";
- a mensagem de reinício explica o ciclo completo: o app fecha, o Windows
  instala, e **o app abre sozinho** — em PT, EN, ES, DE e IT.

### Testes

Dois testes novos em `UpdateScriptTest`, ambos cobrindo exatamente o que
aconteceu: que a falha depois de uma remoção tem caminho próprio, não passa pelo
relançamento, não apaga o instalador e mantém a mensagem na tela; e que a
reinstalação é tentada mais de uma vez antes de desistir. Total do arquivo: 12.

### Ainda não confirmado

A correção é sólida por construção, mas **não foi testada com uma instalação real
sendo atualizada** — isso exige uma máquina com a build antiga instalada. É o
próximo teste que vale fazer.

---

## BUG-008 — Assinaturas: "Séries" e "Esta semana" vazias; "Em breve" vazia; lista parece congelada

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** `v2.0.0-alpha.2`

### Sintomas relatados

1. clicar em **Séries** não mostra nada;
2. **Esta semana** não mostra nada;
3. **Em breve** não mostra nada;
4. a lista de Filmes parece a mesma há dois ou três dias.

### Causa 1 (a principal) — um clique perdido deixa a aba vazia para sempre

`loadStreamingShelves` recusava qualquer carregamento enquanto outro estivesse em
curso, com um booleano único. Abrir Assinaturas dispara o carregamento de Filmes;
clicar em **Séries** durante esses segundos caía em `if (streamingLoading) return`
— e **nada reagendava o pedido**. A aba ficava vazia pelo resto da sessão.

Agora o carregamento em curso é registrado com o *tipo* (`loadingKind`), e só
recusa um pedido duplicado do mesmo tipo. É a mesma classe de falha que o próprio
arquivo já documenta em `DesktopAppState.kt:1680` — a flag que ficava presa.

### Causa 2 — diretório de provedores errado para séries

`watchProviderDirectory` pedia sempre `watch/providers/movie`. Nas primeiras
posições no Brasil isso traz Google Play Movies e Apple TV Store, lojas de filme
que não carregam série; como serviço sem títulos é descartado, gastavam-se slots
à toa. Passou a pedir `watch/providers/tv` para as abas de série.

Medido, não presumido: dos 12 primeiros, o diretório de filmes rende **9** com
séries e o de TV rende **10**. É uma melhora modesta — sozinha **não** explicaria
uma aba totalmente vazia, e por isso a causa 1 é a principal.

### Causa 3 — "Em breve" perguntava algo impossível

A aba pedia, *por provedor*, filmes com data futura. Um filme que ainda não
estreou não está em serviço nenhum, então a resposta era vazia por construção.
Medido por provedor, com janela de um ano: Netflix 1, Prime 0, Disney 0, Apple 0.

Redefinida para a pergunta que o usuário faz — "o que ainda vai entrar no
catálogo": lançamentos de cinema dos últimos 6 meses que **nenhum** serviço
carrega por assinatura, num trilho único (nenhum serviço é dono deles). A
verificação é por título, porque a API não sabe expressar "não está em ninguém".

Confirmado: 305 filmes elegíveis hoje, e *Homem-Aranha: Um Novo Dia* retorna
`flatrate: nenhum` — exatamente o que a aba deve listar.

### Sintoma 4 — a lista parecer congelada

Explicado, sem alteração de código. As prateleiras vêm de `sort_by=popularity` /
`first_air_date.desc` na TMDb e há cache em disco por região e tipo, relido no
mesmo dia. É esperado que mude devagar: o catálogo da Netflix não muda todo dia.
Se ficar preso por muitos dias, aí é bug — vale reportar de novo com a data.

### Efeito colateral corrigido no caminho

`TmdbClient.get()` fazia `.asJsonObject` sem checar. A TMDb responde alguns erros
com um *array*, e isso lançava `ClassCastException` — escapando do `runCatching` e
virando crash em vez do `null` que todo chamador espera. Agora é verificado.

---

## BUG-006 — Assinaturas: "disponível somente no app", sem capa, sinopse, trailer nem foto do elenco

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** `v2.0.0-alpha.2` instalado do GitHub, chave TMDb adicionada pelo usuário

### Sintomas relatados

Depois de adicionar a chave da API do TMDb, a seção **Assinaturas** passou a
aparecer. Ao abrir um filme qualquer nela:

1. aparece "disponível somente no aplicativo TV Guru" — não mostra onde comprar
   ou assinar de verdade;
2. não aparece capa nem sinopse;
3. não aparece trailer;
4. em **Filmes** (catálogo normal), as fotos do elenco não carregam.

### Causa identificada — item 1

`TmdbStreamingCatalogue` responde duas perguntas por caminhos diferentes, e um
deles é falho:

- `pageFor()` usa o **id numérico do TMDb** — correto
  ([TmdbStreamingCatalogue.kt:72-75](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbStreamingCatalogue.kt#L72-L75));
- `detailsFor()` — que responde *onde assistir* — joga o id fora e busca por
  **texto do título + ano**
  ([TmdbStreamingCatalogue.kt:78](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbStreamingCatalogue.kt#L78)).

Pior: `TmdbClient.watchProviders()` chama `findMovieId()` e monta a URL fixa
`movie/$movieId/watch/providers`
([TmdbClient.kt:226-232](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt#L226-L232)),
e `findMovieId` pesquisa em `search/movie`
([TmdbClient.kt:484-496](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt#L484-L496)).

Ou seja:

- **para série, está errado por construção** — procura uma série no endpoint de
  filmes, então acha o filme errado ou nada;
- **para filme, é uma busca por texto** que já tinha o id exato na mão; título
  traduzido, com pontuação diferente ou ano ausente devolve outro filme ou nenhum.

Quando não acha, a lista de ofertas fica vazia e a tela cai no texto de
indisponibilidade — que é exatamente o "disponível somente no TV Guru".

Vale notar que o próprio código já avisa disso: o comentário em
`TmdbStreamingCatalogue.kt:60-62` diz que "null não é o mesmo que indisponível, e
quem chama não deve renderizar assim". A tela está fazendo justamente isso.

### Correção aplicada

**Item 1 — ofertas.** `watchProviders()` passou a receber o **id do TMDb** e um
`isSeries`, usando `tv/{id}/watch/providers` para série. `detailsFor()` para de
jogar fora o id que já tem. `findMovieId()` foi removido: existia só para
recuperar um id que o chamador já possuía.

Verificado contra a API real, não só contra mocks:

| endpoint | resposta para Game of Thrones (id 1399) |
| --- | --- |
| `tv/1399/watch/providers` (correto) | `HBO Max` |
| `movie/1399/watch/providers` (o que o código fazia) | **404 — not found** |

Esse 404 é exatamente o que virava "disponível somente no TV Guru".

**Item 4 — fotos do elenco.** `ensureCastPhoto` guarda `null` quando a busca
falha, para não repetir a consulta a cada recomposição. Só que toda tentativa
feita **antes** da chave existir também é uma falha, e `rebuildMetadataClients()`
não limpava esse cache — então `key in castPhotos` recusava tentar de novo e o
elenco de qualquer filme aberto antes de configurar a chave ficava sem foto para
sempre. O cache agora é descartado junto com o cliente que o produziu.

**Itens 2 e 3 — capa, sinopse e trailer.** Auditado depois, em vez de deixado
como suposição. O caminho inteiro está correto e ligado:

- `pageFor()` usa o id e o endpoint certo por tipo
  ([TmdbClient.kt:533](../../packages/metadata-client/src/main/kotlin/com/lucasserafin94/iptvburo/metadata/TmdbClient.kt#L533));
- a tela chama `pageFor` e guarda o resultado
  ([DesktopAppState.kt:1796](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L1796));
- o valor é passado para o composable
  ([DesktopApp.kt:410](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/DesktopApp.kt#L410));
- e o composable desenha capa, sinopse, elenco e trailer
  ([SubscriptionsWorkspace.kt:551-585](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/app/SubscriptionsWorkspace.kt#L551-L585)).

A API também devolve tudo: para *Duna: Parte Dois*, sinopse em pt-BR, capa, 98
pessoas no elenco e 3 vídeos. Conferido que a sinopse em pt-BR não vem vazia nos
títulos recentes testados, então não há necessidade de fallback de idioma.

Ou seja, não havia um segundo defeito aqui: a única forma de a tela ficar vazia
era o cliente TMDb obsoleto do item 4 — mesma causa, mesma correção. Continua
valendo confirmar em uso, mas a auditoria de código e de API não achou nada mais.

### Ainda em aberto

A interface continua sem distinguir **"o TMDb não sabe"** de **"não há oferta
nesta região"**. As duas ainda viram a mesma frase. Com as ofertas voltando
corretamente o caso fica muito mais raro, mas a distinção continua valendo.

---

## BUG-007 — Espaço e clique não pausam o vídeo

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12 (segundo testador, outro computador)
**Ambiente:** `v2.0.0-alpha.2` instalado do GitHub, Windows

### Sintoma

Durante a reprodução, a barra de espaço não pausa, e clicar no vídeo também não.

### Causa provável

O handler existe e está correto
([DesktopPlayerOverlay.kt:216-219](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L216-L219)),
com foco pedido na abertura
([DesktopPlayerOverlay.kt:190-191](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L190-L191)).
O problema é que **a superfície de vídeo do VLC é uma janela nativa embutida**, e
quando ela recebe o clique, o foco de teclado vai para ela — o evento nunca chega
ao Compose.

Isso não é especulação: o próprio código já registra esse comportamento em outro
contexto, em `DesktopPlayerOverlay.kt:227`, explicando que a barra flutuante em
tela cheia existe porque "confiar só no F11 deixava o usuário preso quando a
superfície de vídeo embutida detinha o foco do teclado e a tecla nunca chegava".

Ou seja: o mesmo mecanismo que quebrava o F11 quebra o espaço. E isso também
explica o clique — não há handler de clique **sobre a superfície de vídeo**; os
dois `onClick = controller::togglePlayback` existentes
([:336](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L336),
[:469](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/DesktopPlayerOverlay.kt#L469))
são dos botões da barra de controles, não do vídeo.

Há ainda uma segunda condição: o espaço só age se `state.ready` for verdadeiro.
`ready` exige que o VLC já tenha reportado `playing`, `paused` ou `stopped`
([VlcDesktopPlayer.kt:784](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/playback/VlcDesktopPlayer.kt#L784)),
então nos primeiros segundos a tecla é ignorada de propósito. Se o testador
apertou logo no início, esse é um segundo motivo somado ao primeiro.

### Correção aplicada

**Espaço.** O `KeyEventDispatcher` global já existia neste arquivo e já resolvia
exatamente este problema — só tratava `Escape` e `F11`. `VK_SPACE` foi somado a
ele. O dispatcher vê a tecla antes de qualquer componente, então funciona
independentemente de quem detém o foco. Retornar `true` consome o evento, então
os outros dois handlers (Compose e canvas) não disparam junto: **não há duplo
toggle** — verificado contra o contrato do AWT, não presumido.

**Clique.** `createComponent` já aceitava um parâmetro `onClick` — adicionado
para o multiview — e o player normal simplesmente nunca passava um. Agora passa
`togglePlayback`, guardado por `state.ready` como os demais caminhos.

Também alinhei o handler do canvas, que chamava `togglePlayback()` sem checar
`ready`, ao contrário dos outros dois.

---

## BUG-001 — "Catálogo Xtream compatível" aparece com o catálogo já carregado; app às vezes trava

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** instalação limpa a partir do release do GitHub, `IPTV BURO v2.0.0-alpha.1`, Windows

### Sintoma

Após instalar do zero e adicionar a lista, a tela **Início** mostra o card de erro
`O servidor não retornou um catálogo Xtream compatível.` — enquanto o cabeçalho
da mesma tela informa `1 fontes · 2258 itens`. Ou seja, a fonte conectou e um
catálogo carregou, mas a Home reporta incompatibilidade. Em algumas execuções a
janela também congela por vários segundos.

### Causa provável

Duas falhas distintas se somam:

**1. A mensagem de erro é enganosa para qualquer falha não-Xtream.**

`DesktopAppState.toSafeXtreamMessage()`
([DesktopAppState.kt:4333](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L4333))
faz `when ((this as? XtreamClientException)?.reason)`. Qualquer throwable que
**não** seja `XtreamClientException` cai no ramo `null ->`, e um
`XtreamClientException` sem `reason` conhecido cai em `INVALID_RESPONSE`.
A Home usa essa função em
[DesktopAppState.kt:2985](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L2985),
então uma falha de memória, de parsing ou de metadados durante a montagem da
tela inicial é apresentada ao usuário como "catálogo Xtream incompatível" —
diagnóstico errado, que foi exatamente o que confundiu este teste.

**2. `categories()` bufferiza a resposta inteira em memória.**

`XtreamClient.categories()`
([XtreamClient.kt:90](../../packages/xtream-client/src/main/kotlin/com/lucasserafin94/iptvburo/xtream/XtreamClient.kt#L90))
usa o caminho `request()`
([XtreamClient.kt:304](../../packages/xtream-client/src/main/kotlin/com/lucasserafin94/iptvburo/xtream/XtreamClient.kt#L304)),
que lê **todo** o corpo para um `ByteArray`, decodifica para `String` e ainda
monta uma `JsonElement` — três cópias do mesmo conteúdo — com teto de
`DEFAULT_MAXIMUM_RESPONSE_BYTES = 512 MiB`
([XtreamClient.kt:704](../../packages/xtream-client/src/main/kotlin/com/lucasserafin94/iptvburo/xtream/XtreamClient.kt#L704)).
O catálogo em si já usa o parser streaming (`streamCatalog`), mas `categories`
não. Em um provedor grande isso é uma alocação enorme no dispatcher de IO, o que
explica o congelamento; se estourar o heap, o `OutOfMemoryError` resultante volta
pela função do item 1 e vira a mensagem de catálogo incompatível.

A Home ainda dispara `loadCatalog(MOVIE)` e `loadCatalog(SERIES)` de dentro do
build da tela
([DesktopAppState.kt:2849-2856](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/DesktopAppState.kt#L2849-L2856)),
o que concentra o custo todo no primeiro acesso — consistente com o cabeçalho
mostrando apenas os itens de LIVE (2258) enquanto filmes/séries ainda falhavam.

### Correção proposta

1. Separar o mapeamento de erro: só usar a mensagem Xtream quando o erro **for**
   `XtreamClientException`; para o resto, mensagem genérica de falha ao montar a
   Home, e nunca atribuir a causa ao servidor do usuário.
2. Migrar `categories()` para o caminho streaming, como `streamCatalog()` já faz.
3. Reduzir o teto de 512 MiB para um valor defensável para uma resposta de
   categorias.

### Correção aplicada

**1. Mensagem de erro separada por origem.**
`toSafeXtreamMessage()` agora é extensão de `XtreamClientException` — só roda
quando o erro **veio mesmo** do provedor. Todo o resto passa por
`toSafeFailureMessage()`, que atribui a falha ao aplicativo e nomeia o tipo da
exceção (sem a mensagem, que pode conter URL com credencial). Os 7 pontos de
chamada foram migrados.

**2. `categories()` agora é streaming.**
Novo `XtreamCategoryStreamParser`, espelhando o `XtreamCatalogStreamParser` que
já existia. Elimina as três cópias simultâneas da resposta (bytes → String →
árvore JSON) sob o teto de 512 MiB. Limite próprio de 50.000 categorias.

### Testes

6 testes novos em `XtreamClientTest` cobrindo o parser de categorias: array,
objeto indexado, id numérico, entradas inválidas puladas, corpo `false` como
lista vazia, e HTML respondido como `INVALID_RESPONSE`. Antes disso a única
cobertura de `categories()` era o teste de servidor privado, que não roda sem
variáveis de ambiente.

### Ainda não confirmado

O log de diagnóstico (`DiagnosticLog`) da execução que travou não foi coletado,
então não está provado que era exatamente OOM em `categories()`. As duas causas
corrigidas são reais e verificadas por leitura de código; se o congelamento
voltar, o log da execução com erro é o próximo passo.

---

## BUG-002 — `Node has been removed.` após "Redefinir"; o app quebra ao adicionar a lista em seguida

**Status:** ✅ CORRIGIDO em 2026-08-12
**Reportado em:** 2026-08-12
**Ambiente:** Windows, instalação limpa
**Reprodução:** Opções → Redefinir → adicionar lista com chave → erro no splash a 100%

### Sintoma

Caixa de diálogo nativa (Swing, fora do tema do app) com o texto
`Node has been removed.` sobre a tela de splash em `100%`, na etapa
"Organizando a sua lista…". O app não conclui a abertura.

### Causa — confirmada

`DesktopUserStore` guarda **uma única** instância de `Preferences`, resolvida na
construção
([DesktopUserStore.kt:111](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/user/DesktopUserStore.kt#L111)):

```kotlin
private val preferences: Preferences = Preferences.userRoot().node("com/lucasserafin94/iptvburo/user-v1")
```

`resetAll()` chama `removeNode()` nessa mesma instância
([DesktopUserStore.kt:497-500](../../apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/user/DesktopUserStore.kt#L497-L500)):

```kotlin
fun resetAll() {
    preferences.removeNode()
    preferences.flush()
}
```

Pelo contrato de `java.util.prefs.Preferences`, `removeNode()` **invalida o
objeto permanentemente**: qualquer método chamado nele depois — exceto `name()`,
`absolutePath()`, `isUserNode()` e `flush()` — lança
`IllegalStateException("Node has been removed.")`. O store nunca readquire o nó.

Portanto, depois de um "Redefinir", **toda** leitura e escrita de preferência no
processo passa a lançar: perfis, idioma, favoritos, geometria da janela, chave
TMDb. Adicionar a lista logo em seguida grava o estado recém-importado, que é
exatamente onde o erro aparece — no fim do splash. A exceção sobe sem tratamento
até o handler padrão do AWT, o que explica a caixa nativa em inglês em vez de um
erro dentro do tema do app.

Observação: `flush()` na linha 499 é legal (é uma das exceções do contrato), então
o próprio `resetAll()` não falha — o dano só se manifesta na chamada seguinte.

### Correção proposta

1. Tornar o nó re-adquirível em vez de fixo — por exemplo trocar o `val` por um
   acessor que resolve `Preferences.userRoot().node(...)` sob demanda, e fazer
   `resetAll()` descartar a referência após remover.
2. Alternativa mais conservadora, sem mexer no ciclo de vida: em `resetAll()`,
   apagar as chaves (`clear()` mais remoção dos nós filhos) em vez de remover o
   próprio nó. Mantém o objeto válido.
3. Adicionar teste de regressão: `resetAll()` seguido de `load()` e de uma
   escrita deve funcionar. Um teste com `Preferences` em nó temporário cobre isso
   sem tocar no registro real do usuário.
4. Independentemente da correção, o app não deveria mostrar exceção crua em
   caixa nativa. Vale um handler que registre no `DiagnosticLog` e apresente
   mensagem no idioma e no tema do app.

### Correção aplicada

`DesktopUserStore` deixou de guardar o nó como `val`. Agora há um acessor que
verifica a validade (`nodeExists("")` lança exatamente quando o nó foi removido)
e readquire o nó quando necessário; `resetAll()` também substitui a referência
logo após remover. O parâmetro do construtor continua existindo para os testes,
e o caminho de re-aquisição usa `absolutePath()` do nó injetado — então um teste
com nó temporário recria **aquele** nó, não as preferências reais da máquina.

### Testes

Dois testes de regressão em `DesktopUserStoreTest`: `resetAll` seguido de leitura
e escrita, e `resetAll` chamado duas vezes.

**Comprovados contra o código antigo:** revertendo a correção, os dois falham com
`IllegalStateException: Node has been removed.` — o mesmo erro do relato. Com a
correção, os 30 testes do arquivo passam.

O `finally` do helper `withStore` passou a usar `runCatching` ao remover o nó:
um teste que exercita `resetAll` já removeu esse nó, e remover duas vezes lança.

---

## TAREFA-005 — Publicar o site para a prévia do link compartilhado funcionar

**Status:** pendente — depende de deploy manual do Cloudflare Pages
**Registrado em:** 2026-08-12

### O que falta

O código do compartilhamento está no repositório e funciona no aplicativo, mas a
**prévia no WhatsApp ainda não**. Verificado contra o site publicado:

```bash
curl -s "https://iptvburo.pages.dev/t/?id=movie:duna:2024&t=Duna&y=2024" \
  | grep -oE '<meta property="og:[^>]*>'
```

Retorna as tags da **página inicial** (`og:url` = `https://iptvburo.pages.dev/`,
título genérico), não as do título compartilhado. Ou seja: `site/functions/` não
foi publicado no projeto Pages ainda.

Consequência prática: um link compartilhado hoje chega no WhatsApp com a capa e o
texto genéricos do site, em vez da capa e da sinopse do filme. O link continua
funcionando — abre a página, e abre o aplicativo se estiver instalado.

### Como resolver

Publicar `site/` no Cloudflare Pages, garantindo que `site/` seja a raiz do
projeto para que `site/functions/` seja reconhecido como Pages Functions. Ver
`site/functions/README.md`, que documenta isso e traz o comando de verificação.

---

## TAREFA-003 — Republicar o release do GitHub após as correções

**Status:** ✅ CONCLUÍDO em 2026-08-12
**Solicitado em:** 2026-08-12

**Decisão do usuário:** apagar o `v2.0.0-alpha.1` de vez (não rebaixar), depois
de corrigir os bugs. Versão nova: `v2.0.0-alpha.2`.

### Pedido

Depois de corrigir BUG-001 e BUG-002, verificar que tudo funciona, **apagar a
versão publicada hoje no GitHub** e publicar uma nova que funcione.

### Por que não foi feito junto

Três motivos, todos a resolver antes de executar:

1. **`CLAUDE.md` proíbe** criar release ou tag sem instrução explícita. Este
   registro é a instrução — mas ela precisa ser confirmada no momento da
   execução, já que aqui ela chegou como parte de outra tarefa.
2. **Apagar um release publicado é destrutivo e externo.** Quem já baixou o
   `v2.0.0-alpha.1` mantém o binário; quem tiver o link passa a receber 404. Se a
   intenção for substituir o instalador mantendo o histórico, o caminho melhor é
   publicar `v2.0.0-alpha.2` e marcar o anterior como *pre-release*/*deprecated*
   em vez de excluir.
3. **Os bugs ainda não foram corrigidos.** BUG-001 e BUG-002 estão diagnosticados,
   não resolvidos. Publicar agora republicaria as mesmas falhas.

### Pré-condições para executar

- [ ] BUG-001 corrigido e verificado em instalação limpa
- [ ] BUG-002 corrigido, com teste de regressão de `resetAll()`
- [ ] Suíte de testes e `packageMsi` executados com sucesso
- [ ] Teste manual: instalar do zero, adicionar lista, redefinir, adicionar de novo
- [ ] Usuário confirma **excluir** vs **substituir** o release anterior
- [ ] Número da nova versão definido pelo usuário

### O que foi feito

Publicado o `v2.0.0-alpha.2` e **apagado** o `v2.0.0-alpha.1`, incluindo a tag,
conforme a decisão do usuário.

Ordem seguida de propósito: a nova versão foi publicada e verificada **antes** de
apagar a antiga, para nunca existir um intervalo sem download disponível.

Verificações antes de apagar:

- os dois assets subiram com `state=uploaded`;
- a URL de download responde `200`;
- os 8 primeiros bytes do arquivo publicado são idênticos aos do arquivo local e
  correspondem à assinatura de MSI (`d0cf11e0a1b11ae1`) — ou seja, o upload não
  corrompeu nada;
- o `SHA256SUMS.txt` publicado bate com o hash do build local
  (`fcab66edf0e0d63037caa67782a2757dea93c332571751d10671d73dbdf0329f`).

Depois de apagar: `alpha.1` responde `404`, `alpha.2` responde `200`, e a única
tag remota é `v2.0.0-alpha.2`.

**Somente Windows.** A `alpha.1` trazia também um APK de Android; a `alpha.2`
não, porque o trabalho de Android desta sessão não foi commitado nem testado.
Quem quiser o APK precisa da build de Android, que continua pendente.

---

## BUG-004 — `ChildProcessJobTest` falhando (4 testes)

**Status:** ✅ RESOLVIDO em 2026-08-12 — o fixture foi corrigido durante a sessão
**Observado em:** 2026-08-12, ao rodar a suíte do desktop

### Sintoma

`apps/desktop/src/test/kotlin/.../playback/ChildProcessJobTest.kt` falha em 4 de
4 testes:

```text
the job assignment must follow registration, not replace it: both defences apply
the fixture must stay alive; a child that exits immediately proves nothing   (x3)
```

O restante da suíte passa: **601 testes no desktop, 4 falhas — todas aqui** — e
**401 testes em `domain-model`, 0 falhas**.

### Contexto

O arquivo está **não rastreado** no git (`??`), ou seja, é trabalho em andamento
sobre o job object dos processos filhos do VLC, não algo que veio do
`codex/windows-clean-release`. Não tem nenhuma referência ao trabalho de
compartilhamento (verificado: 0 ocorrências de `TitleShareLink`,
`SingleInstance`, `ProtocolRegistration`, `ShareStrings`).

As mensagens sugerem que as falhas são do próprio fixture — o processo filho de
teste termina cedo demais para o teste observar o que pretende observar — e não
necessariamente um defeito no código de produção. Precisa ser confirmado por quem
está escrevendo esse teste.

### Resolução

Era o **fixture**, não o `ChildProcessJob`. O teste iniciava o processo filho com
`java -`, e o JVM responde `Unrecognized option: -` e sai imediatamente — então a
própria asserção de vitalidade do fixture falhava (`the fixture must stay alive`).
Foi trocado pelo *single-file source launcher*, com um `Sleeper.java` temporário
que bloqueia em `Thread.sleep(Long.MAX_VALUE)`.

Vale registrar que a asserção de vitalidade fez o trabalho dela: em vez de os
testes passarem contra um processo morto — que é como uma versão anterior deste
mesmo fixture se enganou — eles falharam alto.

Os 4 testes passam. Suíte completa: **1145 testes, 0 falhas**.
