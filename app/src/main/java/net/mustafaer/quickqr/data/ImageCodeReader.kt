package net.mustafaer.quickqr.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface ImageReadResult {
    data class Found(val raw: String) : ImageReadResult
    data object NotFound : ImageReadResult
    data object Unreadable : ImageReadResult
}

/** Reads a barcode out of a picture the user picked. */
fun interface ImageCodeReader {
    suspend fun read(uri: Uri): ImageReadResult
}

/**
 * Decodes the picture at no more than [MAX_EDGE_PX] on its long edge before ML
 * Kit sees it. A 50-megapixel photo decoded at full size is about 200 MB of
 * pixels — enough to kill the process — and a QR code needs nowhere near that
 * resolution to read.
 */
class MlKitImageCodeReader(private val context: Context) : ImageCodeReader {

    private companion object {
        const val MAX_EDGE_PX = 2048
    }

    override suspend fun read(uri: Uri): ImageReadResult = withContext(Dispatchers.IO) {
        val bitmap = try {
            decode(uri)
        } catch (_: OutOfMemoryError) {
            // An Error, not an Exception: it would sail past a catch (Exception).
            null
        } catch (_: Exception) {
            null
        } ?: return@withContext ImageReadResult.Unreadable

        val scanner = BarcodeScanning.getClient()
        try {
            val barcodes = scanner.process(InputImage.fromBitmap(bitmap, 0)).await()
            barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotEmpty) }
                ?.let { ImageReadResult.Found(it) }
                ?: ImageReadResult.NotFound
        } catch (_: Exception) {
            ImageReadResult.Unreadable
        } finally {
            scanner.close()
            bitmap.recycle()
        }
    }

    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longEdge = maxOf(info.size.width, info.size.height)
                if (longEdge > MAX_EDGE_PX) {
                    val scale = MAX_EDGE_PX.toFloat() / longEdge
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1)
                    )
                }
                // ML Kit reads pixels back; a hardware bitmap cannot be read.
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // Barcode detection does not care about orientation, so EXIF rotation
            // can be ignored here and the picture decoded as stored.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE_PX) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }
}
