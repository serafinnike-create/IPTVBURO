# IPTV BURO para Samsung Tizen

Aplicação Web Tizen própria para TV Samsung. O Android TV é a referência de
produto, mas a reprodução e o ciclo de vida usam Samsung AVPlay.

## Estado da preview 0.3.0

Implementado na preview; o pacote foi instalado e aberto no emulador Tizen TV 10.0:

- aviso legal e perfis, inclusive perfil Kids;
- shell BURO Nocturne navegável por D-pad;
- fontes M3U remotas e Xtream-compatible;
- catálogo Ao Vivo, Filmes, Séries, temporadas/episódios e busca local;
- detalhes Xtream de filmes e séries e guia curto de programação ao vivo;
- favoritos por perfil, continuidade e histórico locais;
- perfil Kids, categorias ocultas e bloqueio parental por PIN com hash salgado;
- idiomas PT-BR, EN, DE, IT e ES;
- credenciais no Tizen KeyManager e catálogo no IndexedDB;
- resolução de URL de reprodução somente em memória;
- AVPlay com buffering, play, pause, stop, avanço/retrocesso, seleção de faixas e limpeza de sessão;
- adapter Stalker/Ministra com testes de contrato, ainda oculto na interface até validar o transporte MAG.

Ainda não validado em TV física: codecs, áudio, legendas, HDR e desempenho com
catálogos grandes. Estilo visual de legendas, Stalker/Ministra na interface,
licenciamento, assinaturas, downloads e multiview ainda não estão habilitados.

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
