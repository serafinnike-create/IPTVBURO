# IPTV BURO para Samsung Tizen

Aplicação Web Tizen própria para TV Samsung. O Android TV é a referência de
produto, mas a reprodução e o ciclo de vida usam Samsung AVPlay.

## Estado atual da preview

Implementado e coberto pela suíte automatizada; o pacote compila e assina para Tizen TV:

- aviso legal e perfis, inclusive perfil Kids;
- shell BURO Nocturne navegável por D-pad;
- fontes M3U por URL ou arquivo M3U/M3U8 autorizado em USB, além de Xtream-compatible;
- catálogo Ao Vivo, Filmes, Séries, temporadas/episódios, Descobrir e busca local por substring com debounce de 300 ms, ordem equivalente ao Android e cursor IndexedDB entre páginas;
- detalhes Xtream de filmes e séries e guia curto de programação ao vivo;
- favoritos por perfil, continuidade e histórico locais, ordenados como no Android, limitados a `200/20/60` registros e apresentados em páginas de 40; progresso de episódio abre a série correspondente sem perder a porcentagem assistida;
- perfil Kids, categorias ocultas e bloqueio parental por PIN com hash salgado;
- gerenciamento completo das categorias da fonte ativa, em páginas de 40 linhas navegáveis por D-pad, sem o antigo corte após 300 categorias e sem revelar rótulos adultos a perfis Kids;
- idiomas PT-BR, EN, DE, IT e ES;
- Configurações alinhadas à referência Android: versão real do pacote, aviso legal, idiomas com nomes completos e estado atual, preferências acessíveis e atalho para o perfil ativo;
- credenciais no Tizen KeyManager e catálogo no IndexedDB;
- resolução de URL de reprodução somente em memória;
- AVPlay com buffering, play, pause, stop, avanço de 30 s/retrocesso de 10 s como Android, retomada, timeline, seleção de faixas, velocidade condicionada à capability, modos de imagem, erros recuperáveis e seleção automática de variante compatível para filmes 4K/HDR/DV/HEVC quando a mesma fonte oferece alternativa; durante a reprodução, ENTER pausa/retoma, vermelho alterna Minha BURO, o topo mostra programa atual/próximo e verde abre a programação completa já carregada, inclusive entradas encerradas ou uma explicação quando o provedor não enviou EPG;
- tamanho, cor e fundo de legenda aplicados por uma camada segura de cues do AVPlay quando `setSilentSubtitle` existe; sem essa API, as opções visuais ficam ocultas;
- downloads em USB removível por capability: filmes e episódios individuais, séries/temporadas em lote e filmes M3U que apontam para arquivo direto; a biblioteca prioriza operações ativas/falhas como Android e Windows, pesquisa títulos localmente sem distinguir acentos, filtra Filmes/Séries e limita o DOM a 40 linhas por página; detalhes refletem cancelar/continuar/tentar novamente/assistir/remover, com recuperação da fila após reinício e reprodução offline pelo AVPlay sem nova consulta ao provedor;
- TMDb opcional por perfil/casa, guia local em quatro etapas para obter a chave gratuita, tela de pessoa, Assinaturas/onde assistir e compartilhamento por QR local, sempre condicionados às capabilities e configurações reais;
- foco semântico sincronizado ao D-pad, landmarks, labels, estados de switches/filtros, regiões de loading/erro, diálogos e progressos acessíveis; contraste AA automatizado e suporte preservado a alto contraste/movimento reduzido;
- lembretes por perfil, marcados no detalhe de filmes e séries pela identidade de conteúdo, com trilho próprio na Home logo após Continuar assistindo, destino na Ribbon ao lado de Histórico e um aviso na abertura do aplicativo. Um título ainda ausente do catálogo continua aparecendo; o horizonte de 30 dias, a ordem (lançados, depois por proximidade, depois alfabética) e o tratamento de datas seguem a mesma política do Android. A composição também acompanha a referência: o trilho da Home usa pôsteres com selo fixo e leva à página quando pressionado, enquanto a página é uma lista vertical com arte pequena, estado e Remover em cada linha; sem pôster guardável — o caso comum, já que a arte com credencial é descartada na entrada do banco — a inicial do título ocupa o lugar, em vez de um símbolo repetido em todas as linhas. A TV não possui o aviso diário do celular — `background-support` está desabilitado no manifesto, então nada roda com o app fechado —, e a página diz isso em vez de oferecer um horário que nunca dispararia;
- adapter Stalker/Ministra com testes de contrato, ainda oculto na interface até validar o transporte MAG.

Ainda não validado em TV física: codecs, áudio, legendas, HDR e desempenho com
catálogos grandes, Voice Guide e ordem de leitura real. Stalker/Ministra na interface, licenciamento de produção,
Cast e multiview continuam ocultos ou desabilitados até existir suporte real.
Manifestos HLS `.m3u8` não são apresentados como download de arquivo único.

A VM Tizen TV 10 atual recusa também um pacote mínimo corretamente assinado,
mesmo com DUID e permissão de instalação válidos. Assim, não trate build/assinatura
como prova de execução: valide o `.wgt` em uma TV Samsung física ou em uma nova VM.

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
entre no pacote `.wgt`.

## Build e instalação

Com o Tizen Studio instalado e um perfil de certificado Samsung ativo:

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
