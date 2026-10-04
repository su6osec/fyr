package com.fyr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fyr.R
import com.fyr.data.Theme

/**
 * Fyr renders exactly two schemes, and which one it shows is never a setting.
 * [com.fyr.data.Theme.fromUiMode] reads the system's dark flag straight from
 * the configuration, so flipping the phone's theme flips Fyr with it — there
 * is no stored preference to disagree, and nothing here to pick from. The two
 * schemes below are therefore exhaustive.
 */

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = EmberDim,
    onPrimaryContainer = Color.White,
    secondary = EmberSoft,
    onSecondary = Color.Black,
    background = NightGround,
    onBackground = NightInk,
    surface = NightSurface,
    onSurface = NightInk,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = NightInkMuted,
    surfaceContainerLowest = NightGround,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    // Highest sits one step above High (NightRim), not on top of it. Mapping
    // both to the same tone made every tile, pill and palette cell on a sheet
    // paint the sheet's own colour and vanish.
    surfaceContainerHighest = NightRim,
    outline = NightRim,
    outlineVariant = NightRim,
    inverseSurface = NightInk,
    inverseOnSurface = NightGround,
    scrim = Color.Black,
    // A bin is red because bins are red. Material's default error on dark is
    // a pale pink that at the size of a delete icon reads as white — the one
    // colour a danger control is not allowed to be mistaken for — so danger
    // answers in a true red here, and every dustbin in the app picks it up
    // from this one line.
    error = Color(0xFFFF4D4D),
    onError = Color(0xFF2B0000),
)

private val LightColors = lightColorScheme(
    // EmberDim, not Ember. As *text* — which is what `primary` mostly is in
    // Fyr: an accent on an accent-tinted ground — Ember is 3.58:1 on the
    // near-white sheets, under the 4.5:1 a normal-size label needs. EmberDim
    // is the same fire one step down the ramp at 5.82:1, and white on it for
    // the filled CTA improves the same way (3.58 → 5.82). The dark scheme
    // keeps Ember, where it is the text *on* black and already clears the
    // bar at 5.1:1.
    primary = EmberDim,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D3),
    onPrimaryContainer = Color(0xFF5A1E00),
    secondary = Color(0xFF8C3D00),
    onSecondary = Color.White,
    background = DayGround,
    onBackground = DayInk,
    surface = DaySurface,
    onSurface = DayInk,
    surfaceVariant = DaySurfaceHigh,
    onSurfaceVariant = DayInkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DayGround,
    surfaceContainer = DaySurface,
    surfaceContainerHigh = DaySurfaceHigh,
    surfaceContainerHighest = DayRim,
    outline = DayRim,
    outlineVariant = DayRim,
    inverseSurface = DayInk,
    inverseOnSurface = Color.White,
    scrim = Color.Black,
    // The same true red one step deeper for daylight — #D32F2F holds 4.98:1
    // on the near-white sheets, so the bin is legible as red *and* as a word
    // wherever the word appears beside it.
    error = Color(0xFFD32F2F),
    onError = Color.White,
)

@Composable
fun FyrTheme(
    theme: Theme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (theme == Theme.DARK) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

/**
 * Qurova, the display face — the app's one signature, spent on the greeting's
 * own word: the name under "Good afternoon", the welcome's title, the one
 * credit line in About.
 *
 * One word in a second typeface is a signature; two would be a second theme.
 * Everything else stays in the system face so a name reads as something the
 * writer put there rather than as decoration the layout applied. [Ember]
 * carries the accent, Qurova carries the handwriting.
 *
 * Shipped as the demo cut published by Prioritype, which is licensed for
 * **personal use only**. This build is a sideloaded personal app, so that is
 * the licence it sits under; selling Fyr means buying the full family first.
 */
val Qurova = FontFamily(
    Font(R.font.qurova_regular, FontWeight.Normal),
    Font(R.font.qurova_regular, FontWeight.Medium),
    Font(R.font.qurova_semibold, FontWeight.SemiBold),
    Font(R.font.qurova_semibold, FontWeight.Bold),
)

/**
 * Poppins, the profile's voice: the name on its page and the field that
 * edits it. A plain, upright geometric sans, because the profile *states*
 * who — the gold the name used to wear was the band's accent carried down
 * onto the text, and on the page's own ground the name says itself in the
 * page's own colour and lets only the face be different.
 *
 * The Medium cut, listed under both weights the app asks for: the name asks
 * for Medium, the field arrives as Normal, and a family answers with the
 * nearest cut it holds — declaring both keeps either one on Poppins instead
 * of letting the request fall through to the system face.
 *
 * Shipped from Google Fonts under the SIL Open Font License 1.1 — free to
 * use, study, modify and redistribute, including commercially, with no
 * font-specific licence fee.
 */
val Poppins = FontFamily(
    Font(R.font.poppins_medium, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
)
