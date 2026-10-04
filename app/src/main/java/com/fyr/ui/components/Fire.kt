package com.fyr.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fyr.data.Suggestions
import com.fyr.ui.theme.Ember
import com.fyr.ui.theme.EmberSoft
import kotlin.math.sin

/**
 * A flame that is actually burning.
 *
 * The brightness holds steady: no breathing glow, no pulsing alpha. What
 * gives the fire its life is the embers lifting off it — [FlameFlares],
 * thrown once when the fire appears and once per poke — and a halo swelling
 * behind a constantly flaring fire read as a second, slower fire competing
 * with the first.
 *
 * What remains is motion rather than light: two out-of-phase waves on
 * scale, so the flicker never settles into a metronome tick. The amplitude
 * is kept small (±7%) because this sits next to a number people are
 * reading; a flame that lurches reads as a loading spinner, and this is
 * not loading anything.
 *
 * Purely decorative: it draws, it does not handle pointers, and it costs one
 * infinite transition — but only while there is someone to be decorative
 * *for*. The clock stands down entirely when the app leaves the screen or
 * the system enters battery saver (see [rememberFlameBurning]), because the
 * whole cost of this composable is the frame callbacks its wave is read in,
 * and a flicker nobody can see is a flicker that should not be billed to
 * the battery.
 */
@Composable
fun BurningFlame(
    size: Dp,
    modifier: Modifier = Modifier,
    showGlow: Boolean = true,
) {
    // Two compositions, chosen by one condition: allowed to burn or not.
    // The transition lives only in the burning branch, so standing down
    // *disposes* it rather than pausing it — no frame callback survives,
    // and nothing is left running to be resumed later.
    if (rememberFlameBurning()) {
        val transition = rememberInfiniteTransition(label = "flame")
        val clock by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "flameClock",
        )
        FlameDrawing(size = size, showGlow = showGlow, clock = { clock }, modifier = modifier)
    } else {
        // The same flame at the wave's rest phase: a still fire rather than
        // a vanished one, so the mark never leaves while its cost does.
        FlameDrawing(size = size, showGlow = showGlow, clock = { 0f }, modifier = modifier)
    }
}

/**
 * Whether the fire is allowed to burn.
 *
 * The flame's entire cost is its clock, so this is the switch that keeps
 * that cost off the battery: no screen means no frame callbacks to service
 * (a decorative flicker has no business running an activity nobody can
 * see), and battery saver means the system has asked everyone to stop
 * spending — a paragraph of animation is exactly what that request is for.
 *
 * The saver is re-read on every return to the screen *and* on the system's
 * own change broadcast, so flipping the toggle is answered in the same
 * moment rather than at the next resume. The broadcast is the only
 * receiver in the app: it fires when a person moves the one switch, never
 * on a schedule, and it costs nothing to hold.
 */
@Composable
private fun rememberFlameBurning(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }

    // The saver starts as it actually is: composition happens while the
    // activity is being resumed, and a flame that opened *dark* on the
    // splash would be a bug of its own.
    var saverOn by remember { mutableStateOf(power?.isPowerSaveMode == true) }
    var foreground by remember { mutableStateOf(true) }

    LifecycleResumeEffect(Unit) {
        saverOn = power?.isPowerSaveMode == true
        foreground = true
        onPauseOrDispose { foreground = false }
    }

    DisposableEffect(context, power) {
        val saver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                saverOn = power?.isPowerSaveMode == true
            }
        }
        ContextCompat.registerReceiver(
            context,
            saver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(saver) }
    }

    return foreground && !saverOn
}

/**
 * The flame itself: the halo, and the wave the flicker is made of.
 *
 * [clock] is a *supplier*, invoked inside the layer and draw lambdas and
 * never in this body — the clock changes every frame, and reading it up
 * here made this composable recompose forty-odd times a second; the flame
 * is one drawing, so it only needs to be *redrawn* — and only those two
 * phases read the animation. The supplier form is also what lets the
 * caller hand over a clock that has stopped: reading `{ 0f }` draws the
 * same frame forever with nothing subscribed to any clock at all.
 *
 * 3.1 and 4.7 cycles across the loop: neither divides the other, so the
 * combined wave takes the full 1.4s to repeat.
 */
@Composable
private fun FlameDrawing(
    size: Dp,
    showGlow: Boolean,
    clock: () -> Float,
    modifier: Modifier = Modifier,
) {
    val tau = TAU

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (showGlow) {
            Canvas(Modifier.size(size * 1.9f)) {
                // Fixed at the middle of the range the glow used to sweep:
                // the same average light, none of the breathing.
                val glowAlpha = 0.41f
                val radius = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to EmberSoft.copy(alpha = glowAlpha),
                        0.45f to Ember.copy(alpha = glowAlpha * 0.45f),
                        1f to Color.Transparent,
                    ),
                    radius = radius,
                )
            }
        }

        Emoji(
            emoji = Suggestions.FLAME,
            size = size,
            modifier = Modifier.graphicsLayer {
                val phase = clock()
                val waveA = sin(phase * tau * 3.1f)
                val waveB = sin(phase * tau * 4.7f + 1.1f)
                scaleX = 1f - 0.045f * waveB
                scaleY = 1f + 0.07f * waveA
                translationY = (-0.9f * waveA).dp.toPx()
            },
        )
    }
}

/** One turn, as a float: the length of the wave the flame is built from. */
private const val TAU = 6.2831853f

/**
 * Embers climbing off a fire.
 *
 * One-shot by design: each change of [tick] restarts the burst — a poke on
 * the header flame, a press on a streak pill, the launch logo drawing its
 * single welcome. The fire does not throw embers on its own; the hand that
 * stokes it gets the answer, which is what makes the poke feel wired to the
 * mark rather than decorative.
 *
 * The particles come from a fixed table of seeds rather than a random one,
 * so the frame costs no allocations and every burst fans out the same way —
 * a burst is a picture, not a dice roll.
 *
 * Each ember leaves at its own moment — a fan fired in unison reads as a
 * spray, a fan fired in sequence reads as fire — climbs fast and stalls as
 * it cools (embers leave hot and hang in still air), turns from white-hot
 * to ember as it rises, and is fully faded before it reaches the top of the
 * box, so nothing ever touches the edge of the list the header sits in.
 *
 * Draws above the flame it belongs to: the embers are composed last in the
 * caller's box, lifting off the art rather than behind it.
 */
@Composable
fun FlameFlares(
    modifier: Modifier = Modifier,
    tick: Int = 0,
) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(tick) {
        if (tick <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(FLARE_MS, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val p = progress.value
        if (p >= 1f) return@Canvas
        val w = size.width
        val h = size.height

        for (i in 0 until FLARE_COUNT) {
            // Golden-ratio seeds: neighbouring embers never share a phase.
            val seed = (i * 0.618034f) % 1f
            val delay = (i % 5) * 0.055f + seed * 0.16f
            if (p <= delay) continue
            val t = ((p - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (t >= 1f) continue

            val lateral = sin(seed * TAU) // −1..1, spread across the belly
            val y0 = h * (0.52f + 0.16f * ((i * 7 % 11) / 10f))
            val rise = h * (0.40f + 0.30f * seed)
            // Ease-out: off the fire fast, hanging as it climbs.
            val climb = 1f - (1f - t) * (1f - t)
            val x = w * 0.5f +
                lateral * w * (0.22f + 0.12f * t) +
                sin(t * 6f + seed * 9f) * w * 0.04f
            val y = y0 - rise * climb
            val radius = w * (0.055f + 0.035f * seed) * (1f - 0.55f * t)
            // Lit near-white, cooled to ember before it thins out: the cool
            // end is the one that still reads against a light theme.
            val cool = (t * 1.9f).coerceAtMost(1f)
            val alpha = (t / 0.12f).coerceAtMost(1f) * (1f - t) * (1f - t)
            drawCircle(
                color = lerp(FLARE_HOT, Ember, cool).copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}

/** How long one burst lasts, first ember to last. */
private const val FLARE_MS = 820

/** Fixed count, fixed seeds — a burst is a picture, not a dice roll. */
private const val FLARE_COUNT = 12

/** The colour an ember leaves the fire in: nearly white, faintly warm. */
private val FLARE_HOT = Color(0xFFFFEEC4)
