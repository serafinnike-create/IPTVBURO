# Primeira versão para download

A primeira entrega será uma **prévia Android/Android TV**.

## Artefato inicial

- nome planejado: `IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`;
- instalação direta em Android 6.0 ou superior;
- otimizada para Android TV e navegação D-pad;
- instalável no celular para validação, sem prometer layout mobile final;
- nenhuma playlist ou conteúdo incluído.

O APK debug é assinado automaticamente e serve somente para testes. Como a
chave debug do CI pode mudar, uma atualização futura pode exigir desinstalar a
prévia anterior. A primeira versão estável deverá usar uma chave de assinatura
de produção armazenada fora do repositório e copiada para GitHub Secrets.

## Checklist antes de publicar

- `test`, `lint` e `assembleDebug` verdes;
- instalação e abertura no aparelho físico;
- fluxo por D-pad verificado;
- nenhuma credencial no Git;
- README e changelog atualizados;
- autenticação do GitHub CLI restaurada;
- tag e release marcadas como pre-release.
