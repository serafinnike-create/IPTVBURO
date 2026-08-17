/* Catalogue visibility and parental PIN contracts. No clear-text PIN is persisted. */
var BuroGuard = (function () {
    'use strict';

    var ADULT_WORDS = {
        adulto: true, adult: true, xxx: true, porn: true, porno: true,
        pornografia: true, erotico: true, erotic: true, sexy: true,
        sex: true, '+18': true, '18+': true
    };

    function words(value) {
        return String(value || '').toLowerCase()
            .replace(/[àáâãäå]/g, 'a').replace(/[èéêë]/g, 'e')
            .replace(/[ìíîï]/g, 'i').replace(/[òóôõö]/g, 'o')
            .replace(/[ùúûü]/g, 'u').replace(/ç/g, 'c')
            .replace(/[^a-z0-9+]+/g, ' ').replace(/^\s+|\s+$/g, '')
            .split(/\s+/);
    }

    function looksAdult(name) {
        return words(name).some(function (word) {
            return Boolean(ADULT_WORDS[word] ||
                (word.length > 1 && word.charAt(word.length - 1) === 's' && ADULT_WORDS[word.substring(0, word.length - 1)]));
        });
    }

    function contains(values, value) {
        return Array.isArray(values) && values.indexOf(String(value)) >= 0;
    }

    function categoryVisible(category, preferences, isKids) {
        if (!category) { return true; }
        if (contains(preferences.hiddenCategoryIds, category.id)) { return false; }
        if (isKids && looksAdult(category.name)) { return false; }
        return true;
    }

    function requiresPin(category, preferences) {
        if (!category || !preferences.parentalPin) { return false; }
        if (contains(preferences.lockedCategoryIds, category.id)) { return true; }
        return Boolean(preferences.lockAdultCategories && looksAdult(category.name));
    }

    function validPin(value) { return /^\d{4}$/.test(String(value || '')); }

    function bytesToHex(buffer) {
        return Array.prototype.map.call(new Uint8Array(buffer), function (byte) {
            return ('0' + byte.toString(16)).slice(-2);
        }).join('');
    }

    function randomSalt() {
        var bytes = new Uint8Array(16);
        if (!window.crypto || !window.crypto.getRandomValues) { throw new Error('SECURE_RANDOM_UNAVAILABLE'); }
        window.crypto.getRandomValues(bytes);
        return Array.prototype.map.call(bytes, function (byte) {
            return ('0' + byte.toString(16)).slice(-2);
        }).join('');
    }

    function hash(pin, salt, success, failure) {
        var encoded;
        if (!window.crypto || !window.crypto.subtle || !window.TextEncoder) {
            failure(new Error('WEB_CRYPTO_UNAVAILABLE')); return;
        }
        encoded = new window.TextEncoder().encode(String(salt) + String(pin));
        window.crypto.subtle.digest('SHA-256', encoded).then(function (buffer) {
            success(bytesToHex(buffer));
        }).catch(function () { failure(new Error('PIN_HASH_FAILED')); });
    }

    function createPin(value, success, failure) {
        var salt;
        if (!validPin(value)) { failure(new Error('PIN_FORMAT_INVALID')); return; }
        try { salt = randomSalt(); } catch (error) { failure(error); return; }
        hash(value, salt, function (digest) { success({ salt: salt, hash: digest }); }, failure);
    }

    function matches(value, record, success, failure) {
        if (!validPin(value) || !record || !record.salt || !record.hash) { success(false); return; }
        hash(value, record.salt, function (digest) { success(digest === record.hash); }, failure);
    }

    function toggle(values, value) {
        var copy = Array.isArray(values) ? values.slice() : [];
        var index = copy.indexOf(String(value));
        if (index >= 0) { copy.splice(index, 1); }
        else { copy.push(String(value)); }
        return copy;
    }

    return {
        looksAdult: looksAdult,
        categoryVisible: categoryVisible,
        requiresPin: requiresPin,
        validPin: validPin,
        createPin: createPin,
        matches: matches,
        toggle: toggle
    };
}());
