package com.fyr.ui.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fyr.ui.components.BurningFlame
import com.fyr.ui.components.FlameFlares
import com.fyr.ui.theme.Qurova

/**
 * The first screen a new install ever sees, once the launch logo has had its
 * beat.
 *
 * One composition, three jobs, in the order a thumb meets them: the fire —
 * which is the app's own mark, the same [BurningFlame] the splash and the
 * Today header burn, so identity is established by the thing itself rather
 * than by a picture of a stranger meditating — the sentence that says what
 * Fyr is and where it keeps it, and one door out. The arcs behind are the
 * only purely decorative marks in the app: thin ember threads looping
 * through the top of the screen so the white space above the fire reads as
 * sky rather than as an unfinished layout.
 *
 * It is a gate, not a route: it is rendered over the shell whenever the
 * first-launch question is still unanswered, it never appears in the back
 * stack, and dismissing it is remembered by the same flag the name prompt
 * uses — so a person meets it exactly once, and an existing install never
 * meets it at all.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background),
    ) {
        // Decorative, drawn under everything: two lazy threads and one wide
        // arc, all in the accent held so far back it reads as paper stock
        // rather than as line work. They cross the top only — the copy below
        // never sits on one.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val thread = 1.4.dp.toPx()
            val arc = Stroke(width = thread, cap = StrokeCap.Round)

            val upper = Path().apply {
                moveTo(-0.06f * w, 0.17f * h)
                cubicTo(0.22f * w, 0.06f * h, 0.44f * w, 0.28f * h, 0.70f * w, 0.15f * h)
                cubicTo(0.90f * w, 0.05f * h, 1.02f * w, 0.16f * h, 1.08f * w, 0.11f * h)
            }
            drawPath(upper, color = scheme.primary.copy(alpha = 0.28f), style = arc)

            val lower = Path().apply {
                moveTo(-0.05f * w, 0.34f * h)
                cubicTo(0.16f * w, 0.42f * h, 0.30f * w, 0.24f * h, 0.44f * w, 0.30f * h)
            }
            drawPath(lower, color = scheme.primary.copy(alpha = 0.20f), style = arc)

            drawArc(
                color = scheme.primary.copy(alpha = 0.18f),
                startAngle = 205f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(0.58f * w, -0.18f * h),
                size = androidx.compose.ui.geometry.Size(0.62f * w, 0.62f * w),
                style = arc,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp)
                .padding(top = 24.dp, bottom = 40.dp),
        ) {
            Spacer(Modifier.weight(1f))

            // The mark itself, on the halo it throws when it is lit — the
            // same construction as the launch logo, so the logo handing over
            // to this screen changes the fire's size and nothing else about
            // it. One flare on appearance: the welcome, said in embers.
            Box(
                modifier = Modifier.size(216.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(216.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to com.fyr.ui.theme.EmberSoft.copy(alpha = 0.16f),
                            0.55f to com.fyr.ui.theme.Ember.copy(alpha = 0.10f),
                            1f to Color.Transparent,
                        ),
                        radius = r,
                        center = Offset(r, r),
                    )
                }
                BurningFlame(size = 150.dp, showGlow = true)
                FlameFlares(tick = 1, modifier = Modifier.matchParentSize())
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Welcome",
                fontFamily = Qurova,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Track your habits and grow your streaks.",
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1.35f))

            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 9.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    )
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable(role = Role.Button, onClick = onGetStarted)
                    .padding(horizontal = 40.dp, vertical = 17.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Get started",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onPrimary,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}
