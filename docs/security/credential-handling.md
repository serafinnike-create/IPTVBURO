# Tratamento de credenciais

## Regra principal

O IPTV BURO processa playlists e streams no dispositivo. URLs, usernames,
senhas, tokens, cookies e headers de autenticação não devem sair do aparelho
nem aparecer em logs.

## Controles da Sprint 1

- importação por `ContentResolver`, sem upload;
- banco local privado ao aplicativo;
- logger central com redação antes de escrever no Logcat;
- parâmetros de query removidos de URLs nos diagnósticos;
- headers `Authorization`, `Cookie`, `Set-Cookie`, `X-Api-Key`, `X-Auth-Token`
  e equivalentes substituídos por `[REDACTED]`;
- testes unitários de redação;
- analytics e crash reporting não são incluídos nesta Sprint;
- fixtures reais de usuários são proibidas no repositório.

## Dados em repouso

A Sprint 1 não solicita credenciais Xtream. Quando esse recurso for criado,
segredos devem ser cifrados com chaves protegidas pelo Android Keystore. Room
continua responsável apenas por dados de catálogo que não sejam secretos.

## HTTP sem TLS

O manifesto permite tráfego HTTP porque algumas fontes legais ainda dependem
dele. A interface deve tratar HTTP como transporte menos seguro e a
documentação recomenda HTTPS. O aplicativo não contorna autenticação, DRM ou
bloqueio geográfico.

## Relatos e suporte

Antes de compartilhar um relatório, o usuário deve verificar que não há
credenciais. O relatório técnico deve conter somente:

- tipo genérico de protocolo;
- código HTTP, sem URL completa;
- codec/container quando conhecidos;
- etapa da reprodução;
- modelo do dispositivo e versão do app.
