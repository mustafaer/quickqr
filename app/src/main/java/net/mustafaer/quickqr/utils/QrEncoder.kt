package net.mustafaer.quickqr.utils

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode

/**
 * An encoded QR symbol, independent of how it is drawn: [size]×[size] modules,
 * row-major, `true` for a dark module. The screen draws it as vector rectangles
 * and the gallery export rasterises it at whole-pixel module sizes, so neither
 * ever scales a bitmap and blurs the module edges.
 */
class QrMatrix(
    val size: Int,
    private val modules: BooleanArray,
    /** QR version, 1–40. The symbol is `17 + 4 × version` modules wide. */
    val version: Int,
    val errorCorrection: ErrorCorrection
) {
    init {
        require(modules.size == size * size) { "Expected ${size * size} modules, got ${modules.size}" }
    }

    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/** How much of the symbol can be damaged and still read. */
enum class ErrorCorrection(internal val zxing: ErrorCorrectionLevel) {
    L(ErrorCorrectionLevel.L),
    M(ErrorCorrectionLevel.M),
    Q(ErrorCorrectionLevel.Q),
    H(ErrorCorrectionLevel.H)
}

/**
 * Outcome of a QR encode. Failure is a value rather than a null so the screen can
 * tell the user *why* nothing appeared instead of silently showing the empty
 * placeholder.
 */
sealed interface QrResult {
    data class Success(val matrix: QrMatrix) : QrResult
    data object Empty : QrResult
    data class TooLong(val byteCount: Int, val maxBytes: Int) : QrResult
    data object Failed : QrResult
}

object QrEncoder {

    /**
     * Byte-mode capacity of a version-40 symbol at level L. Text that needs UTF-8
     * also carries a 12-bit ECI header, which costs it one more byte.
     */
    const val MAX_ASCII_BYTES = 2953
    const val MAX_UTF8_BYTES = 2952

    /**
     * Largest version the adaptive search prefers: 57×57 modules. Beyond it, a
     * symbol printed or shown at a typical size has modules small enough that
     * phone cameras start to struggle, so trading error correction for size wins.
     */
    internal const val COMFORTABLE_VERSION = 10

    private val PREFERRED = listOf(ErrorCorrection.H, ErrorCorrection.Q, ErrorCorrection.M)

    /**
     * Encodes [text] with the strongest error correction that still fits in a
     * [COMFORTABLE_VERSION] symbol. Content too big for that falls back to M at
     * whatever size it needs, and only then to L — so a short link gets the full
     * 30% damage tolerance, and a long vCard is still encodable at all.
     *
     * Pure ASCII is encoded without a character-set hint. ISO-8859-1, the QR
     * default, is a superset of ASCII, and leaving the hint out avoids an ECI
     * header that some older readers mis-handle.
     */
    fun encode(text: String): QrResult {
        if (text.isBlank()) return QrResult.Empty

        val isAscii = text.all { it.code < 0x80 }
        val byteCount = if (isAscii) text.length else text.toByteArray(Charsets.UTF_8).size
        val maxBytes = if (isAscii) MAX_ASCII_BYTES else MAX_UTF8_BYTES
        if (byteCount > maxBytes) return QrResult.TooLong(byteCount, maxBytes)

        val hints: Map<EncodeHintType, Any> =
            if (isAscii) emptyMap() else mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")

        return try {
            val code = PREFERRED.firstNotNullOfOrNull { level ->
                encodeOrNull(text, level, hints)?.takeIf { it.version.versionNumber <= COMFORTABLE_VERSION }
            }
                ?: encodeOrNull(text, ErrorCorrection.M, hints)
                ?: encodeOrNull(text, ErrorCorrection.L, hints)
                ?: return QrResult.TooLong(byteCount, maxBytes)
            QrResult.Success(code.toMatrix())
        } catch (_: RuntimeException) {
            QrResult.Failed
        }
    }

    /** ZXing reports "does not fit at this level" as a checked [WriterException]. */
    private fun encodeOrNull(
        text: String,
        level: ErrorCorrection,
        hints: Map<EncodeHintType, Any>
    ): QRCode? =
        try {
            Encoder.encode(text, level.zxing, hints)
        } catch (_: WriterException) {
            null
        }

    private fun QRCode.toMatrix(): QrMatrix {
        val source = matrix
        val size = source.width
        val modules = BooleanArray(size * size)
        for (y in 0 until size) {
            val row = y * size
            for (x in 0 until size) {
                modules[row + x] = source[x, y].toInt() == 1
            }
        }
        val level = ErrorCorrection.entries.first { it.zxing == ecLevel }
        return QrMatrix(size, modules, version.versionNumber, level)
    }
}
