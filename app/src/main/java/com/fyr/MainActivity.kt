package com.fyr

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import com.fyr.data.Theme
import com.fyr.ui.nav.FyrRoot
import com.fyr.ui.update.UpdateGate

/**
 * The only activity in the app.
 *
 * Edge-to-edge is enabled so the screen colour runs unbroken behind the system
 * bars — on a near-black ground the alternative is a visibly lighter strip
 * across the top, which is the sort of thing that makes an interface feel
 * assembled rather than designed. The bar icon contrast follows the system's
 * dark setting, because that is what Fyr renders — there is no stored theme of
 * its own to disagree with it.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The system gesture bar must not draw its own background — the pill
        // is the only thing at the bottom. Transparent nav bar + no contrast
        // enforcement keeps the page colour clean behind it in both lights.
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false)
        }

        val store = (application as FyrApp).store

        // The launch logo is a *process* event, not an activity one: rotating
        // the phone, or returning from the recents tray, rebuilds this
        // activity inside a process that is already running and should not
        // play it a second time.
        val launchLogo = !launchedOnce
        launchedOnce = true

        // This activity is exported — it has to be, it is the launcher entry —
        // so anything on the phone can start it with whatever extras it likes.
        // The verification hooks below only *navigate* and open dialogs, so
        // they were never a way to read or alter data; but an app that can
        // steer another app's UI into a specific screen is still an app
        // driving yours, and there is no reason for a build anyone installs
        // for real to keep answering. They are therefore read only from a
        // debuggable build, which is the build the hooks exist for.
        val hooks = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val addSpec = if (hooks) intent.getStringExtra("fyr_add") else null

        setContent {
            // The gate is outermost on purpose. Everything below it — the
            // side effects, the shell, every route and dialog — is behind a
            // single `when`, so on a build that owes an update none of it is
            // ever composed, let alone reachable. Wrapping here rather than
            // inside FyrRoot is what keeps that guarantee from depending on
            // the shell remembering to ask.
            UpdateGate {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    val light = Theme.fromUiMode(resources.configuration.uiMode) == Theme.LIGHT
                    controller.isAppearanceLightStatusBars = light
                    controller.isAppearanceLightNavigationBars = light
                }

                FyrRoot(
                    store = store,
                    initial = if (hooks) routeFrom(intent, store) else com.fyr.ui.nav.Route.Today,
                    initialAdd = addSpec == "true" || addSpec == "write" || addSpec == "date",
                    initialWrite = addSpec == "write" || addSpec == "date",
                    initialDatePicker = addSpec == "date",
                    initialEdit = hooks && intent.getStringExtra("fyr_edit") == "true",
                    initialCelebrate = hooks && intent.getStringExtra("fyr_celebrate") == "true",
                    initialProfileEdit = hooks && intent.getStringExtra("fyr_profile_edit") == "true",
                    // First-launch welcome, previewable the same way every other
                    // one-shot screen in this activity is: a hook, debuggable
                    // builds only, that opens the gate without uninstalling.
                    initialWelcome = hooks && intent.getStringExtra("fyr_welcome") == "true",
                    // The setup form that follows the welcome, previewed the
                    // same way. Forced this way it is dismissible with back —
                    // that is the hook's privilege, not the real gate's: the
                    // unanswered one never opens outward.
                    initialSetup = hooks && intent.getStringExtra("fyr_setup") == "true",
                    launchLogo = launchLogo,
                )
            }
        }
    }

    private companion object {
        /** Set the first time this process builds the activity. */
        var launchedOnce = false
    }

    /**
     * `adb shell am start ... -e fyr_route calendar` opens on a given screen;
     * `-e fyr_route detail` opens the first habit, `-e fyr_route trophies`
     * the trophy cabinet.
     *
     * `-e fyr_add true` opens the sheet on the suggestion shelf and `write`
     * opens it with the write-your-own form already up — the started-on picker
     * is inline in that form, so `date` is accepted as a synonym for `write`.
     * `-e fyr_edit true` opens the detail screen of the first habit with its
     * edit dialog already up, and `-e fyr_profile_edit true` opens Profile
     * with the edit-profile dialog up. `-e fyr_welcome true` opens the
     * first-launch welcome and `-e fyr_setup true` the name-and-picture
     * form that follows it.
     *
     * A verification hook, not a feature: the handset is driven by hand, so
     * this is the only way anything other than Today can be inspected — and
     * captured — without someone tapping through it first. It is read only
     * when the build is debuggable; see [onCreate] for why.
     */
    private fun routeFrom(intent: android.content.Intent, store: com.fyr.data.Store) =
        when (intent.getStringExtra("fyr_route")) {
            "calendar" -> com.fyr.ui.nav.Route.Calendar
            "insights" -> com.fyr.ui.nav.Route.Insights
            "settings" -> com.fyr.ui.nav.Route.Settings
            "trophies" -> com.fyr.ui.nav.Route.Trophies
            "detail" -> store.state.value.habits
                .firstOrNull { !it.archived }
                ?.let { com.fyr.ui.nav.Route.Detail(it.id) }
                ?: com.fyr.ui.nav.Route.Today

            else -> com.fyr.ui.nav.Route.Today
        }
}
