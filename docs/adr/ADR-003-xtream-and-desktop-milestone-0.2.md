# ADR-003 — Xtream estruturado e preview desktop na milestone 0.2

- Status: aceito
- Data: 31 de julho de 2026
- Escopo: arquitetura da milestone `0.2`; validação E2E e publicação são
  registradas separadamente

## Contexto

Uma fonte compatível com Xtream pode oferecer TV ao vivo, filmes, séries,
categorias e episódios por endpoints estruturados. Tratar a mesma fonte apenas
como uma M3U plana:

- perde a separação semântica entre os tipos de conteúdo;
- exige processar catálogos muito maiores;
- dificulta carregar detalhes de séries sob demanda;
- incentiva a persistência de URLs que contêm credenciais;
- não oferece uma base adequada para paginação.

A milestone também precisa disponibilizar uma aplicação executável no notebook.
O monorepo já usa Kotlin/JVM no domínio, no parser M3U e no novo cliente Xtream.
Ainda não existe um player desktop interno com matriz de codecs validada.

## Decisão

### Cliente Xtream compartilhado

Criar `packages/xtream-client` como módulo Kotlin/JVM sem dependência de UI. O
cliente:

- normaliza endereços `get.php`, `player_api.php` e variações equivalentes;
- aceita somente HTTP e HTTPS;
- autentica e consulta categorias e catálogos separados de TV ao vivo, filmes e
  séries;
- consulta detalhes e episódios de séries sob demanda;
- tolera campos opcionais e valores numéricos representados como texto;
- limita o tamanho das respostas, exige JSON UTF-8 e não segue redirects;
- produz erros sanitizados e modelos cujo `toString()` não revela credenciais
  ou URLs sensíveis.

O aplicativo não usa backend intermediário para consultar a fonte.

### Android

No Android, a importação Xtream persiste no Room apenas metadados, IDs do
provedor, tipo de conteúdo, extensão de container e um locator local
`xtream://`. O locator não contém servidor, usuário, senha nem URL final de
reprodução.

Servidor, usuário e senha ficam em armazenamento separado:

```text
Android Keystore — chave AES de 256 bits
        ↓
AES/GCM/NoPadding + IV aleatório + sourceId como AAD
        ↓
SharedPreferences privado — somente envelope cifrado
```

A URL final de TV ao vivo ou filme é resolvida em memória imediatamente antes
do playback. Detalhes de séries e URLs de episódios são obtidos e montados sob
demanda. O catálogo é consultado no Room por tipo, categoria e páginas
limitadas, evitando entregar a coleção inteira para a UI.

A interface Android usa o espaço disponível da janela para distinguir retrato
compacto, paisagem compacta e layout expandido. Não há bloqueio de orientação;
telefone, rotação, multiwindow e Android TV recompõem a mesma árvore de telas.
A navegação por toque e o contrato de foco por D-pad permanecem requisitos
simultâneos.

### Desktop

Adotar Compose Desktop para o preview de notebook porque ele:

- reutiliza diretamente os módulos Kotlin/JVM existentes;
- mantém UI, domínio, M3U e Xtream no mesmo toolchain;
- gera distribuições nativas por sistema operacional;
- permite evoluir o player de forma incremental sem introduzir outro runtime
  nesta milestone.

O desktop mantém fontes M3U e Xtream somente na sessão. Credenciais Xtream são
mantidas em buffers apagáveis enquanto a sessão está ativa; catálogos de filmes,
séries e episódios são carregados sob demanda e apresentados em páginas.

O preview não declara possuir player interno. Ao reproduzir, mostra uma
confirmação e entrega a URI ao handler externo registrado no sistema. Para uma
fonte Xtream, a URI com credenciais é montada apenas depois da confirmação e
não é guardada pelo repositório desktop.

## Consequências

### Positivas

- catálogo semântico menor e mais navegável que a M3U plana equivalente;
- credenciais Xtream não são duplicadas em cada linha do Room;
- resolução tardia reduz a superfície de exposição das URLs de playback;
- Android e desktop compartilham parsing e regras de compatibilidade;
- paginação limita o volume apresentado de uma vez à UI;
- o notebook recebe uma aplicação real sem bloquear a evolução do player
  Android já existente;
- retrato e paisagem passam a ser estados suportados, não aplicativos separados.

### Limitações e riscos

- HTTP continua permitido por compatibilidade e transmite credenciais sem a
  proteção de TLS; a UI deve alertar o usuário;
- depois que o desktop entrega uma URI ao aplicativo externo, o sistema
  operacional ou esse aplicativo pode registrá-la em histórico;
- buffers apagáveis reduzem retenção, mas bibliotecas JVM e componentes de texto
  podem criar cópias imutáveis transitórias;
- URLs de streams vindas de M3U continuam sendo dados sensíveis no Room; esta
  decisão protege especificamente credenciais e URLs derivadas de Xtream;
- diferenças entre painéis compatíveis com Xtream exigem fixtures sintéticas e
  tratamento contínuo de schema drift;
- o player desktop interno, EPG/XMLTV e refresh incremental permanecem fora
  desta decisão.

## Alternativas rejeitadas

- **Converter sempre Xtream em M3U:** aumenta o catálogo, perde metadados
  estruturados e persiste URLs com segredos.
- **Guardar a URL final no Room:** replica credenciais por item e amplia o
  impacto de uma leitura indevida do banco.
- **Persistir credenciais desktop:** exigiria integração segura específica para
  cada sistema operacional antes de haver necessidade de sessões duráveis.
- **Flutter ou Tauri nesta milestone:** reduzem o reaproveitamento imediato dos
  módulos Kotlin/JVM já existentes.
- **Declarar o handler externo como player interno:** esconderia uma limitação
  relevante de compatibilidade e privacidade.
