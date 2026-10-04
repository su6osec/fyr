package com.fyr.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fyr.FyrApp
import com.fyr.data.Dates

/**
 * The widget's one action: tick the habit whose row was pressed, then redraw.
 *
 * Kept in a receiver of its own, and kept **not exported**, on purpose. The
 * provider has to be reachable by the launcher and therefore by anyone; if
 * the toggle lived there too, any app on the phone could send it an explicit
 * broadcast and start ticking habits on the owner's behalf. A PendingIntent
 * raised by Fyr carries Fyr's own identity, so this component never needs to
 * be open to anyone else to do its job.
 *
 * The day is read at the moment of the press rather than carried in the
 * intent. A widget sits on a home screen for days: an intent holding
 * yesterday's date would, at midnight, tick the wrong day.
 */
class FyrWidgetActions : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FyrWidgetProvider.ACTION_TOGGLE) return
        val habitId = intent.getStringExtra(FyrWidgetProvider.EXTRA_HABIT) ?: return

        val store = (context.applicationContext as FyrApp).store
        val today = Dates.today()
        // Deleted from under a stale widget, or pressed before the redraw
        // that follows midnight. Either way the row is showing a habit that is
        // not due *today*, and ticking it would write a completion on a day
        // nothing was ever asked for — history the calendar would then have to
        // explain. Ignore it; the redraw below is what fixes the row.
        val habit = store.state.value.habits.firstOrNull { it.id == habitId }
        if (habit == null || !habit.isDueOn(today)) {
            FyrWidgetProvider.render(context)
            return
        }

        store.toggle(habitId, today)
        FyrWidgetProvider.render(context)
    }
}
