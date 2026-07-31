# IPTV BURO — Apple

Este diretório reunirá targets para:

- Apple TV;
- iPhone;
- iPad;
- macOS.

Estratégia:

- SwiftUI compartilhado quando apropriado;
- AVPlayer/AVFoundation;
- adaptações específicas para toque, foco, teclado e mouse;
- BURO Offline Vault somente em iPhone/iPad durante o P0;
- armazenamento e credenciais protegidos pelas APIs do ecossistema;
- builds e testes em runners macOS;
- validação em aparelhos físicos antes de release.

Apple TV e macOS não recebem download offline no primeiro escopo do GDD 6.0.
