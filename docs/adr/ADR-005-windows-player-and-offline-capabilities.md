# ADR-005 — Player Windows, HDR e capacidades offline

- Estado: aceito; integração nativa ampla ainda pendente
- Data: 2 de agosto de 2026

## Contexto

O preview Windows precisava reproduzir dentro do produto. JavaFX Media fornece
um primeiro player embutido, mas não cobre a matriz profissional completa de
HEVC, Dolby Vision, HDR, áudio e legendas. O Cofre Offline do GDD 6 também proíbe
um downloader genérico para a pasta pública do usuário.

## Decisão

1. O preview Windows usa o player JavaFX embutido apenas para formatos que ele
   realmente suporta, principalmente MP4/H.264/AAC e HLS compatível.
2. A versão estável do Windows terá `PlayerAdapter` nativo respaldado por um
   spike de libmpv/Media Foundation e uma matriz verificável de codecs, GPU,
   legendas, faixas de áudio, seek e HDR.
3. Não haverá chave universal "HDR ligado/desligado". O controle só aparece
   quando o manifesto oferece uma variante SDR real ou o adaptador comprova
   tone mapping em reprodução. Caso contrário, o app informa a capacidade.
4. Download Windows permanece indisponível até um ADR específico definir
   autorização, índice persistente, retomada, armazenamento privado, criptografia,
   expiração, perfil/Kids e remoção segura. O downloader genérico foi removido.
5. Android TV também não apresenta download. O P0 do Cofre Offline fica restrito
   a Android mobile/tablet e, futuramente, iPhone/iPad, conforme o GDD 6.

## Critério para promover o Windows

- reprodução embutida em uma matriz pública de amostras e hardware;
- troca real de áudio e legenda;
- seek e retomada confiáveis;
- diagnóstico sem URL/credencial;
- acessibilidade por teclado;
- instalador assinado e smoke test limpo;
- capacidades declaradas no manifest sem apresentar recurso ausente.

## Consequências

O preview é mais honesto e seguro, mas ainda não possui paridade completa com o
Media3 Android. A publicação Windows final fica bloqueada até o adapter nativo
e os gates acima passarem.
