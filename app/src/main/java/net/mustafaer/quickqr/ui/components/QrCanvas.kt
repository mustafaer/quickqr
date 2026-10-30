package net.mustafaer.quickqr.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import net.mustafaer.quickqr.utils.QrMatrix
import kotlin.math.floor

/**
 * Draws a QR symbol as vector rectangles at a whole number of pixels per
 * module, so every edge is pixel-sharp at any density. The old preview scaled a
 * 512 px bitmap into the box with bilinear filtering and softened every module.
 *
 * The path is built once per symbol and size — each horizontal run of dark
 * modules is one rectangle — and replayed on every draw.
 *
 * Always black on white: a themed QR is one some scanners refuse to read.
 */
@Composable
fun QrCanvas(
    matrix: QrMatrix,
    contentDescription: String,
    modifier: Modifier = Modifier,
    quietZoneModules: Int = 2
) {
    Spacer(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
            .drawWithCache {
                val across = matrix.size + quietZoneModules * 2
                val exact = size.minDimension / across
                // Whole pixels per module where there is room; a fractional cell
                // only when the symbol is denser than the box has pixels.
                val cell = if (exact >= 1f) floor(exact) else exact
                val symbolSize = cell * matrix.size
                val left = floor((size.width - symbolSize) / 2f)
                val top = floor((size.height - symbolSize) / 2f)
                val modules = Path()
                for (y in 0 until matrix.size) {
                    var x = 0
                    while (x < matrix.size) {
                        if (!matrix[x, y]) {
                            x++
                            continue
                        }
                        val start = x
                        while (x < matrix.size && matrix[x, y]) x++
                        modules.addRect(
                            Rect(left + start * cell, top + y * cell, left + x * cell, top + (y + 1) * cell)
                        )
                    }
                }
                onDrawBehind {
                    drawRect(Color.White)
                    drawPath(modules, Color.Black)
                }
            }
    )
}
