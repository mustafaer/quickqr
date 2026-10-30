package net.mustafaer.quickqr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrEncoderTest {

    private fun encode(text: String): QrMatrix {
        val result = QrEncoder.encode(text)
        assertTrue("$result", result is QrResult.Success)
        return (result as QrResult.Success).matrix
    }

    @Test
    fun `blank input encodes nothing`() {
        assertEquals(QrResult.Empty, QrEncoder.encode(""))
        assertEquals(QrResult.Empty, QrEncoder.encode("   "))
    }

    @Test
    fun `a short link gets the strongest error correction`() {
        val matrix = encode("https://example.com/menu")
        assertEquals(ErrorCorrection.H, matrix.errorCorrection)
        assertEquals(17 + 4 * matrix.version, matrix.size)
    }

    /**
     * Forcing level H made a 60-byte URL a version-7 symbol (45×45). Choosing
     * the strongest level that stays at version 10 or below keeps codes small
     * enough to scan off a screen.
     */
    @Test
    fun `longer content trades error correction for a smaller symbol`() {
        val matrix = encode("https://example.com/" + "a".repeat(140))
        assertTrue(matrix.version <= QrEncoder.COMFORTABLE_VERSION)
        assertTrue(matrix.errorCorrection < ErrorCorrection.H)
    }

    @Test
    fun `content beyond what level H can hold is still encodable`() {
        // 1,273 bytes was the old hard limit, set by level H. Past the comfortable
        // size, M is preferred while it fits, and L only once M cannot hold it.
        assertEquals(ErrorCorrection.M, encode("x".repeat(2_000)).errorCorrection)
        assertEquals(ErrorCorrection.L, encode("x".repeat(2_500)).errorCorrection)
    }

    @Test
    fun `the limit is the real version-40 capacity`() {
        encode("x".repeat(QrEncoder.MAX_ASCII_BYTES))
        assertEquals(
            QrResult.TooLong(QrEncoder.MAX_ASCII_BYTES + 1, QrEncoder.MAX_ASCII_BYTES),
            QrEncoder.encode("x".repeat(QrEncoder.MAX_ASCII_BYTES + 1))
        )
    }

    @Test
    fun `non-ascii text is measured in utf-8 bytes`() {
        val tooLong = "ğ".repeat(QrEncoder.MAX_UTF8_BYTES / 2 + 1)
        val result = QrEncoder.encode(tooLong)
        assertEquals(QrResult.TooLong(tooLong.toByteArray().size, QrEncoder.MAX_UTF8_BYTES), result)
        encode("Merhaba dünya — नमस्ते — مرحبا")
    }
}
