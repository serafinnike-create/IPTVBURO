/**
 * Renders the shared-title page with per-title preview tags.
 *
 * WhatsApp, Telegram and the rest fetch a shared URL and read its Open Graph tags **without running
 * any JavaScript**. The static page at `/t/index.html` fills itself in from the query string on the
 * client, which is right for a human visitor and invisible to a crawler — so a shared film arrived
 * in the chat as a bare link with no poster and no title, which is most of what makes someone tap
 * it.
 *
 * This function serves the same static asset and injects the tags into the `<head>` on the way
 * past. The client script still does its job for the visitor; this only adds what the crawler
 * needs. Everything is derived from the query string, and the query string is written by whoever
 * sent the link, so every value here is escaped and the poster is checked against the same
 * allowlist the app and the client script use.
 */

/** Mirrors TitleShareLink.PUBLIC_ARTWORK_HOSTS in the Kotlin domain. Keep the three in step. */
const PUBLIC_ARTWORK_HOSTS = ['image.tmdb.org', 'www.themoviedb.org', 'themoviedb.org'];

const SITE_NAME = 'IPTV BURO';

export async function onRequestGet(context) {
  const { request, next } = context;
  const url = new URL(request.url);

  // The unmodified asset. Serving it through `next()` keeps one copy of the markup rather than a
  // duplicate maintained here.
  const response = await next();

  const contentType = response.headers.get('content-type') || '';
  if (!contentType.includes('text/html')) return response;

  const params = url.searchParams;
  const title = clamp(params.get('t'), 200);
  const identity = clamp(params.get('id'), 300);

  // Nothing to preview without these two, and the app refuses such a link anyway. The page is
  // served untouched so it can show its own neutral wording.
  if (!title || !identity) return response;

  const year = /^\d{4}$/.test(params.get('y') || '') ? params.get('y') : null;
  const description = clamp(params.get('d'), 400) || 'Compartilhado no IPTV BURO.';
  const poster = publicArtwork(params.get('img'));

  const headline = year ? `${title} (${year})` : title;
  const pageTitle = `${headline} — ${SITE_NAME}`;

  const tags = [
    meta('name', 'description', description),
    meta('property', 'og:type', 'video.other'),
    meta('property', 'og:site_name', SITE_NAME),
    meta('property', 'og:url', url.href),
    meta('property', 'og:title', pageTitle),
    meta('property', 'og:description', description),
    meta('name', 'twitter:title', pageTitle),
    meta('name', 'twitter:description', description),
    // summary_large_image only when there is an image to make large; the plain summary card is the
    // correct shape for a preview with no poster.
    meta('name', 'twitter:card', poster ? 'summary_large_image' : 'summary'),
    poster ? meta('property', 'og:image', poster) : '',
    poster ? meta('name', 'twitter:image', poster) : '',
  ].join('');

  return new HTMLRewriter()
    // Replaced rather than appended to: the static file ships a generic title and description, and
    // a crawler that meets two of each is entitled to believe the first.
    .on('title', {
      element(element) {
        element.setInnerContent(pageTitle);
      },
    })
    .on('meta[name="description"]', {
      element(element) {
        element.remove();
      },
    })
    .on('head', {
      element(element) {
        element.append(tags, { html: true });
      },
    })
    .transform(response);
}

/**
 * One meta tag, with the value escaped.
 *
 * The attribute value is quoted with `"` and every `"` in the content is escaped, so a title
 * containing a quote cannot close the attribute and introduce markup of its own.
 */
function meta(keyAttribute, key, value) {
  return `<meta ${keyAttribute}="${escapeAttribute(key)}" content="${escapeAttribute(value)}">`;
}

function escapeAttribute(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/** Trims, collapses whitespace and bounds a field. Null for anything empty. */
function clamp(value, maxLength) {
  if (!value) return null;
  const cleaned = value.replace(/\s+/g, ' ').trim();
  if (!cleaned) return null;
  return cleaned.length > maxLength ? `${cleaned.slice(0, maxLength).trimEnd()}…` : cleaned;
}

/**
 * The poster, if it is an https image on a known public metadata host.
 *
 * Same rule as the app: the provider's own artwork host is not on the list, so a link that carries
 * one — hand-written, or from some future version — is previewed without an image rather than
 * publishing the sender's server address to everyone in the chat.
 */
function publicArtwork(value) {
  if (!value) return null;
  let parsed;
  try {
    parsed = new URL(value);
  } catch (error) {
    return null;
  }
  if (parsed.protocol !== 'https:') return null;
  if (!PUBLIC_ARTWORK_HOSTS.includes(parsed.hostname.toLowerCase())) return null;
  return parsed.href;
}
