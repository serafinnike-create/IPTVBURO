# ADR-007 — Player VLC e atualização segura no Windows

**Status:** aceita para `v0.2.0-alpha.2`

## Contexto

O player JavaFX do preview anterior não decodifica de forma confiável a matriz
real de IPTV, em especial filmes H.265/HEVC. Também faltava um caminho simples
para o usuário instalar uma nova versão Windows publicada no GitHub.

## Decisão

1. O MSI inclui o binário oficial do VLC 3.0.23 para Windows x64. O Gradle baixa
   o ZIP do servidor VideoLAN e exige o SHA-256 fixado antes de extrair.
2. O IPTV BURO inicia o VLC como processo separado e incorpora a saída de vídeo
   em um `Canvas` AWT usando o handle nativo da janela.
3. O controle usa a interface HTTP do VLC ligada somente a `127.0.0.1`, em uma
   porta livre e com senha aleatória por sessão.
4. A URL privada da mídia é enviada ao loopback somente depois da inicialização;
   não entra na linha de comando, em logs ou no armazenamento persistente.
5. A interface expõe apenas ações verificadas: play/pause, seek, volume,
   velocidade, tela cheia, retry e continuidade por perfil.
6. O atualizador consulta `serafinnike-create/IPTVBURO` pelo GitHub Releases API
   sem reutilizar cache quando o usuário aciona o botão,
   escolhe somente uma versão semanticamente mais nova, exige um MSI com origem
   HTTPS do GitHub e digest `sha256:`, verifica o download e então chama
   `msiexec`.

## Consequências

- H.264 e H.265/HEVC da fonte de compatibilidade passam pelo mesmo player.
- O instalador cresce porque carrega o runtime do VLC, mas não depende de uma
  instalação prévia no computador.
- O botão de atualização não instala assets sem digest nem versões antigas.
- O asset precisa pertencer ao mesmo repositório consultado; um MSI hospedado em
  outro repositório GitHub é recusado.
- O VLC continua sendo um componente externo oficial, com seus próprios avisos
  e licença; o aplicativo não incorpora bindings GPL ao código Kotlin.
- Brilho global, HDR forçado e download offline continuam ocultos até existirem
  contratos de capacidade confiáveis por hardware e por autorização da fonte.

## Fontes técnicas

- VideoLAN, downloads oficiais do VLC: <https://download.videolan.org/pub/videolan/vlc/last/win64/>
- GitHub Releases REST API: <https://docs.github.com/en/rest/releases>
- GitHub release asset digests: <https://github.blog/changelog/2025-06-03-releases-now-expose-digests-for-release-assets/>
- Compose native distribution resources: <https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html>
