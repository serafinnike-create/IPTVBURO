/* Bounded XHR client. URLs and response bodies never enter logs or errors. */
var BuroNetwork = (function () {
    'use strict';

    var DEFAULT_TIMEOUT_MS = 18000;
    var MAX_TEXT_BYTES = 16 * 1024 * 1024;

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
                if (xhr.status < 200 || xhr.status >= 300) {
                    fail(xhr.status === 401 || xhr.status === 403 ? 'AUTH_REJECTED' : 'HTTP_ERROR', xhr.status);
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
