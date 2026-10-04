package com.fyr.ui.habit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fyr.data.Dates
import com.fyr.data.FyrState
import com.fyr.data.Habit
import com.fyr.data.Suggestions
import com.fyr.domain.Stats
import com.fyr.ui.components.Emoji
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.Heatmap
import com.fyr.ui.components.MonthGrid
import com.fyr.ui.components.StreakSeal
import com.fyr.ui.components.rememberDayStyle
import com.fyr.ui.components.rememberHeatColors
import com.fyr.ui.components.swipeStep
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One habit, in full.
 *
 * The current streak sits inside the ring because that is the number people
 * come here for; the arc around it is the 30-day completion rate, named in the
 * caption beneath so the two never get mistaken for each other. Rate and streak
 * are always shown together — the streak is motivating, the rate is the one
 * that tells the truth about whether the habit is working.
 *
 * The month grid and the heatmap here both isolate *this* habit. Showing every
 * habit's traffic on a single habit's page would make its own record unreadable.
 */
@Composable
fun HabitDetailScreen(
    habitId: String,
    state: FyrState,
    today: Int,
    openEdit: Boolean,
    onBack: () -> Unit,
    onToggle: (habitId: String, day: Int) -> Unit,
    onSave: (habit: Habit) -> Unit,
    onArchive: (habitId: String, archived: Boolean) -> Unit,
    onDelete: (habitId: String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dayStyle = rememberDayStyle()
    val heatColors = rememberHeatColors()

    val habit = state.habits.firstOrNull { it.id == habitId }

    if (habit == null) {
        // Deleted from under us — leave rather than render a tombstone. Fired
        // from an effect because navigation mutates the back stack, and that
        // must not happen during composition.
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
        LaunchedEffect(habitId) { onBack() }
        return
    }

    val stats = remember(state, habit, today) { Stats.statsFor(state, habit, today) }
    val archived = habit.archived

    var monthAnchor by rememberSaveable(habitId) { mutableStateOf(today) }
    // Midnight under an open detail: the month only follows the day when it
    // is still the *current* month. Someone reading last March at 23:50 keeps
    // last March; the month they are living in moves with the date, exactly
    // as the heat strip below it does — one screen never shows two verdicts
    // about which month it is.
    var anchorWasDay by remember { mutableStateOf(today) }
    LaunchedEffect(today) {
        if (Dates.startOfMonth(monthAnchor) == Dates.startOfMonth(anchorWasDay)) {
            monthAnchor = today
        }
        anchorWasDay = today
    }
    // Saveable so a rotation does not quietly close the dialog mid-edit — the
    // user loses the field they were in otherwise, which reads as a crash.
    var editing by rememberSaveable(habitId) { mutableStateOf(openEdit) }
    var confirmingDelete by rememberSaveable(habitId) { mutableStateOf(false) }

    val month = remember(monthAnchor) { Dates.startOfMonth(monthAnchor) }
    val monthFirst = Dates.epochDay(month)
    val monthLast = monthFirst + Dates.lengthOfMonth(month) - 1
    val habitRatios = remember(state, habit, monthFirst, monthLast) {
        (monthFirst..monthLast).associate { day ->
            day to Stats.habitRatio(state, habit, day)
        }
    }
    val heatCells = remember(state, habit, today) {
        Stats.heatmapFor(state, habit, today, 26, state.weekStartDay)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── back ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.CHEVRON_LEFT, color = scheme.onSurfaceVariant, size = 22.dp)
        }

        Spacer(Modifier.height(14.dp))

        // ── hero ────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Emoji(habit.emoji, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append(habit.category.label)
                        if (archived) append("  ·  retired")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        // ── ring + streak ───────────────────────────────────────────────
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StreakSeal(
                streak = stats.currentStreak,
                fraction = if (stats.rate30 < 0f) 0f else stats.rate30,
                size = 168.dp,
                stroke = 9.dp,
                accent = scheme.primary,
                track = scheme.outlineVariant,
                ink = scheme.onBackground,
                muted = scheme.onSurfaceVariant,
                unit = "days",
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = buildString {
                    append("Current streak")
                    if (archived) append(" · ended")
                },
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${Stats.percent(stats.rate30)} over the last 30 days",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── supporting numbers ──────────────────────────────────────────
        // Drawn as the Insights headline card's twin: one surface, ember
        // figures, hairline dividers — because these *are* the same object
        // as that card, the habit-sized version of the same four numbers.
        // Four rather than three: frozen days used to exist only as ice in
        // the graph below, and a fact a person has to count squares to
        // answer is a fact the card should be answering itself.
        val frozenCount = remember(state, habit, today) { Stats.frozenDays(habit, today) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            StatCell("Best", "${stats.bestStreak}", Modifier.weight(1f))
            StatDivider()
            StatCell("Done", "${stats.total}", Modifier.weight(1f))
            StatDivider()
            StatCell("Frozen", "$frozenCount", Modifier.weight(1f))
            StatDivider()
            StatCell("All time", Stats.percent(stats.rateAll), Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Since ${Dates.dayTitle(habit.createdAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
        )

        // ── time away ───────────────────────────────────────────────────
        // A streak reset says that *something* stopped. It never says when,
        // or for how long, and "streak: 1" tells nobody that the reason was
        // three weeks in bed. This is the interruption itself — the dates it
        // spanned and how many days it ran — which is the part a person
        // actually remembers about their own absence.
        //
        // Derived from the ticks rather than recorded: the ticks already hold
        // every one of these facts, and a second editable copy of the same
        // truth is how the two start disagreeing.
        if (today - habit.createdAt >= Stats.MIN_AWAY_DAYS) {
            val awayList = remember(state, today) {
                Stats.aways(habit, state.completionsFor(habit.id), today)
            }

            Spacer(Modifier.height(26.dp))
            Text(
                text = "Time away",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surface)
                    .padding(
                        horizontal = 14.dp,
                        // The rows carry their own vertical padding; the
                        // empty state has none to borrow from.
                        vertical = if (awayList.isEmpty()) 14.dp else 2.dp,
                    ),
            ) {
                if (awayList.isEmpty()) {
                    Text(
                        text = "Nothing longer than two days so far.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    awayList.take(MAX_AWAY_ROWS).forEachIndexed { index, away ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(scheme.surfaceContainerHighest),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 13.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = awayRange(away.from, away.to),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurface,
                                )
                                if (away.ongoing) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "Away right now",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = "${away.days} days",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (awayList.size > MAX_AWAY_ROWS) {
                        Text(
                            text = "${awayList.size - MAX_AWAY_ROWS} earlier, not shown",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 11.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── this habit's month ──────────────────────────────────────────
        // The card answers to a thumb-slide the way the Calendar tab's own
        // card does — same 44dp to commit, same one slide, one month — so
        // the app's two calendars are browsed with a single gesture. The
        // forward cap is the arrow's: no slide walks into a month that has
        // not happened yet, and backward runs as far as the arrows do.
        Column(
            modifier = Modifier.swipeStep { delta ->
                val forward = month.withDayOfMonth(1) < Dates.startOfMonth(today)
                when {
                    delta > 0 && forward -> monthAnchor = Dates.epochDay(month.plusMonths(1))
                    delta < 0 -> monthAnchor = Dates.epochDay(month.minusMonths(1))
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = Dates.monthTitle(month),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                MonthArrow(Glyph.CHEVRON_LEFT) {
                    monthAnchor = Dates.epochDay(month.minusMonths(1))
                }
                // Capped at the month this habit has records in, exactly as the
                // Calendar tab caps its own forward step. Without the cap, ‹ was
                // live and led into month after month of permanently empty grid —
                // a control that goes somewhere there is nothing to see should
                // say so before it is pressed, not after.
                MonthArrow(
                    glyph = Glyph.CHEVRON_RIGHT,
                    enabled = month.withDayOfMonth(1) < Dates.startOfMonth(today),
                ) {
                    monthAnchor = Dates.epochDay(month.plusMonths(1))
                }
            }

            Spacer(Modifier.height(10.dp))

            MonthGrid(
                month = month,
                today = today,
                selectedDay = -1,
                ratios = habitRatios,
                style = dayStyle,
                weekStart = state.weekStartDay,
                onDayClick = { onToggle(habit.id, it) },
                habit = habit,
                onDayLongClick = { day ->
                    // Toggle the one-off skip day. Tested against `skipDays`
                    // specifically: `isSkipDay` also answers true for a *recurring*
                    // skip weekday, and subtracting a day that was never in this
                    // set would silently do nothing — a long press that appears
                    // dead is worse than one that is unavailable.
                    val newSkipDays = if (habit.skipDays.contains(day)) {
                        habit.skipDays - day
                    } else {
                        habit.skipDays + day
                    }
                    onSave(habit.copy(skipDays = newSkipDays))
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── six months, just this habit ─────────────────────────────────
        Text(
            text = "Last 6 months",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        Heatmap(
            cells = heatCells,
            weeks = 26,
            colors = heatColors,
            weekStart = state.weekStartDay,
            // The whole six months must be on screen: the recent weeks are
            // exactly where the streak lives, and a map you have to drag to
            // read is a map nobody checks.
            fitWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(30.dp))

        // ── actions ─────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                label = "Edit",
                onClick = { editing = true },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = if (archived) "Restore" else "Retire",
                onClick = { onArchive(habit.id, !archived) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "Delete",
                destructive = true,
                onClick = { confirmingDelete = true },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = if (archived) {
                "Retired habits stay out of Today; history is kept."
            } else {
                "Retire keeps the record; delete removes it."
            },
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))
    }

    if (editing) {
        EditHabitDialog(
            habit = habit,
            today = today,
            onDismiss = { editing = false },
            onSave = {
                onSave(it)
                editing = false
            },
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = scheme.surfaceContainerHigh,
            title = { Text("Delete ${habit.name}?") },
            text = {
                // Recoverable, and the dialog says so. The old wording promised
                // a permanent loss that is no longer what this button does, and
                // a confirmation that overstates the danger trains people to
                // read past the ones that are real.
                Text("Goes to the trash with its ticks — restorable.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(habit.id)
                }) {
                    Text("Delete", color = scheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text("Keep", color = scheme.onSurfaceVariant)
                }
            },
        )
    }
}

/**
 * How many interruptions a single screenful carries before the rest are
 * summarised. Six fits the card without pushing the calendar off the fold, and
 * a seventh is a pattern rather than an event — by then the number below the
 * card is the more useful reading.
 */
private const val MAX_AWAY_ROWS = 6

/**
 * `21 Sep – 1 Oct`, or `29 Dec 2025 – 3 Jan 2026` when a gap crosses a year,
 * which it can: December absences are the longest ones people take.
 */
private fun awayRange(from: Int, to: Int): String {
    val f = Dates.of(from)
    val t = Dates.of(to)
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    return if (f.year == t.year) {
        "${f.format(dayMonth)} – ${t.format(dayMonth)}"
    } else {
        val full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        "${f.format(full)} – ${t.format(full)}"
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    // Centered column, ember figure over grey word — the shape of the
    // Insights headline's MiniStat, restated here so the two stat rows in
    // the app read as one design at two scales.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The hairline between two [StatCell]s — the Insights card's divider, same weight. */
@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * The ‹ › beside the month name on a habit's page.
 *
 * [enabled] exists because the forward arrow has somewhere to stop: the month
 * the habit's record runs out in. A disabled arrow is the one honest way to
 * say "nothing there yet" without spending a line of text on it.
 */
@Composable
private fun MonthArrow(
    glyph: Glyph,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val forward = glyph == Glyph.CHEVRON_RIGHT
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(enabled = enabled, onClick = onClick)
            // Icon-only controls are silent to a screen reader until they say
            // what they do; "button" alone is not an affordance.
            .semantics {
                contentDescription = if (forward) "Next month" else "Previous month"
            },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph,
            color = if (enabled) scheme.onSurfaceVariant
            else scheme.onSurfaceVariant.copy(alpha = 0.32f),
            size = 18.dp,
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (destructive) scheme.primary.copy(alpha = 0.12f) else scheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (destructive) scheme.primary else scheme.onSurface,
        )
    }
}

/**
 * Rename, re-emoji, and move the streak's start date.
 *
 * The schedule stays out of it: moving days around on an existing habit would
 * silently rewrite which past days were ever due, which is a different and far
 * more destructive edit than the one this dialog is for. The start date *is*
 * offered, because backdating is the common case — you begin a habit on
 * Monday and only think to log it on Thursday.
 *
 * The date is capped at today for the same reason the add sheet caps it: a
 * streak cannot have started tomorrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditHabitDialog(
    habit: Habit,
    today: Int,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // Saveable, all four: a rotation with the dialog open used to bring the
    // dialog *back* over wiped fields — the name typed but not yet saved was
    // simply gone, and the comment above claimed the opposite. The dialog
    // surviving is only half of surviving an edit.
    var name by rememberSaveable(habit.id) { mutableStateOf(habit.name) }
    var emoji by rememberSaveable(habit.id) { mutableStateOf(habit.emoji) }
    var startDay by rememberSaveable(habit.id) { mutableStateOf(habit.createdAt) }
    var showPicker by rememberSaveable(habit.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = { Text("Edit habit") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    singleLine = true,
                    placeholder = { Text("Name") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = scheme.primary,
                        unfocusedBorderColor = scheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Suggestions.palette.take(8).forEach { e ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (e == emoji) scheme.primary.copy(alpha = 0.18f)
                                    else scheme.surfaceContainerHighest
                                )
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center,
                        ) {
                            Emoji(e, size = 22.dp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Started on",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showPicker = true }) {
                        Text(
                            text = if (startDay == today) "Today"
                            else Dates.shortDate(startDay),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary,
                        )
                    }
                }

                Text(
                    text = if (startDay == habit.createdAt) {
                        "Days before this are never counted."
                    } else {
                        "Moving the start re-opens the days in between."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        habit.copy(
                            name = name.trim(),
                            emoji = emoji,
                            createdAt = startDay,
                        )
                    )
                },
            ) {
                Text("Save", color = scheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = scheme.onSurfaceVariant)
            }
        },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = Dates.utcMillis(startDay),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Dates.epochDayOf(utcTimeMillis) <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDay = Dates.epochDayOf(it) }
                    showPicker = false
                }) {
                    Text("Done", color = scheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("Cancel", color = scheme.onSurfaceVariant)
                }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}
