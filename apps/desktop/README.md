# IPTV BURO Desktop

Preview executável da milestone `0.2` para Windows, macOS e Linux, construído
com Compose Desktop. A escolha técnica e suas fronteiras estão registradas no
[`ADR-003`](../../docs/adr/ADR-003-xtream-and-desktop-milestone-0.2.md).

## Escopo atual

- importação de arquivo local M3U/M3U8;
- sessão Xtream com TV ao vivo, filmes, séries e episódios;
- categorias, busca e paginação;
- carregamento inicial de TV ao vivo;
- filmes, séries, detalhes e episódios carregados sob demanda;
- Home editorial diária, perfis, favoritos e continuidade por perfil;
- credenciais lembradas opcionalmente e cifradas pelo DPAPI do usuário Windows;
- player VLC incluído para H.264, H.265/HEVC, AAC, MP4, MKV e HLS;
- play/pause, seek, volume, velocidade e tela cheia;
- verificação de atualização pelo GitHub Releases com validação SHA-256.

O desktop não inclui conteúdo, servidor ou credenciais de demonstração.

## Executar

Pelo build raiz do monorepo:

```powershell
.\gradlew.bat :apps:desktop:run
```

Ou pelo build isolado do módulo:

```powershell
.\gradlew.bat -p apps/desktop run
```

Testes e compilação:

```powershell
.\gradlew.bat :apps:desktop:test :apps:desktop:compileKotlin
```

Gerar a distribuição nativa do sistema atual:

```powershell
.\gradlew.bat :apps:desktop:packageDistributionForCurrentOS
```

Os formatos configurados são MSI no Windows, DMG no macOS e DEB no Linux. A
geração de cada formato deve acontecer no sistema operacional correspondente.

## Dados e paginação

O catálogo M3U é lido do arquivo selecionado e mantido em memória. No Xtream, a
autenticação carrega categorias e o catálogo ao vivo inicial; filmes e séries
são carregados quando suas abas são abertas. Detalhes e episódios também são
consultados sob demanda.

A interface apresenta páginas pequenas, com filtro por categoria e busca, sem
criar uma segunda cópia completa do catálogo filtrado. Fechar a aplicação
descarta o catálogo em memória. Quando o usuário escolhe lembrar a fonte, apenas
o envelope cifrado pelo DPAPI permanece no perfil local.

## Segurança da sessão

O módulo não grava URLs de reprodução, headers HTTP ou credenciais em texto
claro. Campos Xtream usam buffers apagáveis controlados pelo aplicativo e não
usam `rememberSaveable`. A opção de lembrar a fonte usa DPAPI e pode ser apagada
por `Encerrar sessão`.

Essa limpeza é uma defesa de redução de exposição, não uma garantia de memória
forense: Compose, a JVM e bibliotecas de rede podem criar strings transitórias.
Endereços sem esquema usam HTTPS por padrão. Quando o usuário informa `http://`,
a interface alerta que servidor, usuário e senha trafegam sem proteção TLS.

## Reprodução e atualização no Windows

O instalador Windows inclui o executável oficial do VLC 3.0.23 e o controla por
uma interface HTTP vinculada apenas a `127.0.0.1`, com porta e senha aleatórias.
A URL privada é enviada somente depois que o player inicia; não aparece na linha
de comando, nos logs ou na persistência do aplicativo. O vídeo é incorporado à
janela do IPTV BURO e recebe controles reais de volume, seek, velocidade e tela
cheia.

`Verificar atualização` faz uma consulta sem cache a cada clique aos releases de
`serafinnike-create/IPTVBURO`, aceita apenas uma versão semanticamente mais nova
e um MSI publicado nesse mesmo repositório, exige o digest `sha256:` do asset,
verifica o arquivo local e só então abre o instalador.

## Build pública limpa

Por padrão, nenhuma chave de `local.properties` é incluída no binário. A build
pública não contém playlist, fonte, credencial, perfil, histórico, download ou
registo do utilizador da máquina que compilou o aplicativo. Para uma execução
local de desenvolvimento, a chave TMDb pode ser habilitada explicitamente com
`-Piptvburo.includeLocalTmdbKey=true`; qualquer tarefa de distribuição recusa
essa opção.

```powershell
.\gradlew.bat :apps:desktop:clean :apps:desktop:test :apps:desktop:createDistributable
```

O MSI público exige Authenticode. Depois de configurar o certificado fora do
repositório, execute `scripts/sign-windows-release.ps1`; `packageMsi` direto é
bloqueado para impedir a publicação acidental de um instalador sem assinatura.

Download offline continua oculto até a fonte ou o backend declarar autorização
para o item. Brilho global do monitor e HDR forçado também não são simulados: são
capacidades de hardware/sistema e só serão expostos quando houver suporte
confiável por dispositivo.
