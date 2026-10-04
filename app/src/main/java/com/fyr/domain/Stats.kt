package com.fyr.domain

import com.fyr.data.Dates
import com.fyr.data.FyrState
import com.fyr.data.Habit
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Every number Fyr shows.
 *
 * Two rules run through all of it:
 *
 * 1. **Strict** — a missed due day ends the streak. There is no grace day and
 *    no carry-over; that is the product decision, and the maths never softens it.
 *
 * 2. **Completion rate sits next to the streak, always.** A streak resets to
 *    zero and tells you nothing about the 29 days you did show up. The rate
 *    keeps those. Showing both is what stops a single miss from reading as
 *    total failure — which is the moment people abandon trackers.
 *
 * Day-of-week is integer maths rather than [java.time.LocalDate] because the
 * heatmap and the rankings walk thousands of days per frame.
 */
object Stats {

    /** A completion-rate measurement over an inclusive range. */
    data class Rate(val due: Int, val hit: Int) {
        /** -1 when nothing was due in range, so callers can distinguish "none" from "zero". */
        val fraction: Float get() = if (due == 0) -1f else hit.toFloat() / due
    }

    /**
     * One stretch of not showing up, in the terms a person would use it.
     *
     * A streak reset already says *that* something stopped; it never says
     * *when* or *for how long*, and "my streak is 1" tells nobody that the
     * reason was three weeks in bed. This is the record of the interruption
     * itself — the dates it spanned, how long it ran, and whether it is still
     * open.
     *
     * Derived from the ticks alone rather than stored: the history already
     * contains every one of these facts, and a second, editable copy of the
     * truth is how two copies start disagreeing.
     */
    data class Away(
        /** First day missed. */
        val from: Int,
        /** Last day of the stretch — today, while it is still running. */
        val to: Int,
        /** [from] to [to] inclusive, which is how long a person would say they were away. */
        val days: Int,
        /**
         * No completed due day has ended the stretch — it runs through
         * today, so they are away right now. Never true for a retired
         * habit: finished, not away.
         */
        val ongoing: Boolean,
    )

    data class HabitStats(
        val habit: Habit,
        val currentStreak: Int,
        val bestStreak: Int,
        val total: Int,
        /** Trailing 30 days; -1 when nothing was due. */
        val rate30: Float,
        /** How many due days that rate rests on — shown alongside so 3/3 isn't sold as 100%. */
        val days30: Int,
        /** Since creation; -1 when none. */
        val rateAll: Float,
        /** Last 7 days minus the 7 before, in percentage points. */
        val trendPoints: Float,
        val trendValid: Boolean,
    )

    data class TodayProgress(val done: Int, val total: Int) {
        val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
        val percent: Int get() = (fraction * 100).roundToInt()
    }

    // ── streaks ──────────────────────────────────────────────────────────

    /**
     * Consecutive due days completed, counting back from [today].
     *
     * Today is treated as still in progress: if it is due but unticked, the
     * streak is judged through yesterday rather than being already broken. The
     * day ends, the tick does not arrive, and yesterday's walk-back breaks it —
     * strict, but the verdict lands when the day is actually over.
     */
    fun currentStreak(h: Habit, done: Set<Int>, today: Int): Int {
        var day = if (h.isDueOn(today) && !done.contains(today)) today - 1 else today
        var count = 0
        while (day >= h.createdAt) {
            if (h.isDueOn(day)) {
                if (done.contains(day)) count++ else break
            }
            day--
        }
        return count
    }

    /**
     * Shortest run of missed due days that is still worth calling an
     * interruption. Two — a single missed day is the streak's own story and
     * already has a card; so is a pair, which is the shape of an ordinary
     * weekend off. Three is where a gap starts to be worth saying out loud.
     */
    const val MIN_AWAY_DAYS: Int = 3

    /**
     * Every stretch of at least [minDays] missed due days, newest first.
     *
     * Measured in **due** days so a habit scheduled three times a week does
     * not report a holiday every weekend — a day the schedule never asked
     * for closes nothing and opens nothing; only a completed due day ends a
     * stretch. Printed as the calendar dates it actually spanned — because
     * "21 Sep – 1 Oct" is the range a person remembers, whereas "6 of 8
     * sessions" is a number they have to work out.
     *
     * Derived, never stored: the ticks already contain every one of these
     * facts, and a second editable copy is how two copies start disagreeing.
     */
    fun aways(
        h: Habit,
        done: Set<Int>,
        today: Int,
        minDays: Int = MIN_AWAY_DAYS,
    ): List<Away> {
        val out = mutableListOf<Away>()
        var runStart = -1
        var runLast = -1
        var runLen = 0

        // Today is still in progress, exactly as the streak treats it: a due
        // day that has not been ticked yet is not a day anyone has failed,
        // and this card must never contradict the streak above it.
        val todayInProgress = h.isDueOn(today) && !done.contains(today)

        fun close(open: Boolean) {
            if (runLen >= minDays) {
                // Open means no completed due day has ended the stretch —
                // they are still away, and the range runs through today.
                // A retired habit is not away, it is finished: it stopped
                // and the dates say so, without claiming it still owes
                // anyone a day.
                val stillAway = open && !h.archived
                val last = if (stillAway) today else runLast
                out += Away(
                    from = runStart,
                    to = last,
                    days = last - runStart + 1,
                    ongoing = stillAway,
                )
            }
            runStart = -1
            runLast = -1
            runLen = 0
        }

        var day = h.createdAt
        while (day <= today) {
            when {
                // Not on the books — a weekend between sessions, a freeze, a
                // day off. It closes nothing: that is the entire difference
                // between measuring in due days and counting calendar days,
                // and it is why a three-times-a-week habit can report an
                // interruption at all.
                !h.isDueOn(day) -> Unit
                day == today && todayInProgress -> Unit
                // They showed up. Whatever was owed before this is settled.
                done.contains(day) -> close(open = false)
                else -> {
                    if (runStart < 0) runStart = day
                    runLast = day
                    runLen++
                }
            }
            day++
        }
        close(open = true)
        return out.asReversed()
    }

    /** Longest unbroken run of due days ever, from creation to today. */
    fun bestStreak(h: Habit, done: Set<Int>, today: Int): Int {
        var best = 0
        var run = 0
        var day = h.createdAt
        while (day <= today) {
            if (h.isDueOn(day)) {
                run = if (done.contains(day)) run + 1 else 0
                if (run > best) best = run
            }
            day++
        }
        return best
    }

    /**
     * The first day of the streak that is running right now — where the run
     * actually started, not when the habit was created.
     *
     * Returns [Habit.createdAt] when nothing is running, so a caption asking
     * "since when" always has an honest answer instead of a dash.
     */
    fun streakStart(h: Habit, done: Set<Int>, today: Int): Int {
        val streak = currentStreak(h, done, today)
        if (streak <= 0) return h.createdAt

        var day = if (h.isDueOn(today) && !done.contains(today)) today - 1 else today
        var counted = 0
        while (day >= h.createdAt) {
            if (h.isDueOn(day)) {
                if (!done.contains(day)) break
                counted++
                if (counted == streak) return day
            }
            day--
        }
        return h.createdAt
    }

    /** Completed days that were genuinely due — backfilled or spurious days don't count. */
    fun total(h: Habit, done: Set<Int>): Int =
        done.count { it >= h.createdAt && h.isDueOn(it) }

    // ── rates ────────────────────────────────────────────────────────────

    fun rate(h: Habit, done: Set<Int>, from: Int, to: Int): Rate {
        if (to < from) return Rate(0, 0)
        var due = 0
        var hit = 0
        var d = maxOf(from, h.createdAt)
        while (d <= to) {
            if (h.isDueOn(d)) {
                due++
                if (done.contains(d)) hit++
            }
            d++
        }
        return Rate(due, hit)
    }

    // ── per habit ────────────────────────────────────────────────────────

    fun statsFor(s: FyrState, h: Habit, today: Int): HabitStats {
        val done = s.completionsFor(h.id)

        // Today counts once it is *settled*. A due day still in progress is
        // excluded from every window below — the same grace the streak grants
        // it — so an un-ticked morning cannot read as a miss beside a streak
        // that is still standing, and the ranking cannot reshuffle itself
        // through the day. Ticked (or not due at all) means today counts.
        val settled = if (h.isDueOn(today) && !done.contains(today)) today - 1 else today

        val r30 = rate(h, done, today - 29, settled)
        val rAll = rate(h, done, h.createdAt, settled)

        val recent = rate(h, done, today - 6, settled)
        val prior = rate(h, done, today - 13, today - 7)
        val trendValid = recent.due >= 5 && prior.due >= 5

        return HabitStats(
            habit = h,
            currentStreak = currentStreak(h, done, today),
            bestStreak = bestStreak(h, done, today),
            total = total(h, done),
            rate30 = r30.fraction,
            days30 = r30.due,
            rateAll = rAll.fraction,
            trendPoints = if (trendValid) (recent.fraction - prior.fraction) * 100f else 0f,
            trendValid = trendValid,
        )
    }

    fun ranked(s: FyrState, today: Int): List<HabitStats> =
        s.live.map { statsFor(s, it, today) }

    /** Highest 30-day rate first; ties break on the live streak. */
    fun topPerformers(s: FyrState, today: Int, limit: Int = 3): List<HabitStats> =
        ranked(s, today)
            .filter { it.days30 > 0 }
            .sortedWith(
                compareByDescending<HabitStats> { it.rate30 }
                    .thenByDescending { it.currentStreak }
            )
            .take(limit)

    /**
     * Lowest 30-day rate — but only habits with at least a week of due days
     * behind them, and only ones that actually have room to improve. Ranking a
     * two-day-old habit as "struggling" is noise; naming a habit sitting at
     * 100% as needing attention is worse than noise, it is wrong.
     *
     * The [exclude] set is the top performers already shown above — a habit
     * cannot be both a star and a struggler at once, so we remove any overlap
     * before the list is built.
     */
    fun needsAttention(
        s: FyrState,
        today: Int,
        limit: Int = 3,
        exclude: Set<String> = emptySet(),
    ): List<HabitStats> =
        ranked(s, today)
            .filter { it.habit.id !in exclude }
            .filter { it.days30 >= 7 && it.rate30 < 0.999f }
            .sortedWith(compareBy<HabitStats> { it.rate30 }.thenBy { it.currentStreak })
            .take(limit)

    fun today(s: FyrState, day: Int): TodayProgress {
        val due = s.dueOn(day)
        return TodayProgress(due.count { s.isDone(it.id, day) }, due.size)
    }

    // ── patterns ─────────────────────────────────────────────────────────

    /** All-habits completion rate across the trailing [window] days. */
    fun overallRate(s: FyrState, today: Int, window: Int = 30): Rate {
        var due = 0
        var hit = 0
        var d = today - window + 1
        while (d <= today) {
            s.dueOn(d).forEach { h ->
                // Today is counted only once it is settled: a due day still
                // in progress leaves the denominator here exactly the way it
                // leaves every per-habit window above — otherwise the figure
                // on the profile contradicts the streaks under it every
                // morning until the first tick lands.
                if (d == today && !s.isDone(h.id, d)) return@forEach
                due++
                if (s.isDone(h.id, d)) hit++
            }
            d++
        }
        return Rate(due, hit)
    }

    /**
     * Completion ratio for every day in `[from, to]`, inclusive.
     * -1 means nothing was due, which the calendar renders as an empty day
     * rather than as zero.
     */
    fun ratios(s: FyrState, from: Int, to: Int): Map<Int, Float> {
        if (to < from) return emptyMap()
        val map = HashMap<Int, Float>(to - from + 1)
        var d = from
        while (d <= to) {
            val due = s.dueOn(d)
            map[d] = if (due.isEmpty()) {
                -1f
            } else {
                due.count { s.isDone(it.id, d) }.toFloat() / due.size
            }
            d++
        }
        return map
    }

    // ── heatmap ──────────────────────────────────────────────────────────

    /**
     * One day of the grid.
     *
     * [frozen] marks an excused (skipped) day — work was on the books and
     * it was let go. That is a different story from a bare "nothing was
     * due", and it gets its own ice colour for it.
     */
    data class HeatCell(val day: Int, val ratio: Float, val frozen: Boolean = false) {
        /**
         * -1 nothing due (bare track), 0 due but nothing done, 1..4 intensity.
         * Mirrors the five-state contribution graph so the legend reads
         * instantly to anyone who has used one. An excused day keeps -1: a
         * skip must never read as a miss.
         */
        val level: Int
            get() = when {
                ratio < 0f -> -1
                ratio <= 0f -> 0
                else -> ceil(ratio * 4).toInt().coerceIn(1, 4)
            }
    }

    /**
     * [weeks] columns ending with the week containing [endDay]; rows run from
     * [weekStart] down to the day before it. Index order is column-major so the
     * grid can be fed straight into a LazyVerticalGrid.
     */
    fun heatmap(s: FyrState, endDay: Int, weeks: Int, weekStart: Int): List<HeatCell> {
        val openOfEnd = Dates.weekStartOf(endDay, weekStart)
        val start = openOfEnd - (weeks - 1) * 7
        return buildList(weeks * 7) {
            for (col in 0 until weeks) {
                val weekStart = start + col * 7
                for (row in 0 until 7) {
                    add(cell(s, weekStart + row, endDay))
                }
            }
        }
    }

    private fun cell(s: FyrState, day: Int, endDay: Int): HeatCell {
        if (day > endDay) return HeatCell(day, -1f)
        // Frozen wins over completion here exactly as it does in a single
        // habit's heatmap and in the month grid: an excused day reads as
        // excused even when the habits that stayed behind still ticked.
        // Scheduled *and* skipped by some live habit — the same test the
        // calendar's ice and the day detail's frozen count read, so one day
        // carries one verdict everywhere it is shown, and the legend's ice
        // count can never disagree with the strip below it. A day nobody was
        // ever excused from stays bare: ice is for an excuse, and there was
        // nobody to excuse.
        if (s.live.any { it.isScheduledOn(day) && it.isSkipDay(day) }) {
            return HeatCell(day, -1f, frozen = true)
        }
        val due = s.dueOn(day)
        if (due.isNotEmpty()) {
            val hit = due.count { s.isDone(it.id, day) }
            return HeatCell(day, hit.toFloat() / due.size)
        }
        return HeatCell(day, -1f)
    }

    /**
     * Completion for a single habit across the same grid shape — used by the
     * habit detail screen, where "all habits" would answer the wrong question.
     *
     * For one habit the ratio is necessarily binary: it was due and it was
     * done, or it was due and it was not. Nothing due is -1, so gaps in a
     * schedule read as gaps rather than as failures.
     */
    fun habitRatio(s: FyrState, h: Habit, day: Int, endDay: Int = Int.MAX_VALUE): Float = when {
        day > endDay -> -1f
        !h.isDueOn(day) -> -1f
        s.isDone(h.id, day) -> 1f
        else -> 0f
    }

    fun heatmapFor(
        s: FyrState,
        h: Habit,
        endDay: Int,
        weeks: Int,
        weekStart: Int,
    ): List<HeatCell> {
        val openOfEnd = Dates.weekStartOf(endDay, weekStart)
        val start = openOfEnd - (weeks - 1) * 7
        return buildList(weeks * 7) {
            for (col in 0 until weeks) {
                val weekStart = start + col * 7
                for (row in 0 until 7) {
                    val day = weekStart + row
                    // Frozen wins over completion here exactly as it does in
                    // the month grid: an excused day reads as excused even
                    // when a tick landed on it. Scheduled *and* skipped: ice
                    // is for a day the habit was let go from, and a skip
                    // sitting on a day the schedule never asked for let go
                    // of nothing — that square stays bare, the same way the
                    // aggregate grid's frozen test reads.
                    if (day <= endDay && h.isScheduledOn(day) && h.isSkipDay(day)) {
                        add(HeatCell(day, -1f, frozen = true))
                    } else {
                        add(HeatCell(day, habitRatio(s, h, day, endDay)))
                    }
                }
            }
        }
    }

    /**
     * How many days a habit has been excused through [today] — the frozen
     * count, stated rather than coloured.
     *
     * Only days the schedule actually asked for count, for the same reason
     * the heatmap only ices them: a skip on a day that was never due is a
     * skip from nothing. Recurring skips earn a day each time their weekday
     * comes round, because each occurrence excused a real expectation — and
     * that is exactly what the ice under a weekly habit is a picture of.
     *
     * Counted from [Habit.createdAt] rather than stored: the skips are
     * already the record, and a second editable copy of a fact is how two
     * copies start disagreeing.
     */
    fun frozenDays(h: Habit, today: Int): Int {
        var count = 0
        var day = h.createdAt
        while (day <= today) {
            if (h.isScheduledOn(day) && h.isSkipDay(day)) count++
            day++
        }
        return count
    }

    /** [frozenDays] over every live habit — the number the whole app can show. */
    fun frozenDaysTotal(s: FyrState, today: Int): Int =
        s.live.sumOf { frozenDays(it, today) }

    // ── formatting ───────────────────────────────────────────────────────

    /** "86%", or an em dash where there is no sample to report. */
    fun percent(fraction: Float): String =
        if (fraction < 0f) "—" else "${(fraction * 100).roundToInt()}%"

    /** Signed trend with a real arrow, e.g. "+12", "-8", "±0". */
    fun trend(points: Float): String {
        val p = points.roundToInt()
        return when {
            p > 0 -> "+$p"
            p < 0 -> "$p"
            else -> "±0"
        }
    }
}
