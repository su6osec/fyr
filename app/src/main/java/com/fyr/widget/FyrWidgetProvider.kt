package com.fyr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.fyr.FyrApp
import com.fyr.R
import com.fyr.data.Dates
import com.fyr.data.Habit

/**
 * The home-screen widget: today's habits with app icon, and a row you can press to tick one
 * without opening anything. Shows all habits for today — done or not — so the widget
 * always reflects the full picture. Tap the logo or any row to open the app.
 *
 * One [RemoteViews] is built and handed to every placed instance, because the
 * widget has no per-instance state — two copies of the same day are the same
 * picture. Rebuilding from the store on every render rather than diffing
 * means a change anywhere (a tick in the app, a new habit, a rollover past
 * midnight) converges on the same answer without anyone having to remember
 * what was last drawn.
 *
 * Clicks deliberately do *not* live here. This receiver is exported because a
 * launcher must be able to reach it, and an exported receiver that will
 * happily toggle habits is a hole anybody could walk through; the toggle
 * lives in [FyrWidgetActions], which nothing outside the app can send to.
 */
class FyrWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_CONFIGURATION_CHANGED,
            -> render(context)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, build(context, rowsThatFit(appWidgetManager, id)))
        }
    }

    // Resizing is its own event: without this, dragging the widget to a
    // smaller cell would keep drawing the row count that cell can no longer
    // hold, and every row would be squeezed into a target too small to hit.
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            build(context, rowsThatFit(appWidgetManager, appWidgetId)),
        )
    }

    companion object {

        const val ACTION_TOGGLE = "com.fyr.action.WIDGET_TOGGLE"
        const val ACTION_OPEN_APP = "com.fyr.action.WIDGET_OPEN_APP"
        const val EXTRA_HABIT = "com.fyr.extra.HABIT_ID"

        /** The most rows the layout carries. Beyond this the count still tells the truth. */
        private const val MAX_ROWS = 5

        /** One row's height in dp — the floor a row has to keep to stay hittable. */
        private const val ROW_HEIGHT_DP = 44

        /** One row of the layout. Ids are per-row: see fyr_widget.xml for why. */
        private class Row(val container: Int, val fireIcon: Int, val name: Int, val mark: Int)

        private val ROWS = listOf(
            Row(R.id.w1, R.id.w1_fire, R.id.w1_name, R.id.w1_mark),
            Row(R.id.w2, R.id.w2_fire, R.id.w2_name, R.id.w2_mark),
            Row(R.id.w3, R.id.w3_fire, R.id.w3_name, R.id.w3_mark),
            Row(R.id.w4, R.id.w4_fire, R.id.w4_name, R.id.w4_mark),
            Row(R.id.w5, R.id.w5_fire, R.id.w5_name, R.id.w5_mark),
        )

        /** Redraws every placed instance. Safe to call with none placed. */
        fun render(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, FyrWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            // Built per instance, not once for all of them: two copies of the
            // widget can be sitting at different sizes, and each one shows as
            // many rows as its own cell can hold at 44dp each.
            ids.forEach { id ->
                manager.updateAppWidget(id, build(context, rowsThatFit(manager, id)))
            }
        }

        /**
         * How many rows fit the space this widget is actually given.
         *
         * The layout distributes its height evenly across five rows, which is
         * right when there is room and wrong when there is not: `minHeight` is
         * not honoured under an exact measure spec, so on a short cell the rows
         * collapsed to a few pixels each — targets nobody can hit and text
         * nobody can read. Sizing the count to the cell means the widget
         * shows fewer rows instead of thinner ones.
         *
         * 76dp is what is not row space: 24 of root padding and 52 for the
         * header (a 44dp logo plus the 8dp under it).
         */
        private fun rowsThatFit(manager: AppWidgetManager, id: Int): Int {
            val height = manager.getAppWidgetOptions(id)
                ?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            if (height <= 0) return MAX_ROWS
            return ((height - 76) / ROW_HEIGHT_DP).coerceIn(1, MAX_ROWS)
        }

        /** The whole widget for one moment in time. */
        fun build(context: Context, maxRows: Int = MAX_ROWS): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.fyr_widget)
            val state = (context.applicationContext as FyrApp).store.state.value
            val today = Dates.today()

            val due = state.dueOn(today)
            val shown = due.take(maxRows)

            views.setTextViewText(
                R.id.widget_title,
                "Fyr  ·  " + Dates.dayTitle(today),
            )
            views.setTextViewText(
                R.id.widget_count,
                when {
                    state.live.isEmpty() -> ""
                    due.isEmpty() -> "No habits"
                    else -> "${due.size} habit${if (due.size > 1) "s" else ""}"
                },
            )
            views.setViewVisibility(
                R.id.widget_empty,
                if (state.live.isEmpty()) View.VISIBLE else View.GONE,
            )

            // Logo button opens the app
            val openAppIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, com.fyr.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_logo, openAppIntent)
            // The empty state tells the user to open Fyr. Saying so and doing
            // nothing is a dead end; the sentence itself is now the way out.
            views.setOnClickPendingIntent(R.id.widget_empty, openAppIntent)

            ROWS.forEachIndexed { index, row ->
                val habit: Habit? = shown.getOrNull(index)
                if (habit == null) {
                    views.setViewVisibility(row.container, View.GONE)
                    return@forEachIndexed
                }

                val done = state.isDone(habit.id, today)
                views.setViewVisibility(row.container, View.VISIBLE)
                // Use app icon for fire icon
                views.setImageViewResource(row.fireIcon, R.mipmap.ic_launcher)
                views.setTextViewText(row.name, habit.name)
                views.setTextViewText(row.mark, if (done) "✓" else "○")
                views.setTextColor(
                    row.name,
                    ContextCompat.getColor(
                        context,
                        if (done) R.color.widget_muted else R.color.widget_ink,
                    ),
                )
                views.setTextColor(
                    row.mark,
                    ContextCompat.getColor(
                        context,
                        if (done) R.color.widget_accent else R.color.widget_muted,
                    ),
                )
                // The whole row toggles the habit
                views.setOnClickPendingIntent(row.container, toggleIntent(context, habit.id))
                // The mark button also toggles the habit (for accessibility)
                views.setOnClickPendingIntent(row.mark, toggleIntent(context, habit.id))
            }

            return views
        }

        private fun toggleIntent(context: Context, habitId: String): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                habitId.hashCode(),
                Intent(context, FyrWidgetActions::class.java)
                    .setAction(ACTION_TOGGLE)
                    .putExtra(EXTRA_HABIT, habitId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}