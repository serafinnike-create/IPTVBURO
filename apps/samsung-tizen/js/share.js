/* Compartilhamento é recomendação pública de título — nunca stream, fonte ou credencial. */
var BuroShare = (function () {
    'use strict';

    var BASE_URL = 'https://iptvburo.pages.dev';
    var MAX_DESCRIPTION = 400;
    var MAX_INCOMING_LINK = 4096;
    var MAX_INCOMING_FIELD = 300;
    var NOISE = {};
    ('4k uhd hd sd fhd hdr hdr10 dolby atmos vision 1080p 1080 720p 720 480p 2160p 2160 ' +
        'h264 h265 hevc x264 x265 avc aac ac3 dts dub dubbed dublado leg legendado sub subbed ' +
        'subtitulado dual multi audio remux bluray webrip webdl web l d ptbr pt br en es lat latino vo vose')
        .split(' ').forEach(function (token) { NOISE[token] = true; });

    function clean(value) { return BuroDomain.trim(value); }

    function slugify(title) {
        return BuroDomain.foldAccents(String(title || '').replace(/\[[^\]]*\]/g, ' ').replace(/\((\d{4})\)/g, ' '))
            .split(/\s+/).filter(function (token) { return token && !NOISE[token]; }).join('-');
    }

    function javaHashHex(value) {
        var hash = 0;
        var text = String(value || '');
        var index;
        for (index = 0; index < text.length; index += 1) { hash = ((hash * 31) + text.charCodeAt(index)) | 0; }
        return (hash >>> 0).toString(16);
    }

    function identity(kind, title, year) {
        var type = String(kind || 'UNKNOWN').toLowerCase();
        var stem = slugify(title) || ('raw' + javaHashHex(clean(title)));
        var validYear = Number(year);
        if (['movie', 'series', 'episode', 'live', 'unknown'].indexOf(type) < 0) { type = 'unknown'; }
        return type + ':' + stem + (validYear >= 1888 && validYear <= 2100 ? ':' + Math.floor(validYear) : '');
    }

    function publicArtwork(value) {
        var raw = clean(value);
        var anchor;
        var authority;
        var allowed = ['image.tmdb.org', 'www.themoviedb.org', 'themoviedb.org'];
        if (!/^https:\/\//i.test(raw)) { return null; }
        authority = raw.replace(/^https:\/\//i, '').split(/[\/?#]/)[0];
        if (authority.indexOf('@') >= 0) { return null; }
        try { anchor = document.createElement('a'); anchor.href = raw; }
        catch (ignoredUrl) { return null; }
        return anchor.protocol === 'https:' && allowed.indexOf(String(anchor.hostname || '').toLowerCase()) >= 0 ? anchor.href : null;
    }

    function description(value) {
        var text = clean(String(value || '').replace(/\s+/g, ' '));
        if (!text) { return null; }
        return text.length <= MAX_DESCRIPTION ? text : text.substring(0, MAX_DESCRIPTION).replace(/\s+$/, '') + '…';
    }

    function encode(value) { return encodeURIComponent(String(value)).replace(/[!'()*]/g, function (character) {
        return '%' + character.charCodeAt(0).toString(16).toUpperCase();
    }); }

    function query(value, options) {
        var fields = [['id', value.identity], ['t', value.title]];
        options = options || {};
        if (value.year) { fields.push(['y', value.year]); }
        if (value.artworkUrl && options.artwork !== false) { fields.push(['img', value.artworkUrl]); }
        if (value.description && options.description !== false) { fields.push(['d', value.description]); }
        return fields.map(function (field) { return field[0] + '=' + encode(field[1]); }).join('&');
    }

    function webUrl(value, options) { return BASE_URL + '/t/?' + query(value, options); }

    /* O esquema privado e o que o Web App Samsung declara no config.xml. */
    function appUri(value, options) { return 'iptvburo://title?' + query(value, options); }

    function qr(value) {
        var candidates = [webUrl(value), webUrl(value, { description: false }),
            webUrl(value, { artwork: false, description: false })];
        var selected = null;
        var matrix = null;
        candidates.some(function (candidate) {
            matrix = BuroQr.encode(candidate);
            if (matrix) { selected = candidate; return true; }
            return false;
        });
        return { url: selected, matrix: matrix, svg: BuroQr.svg(matrix) };
    }

    function build(input) {
        var title = clean(input && input.title);
        var year = Number(input && input.year);
        var result;
        if (!title) { return null; }
        result = {
            identity: identity(input.kind, title, year), title: title,
            year: year >= 1888 && year <= 2100 ? Math.floor(year) : null,
            artworkUrl: publicArtwork(input.artworkUrl), description: description(input.description)
        };
        result.webUrl = webUrl(result);
        result.appUri = appUri(result);
        result.qr = qr(result);
        return result;
    }

    function parse(raw) {
        var queryText = String(raw || '').split('?')[1];
        var fields = {};
        if (!queryText) { return null; }
        queryText.split('#')[0].split('&').forEach(function (pair) {
            var parts = pair.split('=');
            var name = parts.shift();
            var value;
            if (!name || !parts.length) { return; }
            try { value = decodeURIComponent(parts.join('=').replace(/\+/g, '%20')); }
            catch (ignoredValue) { return; }
            if (value) { fields[name] = value; }
        });
        if (!fields.id || !fields.t) { return null; }
        fields.t = clean(fields.t);
        if (!fields.t) { return null; }
        return {
            identity: fields.id, title: fields.t, year: Number(fields.y) || null,
            artworkUrl: publicArtwork(fields.img), description: description(fields.d)
        };
    }

    /*
      Entrada do app-control: o parser generico acima tambem atende ao link web
      exibido no QR, mas o runtime da TV so deve aceitar o esquema que o
      manifesto registrou. A checagem acontece antes de qualquer consulta ao
      catalogo e nenhum campo recebido vira URL de reproducao.
    */
    function validatedIncoming(text) {
        var value;
        value = parse(text);
        if (!value || value.identity.length > MAX_INCOMING_FIELD || value.title.length > MAX_INCOMING_FIELD) {
            return null;
        }
        if (/[\u0000-\u001f\u007f]/.test(value.identity) || /[\u0000-\u001f\u007f]/.test(value.title) ||
                !/^(movie|series|episode|live|unknown):[^:]+(?::\d{4})?$/i.test(value.identity)) { return null; }
        return value;
    }

    function safeIncomingText(raw) {
        var text = clean(raw);
        return !text || text.length > MAX_INCOMING_LINK || /[\u0000-\u001f\u007f\s]/.test(text) ||
            text.indexOf('#') >= 0 ? null : text;
    }

    function parseIncoming(raw) {
        var text = safeIncomingText(raw);
        if (!text || !/^iptvburo:\/\/title\/?\?/i.test(text)) { return null; }
        return validatedIncoming(text);
    }

    /* O relay aceita somente os dois enderecos emitidos pelo IPTV BURO. */
    function parsePairingPayload(raw) {
        var text = safeIncomingText(raw);
        if (!text || (!/^iptvburo:\/\/title\/?\?/i.test(text) &&
                !/^https:\/\/iptvburo\.pages\.dev\/t\/?\?/i.test(text))) { return null; }
        return validatedIncoming(text);
    }

    return {
        build: build, parse: parse, parseIncoming: parseIncoming, parsePairingPayload: parsePairingPayload,
        identity: identity, publicArtwork: publicArtwork,
        maxDescription: MAX_DESCRIPTION, baseUrl: BASE_URL
    };
}());
