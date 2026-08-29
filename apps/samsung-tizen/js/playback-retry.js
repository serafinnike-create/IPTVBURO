/* Uma tentativa automática curta para falhas transitórias; depois o usuário decide. */
var BuroPlaybackRetry = (function () {
    'use strict';

    function create(options) {
        options = options || {};
        var delayMs = Math.max(0, Number(options.delayMs) || 1500);
        var maxRetries = Math.max(0, Math.floor(Number(options.maxRetries) || 1));
        var attempts = 0;
        var timer = null;

        function reset() {
            if (timer !== null) { clearTimeout(timer); timer = null; }
            attempts = 0;
        }

        function schedule(error, retry) {
            var code = error && (error.code || error.name || error.message);
            if (code !== 'PLAYBACK_CONNECTION' || attempts >= maxRetries || typeof retry !== 'function') {
                return false;
            }
            attempts += 1;
            timer = setTimeout(function () {
                timer = null;
                retry();
            }, delayMs);
            return true;
        }

        return { schedule: schedule, reset: reset };
    }

    return { create: create };
}());
