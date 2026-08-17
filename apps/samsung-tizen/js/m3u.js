/* Bounded M3U parser. Stream URLs remain in memory and are not catalog metadata. */
var BuroM3u = (function () {
    'use strict';

    var MAX_ITEMS = 100000;
    var MAX_LINE_LENGTH = 16384;

    function parseAttributes(payload) {
        var attributes = {};
        var expression = /([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))/g;
        var match;
        while ((match = expression.exec(payload)) !== null) {
            attributes[match[1].toLowerCase()] = match[2] != null ? match[2] :
                (match[3] != null ? match[3] : match[4]);
        }
        return attributes;
    }

    function contentType(url, group) {
        var value = (String(url || '') + ' ' + String(group || '')).toLowerCase();
        if (/\/(movie|vod)\//.test(value) || /\b(movie|movies|filmes|film)\b/.test(value)) {
            return BuroDomain.CONTENT.MOVIE;
        }
        if (/\/(series|episode)\//.test(value) || /\b(series|séries|serie)\b/.test(value)) {
            return BuroDomain.CONTENT.SERIES;
        }
        return BuroDomain.CONTENT.LIVE;
    }

    function parseExtInf(line) {
        var payload = line.substring(line.indexOf(':') + 1);
        var comma = payload.lastIndexOf(',');
        var attributesPart = comma >= 0 ? payload.substring(0, comma) : payload;
        var displayName = comma >= 0 ? payload.substring(comma + 1) : '';
        return { attributes: parseAttributes(attributesPart), name: BuroDomain.trim(displayName) };
    }

    function directFileExtension(url) {
        var path = String(url || '').split(/[?#]/)[0];
        var match = path.match(/\.([a-zA-Z0-9]{2,5})$/);
        var extension = match ? match[1].toLowerCase() : '';
        return /^(mp4|mkv|avi|mov|webm|ts|m2ts)$/.test(extension) ? extension : null;
    }

    function parse(text, sourceId) {
        var lines = String(text || '').replace(/^\uFEFF/, '').split(/\r?\n/);
        var entries = [];
        var warnings = [];
        var pending = null;
        var index;
        var line;
        var info;
        var attrs;
        var name;
        var group;
        var itemType;
        var item;

        if (!/^#EXTM3U/i.test(BuroDomain.trim(lines[0] || ''))) {
            throw new Error('M3U_HEADER_REQUIRED');
        }

        for (index = 1; index < lines.length && entries.length < MAX_ITEMS; index += 1) {
            line = BuroDomain.trim(lines[index]);
            if (!line) { continue; }
            if (line.length > MAX_LINE_LENGTH) { warnings.push('LINE_TOO_LONG'); pending = null; continue; }
            if (/^#EXTINF:/i.test(line)) { pending = parseExtInf(line); continue; }
            if (line.charAt(0) === '#') { continue; }
            if (!pending || !/^https?:\/\//i.test(line)) { pending = null; continue; }

            info = pending;
            attrs = info.attributes;
            name = info.name || attrs['tvg-name'] || 'Sem título';
            group = attrs['group-title'] || 'Outros';
            itemType = contentType(line, group);
            item = BuroDomain.createItem({
                sourceId: sourceId,
                providerItemId: attrs['tvg-id'] || String(entries.length),
                name: name,
                categoryId: BuroDomain.id('category', sourceId + ':' + itemType + ':' + group),
                contentType: itemType,
                sortOrder: entries.length,
                // Arte de provedor pode conter token/URL assinada; não é persistida.
                logoUrl: null,
                locator: {
                    kind: 'm3u-index', entryIndex: entries.length,
                    extension: directFileExtension(line)
                }
            });
            entries.push({
                item: item,
                group: group,
                streamUrl: line,
                // Mantida somente no resultado em memória; metadata() devolve apenas `item`.
                artworkUrl: attrs['tvg-logo'] || null
            });
            pending = null;
        }

        if (entries.length === MAX_ITEMS) { warnings.push('ITEM_LIMIT_REACHED'); }
        return { entries: entries, warnings: warnings };
    }

    function metadata(result) {
        return result.entries.map(function (entry) { return entry.item; });
    }

    return { parse: parse, parseAttributes: parseAttributes, metadata: metadata };
}());
