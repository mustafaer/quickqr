package net.mustafaer.quickqr.utils

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

/**
 * Outcome of a QR encode. Failure is a value rather than a null so the screen can
 * tell the user *why* nothing appeared instead of silently showing the empty
 * placeholder.
 */
sealed interface QrResult {
    data class Success(val bitmap: Bitmap) : QrResult
    data object Empty : QrResult
    data class TooLong(val byteCount: Int, val maxBytes: Int) : QrResult
    data object Failed : QrResult
}

object QrGenerator {

    /**
     * Capacity of a version-40 QR in byte mode at error-correction level H.
     * Anything longer cannot be encoded at all, so it is worth rejecting before
     * ZXing spends time discovering the same thing.
     */
    const val MAX_BYTES = 1273

    fun generate(text: String, size: Int = 512): QrResult {
        if (text.isBlank()) return QrResult.Empty

        val byteCount = text.toByteArray(Charsets.UTF_8).size
        if (byteCount > MAX_BYTES) return QrResult.TooLong(byteCount, MAX_BYTES)

        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 2)
            }

            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            // Always pure black on pure white: a themed QR is a QR that some
            // scanners refuse to read.
            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            QrResult.Success(bitmap)
        } catch (_: Exception) {
            QrResult.Failed
        }
    }
}
