# Tratamento de credenciais

## Regra principal

O IPTV BURO processa playlists e streams no dispositivo. URLs, usernames,
senhas, tokens, cookies e headers de autenticação não devem sair do aparelho
nem aparecer em logs, mensagens de erro, screenshots, fixtures, relatórios,
artefatos de build ou commits.

## Fronteiras de armazenamento

### Android — fontes Xtream

Na milestone `0.2`, servidor, usuário e senha de uma fonte Xtream são
serializados em um único payload e cifrados com:

- uma chave AES de 256 bits criada e protegida pelo Android Keystore;
- `AES/GCM/NoPadding`;
- IV aleatório por escrita;
- tag de autenticação de 128 bits;
- identificador da fonte como AAD, impedindo trocar envelopes entre fontes.

O `SharedPreferences` privado guarda somente a versão do formato, IV e
ciphertext. A chave não é exportada do Android Keystore. Backup e transferência
dos dados do aplicativo permanecem desabilitados no manifesto.

O Room guarda apenas o catálogo derivado e locators locais sem credencial. Para
Xtream, uma linha contém IDs estáveis, tipo de conteúdo, identificadores do
provedor, metadados e um valor no formato `xtream://`; servidor, usuário, senha
e URL final não fazem parte desse locator. A URL reproduzível é construída em
memória somente quando o usuário abre um item. Detalhes e episódios de séries
também são resolvidos sob demanda.

Excluir uma fonte deve remover tanto seu catálogo no Room quanto o envelope
cifrado correspondente. Se a persistência transacional do catálogo falhar
depois da criação do envelope, o importador deve remover esse envelope.

### Android — fontes M3U

Arquivos M3U podem trazer segredo na própria URL ou em headers. Essas URLs ainda
são persistidas no banco privado do aplicativo para permitir o playback e não
recebem a proteção específica aplicada às credenciais Xtream. Portanto:

- o sandbox do aplicativo deve ser tratado como dado sensível;
- backup continua desabilitado;
- URLs M3U nunca devem aparecer em logs ou relatórios;
- criptografia de campos sensíveis M3U permanece uma lacuna antes de uma versão
  estável.

### Desktop

O preview Compose Desktop mantém o catálogo `session-only`, mas pode lembrar uma
conexão Xtream entre execuções no Windows:

- servidor, usuário e senha são serializados somente dentro de um blob protegido
  pelo Windows DPAPI para o usuário atual;
- o blob fica em `%LOCALAPPDATA%/IPTVBURO/remembered-source.dpapi` e não contém
  texto simples;
- a ação explícita `Encerrar sessão` remove o blob lembrado;
- caminhos M3U, catálogo e URLs finais não são persistidos;
- mantém os campos Xtream em buffers apagáveis enquanto a sessão está ativa;
- limpa os buffers ao encerrar ou substituir a sessão;
- carrega filmes, séries, detalhes e episódios somente quando necessários;
- monta uma URI Xtream reproduzível apenas depois de confirmação explícita.

DPAPI vincula a descriptografia ao usuário do Windows, mas o arquivo ainda deve
ser tratado como dado sensível. O uso de arrays apagáveis reduz o tempo de
retenção, mas não garante ausência
absoluta de cópias transitórias: componentes de texto e APIs JVM trabalham com
`String` imutável. Depois que a URI é entregue ao handler externo do sistema
operacional, o IPTV BURO não controla o histórico, os logs ou o cache desse
aplicativo externo.

## Controles comuns

- importação por `ContentResolver`, sem upload;
- banco local privado ao aplicativo;
- logger central com redação antes de escrever no Logcat;
- parâmetros de query removidos de URLs nos diagnósticos;
- headers `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, `X-Auth-Token`
  e equivalentes substituídos por `[REDACTED]`;
- testes unitários de redação;
- modelos sensíveis sobrescrevem `toString()` com valores redigidos;
- analytics e crash reporting não são incluídos nesta fase;
- fixtures reais de usuários são proibidas no repositório.

O cliente Xtream aceita apenas HTTP e HTTPS, limita respostas, não segue
redirects e retorna erros sem o endpoint completo. Catálogo e credenciais não
são enviados a um backend do IPTV BURO.

## Ciclo de teste com fonte privada

Uma fonte privada autorizada pode ser usada somente por variáveis de ambiente ou
entrada interativa. O ciclo de validação deve:

1. impedir que valores reais sejam copiados para código, fixtures, documentação
   ou nomes de teste;
2. evitar screenshots enquanto campos sensíveis estiverem visíveis;
3. usar somente métricas agregadas e sanitizadas no relatório;
4. pesquisar o workspace e arquivos rastreados por ocorrências dos segredos;
5. limpar dados do aplicativo, Logcat, processos e arquivos temporários ao fim;
6. repetir um smoke test em estado limpo quando necessário.

Testes privados são opt-in e devem ser ignorados quando as variáveis necessárias
não estiverem presentes.

## HTTP sem TLS

O manifesto permite tráfego HTTP porque algumas fontes legais ainda dependem
dele. Em HTTP, servidor, usuário, senha, metadados e stream podem ser observados
ou alterados na rede. A interface deve alertar antes da conexão e recomendar
HTTPS. O aplicativo não contorna autenticação, DRM ou bloqueio geográfico.

## Relatos e suporte

Antes de compartilhar um relatório, o usuário deve verificar que não há
credenciais. O relatório técnico deve conter somente:

- tipo genérico de protocolo;
- código HTTP, sem URL completa;
- codec/container quando conhecidos;
- etapa da reprodução;
- modelo do dispositivo e versão do app.

O nome real da fonte, títulos do catálogo, identificadores de conta e contagens
que possam identificar um provedor privado não pertencem ao relatório público.
