package com.fyr.ui.trophy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fyr.data.FyrState
import com.fyr.domain.Stats
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.Milestone
import com.fyr.ui.components.Milestones
import com.fyr.ui.components.isDarkTheme

/**
 * The trophy cabinet: every level there is, won and waiting.
 *
 * Built on request as the trophies' own room, and built for the half of the
 * cabinet that did not exist before — the trophies *not yet* earned. A row
 * of chips showing only what you hold tells you nothing you do not know and
 * offers nothing to reach for; a full grid does both at once. Earned tiles
 * wear their tier colour, ring and all; the locked ones sit grey and quiet
 * with the same number underneath, because the streak required is the same
 * fact either way — one is a receipt and the other is an invitation.
 *
 * Above them, the one number that turns the grid into a game: what the next
 * trophy is, and how many days the best run still owes it. The progress
 * line under the count is cabinet completion — of the levels, not of days —
 * because a bar that only ever approaches 100% through patience is exactly
 * the kind of bar a person checks twice a day.
 */
@Composable
fun TrophiesScreen(
    state: FyrState,
    today: Int,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkTheme()

    // The cabinet is read from the record itself — every level any habit's
    // *best* run has ever held — so it can never drift away from the thing
    // that earned it. Break a twenty and the badge stands.
    val earnedDays = remember(state, today) {
        state.habits
            .flatMap { h ->
                Milestones.held(Stats.bestStreak(h, state.completionsFor(h.id), today))
            }
            .map { it.days }
            .toSet()
    }
    val best = remember(state, today) {
        state.habits.maxOfOrNull { Stats.bestStreak(it, state.completionsFor(it.id), today) }
            ?: 0
    }

    val earned = Milestones.all.count { it.days in earnedDays }
    val total = Milestones.all.size
    val next = Milestones.all.firstOrNull { it.days !in earnedDays }
    val daysLeft = next?.days?.minus(best) ?: 0
    val completion by animateFloatAsState(
        targetValue = earned.toFloat() / total,
        animationSpec = tween(600),
        label = "cabinet",
    )

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
        Text(
            text = "Trophy cabinet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$earned of $total earned",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // Cabinet completion. One line, no ceremony — the game part of this
        // screen is the grid below, and the bar's whole job is to be
        // glanced at.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(scheme.outlineVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(completion)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }

        Spacer(Modifier.height(18.dp))

        // ── next up ─────────────────────────────────────────────────────
        // The one tile on this screen aimed forward: what the next trophy
        // is called, and how many days the longest run still owes it.
        val nextColor = if (next == null) {
            if (dark) Color(0xFFFFD600) else Color(0xFFF39C12)
        } else {
            Milestones.tierColor(next.days, dark)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(nextColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.TROPHY, color = nextColor, size = 26.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (next == null) "Cabinet complete" else "Next up",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = nextColor,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = next?.headline ?: "Every trophy earned",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (next == null) {
                        "Nothing left to reach for."
                    } else {
                        "Best run $best · ${daysLeft.toGo()}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (next == null) "$total" else "$daysLeft",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
                Text(
                    text = if (next == null) "of $total" else "days to go",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── the grid ────────────────────────────────────────────────────
        // Three across: the tile needs enough width for its number and its
        // name without shrinking either, and three rows of the ladder fit
        // above the fold this way.
        Milestones.all.chunked(GRID_COLUMNS).forEach { rowMilestones ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowMilestones.forEach { milestone ->
                    TrophyTile(
                        milestone = milestone,
                        earned = milestone.days in earnedDays,
                        dark = dark,
                        modifier = Modifier.weight(1f),
                    )
                }
                // A short final row starts at the left edge like every line
                // of text on the screen; the empty weights hold the tiles
                // to their columns instead of letting them sprawl.
                repeat(GRID_COLUMNS - rowMilestones.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(18.dp))
    }
}

/** "3 days to go", singular-safe. */
private fun Int.toGo(): String = if (this == 1) "1 day to go" else "$this days to go"

private const val GRID_COLUMNS = 3

/**
 * One level of the ladder.
 *
 * The whole card is the state: earned, it wears the tier's colour as a tint
 * with the same colour ringed around it — a lit tile in the wall. Locked, it
 * sits in the theme's quiet grey with the trophy drawn down into the
 * surface. What never changes is the number under the icon: the days it
 * stands for are the same fact in both states, which is precisely what makes
 * the grey ones worth looking at.
 */
@Composable
private fun TrophyTile(
    milestone: Milestone,
    earned: Boolean,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tier = Milestones.tierColor(milestone.days, dark)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (earned) tier.copy(alpha = 0.14f) else scheme.surfaceContainerHighest)
            .then(
                if (earned) {
                    Modifier.border(1.dp, tier.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = buildString {
                    append("Trophy, ")
                    append(milestone.label)
                    if (earned) append(", earned") else append(", not earned yet")
                }
            }
            .padding(horizontal = 8.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (earned) tier.copy(alpha = 0.20f) else scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.TROPHY,
                color = if (earned) tier else scheme.onSurfaceVariant.copy(alpha = 0.38f),
                size = 26.dp,
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = milestone.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (earned) tier else scheme.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = milestone.headline,
            style = MaterialTheme.typography.labelSmall,
            color = if (earned) tier.copy(alpha = 0.85f)
            else scheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
