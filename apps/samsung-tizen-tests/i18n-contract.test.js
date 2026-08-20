/*
  O shell Samsung oferece a mesma interface em PT-BR, EN, DE, IT e ES.

  BuroI18n.t() tem fallback para português. Esse fallback é útil em produção,
  mas também esconderia de um teste superficial uma chave ausente em alemão
  ou italiano. Este contrato inspeciona as tabelas reais antes do fallback e
  protege chaves, parâmetros, codificação e referências literais do app.
*/
'use strict';

var fs = require('fs');
var path = require('path');
var vm = require('vm');

var APP_DIR = path.resolve(__dirname, '..', 'samsung-tizen');
var I18N_FILE = path.join(APP_DIR, 'js', 'i18n.js');
var passed = 0;
var failures = [];

function check(label, condition, detail) {
    if (condition) {
        passed += 1;
        process.stdout.write('  ok    ' + label + '\n');
        return;
    }
    failures.push(label + (detail ? ' — ' + detail : ''));
    process.stdout.write('  FALHA ' + label + (detail ? ' — ' + detail : '') + '\n');
}

function placeholders(value) {
    var found = [];
    String(value).replace(/\{([^}]+)\}/g, function (_, name) {
        found.push(name);
        return _;
    });
    return found.sort().join('|');
}

function loadContract() {
    var source = fs.readFileSync(I18N_FILE, 'utf8');
    var marker = 'return { setLanguage: setLanguage, t: t, language: language, supported: supported, normalize: normalize };';
    if (source.indexOf(marker) < 0) {
        throw new Error('Não foi possível instrumentar a tabela i18n de produção.');
    }
    source = source.replace(marker, '__tables = tables; ' + marker);
    var context = {
        document: { documentElement: { lang: '', setAttribute: function (name, value) { this[name] = value; } } },
        __tables: null
    };
    vm.createContext(context);
    vm.runInContext(source, context, { filename: I18N_FILE });
    return { api: context.BuroI18n, tables: context.__tables, source: source };
}

function literalTranslationReferences() {
    var references = {};
    fs.readdirSync(path.join(APP_DIR, 'js')).filter(function (name) {
        return /\.js$/.test(name) && name !== 'i18n.js';
    }).forEach(function (name) {
        var source = fs.readFileSync(path.join(APP_DIR, 'js', name), 'utf8');
        var pattern = /(?:\bBuroI18n\.)?\bt\(\s*['"]([^'"]+)['"]\s*\)/g;
        var match = pattern.exec(source);
        while (match) {
            references[match[1]] = true;
            match = pattern.exec(source);
        }
    });
    return Object.keys(references).sort();
}

function run() {
    var contract = loadContract();
    var expectedLanguages = ['pt-BR', 'en', 'de', 'it', 'es'];
    var base = contract.tables['pt-BR'];
    var baseKeys = Object.keys(base).sort();

    process.stdout.write('Contrato multilíngue Samsung\n');
    check('a API anuncia exatamente os cinco idiomas suportados',
        JSON.stringify(contract.api.supported()) === JSON.stringify(expectedLanguages));
    check('a tabela de referência possui um catálogo substancial de mensagens', baseKeys.length >= 500,
        String(baseKeys.length));

    expectedLanguages.forEach(function (language) {
        var table = contract.tables[language] || {};
        var keys = Object.keys(table).sort();
        var missing = baseKeys.filter(function (key) { return !Object.prototype.hasOwnProperty.call(table, key); });
        var extra = keys.filter(function (key) { return !Object.prototype.hasOwnProperty.call(base, key); });
        var empty = keys.filter(function (key) { return !String(table[key]).trim(); });
        var mismatched = baseKeys.filter(function (key) {
            return Object.prototype.hasOwnProperty.call(table, key) &&
                placeholders(base[key]) !== placeholders(table[key]);
        });
        var encodingDamage = keys.filter(function (key) {
            /* Âmbar e Ãland são texto válido; mojibake usa um segundo byte
               reinterpretado como caractere Latin-1 (U+0080..U+00BF). */
            return /\uFFFD|Ã[\u0080-\u00BF]|Â[\u0080-\u00BF]|â[\u0080-\u00BF]|ðŸ/.test(String(table[key]));
        });

        check(language + ' possui as mesmas ' + baseKeys.length + ' chaves',
            missing.length === 0 && extra.length === 0,
            'faltando: ' + missing.join(', ') + '; extras: ' + extra.join(', '));
        check(language + ' preserva todos os parâmetros de interpolação',
            mismatched.length === 0, mismatched.join(', '));
        check(language + ' não contém mensagem vazia nem texto com codificação quebrada',
            empty.length === 0 && encodingDamage.length === 0,
            empty.concat(encodingDamage).join(', '));
    });

    var references = literalTranslationReferences();
    var unknown = references.filter(function (key) {
        return !Object.prototype.hasOwnProperty.call(base, key);
    });
    check('todas as chaves literais usadas pelos módulos existem na tabela',
        references.length > 0 && unknown.length === 0, unknown.join(', '));

    contract.api.setLanguage('de-DE');
    check('idioma regional é normalizado e refletido no documento',
        contract.api.language() === 'de' && contract.api.t('settings') === contract.tables.de.settings);
    contract.api.setLanguage('fr-FR');
    check('idioma desconhecido volta com segurança para PT-BR',
        contract.api.language() === 'pt-BR' && contract.api.t('settings') === base.settings);
    check('chave desconhecida permanece diagnosticável em vez de virar vazio',
        contract.api.t('__missing_translation__') === '__missing_translation__');

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
