/*
  Prepara uma foto de perfil para o armazenamento privado do aplicativo.

  A imagem de origem existe somente durante a importacao do USB. O resultado
  persistido e um JPEG pequeno em data URL: nenhum caminho do pendrive e
  mantido e SVG nunca e aceito como imagem de perfil.
*/
var BuroProfilePhoto = (function () {
    'use strict';

    var MAX_SOURCE_LENGTH = 7 * 1024 * 1024;
    var MAX_STORED_LENGTH = 220000;
    var SOURCE_PATTERN = /^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/;
    var STORED_PATTERN = /^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/;

    function safe(value) {
        var data = String(value || '');
        return data.length <= MAX_STORED_LENGTH && STORED_PATTERN.test(data) ? data : null;
    }

    function resize(source, success, failure) {
        var data = String(source || '');
        var image;
        if (!SOURCE_PATTERN.test(data) || data.length > MAX_SOURCE_LENGTH) {
            failure({ code: 'PHOTO_INVALID' });
            return;
        }
        image = new Image();
        image.onload = function () {
            var sourceWidth = Number(image.naturalWidth || image.width) || 0;
            var sourceHeight = Number(image.naturalHeight || image.height) || 0;
            var sourceSize = Math.min(sourceWidth, sourceHeight);
            var sourceX = Math.max(0, Math.floor((sourceWidth - sourceSize) / 2));
            var sourceY = Math.max(0, Math.floor((sourceHeight - sourceSize) / 2));
            var attempts = [
                { size: 320, quality: 0.84 },
                { size: 256, quality: 0.74 },
                { size: 192, quality: 0.64 }
            ];
            var canvas;
            var context;
            var result = null;
            var index;
            if (!sourceSize) { failure({ code: 'PHOTO_INVALID' }); return; }
            try {
                canvas = document.createElement('canvas');
                context = canvas.getContext('2d');
                if (!context) { failure({ code: 'PHOTO_UNSUPPORTED' }); return; }
                for (index = 0; index < attempts.length; index += 1) {
                    canvas.width = attempts[index].size;
                    canvas.height = attempts[index].size;
                    context.clearRect(0, 0, canvas.width, canvas.height);
                    context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize,
                        0, 0, canvas.width, canvas.height);
                    result = canvas.toDataURL('image/jpeg', attempts[index].quality);
                    if (safe(result)) { success(result); return; }
                }
            } catch (error) {
                failure({ code: 'PHOTO_PROCESS_FAILED' });
                return;
            }
            failure({ code: 'PHOTO_TOO_LARGE' });
        };
        image.onerror = function () { failure({ code: 'PHOTO_INVALID' }); };
        image.src = data;
    }

    return { safe: safe, resize: resize };
}());
