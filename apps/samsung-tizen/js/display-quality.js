/* Qualidade visual adaptativa sem alterar a superficie logica 1920x1080. */
var BuroDisplayQuality = (function () {
    'use strict';
    var state = { tier: 'fhd', scale: 1, width: 1920, height: 1080, source: 'logical' };

    function finitePositive(value) {
        value = Number(value);
        return isFinite(value) && value > 0 ? value : 0;
    }
    function snapshot() {
        return { tier: state.tier, scale: state.scale, width: state.width,
            height: state.height, source: state.source };
    }
    function apply(width, height, source) {
        var largest = Math.max(finitePositive(width), finitePositive(height));
        var ratio = largest >= 7000 ? 4 : (largest >= 3800 ? 2 : 1);
        state = { tier: ratio >= 4 ? '8k' : (ratio >= 2 ? 'uhd' : 'fhd'), scale: ratio,
            width: finitePositive(width) || 1920, height: finitePositive(height) || 1080,
            source: source || 'logical' };
        try { document.documentElement.setAttribute('data-display-quality', state.tier); }
        catch (ignoredDocument) {}
        return snapshot();
    }
    function fallback() {
        var ratio = 1; var width = 1920; var height = 1080;
        try {
            ratio = finitePositive(window.devicePixelRatio) || 1;
            width = finitePositive(window.screen && window.screen.width) || 1920;
            height = finitePositive(window.screen && window.screen.height) || 1080;
        } catch (ignoredScreen) {}
        return apply(width * ratio, height * ratio, ratio > 1 ? 'pixel-ratio' : 'screen');
    }
    function read(property, success, failure) {
        try {
            if (!window.tizen || !tizen.systeminfo ||
                    typeof tizen.systeminfo.getPropertyValue !== 'function') { failure(); return; }
            tizen.systeminfo.getPropertyValue(property, success, failure);
        } catch (ignoredSystemInfo) { failure(); }
    }
    function init(done) {
        fallback();
        read('PANEL', function (panel) {
            if (!finitePositive(panel && panel.panelWidth) ||
                    !finitePositive(panel && panel.panelHeight)) {
                readDisplay(done); return;
            }
            var value = apply(panel && panel.panelWidth, panel && panel.panelHeight, 'panel');
            if (done) { done(value); }
        }, function () { readDisplay(done); });
        return snapshot();
    }
    function readDisplay(done) {
        read('DISPLAY', function (display) {
            if (!finitePositive(display && display.resolutionWidth) ||
                    !finitePositive(display && display.resolutionHeight)) {
                if (done) { done(snapshot()); }
                return;
            }
            var value = apply(display.resolutionWidth, display.resolutionHeight, 'display');
            if (done) { done(value); }
        }, function () { if (done) { done(snapshot()); } });
    }
    function tmdbSize(size) {
        if (state.scale < 2) { return size; }
        return { w92: 'w185', w185: 'w342', w342: 'w780', w1280: 'original' }[size] || size;
    }
    init();
    return { init: init, info: snapshot, tmdbSize: tmdbSize };
}());
