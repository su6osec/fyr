package com.fyr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.fyr.R

/**
 * The flame badge — the launcher artwork, reused in-app.
 *
 * Always shown on a black tile rather than straight on the surface. The flame
 * is white, so on a dark screen it would float with no edge and on a light
 * screen it would disappear entirely; the black field is what lets the same
 * asset carry the brand in both themes, and it keeps the in-app mark identical
 * to the launcher one.
 */
@Composable
fun FlameBadge(
    size: Dp,
    modifier: Modifier = Modifier,
    cornerRatio: Float = 0.26f,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(size * cornerRatio))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(size * 0.82f),
        )
    }
}
