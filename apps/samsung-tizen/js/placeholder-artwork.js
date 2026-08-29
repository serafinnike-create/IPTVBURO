/* Detects one provider image reused as the cover for many different titles. */
var BuroPlaceholderArtwork = (function () {
    'use strict';

    /* Same contract as PlaceholderArtwork.SHARED_COVER_THRESHOLD in Kotlin. */
    var SHARED_COVER_THRESHOLD = 25;

    function clean(value) {
        return String(value == null ? '' : value).replace(/^\s+|\s+$/g, '');
    }

    /* Prefixing keys keeps values such as "__proto__" ordinary data. */
    function key(value) { return '$' + value; }

    function create() {
        return { counts: Object.create(null), values: Object.create(null), placeholders: Object.create(null) };
    }

    function add(scan, value) {
        var url = clean(value);
        var id;
        var count;
        if (!scan || !url) { return scan; }
        id = key(url);
        if (scan.placeholders[id]) { return scan; }
        count = Number(scan.counts[id]) || 0;
        count += 1;
        if (count >= SHARED_COVER_THRESHOLD) {
            scan.placeholders[id] = url;
            delete scan.counts[id];
            delete scan.values[id];
        } else {
            scan.counts[id] = count;
            scan.values[id] = url;
        }
        return scan;
    }

    function finish(scan) {
        if (!scan || !scan.placeholders) { return []; }
        return Object.keys(scan.placeholders).map(function (id) { return scan.placeholders[id]; });
    }

    function detect(values) {
        var scan = create();
        (values || []).forEach(function (value) { add(scan, value); });
        return finish(scan);
    }

    return {
        SHARED_COVER_THRESHOLD: SHARED_COVER_THRESHOLD,
        create: create,
        add: add,
        finish: finish,
        detect: detect
    };
}());
