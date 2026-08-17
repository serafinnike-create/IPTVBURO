/* Contratos da foto de perfil Samsung: USB transitório e JPEG privado pequeno. */
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

function imageFile(name, size, uri, encoded) {
    return {
        name: name,
        fileSize: size,
        isDirectory: false,
        fullPath: '/private/' + name,
        toURI: function () { return uri; },
        openStream: function (mode, success) {
            success({
                readBase64: function () { return encoded; },
                close: function () {}
            });
        }
    };
}

function directory(name, children) {
    return {
        name: name,
        isDirectory: true,
        listFiles: function (success) { success(children); }
    };
}

function makeWindow() {
    var dom = new JSDOM('<!doctype html><html><body></body></html>', {
        runScripts: 'outside-only', url: 'https://iptvburo.test/'
    });
    var window = dom.window;
    var state = { mounted: true, crop: null, outputCalls: 0 };
    var nestedPng = imageFile('familia.png', 256, 'file:///usb/fotos/familia.png', 'UE5H');
    var rootJpeg = imageFile('sala.jpg', 128, 'file:///usb/sala.jpg', 'SlBFRw==');
    var tooLarge = imageFile('enorme.jpg', 6 * 1024 * 1024, 'file:///usb/enorme.jpg', 'QQ==');
    var root = directory('root', [
        rootJpeg,
        tooLarge,
        imageFile('notas.txt', 20, 'file:///usb/notas.txt', 'VEVYVA=='),
        directory('fotos', [nestedPng, directory('nivel2', [
            imageFile('capa.webp', 300, 'file:///usb/fotos/nivel2/capa.webp', 'V0VCUA=='),
            directory('nivel3', [imageFile('ignorar.jpg', 100, 'file:///usb/ignorar.jpg', 'WA==')])
        ])])
    ]);

    window.tizen = { filesystem: {
        listStorages: function (success) {
            success([{ label: 'removable_fixture', state: state.mounted ? 'MOUNTED' : 'REMOVED' }]);
        },
        addStorageStateChangeListener: function () { return 1; },
        resolve: function (label, success, failure) {
            if (!state.mounted || label !== 'removable_fixture') { failure({ name: 'NotFoundError' }); return; }
            success(root);
        }
    } };

    window.Image = function () {
        this.naturalWidth = 960;
        this.naturalHeight = 640;
    };
    Object.defineProperty(window.Image.prototype, 'src', {
        set: function () { this.onload(); }
    });
    window.HTMLCanvasElement.prototype.getContext = function () {
        return {
            clearRect: function () {},
            drawImage: function () { state.crop = Array.prototype.slice.call(arguments, 1); }
        };
    };
    window.HTMLCanvasElement.prototype.toDataURL = function () {
        state.outputCalls += 1;
        return 'data:image/jpeg;base64,' + (state.outputCalls === 1 ? 'A'.repeat(220000) : 'SlBFRw==');
    };

    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/usb.js'), 'utf8'));
    window.eval(fs.readFileSync(path.join(APP_DIR, 'js/profile-photo.js'), 'utf8'));
    window.__fixture = state;
    return window;
}

function listImages(window) {
    return new Promise(function (resolve, reject) { window.BuroUsb.listImages(resolve, reject); });
}

function readImage(window, key) {
    return new Promise(function (resolve, reject) { window.BuroUsb.readImage(key, resolve, reject); });
}

function resize(window, source) {
    return new Promise(function (resolve, reject) { window.BuroProfilePhoto.resize(source, resolve, reject); });
}

async function run() {
    var window = makeWindow();
    var images;
    var source;
    var photo;
    var missingCode = null;

    window.BuroUsb.watch(function () {});
    images = await listImages(window);
    check('lista somente JPG, PNG e WebP dentro do limite e da profundidade segura',
        images.length === 3 && images.some(function (row) { return row.name === 'sala.jpg'; }) &&
        images.some(function (row) { return row.name === 'familia.png'; }) &&
        images.some(function (row) { return row.name === 'capa.webp'; }) &&
        !images.some(function (row) { return row.name === 'enorme.jpg' || row.name === 'ignorar.jpg'; }));
    check('metadados visíveis não expõem caminho nem URI do USB',
        JSON.stringify(images).indexOf('file:') === -1 && JSON.stringify(images).indexOf('/private/') === -1);
    check('URI de prévia existe apenas através da chave transitória',
        window.BuroUsb.imagePreviewUrl(images[0].key).indexOf('file:///usb/') === 0);

    source = await readImage(window, images[0].key);
    check('leitura produz somente data URL do tipo validado', /^data:image\/jpeg;base64,/.test(source));
    photo = await resize(window, 'data:image/png;base64,UE5H');
    check('recorta o centro em quadrado e gera JPEG privado',
        photo === 'data:image/jpeg;base64,SlBFRw==' &&
        window.__fixture.crop[0] === 160 && window.__fixture.crop[1] === 0 &&
        window.__fixture.crop[2] === 640 && window.__fixture.crop[3] === 640);
    check('reduz dimensão e qualidade quando a primeira saída ultrapassa o limite',
        window.__fixture.outputCalls === 2 && window.__fixture.crop[4] === 0 && window.__fixture.crop[5] === 0 &&
        window.__fixture.crop[6] === 256 && window.__fixture.crop[7] === 256);
    check('persistência aceita só JPEG pequeno e rejeita SVG, PNG cru e caminho local',
        window.BuroProfilePhoto.safe(photo) === photo &&
        !window.BuroProfilePhoto.safe('data:image/png;base64,UE5H') &&
        !window.BuroProfilePhoto.safe('data:image/svg+xml;base64,PHN2Zz4=') &&
        !window.BuroProfilePhoto.safe('file:///usb/sala.jpg'));

    window.BuroUsb.readImage('inexistente', function () {}, function (error) { missingCode = error.code; });
    check('chave desconhecida falha sem aceitar caminho fornecido pelo chamador', missingCode === 'PHOTO_UNAVAILABLE');

    window.__fixture.mounted = false;
    await new Promise(function (resolve) { window.BuroUsb.refresh(resolve, resolve); });
    images = await listImages(window);
    check('remover o USB limpa a lista e invalida as referências transitórias',
        images.length === 0 && window.BuroUsb.imagePreviewUrl('usb-photo-0') === null);

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
