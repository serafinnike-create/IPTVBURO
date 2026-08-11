# Android ↔ Windows — plano de paridade verificável

Atualizado em 2026-08-09. Este plano separa funções existentes, funções entregues neste corte e
funções ainda bloqueadas. Uma entrada só muda para **entregue** depois de teste e build.

## Objetivo do fluxo móvel

```text
Idioma → aviso legal → verificação assinada da licença → perfis → fonte autorizada → biblioteca
                            ↓ bloqueado
                 ativação / compra / nova verificação
```

Licença do aplicativo e credenciais da fonte não são o mesmo login. A licença decide se o IPTV
BURO pode abrir; Xtream/Stalker/M3U conectam a biblioteca autorizada pelo usuário. Nenhuma senha de
fonte é enviada ao servidor de licenças.

## Corte 1 — implementado

- Verificação Android contra `/v1/validate`, registro em `/v1/register` e resgate em `/v1/redeem`.
- Teste gratuito de 7 dias e bloqueio antes de perfis, fontes, catálogo e player.
- Identidade por UUID aleatório + chave P-256 no Android Keystore; sem MAC do telefone.
- Nonce assinado em cada pedido, chave pública Ed25519 do servidor fixada no aplicativo e licença
  local sempre revalidada criptograficamente.
- Janela offline governada pela política comum: 2 dias no teste e 14 dias para licença paga.
- Tela cinematográfica local com capas em movimento durante preparação e verificação.
- Tela bloqueada com código do aparelho, compra/ativação, nova verificação e chave de uso único.
- Navegação de telefone com menu lateral: Início, Ao vivo, Filmes, Séries, Favoritos, Fontes,
  Perfis e Configurações. Android TV mantém navegação por D-pad.
- Avatar abre Perfis diretamente; Configurações é um destino separado.
- Botão de recarga real em categorias e itens, sem confundir recarga com paginação.
- Chave TMDB v3 opcional, criptografada por perfil, usada para completar trailers ausentes.
- “Continue assistindo” alimentado pelo progresso Room real e isolado por perfil.
- Login Xtream existente tornou-se acessível diretamente por Fontes; usuário e senha continuam
  cifrados no Keystore e redigidos dos diagnósticos.
- Ao escolher um perfil sem nenhuma fonte, o primeiro destino agora é a conexão M3U/Xtream/Stalker,
  deixando o login de usuário e senha visível no fluxo inicial.
- Perfis são roláveis em telas estreitas e os textos do seletor existem em PT-BR, EN, DE e IT.
- Ao voltar do portal de compra, o aplicativo consulta a licença novamente automaticamente.

## Corte 2 — parcialmente entregue

**Entregue — Assinaturas no Android (9 de agosto de 2026).**

- `TmdbStreamingCatalogue` e `TmdbStreamingDiscovery` saíram de `apps/desktop` para
  `packages/metadata-client`. As duas plataformas passaram a compartilhar uma implementação em vez
  de responder “onde assistir” de formas que podem divergir.
- `StreamingDiscoveryRepository` no Android lê a chave cifrada do perfil no ponto de uso, roda toda
  chamada fora da main thread e transforma chave ausente em capability, não em erro.
- O destino aparece no ribbon e no menu lateral do telefone somente quando
  `StreamingDiscoveryCapability` for visível, ou seja, quando existe chave TMDB. `selectSection`
  repete o guard para que nenhuma outra rota abra a tela.
- Cada linha carrega o crédito `Streaming data provided by JustWatch`, exigido por item pelos termos
  deles. A biblioteca do próprio usuário não recebe o crédito, porque não veio deles.
- Nenhuma oferta recebe preço: o TMDB não devolve preço em nenhum bucket, então qualquer valor seria
  inventado. Disponibilidade desconhecida é apresentada como “não podemos dizer”, nunca como
  “indisponível”.
- Textos em PT-BR, EN, DE e IT.

**Entregue — chave TMDB encontrável e funcionando (10 de agosto de 2026).** Além do card que ficava
mudo sem perfil ativo, o relato "coloquei a chave e nada aconteceu" revelou três defeitos maiores,
todos resolvidos adotando o que o Windows já fazia:

- salvar a chave agora recarrega as prateleiras, como `rebuildMetadataClients()` no Windows. Antes
  só um booleano mudava, e o destino ficava vazio até reiniciar o app;
- o Android passou a ler `tmdb.apiKey` de `local.properties` em tempo de build, igual ao Windows, o
  que faz o recurso funcionar sem configuração. O arquivo continua no `.gitignore`;
- a resolução de chave estava duplicada em quatro lugares que podiam discordar. Agora há um único
  `effectiveKey`: chave do perfil, senão a da build. Campo vazio volta ao padrão em vez de desligar;
- a tela Perfis ganhou atalho para Configurações, que é onde o usuário foi procurar a chave.

Ainda pendente neste corte:

- Transformar “Minha BURO” numa área com Favoritos, Histórico completo e Continue assistindo.
- Acrescentar pesquisa real sobre o catálogo local e filtros consistentes com o Windows.
- Completar gerenciamento de perfis: editar nome/avatar, PIN, Kids e exclusão segura.
- Testar todas as rotas em telefone pequeno, tablet, Android TV, touch, teclado e D-pad.
- Validar Assinaturas com chave TMDB real em aparelho físico. O corte atual passou em teste e build,
  mas **não foi exercitado contra a API com uma chave verdadeira**.

## Corte 3 — downloads

O ADR-008 substituiu a condição de autorização do GDD 6: o proprietário liberou download de VOD sem
a fonte declarar elegibilidade offline, e a capability Android `offline.supported` passou a `true`.
Consulte [`ADR-008`](../adr/ADR-008-UNRESTRICTED-VOD-DOWNLOAD.md) — o risco legal foi assumido lá,
de forma explícita e rastreável.

Restrições que **permanecem** e não são estéticas:

- TV ao vivo é recusada: um stream ao vivo não termina e encheria o disco.
- Download continua oculto no Android TV; a decisão vale para telefone.
- Nenhuma URL assinada ou credencial vai para o disco, log ou nome de arquivo.
- Nada de remover proteção: conteúdo protegido é gravado como recebido.

Ainda pendente:

- Fila persistente, retomada, remoção, espaço disponível e testes de perda de rede.

## Corte 4 — pagamentos de loja e restauração

- APK direto: o portal web/Stripe pode continuar sendo a rota de compra vinculada ao código do
  aparelho.
- Google Play: integrar Play Billing, verificar compras no backend e restaurar direitos. Um build
  distribuído pela loja não deve depender apenas de Stripe dentro do app sem estar num programa de
  faturamento alternativo elegível.
- Implementar transferência/restauração auditada quando o aparelho ou a chave Keystore for perdido.
- Testar compra de baixo valor, webhook, reembolso, disputa e recuperação após disputa ganha.

## Corte 5 — música e áudio

Música, rádio, podcasts e audiobooks permanecem fora da interface durante a Fase 0 do GDD 8. O
destino só será exposto depois de domínio, capabilities, persistência, player e testes do vertical
de áudio estarem prontos. Mostrar um botão vazio agora violaria o próprio contrato do projeto.

## Critérios para chamar o Android de pronto

- Nenhum caminho abre catálogo ou player com licença bloqueada.
- Compra e restauração funcionam no canal real de distribuição.
- Perfis isolam favoritos, histórico, progresso, Kids e metadados.
- Nenhuma credencial, URL assinada, token ou chave TMDB aparece em log, estado serializado ou backup.
- Testes unitários, instrumentados, lint, APK/AAB e inspeção física passam nas quatro línguas.
- Downloads, Assinaturas e Música só aparecem quando as respectivas capabilities forem verdadeiras.
  Assinaturas depende de chave TMDB configurada; Downloads segue o ADR-008 e continua oculto na TV.
