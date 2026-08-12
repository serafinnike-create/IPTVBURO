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

### Android App Links (títulos compartilhados)

`site/.well-known/assetlinks.json` é publicado com o placeholder
`REPLACE_WITH_RELEASE_SHA256_FINGERPRINT` e **precisa** receber a impressão digital real antes de o
site ir ao ar. Enquanto o valor for o placeholder, o link compartilhado abre no navegador — a página
`/t/` funciona, mas o app instalado não é aberto direto no título.

Use a impressão digital do certificado que efetivamente assina o APK entregue ao usuário:

- com Play App Signing, copie o SHA-256 em **Play Console → Configuração → Integridade do app →
  Chave de assinatura do app** (não a chave de upload — é o erro mais comum aqui);
- com assinatura própria:

```powershell
keytool -list -v -keystore $env:IPTVBURO_ANDROID_KEYSTORE -alias $env:IPTVBURO_ANDROID_KEY_ALIAS
```

Depois de publicar, confirme que a verificação passou:

```powershell
adb shell pm get-app-links com.lucasserafin94.iptvburo
```

O domínio `iptvburo.pages.dev` deve aparecer como `verified`. O arquivo precisa ser servido por
HTTPS, com `Content-Type: application/json`, sem redirecionamento.

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
arquivo e o certificado no bloco `finally`. Se os três segredos estiverem ausentes,
o workflow de *preview* publica um MSI com o sufixo `-unsigned` e um aviso explícito na
release. Se apenas parte dos segredos estiver configurada, o job falha. Releases finais
continuam bloqueadas até existir assinatura Authenticode válida.

Antes do upload, `scripts/verify-clean-desktop-package.ps1` também inventaria a
imagem do aplicativo, recusa arquivos com formato de playlist, banco ou keystore,
confirma que não existe chave TMDb embutida e procura valores exatos da estação
de build sem imprimi-los no log.
