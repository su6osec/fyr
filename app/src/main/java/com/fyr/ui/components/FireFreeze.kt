package com.fyr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fyr.R
import com.fyr.data.Suggestions

/**
 * Fire and frost, counted out loud.
 *
 * Every screen that colours a flame or an ice-blue square is already
 * carrying these two numbers — how much is burning, how much was excused —
 * but colour is a shape, not a statement, and a shape never answers
 * *"exactly how many"*. This is that answer: two halves, two icons, the
 * figure each in the colour its own half of the record is drawn in.
 *
 * Both halves are the same fire: the burning one in the emoji flame, the
 * excused one in [R.drawable.ic_frozen_flame] — Fyr's own flame redrawn in
 * a frost-to-ice gradient. A snowflake was a weather report standing in for
 * a record; the frozen twin of the mark the streak already wears says
 * *"this fire, excused"* in one shape, which is the whole sentence.
 *
 * The labels are the caller's because the two halves count different things
 * in different places — habits burning *now* beside days frozen *ever* on
 * Insights, ticks landed beside days excused on a single calendar day — and
 * a number whose unit its own card will not name is a number people learn
 * to skip.
 */
@Composable
fun FireFreezeStat(
    fire: Int,
    frozen: Int,
    fireLabel: String,
    frozenLabel: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val heatColors = rememberHeatColors()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        StatHalf(
            icon = {
                Emoji(Suggestions.FLAME, size = 17.dp)
            },
            value = fire,
            label = fireLabel,
            valueColor = scheme.primary,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(38.dp)
                .background(scheme.outlineVariant),
        )
        StatHalf(
            icon = {
                // The flame, frozen over: the same silhouette as the streak
                // beside it, in ice from frost-white crown to deep cold base.
                Image(
                    painter = painterResource(R.drawable.ic_frozen_flame),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
            },
            value = frozen,
            label = frozenLabel,
            valueColor = heatColors.frozen,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatHalf(
    icon: @Composable () -> Unit,
    value: Int,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(7.dp))
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
