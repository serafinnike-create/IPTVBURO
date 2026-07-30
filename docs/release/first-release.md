# Primeira versão para download

A primeira entrega está preparada como **prévia Android/Android TV**.

## Artefato inicial

- versão/tag preparada: `v0.1.0-alpha.1`;
- implementação de referência: `main@2c9bd5b`;
- nome previsto: `IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`;
- instalação direta em Android 6.0 ou superior;
- otimizada para Android TV e navegação D-pad;
- instalável no celular para validação, sem prometer layout mobile final;
- nenhuma playlist ou conteúdo incluído.

Páginas previstas após a conclusão do workflow:

- [GitHub Pre-release v0.1.0-alpha.1](https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.1.0-alpha.1);
- [download do APK](https://github.com/lucasserafin94/IPTVBURO/releases/download/v0.1.0-alpha.1/IPTV-BURO-v0.1.0-alpha.1-android-debug.apk).

Os links ainda não representam uma Release confirmada. O push da tag dispara o
workflow, que reconstrói o APK e cria a Pre-release.

## Validação anterior à tag

- `test`, `lint` e `assembleDebug` verdes;
- 55 testes JVM aprovados;
- lint com 0 erros e 18 warnings não bloqueantes;
- APK local com 25.433.893 bytes;
- SHA-256 do APK local:
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`;
- instalação, abertura, navegação por D-pad e fluxo HLS validados no aparelho
  físico.

O APK publicado será um novo artefato gerado pelo CI. Seu tamanho e SHA-256
deverão ser registrados somente depois da conclusão do workflow.

O APK debug é assinado automaticamente e serve somente para testes. Como a
chave debug do CI pode mudar, uma atualização futura pode exigir desinstalar a
prévia anterior. A primeira versão estável deverá usar uma chave de assinatura
de produção armazenada fora do repositório e copiada para GitHub Secrets.

## Checklist de publicação

- [x] `test`, `lint` e `assembleDebug` verdes;
- [x] instalação e abertura no aparelho físico;
- [x] fluxo por D-pad verificado;
- [x] nenhuma credencial conhecida versionada;
- [x] implementação enviada à `main`;
- [x] README e changelog preparados;
- [ ] criar e enviar a tag `v0.1.0-alpha.1`;
- [ ] confirmar a execução verde do workflow;
- [ ] confirmar a GitHub Release marcada como pre-release;
- [ ] registrar tamanho e SHA-256 do APK publicado pelo CI.
