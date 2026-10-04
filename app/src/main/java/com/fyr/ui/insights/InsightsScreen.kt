package com.fyr.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import com.fyr.data.FyrState
import com.fyr.data.Suggestions
import com.fyr.domain.Stats
import com.fyr.ui.components.FireFreezeStat
import com.fyr.ui.components.FlameBadge
import com.fyr.ui.components.Emoji
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.Heatmap
import com.fyr.ui.components.Milestones
import com.fyr.ui.components.isDarkTheme
import com.fyr.ui.components.rememberHeatColors
import kotlin.math.roundToInt

/**
 * Where the app earns its name.
 *
 * The tab is organised around two things: the *shape* of the last six months,
 * and a verdict on each habit. A streak measures consistency but resets to
 * nothing on one miss, which makes it a poor answer to "is this habit
 * working" — one bad Monday can erase a quarter. So the rate sits beside the
 * streak on every card here, and the credit for the days that did happen
 * survives the one that did not.
 *
 * The headline carries only the numbers that need no denominator: how far the
 * best run currently goes, how many days have been recorded in total, how
 * many habits those days belong to, and how many days were let go of. Rates
 * have a sample to justify them, so they live on the cards below where each
 * one can show its own.
 */
@Composable
fun InsightsScreen(
    state: FyrState,
    today: Int,
    onOpenHabit: (habitId: String) -> Unit,
    onOpenTrophies: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    val all = remember(state, today) { Stats.ranked(state, today) }
    val leading = remember(all) { all.maxByOrNull { it.currentStreak } }
    val totalCompleted = remember(state) {
        state.live.sumOf { Stats.total(it, state.completionsFor(it.id)) }
    }
    // The other total the headline owes: days let go rather than days
    // recorded. It used to exist only as ice in the squares below — visible,
    // countable, and nobody was going to count it.
    val totalFrozen = remember(state, today) { Stats.frozenDaysTotal(state, today) }
    val top = remember(state, today) { Stats.topPerformers(state, today, limit = 3) }
    val topIds = top.map { it.habit.id }.toSet()
    val attention = remember(state, today, topIds) {
        Stats.needsAttention(state, today, limit = 3, exclude = topIds)
    }
    // Everything these two sections already name. The full list below used to
    // restate them, so a habit could appear twice on one screen — two cards,
    // identical, one under the other.
    val alreadyNamed = remember(top, attention) {
        (top + attention).mapTo(HashSet()) { it.habit.id }
    }
    val rest = remember(all, alreadyNamed) {
        all.filter { it.habit.id !in alreadyNamed }
            .sortedByDescending { it.rate30 }
    }
    val heatColors = rememberHeatColors()
    val weekStart = state.weekStartDay
    val heatCells = remember(state, today, weekStart) {
        Stats.heatmap(state, today, INSIGHT_WEEKS, weekStart)
    }

    if (all.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlameBadge(size = 78.dp)
            Spacer(Modifier.height(22.dp))
            Text(
                text = "No data yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Appears after a few habits and days.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
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
            text = "Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
        )

        // ── the headline ────────────────────────────────────────────────
        // Four numbers, and the 30-day and 90-day percentages that used to
        // make up the rest of this row are gone: they were the same figure
        // counted twice at two depths. What is left is the question a person
        // actually opens this tab to ask — how long has this been going —
        // beside what it is made of. The per-habit rates below carry the
        // detail; this carries the score. "Total ticks" is the denominator
        // of the first two: "51 ticks" means something different depending
        // on whether those ticks belong to two habits or to twenty — and
        // "Frozen days" is the number the ice squares would have handed out
        // only to anyone willing to count them.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            MiniStat(
                label = "Total habits",
                value = "${state.habits.size}",
                modifier = Modifier.weight(1f),
            )
            // Horizontal separator between stats
            Spacer(Modifier.width(1.dp).height(40.dp).background(scheme.outlineVariant))
            MiniStat(
                label = "Longest now",
                value = leading?.currentStreak?.toString() ?: "0",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(1.dp).height(40.dp).background(scheme.outlineVariant))
            MiniStat(
                label = "Total ticks",
                value = "$totalCompleted",
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(1.dp).height(40.dp).background(scheme.outlineVariant))
            MiniStat(
                label = "Frozen days",
                value = "$totalFrozen",
                modifier = Modifier.weight(1f),
            )
        }

        // ── the cabinet, behind one row ────────────────────────────────
        // Every level any habit has ever held, once each. Read from the *best*
        // run rather than from a stored unlock, so the cabinet can never drift
        // away from the record that earned it — break a twenty and the badge
        // still stands, because a thing you did is still a thing you did.
        // Deduplicated for the same reason: "one month" reached on three habits
        // is one achievement held once, not three trophies in three drawers.
        //
        // The trophies themselves moved behind this row on request: earned
        // ones were taking a whole section to tell their holder what they
        // already know, and there was nowhere on the screen for the ones not
        // won yet — which are the half that does the motivating. The door
        // stays open whether the count behind it reads 2 of 14 or 12 of 14,
        // because a locked row is a promise, not an absence.
        val trophies = remember(state, today) {
            state.habits
                .flatMap { h ->
                    Milestones.held(Stats.bestStreak(h, state.completionsFor(h.id), today))
                }
                .distinctBy { it.days }
                .sortedBy { it.days }
        }
        val cabinetGold = if (isDarkTheme()) Color(0xFFFFD600) else Color(0xFFF39C12)
        SectionTitle("Trophies")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .clickable(onClick = onOpenTrophies)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(cabinetGold.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.TROPHY, color = cabinetGold, size = 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Trophy cabinet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${trophies.size} of ${Milestones.all.size} earned",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            GlyphIcon(
                glyph = Glyph.CHEVRON_RIGHT,
                color = scheme.onSurfaceVariant,
                size = 20.dp,
            )
        }

        // ── the last six months ─────────────────────────────────────────
        SectionTitle("Last six months")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            Heatmap(
                cells = heatCells,
                weeks = INSIGHT_WEEKS,
                colors = heatColors,
                weekStart = weekStart,
                // Sized to the card, so the six most recent months are all
                // here rather than half off the right edge.
                fitWidth = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "One square a day — ice-blue means excused.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // ── the ranking, moved here from Today ─────────────────────────
        // It used to be a horizontal carousel on the home screen and a list
        // here, under another name — the same three habits, twice, two screens
        // apart. One place, one shape: rows, which scan and sort the eye
        // downward the way everything else on this tab already does.
        // One line, and only over whichever of these lists comes first.
        //
        // The percentage is the whole point of a ranking card, and nobody
        // has ever been able to read a bare one without asking fifty percent
        // of *what*. This says it out loud, in the two words the rest of the
        // screen already uses — due and ticked — and it says it once rather
        // than under every heading, because a legend repeated three screens
        // apart stops being a legend and starts being noise.
        val rateNote = "Of your due days — how many you ticked."

        if (top.isNotEmpty()) {
            SectionTitle("Top performers", rateNote)
            // Fire and frost, as figures. The flames on the rows below and
            // the ice in the graph above both carry these numbers as
            // *colour*, and colour answers "roughly" — this answers exactly:
            // how many habits are burning right now, and how many days have
            // been let go of across the whole record.
            FireFreezeStat(
                fire = all.count { it.currentStreak > 0 },
                frozen = totalFrozen,
                fireLabel = "on fire now",
                frozenLabel = "days frozen",
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                top.take(3).forEach { s ->
                    RankCard(s, onOpenHabit, positive = true)
                }
            }
        }

        // ── what isn't ──────────────────────────────────────────────────
        if (attention.isNotEmpty()) {
            SectionTitle(
                "Needs attention",
                rateNote.takeIf { top.isEmpty() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                attention.take(3).forEach { s ->
                    RankCard(s, onOpenHabit, positive = false)
                }
            }
        }

        // ── every remaining habit ───────────────────────────────────────
        if (rest.isNotEmpty()) {
            SectionTitle(
                "Every habit",
                rateNote.takeIf { top.isEmpty() && attention.isEmpty() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rest.forEach { s ->
                    RankCard(s, onOpenHabit, positive = s.rate30 >= 0.7f)
                }
            }
        }

        // Deliberately shorter than the 28dp it used to be. The caption
        // above costs a line, and without this the last card would creep
        // under the tab bar by a few pixels — which reads as a bug rather
        // than as a page that has simply ended.
        Spacer(Modifier.height(14.dp))
    }
}

private const val INSIGHT_WEEKS = 26

/**
 * A section heading, optionally with the one line that says what the section
 * is counting.
 *
 * Wrapped in a [Column] so that a caption belongs to the heading rather than
 * to the page — but that changes what the heading *is* to the column around
 * it. It used to be two children, so the parent's 14dp spacing landed both
 * before and after its own 6dp lead-in: 34dp above the words, 14 below. As
 * one child that becomes 20 above, which quietly pulled every section in
 * this screen a finger's width closer to the one before it. The lead-in is
 * 20dp rather than 6dp to put the 34 back; the parent supplies the other 14.
 */
@Composable
private fun SectionTitle(text: String, caption: String? = null) {
    val scheme = MaterialTheme.colorScheme
    Column {
        Spacer(Modifier.height(20.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
        )
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
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

/**
 * One habit's verdict: how many of the days it asked for were answered,
 * then how long the answer has held.
 *
 * The percentage is never shown alone. It is always followed by its own
 * denominator in days, because "53%" is a figure and "16 of 30 days" is a
 * fact — and the fact is the one a person can act on. Trend is gone: two
 * seven-day windows on a two-week-old habit is a number dressed as a
 * signal, and it spent most of its life being negative for reasons that had
 * nothing to do with the habit.
 */
@Composable
private fun RankCard(
    s: Stats.HabitStats,
    onOpenHabit: (String) -> Unit,
    positive: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .clickable { onOpenHabit(s.habit.id) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Emoji(s.habit.emoji, size = 32.dp)
        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = s.habit.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (s.currentStreak > 0) {
                    Emoji(Suggestions.FLAME, size = 15.dp)
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = buildString {
                        append(if (s.currentStreak == 1) "1 day" else "${s.currentStreak} days")
                        if (s.bestStreak > s.currentStreak) {
                            append("  ·  best ${s.bestStreak}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Stats.percent(s.rate30),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (positive) scheme.primary else scheme.onSurface,
            )
            // The percentage, spelled out in days.
            //
            // A bare "80%" invites the question eighty percent of *what*, and
            // the line that used to sit here — "5d sample", "−29 pts" —
            // answered it in a shorthand nobody had agreed to. The count under
            // the figure says the same thing in the only units anyone actually
            // keeps a habit in: days they showed up, out of the days that
            // asked them to.
            Text(
                text = if (s.days30 > 0) {
                    val kept = (s.rate30 * s.days30).roundToInt()
                    val unit = if (kept == 1 && s.days30 == 1) "day" else "days"
                    "$kept of ${s.days30} $unit"
                } else {
                    "nothing due"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}
