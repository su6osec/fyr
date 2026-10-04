package com.fyr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn glyphs on a 24×24 grid.
 *
 * Fyr carries no icon library. Two reasons: the Material icon set is a large
 * dependency for a handful of marks, and third-party icons would immediately
 * disagree with the flame. These are drawn from lines, arcs and circles with
 * the same stroke weight throughout, so they sit together as one family.
 */
enum class Glyph {
    ADD, CLOSE, SEARCH, CHEVRON_LEFT, CHEVRON_RIGHT, TODAY, CALENDAR, BARS, PERSON,
    // The three a habit row offers on a long press. Drawn to the same grid and
    // the same stroke weight as the navigation marks, so a menu opened off a
    // list still belongs to the same family as the bar at the bottom of it.
    EDIT, ARCHIVE, DELETE,
    // The long-press offer that starts a batch: a checkbox with its tick,
    // drawn on the same grid — the mark a list wears when it can be picked
    // apart from the mark any single row wears when it is picked.
    SELECT,
    // The reward. Drawn filled rather than stroked, because a trophy carries
    // too many small parts — cup, two handles, stem, foot — to survive an
    // outline at the size a celebration card shows it.
    TROPHY,
    // The badge on an empty profile picture: an arrow lifting off a base, the
    // direct mirror of DELETE, which only ever appears on a full one. A bin on
    // the right tells a person a photo can be removed; it implies — very
    // quietly — that one can be added. This says it out loud.
    UPLOAD,
    // The person, flooded rather than outlined. The stroked version is right
    // at 20dp sitting beside a word; at 46dp alone in the middle of a circle
    // it is a wireframe of a person where a person should be.
    PERSON_FILLED,
    // Information: a simple "i" in a circle — the universal mark for "tap to
    // learn more". Sits beside the edit pencil in the profile masthead so the
    // About card's version and credits are one tap away without crowding the
    // screen with a separate button.
    INFO
}

@Composable
fun GlyphIcon(
    glyph: Glyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    strokeWidth: Dp = 2.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val u = this.size.width / 24f
        val sw = strokeWidth.toPx()
        fun at(x: Float, y: Float) = Offset(x * u, y * u)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = sw) {
            drawLine(color, at(x1, y1), at(x2, y2), strokeWidth = width, cap = StrokeCap.Round)
        }

        fun polyline(vararg pts: Float, width: Float = sw) {
            val path = Path()
            path.moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) {
                path.lineTo(pts[i] * u, pts[i + 1] * u)
                i += 2
            }
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
        }

        // The same walk, closed and flooded: for marks too intricate to read
        // as an outline at small sizes.
        fun filled(vararg pts: Float) {
            val path = Path()
            path.moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) {
                path.lineTo(pts[i] * u, pts[i + 1] * u)
                i += 2
            }
            path.close()
            drawPath(path, color)
        }

        when (glyph) {
            Glyph.ADD -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }

            Glyph.CLOSE -> {
                line(6.5f, 6.5f, 17.5f, 17.5f)
                line(17.5f, 6.5f, 6.5f, 17.5f)
            }

            Glyph.SEARCH -> {
                drawCircle(
                    color = color,
                    radius = 5.6f * u,
                    center = at(10.4f, 10.4f),
                    style = Stroke(width = sw),
                )
                line(14.6f, 14.6f, 19.5f, 19.5f, sw * 1.1f)
            }

            Glyph.CHEVRON_LEFT -> polyline(14.5f, 5.5f, 8.5f, 12f, 14.5f, 18.5f)
            Glyph.CHEVRON_RIGHT -> polyline(9.5f, 5.5f, 15.5f, 12f, 9.5f, 18.5f)

            Glyph.TODAY -> {
                // A check, held inside a ring — "done for today".
                drawCircle(
                    color = color,
                    radius = 9f * u,
                    center = at(12f, 12f),
                    style = Stroke(width = sw),
                )
                polyline(7.5f, 12.3f, 10.6f, 15.4f, 16.6f, 8.6f, width = sw * 1.15f)
            }

            Glyph.CALENDAR -> {
                val r = 3f * u
                val left = 3.2f * u
                val top = 5.4f * u
                val right = 20.8f * u
                val bottom = 20.6f * u
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = Stroke(width = sw),
                )
                // header rule
                line(3.4f, 10f, 20.6f, 10f, sw * 0.85f)
                // hangers
                line(8.2f, 3f, 8.2f, 7.2f, sw)
                line(15.8f, 3f, 15.8f, 7.2f, sw)
            }

            Glyph.BARS -> {
                line(5.5f, 19f, 5.5f, 14f, sw * 1.25f)
                line(12f, 19f, 12f, 9f, sw * 1.25f)
                line(18.5f, 19f, 18.5f, 4.5f, sw * 1.25f)
            }

            Glyph.PERSON -> {
                // Head, then the shoulders as the top half of an ellipse: two
                // primitives, which is what keeps it in the same family as the
                // ring-and-check TODAY mark beside it.
                drawCircle(
                    color = color,
                    radius = 3.8f * u,
                    center = at(12f, 8f),
                    style = Stroke(width = sw),
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = at(4.5f, 15f),
                    size = androidx.compose.ui.geometry.Size(15f * u, 11f * u),
                    style = Stroke(width = sw, cap = StrokeCap.Round),
                )
            }

            // Same two primitives, flooded. The head sits a touch lower and
            // larger than the stroked version because an outline has its own
            // visual weight to spare and a solid shape does not — measured
            // against the 64dp circle it fills, rather than against the icon.
            Glyph.PERSON_FILLED -> {
                drawCircle(
                    color = color,
                    radius = 4.1f * u,
                    center = at(12f, 8.4f),
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = at(4.6f, 15.2f),
                    size = androidx.compose.ui.geometry.Size(14.8f * u, 11.6f * u),
                )
            }

            // An arrow lifting off a base. Deliberately the same weight and
            // the same 24-grid as DELETE, so the badge reads as one control in
            // two states rather than two unrelated stickers on one circle.
            Glyph.UPLOAD -> {
                line(12f, 16.2f, 12f, 4.4f)
                polyline(6.6f, 9.6f, 12f, 4.4f, 17.4f, 9.6f)
                line(4.6f, 20.2f, 19.4f, 20.2f)
            }

            // A pencil: one closed outline that runs tip → base → cap → base →
            // back to the tip, plus the single line across the wood that turns
            // a plain diagonal bar into something obviously a pencil. The tip
            // sits low-left, which is where a hand holds it, so the mark reads
            // as "edit" rather than as a slash at 18dp.
            Glyph.EDIT -> {
                polyline(
                    4.8f, 21.2f,
                    11.1f, 19.1f,
                    20.3f, 9.9f,
                    16.1f, 5.7f,
                    6.9f, 14.9f,
                    4.8f, 21.2f,
                )
                line(11.1f, 19.1f, 6.9f, 14.9f)
            }

            // An archive box: lid, straight-sided body, and the slot a label
            // would go in. "Retire" is not deletion, so the mark is a box that
            // keeps things rather than a bin that loses them — the sides are
            // deliberately square, where the bin above tapers, because a
            // tapered box with a lid would read as the trash icon beside it.
            Glyph.ARCHIVE -> {
                drawRoundRect(
                    color = color,
                    topLeft = at(4.4f, 4.6f),
                    size = androidx.compose.ui.geometry.Size(15.2f * u, 5.4f * u),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.6f * u, 1.6f * u),
                    style = Stroke(width = sw),
                )
                polyline(6.6f, 10f, 6.6f, 20.4f, 17.4f, 20.4f, 17.4f, 10f)
                line(10.3f, 13.8f, 13.7f, 13.8f)
            }

            Glyph.DELETE -> {
                line(4.6f, 6.7f, 19.4f, 6.7f)
                polyline(9.6f, 6.7f, 9.6f, 4.5f, 14.4f, 4.5f, 14.4f, 6.7f)
                // No ribs inside the bin: at 18dp a 1.9dp stroke needs ~2.4dp
                // of clearance per gap, and the body is only ~6dp across, so
                // two ribs welded into one slab. The taper carries the shape.
                polyline(6.6f, 8.9f, 8.1f, 20.2f, 15.9f, 20.2f, 17.4f, 8.9f)
            }

            // A checkbox: rounded square, then the tick that says one is
            // already inside it. The square is stroked rather than filled
            // because this mark appears in a menu of strokes — a solid slab
            // there would out-shout the label beside it.
            Glyph.SELECT -> {
                drawRoundRect(
                    color = color,
                    topLeft = at(4.2f, 4.2f),
                    size = androidx.compose.ui.geometry.Size(15.6f, 15.6f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.4f, 4.4f),
                    style = Stroke(width = sw),
                )
                polyline(8.3f, 12.3f, 11.1f, 15.1f, 16.2f, 8.9f, width = sw * 1.15f)
            }

            // Cup, handles, stem, foot. The handles are stroked arcs that run
            // into the cup wall rather than floating beside it — a trophy
            // whose handles do not touch reads as a cup with two brackets.
            Glyph.TROPHY -> {
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = at(3.6f, 5.4f),
                    size = androidx.compose.ui.geometry.Size(5.2f * u, 5.2f * u),
                    style = Stroke(width = sw * 0.8f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 270f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = at(15.2f, 5.4f),
                    size = androidx.compose.ui.geometry.Size(5.2f * u, 5.2f * u),
                    style = Stroke(width = sw * 0.8f, cap = StrokeCap.Round),
                )
                filled(6.6f, 4.8f, 17.4f, 4.8f, 16f, 13f, 8f, 13f)
                drawRect(
                    color = color,
                    topLeft = at(11f, 12.6f),
                    size = androidx.compose.ui.geometry.Size(2f * u, 4.2f * u),
                )
                filled(8.4f, 16.6f, 15.6f, 16.6f, 16.8f, 20.6f, 7.2f, 20.6f)
            }

            Glyph.INFO -> {
                // Circle
                drawCircle(
                    color = color,
                    radius = 9f * u,
                    center = at(12f, 12f),
                    style = Stroke(width = sw),
                )
                // Dot
                drawCircle(
                    color = color,
                    radius = 1.2f * u,
                    center = at(12f, 6.5f),
                )
                // Stem
                line(12f, 8.5f, 12f, 16f, width = sw * 1.3f)
            }
        }
    }
}
