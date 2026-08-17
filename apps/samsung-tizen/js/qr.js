/* QR byte-mode, versões 1–10, correção M. Port direto do domínio Kotlin compartilhado. */
var BuroQr = (function () {
    'use strict';

    var DATA_CODEWORDS = [0, 16, 28, 44, 64, 86, 108, 124, 154, 182, 216];
    var EC_CODEWORDS = [0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26];
    var EC_BLOCKS = [0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5];
    var EXP = new Array(256);
    var LOG = new Array(256);

    (function initialiseFields() {
        var value = 1;
        var index;
        for (index = 0; index < 255; index += 1) {
            EXP[index] = value; LOG[value] = index;
            value <<= 1;
            if (value & 0x100) { value ^= 0x11D; }
        }
        EXP[255] = EXP[0];
    }());

    function bytes(text) {
        /* O link compartilhado é ASCII após encodeURIComponent. Recusar, em vez de codificar
           silenciosamente outro conjunto de bytes, mantém a matriz determinística. */
        var value = String(text == null ? '' : text);
        var output = [];
        var index;
        for (index = 0; index < value.length; index += 1) {
            if (value.charCodeAt(index) > 255) { return null; }
            output.push(value.charCodeAt(index));
        }
        return output;
    }

    function capacity(version) {
        return Math.floor((DATA_CODEWORDS[version] * 8 - (4 + (version < 10 ? 8 : 16))) / 8);
    }

    function bitBuffer() {
        var data = [];
        return {
            append: function (value, count) {
                var index;
                for (index = count - 1; index >= 0; index -= 1) { data.push(((value >>> index) & 1) === 1); }
            },
            length: function () { return data.length; },
            toBytes: function () {
                var output = new Array(Math.floor(data.length / 8));
                var index;
                for (index = 0; index < output.length; index += 1) { output[index] = 0; }
                data.forEach(function (bit, position) {
                    if (bit && Math.floor(position / 8) < output.length) {
                        output[Math.floor(position / 8)] |= 1 << (7 - position % 8);
                    }
                });
                return output;
            }
        };
    }

    function multiply(left, right) {
        return left === 0 || right === 0 ? 0 : EXP[(LOG[left] + LOG[right]) % 255];
    }

    function generatorPolynomial(degree) {
        var result = [1];
        var power;
        var index;
        for (power = 0; power < degree; power += 1) {
            var next = new Array(result.length + 1);
            for (index = 0; index < next.length; index += 1) { next[index] = 0; }
            for (index = 0; index < result.length; index += 1) {
                next[index] ^= multiply(result[index], 1);
                next[index + 1] ^= multiply(result[index], EXP[power]);
            }
            result = next;
        }
        return result;
    }

    function errorCorrection(data, count) {
        var generator = generatorPolynomial(count);
        var result = new Array(count);
        var row;
        var index;
        for (index = 0; index < count; index += 1) { result[index] = 0; }
        data.forEach(function (value) {
            var factor = value ^ result[0];
            for (row = 0; row < result.length - 1; row += 1) { result[row] = result[row + 1]; }
            result[result.length - 1] = 0;
            for (row = 0; row < result.length; row += 1) {
                result[row] ^= multiply(generator[row + 1], factor);
            }
        });
        return result;
    }

    function interleave(data, version) {
        var blockCount = EC_BLOCKS[version];
        var ecPerBlock = EC_CODEWORDS[version];
        var shortLength = Math.floor(data.length / blockCount);
        var longBlocks = data.length % blockCount;
        var dataBlocks = [];
        var ecBlocks = [];
        var offset = 0;
        var index;
        var block;
        var length;
        var maximum = 0;
        var output = [];
        for (index = 0; index < blockCount; index += 1) {
            length = shortLength + (index >= blockCount - longBlocks ? 1 : 0);
            block = data.slice(offset, offset + length); offset += length;
            dataBlocks.push(block); ecBlocks.push(errorCorrection(block, ecPerBlock));
            maximum = Math.max(maximum, block.length);
        }
        for (index = 0; index < maximum; index += 1) {
            dataBlocks.forEach(function (row) { if (index < row.length) { output.push(row[index]); } });
        }
        for (index = 0; index < ecPerBlock; index += 1) {
            ecBlocks.forEach(function (row) { output.push(row[index]); });
        }
        return output;
    }

    function matrix(size) {
        var bits = new Array(size * size);
        var index;
        for (index = 0; index < bits.length; index += 1) { bits[index] = false; }
        return {
            size: size,
            get: function (x, y) { return bits[y * size + x]; },
            set: function (x, y, value) { bits[y * size + x] = Boolean(value); }
        };
    }

    function alignmentCentres(version) {
        return {
            2: [6, 18], 3: [6, 22], 4: [6, 26], 5: [6, 30], 6: [6, 34],
            7: [6, 22, 38], 8: [6, 24, 42], 9: [6, 26, 46], 10: [6, 28, 50]
        }[version] || [6];
    }

    function isFunctionModule(size, version, x, y) {
        var centres;
        var cx;
        var cy;
        var left;
        var top;
        if (x < 9 && y < 9) { return true; }
        if (x >= size - 8 && y < 9) { return true; }
        if (x < 9 && y >= size - 8) { return true; }
        if (x === 6 || y === 6) { return true; }
        if (version >= 7 && ((x >= size - 11 && y < 6) || (y >= size - 11 && x < 6))) { return true; }
        if (version >= 2) {
            centres = alignmentCentres(version);
            for (left = 0; left < centres.length; left += 1) {
                cx = centres[left];
                for (top = 0; top < centres.length; top += 1) {
                    cy = centres[top];
                    if (cx <= 8 && cy <= 8) { continue; }
                    if (cx >= size - 9 && cy <= 8) { continue; }
                    if (cx <= 8 && cy >= size - 9) { continue; }
                    if (x >= cx - 2 && x <= cx + 2 && y >= cy - 2 && y <= cy + 2) { return true; }
                }
            }
        }
        return false;
    }

    function drawFunctions(output, version) {
        var size = output.size;
        function finder(originX, originY) {
            var dx;
            var dy;
            var x;
            var y;
            var ring;
            for (dy = -1; dy <= 7; dy += 1) {
                for (dx = -1; dx <= 7; dx += 1) {
                    x = originX + dx; y = originY + dy;
                    if (x < 0 || x >= size || y < 0 || y >= size) { continue; }
                    ring = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
                    output.set(x, y, ring !== 2 && ring <= 3);
                }
            }
        }
        var centres;
        var cx;
        var cy;
        var left;
        var top;
        var dx;
        var dy;
        var index;
        finder(0, 0); finder(size - 7, 0); finder(0, size - 7);
        for (index = 8; index < size - 8; index += 1) {
            output.set(index, 6, index % 2 === 0); output.set(6, index, index % 2 === 0);
        }
        if (version >= 2) {
            centres = alignmentCentres(version);
            for (left = 0; left < centres.length; left += 1) {
                cx = centres[left];
                for (top = 0; top < centres.length; top += 1) {
                    cy = centres[top];
                    if (cx <= 8 && cy <= 8) { continue; }
                    if (cx >= size - 9 && cy <= 8) { continue; }
                    if (cx <= 8 && cy >= size - 9) { continue; }
                    for (dy = -2; dy <= 2; dy += 1) {
                        for (dx = -2; dx <= 2; dx += 1) {
                            output.set(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
                        }
                    }
                }
            }
        }
        output.set(8, size - 8, true);
    }

    function drawCodewords(output, data, version) {
        var size = output.size;
        var bitIndex = 0;
        var column = size - 1;
        var step;
        var offset;
        var x;
        var y;
        var upward;
        var value;
        while (column >= 1) {
            if (column === 6) { column = 5; }
            for (step = 0; step < size; step += 1) {
                for (offset = 0; offset < 2; offset += 1) {
                    x = column - offset; upward = ((column + 1) & 2) === 0;
                    y = upward ? size - 1 - step : step;
                    if (isFunctionModule(size, version, x, y)) { continue; }
                    if (bitIndex < data.length * 8) {
                        value = data[Math.floor(bitIndex / 8)];
                        output.set(x, y, ((value >>> (7 - bitIndex % 8)) & 1) === 1); bitIndex += 1;
                    }
                }
            }
            column -= 2;
        }
    }

    function drawVersion(output, version) {
        var remainder;
        var bits;
        var dark;
        var index;
        var bit;
        if (version < 7) { return; }
        remainder = version << 12;
        for (bit = 17; bit >= 12; bit -= 1) {
            if (((remainder >>> bit) & 1) === 1) { remainder ^= 0x1F25 << (bit - 12); }
        }
        bits = (version << 12) | remainder;
        for (index = 0; index < 18; index += 1) {
            dark = ((bits >>> index) & 1) === 1;
            output.set(output.size - 11 + index % 3, Math.floor(index / 3), dark);
            output.set(Math.floor(index / 3), output.size - 11 + index % 3, dark);
        }
    }

    function applyMask(output, version) {
        var size = output.size;
        var format = 0x5412;
        var x;
        var y;
        var index;
        for (y = 0; y < size; y += 1) {
            for (x = 0; x < size; x += 1) {
                if (!isFunctionModule(size, version, x, y) && (x + y) % 2 === 0) {
                    output.set(x, y, !output.get(x, y));
                }
            }
        }
        for (index = 0; index <= 5; index += 1) { output.set(8, index, ((format >>> index) & 1) === 1); }
        output.set(8, 7, ((format >>> 6) & 1) === 1);
        output.set(8, 8, ((format >>> 7) & 1) === 1);
        output.set(7, 8, ((format >>> 8) & 1) === 1);
        for (index = 9; index <= 14; index += 1) { output.set(14 - index, 8, ((format >>> index) & 1) === 1); }
        for (index = 0; index <= 7; index += 1) { output.set(size - 1 - index, 8, ((format >>> index) & 1) === 1); }
        for (index = 8; index <= 14; index += 1) { output.set(8, size - 15 + index, ((format >>> index) & 1) === 1); }
    }

    function encode(text) {
        var data = bytes(text);
        var version = null;
        var buffer;
        var total;
        var alternate = true;
        var output;
        var candidate;
        if (!data) { return null; }
        for (candidate = 1; candidate <= 10; candidate += 1) {
            if (data.length <= capacity(candidate)) { version = candidate; break; }
        }
        if (!version) { return null; }
        total = DATA_CODEWORDS[version]; buffer = bitBuffer();
        buffer.append(4, 4); buffer.append(data.length, version < 10 ? 8 : 16);
        data.forEach(function (value) { buffer.append(value, 8); });
        candidate = Math.min(4, total * 8 - buffer.length());
        while (candidate > 0) { buffer.append(0, 1); candidate -= 1; }
        while (buffer.length() % 8 !== 0) { buffer.append(0, 1); }
        while (buffer.length() / 8 < total) {
            buffer.append(alternate ? 0xEC : 0x11, 8); alternate = !alternate;
        }
        output = matrix(version * 4 + 17);
        drawFunctions(output, version); drawVersion(output, version);
        drawCodewords(output, interleave(buffer.toBytes(), version), version); applyMask(output, version);
        return output;
    }

    function svg(matrixValue) {
        var quiet = 4;
        var path = [];
        var x;
        var y;
        if (!matrixValue) { return null; }
        for (y = 0; y < matrixValue.size; y += 1) {
            for (x = 0; x < matrixValue.size; x += 1) {
                if (matrixValue.get(x, y)) { path.push('M' + (x + quiet) + ' ' + (y + quiet) + 'h1v1h-1z'); }
            }
        }
        return '<svg class="share-qr" viewBox="0 0 ' + (matrixValue.size + quiet * 2) + ' ' +
            (matrixValue.size + quiet * 2) + '" role="img" aria-label="QR"><rect width="100%" height="100%" fill="#fff"/>' +
            '<path d="' + path.join('') + '" fill="#090a0d"/></svg>';
    }

    return { encode: encode, svg: svg };
}());
