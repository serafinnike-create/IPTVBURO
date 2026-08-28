/* Guia XMLTV para listas M3U, sem fonte ou credencial real. */
'use strict';

var fs = require('fs');
var path = require('path');
var JSDOM = require('jsdom').JSDOM;

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else { failures.push(label); process.stdout.write('  FALHA ' + label + '\n'); }
}

function makeWindow() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    ['js/domain.js', 'js/network.js', 'js/m3u.js', 'js/xmltv.js'].forEach(function (file) {
        window.eval(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
    });
    return window;
}

function guide(body) {
    return '<?xml version="1.0" encoding="UTF-8"?><tv>' + body + '</tv>';
}

function programme(channel, title, start, stop, description) {
    return '<programme channel="' + channel + '" start="' + start + '" stop="' + stop + '">' +
        '<title>' + title + '</title>' + (description ? '<desc>' + description + '</desc>' : '') +
        '</programme>';
}

function load(window, sourceId, urls, ids) {
    return new Promise(function (resolve) {
        window.BuroXmltv.load(sourceId, urls, ids, function (result) {
            resolve({ result: result });
        }, function (error) {
            resolve({ error: error });
        });
    });
}

async function run() {
    var window = makeWindow();
    var xml;
    var parsed;
    var m3u;
    var requests = [];
    var result;

    process.stdout.write('Cabeçalho M3U e privacidade\n');
    m3u = window.BuroM3u.parse(
        '#EXTM3U url-tvg="https://guide.synthetic/epg.xml?token=private" x-tvg-url="https://backup.synthetic/epg.xml"\n' +
        '#EXTINF:-1 tvg-id="Globo.br" group-title="TV",Canal\nhttps://stream.synthetic/live.m3u8',
        'source-xmltv'
    );
    check('url-tvg e x-tvg-url são descobertos em ordem e sem repetição',
        m3u.header.epgUrls.length === 2 && m3u.header.epgUrls[0].indexOf('guide.synthetic') >= 0);
    check('tvg-id permanece no locator seguro para cruzar com o guia',
        m3u.entries[0].item.locator.tvgId === 'Globo.br');
    check('metadata persistível não leva URL de guia nem stream',
        JSON.stringify(window.BuroM3u.metadata(m3u)).indexOf('guide.synthetic') === -1 &&
        JSON.stringify(window.BuroM3u.metadata(m3u)).indexOf('stream.synthetic') === -1);

    process.stdout.write('Parser XMLTV\n');
    xml = guide(
        programme('Globo.br', 'Segundo', '20260824210000 +0000', '20260824220000 +0000') +
        programme('outro', 'Não deve entrar', '20260824200000 +0000', '20260824210000 +0000') +
        programme('Globo.br', 'Primeiro &amp; Notícias', '20260824200000 +0000', '20260824210000 +0000',
            '<![CDATA[Resumo <seguro>.]]>') +
        programme('Globo.br', 'Fuso', '20260824223000 -0300', '20260824233000 -0300')
    );
    parsed = window.BuroXmltv.parse(xml, [' globo.BR ']);
    check('somente canais pedidos entram no índice',
        Object.keys(parsed.byChannel).length === 1 && parsed.byChannel['globo.br'].length === 3);
    check('programas são ordenados por transmissão',
        parsed.byChannel['globo.br'][0].title === 'Primeiro & Notícias' &&
        parsed.byChannel['globo.br'][1].title === 'Segundo');
    check('CDATA e entidades viram texto, nunca marcação executável',
        parsed.byChannel['globo.br'][0].description === 'Resumo <seguro>.');
    check('o fuso XMLTV é convertido para epoch UTC',
        parsed.byChannel['globo.br'][2].startEpochSeconds === Date.UTC(2026, 7, 25, 1, 30, 0) / 1000);
    check('lookup ignora caixa e espaços como o Windows', (function () {
        window.BuroXmltv.useParsedForTesting('source-xmltv', parsed);
        return window.BuroXmltv.schedule('source-xmltv', ' GLOBO.BR ').length === 3;
    }()));

    check('programa incompleto ou com duração negativa é descartado', (function () {
        var broken = guide(
            '<programme channel="c1" start="20260824210000 +0000"><title>Sem fim</title></programme>' +
            programme('c1', 'Invertido', '20260824220000 +0000', '20260824210000 +0000')
        );
        return window.BuroXmltv.parse(broken, ['c1']).count === 0;
    }()));
    check('DOCTYPE e entidades externas são recusados antes do parse', (function () {
        try {
            window.BuroXmltv.parse('<!DOCTYPE tv [<!ENTITY x SYSTEM "file:///etc/passwd">]><tv></tv>', ['c1']);
            return false;
        } catch (error) { return error.message === 'XMLTV_UNSAFE_XML'; }
    }()));

    process.stdout.write('Carregamento e alternates\n');
    window.BuroXmltv.clear();
    window.BuroNetwork.text = function (options, success, failure) {
        requests.push(options.url);
        if (requests.length === 1) { failure({ code: 'NETWORK_ERROR', message: 'URL privada não deve voltar' }); }
        else { success(xml); }
        return { abort: function () {} };
    };
    result = await load(window, 'source-load', [
        'https://first.synthetic/guide.xml?token=hidden',
        'https://second.synthetic/guide.xml',
        'https://third.synthetic/guide.xml',
        'https://fourth.synthetic/ignored.xml'
    ], ['globo.br']);
    check('alternates são tentados em ordem e param no primeiro guia válido',
        result.result && requests.length === 2 && requests[1].indexOf('second.synthetic') >= 0);
    check('a resposta carregada fica disponível por tvg-id',
        window.BuroXmltv.schedule('source-load', 'globo.br').length === 3);
    check('status e erro público nunca carregam URL ou token',
        JSON.stringify(window.BuroXmltv.status()).indexOf('synthetic') === -1 &&
        JSON.stringify(result).indexOf('hidden') === -1);
    check('somente três endereços HTTPS/HTTP limitados são aceitos',
        window.BuroXmltv.safeUrls(['javascript:alert(1)', 'https://a.test/x', 'https://a.test/x',
            'http://b.test/y', 'https://c.test/z', 'https://d.test/w']).join('|') ===
            'https://a.test/x|http://b.test/y|https://c.test/z');

    window.BuroNetwork.text = function (options, success) { success('\u001f\u008bconteudo'); };
    result = await load(window, 'source-gzip', ['https://guide.synthetic/raw.xml.gz'], ['c1']);
    check('gzip cru não vira texto corrompido nem agenda falsa',
        result.error && result.error.code === 'XMLTV_GZIP_UNSUPPORTED');

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte XMLTV: ' + error.stack + '\n');
    process.exitCode = 1;
});
