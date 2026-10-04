package com.fyr.domain

import com.fyr.data.Category
import com.fyr.data.FyrState
import com.fyr.data.Habit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Streaks, interruptions and rates — the arithmetic nobody looks at until it
 * is wrong on screen.
 *
 * Dates are October 2026 throughout: Thu 1 Oct is epoch day **20727**, so
 * Mon 28 Sep = 20724, Sat 3 Oct = 20729, Sun 4 = 20730, Mon 5 = 20731,
 * Tue 6 = 20732, Wed 7 = 20733, Thu 8 = 20734, Fri 9 = 20735, Mon 12 = 20738.
 */
class StatsTest {

    private val thu1 = 20727

    private fun daily(
        id: String = "d",
        createdAt: Int = thu1,
        archived: Boolean = false,
        skipDays: Set<Int> = emptySet(),
    ) = Habit(
        id = id,
        name = "Water",
        emoji = "💧",
        category = Category.HEALTH,
        createdAt = createdAt,
        weekdays = emptySet(), // empty = every day
        archived = archived,
        skipDays = skipDays,
    )

    // ── Time away ───────────────────────────────────────────────────────

    @Test
    fun `a three-times-a-week habit can report an interruption at all`() {
        // M/W/F, created Mon 28 Sep, never ticked, today Mon 12 Oct.
        // Measured in calendar days the old walk saw isolated single misses
        // (Tue/Thu/Sun closed every run) and returned nothing — so the card
        // told someone five weeks absent that they had "kept going".
        val mwf = Habit(
            id = "m",
            name = "Gym",
            emoji = "🏋️",
            category = Category.SPORTS,
            createdAt = 20724,
            weekdays = setOf(1, 3, 5),
        )

        val away = Stats.aways(mwf, emptySet(), today = 20738)

        assertEquals(1, away.size)
        val stretch = away.first()
        assertEquals(20724, stretch.from, "first due day missed: Mon 28 Sep")
        assertEquals(20738, stretch.to, "still open, so it runs through today")
        assertTrue(stretch.ongoing)
        assertTrue(stretch.days >= Stats.MIN_AWAY_DAYS)
    }

    @Test
    fun `today in progress is not yet a day anyone failed`() {
        // Ticked Thu 1 and Fri 2; missed Sat 3 – Mon 5; today Tue 6 is due
        // and still open. Three *finished* missed days, with today judged
        // exactly as the streak judges it — in progress, not failed.
        val h = daily()
        val done = setOf(thu1, thu1 + 1)

        val away = Stats.aways(h, done, today = 20732)

        assertEquals(1, away.size)
        assertEquals(20729, away.first().from)
        assertEquals(20732, away.first().to, "open stretches run through today")
        assertTrue(away.first().ongoing)
        assertEquals(4, away.first().days, "Sat 3 → today, as a person would count it")
    }

    @Test
    fun `a completed due day closes the stretch for good`() {
        val h = daily()
        // Missed Sat 3 – Mon 5, ticked Tue 6, today Wed 7.
        val done = setOf(thu1, thu1 + 1, 20732)

        val away = Stats.aways(h, done, today = 20733)

        assertEquals(1, away.size)
        assertEquals(20729, away.first().from)
        assertEquals(20731, away.first().to, "closed stretches end at the last miss")
        assertEquals(3, away.first().days)
        assertFalse(away.first().ongoing)
    }

    @Test
    fun `a frozen day neither opens nor closes a stretch`() {
        // Sat 3 excused; missed Sun 4, Mon 5, Tue 6 → one three-day stretch,
        // not a one-day run stopped dead by an excuse. The old walk closed
        // every run on any non-due day, freezes included.
        val h = daily(skipDays = setOf(20729))
        val done = setOf(thu1, thu1 + 1)

        val away = Stats.aways(h, done, today = 20733)

        assertEquals(1, away.size)
        assertEquals(20730, away.first().from, "Sun 4 — the first *missed due* day")
    }

    @Test
    fun `a retired habit is finished, not away`() {
        val h = daily(archived = true)
        val done = setOf(thu1, thu1 + 1)

        val away = Stats.aways(h, done, today = 20732)

        assertEquals(1, away.size)
        assertFalse(away.first().ongoing, "retired habits do not owe anyone a day")
        assertEquals(20731, away.first().to, "and their range stops at the last miss")
    }

    @Test
    fun `two ordinary missed days are the streak's own story, not a card`() {
        val h = daily()
        val done = setOf(thu1, thu1 + 1)
        // Missed Sat 3, Sun 4; today Mon 5 in progress → two finished days.
        val away = Stats.aways(h, done, today = 20731)
        assertTrue(away.isEmpty())
    }

    // ── streaks ─────────────────────────────────────────────────────────

    @Test
    fun `an un-ticked due today is judged through yesterday`() {
        val h = daily()
        val done = (thu1 until 20731).toSet() // ticked Thu 1 … Sun 4; Mon 5 open

        assertEquals(4, Stats.currentStreak(h, done, today = 20731))
        assertEquals(thu1, Stats.streakStart(h, done, today = 20731))
    }

    @Test
    fun `one missed due day ends the run behind it, strictly`() {
        val h = daily()
        // Fri 2 missed, Sat 3 (today) still open → judged from Fri → zero.
        assertEquals(0, Stats.currentStreak(h, setOf(thu1), today = thu1 + 2))
        // Sat 3 ticked after the miss is a new run of one, today only.
        assertEquals(1, Stats.currentStreak(h, setOf(thu1, thu1 + 2), today = thu1 + 2))
    }

    @Test
    fun `a freeze walks past without breaking the run`() {
        val h = daily(skipDays = setOf(thu1 + 1)) // Fri 2 excused
        val done = setOf(thu1, thu1 + 2) // Thu 1, Sat 3 ticked; Sun 4 today

        assertEquals(2, Stats.currentStreak(h, done, today = thu1 + 3))
        assertEquals(2, Stats.bestStreak(h, done, today = thu1 + 3))
    }

    // ── rates: today counts once it is settled ──────────────────────────

    @Test
    fun `an un-ticked today leaves the rate untouched instead of reading as a miss`() {
        val h = daily()
        val state = FyrState(
            habits = listOf(h),
            completions = mapOf(h.id to (thu1..20731).toSet()), // Thu 1 … Mon 5 all ticked
        )

        val stats = Stats.statsFor(state, h, today = 20732) // Tue 6, due, not yet ticked

        assertEquals(5, stats.currentStreak, "judged through yesterday")
        assertEquals(1f, stats.rate30, 0.001f, "today in progress is not a miss")
        assertEquals(5, stats.days30)
        assertEquals(1f, stats.rateAll, 0.001f)
    }

    @Test
    fun `a settled today counts like every other day`() {
        val h = daily()
        val state = FyrState(
            habits = listOf(h),
            completions = mapOf(h.id to (thu1..20732).toSet()), // …and today ticked too
        )

        val stats = Stats.statsFor(state, h, today = 20732)

        assertEquals(6, stats.currentStreak)
        assertEquals(1f, stats.rate30, 0.001f)
        assertEquals(6, stats.days30)
    }

    @Test
    fun `the all-habits rate leaves an in-progress day out of the denominator`() {
        val h = daily()
        val due5 = FyrState(
            habits = listOf(h),
            completions = mapOf(h.id to (thu1..20731).toSet()),
        )

        val morning = Stats.overallRate(due5, today = 20732)
        assertEquals(5, morning.due, "the open day is not yet owed")
        assertEquals(5, morning.hit)

        val evening = Stats.overallRate(
            due5.copy(completions = mapOf(h.id to (thu1..20732).toSet())),
            today = 20732,
        )
        assertEquals(6, evening.due)
        assertEquals(6, evening.hit)
    }

    @Test
    fun `nothing due reads as nothing, not as zero`() {
        val h = daily(createdAt = 20732, skipDays = setOf(20732)) // born today, excused today
        val state = FyrState(habits = listOf(h))

        val stats = Stats.statsFor(state, h, today = 20732)
        assertEquals(-1f, stats.rate30, 0.001f)
        assertEquals(0, stats.days30)
        assertEquals(0, stats.currentStreak)
    }
}
