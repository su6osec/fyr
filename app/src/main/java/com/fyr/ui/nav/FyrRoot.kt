package com.fyr.ui.nav

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fyr.data.Dates
import com.fyr.data.FyrState
import com.fyr.data.Store
import com.fyr.data.Theme
import com.fyr.domain.Stats
import com.fyr.ui.add.AddHabitSheet
import com.fyr.ui.calendar.CalendarScreen
import com.fyr.ui.components.BurningFlame
import com.fyr.ui.components.FlameFlares
import com.fyr.ui.components.ConfettiBurst
import com.fyr.ui.components.FlameBadge
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.Milestone
import com.fyr.ui.components.MilestoneCard
import com.fyr.ui.components.MilestoneHit
import com.fyr.ui.components.Milestones
import com.fyr.ui.components.drawHaloBreath
import com.fyr.ui.components.drawHaloMarks
import com.fyr.ui.habit.HabitDetailScreen
import com.fyr.ui.insights.InsightsScreen
import com.fyr.ui.settings.SettingsScreen
import com.fyr.ui.theme.Ember
import com.fyr.ui.theme.EmberDim
import com.fyr.ui.theme.EmberSoft
import com.fyr.ui.theme.FyrTheme
import com.fyr.ui.today.TodayScreen
import com.fyr.ui.trophy.TrophiesScreen
import com.fyr.ui.welcome.ProfileSetupScreen
import com.fyr.ui.welcome.WelcomeScreen
import com.fyr.widget.FyrWidgetProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

sealed interface Route {
    data object Today : Route
    data object Calendar : Route
    data object Insights : Route
    data object Settings : Route
    data class Detail(val habitId: String) : Route

    // The trophy cabinet. Not a page — it is pushed over Insights the way a
    // habit detail is pushed over a page, so it takes the whole screen and
    // the tab bar steps aside while it is open.
    data object Trophies : Route
}

private val pages: List<Route> = listOf(
    Route.Today,
    Route.Calendar,
    Route.Insights,
    Route.Settings,
)

/** The page index for [Route.Settings] — the one page that is not a habit screen. */
private val settingsIndex: Int get() = pages.indexOf(Route.Settings)

/**
 * The tick that lands when a tab becomes active.
 *
 * `SEGMENT_TICK` is the sound Android uses for a segmented control — one
 * notch, no weight behind it — and it only exists from API 34, so older
 * releases fall back to the clock tick, which is the same click one weight
 * lighter. The guard is on the API level and not on the device: the constant
 * is resolved here, once, rather than inside the frame that plays it.
 */
private fun tabTick(): Int =
    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
    else HapticFeedbackConstants.CLOCK_TICK

/** Two celebrations closer together than this are one celebration. */
private const val COOLDOWN_MS = 4_000L

/**
 * How long the undo bar stays up.
 *
 * Five seconds is longer than it takes to read the one line it carries and
 * shorter than the moment where the offer starts to feel like an accusation.
 */
private const val UNDO_HOLD_MS = 5_000L

/** How long the launch logo holds before it begins to fade. */
private const val SPLASH_HOLD_MS = 1_050L

/** Nothing is on offer. Also the value a fresh composition starts from. */
private const val UNDO_NONE = ""

/** What the undo bar would put back: a deleted habit, or a retired one. */
private const val UNDO_DELETE = "delete"
private const val UNDO_RETIRE = "retire"

/**
 * The application shell: one back stack, four pages, one add button.
 *
 * The stack is hand-rolled rather than driven by a navigation library. Fyr has
 * exactly five destinations and one kind of push; a library would bring a
 * graph DSL, a plugin version to keep in step with the Compose compiler, and a
 * migration surface — all to express something this file says in twenty lines.
 *
 * Switching pages resets the stack to that page, which is the behaviour people
 * already carry over from every other app — and all four pages sit in one
 * pager, so they move under the finger as one strip rather than dissolving
 * into each other. Detail screens push on top and the system back gesture pops
 * them; add lives in a sheet so it never costs you where you were.
 *
 * [launchLogo] is the cold-start handshake from the activity: true exactly
 * once per process, so the logo plays when the app is genuinely opened and
 * never again until it is genuinely opened again.
 */
@Composable
fun FyrRoot(
    store: Store,
    initial: Route = Route.Today,
    initialAdd: Boolean = false,
    initialWrite: Boolean = false,
    initialDatePicker: Boolean = false,
    initialEdit: Boolean = false,
    initialCelebrate: Boolean = false,
    initialProfileEdit: Boolean = false,
    /**
     * Debug preview for the first-launch welcome: force the gate open on an
     * install that has long since answered it. Read only from a debuggable
     * build, like every other hook on this activity.
     */
    initialWelcome: Boolean = false,
    /**
     * Debug preview for the setup form that follows the welcome: force the
     * name-and-picture gate open on an install that has long since
     * answered it. Read only from a debuggable build, like every other hook
     * on this activity.
     */
    initialSetup: Boolean = false,
    launchLogo: Boolean = false,
) {
    val state by store.state.collectAsState()
    val appContext = LocalContext.current.applicationContext

    // "Today" is a value that moves. Held as state rather than captured once,
    // because this composition outlives midnight: an app left open on the
    // nightstand is the normal case, and a frozen date would put every tick
    // after 00:00 on yesterday — wrong streak, wrong history, and in
    // disagreement with the widget, which does re-read the date. The system
    // broadcasts the change itself, so nothing has to poll for it.
    var today by remember { mutableStateOf(Dates.today()) }
    DisposableEffect(appContext) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                today = Dates.today()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // RECEIVER_NOT_EXPORTED: these are system broadcasts and nothing else
        // on the phone needs to be able to move this app's date.
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { appContext.unregisterReceiver(receiver) }
    }

    // The belt under that broadcast. A vendor build can deliver DATE_CHANGED
    // late — or not at all — and a day the app never hears about is every
    // tick after midnight written onto yesterday: wrong streak, wrong
    // history, and in disagreement with the widget, which re-reads the date
    // on every press. Coming back to the foreground is the other moment the
    // day can have moved under us while nothing was running, so it re-reads
    // there too. Costs one line on every resume and closes the single point
    // of failure the receiver alone would otherwise be.
    LifecycleResumeEffect(Unit) {
        today = Dates.today()
        onPauseOrDispose { }
    }

    // The widget is told about every change, for the mirror-image reason. It
    // sits on a home screen and cannot see the app: without this, a tick made
    // here would not reach it until something else happened to redraw it.
    // With nothing placed, render returns before it does any work at all.
    LaunchedEffect(state) { FyrWidgetProvider.render(appContext) }

    // Read on every recomposition rather than captured once: the system can
    // change its mind while Fyr is on screen, and there is no stored choice
    // that could shadow it.
    val systemTheme = Theme.fromUiMode(LocalConfiguration.current.uiMode)

    FyrTheme(theme = systemTheme) {
        // Depth is at most two — a page, optionally one screen pushed on top —
        // so the whole back stack is two saveable scalars rather than a list
        // needing a custom Saver. Rotation and process death then cost nothing.
        var currentPage by rememberSaveable {
            mutableStateOf(if (initial in pages) pages.indexOf(initial) else 0)
        }
        var detailId by rememberSaveable {
            mutableStateOf((initial as? Route.Detail)?.habitId ?: "")
        }

        // The other push: the trophy cabinet. Held apart from `detailId`
        // because only one thing ever covers the pager at a time and the two
        // never overlap — a cabinet is opened from Insights, a detail from a
        // habit list, and opening either closes the other in [push].
        var trophiesOpen by rememberSaveable { mutableStateOf(initial == Route.Trophies) }

        // Which habit a long press asked to edit. Set the moment the menu is
        // tapped, cleared the moment the stack is empty again — so the flag
        // can only ever apply to the screen it was asked about. It lives here
        // rather than inside the detail because this is the layer that knows
        // when the detail has gone.
        var editRequest by rememberSaveable { mutableStateOf("") }

        // Where back goes from Profile. Written every time the pager comes to
        // rest anywhere else, so arriving by swipe records the same origin a
        // tap does, and Profile opened cold falls back to Today.
        var pageBeforeSettings by rememberSaveable { mutableStateOf(0) }

        // Stoked by every tap on the Today tab — including taps on the tab
        // already standing on, which have no navigating left to do. The
        // header's fire reads the count and answers each one with flares.
        // Deliberately not saveable: this is a burst of light, not a fact
        // about the world, and a rotation may safely let the last one go.
        var flameTick by remember { mutableStateOf(0) }

        // What is pushed on top of the page layer, if anything. Profile is not
        // a push — it is the fourth page — so a habit detail and the trophy
        // cabinet are the only things that ever cover the pager. Held apart
        // from the page itself so that switching pages never animates the
        // screen: the pager does that.
        val pushed: Route? = when {
            detailId.isNotEmpty() -> Route.Detail(detailId)
            trophiesOpen -> Route.Trophies
            else -> null
        }

        val current: Route = pushed ?: pages[currentPage]

        val scope = rememberCoroutineScope()
        val pagerState = rememberPagerState(initialPage = currentPage) { pages.size }

        // One tick per tab and never more. The pill below moves every frame
        // the finger does, but a buzz per frame is a vibration, not a signal —
        // this fires on the same edge the eye reads as "the tab changed", so
        // the sound of the change and the sight of it are one event.
        //
        // Through the view rather than through LocalHapticFeedback, whose type
        // only carries the platform's two generic constants: a tab is a
        // segment, and SEGMENT_TICK is the notch Android uses for exactly that
        // — one light click, no weight behind it.
        val view = LocalView.current
        var tabArmed by remember { mutableStateOf(false) }
        // Keyed on the *page*, not on the route: a habit detail is a push on
        // top of a page, so coming back from one changed `current` and played
        // the tab tick again — a click for a tab that had not moved.
        LaunchedEffect(currentPage) {
            if (tabArmed) view.performHapticFeedback(tabTick())
            tabArmed = true
        }

        // The bar tracks the *finger*, not the landing — and it does that
        // tracking **inside its own composition** (see [TabBar]): reading the
        // pager's position up here used to invalidate this entire shell —
        // every screen, every page — on every frame of a swipe, for a number
        // only the bar has any use for.

        // The pager is the source of truth while it is on screen, but only
        // once it has come to rest. currentPage walks through every page it
        // passes over, so reading it mid-scroll would drag the tab indicator
        // along with a jump — highlights firing in the time it takes to cross
        // two pages. A swipe lands on its tab instead; a tap already knows
        // where it is going and never gets overridden on the way there.
        LaunchedEffect(pagerState) {
            snapshotFlow {
                if (pagerState.isScrollInProgress) -1 else pagerState.currentPage
            }.collect { page ->
                if (page < 0) return@collect
                if (page != currentPage) currentPage = page
                if (pages[page] != Route.Settings) pageBeforeSettings = page
            }
        }

        var showAdd by rememberSaveable { mutableStateOf(initialAdd) }

        // The frost for any box that owns the screen, hoisted to the root:
        // the dialogs it belongs to live in the settings screen's
        // composition — the profile editor and the About card alike — but
        // what has to blur is everything this Box holds, the pages and the
        // bar, so the screen reports its box up and the blur is painted
        // here. One flag for either, because the frost behind them is the
        // same frost by design. Not saveable: a process death mid-dialog
        // comes back to a fresh window over an unfrosted screen, which is
        // exactly what it should be.
        var frostOpen by remember { mutableStateOf(false) }

        // ── cold start ──────────────────────────────────────────────────
        // The launcher's own logo, held for a beat while the app assembles
        // itself behind it, then faded off. Saveable so that turning the
        // phone mid-splash continues it rather than playing it again; the
        // `launchLogo` flag itself is the activity's once-per-process mark,
        // which is what keeps returning from the recents tray quiet.
        var splash by rememberSaveable { mutableStateOf(launchLogo) }
        var splashLeaving by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(splash, splashLeaving) {
            if (!splash) return@LaunchedEffect
            delay(if (splashLeaving) 400L else SPLASH_HOLD_MS)
            if (splashLeaving) {
                splash = false
            } else {
                splashLeaving = true
                // The moment the logo starts handing the screen over, it
                // stokes Today's fire — so the first thing revealed is the
                // header throwing its one welcome burst, in step with the
                // launch fire fading out. One flare on appearance, then only
                // the hand asks for more.
                flameTick += 1
            }
        }
        val splashAlpha by animateFloatAsState(
            targetValue = if (splashLeaving) 0f else 1f,
            animationSpec = tween(320),
            label = "splashAlpha",
        )

        // ── first launch ──────────────────────────────────────────────────
        // The welcome is a gate, not a route: open exactly while the
        // first-launch question is unanswered, drawn over the whole shell,
        // and closed by the same visit that answers it. Existing installs
        // have [com.fyr.data.FyrState.nameAsked] long true and never see
        // it; a fresh one sees the logo, then this, then the one question.
        var welcomeDone by rememberSaveable { mutableStateOf(false) }
        val showWelcome = !welcomeDone && (initialWelcome || !state.nameAsked)

        // ── the name gate ─────────────────────────────────────────────────
        // The form the welcome hands over to: first name, last name, a
        // picture — saved, and only then, does the app open. Answered once
        // (`nameAsked` is the same flag the welcome defers to), so an
        // existing install never meets it; forced open by the debug hook it
        // is dismissible with back, because a preview that cannot be closed
        // is a preview that needs a rebuild to leave.
        var setupDismissed by rememberSaveable { mutableStateOf(false) }
        val showSetup = !showWelcome && !splash && pushed == null && !showAdd &&
            if (initialSetup) !setupDismissed else !state.nameAsked

        // ── one burst per new streak ─────────────────────────────────────
        // Two moments count as "starting a streak": laying the habit down, and
        // the run going 0 → 1. Both are beginnings, and celebrating only the
        // second would mean the confetti does not appear when you have just
        // created something. The 4s cooldown is what stops the pair — create,
        // then immediately tick — from firing twice in one breath.
        var celebrating by remember { mutableStateOf(initialCelebrate) }
        // Saveable so the four-second cooldown still applies after a rotation:
        // reset to zero, two quick ticks would each play the full burst.
        var celebratedAt by rememberSaveable { mutableStateOf(0L) }
        var baselined by remember { mutableStateOf(false) }
        var previousStreaks by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

        // The level-up card, held apart from the burst underneath it so the
        // two can run on their own clocks: the confetti is gone in a second
        // and a half, and the sentence that says *what* you just earned is
        // still up when you look back at the screen.
        //
        // Saved as its five primitives because a card announcing an achievement
        // is exactly the thing a rotation should not be able to interrupt —
        // the reward was for the run, not for the orientation of the screen.
        var milestone by rememberSaveable(
            stateSaver = listSaver<MilestoneHit?, Any>(
                save = { hit ->
                    if (hit == null) {
                        emptyList()
                    } else {
                        listOf(
                            hit.habitName,
                            hit.habitEmoji,
                            hit.milestone.days,
                            hit.milestone.label,
                            hit.milestone.headline,
                        )
                    }
                },
                restore = { parts ->
                    if (parts.size != 5) null else MilestoneHit(
                        habitName = parts[0] as String,
                        habitEmoji = parts[1] as String,
                        milestone = Milestone(
                            days = parts[2] as Int,
                            label = parts[3] as String,
                            headline = parts[4] as String,
                        ),
                    )
                },
            ),
        ) { mutableStateOf<MilestoneHit?>(null) }

        fun celebrate(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!celebrating && (force || now - celebratedAt >= COOLDOWN_MS)) {
                celebratedAt = now
                celebrating = true
            }
        }

        val streaksNow = remember(state, today) {
            state.habits.associate { h ->
                h.id to Stats.currentStreak(h, state.completionsFor(h.id), today)
            }
        }

        LaunchedEffect(streaksNow) {
            // The first pass only records what the streaks already are, so
            // opening the app on a four-day run does not celebrate a streak
            // that began four days ago.
            if (!baselined) {
                previousStreaks = streaksNow
                baselined = true
                return@LaunchedEffect
            }

            val before = previousStreaks
            // `before + streaksNow` rather than `streaksNow`: a deleted habit
            // leaves the live map, and dropping its baseline here meant the
            // Undo bar — which puts the habit and its record straight back —
            // read as a streak going 0 → N and fired confetti for a run the
            // user already had. Entries for live habits still come from
            // `streaksNow`; only the vanished ids linger, waiting to be
            // restored.
            previousStreaks = before + streaksNow

            if (streaksNow.any { (id, count) ->
                    count > 0 && (before[id] ?: 0) == 0
                }
            ) {
                celebrate()
            }

            // A level crossed — on any day, including one in the past, which
            // is the whole point of a backfill. Ticking yesterday can carry a
            // run over two levels in a single tap; the highest one reached is
            // the only one announced, because three cards for one press would
            // make the reward feel like a queue rather than an arrival.
            var best: MilestoneHit? = null
            var bestDays = -1
            streaksNow.forEach { (id, now) ->
                val was = before[id] ?: 0
                if (now > was) {
                    val level = Milestones.crossed(was, now)
                    val habit = state.habits.firstOrNull { it.id == id }
                    if (level != null && habit != null && level.days > bestDays) {
                        bestDays = level.days
                        best = MilestoneHit(habit.name, habit.emoji, level)
                    }
                }
            }
            best?.let {
                milestone = it
                // Forced: a reward that occasionally does not appear because
                // something else was recently celebrated is not a reward.
                celebrate(force = true)
            }
        }

        fun push(route: Route) {
            when {
                route in pages -> {
                    val index = pages.indexOf(route)
                    detailId = ""
                    trophiesOpen = false
                    // Leaving for a tab also leaves whatever edit was asked
                    // for on the way out; otherwise the next habit opened
                    // would inherit a request made for the previous one.
                    editRequest = ""
                    // Every tap on the Today tab stokes Today's fire, first
                    // arrival and re-tap alike — the tab and the mark at the
                    // top of its page are wired together by this count.
                    if (route == Route.Today) flameTick += 1
                    if (index != currentPage) {
                        currentPage = index
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                }
                route is Route.Detail -> {
                    trophiesOpen = false
                    detailId = route.habitId
                }
                route == Route.Trophies -> {
                    detailId = ""
                    trophiesOpen = true
                }
            }
        }

        fun pop() {
            detailId = ""
            trophiesOpen = false
            editRequest = ""
        }

        // Open a habit with its own editor already up. The three menu actions
        // all arrive here: "edit" is the only one that has a destination, and
        // that destination already contains the exact dialog being asked for.
        fun editHabit(habitId: String) {
            editRequest = habitId
            detailId = habitId
        }

        // ── one word back ──────────────────────────────────────────────
        // A long press puts "delete" one tap from every list, and deletion
        // takes the history with it. A confirmation box would be the obvious
        // answer, but a box people press through without reading is not a
        // guard — it is a speed bump, and it would sit in front of the two
        // actions that are perfectly safe. So the row goes, and for five
        // seconds the habit, its record and a single word come back along the
        // bottom of the screen: the confirmation is the thing it restores.
        //
        // Held as three saveable scalars rather than as one captured lambda.
        // The action is rebuilt from the record when Undo is actually pressed,
        // which is what lets the offer survive a rotation inside its five
        // seconds — previously the only way back from a delete was a closure
        // a configuration change threw away.
        var undoKind by rememberSaveable { mutableStateOf(UNDO_NONE) }
        var undoHabitId by rememberSaveable { mutableStateOf("") }
        var undoMessage by rememberSaveable { mutableStateOf("") }
        // Bumped on every offer so the countdown restarts even when the same
        // sentence comes back twice running. "Deleted Sprinkle" twice in a row
        // is two different chances to change your mind, and the second one
        // deserves a full five seconds as much as the first.
        var undoToken by rememberSaveable { mutableStateOf(0L) }
        val undoPending = undoKind != UNDO_NONE

        fun offerUndo(kind: String, habitId: String, message: String) {
            undoKind = kind
            undoHabitId = habitId
            undoMessage = message
            undoToken += 1
        }

        LaunchedEffect(undoKind, undoToken) {
            if (undoKind == UNDO_NONE) return@LaunchedEffect
            delay(UNDO_HOLD_MS)
            undoKind = UNDO_NONE
        }

        fun undoLast() {
            when (undoKind) {
                // The history rides in the trash entries [Store.deleteHabit]
                // just wrote, so it is read back from there rather than kept
                // in this closure — same data, no lifetime to manage. A batch
                // undo arrives as its ids joined by comma and restores every
                // entry still sitting in the trash under them; a single undo
                // is the same string of one.
                UNDO_DELETE -> undoHabitId.split(',')
                    .filter { it.isNotEmpty() }
                    .forEach { id ->
                        state.trash.firstOrNull { it.habit.id == id }?.let {
                            store.restoreHabit(it.habit, it.completions)
                        }
                    }

                // Retiring is one flag per habit, so the batch is one flag
                // flipped back per id — same string, same walk.
                UNDO_RETIRE -> undoHabitId.split(',')
                    .filter { it.isNotEmpty() }
                    .forEach { store.setArchived(it, false) }
            }
            undoKind = UNDO_NONE
        }

        fun deleteManyWithUndo(habitIds: List<String>) {
            if (habitIds.isEmpty()) return
            if (habitIds.size == 1) {
                val habit = state.habits.firstOrNull { it.id == habitIds.first() }
                store.deleteHabit(habitIds.first())
                offerUndo(UNDO_DELETE, habitIds.first(), "Deleted ${habit?.name ?: "habit"}")
                return
            }
            habitIds.forEach { store.deleteHabit(it) }
            offerUndo(UNDO_DELETE, habitIds.joinToString(","), "Deleted ${habitIds.size} habits")
        }

        fun deleteWithUndo(habitId: String) = deleteManyWithUndo(listOf(habitId))

        fun retireManyWithUndo(habitIds: List<String>) {
            if (habitIds.isEmpty()) return
            if (habitIds.size == 1) {
                val habit = state.habits.firstOrNull { it.id == habitIds.first() }
                store.setArchived(habitIds.first(), true)
                offerUndo(
                    UNDO_RETIRE,
                    habitIds.first(),
                    if (habit == null) "Habit retired" else "Retired ${habit.name}",
                )
                return
            }
            habitIds.forEach { store.setArchived(it, true) }
            offerUndo(UNDO_RETIRE, habitIds.joinToString(","), "Retired ${habitIds.size} habits")
        }

        fun retireWithUndo(habitId: String) = retireManyWithUndo(listOf(habitId))

        // One hold, one excuse: the pill in Today (and in a day's list)
        // toggles the same one-off skip the habit's own calendar toggles —
        // same record, same instant, no round trip between screens. It
        // carries no undo offer because it needs none: the control that
        // wrote it is still under the thumb, and the second hold erases the
        // first. The day's ice in the pill, in the grid and in the counts
        // all follow from this one write.
        fun toggleFreeze(habitId: String, day: Int) {
            val habit = state.habits.firstOrNull { it.id == habitId } ?: return
            store.setHabit(
                if (habit.skipDays.contains(day)) {
                    habit.copy(skipDays = habit.skipDays - day)
                } else {
                    habit.copy(skipDays = habit.skipDays + day)
                },
            )
        }

        // Profile is a page, so back does not close it — it leaves it, the
        // way it left the screen underneath before Profile was promoted out of
        // a push. The origin is the page the pager last came to rest on, which
        // makes a swipe onto Profile back out exactly where a tap would have.
        // True while either push — a habit detail or the trophy cabinet —
        // covers the pager, so back closes whichever one is up before it
        // ever thinks about leaving a page.
        val overlayOpen = detailId.isNotEmpty() || trophiesOpen

        BackHandler(enabled = !overlayOpen && currentPage == settingsIndex) {
            val back = pageBeforeSettings
            currentPage = back
            scope.launch { pagerState.animateScrollToPage(back) }
        }
        BackHandler(enabled = overlayOpen) { pop() }
        // Composed after the navigation handler so it sits above it in the
        // back-press stack: when the sheet is up, back closes the sheet first.
        BackHandler(enabled = showAdd) { showAdd = false }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // The frost. One frame, pages and bar alike, held behind
                // whatever box is up — editor or About card — clipped to
                // the screen's own bounds, so the blur never bleeds a halo
                // past the edge, and gated on S because RenderEffect — the
                // only thing that can do this on the GPU — is API 31;
                // older devices keep the dialog's plain dim, which is what
                // they had before.
                .then(
                    if (frostOpen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(24.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.systemBars.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Bottom,
                ),
            bottomBar = {
                // Profile is a page, so the bar stays under it; only a habit
                // detail claims the whole screen.
                if (current in pages) {
                    TabBar(
                        pagerState = pagerState,
                        selectedIndex = currentPage,
                        onSelect = ::push,
                        showAdd = !showAdd,
                        onAdd = { showAdd = true },
                    )
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = pushed,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(120))
                },
                label = "screen",
            ) { route ->
                when (route) {
                    // The three tabs sit side by side in a pager, so dragging
                    // across slides them rather than dissolving one into the
                    // next. This target only moves when something is pushed.
                    null -> HorizontalPager(
                        state = pagerState,
                        // All four pages are kept alive, exactly as the three
                        // were before Profile joined them: composing a page
                        // mid-swipe is a hitch, and these are small screens.
                        beyondViewportPageCount = pages.size,
                        pageSpacing = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                        key = { pages[it].toString() },
                    ) { page ->
                        when (pages[page]) {
                            Route.Today -> TodayScreen(
                                state = state,
                                today = today,
                                flameTick = flameTick,
                                onToggle = { id, day -> store.toggle(id, day) },
                                onOpenHabit = { push(Route.Detail(it)) },
                                onEditHabit = { editHabit(it) },
                                onRetireHabit = { retireWithUndo(it) },
                                onDeleteHabit = { deleteWithUndo(it) },
                                onFreezeHabit = { id, day -> toggleFreeze(id, day) },
                                onRetireMany = { retireManyWithUndo(it) },
                                onDeleteMany = { deleteManyWithUndo(it) },
                                contentPadding = padding,
                            )

                            Route.Calendar -> CalendarScreen(
                                state = state,
                                today = today,
                                onToggle = { id, day -> store.toggle(id, day) },
                                onOpenHabit = { push(Route.Detail(it)) },
                                onEditHabit = { editHabit(it) },
                                onRetireHabit = { retireWithUndo(it) },
                                onDeleteHabit = { deleteWithUndo(it) },
                                onFreezeHabit = { id, day -> toggleFreeze(id, day) },
                                contentPadding = padding,
                            )

                            Route.Settings -> SettingsScreen(
                                state = state,
                                today = today,
                                initialEditing = initialProfileEdit,
                                onName = { store.setUserName(it) },
                                // The picture, in two steps: the picker's
                                // result is staged beside the profile and
                                // only Save adopts it. Cancel discards it.
                                stagePhoto = { uri, onStaged ->
                                    store.stageAvatar(uri, onStaged)
                                },
                                adoptPhoto = { store.adoptStagedAvatar() },
                                discardPhoto = { store.dropStagedAvatar() },
                                hasStagedPhoto = { store.hasStagedAvatar() },
                                onAvatarClear = { store.clearAvatarPhoto() },
                                onWeekStart = { store.setWeekStart(it) },
                                onOpenHabit = { push(Route.Detail(it)) },
                                onRestoreTrash = { habitId ->
                                    val entry = state.trash.firstOrNull { it.habit.id == habitId }
                                    if (entry != null) {
                                        store.restoreHabit(entry.habit, entry.completions)
                                    }
                                },
                                onPurgeTrash = { store.purgeTrash(it) },
                                onEmptyTrash = { store.emptyTrash() },
                                // The backup, straight through to the store:
                                // writing, reading (which decides nothing) and
                                // applying — and each callback comes back on
                                // the main thread, because all three of them
                                // land in Compose state.
                                exportBackup = { uri, onDone ->
                                    store.exportBackup(uri, onDone)
                                },
                                readBackup = { uri, onReady ->
                                    store.readBackup(uri, onReady)
                                },
                                applyBackup = { archive, mode, onDone ->
                                    store.applyBackup(archive, mode, onDone)
                                },
                                // Each box floats in a window over this
                                // screen, so the frost it asks for has to
                                // be painted here: blur the whole frame —
                                // pages and bar alike — while it is up, so
                                // the card reads as glass over a room that
                                // has stepped back rather than a card
                                // pasted on a screen still demanding taps.
                                onOverlayChange = { frostOpen = it },
                                contentPadding = padding,
                            )

                            // Insights: the one page with no screen of its own
                            // to name here, so it takes the fall-through.
                            else -> InsightsScreen(
                                state = state,
                                today = today,
                                onOpenHabit = { push(Route.Detail(it)) },
                                onOpenTrophies = { push(Route.Trophies) },
                                contentPadding = padding,
                            )
                        }
                    }

                    is Route.Detail -> HabitDetailScreen(
                        habitId = route.habitId,
                        state = state,
                        today = today,
                        // Scoped to the habit the hook asked for: as a bare
                        // boolean it stayed true for the life of the activity,
                        // so opening *any* habit afterwards brought up an edit
                        // dialog nobody had asked for.
                        openEdit = (editRequest == route.habitId) ||
                            (initialEdit && (initial as? Route.Detail)?.habitId == route.habitId),
                        onBack = { pop() },
                        onToggle = { id, day -> store.toggle(id, day) },
                        onSave = { store.setHabit(it) },
                        onArchive = { id, archived -> store.setArchived(id, archived) },
                        onDelete = {
                            store.deleteHabit(it)
                            pop()
                        },
                        contentPadding = padding,
                    )

                    is Route.Trophies -> TrophiesScreen(
                        state = state,
                        today = today,
                        onBack = { pop() },
                        contentPadding = padding,
                    )

                    // Pages are never a push target; they are all in the pager.
                    else -> Unit
                }
            }
        }

        // Rides above the bar rather than over it: the tab bar is where the
        // thumb already is, and an offer you cannot reach is an offer nobody
        // takes. The message is kept outside the visibility flag so the line
        // does not blank out mid-exit.
        AnimatedVisibility(
            visible = undoPending,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(tween(180)),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(tween(140)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp)
                .padding(bottom = 104.dp),
        ) {
            UndoBar(
                message = undoMessage,
                token = undoToken,
                holdMillis = UNDO_HOLD_MS,
                onUndo = ::undoLast,
            )
        }

        if (showAdd) {
            AddHabitSheet(
                onDismiss = { showAdd = false },
                onCreate = {
                    store.setHabit(it)
                    celebrate()
                },
                newId = { store.newId() },
                today = today,
                weekStart = state.weekStartDay,
                // The shelf's inventory, and the delete that goes with it.
                // Deliberately *not* the undoable pair above: the sheet
                // covers the bottom of the screen, so an undo bar raised by
                // something done inside it would appear underneath the sheet
                // that raised it. This path confirms before it acts instead.
                habits = state.shelf,
                onHidePill = { store.hideShelfPill(it) },
                initialWrite = initialWrite,
                initialDatePicker = initialDatePicker,
            )
        }

        // Drawn last so it lands over everything, and it handles no pointers
        // at all: nobody should have to wait out a celebration before they
        // can tick the next habit.
        if (celebrating) {
            ConfettiBurst(
                accent = MaterialTheme.colorScheme.primary,
                onFinished = { celebrating = false },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The reward itself, over the burst. Same pass-through rule as the
        // confetti: it is a sentence held up for a moment, not a dialog to be
        // answered, so only its own body takes the tap and everything around
        // it keeps working underneath.
        milestone?.let { hit ->
            MilestoneCard(
                hit = hit,
                onDismiss = { milestone = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Under the logo, over everything else: the fire on the welcome is
        // the one the launch fire hands over to, so the two compose rather
        // than cut — the logo fades and the same flame, larger, is what is
        // left holding the screen. A gate owes the eye a dissolve; it takes
        // every pointer itself, so nothing beneath it can be reached by
        // accident while it is up.
        if (showWelcome) {
            WelcomeScreen(onGetStarted = { welcomeDone = true })
        }

        // Drawn last so it lands over the whole shell, and it takes no
        // pointers at all — everything under it is already built by the time
        // it fades, so there is nothing here worth blocking a thumb for.
        if (splash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .graphicsLayer(alpha = splashAlpha),
                contentAlignment = Alignment.Center,
            ) {
                // BurningFlame with light firey background glow
                Box(
                    modifier = Modifier.size(148.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // Light firey background glow behind the animated flame
                    Canvas(modifier = Modifier.size(148.dp)) {
                        val radius = this.size.minDimension / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to EmberSoft.copy(alpha = 0.18f),
                                0.5f to Ember.copy(alpha = 0.12f),
                                1f to Color.Transparent,
                            ),
                            radius = radius,
                        )
                    }
                    BurningFlame(size = 148.dp, showGlow = true)
                    // Composed last: the launch fire throws exactly one
                    // burst as it appears — the app's welcome — and then
                    // waits, the same way the Today tab's flame waits for
                    // the hand that stokes it.
                    FlameFlares(tick = 1, modifier = Modifier.matchParentSize())
                }
            }
        }
        }

        // The gate between the welcome and the app: no name, no app. Back
        // does nothing while the real one is up — leaving is the phone's
        // business (home, recents), not a third answer the form offers —
        // and it waits for the launch logo to clear, so the first thing a
        // new user ever sees in Fyr is the logo rather than a form. The
        // hook's forced preview is the exception: only that one opens
        // outward, and only with back.
        BackHandler(enabled = showSetup) {
            if (initialSetup) setupDismissed = true
        }
        if (showSetup) {
            ProfileSetupScreen(
                onStagePhoto = { uri, onStaged -> store.stageAvatar(uri, onStaged) },
                hasStagedPhoto = { store.hasStagedAvatar() },
                onSave = { first, last ->
                    // The forced preview leaves the way it came in; the
                    // real gate is ended by the save's own flag.
                    setupDismissed = true
                    store.setUserName(
                        listOf(first, last).filter(String::isNotBlank).joinToString(" "),
                    )
                    // On the same write as the name: the picture brought to
                    // the form arrives *with* it, never before it — and is
                    // a no-op if none was brought.
                    store.adoptStagedAvatar()
                },
            )
        }
    }
}

/**
 * Four destinations and one control that is not one of them.
 *
 * The old bar was a [androidx.compose.material3.NavigationBar], which owns its
 * own pill and can only be told *which* item is selected. Selection is a
 * discrete fact; a swipe is a continuous one — so the pill sat on the origin
 * tab for the whole drag and then jumped, and the colour answered only after
 * the gesture had already ended. The animation was always one event behind
 * the finger driving it.
 *
 * The pill is gone rather than improved. What a finger dragging across the bar
 * should change is *colour*: one mark warming as the next one cools, and
 * nothing travelling between them — because anything that slides has to travel
 * across the middle of this bar, and the middle is where the add button sits.
 * A layer crossing the control on its way from Calendar to Insights made the
 * two look like stops on the same route, which is exactly what the plus is not.
 * The haptic stays deliberately non-interpolating for the same reason it always
 * had: it fires once, when a tab is actually reached, because a tick per frame
 * is a hum.
 *
 * The bar itself floats: a pill inset from both edges, held off the page by
 * a hairline and nothing else — no drop shadow. A shadow under a full-width
 * pill in the light theme is a grey rectangle lying behind the curved bar,
 * which is precisely the reading the bar must avoid; the hairline alone
 * holds the rim, everywhere, in both lights. A floor is something to stand
 * on; a pill is something being held.
 *
 * The bar speaks in marks alone: no word sits under an icon. What says
 * *here* is one ember bead beneath the lit mark and nothing beneath the
 * rest — the silence around the bead is what makes it a word rather than a
 * speck — and the names still exist, on the marks themselves, where a
 * screen reader picks them up the way it picks up every other label.
 *
 * [showAdd] controls the one control that is not a tab. The pill's top edge
 * swells into a shallow, wide hump at the centre, and the control sits
 * *inside* the bar — its centre on the same line as the icons beside it, its
 * rim never crossing the silhouette — so the thumb can slide along the bar's
 * own edge without meeting a bump it did not put there. The hump's job is
 * only to frame what the control wears: the one halo Fyr draws — a breath
 * of ember, a hairline ring and two rings of beads — in *both* lights, so
 * a theme switch does not give the plus a second face. That
 * decoration belongs to the slot, not to the bar: when [showAdd] goes false
 * it goes with the hump, and the footprint stays the same, so nothing
 * underneath jumps.
 */
@Composable
private fun TabBar(
    pagerState: PagerState,
    selectedIndex: Int,
    onSelect: (Route) -> Unit,
    showAdd: Boolean,
    onAdd: () -> Unit,
) {
    // The bar tracks the *finger*, not the landing. The pager's continuous
    // position makes the pill, the icon and the colour travel together
    // through a swipe instead of snapping once the gesture has ended —
    // which is what made the old bar feel like it was answering a question
    // it had only just been asked. Profile rides the same number as every
    // other page, because it is one: four pages, one continuous position.
    //
    // Computed *here*, not in the shell above: both pager reads and the
    // animated value are consumed only by this bar, so only this bar
    // invalidates while a swipe is in flight — dragging a finger across the
    // tabs used to recompose every screen behind the bar, every frame.
    val tabTarget: Float = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val position by animateFloatAsState(
        targetValue = tabTarget,
        // Fast enough that a drag feels unmediated, yet still eases the
        // jump to Profile instead of teleporting there.
        animationSpec = spring(dampingRatio = 1f, stiffness = 1200f),
        label = "tabPosition",
    )

    val scheme = MaterialTheme.colorScheme
    val pillHeight = 68.dp
    // The bar's two lights again, hoisted so the cradle's drawing can answer
    // them the same way the control's face does below.
    val light = scheme.background.luminance() >= 0.5f

    // The cradle's profile: a swell rather than a socket. The rise is how far
    // the lip lifts over the control and the ring around it; the half-width
    // is the shoulder it keeps on either side — wider than the control by
    // enough that the curve reads as the pill breathing rather than as a bump
    // with corners where it meets the straight edge.
    val humpRise = 10.dp
    val humpHalf = 43.dp

    // The line every mark hangs from: the icons, the bead under the active
    // one, and the control all share this centre, which is what seats the
    // button as one of the columns rather than as cargo riding above them.
    val contentY = 30.dp
    // Where the bead lands, measured from the same line the icons hang from.
    val dotY = 53.dp

    // One outline for the whole surface: a rounded strip that rises into a
    // hump while the control is up and settles to a plain strip when it is
    // not — the footprint either way is identical, so hiding the control
    // lifts nothing under the bar. Remembered against [showAdd] and the
    // measured pixels: a drag across the tabs recomposes this bar every
    // frame, and rebuilding the path the surface is clipped to on every one
    // of them is how a smooth swipe turns into a stutter. The builder gets
    // px, not density — it has neither a Density receiver nor a density
    // parameter — so the conversion happens here, once per composition that
    // actually changed something.
    //
    // The strip and the hump are *one* contour, walked: top edge, the arc
    // over the control, top edge again, then the four corners home. They
    // used to be two subpaths dropped in the same Path — a rectangle and an
    // oval, unioned by the fill — and a stroke has no notion of a union: the
    // border traced the oval's buried half too, ruling a full ellipse across
    // the bar above the plus. Filling never noticed; the hairline did —
    // and the arc's own numbers are worked out where it is drawn, where
    // the feet and the rise it is drawn through are to hand.
    val density = LocalDensity.current
    val risePx = with(density) { humpRise.toPx() }
    val halfPx = with(density) { humpHalf.toPx() }
    val cornerPx = with(density) { 26.dp.toPx() }
    val pillShape = remember(showAdd, risePx, halfPx, cornerPx) {
        GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val r = cornerPx

            // Start where the top-left corner leaves off, and walk clockwise.
            moveTo(r, risePx)
            if (showAdd) {
                lineTo(cx - halfPx, risePx)
                // The hump's arc is solved rather than eyeballed: through
                // both its feet and its apex, one radius — (half² + rise²)
                // / 2·rise — which puts its centre at (cx, R) exactly, so
                // the shoulder cannot drift with the rise. The sweep runs
                // from where the left foot stands, up over the north of
                // that circle, down to the right foot: start at −180 + the
                // foot's own bearing, for 180 − twice it.
                val arcR = (halfPx * halfPx + risePx * risePx) / (2f * risePx)
                val foot = Math.toDegrees(
                    Math.atan2((arcR - risePx).toDouble(), halfPx.toDouble()),
                ).toFloat()
                arcTo(
                    rect = Rect(cx - arcR, 0f, cx + arcR, 2f * arcR),
                    startAngleDegrees = -180f + foot,
                    sweepAngleDegrees = 180f - 2f * foot,
                    forceMoveTo = false,
                )
            }
            lineTo(w - r, risePx)
            arcTo(Rect(w - 2f * r, risePx, w, risePx + 2f * r), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2f * r, h - 2f * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2f * r, 2f * r, h), 90f, 90f, false)
            lineTo(0f, risePx + r)
            arcTo(Rect(0f, risePx, 2f * r, risePx + 2f * r), 180f, 90f, false)
            close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Padding first, paint second: the inset is outside the fill, so
            // the surface never reaches the screen's edge — the bar is an
            // object lying on the page, and the page keeps showing around it.
            // No lift this time: the drop shadow a full-width pill casts in
            // the light is a grey rectangle bolted under the curved bar,
            // which is the one thing the bar must not read as. The hairline
            // alone holds the rim — it was always what held it in the dark,
            // where the shadow could not be seen anyway.
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(pillHeight + humpRise)
            .clip(pillShape)
            .background(
                // White pill on paper, lifted pill in the dark: the surface
                // the rest of the app is made of, so the bar is the screen's
                // own material folded into a shape, not a foreign panel.
                if (scheme.background.luminance() < 0.5f) {
                    scheme.surfaceContainer
                } else {
                    scheme.surface
                },
            )
            .border(
                width = 1.dp,
                color = scheme.outlineVariant.copy(alpha = 0.5f),
                shape = pillShape,
            ),
    ) {
        // Drawn first, so the control draws over it rather than under it.
        // One frame in both lights — the marks each light used to keep for
        // itself, drawn together: the breath, the thin ring three dp off the
        // rim, and both rings of beads — twelve bright with twelve dimmer
        // ones half a step round and further out, because a single even
        // ring reads as a dial. Paper used to get only the beads and the
        // dark only the ring, so the same button wore two different faces
        // across a theme switch; now the hump frames dots *and* rings
        // either way, the same frame the portraits wear. All of it stays
        // under the hump's lip, which is what the hump exists to frame.
        if (showAdd) {
            val decor = 76.dp
            val buttonCenterY = humpRise + contentY
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = buttonCenterY - decor / 2f)
                    .size(decor),
            ) {
                drawHaloBreath(
                    radius = this.size.minDimension / 2f,
                    color = scheme.primary,
                    alpha = if (light) 0.16f else 0.34f,
                )
                drawHaloMarks(
                    // The control's outer edge: it draws its own thick ring
                    // as a border, so the halo only adds what sits beyond.
                    contentRadius = AddButtonSize.toPx() / 2f,
                    rim = null,
                    wavy = false,
                    hairlineColor = scheme.primary.copy(alpha = 0.55f),
                    dotColor = scheme.primary,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = humpRise)
                .height(pillHeight),
        ) {
            tabs.forEachIndexed { index, tab ->
                // Proximity to the centre of travel: 1 when this tab is the
                // one under the finger, 0 the moment it is a slot away. Colour
                // is the whole answer to that number — no scale, no backdrop,
                // no weight change — so crossing the middle of the bar changes
                // what the marks are and never what the bar is carrying.
                val focus = (1f - abs(position - index)).coerceIn(0f, 1f)
                val color = lerp(scheme.onSurfaceVariant, scheme.primary, focus)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // `selectable` rather than `clickable`: the bar is a
                        // set of four choices, and announcing the current one
                        // is the only way a screen-reader user learns which
                        // screen they are on — the colour says it to everyone
                        // else. No indication: the box a ripple paints is a
                        // square around a slot that has no edges of its own,
                        // and the colour change already says which one you hit.
                        .selectable(
                            selected = index == selectedIndex,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab.route) },
                        )
                        // The name, said rather than shown: the bar carries
                        // marks and a bead now, and a screen reader still has
                        // to be told which mark is which.
                        .semantics { contentDescription = tab.label }
                        .padding(top = contentY - 12.dp),
                ) {
                    GlyphIcon(
                        glyph = tab.glyph,
                        color = color,
                        size = 24.dp,
                        strokeWidth = 2.2.dp,
                    )
                    // The bead replaces the word: one ember dot under the
                    // mark that is lit, nothing under the rest. Top-anchored,
                    // so the icons never move — only the bead appears and
                    // goes.
                    if (index == selectedIndex) {
                        Spacer(Modifier.height(dotY - contentY - 14.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(scheme.primary),
                        )
                    }
                }

                // The control's own slot, wedged between Calendar and
                // Insights so that all five positions are the same width.
                // Without it the four tabs were four equal fractions of the
                // whole bar and the hole in the middle overlapped two of them
                // — "Calendar" and "Insights" are the two longest labels in
                // the app, and both were running under the button. Now the
                // plus has a column of its own, exactly centred, and every
                // tab sits a full slot away from it.
                if (index == tabs.size / 2 - 1) {
                    Spacer(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }

        // Drawn last so it sits over the sparkles as well as over the slot
        // the pill reserves for it. The tabs are five equal shares — four of
        // them and one empty — which is what puts this button dead centre
        // and level with the icons either side of it: inside the bar, on
        // their line, part of the row.
        if (showAdd) {
            AddButton(
                onClick = onAdd,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = humpRise + contentY - AddButtonSize / 2f),
            )
        }
    }
}

private data class Tab(val route: Route, val label: String, val glyph: Glyph)

private val tabs = listOf(
    Tab(Route.Today, "Today", Glyph.TODAY),
    Tab(Route.Calendar, "Calendar", Glyph.CALENDAR),
    Tab(Route.Insights, "Insights", Glyph.BARS),
    Tab(Route.Settings, "Profile", Glyph.PERSON),
)

/** The add control's diameter — what the cradle is built around. */
private val AddButtonSize = 48.dp

/**
 * The add control: a circle seated *in* the bar, its centre on the same line
 * as the icons beside it.
 *
 * Two faces, because the bar is two objects in two lights. On paper the
 * control is an ember disc — lit from above, shaded toward the base —
 * carrying a plus extruded in three layers of that same ember, so the only
 * thing with depth on this screen is the thing that makes something. In the
 * dark the disc empties: the bar's own surface shows through an ember ring
 * and the plus inside it is ember too, because a solid dark disc on a dark
 * bar reads as a hole while a lit ring reads as the same object with its
 * light on.
 *
 * [AddButtonSize] is shared with the cradle: the hump is built around this
 * diameter, so the two cannot drift apart and start crowding each other.
 */
@Composable
private fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val light = scheme.background.luminance() >= 0.5f

    // Derived from the scheme rather than from the palette's own ember, so a
    // theme is still one accent and the button cannot end up the one thing on
    // screen that does not follow it. The top stop is the accent toward white,
    // the bottom the same accent toward black: a face that catches light.
    val lit = lerp(scheme.primary, Color.White, 0.30f)
    val shaded = lerp(scheme.primary, Color.Black, 0.26f)
    val rim = Brush.verticalGradient(listOf(lit, scheme.primary, shaded))

    Box(
        modifier = modifier
            .size(AddButtonSize)
            // On paper it lifts off the pill in shadow; in the dark it has no
            // shadow to cast on a bar this near-black, so it lifts in light
            // instead — there the elevation colour *is* the glow.
            .shadow(
                elevation = if (light) 9.dp else 10.dp,
                shape = CircleShape,
                ambientColor = if (light) Color.Black else scheme.primary,
                spotColor = if (light) Color.Black else scheme.primary,
            )
            .clip(CircleShape)
            .then(
                if (light) {
                    Modifier.background(rim)
                } else {
                    Modifier
                        // Opaque so the glow behind this circle cannot shine
                        // through the middle of it: the interior has to stay
                        // the pill's own colour for the ring to read as a
                        // ring and not as a lens.
                        .background(scheme.surfaceContainer)
                        .border(2.5.dp, rim, CircleShape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            // The symbol has no text beside it, so TalkBack had nothing to
            // say but "button". The label is on the control itself, which is
            // what a screen reader reads first.
            .semantics { contentDescription = "Add habit" },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            // Darkest and furthest back first: together the two rear copies
            // read as the side wall of a plus cut out of the face, and the
            // face itself stays perfectly flat. The top layer is the only
            // light between the two states — white on the lit disc, the soft
            // ember in the ring, where white would be a lamp in a bar whose
            // icons are all grey.
            GlyphIcon(
                glyph = Glyph.ADD,
                color = EmberDeep,
                size = 24.dp,
                strokeWidth = 2.7.dp,
                modifier = Modifier.offset(3.dp, 3.4.dp),
            )
            GlyphIcon(
                glyph = Glyph.ADD,
                color = EmberDim,
                size = 24.dp,
                strokeWidth = 2.6.dp,
                modifier = Modifier.offset(1.5.dp, 1.7.dp),
            )
            GlyphIcon(
                glyph = Glyph.ADD,
                color = if (light) scheme.onPrimary else EmberSoft,
                size = 24.dp,
                strokeWidth = 2.4.dp,
            )
        }
    }
}

/** The deepest step of the extrusion — one shade under [EmberDim]. */
private val EmberDeep = Color(0xFF7A2C05)

/**
 * The offer made after a long-press delete or retire: one line, one word, and
 * five seconds you can actually see running out.
 *
 * Deliberately not a [androidx.compose.material3.Snackbar]. The default one
 * paints in the scheme's *inverse* surface, which in Fyr's dark theme is a
 * near-white slab laid across the bottom of a near-black screen — correct
 * Material, and entirely out of family. This is the app's own rim grey with
 * the accent on the action, so it reads as something the screen said rather
 * than as a notification that happened to be routed through it.
 *
 * One ellipsised sentence, because the bar's whole job is to be read in the
 * glance between noticing the row is gone and deciding whether that was a
 * mistake.
 *
 * ── why it counts down out loud ───────────────────────────────────────
 *
 * The window used to be invisible, which quietly turned it into a trap: the
 * row came back if you were quick and vanished if you were slow, and nothing
 * on screen said which. A number beside the word and a line along the bottom
 * make the grace period *countable* — you can watch the time you have rather
 * than infer it from an object disappearing. It runs at constant speed, so
 * the last second is the same length as the first; an eased countdown that
 * lingers on "1" would flatter the offer rather than describe it.
 *
 * The line is scaled rather than resized. Its width changes sixty times a
 * second, and a layout parameter changing that often would put a measure pass
 * on every frame of a bar that is otherwise completely still — this way the
 * only thing that moves is a drawn rectangle.
 */
@Composable
private fun UndoBar(
    message: String,
    token: Long,
    holdMillis: Long,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val totalSeconds = (holdMillis / 1000L).toInt().coerceAtLeast(1)

    // Keyed by identity rather than by message: "Deleted Sprinkle" twice in a
    // row is two different chances to change your mind, and the second one is
    // owed a full five seconds as much as the first.
    val clock = remember(token) { Animatable(1f) }
    LaunchedEffect(token) {
        clock.snapTo(1f)
        clock.animateTo(0f, tween(holdMillis.toInt(), easing = LinearEasing))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                12.dp,
                RoundedCornerShape(16.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHighest),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CountdownDisc(clock = clock, totalSeconds = totalSeconds)
            TextButton(onClick = onUndo) {
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.primary,
                )
            }
        }

        // The window, as a bar along the pill's own bottom edge. The pill's
        // radius clips its ends, so it reads as part of the object instead of
        // as a progress bar bolted underneath one.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(scheme.outlineVariant.copy(alpha = 0.5f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The transform comes before the paint so the colour lives
                    // inside the layer: scaling the background rather than the
                    // node keeps the hairline's own bounds untouched while its
                    // visible length runs from full width down to nothing.
                    .graphicsLayer {
                        scaleX = clock.value
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
                    .background(scheme.primary),
            )
        }
    }
}

/**
 * The seconds left, in a small disc beside the word it belongs to.
 *
 * Its own composable for one reason: reading [Animatable.value] here makes
 * this the only node that recomposes sixty times a second, so the message,
 * the button and the bar's own frame stay out of the churn entirely.
 */
@Composable
private fun CountdownDisc(clock: Animatable<Float, AnimationVector1D>, totalSeconds: Int) {
    val scheme = MaterialTheme.colorScheme
    val left = ceil(clock.value * totalSeconds).toInt().coerceIn(1, totalSeconds)

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(scheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$left",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}
