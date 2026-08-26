/* Bounded XHR client. URLs and response bodies never enter logs or errors. */
var BuroNetwork = (function () {
    'use strict';

    var DEFAULT_TIMEOUT_MS = 18000;
    var MAX_TEXT_BYTES = 16 * 1024 * 1024;

    /**
     * Compares the host actually reached against the host asked for.
     *
     * `responseURL` is XHR Level 2; a runtime that predates it reports an empty string, and the
     * comparison is skipped rather than failing every request — a runtime this old cannot be made
     * to check something it has no way to report, and refusing everything would be worse than the
     * gap this exists to close. Parsed with an anchor element rather than the `URL` constructor,
     * matching every other host comparison in this codebase (xtream.js, stalker.js, share.js,
     * tmdb.js, app.js) — the one already proven to work on this runtime.
     */
    function sameHost(requestedUrl, respondedUrl) {
        if (!respondedUrl) { return true; }
        try {
            var requestedAnchor = document.createElement('a');
            var respondedAnchor = document.createElement('a');
            requestedAnchor.href = requestedUrl;
            respondedAnchor.href = respondedUrl;
            return requestedAnchor.host === respondedAnchor.host;
        } catch (error) {
            return true;
        }
    }

    function request(options, success, failure) {
        var xhr = new XMLHttpRequest();
        var completed = false;
        var method = options.method || 'GET';

        function fail(code, status) {
            if (completed) { return; }
            completed = true;
            failure({ code: code, status: status || 0, message: code });
        }

        try {
            xhr.open(method, options.url, true);
            xhr.timeout = options.timeoutMs || DEFAULT_TIMEOUT_MS;
            Object.keys(options.headers || {}).forEach(function (name) {
                xhr.setRequestHeader(name, options.headers[name]);
            });
            xhr.onreadystatechange = function () {
                var text;
                if (xhr.readyState !== 4 || completed) { return; }
                // XMLHttpRequest has no equivalent of OkHttp's followRedirects(false): the platform
                // follows a 3xx on its own before this handler ever runs. A compromised or hostile
                // Xtream server can therefore redirect an authenticated call anywhere it likes with
                // no warning — proven by dynamic testing against a mock server that pointed a login
                // call at an attacker-controlled host, which the client obediently followed. Checked
                // here instead: responseURL reports where the request actually landed once it is
                // done, and a host mismatch against what was asked for is treated as failure.
                //
                // Opt-in via options.pinHost, not the default for every caller of this shared
                // module: a remote M3U playlist (app.js) can legitimately sit behind a CDN that
                // redirects to a different storage domain, and failing that would turn a working
                // playlist into "unavailable". Only xtream.js's player_api.php calls — which carry
                // the customer's Xtream username and password in the URL itself — ask for this.
                if (options.pinHost && !sameHost(options.url, xhr.responseURL)) {
                    fail('UNEXPECTED_REDIRECT', xhr.status);
                    return;
                }
                text = xhr.responseText || '';
                if (text.length > (options.maxBytes || MAX_TEXT_BYTES)) {
                    fail('RESPONSE_TOO_LARGE', xhr.status);
                    return;
                }
                completed = true;
                success(text, xhr.status);
            };
            xhr.onerror = function () { fail('NETWORK_ERROR'); };
            xhr.ontimeout = function () { fail('NETWORK_TIMEOUT'); };
            xhr.onabort = function () { fail('NETWORK_ABORTED'); };
            xhr.send(options.body || null);
        } catch (error) {
            fail('NETWORK_SETUP_FAILED');
        }
        return { abort: function () { try { xhr.abort(); } catch (ignore) {} } };
    }

    function json(options, success, failure) {
        return request(options, function (text) {
            var value;
            try { value = JSON.parse(text); } catch (error) {
                failure({ code: 'MALFORMED_JSON', status: 0, message: 'MALFORMED_JSON' });
                return;
            }
            success(value);
        }, failure);
    }

    function text(options, success, failure) { return request(options, success, failure); }

    return { request: request, json: json, text: text };
}());
