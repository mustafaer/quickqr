package net.mustafaer.quickqr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.min

/**
 * Where the viewfinder frame sits for a given overlay size. The overlay draws it
 * and the scanner screen tests codes against it, so both read from here and can
 * never disagree.
 */
fun viewfinderRect(size: Size): Rect {
    // Width-driven in portrait, height-capped in landscape so the square fits.
    val side = min(size.width * 0.70f, size.height * 0.55f)
    val left = (size.width - side) / 2f
    val top = (size.height - side) / 2.3f
    return Rect(left, top, left + side, top + side)
}

/**
 * Where a code's centre may sit and still be accepted. Deliberately wider than
 * the drawn frame — a code the user lines up inside it must always scan — while
 * still ruling out a code at the very edge of the darkened area being read and
 * saved without the user ever having aimed at it.
 */
fun Rect.acceptRegion(): Rect = inflate(width * 0.15f)

private val Scrim = Color.Black.copy(alpha = 0.6f)
private val IdleBracket = Color.White
private val LockedBracket = Color(0xFFB4A5FF)
private const val BREATHE_SCALE = 1.03f

/**
 * The scanner overlay: a darkened view with a cut-out and four corner brackets.
 *
 * Idle, the brackets breathe very slightly. When a code is read they spring
 * from the frame onto the code itself and change colour, so the eye sees the
 * lock before the result sheet opens. The idle animation runs only while
 * [idle] — when the scanner tab is visible and scanning — so the app stops
 * producing frames on other tabs and while a result is open, instead of keeping
 * the display at its maximum refresh rate forever.
 *
 * The cut-out is cleared from an offscreen layer with [BlendMode.Clear], which
 * costs one rounded rectangle per frame rather than rebuilding a clip path.
 *
 * @param lockedBox the read code's bounding box, in this composable's pixels.
 */
@Composable
fun Viewfinder(lockedBox: Rect?, idle: Boolean, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var size by remember { mutableStateOf(Size.Zero) }
    val frame = if (size == Size.Zero) null else viewfinderRect(size)
    val lockPadding = with(density) { 12.dp.toPx() }
    val target = lockedBox?.inflate(lockPadding) ?: frame
    val locked = lockedBox != null

    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(target) {
        val destination = target ?: return@LaunchedEffect
        if (!placed) {
            rect.snapTo(destination)
            placed = true
        } else {
            rect.animateTo(destination, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val lockProgress by animateFloatAsState(
        targetValue = if (locked) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "viewfinderLock"
    )

    val breathe = remember { Animatable(1f) }
    val breathing = idle && !locked
    LaunchedEffect(breathing) {
        // Leaving this effect cancels the loop — and with it the frame requests.
        if (!breathing) {
            breathe.animateTo(1f, tween(durationMillis = 200))
            return@LaunchedEffect
        }
        breathe.animateTo(
            targetValue = BREATHE_SCALE,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    val brackets = remember { Path() }

    Spacer(
        modifier = modifier
            .onSizeChanged { size = it.toSize() }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawBehind {
                if (!placed) return@drawBehind
                // Animated values are read here, in the draw phase only, so each
                // animation frame redraws without recomposing or relaying out.
                val base = rect.value
                val scale = breathe.value
                val halfWidth = base.width * scale / 2f
                val halfHeight = base.height * scale / 2f
                val shown = Rect(
                    base.center.x - halfWidth, base.center.y - halfHeight,
                    base.center.x + halfWidth, base.center.y + halfHeight
                )
                val corner = min(20.dp.toPx(), shown.minDimension * 0.12f)

                drawRect(Scrim)
                drawRoundRect(
                    color = Color.Black,
                    topLeft = shown.topLeft,
                    size = shown.size,
                    cornerRadius = CornerRadius(corner, corner),
                    blendMode = BlendMode.Clear
                )

                val arm = min(28.dp.toPx(), shown.minDimension * 0.24f).coerceAtLeast(corner)
                brackets.reset()
                brackets.addBrackets(shown, corner, arm)
                drawPath(
                    path = brackets,
                    color = lerp(IdleBracket, LockedBracket, lockProgress),
                    style = Stroke(
                        width = 3.dp.toPx() + lockProgress * 1.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
    )
}

/** Four L-shaped corners, each rounded to follow the cut-out's radius. */
private fun Path.addBrackets(r: Rect, corner: Float, arm: Float) {
    moveTo(r.left, r.top + arm)
    lineTo(r.left, r.top + corner)
    quadraticTo(r.left, r.top, r.left + corner, r.top)
    lineTo(r.left + arm, r.top)

    moveTo(r.right - arm, r.top)
    lineTo(r.right - corner, r.top)
    quadraticTo(r.right, r.top, r.right, r.top + corner)
    lineTo(r.right, r.top + arm)

    moveTo(r.right, r.bottom - arm)
    lineTo(r.right, r.bottom - corner)
    quadraticTo(r.right, r.bottom, r.right - corner, r.bottom)
    lineTo(r.right - arm, r.bottom)

    moveTo(r.left + arm, r.bottom)
    lineTo(r.left + corner, r.bottom)
    quadraticTo(r.left, r.bottom, r.left, r.bottom - corner)
    lineTo(r.left, r.bottom - arm)
}
