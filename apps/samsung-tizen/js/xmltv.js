/* XMLTV guide adapter for M3U sources. Guide URLs remain in the Tizen KeyManager. */
var BuroXmltv = (function () {
    'use strict';

    var MAX_URLS = 3;
    var MAX_URL_LENGTH = 2048;
    var MAX_RESPONSE_BYTES = 24 * 1024 * 1024;
    var MAX_PROGRAMMES = 100000;
    var MAX_PER_CHANNEL = 400;
    var MAX_FIELD_LENGTH = 300;
    var MAX_DESCRIPTION_LENGTH = 2000;
    var cache = {};
    var cacheOrder = [];

    function normalizedId(value) {
        return BuroDomain.trim(value).toLowerCase();
    }

    function safeUrls(values) {
        var rows = [];
        var seen = {};
        (Array.isArray(values) ? values : []).some(function (value) {
            var clean = BuroDomain.trim(value);
            if (rows.length >= MAX_URLS) { return true; }
            if (!clean || clean.length > MAX_URL_LENGTH || /[\u0000-\u001f\u007f]/.test(clean) ||
                    !/^https?:\/\/[^\s]+$/i.test(clean) || seen[clean]) { return false; }
            seen[clean] = true;
            rows.push(clean);
            return false;
        });
        return rows;
    }

    function decodeEntities(value) {
        return String(value || '').replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, function (all, entity) {
            var lower = entity.toLowerCase();
            var number;
            if (lower === 'amp') { return '&'; }
            if (lower === 'lt') { return '<'; }
            if (lower === 'gt') { return '>'; }
            if (lower === 'quot') { return '"'; }
            if (lower === 'apos') { return "'"; }
            number = lower.indexOf('#x') === 0 ? parseInt(lower.substring(2), 16) : parseInt(lower.substring(1), 10);
            if (!isFinite(number) || number < 0 || number > 0x10ffff || (number >= 0xd800 && number <= 0xdfff)) {
                return '';
            }
            if (number <= 0xffff) { return String.fromCharCode(number); }
            number -= 0x10000;
            return String.fromCharCode(0xd800 + (number >> 10), 0xdc00 + (number & 1023));
        });
    }

    function elementText(body, name, limit) {
        var expression = new RegExp('<' + name + '(?:\\s[^>]*)?>([\\s\\S]*?)<\\/' + name + '\\s*>', 'i');
        var match = expression.exec(body);
        var cdata = [];
        var text;
        if (!match) { return ''; }
        text = match[1].replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, function (all, content) {
            var token = '___BURO_CDATA_' + cdata.length + '___';
            cdata.push(content);
            return token;
        });
        text = decodeEntities(text.replace(/<[^>]*>/g, ' '));
        cdata.forEach(function (content, index) {
            text = text.replace('___BURO_CDATA_' + index + '___', content);
        });
        return BuroDomain.trim(text.replace(/\s+/g, ' ')).substring(0, limit);
    }

    function attribute(attributes, name) {
        var expression = new RegExp('(?:^|\\s)' + name + '\\s*=\\s*(?:"([^"]*)"|\'([^\']*)\')', 'i');
        var match = expression.exec(attributes || '');
        return match ? decodeEntities(match[1] != null ? match[1] : match[2]) : '';
    }

    function parseXmltvTime(value) {
        var text = BuroDomain.trim(value);
        var match = /^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-])(\d{2})(\d{2}))?/.exec(text);
        var year;
        var month;
        var day;
        var hour;
        var minute;
        var second;
        var utc;
        var check;
        var offset = 0;
        if (!match) { return null; }
        year = Number(match[1]); month = Number(match[2]); day = Number(match[3]);
        hour = Number(match[4]); minute = Number(match[5]); second = Number(match[6]);
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || minute > 59 || second > 59) {
            return null;
        }
        utc = Date.UTC(year, month - 1, day, hour, minute, second);
        check = new Date(utc);
        if (check.getUTCFullYear() !== year || check.getUTCMonth() !== month - 1 || check.getUTCDate() !== day ||
                check.getUTCHours() !== hour || check.getUTCMinutes() !== minute || check.getUTCSeconds() !== second) {
            return null;
        }
        if (match[7]) {
            if (Number(match[8]) > 23 || Number(match[9]) > 59) { return null; }
            offset = (Number(match[8]) * 60 + Number(match[9])) * 60;
            if (match[7] === '-') { offset = -offset; }
        }
        return Math.floor(utc / 1000) - offset;
    }

    function parse(text, wantedIds) {
        var documentText = String(text || '');
        var wanted = {};
        var byChannel = {};
        var expression = /<programme\b([^>]*)>([\s\S]*?)<\/programme\s*>/gi;
        var match;
        var channel;
        var start;
        var end;
        var title;
        var description;
        var count = 0;
        if (/<!DOCTYPE\b|<!ENTITY\b/i.test(documentText)) { throw new Error('XMLTV_UNSAFE_XML'); }
        (Array.isArray(wantedIds) ? wantedIds : []).forEach(function (value) {
            var key = normalizedId(value);
            if (key) { wanted[key] = true; }
        });
        while (count < MAX_PROGRAMMES && (match = expression.exec(documentText)) !== null) {
            channel = normalizedId(attribute(match[1], 'channel'));
            if (!channel || !wanted[channel]) { continue; }
            if (!byChannel[channel]) { byChannel[channel] = []; }
            if (byChannel[channel].length >= MAX_PER_CHANNEL) { continue; }
            start = parseXmltvTime(attribute(match[1], 'start'));
            end = parseXmltvTime(attribute(match[1], 'stop'));
            title = elementText(match[2], 'title', MAX_FIELD_LENGTH);
            if (start == null || end == null || end <= start || !title) { continue; }
            description = elementText(match[2], 'desc', MAX_DESCRIPTION_LENGTH);
            byChannel[channel].push({
                title: title,
                description: description || null,
                startEpochSeconds: start,
                endEpochSeconds: end,
                start: null,
                end: null
            });
            count += 1;
        }
        Object.keys(byChannel).forEach(function (key) {
            byChannel[key].sort(function (left, right) {
                return left.startEpochSeconds - right.startEpochSeconds;
            });
        });
        return { byChannel: byChannel, count: count };
    }

    function remember(sourceId, parsed) {
        if (!BuroDomain.safeId(sourceId)) { throw new Error('SOURCE_ID_INVALID'); }
        if (!cache[sourceId]) { cacheOrder.push(sourceId); }
        cache[sourceId] = parsed;
        while (cacheOrder.length > 2) { delete cache[cacheOrder.shift()]; }
    }

    function schedule(sourceId, tvgId) {
        var parsed = cache[sourceId];
        var rows = parsed && parsed.byChannel[normalizedId(tvgId)];
        return rows ? rows.slice() : [];
    }

    function clear(sourceId) {
        var index;
        if (!sourceId) { cache = {}; cacheOrder = []; return; }
        delete cache[sourceId];
        index = cacheOrder.indexOf(sourceId);
        if (index >= 0) { cacheOrder.splice(index, 1); }
    }

    function status() {
        var programmeCount = 0;
        Object.keys(cache).forEach(function (sourceId) {
            programmeCount += Number(cache[sourceId] && cache[sourceId].count) || 0;
        });
        return { cachedSources: cacheOrder.length, programmeCount: programmeCount };
    }

    function publicError(code) {
        return { code: code, message: code };
    }

    function load(sourceId, urls, wantedIds, success, failure) {
        var candidates = safeUrls(urls);
        var request = null;
        var index = 0;
        var ids = (Array.isArray(wantedIds) ? wantedIds : []).map(normalizedId).filter(function (value) {
            return Boolean(value);
        });
        function next() {
            var url;
            if (index >= candidates.length) { failure(publicError('XMLTV_UNAVAILABLE')); return; }
            url = candidates[index]; index += 1;
            request = BuroNetwork.text({ url: url, maxBytes: MAX_RESPONSE_BYTES, timeoutMs: 180000 }, function (text) {
                var parsed;
                if (text && text.charCodeAt(0) === 0x1f && text.charCodeAt(1) === 0x8b) {
                    failure(publicError('XMLTV_GZIP_UNSUPPORTED')); return;
                }
                try { parsed = parse(text, ids); }
                catch (error) {
                    if (error && error.message === 'XMLTV_UNSAFE_XML') { failure(publicError('XMLTV_UNSAFE_XML')); }
                    else { next(); }
                    return;
                }
                if (!parsed.count) { next(); return; }
                remember(sourceId, parsed);
                success({ count: parsed.count });
            }, function () { next(); });
            url = null;
        }
        if (!BuroDomain.safeId(sourceId) || !ids.length || !candidates.length) {
            failure(publicError('XMLTV_UNAVAILABLE'));
            return { abort: function () {} };
        }
        clear(sourceId);
        next();
        return { abort: function () { if (request && request.abort) { request.abort(); } } };
    }

    return {
        safeUrls: safeUrls,
        parseXmltvTime: parseXmltvTime,
        parse: parse,
        load: load,
        schedule: schedule,
        clear: clear,
        status: status,
        useParsedForTesting: remember
    };
}());
