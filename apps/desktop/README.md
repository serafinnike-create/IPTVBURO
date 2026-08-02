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
- catálogo e credenciais mantidos somente durante a sessão;
- confirmação antes de entregar uma mídia ao aplicativo externo.

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
descarta fontes, catálogos e estado da sessão.

## Segurança da sessão

O módulo não persiste caminhos de arquivo, URLs, headers HTTP, servidores,
usuários ou senhas. Campos Xtream usam buffers apagáveis controlados pelo
aplicativo e não usam `rememberSaveable`. O repositório limpa esses buffers ao
encerrar ou substituir a sessão.

Essa limpeza é uma defesa de redução de exposição, não uma garantia de memória
forense: Compose, a JVM e bibliotecas de rede podem criar strings transitórias.
Endereços sem esquema usam HTTPS por padrão. Quando o usuário informa `http://`,
a interface alerta que servidor, usuário e senha trafegam sem proteção TLS.

## Limite honesto de reprodução

Esta etapa não possui player desktop interno multi-codec. A ação de reprodução:

1. pede confirmação;
2. monta a URI final somente quando necessário;
3. entrega a URI ao handler externo registrado no sistema;
4. não guarda a URI no repositório desktop.

A integração usa `java.awt.Desktop`, sem montar uma linha de comando. Ainda
assim, depois da entrega, o sistema operacional ou o aplicativo externo pode
reter a URI em histórico, cache ou logs. Canais M3U que exigem headers especiais
também podem não funcionar no handler padrão.

Essas limitações devem permanecer visíveis na interface e nas notas de release
até existir um player interno validado.
