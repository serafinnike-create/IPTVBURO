package com.lucasserafin94.iptvburo.desktop.license

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The QR code on the activation screen.
 *
 * ## Why a library
 *
 * This was written by hand first, on the reasoning that turning a short URL into a grid of squares
 * is a small, well-specified job. It produced correct data codewords and correct Reed-Solomon
 * correction bytes — both verified against an independent implementation — and codes that no decoder
 * could read, because of a subtler error in how the mask was applied.
 *
 * That is the failure mode worth avoiding here. A wrong QR code looks exactly like a right one. It
 * passes every structural check, and the customer discovers the problem by standing in front of
 * their laptop pointing a phone at a screen that never responds — at the moment they were trying to
 * pay.
 *
 * ZXing is read by an enormous number of devices every day. `core` alone, without the `javase`
 * artifact, is the encoder and nothing else.
 *
 * ## Error correction
 *
 * Level M recovers from roughly 15% damage. That margin is what makes a code readable when it is
 * photographed at an angle, off a glossy screen, in a room with a lamp behind it — which is the
 * situation this exists for, rather than an ideal one.
 */
object QrCode {
    /**
     * Encodes [text] into a square grid where true means a dark module.
     *
     * The quiet zone is left to the renderer: how much blank space surrounds the code is a layout
     * decision, and baking a margin into the grid makes the modules smaller for no benefit.
     */
    fun encode(text: String): Array<BooleanArray> {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // Zero, because the caller draws its own padding. ZXing's default of 4 modules would be
            // added inside the returned matrix and then padded again by the layout.
            EncodeHintType.MARGIN to 0,
        )

        // 0x0 asks ZXing to pick the smallest version that fits, rather than scaling a fixed size.
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)

        return Array(matrix.height) { row ->
            BooleanArray(matrix.width) { column -> matrix.get(column, row) }
        }
    }
}
