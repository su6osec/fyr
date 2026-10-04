package com.fyr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin

/**
 * The frame every identity in Fyr wears: a breath of ember behind, the
 * picture's own edge held by the lit gradient ring, a thin ring beyond it,
 * and two rings of beads outside that — *all* of it in both lights.
 *
 * The bar's control used to dress by the clock: sparkles in the light, one
 * hairline in the dark, so the same button wore two different frames and a
 * person switching themes met a different object. This is the one drawing
 * now — the marks each light kept for itself, drawn together — and the
 * portraits (masthead, editor, first-launch form) wear the identical frame,
 * so *identity* has exactly one shape in the app: dots and rings.
 *
 * The portrait's rings **wave**: twelve gentle undulations riding the
 * picture's own circle, so the border reads circular first and ripples
 * second — a flower around a face rather than a stamp over it. Every layer
 * shares the same wave at the same phase, which is what keeps the channels
 * between ring, hairline and beads constant: the frame breathes as one
 * object and no bead ever collides with the line it sits beside. The plus
 * stays perfectly round ([drawHaloMarks] with `wavy = false`) — it is a
 * tool, not a portrait.
 *
 * Nothing here animates. The wave is a shape, not a clock: it is redrawn
 * when the theme changes and not once after, because a border that moves
 * is a border that bills the battery for the privilege.
 */
@Composable
fun PortraitHalo(
    photo: String,
    name: String,
    photoSize: Dp,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() >= 0.5f

    // The same face that catches light the bar's add control wears, derived
    // from the scheme so this circle cannot become the one thing on screen
    // that ignores the accent.
    val lit = lerp(scheme.primary, Color.White, 0.30f)
    val shaded = lerp(scheme.primary, Color.Black, 0.26f)
    val rim = Brush.verticalGradient(listOf(lit, scheme.primary, shaded))

    // With a badge the frame's square almost contains it — the badge hangs
    // to the picture's bottom point, half in and half out, and reaches two
    // dp past the air the rings themselves keep.
    val height = photoSize + HaloAir * 2 +
        if (badge != null) HaloBadgeDrop - HaloAir else 0.dp

    Box(
        modifier = modifier.size(photoSize + HaloAir * 2, height),
        contentAlignment = Alignment.TopCenter,
    ) {
        // The core: one square the photo sits at the centre of, so the
        // breath and the marks are drawn on the photo's own axes no matter
        // how much extra room the badge hangs into below. Alignment is
        // load-bearing, not decoration: the marks come off the canvas's
        // centre, so a picture left in the default top-start corner would
        // stand a full air's width left of the ring circling it — visible
        // the moment the theme is the paper one, where the photo's own
        // colours trespass on white instead of hiding in black.
        Box(
            modifier = Modifier.size(photoSize + HaloAir * 2),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawHaloBreath(
                    radius = size.minDimension / 2f,
                    color = scheme.primary,
                    alpha = if (light) 0.16f else 0.34f,
                )
            }

            // The photo is clipped to a clean circle so it always renders; the wavy
            // rings are drawn on top and visually contain it. This keeps the
            // picture from ever vanishing due to a malformed clip path while
            // the rings still undulate around it. Shadow stays circular so the
            // lift reads as one piece in both lights.
            Box(
                modifier = Modifier
                    .size(photoSize)
                    .shadow(
                        elevation = if (light) 9.dp else 10.dp,
                        shape = CircleShape,
                        ambientColor = if (light) Color.Black else scheme.primary,
                        spotColor = if (light) Color.Black else scheme.primary,
                    )
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Avatar(photo = photo, size = photoSize, name = name)
            }

            Canvas(Modifier.matchParentSize()) {
                drawHaloMarks(
                    contentRadius = photoSize.toPx() / 2f,
                    rim = rim,
                    wavy = true,
                    hairlineColor = scheme.primary.copy(alpha = 0.55f),
                    dotColor = scheme.primary,
                )
            }
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = HaloAir + photoSize - HaloBadgeDrop),
            ) {
                badge()
            }
        }
    }
}

/**
 * The breath: one ember radial, wider in the dark where it doubles as the
 * glow paper would have got from shadow, quieter on paper where the page is
 * already lit. Drawn *behind* the picture — it is the light the frame sits
 * in, not a wash over the face.
 */
fun DrawScope.drawHaloBreath(radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(
                color.copy(alpha = alpha),
                color.copy(alpha = 0f),
            ),
        ),
        radius = radius,
        center = center,
    )
}

/**
 * The marks: the thick rim (when the caller draws one — the portraits do,
 * the plus carries its own border on the control itself), the thin hairline
 * beyond it, and twelve bright beads with twelve dimmer ones tucked half a
 * step around and further out — one family of marks for every light.
 *
 * Offsets are measured from [contentRadius], the circle being framed: the
 * rim's outer edge lands exactly on it, and everything else steps outward
 * from there — the same three-plus-six-and-eleven the bar has always used,
 * so the control and the portraits are dressed from one tape measure.
 * The rim stays a perfect circle (it is the photo's border); the hairline
 * and beads ride the wave so the frame reads circular first and ripples
 * second — a flower around a face rather than a stamp over it.
 */
fun DrawScope.drawHaloMarks(
    contentRadius: Float,
    rim: Brush?,
    wavy: Boolean,
    hairlineColor: Color,
    dotColor: Color,
) {
    val c = center
    val waveAmp = if (wavy) HaloWaveAmp.toPx() else 0f

    fun at(deg: Double, base: Float): Offset {
        val rr = base + waveAmp * sin(HaloWaves * deg).toFloat()
        return Offset(
            c.x + rr * cos(deg).toFloat(),
            c.y + rr * sin(deg).toFloat(),
        )
    }

    // Rim: always a perfect circle — it is the photo's own border
    if (rim != null) {
        drawCircle(
            brush = rim,
            radius = contentRadius - RimInset.toPx(),
            center = c,
            style = Stroke(width = RimWidth.toPx()),
        )
    }

    // Hairline and beads: ride the wave when enabled
    drawPath(
        path = wavePath(c.x, c.y, contentRadius + HaloHairline.toPx(), waveAmp),
        color = hairlineColor,
        style = Stroke(width = HairlineWidth.toPx()),
    )

    val step = 2.0 * Math.PI / HaloDots
    repeat(HaloDots) { i ->
        val a = i * step
        drawCircle(
            color = dotColor.copy(alpha = 0.9f),
            radius = BrightDot.toPx(),
            center = at(a, contentRadius + HaloBright.toPx()),
        )
        drawCircle(
            color = dotColor.copy(alpha = 0.5f),
            radius = DimDot.toPx(),
            center = at(a + step / 2, contentRadius + HaloDim.toPx()),
        )
    }
}

/**
 * A closed circle whose radius undulates: [amp] × sin([waves]θ), sampled
 * once per degree. A shape, not a curve engine — three hundred-odd line
 * segments into one path is smoother than any ring a screen can show, and
 * it is walked once per theme change rather than per frame.
 */
private fun wavePath(cx: Float, cy: Float, r: Float, amp: Float): Path {
    val path = Path()
    for (degree in 0..360) {
        val t = Math.toRadians(degree.toDouble())
        val rr = r + amp * sin(HaloWaves * t).toFloat()
        val x = cx + (rr * cos(t)).toFloat()
        val y = cy + (rr * sin(t)).toFloat()
        if (degree == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** The air the frame keeps around the picture: as far as the dim beads reach. */
private val HaloAir = 16.dp

/** The wave's own size — how far the rings ride in and out of true. */
private val HaloWaveAmp = 3.dp

/** Twelve waves and twelve bright beads: every bead sits on a wave's node. */
private val HaloWaves = 12

/** The thick ring: two and a half wide, its outer edge landing on the rim. */
private val RimWidth = 2.5.dp
private val RimInset = 1.25.dp

/** The thin ring, three out from the rim — the plus's old dark hairline. */
private val HairlineWidth = 1.5.dp
private val HaloHairline = 3.dp

/** The beads: bright at six and a half, dim at eleven, half a step apart. */
private val HaloBright = 6.5.dp
private val BrightDot = 1.7.dp
private val HaloDim = 11.dp
private val DimDot = 1.2.dp
private val HaloDots = 12

/** How far a rim badge hangs from the picture's bottom point, centre on. */
private val HaloBadgeDrop = 18.dp
