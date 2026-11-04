# ADR-014 — Serviços web assinados à parte (GoDorama)

- Estado: implementado (Android/Android TV e Windows; Mac abre no navegador do sistema)
- Data: 3 de novembro de 2026

## Contexto

O dono revende, por um painel de créditos, acesso a dois serviços além de listas IPTV:

- **GoDorama** (godorama.com): microdramas em vídeo vertical.
- **NoPrivado** (noprivado.com): conteúdo adulto.

Em ambos, a conta do cliente é só **usuário e senha no site do próprio serviço**. Não há Xtream, Stalker nem M3U, e não há API pública.

Os termos do GoDorama (godorama.com/termos, versão de 1 de outubro de 2026) dizem:
- **Permitem** assistir "pelo site ou pelo app instalado a partir dele", por maiores de 18 anos, em uma tela por vez.
- **Proíbem** robôs, scripts e acesso automatizado.
- **Proíbem** baixar, gravar ou retransmitir episódios.
- **Proíbem** burlar os endereços temporários de vídeo e o limite de telas.

## Decisão

1. **O GoDorama é um *serviço web*, não uma fonte.** O BURO abre a página do próprio serviço num navegador embutido, e o usuário entra lá com a conta dele.
   - O BURO não lê, não preenche e não guarda a senha.
   - Não lê o conteúdo da página.
   - Não monta trilhos a partir das rotas internas (`/_app/`).
   - Não toca o vídeo no player próprio.
2. **Capabilities reais** (`WebServiceCapability`): só `EMBEDDED_PAGE` e `ACCOUNT_HISTORY`. Não há `NATIVE_RAILS`, `NATIVE_PLAYBACK` nem `OFFLINE_DOWNLOAD`. Trilhos nativos só com um feed oficial do serviço.
3. **Navegação** (`WebServicePolicy.navigation`):
   - páginas `https` do próprio serviço ficam na janela;
   - outras páginas `https` vão para o navegador do sistema;
   - qualquer outro esquema, ou endereço com credenciais, é bloqueado.
4. **Idade**: `minimumAge = 18`, conforme os termos do serviço.
   - O serviço só é oferecido ao perfil adulto, nunca a Kids ou Convidado (`WebServicePolicy.isOfferedTo`).
   - Ativar exige confirmar 18+.
   - A escolha fica guardada por perfil; um perfil que vire Kids perde o serviço em todo lugar.
5. **Sessão**:
   - **Android**: cookies do WebView. Quando outro perfil entrou por último, os cookies são apagados antes de carregar (`WebServiceSettings.claimSession`).
   - **Desktop**: o cache do Chromium é temporário (decisão dos trailers). Por isso os cookies do próprio domínio do serviço são salvos por perfil, selados com `LocalSecretProtector` (DPAPI no Windows, chave no Keychain no Mac), em `web-sessions/` (`WebServiceSessionStore`). Sem protetor disponível, nada é gravado.
   - "Sair da conta" e desativar o serviço apagam a sessão.
6. **Sem mudança de schema**:
   - **Android**: DataStore `web_services`.
   - **Desktop**: preferências `web-services.<perfil>`.
7. **"Só GoDorama"**:
   - **Android**: Fontes ganha a seção "Serviços". Com o serviço ativo e sem lista, o perfil abre na Home com o cartão do serviço.
   - **Desktop**: a configuração inicial ganha "Usar só GoDorama", que cria um perfil adulto sem lista.

## NoPrivado: não integrado

O próprio painel descreve o NoPrivado como "conteúdo adulto (OnlyFans, Privacy e similares)". O site não tem termos de uso.

Revender por créditos o acesso a conteúdo que as criadoras vendem nas próprias plataformas é, salvo prova de licença, **redistribuição de conteúdo pago sem autorização**. Isso cai na regra do `CLAUDE.md` contra contornar paywall e conteúdo protegido, e há ainda o risco de imagem íntima distribuída sem consentimento.

A proteção adulta não resolve, porque o problema é a origem do conteúdo, não quem o vê. A decisão será revista se houver documento de licença ou contrato com as criadoras ou estúdios.

> **Revisão — 4 de novembro de 2026**: o dono aceitou o risco jurídico e pediu a integração do NoPrivado com o mesmo padrão de página embutida: login na própria página, senha nunca vista pelo BURO. Ele agora é um segundo serviço web ao lado do GoDorama — guia própria no Android/TV e item próprio na barra lateral do desktop —, com a confirmação 18+ mantida por padrão. A questão da origem do conteúdo segue em aberto.

## Limitações conhecidas

- **Mac**: abre no navegador do sistema, porque o Chromium embutido só existe no instalador Windows.
- **TV com controle remoto**: o site não foi feito para D-pad e não foi testado numa TV real.
- **Desktop, sessão salva**: a sessão é gravada ao **fechar** a janela do serviço. Se o app for encerrado com a janela aberta, o próximo uso pede login de novo.
- **Android**: o WebView tem um único pote de cookies por aparelho, e a troca entre perfis adultos apaga a sessão anterior. Os cookies dos trailers do YouTube também são apagados nessa troca.
