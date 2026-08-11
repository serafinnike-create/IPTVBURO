# Assinatura de produção

Build debug serve para desenvolvimento. APK/AAB, launcher Windows e MSI públicos precisam de
identidade de produção protegida e verificável. O repositório bloqueia os atalhos que antes
produziam um artefato “oficial” sem assinatura.

## Android / Google Play

Crie a chave no cofre da organização ou use Play App Signing. Não envie keystore ou senhas por
mensagem e não os coloque em `local.properties`.

Na sessão protegida de build, defina:

```powershell
$env:IPTVBURO_ANDROID_KEYSTORE = 'C:\caminho-protegido\iptvburo-release.jks'
$env:IPTVBURO_ANDROID_KEY_ALIAS = 'iptvburo-release'
$env:IPTVBURO_ANDROID_STORE_PASSWORD = '<do-cofre>'
$env:IPTVBURO_ANDROID_KEY_PASSWORD = '<do-cofre>'
./gradlew.bat :apps:android-tv:bundleRelease --no-daemon
```

Sem os quatro valores, `assembleRelease` e `bundleRelease` falham. Antes do upload:

```powershell
jarsigner -verify -verbose -certs apps/android-tv/build/outputs/bundle/release/android-tv-release.aab
```

Registre SHA-256, versionCode, certificado e faixa do Play Console no relatório da release. Guarde
backup cifrado e offline da chave quando ela não for administrada pelo Play App Signing.

## Windows

O certificado precisa estar em `Cert:\CurrentUser\My`, com chave privada e EKU Code Signing.
Defina apenas na sessão protegida:

```powershell
$env:IPTVBURO_WINDOWS_CERT_THUMBPRINT = '<thumbprint-de-40-hex>'
$env:IPTVBURO_TIMESTAMP_URL = 'https://<servico-rfc3161-do-emissor>'
./scripts/sign-windows-release.ps1
```

O script localiza o Windows SDK `signtool`, manda o Gradle assinar o launcher imediatamente antes
de o WiX montar o pacote, assina o MSI final, valida ambos com a política Authenticode e imprime o
SHA-256. `packageMsi` e `packageReleaseMsi` diretos falham para impedir um instalador sem assinatura.

Antes de publicar, instale o MSI numa conta Windows limpa, confira o editor exibido pelo Windows e
execute:

```powershell
Get-AuthenticodeSignature apps/desktop/build/compose/binaries/main/app/IPTVBURO/IPTVBURO.exe
Get-AuthenticodeSignature apps/desktop/build/compose/binaries/main/msi/IPTVBURO-2.0.0.msi
```

Os dois estados devem ser `Valid`. Certificado ausente, expirado, sem timestamp ou editor inesperado
bloqueia a release.

### GitHub Actions

O workflow `preview-release.yml` exige estes GitHub Actions Secrets:

- `WINDOWS_SIGNING_CERTIFICATE_BASE64`: PFX de code signing convertido para Base64;
- `WINDOWS_SIGNING_CERTIFICATE_PASSWORD`: senha do PFX;
- `WINDOWS_TIMESTAMP_URL`: endpoint RFC 3161 HTTPS indicado pelo emissor.

O runner grava o PFX somente na pasta temporária, importa-o em
`Cert:\CurrentUser\My`, executa o pipeline protegido, valida o MSI e remove o
arquivo e o certificado no bloco `finally`. Se qualquer segredo estiver ausente,
o job falha antes da build e nenhuma release é criada.

Antes do upload, `scripts/verify-clean-desktop-package.ps1` também inventaria a
imagem do aplicativo, recusa arquivos com formato de playlist, banco ou keystore,
confirma que não existe chave TMDb embutida e procura valores exatos da estação
de build sem imprimi-los no log.
