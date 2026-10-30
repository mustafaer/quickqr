package net.mustafaer.quickqr.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mustafaer.quickqr.utils.ExportFormat
import net.mustafaer.quickqr.utils.HistoryExporter
import net.mustafaer.quickqr.utils.QrMatrix
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything that writes a file: generated QR images for the gallery or a share
 * sheet, and history exports. Kept out of the ViewModels so they hold no
 * `ContentResolver`, `MediaStore` or `FileProvider` code.
 *
 * Shared files live in `cache/shared/`, the only directory the FileProvider
 * exposes, and are deleted after [SHARED_FILE_TTL_MS] — they hold scan history,
 * so they should not sit in the cache indefinitely.
 */
class MediaExporter(private val context: Context) {

    private companion object {
        const val SHARED_DIR = "shared"
        const val SHARED_FILE_TTL_MS = 3_600_000L

        /** Generated images are rendered close to this size, at whole pixels per module. */
        const val TARGET_IMAGE_PX = 1536

        /** The QR specification's minimum quiet zone, in modules. */
        const val QUIET_ZONE_MODULES = 4
    }

    private val sharedDir: File get() = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }

    private val authority: String get() = "${context.packageName}.fileprovider"

    suspend fun saveToGallery(matrix: QrMatrix): Uri? = withContext(Dispatchers.IO) {
        val bitmap = render(matrix)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "quickqr_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/QuickQR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        try {
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null
            try {
                resolver.openOutputStream(uri).use { out ->
                    if (out == null) throw IOException("Could not open the gallery entry for writing")
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } catch (writeFailure: Exception) {
                // Never leave a half-written placeholder behind in the gallery.
                resolver.delete(uri, null, null)
                throw writeFailure
            }
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun shareableImage(matrix: QrMatrix): Uri? = withContext(Dispatchers.IO) {
        val bitmap = render(matrix)
        try {
            writeShared("quickqr_generated", "png") { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun exportHistory(scans: List<ScanEntity>, format: ExportFormat): Uri? =
        withContext(Dispatchers.IO) {
            if (scans.isEmpty()) return@withContext null
            val body = when (format) {
                ExportFormat.CSV -> {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    HistoryExporter.toCsv(scans) { dateFormat.format(Date(it)) }
                }
                ExportFormat.JSON -> HistoryExporter.toJson(scans)
            }
            writeShared("quickqr_history", format.extension) { out ->
                // Excel needs the BOM to read non-ASCII CSV as UTF-8.
                if (format == ExportFormat.CSV) out.write(HistoryExporter.UTF8_BOM)
                out.write(body.toByteArray(Charsets.UTF_8))
            }
        }

    /** Deletes shared files older than the TTL. Safe to call from any thread. */
    fun pruneSharedFiles() {
        val cutoff = System.currentTimeMillis() - SHARED_FILE_TTL_MS
        runCatching {
            sharedDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            // Files from before the move into `shared/` sat directly in the cache root.
            context.cacheDir.listFiles()?.forEach {
                if (it.isFile && it.name.startsWith("quickqr_")) it.delete()
            }
        }
    }

    private fun writeShared(prefix: String, extension: String, write: (FileOutputStream) -> Unit): Uri? {
        pruneSharedFiles()
        val file = File(sharedDir, "${prefix}_${System.currentTimeMillis()}.$extension")
        return try {
            FileOutputStream(file).use(write)
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /**
     * Rasterises at an integer number of pixels per module, so every module edge
     * lands on a pixel boundary and the PNG stays sharp when printed or scaled.
     */
    private fun render(matrix: QrMatrix): Bitmap {
        val modules = matrix.size + QUIET_ZONE_MODULES * 2
        val scale = (TARGET_IMAGE_PX / modules).coerceAtLeast(4)
        val px = modules * scale
        val bitmap = createBitmap(px, px, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val row = IntArray(px)
        for (y in 0 until matrix.size) {
            row.fill(Color.WHITE)
            for (x in 0 until matrix.size) {
                if (matrix[x, y]) {
                    val start = (x + QUIET_ZONE_MODULES) * scale
                    row.fill(Color.BLACK, start, start + scale)
                }
            }
            val top = (y + QUIET_ZONE_MODULES) * scale
            for (dy in 0 until scale) bitmap.setPixels(row, 0, px, 0, top + dy, px, 1)
        }
        return bitmap
    }
}
