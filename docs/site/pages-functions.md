# Cloudflare Pages Functions

Este diretório é interpretado pelo Cloudflare Pages, não é conteúdo estático.
Ele só funciona se `site/` continuar sendo a **raiz de publicação** do projeto
Pages — que é como o site é publicado hoje. Se a raiz mudar, `functions/` precisa
acompanhar, senão as funções deixam de existir silenciosamente e o site volta a
servir apenas o HTML estático.

## `t/index.js` — prévia do título compartilhado

WhatsApp, Telegram, X e afins buscam a URL compartilhada e leem as tags Open
Graph **sem executar JavaScript**. A página `/t/index.html` se preenche no
cliente a partir da query string: isso é correto para quem visita, e invisível
para o robô do WhatsApp. Sem esta função, um filme compartilhado chega no chat
como um link cru, sem capa e sem título.

A função serve o mesmo arquivo estático e injeta as tags no `<head>` durante a
resposta, via `HTMLRewriter`. O script do cliente continua fazendo o trabalho
dele para o visitante humano.

### Como verificar depois de publicar

```bash
curl -s "https://iptvburo.pages.dev/t/?id=movie:duna-parte-dois:2024&t=Duna:%20Parte%20Dois&y=2024" \
  | grep -o '<meta property="og:[^>]*>'
```

Devem aparecer `og:title`, `og:description` e `og:url`. Com um `img=` de
`image.tmdb.org` na query, deve aparecer também `og:image`.

Para conferir como o WhatsApp de fato renderiza, use o validador de link do
próprio Facebook ou envie o link para si mesmo — o cache de prévia é agressivo,
então mudanças podem levar a aparecer.

### Regra de segurança que esta função mantém

A capa só é aceita de `image.tmdb.org` / `themoviedb.org`. O host do provedor do
usuário **não** está na lista, então um link que carregue uma capa hospedada no
servidor Xtream é renderizado sem imagem, em vez de publicar o endereço daquele
servidor para todo mundo no grupo. Essa lista aparece em três lugares e precisa
ser mantida em sincronia:

- `packages/domain-model/.../TitleShareLink.kt` (`PUBLIC_ARTWORK_HOSTS`)
- `site/t/shared-title.js` (`PUBLIC_ARTWORK_HOSTS`)
- `site/functions/t/index.js` (`PUBLIC_ARTWORK_HOSTS`)
