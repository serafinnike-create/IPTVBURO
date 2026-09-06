/* Receptor local efemero do smoke Tizen: aceita apenas o codigo publico APPCONTROL_*. */
'use strict';

var fs = require('fs');
var http = require('http');
var port = Number(process.argv[2]);
var resultFile = process.argv[3];
var readyFile = process.argv[4];
var stageFile = process.argv[5];
var shutdownNonce = process.argv[6];
var timeoutMs = Number(process.argv[7]);
var completed = false;
var responseHeaders = {
    'Content-Type': 'text/plain', 'Connection': 'close', 'Access-Control-Allow-Origin': '*'
};

if (!Number.isInteger(port) || port < 0 || port > 65535 || !resultFile || !readyFile || !stageFile ||
        !/^[a-f0-9-]{36}$/i.test(shutdownNonce || '') || !Number.isFinite(timeoutMs) || timeoutMs < 1000) {
    process.exit(64);
}

function finish(exitCode) {
    if (completed) { return; }
    completed = true;
    server.close(function () { process.exit(exitCode); });
    setTimeout(function () { process.exit(exitCode); }, 1000).unref();
}

var server = http.createServer(function (request, response) {
    var parsed;
    try { parsed = new URL(request.url, 'http://127.0.0.1'); }
    catch (ignoredUrl) { response.writeHead(400); response.end('invalid'); return; }
    if (parsed.pathname === '/shutdown' && parsed.searchParams.get('nonce') === shutdownNonce) {
        response.writeHead(200, responseHeaders);
        response.end('bye', function () { finish(0); });
        return;
    }
    var result = parsed.searchParams.get('result') || '';
    if (!/^APPCONTROL_[A-Z0-9_]{1,180}$/.test(result)) {
        response.writeHead(400, responseHeaders);
        response.end('invalid');
        return;
    }
    if (/^APPCONTROL_STAGE_[A-Z0-9_]{1,160}$/.test(result)) {
        fs.appendFileSync(stageFile, result + '\n', { encoding: 'utf8' });
        response.writeHead(200, responseHeaders);
        response.end('stage');
        return;
    }
    fs.writeFileSync(resultFile, result, { encoding: 'utf8', flag: 'wx' });
    response.writeHead(200, responseHeaders);
    response.end('ok', function () { finish(0); });
});

server.on('error', function () { process.exit(70); });
server.listen(port, '0.0.0.0', function () {
    fs.writeFileSync(readyFile, String(server.address().port), { encoding: 'ascii', flag: 'wx' });
});
setTimeout(function () { finish(2); }, timeoutMs).unref();
