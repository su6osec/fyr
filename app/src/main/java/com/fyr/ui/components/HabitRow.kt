package com.fyr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fyr.R
import com.fyr.data.Dates
import com.fyr.data.Suggestions
import com.fyr.ui.components.Emoji
/**
 * One habit, one line, one control that completes it.
 *
 * ── the two halves ────────────────────────────────────────────────────
 *
 * Left, under the name the user chose: **when this run began**. Right, where
 * a tick used to be: **how long the run is**, in words, with the flame. The
 * tick box is gone because it duplicated a number that was already on screen
 * — a circle saying "done" beside a streak saying "5 days" is two answers to
 * one question, and the circle is the uglier of them.
 *
 * The chip on the right *is* the control. That keeps the ergonomics of the
 * old tick — one tap, right-hand side, thumb height — while turning what is
 * pressed into the thing the press produces: press the streak and the streak
 * changes. Tint is the state: ember when today is recorded, grey when it is
 * not, so the eye finds the outstanding row by colour alone.
 *
 * The whole row still opens the habit; only the chip completes it. Splitting
 * those means a mis-tap on the label costs nothing — you land on detail and go
 * back — whereas if the row completed the habit, a stray thumb would quietly
 * falsify the record.
 *
 * A **long press** opens [HabitActionsSheet]: edit, select, retire, delete.
 * The menu only exists when its caller supplies at least one of the offers,
 * so a row drawn where those make no sense is simply a row.
 *
 * A **hold on the streak pill** is neither of those: it freezes the day for
 * this habit — the same one-off skip the habit's own calendar writes with a
 * long press on a day cell — and the pill turns to ice to say the day was
 * excused. The chip claims that hold before the row can open its menu, so a
 * thumb aiming at the streak never summons the action list by accident.
 *
 * In **selection mode** the row changes job rather than shape: a tap ticks
 * the row instead of opening it, the streak chip gives way to a checkbox
 * disc, and long-press keeps working so more habits can be added to the
 * pick from the same gesture that started it. The emoji, the name and the
 * date stay exactly where they were — selection is an overlay on the list,
 * not a second list.
 *
 * The streak reads plainly and uncoloured whether it is 400 or 0. A broken
 * streak is information, and styling it as a warning would put shame in a
 * screen people open every day.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(
    emoji: String,
    name: String,
    streak: Int,
    done: Boolean,
    accent: Color,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    since: Int? = null,
    onEdit: (() -> Unit)? = null,
    onRetire: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onSelect: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onSelectedToggle: (() -> Unit)? = null,
    frozen: Boolean = false,
    onFreeze: (() -> Unit)? = null,
    enableLongPress: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val menuEnabled = onEdit != null || onRetire != null || onDelete != null || onSelect != null
    var menuOpen by remember { mutableStateOf(false) }

    // In selection mode the whole surface is the picker: one tap anywhere
    // ticks or unticks, because hunting for the chip while twenty rows are
    // being picked is a worse trade than losing the door to detail for the
    // duration of the pick.
    val pressed = if (selectionMode && onSelectedToggle != null) {
        onSelectedToggle
    } else {
        onClick
    }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selectionMode && selected) {
                        accent.copy(alpha = 0.10f)
                    } else {
                        scheme.surface
                    },
                )
                // One gesture surface for both: a tap opens, a press holds.
                // combinedClickable supplies the long-press haptic itself, so
                // the menu announces itself by buzzing as it opens.
                .combinedClickable(
                    onClick = pressed,
                    onLongClick = if (menuEnabled && enableLongPress) {
                        {
                            // Long-press during a pick adds to it the same
                            // way the press that started the pick did.
                            if (selectionMode && onSelectedToggle != null) {
                                onSelectedToggle()
                            } else {
                                menuOpen = true
                            }
                        }
                    } else {
                        null
                    },
                )
                .semantics {
                    if (selectionMode) {
                        contentDescription = if (selected) {
                            "$name, selected for batch. Activate to untick."
                        } else {
                            "$name, not selected. Activate to tick."
                        }
                    }
                }
                .padding(horizontal = 14.dp, vertical = 11.dp),
        ) {
            Emoji(emoji, size = 26.dp)

            Spacer(Modifier.width(12.dp))

            // The name, then the date this run started — the answer to "how
            // long has this been going", which is a different question from
            // "how many days" and belongs directly under it.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (since != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "since ${Dates.shortDate(since)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (selectionMode) {
                SelectionTick(
                    selected = selected,
                    name = name,
                    accent = accent,
                    onClick = { onSelectedToggle?.invoke() ?: onClick() },
                )
            } else {
                StreakChip(
                    streak = streak,
                    done = done,
                    frozen = frozen,
                    accent = accent,
                    name = name,
                    onClick = onToggle,
                    onFreeze = onFreeze,
                )
            }
        }

        if (menuEnabled && menuOpen) {
            HabitActionsSheet(
                emoji = emoji,
                name = name,
                since = since,
                streak = streak,
                onEdit = {
                    menuOpen = false
                    onEdit?.invoke()
                },
                onRetire = {
                    menuOpen = false
                    onRetire?.invoke()
                },
                onDelete = {
                    menuOpen = false
                    onDelete?.invoke()
                },
                onSelect = onSelect?.let { select ->
                    {
                        menuOpen = false
                        select()
                    }
                },
                onDismiss = { menuOpen = false },
            )
        }
    }
}

/**
 * The row's control while a pick is running: an empty box waiting, an ember
 * tick when taken.
 *
 * Drawn rather than assembled so both states sit on one 24-grid — the empty
 * state is the same stroked rounded square [Glyph.SELECT] opens the menu
 * with, and the taken state fills it with the app's own accent and a white
 * tick. Ember rather than red or blue because selection is not a verdict;
 * it is the app's normal "this one", the same accent that marks a day done.
 */
@Composable
private fun SelectionTick(
    selected: Boolean,
    name: String,
    accent: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics {
                role = Role.Checkbox
                contentDescription = if (selected) {
                    "$name, selected. Activate to untick."
                } else {
                    "$name, not selected. Activate to tick."
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val u = this.size.width / 24f
            val r = CornerRadius(6.4f * u, 6.4f * u)
            val box = androidx.compose.ui.geometry.Size(18f * u, 18f * u)
            val topLeft = Offset(3f * u, 3f * u)
            if (selected) {
                drawRoundRect(color = accent, topLeft = topLeft, size = box, cornerRadius = r)
                // The tick: two segments, round join, sitting low-left to
                // high-right inside the box with room at every edge.
                drawPath(
                    path = Path().apply {
                        moveTo(7.4f * u, 12.4f * u)
                        lineTo(10.7f * u, 15.6f * u)
                        lineTo(16.6f * u, 8.6f * u)
                    },
                    color = Color.White,
                    style = Stroke(width = 2.3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            } else {
                drawRoundRect(
                    color = scheme.onSurfaceVariant.copy(alpha = 0.42f),
                    topLeft = topLeft,
                    size = box,
                    cornerRadius = r,
                    style = Stroke(width = 2f * u),
                )
            }
        }
    }
}

/**
 * The count and the flame, in one target.
 *
 * One size, everywhere: 104 × 44dp, held whether the run is one day or
 * four figures. The old pill grew with its text, so two rows of one list
 * showed two different controls — "1 day" beside a stub, "46 days" beside
 * a slab — and the eye read the difference as meaning when it was only
 * characters. Fixed, the pills line up like keys on one instrument: what
 * changes is what is printed on them, never how big they are.
 *
 * Printed as a counter: the number stacked over its unit, set large
 * enough to catch without stopping, with the flame coined at the end — a
 * well of surface ringed in the state's own colour, so the glyph sits in
 * a container instead of floating on the fill.
 *
 * A press throws one burst of embers off the coin — the same fire the
 * header makes — and then the pill sits still again: the flare is the
 * record's echo, one per press, not a decoration running all day beside a
 * number people are reading. The flame itself stays a plain glyph with its
 * glow suppressed, because a halo this size inside a pill would spill past
 * the clip and look like a rendering fault.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StreakChip(
    streak: Int,
    done: Boolean,
    frozen: Boolean,
    accent: Color,
    name: String,
    onClick: () -> Unit,
    onFreeze: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    val heatColors = rememberHeatColors()
    val muted = streak <= 0
    // One throw per press: the burst is keyed to its own press, and once
    // it has played the canvas falls silent until the next one.
    var burst by remember { mutableStateOf(0) }

    // The state, resolved once. Every part of the pill — fill, rim, coin,
    // count — reads from these four, so a chip can never wear one state's
    // fill with another state's ink. Ice outranks the flame: a day that
    // was excused reads as excused even if a tick landed on it — the
    // record counts that tick as nothing, and the pill counts it the same
    // way.
    val tint = if (frozen) heatColors.frozen else accent
    val fill = when {
        frozen -> heatColors.frozen.copy(alpha = 0.14f)
        done -> accent.copy(alpha = 0.16f)
        muted -> scheme.surfaceContainerHighest.copy(alpha = 0.55f)
        else -> scheme.surfaceContainerHigh
    }
    val rim = when {
        frozen -> heatColors.frozen.copy(alpha = 0.45f)
        done -> accent.copy(alpha = 0.45f)
        // The pill's edge, in the one colour drawn for edges. In the dark
        // theme the fill already steps off the row — a lighter grey on a
        // darker one — but in the light theme the fill is white on a white
        // row, and the control floated with nothing around it: a number
        // with no container, easy to read as text and easy to miss as a
        // button. The rim costs one hairline and gives the chip the same
        // outline in both lights, so the thing you press looks pressable
        // before you have read it.
        else -> scheme.outlineVariant
    }
    val ink = when {
        frozen -> heatColors.frozen
        done -> accent
        muted -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> scheme.onSurface
    }
    // The unit under the number stays the quiet label in every state —
    // it names the count, it does not compete with it.
    val unitColor = when {
        frozen -> heatColors.frozen.copy(alpha = 0.75f)
        done -> accent.copy(alpha = 0.75f)
        muted -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        else -> scheme.onSurfaceVariant
    }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                // One width for every pill in every list: the count holds
                // the left half at a fixed x, the coin always lands at
                // the same x, and two rows can never show two different
                // controls.
                .width(104.dp)
                .height(44.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(fill)
                .border(
                    width = 1.dp,
                    color = rim,
                    shape = RoundedCornerShape(percent = 50),
                )
                // Two gestures, one surface. A tap is the record — done,
                // undone — and a hold is the excuse: holding the pill
                // freezes this day for this habit, the exact toggle the
                // habit's own calendar performs with a long press on a day
                // cell. Held here the chip claims the pointer before the
                // row's own long press can open the menu — the child wins
                // the gesture — so one hold can either freeze or open the
                // menu, never both. Null where the caller has no day to
                // freeze: the hold then falls through to the row.
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onLongClick = onFreeze,
                    onClick = {
                        burst += 1
                        onClick()
                    },
                )
                // This chip is the only control on the row that changes the
                // record, and it is the only one that says so. Left to itself a
                // screen reader reads "3 days, fire" and calls it a button — with
                // no way to tell a ticked row from an unticked one, which is the
                // entire difference between them. The freeze is spelled out
                // too: a hold that changes the record with no lift and no word
                // would otherwise be a secret between fingers.
                .semantics {
                    role = Role.Button
                    contentDescription = when {
                        frozen -> "$name: today is frozen. Press and hold to unfreeze."
                        done -> "$name: done today. Activate to undo." +
                            if (onFreeze != null) " Press and hold to freeze today." else ""
                        else -> "$name: not done today. Activate to mark it done." +
                            if (onFreeze != null) " Press and hold to freeze today." else ""
                    }
                }
                .padding(horizontal = 10.dp),
        ) {
            // The count, stacked: the number answers, the unit names the
            // answer, and the block holds the left half at one x in every
            // pill so the coin never drifts between rows.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "$streak",
                    fontSize = 17.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = (if (streak == 1) "day" else "days").uppercase(),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.9.sp,
                    fontWeight = FontWeight.Medium,
                    color = unitColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(7.dp))
            // The coin: a well of surface ringed in the state's colour.
            // The well keeps the glyph itself unchanged — the emoji's own
            // art does the burning — while the ring says which state is
            // burning: ember for live and lit, ice for excused, a quiet
            // outline for nothing yet.
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(scheme.surface)
                    .border(
                        width = 1.dp,
                        color = if (muted) {
                            scheme.outlineVariant
                        } else {
                            tint.copy(alpha = if (frozen) 0.55f else 0.40f)
                        },
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (frozen) {
                    // The pill wears the same ice flame the day cells and the
                    // counts wear: one fire, two temperatures, no second symbol
                    // to learn.
                    Image(
                        painter = painterResource(R.drawable.ic_frozen_flame),
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                    )
                } else {
                    Emoji(
                        emoji = Suggestions.FLAME,
                        size = 16.dp,
                        modifier = Modifier.alpha(if (muted) 0.35f else 1f),
                    )
                }
            }
        }

        // The flare field sits *outside* the pill's clip — that clip is
        // what rounds the pill, and it would slice embers in half as they
        // crossed the rim — pinned over the coin at the right end, so the
        // embers lift off the art rather than off the number. It reaches
        // only a few dp past the pill, well inside the row's own inset, so
        // nothing here can be cut by the row either.
        FlameFlares(
            tick = burst,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp),
        )
    }
}
