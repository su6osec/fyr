package com.fyr.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The habit-detail hero, staged.
 *
 * Three beats, once per open of the screen:
 *
 *  1. the border **draws itself** — 760ms of arc, closing the circle;
 *  2. at the moment it closes, a halo breaks outward from the rim;
 *  3. inside it, the streak **pops** in and counts **up from zero**.
 *
 * After the first run the component hands over to ordinary state animation,
 * so ticking a day in the month grid updates ring and number without
 * replaying the entrance. That distinction matters: an animation you see
 * every time you tap stops being a reward and becomes a delay.
 */
@Composable
fun StreakSeal(
    streak: Int,
    fraction: Float,
    size: Dp,
    accent: Color,
    track: Color,
    ink: Color,
    muted: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 9.dp,
    unit: String = "days",
) {
    // Beat 1 — the circle. Disposed with the screen, so it runs on every open.
    val draw = remember { Animatable(0f) }
    // Beat 3 — the pop, and the reveal that the count is allowed to show.
    val pop = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        draw.animateTo(
            targetValue = 1f,
            animationSpec = tween(760, easing = FastOutSlowInEasing),
        )
        launch {
            pop.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.48f,
                    stiffness = 420f,
                ),
            )
        }
        launch {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = tween(720, easing = FastOutSlowInEasing),
            )
        }
    }

    // Steady-state values: inert until the entrance has handed over.
    val liveArc by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "sealArc",
    )
    val liveStreak by animateFloatAsState(
        targetValue = streak.toFloat(),
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "sealStreak",
    )

    val shown = (liveStreak * reveal.value).roundToInt()
    val popClamped = pop.value.coerceIn(0f, 1f)
    val halo = (1f - popClamped).coerceIn(0f, 1f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val diameter = this.size.minDimension
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(diameter - width, diameter - width)
            val arcTopLeft = Offset(inset, inset)
            val style = Stroke(width = width, cap = StrokeCap.Round)

            // The rim closes as it is drawn rather than fading in whole.
            val drawn = 360f * draw.value
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = drawn,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = style,
            )
            val ember = drawn * liveArc
            if (ember > 0.5f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = ember,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = style,
                )
            }

            // Beat 2 — the halo, released the instant the circle is whole.
            if (halo > 0.001f && draw.value > 0.98f) {
                val radius = (diameter / 2f) * (1f + 0.20f * (1f - halo))
                drawCircle(
                    color = accent.copy(alpha = 0.34f * halo),
                    radius = radius,
                    style = Stroke(width = width * 0.55f),
                )
            }
        }

        Column(
            modifier = Modifier.graphicsLayer(
                scaleX = 0.34f + 0.66f * popClamped,
                scaleY = 0.34f + 0.66f * popClamped,
                alpha = popClamped,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "$shown",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = ink,
            )
            Text(
                // Keyed on the streak being aimed at, not on the number the
                // count-up is currently passing through: at 60fps the old
                // test saw `1` for two frames on every run of two days or
                // more, and the label flickered "day" → "days" on its way up.
                text = if (streak == 1) unit.removeSuffix("s") else unit,
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
    }
}
