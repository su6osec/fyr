package com.fyr.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One control riding a picture's rim: a circle half over the edge, ringed
 * in whatever ground stands behind the photo so the rim reads as passing
 * *behind* it rather than being cut by it.
 *
 * Shared by the profile editor and the first-launch setup because they are
 * the same affordance on the same circle — a badge that acts on the picture
 * it sits on — and because both show exactly one of itself: the bin when
 * there is a picture to lose, the tray when there is not. A picture that
 * already says *change* by carrying a tray does not need the bin beside it
 * saying "…or lose it" until there is something to lose.
 *
 * The accent one means *change* and takes the ember whole — the picture is
 * the subject of both screens, so its control is the brightest thing on
 * either. The bin is surface grey with the glyph in the app's error colour,
 * because a bin is red, and it dims rather than vanishes when there is
 * nothing to bin: a control that disappears teaches people to hunt for it.
 *
 * [knockout] is the colour of whatever the badge sits *on* — the card in
 * the editor, the page in the setup — so the ring cuts the rim cleanly at
 * any spread the caller puts the badge at.
 */
@Composable
fun PhotoBadge(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    knockout: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    accent -> scheme.primary
                    enabled -> scheme.surfaceContainerHighest
                    else -> scheme.surfaceContainerHighest.copy(alpha = 0.5f)
                },
            )
            .border(
                width = 3.dp,
                color = knockout,
                shape = CircleShape,
            )
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = glyph,
            color = when {
                accent -> scheme.onPrimary
                enabled -> scheme.error
                else -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
            },
            size = 16.dp,
            strokeWidth = 2.dp,
        )
    }
}
