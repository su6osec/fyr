package com.fyr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fyr.data.Dates

/**
 * What a long press offers.
 *
 * This used to be a [androidx.compose.material3.DropdownMenu] — a small card
 * that materialised wherever the row happened to be, sometimes over the rows
 * below it, carrying three icon-and-label lines at the density of a context
 * menu nobody reads. It was technically correct and visually foreign: nothing
 * else in Fyr floats.
 *
 * So it is a sheet, which is where a thumb already is. It names the habit at
 * the top — an action list with no subject makes you look back up to remember
 * which habit you pressed — and each action says what it *does* underneath,
 * because "Retire" and "Delete" differ by exactly the thing a nervous user
 * needs to know: one can be undone by opening the habit again, the other
 * cannot. The destructive row sits below a rule and is the only one in red.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitActionsSheet(
    emoji: String,
    name: String,
    since: Int?,
    streak: Int,
    onEdit: () -> Unit,
    onRetire: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(scheme.surfaceContainerHighest),
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // The subject of the menu, in the same two lines the row itself
            // uses, so the sheet reads as the row opening up rather than as a
            // different screen arriving.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Emoji(emoji, size = 30.dp)
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (since != null) {
                        Text(
                            text = "Since ${Dates.shortDate(since)}" +
                                if (streak > 0) {
                                    " · ${if (streak == 1) "1 day" else "$streak days"}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(6.dp))

            ActionRow(
                glyph = Glyph.EDIT,
                label = "Edit",
                detail = "Change the name, days or emoji",
                tint = scheme.primary,
                onClick = onEdit,
            )
            // The door into batch mode, offered only where a list can
            // actually carry it: pressing it ticks this habit and turns the
            // section header into the bar that retires or deletes whatever
            // else gets ticked. Where the caller has no list to select in,
            // the row is simply not composed rather than greyed out.
            if (onSelect != null) {
                ActionRow(
                    glyph = Glyph.SELECT,
                    label = "Select",
                    detail = "Tick, then pick more to act together",
                    tint = scheme.primary,
                    onClick = onSelect,
                )
            }
            ActionRow(
                glyph = Glyph.ARCHIVE,
                label = "Retire",
                detail = "Hide from Today, keep the record",
                tint = scheme.onSurfaceVariant,
                onClick = onRetire,
            )

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = scheme.outlineVariant)
            Spacer(Modifier.height(6.dp))

            ActionRow(
                glyph = Glyph.DELETE,
                label = "Delete",
                // Says the same thing the confirmation a tap later says: the
                // habit goes *to the trash*, recoverable, with its ticks. The
                // old line — "Remove the habit and every tick on it" — read as
                // permanent erasure and contradicted the dialog that actually
                // follows it, which is exactly the wrong moment to learn the
                // two screens disagree.
                detail = "To the trash — nothing erased yet",
                tint = scheme.error,
                onClick = onDelete,
            )

            Spacer(Modifier.height(6.dp))
            HorizontalDivider(color = scheme.outlineVariant)

            // Named, rather than left to the scrim. The sheet's subject is
            // someone's habit, and an escape hatch you can see is worth more
            // than one you have to infer from a dimmed background.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 15.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Cancel",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * One line of the sheet: a mark in a soft disc, the verb, and the consequence.
 *
 * The disc is what makes the three read as a set rather than as three unrelated
 * links, and it carries the colour — red only where red is true — so the
 * destructive row is identifiable before the label is read.
 */
@Composable
private fun ActionRow(
    glyph: Glyph,
    label: String,
    detail: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = if (tint.alpha == 1f) 0.13f else tint.alpha)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = glyph,
                color = tint,
                size = 21.dp,
                strokeWidth = 1.9.dp,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (tint == scheme.error) scheme.error else scheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
