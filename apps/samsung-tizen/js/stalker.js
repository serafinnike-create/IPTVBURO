/* Stalker/Ministra source adapter. Secrets and playback commands stay in memory only. */
var BuroStalker = (function () {
    'use strict';

    var TOKEN_LIFETIME_MS = 10 * 60 * 1000;
    var MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    var MAX_CATEGORIES = 10000;
    var MAX_ITEMS_PER_PAGE = 10000;
    var MAX_COMMAND_LENGTH = 8192;
    var STB_USER_AGENT =
        'Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) ' +
        'MAG200 stbapp ver: 2 rev: 250 Safari/533.3';

    function trim(value) {
        if (typeof BuroDomain !== 'undefined' && BuroDomain.trim) {
            return BuroDomain.trim(value);
        }
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    function fail(code, status) {
        return { code: code, status: status || 0, message: code };
    }

    function safeCallback(callback, value) {
        if (typeof callback === 'function') { callback(value); }
    }

    function normalizeMac(value) {
        var compact = trim(value).toUpperCase().replace(/[\s:\-.]/g, '');
        var parts = [];
        var index;
        if (!/^[0-9A-F]{12}$/.test(compact)) { return null; }
        for (index = 0; index < compact.length; index += 2) {
            parts.push(compact.substring(index, index + 2));
        }
        return parts.join(':');
    }

    function maskMac(value) {
        var normalized = normalizeMac(value);
        return normalized ? '**:**:**:**:**:' + normalized.substring(15) : '<invalid>';
    }

    function parsePortal(value) {
        var raw = trim(value);
        var anchor;
        var path;
        if (!/^https?:\/\//i.test(raw)) { throw new Error('PORTAL_URL_INVALID'); }
        if (raw.length > 2048) { throw new Error('PORTAL_URL_INVALID'); }
        anchor = document.createElement('a');
        anchor.href = raw;
        if (!anchor.hostname || (anchor.protocol !== 'http:' && anchor.protocol !== 'https:')) {
            throw new Error('PORTAL_URL_INVALID');
        }
        if (anchor.username || anchor.password) { throw new Error('PORTAL_URL_INVALID'); }
        path = anchor.pathname || '/';
        path = path.replace(/\/{2,}/g, '/');
        return {
            origin: anchor.protocol + '//' + anchor.host,
            path: path,
            explicitEndpoint: /\.php\/?$/i.test(path)
        };
    }

    function normalizePortal(value) {
        var parsed = parsePortal(value);
        var path = parsed.path.replace(/\/+$/, '');
        return parsed.origin + (path && path !== '/' ? path : '');
    }

    function credentials(input) {
        var mac = normalizeMac(input && input.macAddress);
        var portal;
        if (!mac) { throw new Error('MAC_INVALID'); }
        portal = normalizePortal(input && input.portalUrl);
        return {
            portalUrl: portal,
            macAddress: mac,
            username: trim(input && input.username) || null,
            password: String(input && input.password || '') || null
        };
    }

    function candidateEndpoints(portalUrl) {
        var parsed = parsePortal(portalUrl);
        var endpoints = [];
        var seen = {};

        function add(path) {
            var url = parsed.origin + path;
            if (!seen[url]) {
                seen[url] = true;
                endpoints.push(url);
            }
        }

        if (parsed.explicitEndpoint) { add(parsed.path.replace(/\/+$/, '')); }
        add('/portal.php');
        add('/stalker_portal/server/load.php');
        add('/server/load.php');
        return endpoints;
    }

    function portalType(contentType) {
        if (contentType === 'LIVE') { return 'itv'; }
        if (contentType === 'MOVIE') { return 'vod'; }
        if (contentType === 'SERIES') { return 'series'; }
        return null;
    }

    function appendQuery(url, params) {
        var pairs = [];
        Object.keys(params).forEach(function (key) {
            if (params[key] != null) {
                pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(String(params[key])));
            }
        });
        return url + (url.indexOf('?') >= 0 ? '&' : '?') + pairs.join('&');
    }

    function requestHeaders(secret, token, timeZone) {
        var encodedMac = encodeURIComponent(secret.macAddress);
        var cookie = 'mac=' + encodedMac + '; stb_lang=en; timezone=' +
            encodeURIComponent(timeZone || 'Europe/London');
        var headers = {
            'Accept': '*/*',
            'X-User-Agent': 'Model: MAG250; Link: WiFi',
            'Cookie': cookie
        };
        if (token) {
            headers.Cookie += '; token=' + token;
            headers.Authorization = 'Bearer ' + token;
        }
        return headers;
    }

    function stringValue(value) {
        if (value == null || typeof value === 'object') { return null; }
        value = trim(value);
        return value ? value : null;
    }

    function boundedText(value, maximum) {
        var text = stringValue(value);
        return text ? text.substring(0, maximum) : null;
    }

    function safeToken(value) {
        var token = boundedText(value, 4096);
        if (!token || /[\s;,]/.test(token)) { return null; }
        return token;
    }

    function numberValue(value) {
        var parsed = Number(value);
        return isFinite(parsed) ? parsed : null;
    }

    function extractPlaybackUrl(command) {
        var text = boundedText(command, MAX_COMMAND_LENGTH);
        var tokens;
        var index;
        if (!text) { return null; }
        tokens = text.split(/\s+/);
        for (index = 0; index < tokens.length; index += 1) {
            if (/^https?:\/\//i.test(tokens[index]) && tokens[index].length <= MAX_COMMAND_LENGTH) {
                return tokens[index];
            }
        }
        return null;
    }

    function createAdapter(transport, options) {
        var network = transport;
        var clock = options && options.clock ? options.clock : function () { return Date.now(); };
        var timeZone = options && options.timeZone ? options.timeZone : 'Europe/London';
        var commandCache = {};

        function cacheKey(sourceId, contentType, providerItemId) {
            return String(sourceId || '') + '|' + String(contentType || '') + '|' +
                String(providerItemId || '');
        }

        function callCandidates(secret, token, params, success, failure) {
            var endpoints;
            var index = 0;
            var lastError = fail('NETWORK_ERROR');
            try {
                endpoints = candidateEndpoints(secret.portalUrl);
            } catch (error) {
                safeCallback(failure, fail('PORTAL_URL_INVALID'));
                return null;
            }

            function next() {
                var endpoint;
                var optionsForRequest;
                if (index >= endpoints.length) {
                    safeCallback(failure, lastError);
                    return;
                }
                endpoint = endpoints[index];
                index += 1;
                optionsForRequest = {
                    url: appendQuery(endpoint, params),
                    headers: requestHeaders(secret, token, timeZone),
                    maxBytes: MAX_RESPONSE_BYTES,
                    timeoutMs: 18000,
                    // A future native transport can use this identity. Browser XHR cannot set
                    // User-Agent itself, so X-User-Agent remains the portable request header.
                    clientUserAgent: STB_USER_AGENT,
                    followRedirects: false
                };
                network.json(optionsForRequest, function (payload) {
                    safeCallback(success, payload);
                }, function (error) {
                    var code = error && error.code;
                    var status = error && error.status;
                    if (code === 'AUTH_REJECTED' || status === 401 || status === 403) {
                        lastError = fail('UNAUTHORISED', status);
                    } else if (code === 'RESPONSE_TOO_LARGE') {
                        lastError = fail('MALFORMED');
                    } else if (code === 'MALFORMED_JSON') {
                        lastError = fail('MALFORMED');
                    } else {
                        lastError = fail('NETWORK', status);
                    }
                    next();
                });
            }

            if (!network || typeof network.json !== 'function') {
                safeCallback(failure, fail('TRANSPORT_UNAVAILABLE'));
                return null;
            }
            next();
            return { abort: function () { index = endpoints.length; } };
        }

        function handshake(secret, success, failure) {
            if (!secret || !normalizeMac(secret.macAddress)) {
                safeCallback(failure, fail('UNAUTHORISED'));
                return null;
            }
            return callCandidates(secret, null, {
                type: 'stb', action: 'handshake', token: '', JsHttpRequest: '1-xml'
            }, function (payload) {
                var token = payload && payload.js && safeToken(payload.js.token);
                if (!token) {
                    safeCallback(failure, fail('UNAUTHORISED'));
                    return;
                }
                safeCallback(success, {
                    token: token,
                    expiresAtEpochMillis: clock() + TOKEN_LIFETIME_MS
                });
            }, failure);
        }

        function sessionValid(session) {
            return Boolean(session && safeToken(session.token) &&
                Number(session.expiresAtEpochMillis) > clock());
        }

        function account(secret, session, success, failure) {
            if (!sessionValid(session)) {
                safeCallback(failure, fail('SESSION_EXPIRED'));
                return null;
            }
            return callCandidates(secret, session.token, {
                type: 'stb', action: 'get_main_info', JsHttpRequest: '1-xml'
            }, function (payload) {
                var js = payload && payload.js;
                var blocked;
                if (!js || typeof js !== 'object') {
                    safeCallback(failure, fail('MALFORMED'));
                    return;
                }
                blocked = String(js.blocked || '').toLowerCase();
                safeCallback(success, {
                    authenticated: true,
                    expiryDate: boundedText(js.phone, 80) || boundedText(js.end_date, 80),
                    tariffPlan: boundedText(js.tariff_plan, 120),
                    blocked: blocked === '1' || blocked === 'true'
                });
            }, failure);
        }

        function loadCategories(secret, session, sourceId, contentType, success, failure) {
            var type = portalType(contentType);
            var action = contentType === 'LIVE' ? 'get_genres' : 'get_categories';
            if (!type) {
                safeCallback(failure, fail('CONTENT_TYPE_INVALID'));
                return null;
            }
            if (!sessionValid(session)) {
                safeCallback(failure, fail('SESSION_EXPIRED'));
                return null;
            }
            return callCandidates(secret, session.token, {
                type: type, action: action, JsHttpRequest: '1-xml'
            }, function (payload) {
                var rows = payload && payload.js;
                var result = [];
                if (!Array.isArray(rows)) {
                    safeCallback(failure, fail('MALFORMED_CATEGORIES'));
                    return;
                }
                rows.slice(0, MAX_CATEGORIES).forEach(function (row, index) {
                    var providerId = row && boundedText(row.id, 120);
                    var name = row && (boundedText(row.title, 240) || boundedText(row.name, 240));
                    if (!providerId || providerId === '*' || !name) { return; }
                    result.push({
                        id: BuroDomain.id('category', sourceId + ':' + contentType + ':' + providerId),
                        sourceId: String(sourceId || ''),
                        providerCategoryId: providerId,
                        name: name,
                        contentType: contentType,
                        sortOrder: index
                    });
                });
                safeCallback(success, result);
            }, failure);
        }

        function loadItems(secret, session, sourceId, contentType, category, page, success, failure) {
            var type = portalType(contentType);
            var pageNumber = Number(page);
            var categoryId = category && category.providerCategoryId;
            var categoryKey = contentType === 'LIVE' ? 'genre' : 'category';
            var params;
            if (!type) {
                safeCallback(failure, fail('CONTENT_TYPE_INVALID'));
                return null;
            }
            if (!sessionValid(session)) {
                safeCallback(failure, fail('SESSION_EXPIRED'));
                return null;
            }
            if (!isFinite(pageNumber) || pageNumber < 1 || pageNumber > 100000) {
                safeCallback(failure, fail('PAGE_INVALID'));
                return null;
            }
            params = {
                type: type,
                action: 'get_ordered_list',
                p: Math.floor(pageNumber),
                sortby: 'added',
                JsHttpRequest: '1-xml'
            };
            params[categoryKey] = categoryId == null ? '*' : String(categoryId).substring(0, 120);
            return callCandidates(secret, session.token, params, function (payload) {
                var js = payload && payload.js;
                var rows = js && js.data;
                var result = [];
                var total;
                if (!js || !Array.isArray(rows)) {
                    safeCallback(failure, fail('MALFORMED_CATALOG'));
                    return;
                }
                rows.slice(0, MAX_ITEMS_PER_PAGE).forEach(function (row, index) {
                    var providerId = row && boundedText(row.id, 120);
                    var name = row && (boundedText(row.name, 240) || boundedText(row.o_name, 240));
                    var command = row && boundedText(row.cmd, MAX_COMMAND_LENGTH);
                    var rowCategory = row && (boundedText(row.category_id, 120) ||
                        boundedText(row.genre_id, 120));
                    var yearText = row && boundedText(row.year, 20);
                    var rating = row && numberValue(row.rating_imdb);
                    var locator;
                    if (!providerId || !name) { return; }
                    if (command) {
                        commandCache[cacheKey(sourceId, contentType, providerId)] = command;
                    }
                    locator = {
                        kind: 'stalker',
                        contentType: contentType,
                        providerItemId: providerId
                    };
                    result.push(BuroDomain.createItem({
                        sourceId: sourceId,
                        providerItemId: providerId,
                        name: name,
                        categoryId: category && category.id ? category.id : rowCategory,
                        contentType: contentType,
                        // Provider artwork can be signed or private and is never persisted.
                        logoUrl: null,
                        year: yearText ? Number(yearText.substring(0, 4)) || null : null,
                        rating: rating,
                        locator: locator
                    }));
                });
                total = numberValue(js.total_items);
                if (total == null || total < result.length) { total = result.length; }
                safeCallback(success, { items: result, totalItems: Math.floor(total) });
            }, failure);
        }

        function resolvePlayback(secret, session, sourceId, locator, success, failure) {
            var type;
            var command;
            if (!locator || locator.kind !== 'stalker' || !boundedText(locator.providerItemId, 120)) {
                safeCallback(failure, fail('LOCATOR_INVALID'));
                return null;
            }
            type = portalType(locator.contentType);
            if (!type) {
                safeCallback(failure, fail('CONTENT_TYPE_INVALID'));
                return null;
            }
            if (!sessionValid(session)) {
                safeCallback(failure, fail('SESSION_EXPIRED'));
                return null;
            }
            command = commandCache[cacheKey(sourceId, locator.contentType, locator.providerItemId)];
            if (!command) {
                safeCallback(failure, fail('COMMAND_NOT_IN_MEMORY'));
                return null;
            }
            return callCandidates(secret, session.token, {
                type: type,
                action: 'create_link',
                cmd: command,
                forced_storage: '0',
                disable_ad: '0',
                JsHttpRequest: '1-xml'
            }, function (payload) {
                var resolved = payload && payload.js && extractPlaybackUrl(payload.js.cmd);
                if (!resolved) {
                    safeCallback(failure, fail('MALFORMED_PLAYBACK'));
                    return;
                }
                // This URL may contain a single-use token. It is returned only to the caller.
                safeCallback(success, resolved);
            }, failure);
        }

        function clearSession() {
            commandCache = {};
        }

        return {
            normalizePortal: normalizePortal,
            normalizeMac: normalizeMac,
            maskMac: maskMac,
            credentials: credentials,
            handshake: handshake,
            account: account,
            loadCategories: loadCategories,
            loadItems: loadItems,
            resolvePlayback: resolvePlayback,
            clearSession: clearSession,
            sessionValid: sessionValid,
            extractPlaybackUrl: extractPlaybackUrl
        };
    }

    var adapter = createAdapter(
        typeof BuroNetwork !== 'undefined' ? BuroNetwork : null,
        null
    );
    adapter.createAdapter = createAdapter;
    return adapter;
}());
