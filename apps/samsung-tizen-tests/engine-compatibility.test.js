/*
  O app tem de rodar no motor mais antigo que ainda queremos suportar.

  As TVs Samsung em uso hoje vão do Chrome 47 (2015) ao M130. Um `=>` ou um
  `gap:` não falham em lugar nenhum durante o desenvolvimento — o simulador é
  moderno — e depois a TV do cliente abre uma tela preta sem nenhuma mensagem.

  Estas regressões já voltaram três vezes ao repositório, sempre em código
  novo escrito por quem não tinha esse limite em mente. Por isso são um teste
  e não uma revisão manual.
*/
'use strict';

var fs = require('fs');
var path = require('path');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var passed = 0;
var failures = [];

function check(label, condition, detail) {
    if (condition) { passed += 1; process.stdout.write('  ok    ' + label + '\n'); }
    else {
        failures.push(label + (detail ? ' — ' + detail : ''));
        process.stdout.write('  FALHA ' + label + (detail ? ' — ' + detail : '') + '\n');
    }
}

/*
  Comentários e literais de texto saem antes da busca.

  Sem isso, `"would let the app store data"` acusa a palavra-chave `let`, e um
  falso positivo desses ensina a ignorar o teste.
*/
function codeOnly(source) {
    return source
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/^\s*\/\/.*$/gm, '')
        .replace(/'(?:[^'\\]|\\.)*'/g, "''")
        .replace(/"(?:[^"\\]|\\.)*"/g, '""');
}

function scriptFiles() {
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    var pattern = /<script src="([^"]+)"><\/script>/g;
    var files = [];
    var match = pattern.exec(html);
    while (match) { files.push(match[1]); match = pattern.exec(html); }
    return files;
}

function run() {
    /* Cada item traz a versão do Chromium que o introduziu, para quem ler a
       falha saber por que aquilo está proibido. */
    var jsBans = [
        { pattern: /=>/, name: 'arrow function', since: 'Chrome 45' },
        { pattern: /\bconst\s/, name: 'const', since: 'Chrome 49' },
        { pattern: /\blet\s/, name: 'let', since: 'Chrome 49' },
        { pattern: /\?\./, name: 'optional chaining', since: 'Chrome 80' },
        { pattern: /\?\?/, name: 'nullish coalescing', since: 'Chrome 80' },
        { pattern: /\bclass\s+[A-Z]/, name: 'class', since: 'Chrome 49' },
        { pattern: /\basync\s/, name: 'async', since: 'Chrome 55' },
        { pattern: /\bawait\s/, name: 'await', since: 'Chrome 55' },
        { pattern: /`/, name: 'template literal', since: 'Chrome 41' },
        { pattern: /\.\.\.[a-zA-Z[{]/, name: 'spread', since: 'Chrome 46' }
    ];
    var cssBans = [
        { pattern: /(^|[^-])\binset\s*:/, name: 'inset', since: 'Chromium 87' },
        { pattern: /(^|[^-])\bgap\s*:/, name: 'gap', since: 'Chromium 84 (flex) / 66 (grid)' },
        { pattern: /\bclamp\s*\(/, name: 'clamp()', since: 'Chromium 79' },
        { pattern: /:is\s*\(/, name: ':is()', since: 'Chromium 88' },
        { pattern: /:where\s*\(/, name: ':where()', since: 'Chromium 88' },
        { pattern: /\baspect-ratio\s*:/, name: 'aspect-ratio', since: 'Chromium 88' }
    ];

    process.stdout.write('JavaScript aceito pelas TVs antigas\n');
    var files = scriptFiles();
    check('a lista de scripts foi lida do index.html', files.length > 0);

    files.forEach(function (file) {
        var source = codeOnly(fs.readFileSync(path.join(APP_DIR, file), 'utf8'));
        var found = jsBans.filter(function (ban) { return ban.pattern.test(source); });
        check(file + ' é ES5',
            found.length === 0,
            found.map(function (ban) { return ban.name + ' (' + ban.since + ')'; }).join(', '));
    });

    process.stdout.write('CSS aceito pelas TVs antigas\n');
    var css = fs.readFileSync(path.join(APP_DIR, 'css', 'style.css'), 'utf8')
        .replace(/\/\*[\s\S]*?\*\//g, '');
    cssBans.forEach(function (ban) {
        var lines = css.split('\n').reduce(function (hits, line, index) {
            if (ban.pattern.test(line)) { hits.push(index + 1); }
            return hits;
        }, []);
        check('style.css não usa ' + ban.name,
            lines.length === 0,
            lines.length ? 'linhas ' + lines.join(', ') + ' — ' + ban.since : '');
    });

    /*
      A área lógica da TV é fixa em 1920x1080. Um viewport responsivo faria o
      layout ser calculado para outra largura e sair cortado na tela.
    */
    process.stdout.write('Contrato da tela\n');
    var html = fs.readFileSync(path.join(APP_DIR, 'index.html'), 'utf8');
    check('o viewport declara a área lógica fixa da TV',
        /content="width=1920,\s*height=1080"/.test(html));
    check('o body permanece transparente para a camada de vídeo da AVPlay',
        /background:\s*transparent/.test(
            fs.readFileSync(path.join(APP_DIR, 'css', 'style.css'), 'utf8')
        ));

    process.stdout.write('\n');
    if (failures.length) {
        process.stdout.write(failures.length + ' falha(s); ' + passed + ' passaram\n');
        failures.forEach(function (failure) { process.stdout.write(' - ' + failure + '\n'); });
        process.exitCode = 1;
        return;
    }
    process.stdout.write('Todos os ' + passed + ' testes passaram.\n');
}

run();
