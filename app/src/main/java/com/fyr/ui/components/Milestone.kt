package com.fyr.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
/**
 * The levels.
 *
 * A habit tracker's quiet failure is that nothing ever *lands*: you tick, the
 * number goes up, and the feeling of having arrived somewhere never arrives.
 * These are the moments worth stopping for — chosen the way a game chooses
 * them, dense at the start where a new habit needs the most encouragement and
 * then spaced out so that the tenth trophy is not a routine upgrade of the
 * ninth.
 *
 * The early run is deliberately 5 · 10 · 20: short enough to be reached in the
 * first fortnight, which is when most habits are abandoned. From a month on
 * the labels switch from counting days to counting *calendar* time, because
 * "365 days" is a number and "one year" is an achievement.
 */
data class Milestone(val days: Int, val label: String, val headline: String)

object Milestones {
    val all: List<Milestone> = listOf(
        Milestone(5, "5 days", "First five"),
        Milestone(10, "10 days", "Double digits"),
        Milestone(20, "20 days", "Twenty days"),
        Milestone(30, "1 month", "One month"),
        Milestone(40, "40 days", "Forty days"),
        Milestone(60, "2 months", "Two months"),
        Milestone(90, "3 months", "Three months"),
        Milestone(150, "5 months", "Five months"),
        Milestone(180, "6 months", "Half a year"),
        Milestone(365, "1 year", "One year"),
        Milestone(500, "500 days", "Five hundred"),
        Milestone(730, "2 years", "Two years"),
        Milestone(1095, "3 years", "Three years"),
        Milestone(1825, "5 years", "Five years"),
    )

    /**
     * The level a run of [days] has just reached, or null if it reached none.
     *
     * Exact, not cumulative: ticking two days in one go on a 19-day run earns
     * the 20 and nothing else — a reward that fires three times in one tap
     * reads as a slot machine.
     */
    fun crossed(from: Int, to: Int): Milestone? {
        if (to <= from) return null
        return all.lastOrNull { it.days > from && it.days <= to }
    }

    /** Every level a best run of [best] has ever held — the cabinet. */
    fun held(best: Int): List<Milestone> = all.filter { it.days <= best }

    /**
     * The colour of the tier [days] belongs to — the cabinet's own ramp,
     * shared by every screen that shows a trophy.
     *
     * Every boundary sits *between* two milestones in [all], so no two
     * adjacent levels can ever land on the same colour and the cabinet reads
     * as steps rather than as repeats. The ramp runs amber → ember → green →
     * teal → purple → gold → cyan, which puts the milestone everybody is
     * working toward — a full year — on gold instead of wherever a loose
     * range happened to swallow it. [dark] picks the end of each pair that
     * holds its contrast on that theme.
     */
    fun tierColor(days: Int, dark: Boolean): Color = when (days) {
        in 0..9 -> if (dark) Color(0xFFF5A623) else Color(0xFFE67E22)      // 5 days
        in 10..29 -> if (dark) Color(0xFFE8590C) else Color(0xFFD35400)     // 10, 20 days
        in 30..89 -> if (dark) Color(0xFF7ED321) else Color(0xFF27AE60)     // 1 month, 40, 2 months
        in 90..179 -> if (dark) Color(0xFF50E3C2) else Color(0xFF16A085)    // 3 months, 5 months
        in 180..364 -> if (dark) Color(0xFFBD10E0) else Color(0xFF8E44AD)   // 6 months
        in 365..729 -> if (dark) Color(0xFFFFD600) else Color(0xFFF39C12)   // 1 year, 500 days
        else -> if (dark) Color(0xFF00D4FF) else Color(0xFF2980B9)          // 2, 3, 5 years
    }
}

/** One level, one habit: what the celebration card announces. */
data class MilestoneHit(
    val habitName: String,
    val habitEmoji: String,
    val milestone: Milestone,
)

/**
 * The level-up card.
 *
 * Modelled on the moment a game hands you a trophy: it takes the screen for a
 * beat, says what was earned and by whom, and then gets out of the way. Like
 * the confetti underneath it, it handles no pointers except its own body, so
 * tapping through it to tick the next habit is still possible — the reward is
 * a pause offered, not a toll charged.
 */
@Composable
fun MilestoneCard(
    hit: MilestoneHit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    // The accent as it appears *on* the accent's own tint: the 14%-alpha coin
    // and pill below are light enough that Ember only reaches 4.4:1 against
    // them in the dark theme and 3.0:1 in the light one — under the bar for
    // the 14sp label the pill exists to hold. EmberSoft on that ground is
    // 6.7:1 in the dark theme; in the light theme the accent is already
    // EmberDim (5.8:1), so it stays put. Same fire, one step along the ramp.
    val onTint = if (isDarkTheme()) scheme.secondary else scheme.primary

    var shown by remember(hit) { mutableStateOf(false) }
    LaunchedEffect(hit) {
        shown = true
        delay(MILESTONE_HOLD_MS)
        shown = false
        delay(MILESTONE_FADE_MS)
        onDismiss()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = shown,
            enter = scaleIn(initialScale = 0.84f, animationSpec = tween(300)) +
                fadeIn(tween(220)),
            exit = scaleOut(targetScale = 0.92f, animationSpec = tween(200)) +
                fadeOut(tween(180)),
            modifier = Modifier.padding(horizontal = 30.dp),
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(26.dp))
                    .background(scheme.surfaceContainerHigh)
                    // The card's own body is the only dismiss target: a
                    // full-screen scrim would cost four seconds of taps for
                    // one line of good news.
                    .clickable(onClickLabel = "Dismiss", role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 30.dp, vertical = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.TROPHY,
                        color = onTint,
                        size = 40.dp,
                        strokeWidth = 1.9.dp,
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = hit.milestone.headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "${hit.habitName} reached ${hit.milestone.label}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 13.dp, vertical = 7.dp),
                ) {
                    GlyphIcon(
                        glyph = Glyph.TROPHY,
                        color = onTint,
                        size = 15.dp,
                        strokeWidth = 2.4.dp,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = hit.milestone.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = onTint,
                    )
                }
            }
        }
    }
}

/** How long the card holds before it leaves on its own. */
private const val MILESTONE_HOLD_MS = 3400L
private const val MILESTONE_FADE_MS = 260L
