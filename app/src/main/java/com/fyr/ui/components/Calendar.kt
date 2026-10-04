package com.fyr.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fyr.R
import com.fyr.data.Dates
import com.fyr.data.Habit
import com.fyr.data.Suggestions
import java.time.LocalDate
import kotlin.math.ceil

/**
 * Calendar primitives.
 *
 * The month grid is a *full* month — every day of it — rather than a week
 * pager, because the point of that view is to see a month's shape at a glance.
 * Week mode exists as an opt-in for today's context without the scroll, not as
 * the default.
 */

/** Visual treatment for one day, resolved by the caller from the active theme. */
data class DayStyle(
    val accent: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val track: Color,
)

/**
 * One day: a flame on a completed day, a completion ring on a partial one, an
 * accent wash when selected, and a hairline when it is today.
 *
 * **The number is drawn last, on top of the flame, in a fixed near-black
 * brown.** That ordering is the whole point of the mark: the flame has to be
 * big enough to be unmistakable at 40dp, and the date has to stay readable at
 * a glance, and the only way to have both is to put the date over the fire
 * rather than beside or under it. The brown is a constant rather than a theme
 * ink because it is never read against the background — only against the
 * flame, which is the same orange in both themes.
 *
 * A ring rather than a filled square while the day is partial — the month grid
 * is dense with numbers, so the completion signal has to read as secondary and
 * leave the number primary.
 */
/**
 * A ring rather than a filled square while the day is partial — the month grid
 * is dense with numbers, so the completion signal has to read as secondary and
 * leave the number primary.
 *
 * Skip days (vacation, rest, illness) show the frozen flame instead of a ring.
 * They are not due, don't break streaks, and don't count as missed. The mark
 * is Fyr's own flame drawn in ice rather than a pair of emoji: one shape the
 * same width as every other day's symbol, in the app's own hand.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayCell(
    dayNumber: Int,
    ratio: Float,
    isToday: Boolean,
    isSelected: Boolean,
    style: DayStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ring: Dp = 32.dp,
    flame: Dp = 26.dp,
    enabled: Boolean = true,
    isSkipDay: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val animated by animateFloatAsState(
        targetValue = if (ratio < 0f) 0f else ratio.coerceIn(0f, 1f),
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "dayRing",
    )
    val complete = ratio >= COMPLETE_RATIO

    val background =
        if (isSelected) style.accent.copy(alpha = 0.16f) else Color.Transparent
    val border = when {
        isSelected -> style.accent
        isToday -> style.accent.copy(alpha = 0.55f)
        else -> Color.Transparent
    }

    // A day that has not happened yet is shown, not hidden — you can see the
    // month ahead — but it is dimmed and inert. Recording tomorrow's tick is
    // the one thing a streak tracker must never permit, because it is the
    // mistake that makes the record untrustworthy.
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.34f)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .let { if (border != Color.Transparent) it.border(1.5.dp, border, RoundedCornerShape(11.dp)) else it }
            // `enabled` is handed to the gesture itself rather than to the
            // lambdas: an always-live clickable announces "double tap to
            // activate" through TalkBack for a future day that does nothing
            // when you do, and has no disabled state to say why.
            .combinedClickable(
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            // One node, one announcement, state included. Left to itself
            // TalkBack reads the numeral alone — "15, double tap to
            // activate" — which says nothing about whether the 15th is done,
            // excused, in progress, or not yet real. The month's name is in
            // the header this grid sits under, so it is not repeated per cell.
            .semantics {
                contentDescription = buildString {
                    append("Day $dayNumber")
                    if (isToday) append(", today")
                    if (isSelected) append(", selected")
                    when {
                        isSkipDay -> append(", excused")
                        complete -> append(", done")
                        ratio >= 0f -> append(", in progress")
                        else -> append(", nothing due")
                    }
                    if (!enabled) append(", unavailable")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                // Skip day: the frozen flame, no ring — the day is excused,
                // not due. Drawn art rather than text: it is a mark like the
                // completed flame beside it, and it never wraps or spills.
                isSkipDay -> Image(
                    painter = painterResource(R.drawable.ic_frozen_flame),
                    contentDescription = null,
                    modifier = Modifier.size(flame),
                    contentScale = ContentScale.Fit,
                )

                // A finished day wears the flame and no ring: the ring's job
                // was to say how far along it was, and "finished" does not
                // need a dial.
                complete -> Emoji(Suggestions.FLAME, size = flame)

                // In progress: animated ring.
                ratio >= 0f -> Canvas(Modifier.size(ring)) {
                    val d = size.minDimension
                    val w = 2.5.dp.toPx()
                    val arc = Size(d - w, d - w)
                    val tl = Offset(w / 2f, w / 2f)
                    val stroke = Stroke(width = w, cap = StrokeCap.Round)
                    drawArc(
                        color = style.track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = tl,
                        size = arc,
                        style = stroke,
                    )
                    if (animated > 0.001f) {
                        drawArc(
                            color = style.accent,
                            startAngle = -90f,
                            sweepAngle = 360f * animated,
                            useCenter = false,
                            topLeft = tl,
                            size = arc,
                            style = stroke,
                        )
                    }
                }
            }

            // Composed after the mark above, so it lands over it.
            Text(
                text = dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (complete || isToday || isSelected || isSkipDay) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                color = when {
                    isSkipDay -> FROZEN_INK
                    complete -> FLAME_INK
                    isSelected -> style.ink
                    isToday -> style.accent
                    ratio >= 0f -> style.ink
                    else -> style.muted
                },
            )
        }
    }
}

/** 1.0 with room for float error: a day fully recorded by everything due. */
private const val COMPLETE_RATIO = 0.999f

/** Read only against the flame, so it never has to agree with either theme. */
private val FLAME_INK = Color(0xFF2E1200)

/**
 * Read only against the frozen flame: dark ice over pale frost, the same
 * navy in both themes, exactly as [FLAME_INK] is the same brown.
 */
private val FROZEN_INK = Color(0xFF0A2B45)

/** Seven weekday letters, aligned to the columns beneath them. */
@Composable
private fun WeekdayHeader(style: DayStyle, weekStart: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Dates.weekOrder(weekStart).forEach { isoDay ->
            Text(
                text = Dates.weekdayName(isoDay, narrow = true),
                style = MaterialTheme.typography.labelSmall,
                color = style.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A complete calendar month, including the empty slots before the 1st. */
@Composable
fun MonthGrid(
    month: LocalDate,
    today: Int,
    selectedDay: Int,
    ratios: Map<Int, Float>,
    style: DayStyle,
    weekStart: Int,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    habit: Habit? = null,
    onDayLongClick: ((Int) -> Unit)? = null,
    frozenDays: Set<Int> = emptySet(),
) {
    val first = month.withDayOfMonth(1)
    val length = month.lengthOfMonth()
    // Which column the 1st lands in depends only on how far its weekday sits
    // past the chosen week origin — Monday-start and Sunday-start months share
    // every other line of this function.
    val lead = Dates.leadDays(Dates.firstWeekdayOf(first), weekStart)
    val rows = ceil((lead + length) / 7.0).toInt()

    Column(modifier = modifier) {
        WeekdayHeader(style, weekStart)
        Spacer(Modifier.height(6.dp))

        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val offset = r * 7 + c - lead
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            // A row, not a square. The month grid used to be
                            // built from cells as tall as they were wide, which
                            // put six of them at four hundred dp — most of the
                            // screen, for numbers people read in one sweep. At
                            // this height the whole month still fits above the
                            // day detail without a scroll.
                            .height(MONTH_ROW_HEIGHT),
                    ) {
                        if (offset in 0 until length) {
                            val day = Dates.epochDay(first.plusDays(offset.toLong()))
                            val live = day <= today
                            // Excused two ways: this habit let go of the day
                            // (the detail screen's own grid), or the day carries
                            // an excuse from *some* live habit (the all-habits
                            // calendar — the aggregate can only answer "was
                            // anything let go from here?", and it answers it the
                            // same way the day detail's frozen count does).
                            val isSkip = day <= today &&
                                (habit?.isSkipDay(day) == true || day in frozenDays)
                            DayCell(
                                dayNumber = offset + 1,
                                ratio = if (live) ratios[day] ?: -1f else -1f,
                                isToday = day == today,
                                isSelected = day == selectedDay,
                                style = style,
                                onClick = { onDayClick(day) },
                                enabled = live,
                                modifier = Modifier.fillMaxSize(),
                                ring = 24.dp,
                                isSkipDay = isSkip,
                                onLongClick = onDayLongClick?.let { l -> { l(day) } } ?: { /* no-op */ },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One row of the month grid — short enough that six of them still scroll-free. */
private val MONTH_ROW_HEIGHT = 40.dp

/**
 * Drag left or right over a calendar and the month steps with you.
 *
 * Deliberately a *step* rather than a free scroll: the destination is a
 * discrete value, and a gesture that dragged it continuously would leave you
 * somewhere between two months. 44dp is a thumb's width — deliberate enough
 * that tapping a day cell never reads as a step, short enough that a flick
 * across one cell lands the step.
 *
 * **One slide, one month, always.** The first threshold crossing spends the
 * gesture: whatever distance the finger then travels is the follow-through
 * of the same decision, not three more of them — a flick used to burn
 * through half a year before the thumb let go, and a month is not a page you
 * can be thrown past. The arrows (and the next slide) still take you
 * further, when you mean it.
 *
 * Scoped by its callers to the calendar card alone. Anything it does not see
 * — the day detail below, the headers above, any other tab — falls through
 * to whatever gesture owns it there.
 *
 * A composable because the gesture outlives the recompositions that change
 * which month it steps through: [onStep] is read through a snapshot state,
 * so the handler never navigates with a stale month.
 */
@Composable
fun Modifier.swipeStep(
    threshold: Dp = 44.dp,
    onStep: (Int) -> Unit,
): Modifier {
    val step by rememberUpdatedState(onStep)

    return this.pointerInput(Unit) {
        var accumulated = 0f
        var stepped = false
        val limit = threshold.toPx()
        detectHorizontalDragGestures(
            onDragStart = {
                accumulated = 0f
                stepped = false
            },
            onDragEnd = { accumulated = 0f },
            onDragCancel = { accumulated = 0f },
        ) { change, amount ->
            change.consume()
            if (stepped) return@detectHorizontalDragGestures
            // Finger travels left → the date moves forward, as it would if the
            // grid itself were being pushed off to the left.
            accumulated += amount
            if (accumulated <= -limit) {
                step(1)
                stepped = true
            } else if (accumulated >= limit) {
                step(-1)
                stepped = true
            }
        }
    }
}

/**
 * A whole month at a glance, sized for a form rather than for browsing.
 *
 * The add sheet needs exactly one answer — which day the streak started — and
 * so this strips everything the screen version carries: no completion rings, no
 * selection wash beyond the chosen day, no week switch. A real month grid
 * instead of a strip of recent dates, because start dates cluster on the 1st
 * and a rolling week can never reach it.
 *
 * Days after [today] stay visible but inert, and are never a rail the picker
 * can slide past: recording tomorrow's start is the same forgery as recording
 * tomorrow's tick.
 */
@Composable
fun MiniCalendar(
    month: LocalDate,
    today: Int,
    selectedDay: Int,
    style: DayStyle,
    weekStart: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = month.withDayOfMonth(1)
    val length = month.lengthOfMonth()
    val lead = Dates.leadDays(Dates.firstWeekdayOf(first), weekStart)
    val rows = ceil((lead + length) / 7.0).toInt()
    val title = runCatching { Dates.monthName(month, short = false) }.getOrDefault("")

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = style.ink,
                modifier = Modifier.weight(1f),
            )
            MiniArrow(Glyph.CHEVRON_LEFT, onPrev, style, enabled = prevEnabled)
            Spacer(Modifier.width(2.dp))
            MiniArrow(Glyph.CHEVRON_RIGHT, onNext, style, enabled = nextEnabled)
        }

        Spacer(Modifier.height(6.dp))
        WeekdayHeader(style, weekStart)

        for (r in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (c in 0..6) {
                    val offset = r * 7 + c - lead
                    if (offset in 0 until length) {
                        val day = Dates.epochDay(first.plusDays(offset.toLong()))
                        val live = day <= today
                        // The whole column is the target and the mark inside it
                        // is a small circle: a disc stretched to the cell's
                        // width came out as an ellipse, which reads as a bug at
                        // this size. Short rows keep the month compact while
                        // the tap stays a comfortable 34 × 48.
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(MINI_ROW_HEIGHT)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = live) { onDayClick(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(MINI_DAY_SIZE)
                                    .clip(CircleShape)
                                    .background(
                                        if (day == selectedDay) style.accent
                                        else Color.Transparent,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = (offset + 1).toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (day == selectedDay || day == today) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = when {
                                        day == selectedDay -> Color.White
                                        !live -> style.faint
                                        day == today -> style.accent
                                        else -> style.ink
                                    },
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One row of the inline calendar: a month and a half of vertical budget. */
private val MINI_ROW_HEIGHT = 34.dp

/** The disc drawn inside that row — a circle, never the ellipse a full cell is. */
private val MINI_DAY_SIZE = 30.dp

/** The ‹ › that steps [MiniCalendar] by month: a 38dp target drawn small. */
@Composable
private fun MiniArrow(glyph: Glyph, onClick: () -> Unit, style: DayStyle, enabled: Boolean = true) {
    val forward = glyph == Glyph.CHEVRON_RIGHT
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = if (forward) "Next month" else "Previous month"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(style.track.copy(alpha = if (enabled) 1f else 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = glyph,
                // Dimmed explicitly now that [DayStyle.faint] is a readable
                // colour: a control that is off should still *look* off, and
                // that is a graphic, not text, so alpha costs nothing here.
                color = if (enabled) style.ink else style.faint.copy(alpha = 0.45f),
                size = 13.dp,
                strokeWidth = 1.8.dp,
            )
        }
    }
}

/**
 * The seven days of the week containing [anchorDay].
 *
 * Budgeted to the same numbers as a row of the month grid — 40dp of height,
 * a 24dp mark inside it — because the two views are the same object at two
 * zoom levels and a week that costs 66dp for seven cells made switching to it
 * feel like a change of format rather than a change of scope.
 */
@Composable
fun WeekStrip(
    anchorDay: Int,
    today: Int,
    selectedDay: Int,
    ratios: Map<Int, Float>,
    style: DayStyle,
    weekStart: Int,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val opening = Dates.weekStartOf(anchorDay, weekStart)

    Column(modifier = modifier) {
        WeekdayHeader(style, weekStart)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0..6) {
                val day = opening + i
                val live = day <= today
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(WEEK_ROW_HEIGHT),
                ) {
                    DayCell(
                        dayNumber = Dates.of(day).dayOfMonth,
                        ratio = if (live) ratios[day] ?: -1f else -1f,
                        isToday = day == today,
                        isSelected = day == selectedDay,
                        style = style,
                        onClick = { onDayClick(day) },
                        enabled = live,
                        modifier = Modifier.fillMaxSize(),
                        ring = 24.dp,
                    )
                }
            }
        }
    }
}

/** One row of the week strip — the same budget as a row of the month. */
private val WEEK_ROW_HEIGHT = 40.dp

/**
 * One option of a segmented switch, marked by a ring rather than by a fill.
 *
 * The fill it used to carry sat *below* the grey track in dark mode — the
 * active segment was the darker of the two, so the setting read as pressed-in
 * rather than as chosen. The track is the app's own rim grey, so the selected
 * option now wears that grey as a hairline instead: a mark that survives both
 * themes, on a neighbour that carries none. The label finishes the job — ink
 * for the option taken, muted for the option that was not.
 */
@Composable
fun SegmentedOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val ring = scheme.primary.copy(alpha = 0.55f)
    val contentColor =
        if (selected) scheme.primary else scheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(CircleShape)
            .let { base ->
                if (selected) base.border(1.5.dp, ring, CircleShape) else base
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
