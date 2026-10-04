package com.fyr.ui.update

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fyr.BuildConfig
import com.fyr.data.Theme
import com.fyr.ui.components.FlameBadge
import com.fyr.ui.theme.Ember
import com.fyr.ui.theme.EmberSoft
import com.fyr.ui.theme.FyrTheme
import com.fyr.update.UpdateChecker
import kotlinx.coroutines.delay

/**
 * The door the app opens through.
 *
 * Every cold start lands here first, and the shell behind it does not compose
 * until this decides. That ordering is the whole enforcement mechanism: there
 * is no dialog to dismiss, no scrim to tap through, and no route that was
 * already reachable before the answer came back. The three states are
 * [Gate.Checking] — up for the length of one request and never longer than its
 * timeout — [Gate.Blocked], a full screen that takes every pointer, and
 * [Gate.Open], which is the app and is the only state that ever calls
 * [content].
 *
 * A build already carrying a pending verdict from [UpdateChecker] starts in
 * [Gate.Blocked] rather than [Gate.Checking], so the block answers before the
 * network does. The request still runs, because it is the request that lets a
 * retraction through: a record the running build has outgrown is dropped and
 * the door opens again, offline or not.
 *
 * Failure is an open door, always. A train, a captive portal, a CDN having a
 * morning — none of those are reasons for a habit tracker to refuse to start,
 * and the only thing the fail-open path costs is one unnoticed update notice,
 * which the next launch will carry anyway.
 */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val theme = Theme.fromUiMode(LocalConfiguration.current.uiMode)

    // Synchronous, deliberately: the record is one small preference read, and
    // reading it here is what lets the door start closed instead of flashing
    // the app while the request is still out.
    val flagged = remember(context) { UpdateChecker.pending(context) }
    val alreadyBehind = flagged != null && flagged.versionCode > BuildConfig.VERSION_CODE

    var gate by remember(context) {
        mutableStateOf(if (alreadyBehind) Gate.Blocked(flagged) else Gate.Checking)
    }

    LaunchedEffect(context) {
        gate = when (val result = UpdateChecker.check(context)) {
            is UpdateChecker.Result.Required -> Gate.Blocked(result.release)

            UpdateChecker.Result.Current -> Gate.Open

            // No answer. Whatever is on the ground stands — and what is on
            // the ground only ever stands for a build it still outranks.
            UpdateChecker.Result.Unreachable ->
                if (alreadyBehind) Gate.Blocked(flagged!!) else Gate.Open
        }
    }

    when (val current = gate) {
        Gate.Checking -> FyrTheme(theme) { CheckingScreen() }
        Gate.Open -> content()
        is Gate.Blocked -> FyrTheme(theme) { UpdateRequiredScreen(current.release) }
    }
}

/** The three positions of the door. */
private sealed interface Gate {
    data object Checking : Gate
    data object Open : Gate
    data class Blocked(val release: UpdateChecker.Release) : Gate
}

/**
 * The wait.
 *
 * Deliberately almost empty: on a good connection this is on screen for a few
 * hundred milliseconds — the window appearing, then the launch logo — and a
 * screen that *announces* itself at that speed reads as a stutter rather than
 * as information. So it shows nothing until it has been up long enough to owe
 * the person an explanation, and only then admits it is doing anything.
 */
@Composable
private fun CheckingScreen() {
    val scheme = MaterialTheme.colorScheme
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SLOW_HINT_MS)
        slow = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = slow,
            enter = fadeIn(tween(320)),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.navigationBarsPadding(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Ember,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Checking for updates…",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The block.
 *
 * A full screen rather than a dialog, because a dialog is a shape you can miss
 * the edges of: this one has no scrim to tap, no back to press, and no gesture
 * that gets past it. Back is swallowed for exactly that reason — the only ways
 * out of this screen are installing the update, or leaving the app, and
 * leaving was always the phone's business.
 *
 * The copy states both versions, says what is on the other side of the button,
 * and ends on the one sentence that matters to someone who has just been shut
 * out of their own habits: nothing is going anywhere.
 */
@Composable
private fun UpdateRequiredScreen(release: UpdateChecker.Release) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var openFailed by remember { mutableStateOf(false) }

    BackHandler(enabled = true) { }

    fun openRelease() {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url)))
        } catch (_: ActivityNotFoundException) {
            // A browser that answers no intent at all — a stripped build, or
            // a phone mid-setup. Saying so beats a button that ate the press.
            openFailed = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The mark on a breath of its ember — the same cover the About
            // card opens on, so this screen reads as Fyr speaking rather than
            // as a system notice that happens to be in the way.
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    Ember.copy(alpha = 0.20f),
                                    EmberSoft.copy(alpha = 0.06f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension / 2f,
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                FlameBadge(size = 58.dp)
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Update required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Fyr ${BuildConfig.VERSION_NAME} is out of date. " +
                    "Version ${release.versionName} is ready, and this build " +
                    "stays closed until it's installed.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (release.notes.isNotBlank()) {
                Spacer(Modifier.height(18.dp))

                // The well, with an ember rule down its side — the same
                // container About uses for how Fyr keeps score, so release
                // notes land as a note rather than as a pitch.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(Ember.copy(alpha = 0.55f)),
                    )
                    Text(
                        text = release.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 13.dp,
                            top = 11.dp,
                            bottom = 11.dp,
                            end = 14.dp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stamp(text = "you have  v${BuildConfig.VERSION_NAME}", accent = false)
                Stamp(text = "latest  v${release.versionName}", accent = true)
            }

            Spacer(Modifier.height(26.dp))

            Button(
                onClick = ::openRelease,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Ember,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    text = "Update now",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (openFailed) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No browser opened. The download is at\n${release.url}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "Nothing of yours is lost — every habit stays on this device.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The outlined stamp under the ask: one fact, not a button. */
@Composable
private fun Stamp(text: String, accent: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (accent) Ember.copy(alpha = 0.10f) else scheme.surfaceContainerHigh)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (accent) Ember else scheme.onSurfaceVariant,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** How long the waiting screen may stay silent before it explains itself. */
private const val SLOW_HINT_MS = 700L
