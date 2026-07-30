# Fixtures de teste

Este módulo contém somente dados sintéticos e referências públicas destinadas a testes.

`apple-bipbop.m3u` referencia o exemplo BipBop publicado pela Apple na página
[HTTP Live Streaming Examples](https://developer.apple.com/streaming/examples/). O arquivo local
contém apenas metadados M3U criados para o IPTV BURO e a URL do exemplo oficial; nenhum vídeo é
copiado ou redistribuído pelo repositório.

`synthetic-two-channels.m3u` usa o domínio reservado `example.invalid` e nunca deve realizar acesso
de rede.

Os testes automatizados apenas analisam os arquivos. Eles não iniciam reprodução nem dependem de
conectividade externa.
