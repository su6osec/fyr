package com.fyr.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fyr.data.Dates
import com.fyr.data.FyrState
import com.fyr.domain.Stats
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.FireFreezeStat
import com.fyr.ui.components.FlameBadge
import com.fyr.ui.components.HabitRow
import com.fyr.ui.components.MonthGrid
import com.fyr.ui.components.rememberDayStyle
import com.fyr.ui.components.swipeStep

/**
 * The calendar: the full month, and nothing else.
 *
 * There used to be a Week · Month sub-tab over this grid. It was removed on
 * request — month is the view people actually open this tab for, and a
 * segmented control offering one real destination is furniture. The week a
 * person wants is a thumb-scroll from the month they are already looking at,
 * so the arrows, the swipe and the grid all step by month now.
 *
 * The contribution graph lives in Insights rather than as a second view
 * here.
 *
 * Any day can be tapped to tick or untick, including days in the past. A
 * tracker that only lets you record today forces you to lose a day you meant
 * to log, and the honest record is worth more than a convenient one.
 */
@Composable
fun CalendarScreen(
    state: FyrState,
    today: Int,
    onToggle: (habitId: String, day: Int) -> Unit,
    onOpenHabit: (habitId: String) -> Unit,
    onEditHabit: (habitId: String) -> Unit,
    onRetireHabit: (habitId: String) -> Unit,
    onDeleteHabit: (habitId: String) -> Unit,
    onFreezeHabit: (habitId: String, day: Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var anchorDay by rememberSaveable { mutableStateOf(today) }
    var selectedDay by rememberSaveable { mutableStateOf(today) }
    // Where the *person* has taken the calendar, as opposed to where it was
    // seeded. Until they navigate somewhere themselves, the month and the
    // selection track the day: a tab left open across midnight must not go on
    // showing yesterday's month, yesterday's ring and yesterday's detail
    // while the frozen-day test beside it has already moved on. Someone
    // browsing last March keeps last March — reviewing history is a decision,
    // and the day roll does not overrule a decision.
    var userMoved by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(today) {
        if (!userMoved) {
            anchorDay = today
            selectedDay = today
        }
    }

    val scheme = MaterialTheme.colorScheme
    val dayStyle = rememberDayStyle()
    val weekStart = state.weekStartDay

    val month = remember(anchorDay) { Dates.startOfMonth(anchorDay) }
    val range = remember(anchorDay, month) {
        val first = Dates.epochDay(month)
        first to (first + Dates.lengthOfMonth(month) - 1)
    }
    val ratios = remember(state, range) { Stats.ratios(state, range.first, range.second) }

    // An excuse on the day ices its cell: any live habit scheduled for the
    // day and let go from it — the same test the day detail's frozen count
    // reads, so the month and the strip under it can never disagree about
    // whether a day was frozen. Future cells are exempt: nothing can be
    // excused from a day that has not asked anything yet.
    val frozenDays = remember(state, range, today) {
        (range.first..range.second)
            .filter { day ->
                day <= today && state.live.any { it.isScheduledOn(day) && it.isSkipDay(day) }
            }
            .toSet()
    }

    val canNext = Dates.epochDay(month.plusMonths(1)) <= Dates.epochDay(Dates.startOfMonth(today))
    val canPrev = Dates.epochDay(month) > Dates.epochDay(Dates.startOfMonth(1))

    val step: (Int) -> Unit = { delta ->
        // Step from the *selected day*, not from the 1st of its month.
        // Stepping from month-start meant every press of prev or next put
        // you back on the 1st of another month — a navigation that never
        // lets you leave the first day. The clamp keeps a stepped-into
        // future day inside the present.
        val target = Dates.of(anchorDay).plusMonths(delta.toLong())
        anchorDay = Dates.epochDay(target).coerceAtMost(today)
        selectedDay = anchorDay
        // Navigating to today puts the calendar back under the day's hand; anywhere
        // else it stays where the person left it.
        userMoved = selectedDay != today
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Calendar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
        )

        // The drag lives on the calendar and only on the calendar. A finger
        // that starts on the grid steps the month — exactly one month per
        // slide, whatever the finger then does — a finger that starts
        // anywhere else — the day detail below, the header above — is never
        // claimed here, so it reaches the pager and slides Today → Calendar →
        // Insights as it does on every other tab.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .swipeStep(
                    onStep = { delta ->
                        if (delta > 0 && canNext) step(1)
                        else if (delta < 0 && canPrev) step(-1)
                    },
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonthNavRow(
                title = Dates.monthTitle(month),
                prevEnabled = canPrev,
                nextEnabled = canNext,
                onPrev = { step(-1) },
                onNext = { step(1) },
            )

            MonthGrid(
                month = month,
                today = today,
                selectedDay = selectedDay,
                ratios = ratios,
                style = dayStyle,
                weekStart = weekStart,
                onDayClick = {
                    selectedDay = it
                    userMoved = selectedDay != today
                },
                frozenDays = frozenDays,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        DayDetail(
            state = state,
            today = today,
            selectedDay = selectedDay,
            onToggle = onToggle,
            onOpenHabit = onOpenHabit,
            onEditHabit = onEditHabit,
            onRetireHabit = onRetireHabit,
            onDeleteHabit = onDeleteHabit,
            onFreezeHabit = onFreezeHabit,
        )

        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MonthNavRow(
    title: String,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        NavArrow(Glyph.CHEVRON_LEFT, onPrev, enabled = prevEnabled)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        NavArrow(Glyph.CHEVRON_RIGHT, onNext, enabled = nextEnabled)
    }
}

@Composable
private fun NavArrow(glyph: Glyph, onClick: () -> Unit, enabled: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val forward = glyph == Glyph.CHEVRON_RIGHT
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            // A bare ‹ reads as nothing at all through TalkBack, so it says
            // which way it moves and whether it will move this time — the
            // arrows are dead at the ends of the record.
            .semantics {
                contentDescription = when {
                    !forward && enabled -> "Back"
                    !forward -> "Back, unavailable"
                    enabled -> "Forward"
                    else -> "Forward, unavailable"
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            color = if (enabled) scheme.onSurfaceVariant
            else scheme.onSurfaceVariant.copy(alpha = 0.32f),
            size = 22.dp,
        )
    }
}

/**
 * The selected day's habits, tickable in place.
 *
 * This is where backfilling happens, so it mirrors the Today row exactly —
 * same component, same tap, same one-tap completion, same long press on the
 * body for the menu, and the same hold on the pill to freeze or unfreeze the
 * day. Editing a habit from the day you are looking at is the same action as
 * editing it from today, and there is no reason for the two to be two
 * different doors.
 *
 * Excused habits keep their row: a habit let go from this day is still part
 * of this day's story, and a row that vanished the moment you froze it would
 * answer "where did it go?" instead of "what happened here?". Due rows lead,
 * frozen rows follow — obligations first, excuses after — and the day's ice
 * count in the strip above is exactly how many frozen rows sit below it.
 *
 * Everything below is wrapped in one [Column] on purpose. Without it these
 * children were emitted straight into the caller's scrollable column, which
 * spaces *its own* children 14dp apart — so the 12dp lead-in between the day
 * title and the first row arrived as 14 + 12 + 14 = 40dp of dead air, a
 * band of nothing wide enough to read as a layout fault. One child means one
 * gap above it, and the 12dp inside it stays 12dp.
 */
@Composable
private fun DayDetail(
    state: FyrState,
    today: Int,
    selectedDay: Int,
    onToggle: (habitId: String, day: Int) -> Unit,
    onOpenHabit: (habitId: String) -> Unit,
    onEditHabit: (habitId: String) -> Unit,
    onRetireHabit: (habitId: String) -> Unit,
    onDeleteHabit: (habitId: String) -> Unit,
    onFreezeHabit: (habitId: String, day: Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Nothing about a day that has not happened can be recorded, so the list
    // is empty by construction rather than filtered at each row.
    val future = selectedDay > today
    val due = remember(state, selectedDay, future) {
        if (future) emptyList() else state.dueOn(selectedDay)
    }
    // The day's excuses, in the same pass the strip above counts: scheduled
    // here and let go from here. Disjoint from [due] by definition — a skipped
    // day is not a due one — so the concatenation below needs no dedup.
    val frozenRows = remember(state, selectedDay, future) {
        if (future) {
            emptyList()
        } else {
            state.live.filter { it.isScheduledOn(selectedDay) && it.isSkipDay(selectedDay) }
        }
    }
    val rows = due + frozenRows

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Dates.dayTitle(selectedDay),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
            if (selectedDay == today || future) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (future) scheme.surfaceContainerHighest
                            else scheme.primary.copy(alpha = 0.15f),
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (future) "Upcoming" else "Today",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (future) scheme.onSurfaceVariant else scheme.primary,
                    )
                }
            }
        }

        // The day's two counts, said out loud: how many of its habits were
        // ticked — the fire that carried the day — and how many were
        // excused. The second number is the one colour can only hint at,
        // and on a day where every habit was excused it is the only place
        // the empty list below explains itself. A day with nothing due and
        // nothing frozen earns no card — two zeros over "nothing was due"
        // would be the screen narrating its own emptiness.
        val frozenToday = frozenRows.size
        if (rows.isNotEmpty()) {
            FireFreezeStat(
                fire = due.count { state.isDone(it.id, selectedDay) },
                frozen = frozenToday,
                fireLabel = "ticked",
                frozenLabel = "frozen",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surface)
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FlameBadge(size = 46.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (future) {
                        "This day hasn't happened yet."
                    } else {
                        "Nothing was due on this day."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { habit ->
                    val streak = remember(state, habit.id, today) {
                        Stats.currentStreak(habit, state.completionsFor(habit.id), today)
                    }
                    HabitRow(
                        emoji = habit.emoji,
                        name = habit.name,
                        streak = streak,
                        done = state.isDone(habit.id, selectedDay),
                        accent = scheme.primary,
                        onClick = { onOpenHabit(habit.id) },
                        onToggle = { onToggle(habit.id, selectedDay) },
                        since = remember(state, habit.id, today) {
                            Stats.streakStart(habit, state.completionsFor(habit.id), today)
                        },
                        onEdit = { onEditHabit(habit.id) },
                        onRetire = { onRetireHabit(habit.id) },
                        onDelete = { onDeleteHabit(habit.id) },
                        // Holding the pill excuses the day being looked at —
                        // the same record the grid's own long press writes,
                        // so the day can be frozen from either surface.
                        frozen = habit in frozenRows,
                        onFreeze = { onFreezeHabit(habit.id, selectedDay) },
                    )
                }
            }
        }
    }
}
