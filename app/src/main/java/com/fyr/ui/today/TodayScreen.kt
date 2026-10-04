package com.fyr.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fyr.data.Dates
import com.fyr.data.FyrState
import com.fyr.domain.Stats
import com.fyr.ui.components.BurningFlame
import com.fyr.ui.components.FlameBadge
import com.fyr.ui.components.FlameFlares
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.HabitRow
import com.fyr.ui.components.Heatmap
import com.fyr.ui.components.rememberHeatColors
import com.fyr.ui.theme.Ember
import com.fyr.ui.theme.EmberSoft
import com.fyr.ui.theme.Qurova
import kotlin.math.PI
import kotlin.math.sin

/**
 * The landing screen.
 *
 * Built to answer three questions in one glance and one tap: what was due
 * today, how much of it is done, and what is the streak. Everything else in the
 * app is reachable from here but never intrudes on it — this screen exists to
 * be finished quickly, not studied.
 *
 * `Top performers` used to live at the foot of this screen. It moved to
 * Insights as a list — the ranking belongs with the rest of the analysis, and
 * the same three cards were being shown twice, once as a carousel here and
 * once as rows there. What this screen keeps is the record itself: the graph,
 * then a title, then the habits.
 */
@Composable
fun TodayScreen(
    state: FyrState,
    today: Int,
    // Bumped by taps on the Today tab: the fire at the top of this page
    // answers each one with a burst of flares, so the tab feels wired to
    // the mark that names the app.
    flameTick: Int,
    onToggle: (habitId: String, day: Int) -> Unit,
    onOpenHabit: (habitId: String) -> Unit,
    onEditHabit: (habitId: String) -> Unit,
    onRetireHabit: (habitId: String) -> Unit,
    onDeleteHabit: (habitId: String) -> Unit,
    onFreezeHabit: (habitId: String, day: Int) -> Unit,
    // The batch doors, taken whole: a pick can land on one habit or on all
    // of them, and both answer to the same undo bar when it does.
    onRetireMany: (habitIds: List<String>) -> Unit,
    onDeleteMany: (habitIds: List<String>) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val due = remember(state, today) { state.dueOn(today) }
    val all = remember(state, today) { Stats.ranked(state, today) }
    // Excused, not gone. A habit let go from today keeps its row — wearing
    // ice — because a row that vanished the moment you froze it would answer
    // "where did it go?" instead of "what happened today?". Due leads,
    // frozen follows: the obligations first, the excuses after them, and
    // neither is allowed to delete the other's record of the day.
    val frozenToday = remember(state, today) {
        state.live.filter { it.isScheduledOn(today) && it.isSkipDay(today) }
    }
    val rows = due + frozenToday

    val scheme = MaterialTheme.colorScheme

    // Batch mode. Entered from the long-press sheet's "Select" — which
    // arrives with the pressed habit already ticked — and left by the close
    // mark on the bar, by either batch action landing, or by nothing at all:
    // an abandoned pick costs one tap to clear and nothing to walk back.
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable(
        stateSaver = Saver(
            save = { it.joinToString(",") },
            restore = { raw -> raw.split(',').filter { id -> id.isNotEmpty() }.toSet() },
        ),
    ) { mutableStateOf(setOf<String>()) }

    fun exitSelection() {
        selecting = false
        selectedIds = emptySet()
    }

    // The wish, written in full straight from the clock: "Good morning",
    // "Good night", nothing between the word and the edge of the line. The
    // addressee is deliberately not part of this sentence — it gets its own
    // line below, in the display face and in ember, so the wish stays a wish
    // and the name becomes a signature rather than a tail on the greeting.
    val greeting = Dates.partOfDay().greeting

    // With nothing tracked, this is not a list that happens to be empty — it
    // is one centred mark with a line of text under it. The header stays where
    // every other screen puts it, and the mark centres against the whole
    // visible area rather than stacking beneath the header: the only content
    // on the screen should not read as though something were pushing it up.
    if (all.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 20.dp),
        ) {
            TodayHeader(
                greeting = greeting,
                date = Dates.longDayTitle(today),
                // The first name only — "Deepanshu", never "Deepanshu
                // Patiala". The profile carries the whole of it; this line
                // is a wish, and a wish says the name people actually use
                // with each other.
                userName = state.userName.substringBefore(' '),
                flares = flameTick,
                // The list gives its first item the LazyColumn's 8.dp top
                // content padding; this header takes the same 8.dp by hand so
                // that creating the first habit moves nothing on screen.
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )
            EmptyState(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            // Insets sit outside the scroller so rows never travel under the
            // status bar; only the trailing breathing room is inside it.
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            TodayHeader(
                greeting = greeting,
                date = Dates.longDayTitle(today),
                // First word only, same as the empty case above — the two
                // headers must agree pixel for pixel.
                userName = state.userName.substringBefore(' '),
                flares = flameTick,
            )
        }

        // ── four months of the record ────────────────────────────────
        // Above the list, not below it: this is the answer to "how have I
        // actually been doing", and burying that under the chores it
        // describes would put it one scroll past where it is needed.
        item {
            ActivityCard(state = state, today = today, weekStart = state.weekStartDay)
        }

        // ── the habits themselves ─────────────────────────────────────
        // A title, not a scoreboard. The "1 of 1 completed" card that used to
        // sit here said the same thing the ticked rows below say already, at
        // four times the height — and on a day when nothing was due it was a
        // card announcing that there was nothing to announce.
        //
        // While a pick runs the header becomes the bar that runs it: how
        // many are in the pick, one mark for all of them, then the two
        // batch doors — the bin in danger red, because it is the bin — and
        // the way out. Same slot, same line; the list never jumps.
        if (selecting) {
            item {
                SelectionBar(
                    count = selectedIds.size,
                    total = rows.size,
                    onAll = {
                        selectedIds = if (rows.isNotEmpty() && rows.all { it.id in selectedIds }) {
                            emptySet()
                        } else {
                            rows.map { it.id }.toSet()
                        }
                    },
                    onRetire = {
                        onRetireMany(selectedIds.toList())
                        exitSelection()
                    },
                    onDelete = {
                        onDeleteMany(selectedIds.toList())
                        exitSelection()
                    },
                    onClose = { exitSelection() },
                )
            }
        } else if (rows.isNotEmpty()) {
            item {
                // Title on the left, how many exist on the far right. The
                // count is of everything ever created, retired included —
                // that is the question ("how many have I taken on?"), where
                // the rows underneath are the question for today ("which of
                // them are waiting?"). Two numbers answering one would have
                // been confusing, so the second one only appears where there
                // is a first to sit beside.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Habits",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${state.habits.size} created",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        // Ember rather than grey: this is a count of what the
                        // user has taken on, and it answers in the same
                        // accent every other number in the app answers in.
                        color = scheme.primary,
                    )
                }
            }
        }

        items(rows, key = { it.id }) { habit ->
            val streak = remember(state, habit.id, today) {
                Stats.currentStreak(habit, state.completionsFor(habit.id), today)
            }
            val since = remember(state, habit.id, today) {
                Stats.streakStart(habit, state.completionsFor(habit.id), today)
            }
            HabitRow(
                emoji = habit.emoji,
                name = habit.name,
                streak = streak,
                done = state.isDone(habit.id, today),
                accent = scheme.primary,
                onClick = { onOpenHabit(habit.id) },
                onToggle = { onToggle(habit.id, today) },
                since = since,
                onEdit = { onEditHabit(habit.id) },
                onRetire = { onRetireHabit(habit.id) },
                onDelete = { onDeleteHabit(habit.id) },
                // Holding the pill excuses today for this habit — the same
                // one-off skip the calendar's long press writes — and the
                // pill answers in ice the moment it lands.
                frozen = habit in frozenToday,
                onFreeze = { onFreezeHabit(habit.id, today) },
                // Select lands from the sheet already holding this row's
                // tick — the press that asked is the press that picked it —
                // and from there every other row adds itself the same way.
                onSelect = {
                    selecting = true
                    selectedIds = selectedIds + habit.id
                },
                selectionMode = selecting,
                selected = habit.id in selectedIds,
                onSelectedToggle = {
                    selectedIds = if (habit.id in selectedIds) {
                        selectedIds - habit.id
                    } else {
                        selectedIds + habit.id
                    }
                },
            )
        }

        if (rows.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(scheme.surface)
                        .padding(horizontal = 18.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // The mark, not the motion. An empty space should sit
                    // still, and this is the same artwork as the launcher
                    // icon, so the screen signs itself.
                    FlameBadge(size = 54.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Everything due today is done.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The header a pick puts in place of the plain title: count, all, act, out.
 *
 * It occupies the exact slot the "Habits / n created" line holds when no
 * pick is running, so starting one moves nothing on screen except the words
 * themselves. The two doors sit disabled but visible while the count is
 * zero — an action that vanishes teaches people to hunt for it, and the
 * count beside them is the reason it is grey.
 *
 * The bin wears the scheme's error colour, which this build pins to a true
 * red in both themes: on a bar this thin, red before the label is read is
 * the entire safety mechanism.
 */
@Composable
private fun SelectionBar(
    count: Int,
    total: Int,
    onAll: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val armed = count > 0
    val allTaken = total > 0 && count == total

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (count == 1) "1 selected" else "$count selected",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
        Spacer(Modifier.weight(1f))

        // All, becoming Clear the moment there is something to clear —
        // one mark doing both halves of its own job, so the bar never
        // grows a second control for the untick.
        Text(
            text = if (allTaken) "Clear" else "All",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onAll)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )

        Spacer(Modifier.width(2.dp))
        BarAction(
            glyph = Glyph.ARCHIVE,
            label = "Retire selected",
            tint = scheme.onSurfaceVariant,
            enabled = armed,
            onClick = onRetire,
        )
        Spacer(Modifier.width(2.dp))
        BarAction(
            glyph = Glyph.DELETE,
            label = "Delete selected",
            tint = scheme.error,
            enabled = armed,
            onClick = onDelete,
        )
        Spacer(Modifier.width(2.dp))
        BarAction(
            glyph = Glyph.CLOSE,
            label = "Leave selection",
            tint = scheme.onSurfaceVariant,
            enabled = true,
            onClick = onClose,
        )
    }
}

/** One icon on the selection bar: dimmed when there is nothing to aim it at. */
@Composable
private fun BarAction(
    glyph: Glyph,
    label: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.34f)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            color = tint,
            size = 21.dp,
            strokeWidth = 1.9.dp,
        )
    }
}

/**
 * The animated logo, and three lines: the wish, the name it is addressed to,
 * and the day it belongs to.
 *
 * The flame leads because it is the app's mark and this is the app's home —
 * the same fire that signs the launcher and the cold start, kept in motion
 * here so the screen opens already lit. It is the one decorative thing on a
 * page that is otherwise answers, and it costs a single shared animation.
 *
 * What arrives here as [userName] is the stored name's first word: the
 * profile carries the whole of it, but the greeting is a wish, and a wish
 * says the name people actually use with each other — "Deepanshu", not
 * "Deepanshu Patiala" spelled out under the good morning.
 *
 * The name has its own line, and that is the whole answer to overflow. Beside
 * the greeting it was a coin flip — "Good afternoon" plus twelve characters of
 * a display face leaves the row a handful of pixels either side of full — so
 * it lived in a chip that had to ellipsize to survive the collision. Stacked,
 * the name owns the full measure and the limit is honoured by the typeface
 * rather than by the apology of an ellipsis.
 *
 * The name is *shown* in lowercase regardless of how it was written. What is
 * stored stays exactly as typed — a signature is not corrected on the way to
 * the disk — but lowercase is the register of a greeting rather than of a
 * heading, and Qurova's capitals are the glyphs with the least room to
 * negotiate a twelve-character line.
 *
 * Its own composable because this screen has two bodies — a list, and the
 * empty case — and both have to open with the same top of screen, pixel for
 * pixel, or the screen would jump when the first habit is created.
 */
@Composable
private fun TodayHeader(
    greeting: String,
    date: String,
    userName: String,
    flares: Int,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    // Two ways to stoke the same fire, summed into one trigger: ticks from
    // the Today tab arrive from outside, taps on the flame land here. Each
    // one throws a burst of embers *and* swells the flame, so poking the
    // fire still feels answered — and the answering is the hand's to do,
    // never the fire's own.
    var taps by remember { mutableStateOf(0) }
    val beat = flares + taps

    // The logo's own single burst: one welcome when the header first draws,
    // then only beats — tab taps and pokes — restart the embers.
    LaunchedEffect(Unit) {
        if (beat == 0) taps += 1
    }

    // The puff: a swallow and release under the embers, so the fire looks
    // *pushed* rather than merely emitting. Its progress is read in the
    // layer block below, so the pulse redraws the flame without ever
    // recomposing it.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(beat) {
        if (beat <= 0) return@LaunchedEffect
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(460))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (userName.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    // Display only: the store keeps the name as it was typed.
                    text = userName.lowercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = Qurova,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                    // One line, never wrapped: wrapping would push the date
                    // down by a line and make the header's height depend on
                    // the name, which is the one part of this screen that
                    // never changes.
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // At the end of the line rather than in front of it: the greeting
        // reads first and the mark signs it, which also keeps the text on a
        // single left edge no matter what the flame is doing.
        Spacer(Modifier.width(14.dp))
        // Touchable, because the fire should answer the hand: a tap anywhere
        // on it throws a fresh burst of embers and swells the flame — a
        // swallow and release under them — and the box it lives in has the
        // headroom for it: the burst finishes inside it, so the list can
        // never clip embers mid-flight. No ripple: the puff *is* the
        // feedback, and a rectangle of light around a flame reads as a bug.
        // Named for screen readers, which get nothing visual from it but
        // should still be told the control is there.
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    // sin(π·x): swells through the middle of the pulse and
                    // lands back on rest, so the puff never snaps.
                    val puff = sin(pulse.value * PI.toFloat())
                    scaleX = 1f + 0.13f * puff
                    scaleY = 1f + 0.10f * puff
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = { taps += 1 },
                )
                .semantics { contentDescription = "Stoke the flame" },
            contentAlignment = Alignment.Center,
        ) {
            // Light firey background glow behind the animated flame
            Canvas(modifier = Modifier.size(64.dp)) {
                val radius = this.size.minDimension / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to EmberSoft.copy(alpha = 0.18f),
                        0.5f to Ember.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    ),
                    radius = radius,
                )
            }
            BurningFlame(size = 64.dp, showGlow = true)
            // Composed last: the embers lift off the art, over it. Thrown
            // by the beat only — the header's own welcome, then each press
            // of the Today tab and each poke of the flame.
            FlameFlares(tick = beat, modifier = Modifier.matchParentSize())
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme

    // No top padding of its own: the caller centres this block, and any
    // inset baked in here would drag that centre back up the screen.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlameBadge(size = 92.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Nothing tracked yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add a habit to start your streak.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Four months of the record, set above the list rather than under it.
 *
 * Today used to open with a number and then a list: a great deal of *what*,
 * and no *how have I actually been doing*. This card answers the second
 * question without a tap — months along the top, one square per day, and the
 * window's completion rate as the only figure — so the screen shows the shape
 * of the habit before it shows the habit.
 *
 * Sized differently from the same graph in Insights on purpose. Here it is
 * twenty weeks at a size that still reads from arm's length, standing up;
 * there it is six months at the finest pitch the screen allows, for sitting
 * down with. Small cells and more of them, rather than large ones: the point
 * of this card is the *shape* of the last few months, and a 22dp cell turned three
 * weeks into a screenful.
 */
@Composable
private fun ActivityCard(
    state: FyrState,
    today: Int,
    weekStart: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = rememberHeatColors()
    val cells = remember(state, today, weekStart) {
        Stats.heatmap(state, today, ACTIVITY_WEEKS, weekStart)
    }
    val rate = remember(state, today) {
        Stats.overallRate(state, today, ACTIVITY_WEEKS * 7)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recent activity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Last $ACTIVITY_WEEKS weeks",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Stats.percent(rate.fraction),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
                Text(
                    text = "on time",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Heatmap(
            cells = cells,
            weeks = ACTIVITY_WEEKS,
            colors = colors,
            weekStart = weekStart,
            // Fine cells, solved to the card's width so the whole window is on
            // screen. The one streak people come here to check is in the
            // right-most column; a graph that scrolls it off is a graph that
            // has to be dragged into view every morning. 20 weeks at 14dp is
            // the same width as 14 at 22dp — more history, half the height,
            // and the same read from arm's length.
            cell = 14.dp,
            fitWidth = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** How much history this card shows — five months, all of it on screen. */
private const val ACTIVITY_WEEKS = 20
