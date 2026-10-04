package com.fyr.data

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Groupings used by the prebuilt suggestion shelf, in the order they are shown. */
enum class Category(val label: String) {
    HEALTH("Health"),
    SPORTS("Sports"),
    LIFESTYLE("Lifestyle"),
    MIND("Mind growth"),
    QUIT("Break bad habits"),
}

/**
 * Exactly two themes exist in Fyr: light and dark.
 *
 * There is no picker and no stored preference. The system's dark setting is
 * the single source of truth, read here so that the activity's bar-contrast
 * pass and the Compose tree resolve it through one function rather than two
 * near-identical `uiMode` checks that could drift apart.
 */
enum class Theme {
    LIGHT, DARK;

    companion object {
        fun fromUiMode(uiMode: Int): Theme {
            val night = uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return if (night == android.content.res.Configuration.UI_MODE_NIGHT_YES) DARK else LIGHT
        }
    }
}

/**
 * A single tracked habit.
 *
 * [weekdays] uses ISO numbering, 1 = Monday .. 7 = Sunday. An empty set means
 * "every day", which is what every prebuilt suggestion uses.
 *
 * [createdAt] is an epoch day; nothing before it is ever counted, so a habit
 * added mid-week cannot report a streak for days that predate it.
 *
 * Completion always means *success* — for a habit you are trying to break, the
 * suggestion is phrased as the behaviour you want ("No smoking"), so a tick
 * always reads "I did the right thing today".
 *
 * [skipDays] are specific epoch days marked as excused — they don't break the
 * streak and don't count as missed. A skip day is a deliberate "this day
 * doesn't count" for holidays, illness, or planned rest.
 *
 * [skipWeekdays] are recurring weekdays that are always excused (e.g., Sunday
 * for a gym habit that runs Mon–Sat). They behave like skip days but repeat
 * on a weekly schedule.
 */
data class Habit(
    val id: String,
    val name: String,
    val emoji: String,
    val category: Category,
    val createdAt: Int,
    val weekdays: Set<Int> = DEFAULT_WEEK,
    val archived: Boolean = false,
    val skipDays: Set<Int> = emptySet(),
    val skipWeekdays: Set<Int> = emptySet(),
) {
    /**
     * Whether this habit was expected on [epochDay].
     *
     * Deliberately archive-blind: this is pure schedule, so history, streaks
     * and completion rates keep working for a retired habit. Archives are
     * filtered by [FyrState.live] instead, at the point of display.
     *
     * Skip days (both one-off and recurring) are treated as *not due* — they
     * don't break the streak, don't count as missed, and don't show up in
     * completion rates. They are simply days the habit pauses.
     */
    fun isDueOn(epochDay: Int): Boolean = !isSkipDay(epochDay) && isScheduledOn(epochDay)

    /**
     * Whether the schedule alone names [epochDay] — skips set aside.
     *
     * The calendar half of [isDueOn], split out because an excused day is
     * still a day the schedule asked for, and that is exactly what tells a
     * frozen day apart from a day that was never on the books (the heatmap
     * colours the two differently). [isDueOn] consults the skips *before*
     * this runs, so the empty-week fall-through below can never answer
     * "due" for an every-day habit that was actually excused — the one
     * thing the contract above promises it never does.
     */
    fun isScheduledOn(epochDay: Int): Boolean {
        if (epochDay < createdAt) return false
        if (weekdays.isEmpty()) return true
        return weekdays.contains(Dates.dayOfWeek(epochDay))
    }

    /**
     * Whether [epochDay] is a skip day for this habit (one-off or recurring).
     */
    fun isSkipDay(epochDay: Int): Boolean =
        skipDays.contains(epochDay) || skipWeekdays.contains(Dates.dayOfWeek(epochDay))

    companion object {
        val DEFAULT_WEEK: Set<Int> = (1..7).toSet()
    }
}

/**
 * A habit that was deleted, kept rather than destroyed.
 *
 * The whole record — habit, its ticks and the day it was let go — is carried
 * intact so a restore puts back exactly what was removed, history included. A
 * delete that silently burns the record is the one action in this app with no
 * undo past the five-second bar, and five seconds is not how long a person
 * takes to notice they deleted the wrong thing.
 *
 * [deletedAt] is an epoch day rather than a timestamp: the trash is not a
 * forensic log, and "3 days ago" is the only precision anyone reads off it.
 *
 * [index] is where the habit sat in the list when it went. Position is part
 * of what the user was looking at — the row they pulled out of the middle —
 * so a restore that appends it to the end puts it back somewhere it has never
 * been. `-1` for anything written before this was recorded, which restores
 * the only way it then could: at the back.
 */
data class TrashedHabit(
    val habit: Habit,
    val completions: Set<Int> = emptySet(),
    val deletedAt: Int = 0,
    val index: Int = -1,
)

/** The whole persisted world. Treated as immutable; the store swaps in new copies. */
data class FyrState(
    val habits: List<Habit> = emptyList(),
    /** habit id -> set of epoch days marked complete. */
    val completions: Map<String, Set<Int>> = emptyMap(),
    /**
     * The name asked for on first launch, empty until then — stored whole,
     * first and last with a single space between them, because that is the
     * shape the profile shows. The greeting reads only its first word, so
     * the short form never has a field of its own to drift from this one.
     * Capped by [FULL_NAME_LIMIT] at the write edge so it can never become
     * a payload.
     */
    val userName: String = "",
    /** Whether the name has been asked for, so declining is remembered too. */
    val nameAsked: Boolean = false,
    /**
     * Which weekday opens a week, in ISO numbering (1 = Monday .. 7 = Sunday).
     *
     * Sunday by default because that is what most calendars around the world
     * open on; [Store.setWeekStart] is the only writer, and it clamps, so a
     * corrupt preference can never turn into a week with no first day.
     */
    val weekStart: Int = DEFAULT_WEEK_START,
    /**
     * The file name of the photo the profile shows, inside the app's private
     * files directory. Empty means "not chosen yet", which is not an error —
     * it is the default mark, drawn by Fyr itself, until a picture replaces it.
     *
     * A name rather than a byte array because the picture is a file: it is
     * written once, read back for as long as it is wanted, and a copy in
     * SharedPreferences would be a second source of truth for the same bytes.
     */
    val avatarPhoto: String = "",
    /**
     * Habit ids whose pill on the Add sheet's **Your habits** shelf has been
     * dismissed.
     *
     * Kept apart from the habits on purpose. The shelf is a memory aid — a
     * row of what you have already written, so you do not write it again —
     * and dismissing one of those is an act of tidying, not an act against
     * the habit behind it. If the two shared a list, the cross on the shelf
     * would reach through and delete the row on Today along with its history,
     * which is the one thing the shelf must never do.
     */
    val shelfHidden: Set<String> = emptySet(),
    /**
     * Deleted habits, newest first, awaiting either a restore or a purge.
     *
     * Kept in the same document as everything else because it is the same
     * document — a habit in the trash is not a lesser habit, only an
     * unpublished one, and the history it brings with it is the entire point
     * of it being here rather than gone.
     */
    val trash: List<TrashedHabit> = emptyList(),
) {
    companion object {
        /**
         * Hard ceiling on a *name part* — first, last, or the single name of
         * an older install. Twelve, because the name is not a paragraph
         * anywhere in this app: the greeting wears the first word alone,
         * where anything past a word stops reading as a signature and starts
         * competing with the wish for the screen.
         */
        const val NAME_LIMIT: Int = 12

        /**
         * Ceiling on the stored name — the whole of it, first and last.
         * Twelve apiece with the space between them lands at twenty-five, so
         * twenty-eight is the limit with the rounding to spare: wide enough
         * for the profile to say the name whole, tight enough that the write
         * edge still means something.
         */
        const val FULL_NAME_LIMIT: Int = 28

        /** ISO 7 — Sunday. The one and only default, so nothing depends on the locale. */
        const val DEFAULT_WEEK_START: Int = 7
    }

    /** The stored day, repaired if it somehow left the 1..7 range. */
    val weekStartDay: Int get() = if (weekStart in 1..7) weekStart else DEFAULT_WEEK_START

    /** Habits that show up on Today. */
    val live: List<Habit> get() = habits.filter { !it.archived }

    /**
     * What the Add sheet's shelf shows: live habits whose pill has not been
     * put away, in the order they were made.
     */
    val shelf: List<Habit> get() = live.filterNot { shelfHidden.contains(it.id) }

    fun completionsFor(habitId: String): Set<Int> = completions[habitId] ?: emptySet()

    fun isDone(habitId: String, day: Int): Boolean = completions[habitId]?.contains(day) == true

    /** Habits expected on [day]. */
    fun dueOn(day: Int): List<Habit> = live.filter { it.isDueOn(day) }
}

/**
 * Calendar arithmetic. Day-of-week is done with integer maths rather than
 * [LocalDate] because the heatmap and the stats walk thousands of days, and
 * allocating a LocalDate per cell is pure waste.
 */
object Dates {
    /** Epoch day 0 is 1970-01-01, a Thursday — ISO day 4. */
    fun dayOfWeek(epochDay: Int): Int = Math.floorMod(epochDay + 3, 7) + 1

    fun today(): Int = LocalDate.now().toEpochDay().toInt()

    fun of(epochDay: Int): LocalDate = LocalDate.ofEpochDay(epochDay.toLong())

    fun epochDay(date: LocalDate): Int = date.toEpochDay().toInt()

    /** First day of the month containing [epochDay]. */
    fun startOfMonth(epochDay: Int): LocalDate = of(epochDay).withDayOfMonth(1)

    /** 1 = Monday .. 7 = Sunday. */
    fun firstWeekdayOf(month: LocalDate): Int = dayOfWeek(epochDay(month))

    /**
     * The day the week containing [epochDay] opens on, given [weekStart].
     *
     * One wrapped subtraction rather than a branch: the distance from
     * [weekStart] to the real weekday, folded into 0..6, so a Sunday-anchored
     * week and a Monday-anchored week are literally the same arithmetic with a
     * different origin. Every grid in the app goes through here, which is what
     * makes the setting change everywhere at once.
     */
    fun weekStartOf(epochDay: Int, weekStart: Int): Int =
        epochDay - Math.floorMod(dayOfWeek(epochDay) - weekStart, 7)

    /** The seven ISO weekdays in the order [weekStart] lays them out. */
    fun weekOrder(weekStart: Int): List<Int> {
        val start = if (weekStart in 1..7) weekStart else FyrState.DEFAULT_WEEK_START
        return (0 until 7).map { Math.floorMod(start - 1 + it, 7) + 1 }
    }

    /** Empty slots before the 1st of a month whose first weekday is [firstWeekday]. */
    fun leadDays(firstWeekday: Int, weekStart: Int): Int =
        Math.floorMod(firstWeekday - weekStart, 7)

    fun lengthOfMonth(month: LocalDate): Int = month.lengthOfMonth()

    fun monthTitle(month: LocalDate): String =
        month.format(
            java.time.format.DateTimeFormatter.ofPattern(
                "MMMM yyyy",
                Locale.getDefault(),
            )
        )

    fun dayTitle(epochDay: Int): String =
        of(epochDay).format(
            java.time.format.DateTimeFormatter.ofPattern(
                "EEE, d MMM",
                Locale.getDefault(),
            )
        )

    /**
     * `27 Sep – 3 Oct` — the week that opens on [weekStartDay].
     *
     * The two ends share a year unless they do not, in which case the year is
     * written once at the end: `29 Dec – 4 Jan 2026`. Week mode's arrows step
     * by a day, so its heading has to name the seven days under them rather
     * than the month that happens to contain the first one.
     */
    fun weekTitle(weekStartDay: Int): String {
        val endDay = weekStartDay + 6
        val f = of(weekStartDay)
        val t = of(endDay)
        val dm = java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
        return if (f.year == t.year) {
            "${f.format(dm)} – ${t.format(dm)}"
        } else {
            "${f.format(dm)} – ${t.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))}"
        }
    }

    /** `28/09/2026` — the compact stamp used wherever a date sits in a caption. */
    fun shortDate(epochDay: Int): String =
        of(epochDay).format(
            java.time.format.DateTimeFormatter.ofPattern(
                "dd/MM/yyyy",
                Locale.getDefault(),
            )
        )

    fun longDayTitle(epochDay: Int): String =
        of(epochDay).format(
            java.time.format.DateTimeFormatter.ofPattern(
                "EEEE, d MMMM",
                Locale.getDefault(),
            )
        )

    fun weekdayName(isoDay: Int, narrow: Boolean = false): String {
        val style = if (narrow) TextStyle.NARROW else TextStyle.SHORT
        return java.time.DayOfWeek.of(isoDay).getDisplayName(style, Locale.getDefault())
    }

    fun monthName(month: LocalDate, short: Boolean): String {
        val style = if (short) TextStyle.SHORT else TextStyle.FULL
        return month.month.getDisplayName(style, Locale.getDefault())
    }

    /** The four parts of a day the greeting is written for. */
    enum class PartOfDay(val label: String) {
        MORNING("morning"),
        AFTERNOON("afternoon"),
        EVENING("evening"),
        NIGHT("night");

        /** The wish, always written out in full: "Good morning", not "Morning". */
        val greeting: String get() = "Good $label"
    }

    fun partOfDay(): PartOfDay = when (java.time.LocalTime.now().hour) {
        in 5..11 -> PartOfDay.MORNING
        in 12..16 -> PartOfDay.AFTERNOON
        in 17..21 -> PartOfDay.EVENING
        else -> PartOfDay.NIGHT
    }

    /**
     * DatePicker speaks UTC midnight of a day; Fyr keeps plain epoch days.
     * Living here rather than beside each picker keeps the two conversions
     * from drifting apart — an off-by-one in one of them silently moves every
     * streak it touches.
     */
    fun utcMillis(epochDay: Int): Long =
        of(epochDay).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    fun epochDayOf(millis: Long): Int =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneOffset.UTC)
            .toLocalDate()
            .toEpochDay()
            .toInt()
}

// ── names ────────────────────────────────────────────────────────────────
// Both live at the data edge because every path a name can arrive on —
// setup form, edit dialog, keyboard suggestion, paste — has to end up with
// the same shape. The two functions differ by one question only: *when*.

/** Where a word starts: the string's first character, or any after a space. */
private val NAME_WORD_START = Regex("(^|\\s)(\\p{L})")

private fun String.withCapitalWords(): String = replace(NAME_WORD_START) { m ->
    // A Char's uppercase, not a String's: a handful of letters (ß) grow
    // into two when uppercased as text, and a name field that gains
    // characters behind the person's back is worse than a lowercase word.
    m.groupValues[1] + m.groupValues[2].first().uppercaseChar()
}

/**
 * The form a name field keeps *while it is being typed: **letters only**,
 * at most [FyrState.NAME_LIMIT] of them, the first one capital.
 *
 * A digit, a symbol, or a space — the one a keyboard suggestion loves to
 * append after a name — is dropped the moment it arrives, so the field
 * can hold nothing but the name itself; the length check runs on what
 * survives the filter, so a paste of "john42" still takes four letters.
 * The gap between first and last is not this field's to type: the profile
 * joins the two parts, and [normalizeName] keeps that join clean.
 */
fun nameAsTyped(raw: String): String =
    raw.filter(Char::isLetter)
        .take(FyrState.NAME_LIMIT)
        .withCapitalWords()

/**
 * The form a name is *stored* in: letters only, no space at either end,
 * one space between words, every word capitalised — the shape the profile
 * reads back.
 *
 * The space clause allows exactly one kind of whitespace through, because
 * this is the edge the two fields meet at: everything that is neither a
 * letter nor a space (a stray digit, a symbol) is dropped as belt for any
 * path that did not come through [nameAsTyped] — a paste, an old install.
 * Capitals are enforced here rather than trusted from the field because a
 * suggestion tapped from the keyboard can insert any casing it likes, and
 * a name is one of the few strings where the wrong case is visible on
 * every screen it lands on.
 */
fun normalizeName(raw: String): String =
    raw.filter { it.isLetter() || it.isWhitespace() }
        .trim()
        .replace(Regex("\\s+"), " ")
        .withCapitalWords()
        .take(FyrState.FULL_NAME_LIMIT)
