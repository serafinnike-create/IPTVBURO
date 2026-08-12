/**
 * Renders a shared title and offers to open it in the installed app.
 *
 * The query string is written by whoever sent the link, so everything here treats it as untrusted:
 *
 * - text goes in through `textContent`, never `innerHTML`, so a title containing markup is shown as
 *   the characters it is rather than parsed;
 * - the poster is accepted only from the same public metadata hosts the app allows, so a link
 *   cannot turn this page into a beacon pointing at a host of the sender's choosing;
 * - the "open" link is rebuilt here from the parsed fields rather than echoed from the URL, so it
 *   can only ever be an `iptvburo://title?…` address.
 *
 * The page never redirects on its own. An automatic jump to a custom scheme shows an OS error on
 * every device that does not have the app, which is precisely the visitor this page exists for.
 */
(function () {
  'use strict';

  /** Mirrors TitleShareLink.PUBLIC_ARTWORK_HOSTS in the Kotlin domain. Keep the two in step. */
  var PUBLIC_ARTWORK_HOSTS = ['image.tmdb.org', 'www.themoviedb.org', 'themoviedb.org'];

  var params = new URLSearchParams(window.location.search);
  var identity = (params.get('id') || '').trim();
  var title = (params.get('t') || '').trim();
  var year = (params.get('y') || '').trim();
  var plot = (params.get('d') || '').trim();
  var poster = (params.get('img') || '').trim();

  if (title) {
    document.querySelector('[data-title]').textContent = title;
    document.title = title + ' — IPTV BURO';
  }

  if (/^\d{4}$/.test(year)) {
    var yearNode = document.querySelector('[data-year]');
    yearNode.textContent = year;
    yearNode.hidden = false;
  }

  if (plot) {
    document.querySelector('[data-plot]').textContent = plot;
  }

  if (isPublicArtwork(poster)) {
    var figure = document.querySelector('[data-art]');
    document.querySelector('[data-poster]').src = poster;
    figure.hidden = false;
  }

  // Both fields are required to name a title, and the app refuses a link missing either — so
  // offering the button without them would send the visitor to a dead end.
  if (identity && title) {
    var open = document.querySelector('[data-open]');
    var target = new URLSearchParams();
    target.set('id', identity);
    target.set('t', title);
    if (/^\d{4}$/.test(year)) target.set('y', year);
    open.href = 'iptvburo://title?' + target.toString();
    open.hidden = false;
  }

  /**
   * True when `url` is an https image on a known public metadata host.
   *
   * Parsed with the URL constructor rather than matched with a regular expression: `hostname`
   * already excludes any `user:pass@` prefix and any port, which is where a hand-written check
   * reads the wrong side of the string and admits an arbitrary host.
   */
  function isPublicArtwork(url) {
    if (!url) return false;
    var parsed;
    try {
      parsed = new URL(url);
    } catch (error) {
      return false;
    }
    if (parsed.protocol !== 'https:') return false;
    return PUBLIC_ARTWORK_HOSTS.indexOf(parsed.hostname.toLowerCase()) !== -1;
  }
})();
