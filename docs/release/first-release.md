# Primeira versão para download

A primeira entrega foi publicada como **prévia Android/Android TV**.

## Artefato inicial

- versão/tag publicada: `v0.1.0-alpha.1`;
- commit da tag: `7e0b9ec`;
- implementação de referência: `main@2c9bd5b`;
- nome: `IPTV-BURO-v0.1.0-alpha.1-android-debug.apk`;
- tamanho: 24.864.542 bytes;
- SHA-256:
  `179537447d53ef062daf9cd100b5ed52416be796ceedb61cb64601a930965dc6`;
- instalação direta em Android 6.0 ou superior;
- otimizada para Android TV e navegação D-pad;
- instalável no celular para validação, sem prometer layout mobile final;
- nenhuma playlist ou conteúdo incluído.

Links publicados:

- [GitHub Pre-release v0.1.0-alpha.1](https://github.com/lucasserafin94/IPTVBURO/releases/tag/v0.1.0-alpha.1);
- [download do APK](https://github.com/lucasserafin94/IPTVBURO/releases/download/v0.1.0-alpha.1/IPTV-BURO-v0.1.0-alpha.1-android-debug.apk).

O workflow
[`Publish Android preview` — run 30590918504](https://github.com/lucasserafin94/IPTVBURO/actions/runs/30590918504)
reconstruiu o APK, concluiu com sucesso e criou a Pre-release.

## Validação anterior à tag

- `test`, `lint` e `assembleDebug` verdes;
- 55 testes JVM aprovados;
- lint com 0 erros e 18 warnings não bloqueantes;
- APK local com 25.433.893 bytes;
- SHA-256 do APK local:
  `5af0c37258951343e55cb6b0c7a8c3d50d7e088e29d6a8d29db1095d9203ecb4`;
- instalação, abertura, navegação por D-pad e fluxo HLS validados no aparelho
  físico.

O APK local acima foi usado na validação anterior à tag. O arquivo oficial para
download é o artefato separado gerado pelo CI, com 24.864.542 bytes e SHA-256
`179537447d53ef062daf9cd100b5ed52416be796ceedb61cb64601a930965dc6`.

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
- [x] README e changelog atualizados;
- [x] tag `v0.1.0-alpha.1` criada e enviada;
- [x] workflow concluído com sucesso;
- [x] GitHub Release publicada como pre-release;
- [x] tamanho e SHA-256 do APK publicado pelo CI registrados.
