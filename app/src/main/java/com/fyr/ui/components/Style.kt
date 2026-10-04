package com.fyr.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import com.fyr.ui.theme.Heat0
import com.fyr.ui.theme.Heat0Light
import com.fyr.ui.theme.Heat1
import com.fyr.ui.theme.Heat1Light
import com.fyr.ui.theme.Heat2
import com.fyr.ui.theme.Heat2Light
import com.fyr.ui.theme.Heat3
import com.fyr.ui.theme.Heat3Light
import com.fyr.ui.theme.Heat4
import com.fyr.ui.theme.Heat4Light
import com.fyr.ui.theme.HeatFrozen
import com.fyr.ui.theme.HeatFrozenLight
import com.fyr.ui.theme.HeatTrack
import com.fyr.ui.theme.HeatTrackLight

/**
 * Theme wiring for the drawing components.
 *
 * Dark is detected from the resolved background's luminance rather than from
 * `isSystemInDarkTheme()`: reading the scheme that is actually on screen can
 * never disagree with it, whatever decided that scheme.
 */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun rememberDayStyle(): DayStyle {
    val scheme = MaterialTheme.colorScheme
    return DayStyle(
        accent = scheme.primary,
        ink = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        // Full strength. This is the colour of the dates from the month the
        // grid is *not* showing, which are real dates rather than decoration:
        // at 45% alpha they came out at 2.3:1 on the near-black ground and
        // 1.9:1 on the near-white one, so the two months at the edges of a
        // mini calendar were effectively unreadable. The hierarchy still
        // holds — onSurface is near-white/near-black at 17:1 — it is just no
        // longer bought with contrast the text cannot spare.
        faint = scheme.onSurfaceVariant,
        track = scheme.outlineVariant,
    )
}

@Composable
fun rememberHeatColors(): HeatColors {
    val dark = isDarkTheme()
    return if (dark) {
        HeatColors(HeatTrack, listOf(Heat0, Heat1, Heat2, Heat3, Heat4), HeatFrozen)
    } else {
        HeatColors(
            HeatTrackLight,
            listOf(Heat0Light, Heat1Light, Heat2Light, Heat3Light, Heat4Light),
            HeatFrozenLight,
        )
    }
}
