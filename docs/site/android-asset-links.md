# `.well-known/assetlinks.json`

Faz o Android abrir `https://iptvburo.pages.dev/t/...` direto no aplicativo, sem
a caixa de "abrir com". O Android busca este arquivo e só aceita o vínculo se a
impressão digital aqui bater com o certificado que assinou o APK instalado.

## Qual impressão está publicada

Hoje: a do **keystore de debug**, porque é ele que assina o APK de prévia
distribuído nos releases do GitHub.

```text
FD:C8:AD:9D:B3:49:47:54:E9:1C:A3:7E:8D:A6:45:C6:0F:98:EA:60:2C:D6:63:64:E4:2C:0A:EA:00:B2:33:A0
```

Obtida com:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -storepass android -keypass android
```

## O que precisa mudar antes de publicar na Play Store

**Adicionar** — não substituir — a impressão do certificado de release. As duas
podem conviver no mesmo array, e é isso que se quer: o APK de prévia continua
funcionando enquanto o build assinado também passa a funcionar.

Se o aplicativo for distribuído pelo Google Play com **Play App Signing**, a
impressão que importa é a do certificado que o próprio Google usa para reassinar
— disponível no Play Console em *Configuração > Integridade do app*. Usar a
impressão do upload key nesse caso faz a verificação falhar silenciosamente.

## Como verificar depois de publicar

```bash
curl -s https://iptvburo.pages.dev/.well-known/assetlinks.json
```

Precisa responder `200` com `Content-Type: application/json`. A verificação do
Android também pode ser conferida no dispositivo:

```bash
adb shell pm get-app-links com.lucasserafin94.iptvburo
```

O domínio deve aparecer como `verified`.

## Se a verificação falhar

O recurso degrada, não quebra: o link abre no navegador, que é exatamente a
mesma página que alguém sem o aplicativo recebe. O botão "Abrir no IPTV BURO"
nessa página usa o esquema `iptvburo://`, que não depende desta verificação.
