package com.fyr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fyr.data.Dates
import com.fyr.domain.Stats

/**
 * The contribution graph: rows run from the week's first day to its last, and
 * intensity is the share of that day's due habits that were completed.
 *
 * Excused (frozen) days are drawn in ice rather than left bare, so a skipped
 * stretch never passes for a stretch where nothing was ever asked — the one
 * thing bare track cannot say on its own.
 *
 * Rendered as a fixed-width row of columns inside a [LazyRow] rather than a
 * generic grid, so column-major data maps onto layout one-to-one — no index
 * arithmetic, no scroll co-ordination to get subtly wrong. Month headers are
 * positioned from the same column widths, so they stay aligned for free.
 *
 * The week's origin is [weekStart] rather than a baked-in Monday: the grid
 * simply lists the days in that order, so switching the setting in Profile
 * re-axes this graph along with every other one.
 *
 * With [fitWidth] the cell size is solved from the measured width instead of
 * being assumed. That is the difference between a six-month map you can read
 * at a glance and one whose most recent weeks — the streak you came to check —
 * sit off the right edge waiting to be dragged into view. The year-long
 * history graph deliberately does not fit: there, scrolling is the point.
 */
@Composable
fun Heatmap(
    cells: List<Stats.HeatCell>,
    weeks: Int,
    colors: HeatColors,
    weekStart: Int,
    modifier: Modifier = Modifier,
    cell: Dp = 13.dp,
    gap: Dp = 3.dp,
    showLegend: Boolean = true,
    fitWidth: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
) {
    val density = LocalDensity.current
    var measuredWidth by remember { mutableStateOf(0) }
    val order = remember(weekStart) { Dates.weekOrder(weekStart) }
    // How much ice the window holds, counted once. The legend prints it and
    // the screen reader says it — the same number in both places, because a
    // caption that counts its own squares while the sentence beside it counts
    // them again is two chances for the two to disagree.
    val frozenCount = remember(cells) { cells.count { it.frozen } }

    // Largest cell that still leaves every week on screen. Never grows past
    // the requested size, so fit mode can only make the graph finer, never
    // louder than the caller asked for.
    val effectiveCell = remember(measuredWidth, cell, gap, weeks, fitWidth) {
        if (!fitWidth || measuredWidth <= 0) return@remember cell
        with(density) {
            val rail = RAIL.toPx()
            val pitch = gap.toPx()
            val available = measuredWidth - rail - pitch * (weeks - 1)
            if (available <= 0f) {
                cell
            } else {
                (available / weeks).toDp().coerceIn(minOf(MIN_CELL, cell), cell)
            }
        }
    }

    val columnPitch = effectiveCell + gap

    // Label a column when its week opens a new month.
    val monthHeaders = remember(cells, weeks) {
        buildList {
            var previous = -1
            for (col in 0 until weeks) {
                val opening = cells[col * 7].day
                val month = runCatching { Dates.of(opening).monthValue }.getOrDefault(-1)
                if (month == -1) continue
                if (col == 0 || month != previous) {
                    add(
                        col to runCatching {
                            Dates.monthName(Dates.of(opening), short = true)
                        }.getOrDefault("")
                    )
                }
                previous = month
            }
        }
    }

    Column(
        modifier = modifier
            .onSizeChanged { measuredWidth = it.width }
            .semantics {
                // The grid is colour and nothing else — no text nodes at all —
                // so a screen reader had no way to report the one data display
                // on the screen. One sentence carries what the squares show:
                // how long the window is, how many of those days were
                // actually completed, and how many were let go.
                val completed = cells.count { it.level > 0 }
                val frozen = frozenCount
                contentDescription = buildString {
                    append("Activity for the last ")
                    append(weeks)
                    append(if (weeks == 1) " week: " else " weeks: ")
                    append(completed)
                    append(" of ")
                    append(cells.size)
                    append(if (cells.size == 1) " day completed." else " days completed.")
                    if (frozen > 0) {
                        append(' ')
                        append(frozen)
                        append(if (frozen == 1) " day frozen." else " days frozen.")
                    }
                }
            },
    ) {
        // ── month headers ────────────────────────────────────────────────
        // Each label owns a fixed span of columns, so its left edge is exactly
        // the left edge of the week it labels regardless of text width.
        Row(modifier = Modifier.padding(start = RAIL)) {
            monthHeaders.forEachIndexed { i, (col, label) ->
                if (i == 0 && col > 0) Spacer(Modifier.width(columnPitch * col))
                val next = monthHeaders.getOrNull(i + 1)?.first ?: weeks
                val span = (next - col).coerceAtLeast(1)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(columnPitch * span),
                )
            }
        }

        Spacer(Modifier.height(gap))

        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── weekday rail: first, third and fifth day only, so it stays quiet
            Column(
                verticalArrangement = Arrangement.spacedBy(gap),
                modifier = Modifier.width(RAIL),
            ) {
                for (row in 0 until 7) {
                    Box(
                        modifier = Modifier.size(effectiveCell),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (row == 0 || row == 2 || row == 4) {
                            Text(
                                text = Dates.weekdayName(order[row], narrow = true),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            LazyRow(
                state = listState,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(weeks) { col ->
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        for (row in 0 until 7) {
                            val cell = cells[col * 7 + row]
                            HeatSquare(
                                color = colors.forCell(cell),
                                edge = effectiveCell,
                            )
                        }
                    }
                }
            }
        }

        if (showLegend) {
            // The ramp is a pattern people already know; the ice swatch is
            // not, so it appears only when there is ice to explain — and it
            // always carries its word *and its count*. A legend that leaves
            // the odd colour out unlabelled is how "frozen" gets read as
            // "nothing happened", and a legend that labels the ice without
            // saying how much of it there is makes the reader count
            // fourteen-pixel squares to answer a question the graph already
            // knows the answer to.
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Less",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                for (level in -1..4) {
                    HeatSquare(
                        color = colors.forLevel(level),
                        edge = 14.dp,
                    )
                    if (level < 4) Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "More",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (frozenCount > 0) {
                    Spacer(Modifier.width(18.dp))
                    HeatSquare(color = colors.frozen, edge = 14.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Frozen · $frozenCount " +
                            if (frozenCount == 1) "day" else "days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * One square of the grid — and of the legend, so the swatch and the tile it
 * stands for are literally the same drawing.
 *
 * Flat, on purpose. A bevelled pass over this box — crown of light, shade at
 * the base — was tried and taken back out: the raised tiles catch the eye,
 * but they turn a quiet record into a field of buttons, and the intensity
 * ramp underneath them is the only thing the grid is trying to say. One
 * colour per day, printed onto the surface rather than pressed out of it.
 */
@Composable
private fun HeatSquare(
    color: Color,
    edge: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(edge)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

/** The weekday rail's fixed slot, shared by the header and the grid. */
private val RAIL = 22.dp

/** Below this a cell stops being a cell and becomes a smudge. */
private val MIN_CELL = 5.dp

/** Heat ramp for one theme, so screens never reach into colour constants. */
class HeatColors(
    private val bare: Color,
    private val levels: List<Color>,
    /** Ice for excused days — deliberately outside the ramp it sits beside. */
    val frozen: Color,
) {
    fun forLevel(level: Int): Color =
        if (level < 0) bare else levels[level.coerceIn(0, levels.lastIndex)]

    fun forCell(cell: Stats.HeatCell): Color =
        if (cell.frozen) frozen else forLevel(cell.level)
}
