/* Contratos da importação M3U local Samsung: USB transitório, seleção opaca e leitura limitada. */
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

function file(name, fullPath, text, declaredSize) {
    return {
        name: name, fullPath: fullPath, fileSize: declaredSize == null ? text.length : declaredSize,
        isDirectory: false,
        openStream: function (mode, success) {
            success({ read: function () { return text; }, close: function () {} });
        }
    };
}

function directory(name, children) {
    return { name: name, isDirectory: true, listFiles: function (success) { success(children); } };
}

function makeWindow() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', { runScripts: 'outside-only', url: 'https://iptvburo.test/' });
    var window = dom.window;
    var state = { mounted: true, label: 'removable_usb_a', duplicate: false };
    var primaryText = '#EXTM3U\n#EXTINF:-1 group-title="Filmes",Filme USB\nhttps://media.public.test/movie.mp4';
    function root() {
        var children = [
            file('lista.m3u', '/private/lista.m3u', primaryText),
            file('notas.txt', '/private/notas.txt', 'ignorar'),
            file('enorme.m3u', '/private/enorme.m3u', '#EXTM3U', 17 * 1024 * 1024),
            directory('listas', [
                file('radio.m3u8', '/private/listas/radio.m3u8', '#EXTM3U\n#EXTINF:-1,Rádio\nhttps://radio.public.test/live'),
                directory('nivel2', [directory('nivel3', [
                    file('profunda.m3u', '/private/listas/nivel2/nivel3/profunda.m3u', '#EXTM3U')
                ])])
            ])
        ];
        if (state.duplicate) {
            children.push(directory('duplicada', [file('lista.m3u', '/private/duplicada/lista.m3u', primaryText)]));
        }
        return directory('root', children);
    }
    window.tizen = { filesystem: {
        listStorages: function (success) {
            success([{ label: state.label, state: state.mounted ? 'MOUNTED' : 'REMOVED' }]);
        },
        addStorageStateChangeListener: function () { return 1; },
        resolve: function (label, success, failure) {
            if (!state.mounted || label !== state.label) { failure({ name: 'NotFoundError' }); return; }
            success(root());
        }
    } };
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/domain.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/usb.js'), 'utf8'));
    window.__fixture = state;
    window.__primaryText = primaryText;
    return window;
}

function list(window) {
    return new Promise(function (resolve, reject) { window.BuroUsb.listPlaylists(resolve, reject); });
}

function read(window, key) {
    return new Promise(function (resolve, reject) {
        window.BuroUsb.readPlaylist(key, function (text, descriptor) { resolve({ text: text, descriptor: descriptor }); }, reject);
    });
}

function resolveStored(window, selector) {
    return new Promise(function (resolve, reject) {
        window.BuroUsb.resolvePlaylist(selector, function (text, descriptor) { resolve({ text: text, descriptor: descriptor }); }, reject);
    });
}

async function run() {
    var window = makeWindow();
    var files;
    var primary;
    var content;
    var selector;
    var failureCode = null;
    window.BuroUsb.watch(function () {});
    files = await list(window);
    check('lista somente M3U/M3U8 dentro do tamanho e profundidade permitidos',
        files.length === 2 && files.some(function (row) { return row.name === 'lista.m3u'; }) &&
        files.some(function (row) { return row.name === 'radio.m3u8'; }) &&
        !files.some(function (row) { return row.name === 'enorme.m3u' || row.name === 'profunda.m3u'; }));
    check('descritores visíveis não expõem caminho nem URI do USB',
        JSON.stringify(files).indexOf('/private/') === -1 && JSON.stringify(files).indexOf('file:') === -1 &&
        files.every(function (row) { return /^usb-playlist-[a-z0-9]+$/.test(row.key); }));
    primary = files.filter(function (row) { return row.name === 'lista.m3u'; })[0];
    content = await read(window, primary.key);
    check('arquivo selecionado é lido como UTF-8 somente pela chave transitória',
        content.text === window.__primaryText && content.descriptor.key === primary.key);
    selector = { playlistToken: primary.key, fileName: primary.name, fileSize: primary.size };
    content = await resolveStored(window, selector);
    check('token opaco reencontra a lista após nova enumeração', content.text === window.__primaryText);

    window.__fixture.label = 'removable_usb_b';
    await new Promise(function (resolve) { window.BuroUsb.refresh(resolve, resolve); });
    content = await resolveStored(window, selector);
    check('nome e tamanho únicos recuperam a lista se o rótulo do volume mudar', content.text === window.__primaryText);

    window.__fixture.duplicate = true;
    await resolveStored(window, { playlistToken: 'usb-playlist-inexistente', fileName: primary.name, fileSize: primary.size })
        .catch(function (error) { failureCode = error.code; });
    check('fallback ambíguo falha fechado em vez de escolher outra lista', failureCode === 'PLAYLIST_UNAVAILABLE');

    failureCode = null;
    window.BuroUsb.readPlaylist('/private/lista.m3u', function () {}, function (error) { failureCode = error.code; });
    check('caminho fornecido pelo chamador nunca é aceito como chave', failureCode === 'PLAYLIST_UNAVAILABLE');

    window.__fixture.mounted = false;
    await new Promise(function (resolve) { window.BuroUsb.refresh(resolve, resolve); });
    files = await list(window);
    check('retirar o USB limpa referências transitórias e deixa a lista vazia', files.length === 0);

    window.close();
    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
    } else { process.stdout.write('Todos os ' + passed + ' testes passaram.\n'); }
}

run().catch(function (error) {
    process.stderr.write('Falha na suíte: ' + error.stack + '\n');
    process.exitCode = 1;
});
