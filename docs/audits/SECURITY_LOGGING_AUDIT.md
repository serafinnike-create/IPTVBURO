# Auditoria de segurança e logging

- Data: 2 de agosto de 2026

## Controles existentes

- nenhum endpoint, usuário ou senha de fonte privada é versionado;
- credenciais Xtream Android cifradas por chave do Android Keystore;
- lembrança no Windows protegida pelo DPAPI do usuário atual;
- URLs de reprodução são resolvidas tardiamente e não aparecem em `toString`;
- redaction cobre query, token, autorização, cookie e dados de rede;
- backup e transferência de dados Android estão desabilitados;
- identidade de dispositivo usa chave de instalação, não MAC;
- chaves comerciais deverão permanecer apenas no backend.

## Riscos abertos

- M3U pode conter URL e headers sensíveis persistidos no sandbox Room;
- falta varredura automatizada de segredo como gate obrigatório de release;
- falta exportação de diagnóstico seguro e política completa de retenção;
- portal/backend ainda não existem para validar recibos e assinar entitlements;
- Cofre Offline mobile precisa armazenamento privado, licença e remoção segura.

## Gate

Antes de publicar, executar busca por padrões de segredo, inspecionar APK/MSI,
validar logs de erro e confirmar que nenhum fixture privado entrou no histórico
ou artefato. Qualquer ocorrência bloqueia a release.
