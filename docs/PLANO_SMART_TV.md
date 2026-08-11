# Plano Smart TV — Samsung Tizen, LG webOS e outras marcas

**Status:** preview inicial. Nenhuma capability foi validada em TV física.
**Branch:** `agent/iptv-buro-0.2-preview`
**Data:** 11 de agosto de 2026

Este documento cobre a entrada do IPTV BURO nas TVs de fabricante, começando
por Samsung Tizen, conforme `ADR-0001` e `GDD_5`.

---

## 1. Por que TV de fabricante é um projeto separado

O IPTV BURO hoje é Kotlin: Android/Android TV (Compose + Media3) e Windows
(Compose Desktop). Samsung e LG **não executam Kotlin**. As duas rodam
aplicações web.

Consequência direta, já prevista no `ADR-0001`:

- **Não é reaproveitável:** UI Compose, ViewModels, Room, Media3/ExoPlayer,
  Hilt — nada disso atravessa.
- **É reaproveitável:** as *regras* do produto. Contratos
  (`packages/contracts`), design tokens (`packages/design-tokens`),
  fixtures e o comportamento definido nos GDDs.

O domínio Kotlin (`packages/domain-model`) não pode ser importado por um app
web. Ele permanece a **fonte da verdade conceitual**: o app de TV
reimplementa as mesmas regras em JavaScript e prova equivalência por testes
sobre as mesmas fixtures.

> Decisão em aberto (não bloqueia a Fase 0): se a duplicação de regras
> crescer, avaliar Kotlin/JS a partir de `domain-model` para gerar uma
> biblioteca consumível pelos apps de TV. Só vale a pena depois que o
> vertical Tizen estiver de pé.

---

## 2. Comparação das plataformas

| Item | Samsung Tizen | LG webOS | Android TV (atual) |
| --- | --- | --- | --- |
| Linguagem | JS/HTML/CSS | JS/HTML/CSS | Kotlin |
| Player | AVPlay | `<video>` + Luna | Media3/ExoPlayer |
| Empacotamento | `.wgt` | `.ipk` | `.apk` |
| Ferramenta | Tizen Studio | webOS CLI / VS Code | Gradle |
| Loja | Samsung Apps TV | LG Content Store | Play Store |
| Navegação | D-pad | D-pad + Magic Remote (ponteiro) | D-pad + toque |

Duas diferenças que mudam o design:

1. **Player.** A AVPlay da Samsung é uma máquina de estados própria, com
   camada de vídeo em hardware. O webOS usa `<video>` HTML padrão, bem mais
   familiar. Por isso o player precisa ficar atrás de uma interface comum,
   com um adapter por plataforma.
2. **Magic Remote.** A LG tem ponteiro na tela; a Samsung não. A UI não pode
   assumir que só existe D-pad — mas também não pode depender de ponteiro.

Por isso a ordem Samsung → LG é intencional: **Samsung é a mais restritiva.**
O que funciona lá se adapta para a LG com menos retrabalho que o inverso.

---

## 3. Estado atual (o que já existe)

Criado nesta etapa, em `apps/samsung-tizen/`:

```text
config.xml        Manifesto Tizen (privilégios, perfil TV, hwkey)
index.html        Camada AVPlay + shelf de cartões
css/style.css     Tokens BURO Nocturne, foco de TV, safe area
js/keys.js        Mapa de teclas do controle remoto
js/player.js      Wrapper da AVPlay (open→prepare→play)
js/app.js         Foco por D-pad e ligação com o player
icon.png          Marca oficial 512×512
SETUP.md          Guia de ambiente
```

E `packages/platform-capabilities/samsung-tizen.json`, validado contra
`packages/contracts/platform-capabilities.schema.json`.

Todas as capabilities entram **conservadoras** (`offline: false`,
`hdr: false`, `pip: false`), conforme a regra do projeto: recurso só aparece
depois de medido em hardware real.

O app usa apenas streams de teste públicos. Nenhuma lista, credencial ou URL
assinada entra no repositório.

---

## 4. Roteiro de aprendizado

Cada fase termina com algo observável. Não avance sem ver o resultado.

### Fase A — Ver o app na tela (você está aqui)

**Meta:** entender o ciclo *empacotar → assinar → instalar*.

1. ~~Instalar Tizen Studio + TV Extensions~~ — **feito**
   (`docs/SETUP_SAMSUNG_TIZEN.md`).
2. ~~Criar o certificado~~ — **feito** (perfil `iptvburo`, ativo).
3. ~~Empacotar o `.wgt` assinado~~ — **feito**.
4. Abrir o app no **simulador** ← *você está aqui*.
5. Navegar entre os cartões com as setas.

**Aprendizado:** por que a TV recusa app não assinado; por que o foco é
manual; por que o layout é 1920×1080 fixo.

> Seu Hyper-V está ativo, então o emulador não abre agora. Use o simulador —
> ele é suficiente para esta fase.

### Fase B — Vídeo de verdade

**Meta:** um stream tocando.

Exige **TV física** ou emulador — o simulador não reproduz via AVPlay.

**Aprendizado:** a máquina de estados da AVPlay e o motivo da tela preta com
áudio (fundo opaco cobrindo a camada de vídeo).

### Fase C — Ligar ao produto

**Meta:** trocar as fontes de teste pelo catálogo real.

Portar para JS, com testes sobre as fixtures existentes:

- parser M3U (espelhando `packages/playlist-parser`);
- cliente Xtream (espelhando `packages/xtream-client`);
- perfis, favoritos e continuidade.

**Aprendizado:** equivalência de comportamento entre plataformas via
fixtures compartilhadas.

### Fase D — LG webOS

**Meta:** portar com a base já madura.

Extrair o que é comum (catálogo, foco, estado) e isolar o player por
adapter. Tratar o ponteiro do Magic Remote.

### Fase E — Distribuição

Contas de desenvolvedor, requisitos de certificação, privacidade e QA em
múltiplas gerações de TV (a Samsung exige compatibilidade com mais de uma).

---

## 5. Riscos conhecidos

| Risco | Impacto | Mitigação |
| --- | --- | --- |
| Sem TV física | Playback não validável | Fase A no simulador; Fase B aguarda hardware |
| Hyper-V bloqueia emulador | Sem teste de vídeo local | Simulador agora; decidir depois |
| Codecs variam por modelo/ano | Stream falha em TVs antigas | Capabilities por modelo; testar 2+ gerações |
| Regras duplicadas (Kotlin/JS) | Divergência de comportamento | Fixtures compartilhadas; avaliar Kotlin/JS |
| Certificação de loja | Atraso na publicação | Estudar requisitos antes da Fase E |
| TV de baixo desempenho | UI lenta | Evitar framework pesado; DOM enxuto |

---

## 6. Limitações desta entrega

O que **está** validado:

- Toolchain completa: Tizen Studio + TV Extensions (até Tizen 10.0).
- Perfil de assinatura `iptvburo` criado e ativo.
- `build-web` + `package` geram um `.wgt` assinado, com o conteúdo correto.
- O app abre no **TV Web Simulator**.

O que **não** está validado:

- **Playback.** O simulador não expõe `webapis.avplay`, então o wrapper da
  AVPlay nunca foi exercitado contra a engine real. É a peça com maior
  chance de precisar ajuste.
- **TV física.** Nenhum teste em hardware; o emulador está bloqueado pelo
  Hyper-V nesta máquina.
- **Navegação por D-pad** foi implementada mas ainda não conferida
  visualmente por um humano no simulador.
- O app não conversa com o domínio do IPTV BURO; usa fontes de teste.
- webOS ainda não tem código.

---

## 7. Próximo passo

Instalar o Tizen Studio e abrir o app no simulador (Fase A). Depois disso,
decidir entre avançar para o catálogo real (Fase C) ou aguardar a TV para
validar vídeo (Fase B).
