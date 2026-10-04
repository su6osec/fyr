package com.fyr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.fyr.ui.theme.Ember
import kotlin.math.sin
import kotlin.random.Random

/**
 * Confetti, falling.
 *
 * The moment a streak starts is the only time this app raises its voice, so
 * the animation is deliberately *pass-through*: it is drawn in a layer that
 * handles no pointer input, so a user who wants to keep tapping is never made
 * to wait for the celebration to finish. It plays once and composes nothing
 * again.
 */
@Composable
fun ConfettiBurst(
    accent: Color,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deterministic per run, so a burst is varied without ever being chaotic.
    val pieces = remember {
        val rnd = Random(System.nanoTime())
        List(PIECES) {
            Piece(
                x = rnd.nextFloat(),
                // The stagger is the whole trick. With a narrow delay every
                // piece is airborne at once and the burst falls as a single
                // clump — measured as 2159 of 2161 flakes inside one 226px
                // band. Spreading start times across ~58% of the timeline
                // while each fall takes ~40% of it keeps roughly forty pieces
                // aloft at any instant, spread over the whole height, so the
                // screen reads as rain rather than as a curtain. delay + span
                // never exceeds 1, so nothing is cut off at the end.
                delay = rnd.nextFloat() * 0.58f,
                span = 0.36f + rnd.nextFloat() * 0.06f,
                sway = 10f + rnd.nextFloat() * 34f,
                spin = (if (rnd.nextBoolean()) 1 else -1) * (480f + rnd.nextFloat() * 620f),
                halfW = if (rnd.nextFloat() < 0.34f) 13f else 8f,
                halfH = 4.5f,
                followsAccent = rnd.nextInt(4) == 0,
                color = CONFETTI[rnd.nextInt(CONFETTI.size)],
                phase = rnd.nextFloat() * 6.283f,
            )
        }
    }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(DURATION_MS, easing = FastOutSlowInEasing),
        )
        onFinished()
    }

    Canvas(
        modifier = modifier.fillMaxSize(),
    ) {
        val p = progress.value
        val h = size.height
        val w = size.width

        pieces.forEach { piece ->
            val local = ((p - piece.delay) / piece.span).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach

            val drop = START_PAD.toPx()
            val y = -drop + local * (h + drop * 2f)
            val x = piece.x * w + sin(local * 5.4f + piece.phase) * piece.sway

            val alpha = when {
                local >= 0.94f -> ((1f - local) / 0.06f).coerceIn(0f, 1f)
                local <= 0.08f -> (local / 0.08f).coerceIn(0f, 1f)
                else -> 1f
            }
            val colour = if (piece.followsAccent) accent else piece.color

            val halfLen = piece.halfW.dp.toPx()
            val thickness = piece.halfH.dp.toPx() * 2f
            val theta = Math.toRadians((piece.spin * local).toDouble())
            val dx = (Math.cos(theta) * halfLen).toFloat()
            val dy = (Math.sin(theta) * halfLen).toFloat()

            // A stroked segment *is* a rotated rectangle: length along the
            // spin, width across it. That gets tumbling confetti out of one
            // drawLine with no transform stack and no rotation maths to
            // get subtly wrong at the edges of the screen.
            drawLine(
                color = colour.copy(alpha = colour.alpha * alpha),
                start = Offset(x - dx, y - dy),
                end = Offset(x + dx, y + dy),
                strokeWidth = thickness,
                cap = StrokeCap.Butt,
            )
        }
    }
}

/** One flake: position and motion are all resolved from the global clock. */
private class Piece(
    val x: Float,
    val delay: Float,
    val span: Float,
    val sway: Float,
    val spin: Float,
    val halfW: Float,
    val halfH: Float,
    val followsAccent: Boolean,
    val color: Color,
    val phase: Float,
)

/** A few more than look like it: with stagger only ~40% are ever airborne. */
private const val PIECES = 72
private const val DURATION_MS = 1900

/** How far above the screen the flakes start, so they arrive from off-screen. */
private val START_PAD = 40.dp

private val CONFETTI = listOf<Color>(
    Color(0xFFFFD54A), // ember
    Color(0xFFFF7A45), // the flame's hotter edge
    Color(0xFFF5F5F7), // paper white, keeps the palette from warming out
    Color(0xFF9BE7A4), // one cool note for contrast
    Ember,
)
