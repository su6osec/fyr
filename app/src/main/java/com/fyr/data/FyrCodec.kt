package com.fyr.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The document Fyr writes to disk, encoded and decoded.
 *
 * Deliberately pure: strings in, strings out, no Android types, no I/O and no
 * clock beyond [Dates.today] for one optional field — so every byte the app
 * persists can be exercised by a plain JVM unit test. [Store] owns the file
 * and the failure policy; this object owns the shape.
 *
 * Decoding is **partial by design**. One unreadable entry costs exactly that
 * entry, never the document, and anything that had to be skipped is reported
 * back through [Decoded.unread]. That flag is the whole point of the split:
 * the store must know a read went wrong so it can preserve the original bytes
 * *before* the next write would replace them. A corrupt value silently
 * persisted as an empty one turns a bad read into permanent loss, and that is
 * the single failure mode this file exists to prevent.
 */
internal object FyrCodec {

    /**
     * What a decode produced: whatever could be read, plus whether anything
     * could not be. [unread] is true when an entry was skipped or the payload
     * was not parseable at all — never for an empty document, which is a
     * legitimate first-run state and not an error.
     */
    data class Decoded<T>(val value: T, val unread: Boolean = false)

    // ── habits ───────────────────────────────────────────────────────────

    fun habitJson(h: Habit): JSONObject = JSONObject().apply {
        put(KEY_ID, h.id)
        put(KEY_NAME, h.name)
        put(KEY_EMOJI, h.emoji)
        put(KEY_CATEGORY, h.category.name)
        put(KEY_CREATED, h.createdAt)
        put(KEY_WEEKDAYS, h.weekdays.sorted().joinToString(","))
        put(KEY_ARCHIVED, if (h.archived) 1 else 0)
        // Written like every other day-set on this habit: a sorted, comma
        // joined list of ints. Both fields were missing here once, which
        // silently dropped every excused day the moment the process died —
        // the one place a habit tracker must never lose a record.
        put(KEY_SKIP_DAYS, h.skipDays.sorted().joinToString(","))
        put(KEY_SKIP_WEEKDAYS, h.skipWeekdays.sorted().joinToString(","))
    }

    /** `""`, `"1,2,3"` and `"1, 2, 3"` all read as the same set; junk entries drop out. */
    fun readDaySet(raw: String): Set<Int> =
        raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .toSet()

    /**
     * One habit from its JSON, or null when the object does not describe a
     * usable habit at all (no id — it could never be ticked or referenced).
     *
     * Everything else is repaired rather than rejected: an unknown category
     * is one this build has never heard of — a downgrade, or a value renamed
     * since — and losing the whole habit over it would mean losing the record
     * the backup exists to keep. It falls back to [Category.LIFESTYLE] and
     * keeps every other field it arrived with.
     */
    fun habitFromJson(o: JSONObject): Habit? = try {
        Habit(
            id = o.getString(KEY_ID),
            name = o.getString(KEY_NAME),
            emoji = o.optString(KEY_EMOJI, "🔥"),
            category = categoryOf(o.optString(KEY_CATEGORY, Category.LIFESTYLE.name)),
            // Optional: a document written before these existed is upgraded
            // in place rather than rejected. A missing creation day falls
            // back to *today* rather than the epoch, because "nothing before
            // creation counts" is only cheap to compute for a recent day.
            createdAt = o.optInt(KEY_CREATED, Dates.today()),
            weekdays = readDaySet(o.optString(KEY_WEEKDAYS, "")),
            archived = o.optInt(KEY_ARCHIVED, 0) == 1,
            skipDays = readDaySet(o.optString(KEY_SKIP_DAYS, "")),
            skipWeekdays = readDaySet(o.optString(KEY_SKIP_WEEKDAYS, "")),
        )
    } catch (e: JSONException) {
        null
    }

    /** The category this build knows, or the neutral one when it does not. */
    fun categoryOf(name: String): Category =
        Category.entries.firstOrNull { it.name == name } ?: Category.LIFESTYLE

    fun encodeHabits(habits: List<Habit>): String {
        val arr = JSONArray()
        habits.forEach { h -> arr.put(habitJson(h)) }
        return arr.toString()
    }

    fun decodeHabits(raw: String?): Decoded<List<Habit>> {
        if (raw.isNullOrEmpty()) return Decoded(emptyList())
        val arr = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            return Decoded(emptyList(), unread = true)
        }
        var unread = false
        val out = ArrayList<Habit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            val habit = o?.let { habitFromJson(it) }
            if (habit == null) unread = true else out += habit
        }
        return Decoded(out, unread)
    }

    // ── completions ──────────────────────────────────────────────────────

    fun encodeCompletions(c: Map<String, Set<Int>>): String {
        val obj = JSONObject()
        c.forEach { (id, days) ->
            if (days.isNotEmpty()) obj.put(id, JSONArray(days.sorted()))
        }
        return obj.toString()
    }

    /**
     * Ticks per habit, recovered entry by entry.
     *
     * The whole loop is *not* under one catch: an array that is not an array,
     * or a day that is not a number, used to throw mid-loop and discard every
     * entry already parsed. One bad habit id now costs that habit's days and
     * nothing else — the difference between a glitch and a wiped history.
     */
    fun decodeCompletions(raw: String?): Decoded<Map<String, Set<Int>>> {
        if (raw.isNullOrEmpty()) return Decoded(emptyMap())
        val obj = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return Decoded(emptyMap(), unread = true)
        }
        var unread = false
        val out = HashMap<String, Set<Int>>(obj.length())
        val keys = obj.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            try {
                val arr = obj.getJSONArray(id)
                val days = HashSet<Int>(arr.length())
                for (i in 0 until arr.length()) days.add(arr.getInt(i))
                if (days.isNotEmpty()) out[id] = days
            } catch (e: JSONException) {
                unread = true
            }
        }
        return Decoded(out, unread)
    }

    // ── trash ────────────────────────────────────────────────────────────

    /**
     * The trash, as `[{"h": <habit>, "completions": [days], "d": <epochDay>, "i": <pos>}, …]`.
     *
     * Written newest-first, which is the order the list is read in, so the
     * file and the screen agree without a sort on either side.
     */
    fun encodeTrash(trash: List<TrashedHabit>): String {
        if (trash.isEmpty()) return ""
        val arr = JSONArray()
        trash.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put(KEY_TRASH_HABIT, habitJson(t.habit))
                    put(KEY_COMPLETIONS, JSONArray(t.completions.sorted()))
                    put(KEY_DELETED_AT, t.deletedAt)
                    put(KEY_TRASH_INDEX, t.index)
                }
            )
        }
        return arr.toString()
    }

    /** Entry by entry, for the same reason as [decodeCompletions]. */
    fun decodeTrash(raw: String?): Decoded<List<TrashedHabit>> {
        if (raw.isNullOrEmpty()) return Decoded(emptyList())
        val arr = try {
            JSONArray(raw)
        } catch (e: JSONException) {
            return Decoded(emptyList(), unread = true)
        }
        var unread = false
        val out = ArrayList<TrashedHabit>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                unread = true
                continue
            }
            val habit = try {
                habitFromJson(o.getJSONObject(KEY_TRASH_HABIT))
            } catch (e: JSONException) {
                null
            }
            if (habit == null) {
                unread = true
                continue
            }
            val days = try {
                val arr2 = o.getJSONArray(KEY_COMPLETIONS)
                (0 until arr2.length()).mapNotNull { arr2.getInt(it) }.toSet()
            } catch (e: JSONException) {
                unread = true
                emptySet()
            }
            out += TrashedHabit(
                habit = habit,
                completions = days,
                deletedAt = o.optInt(KEY_DELETED_AT, 0),
                index = o.optInt(KEY_TRASH_INDEX, -1),
            )
        }
        return Decoded(out, unread)
    }

    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_EMOJI = "emoji"
    private const val KEY_CATEGORY = "category"
    private const val KEY_CREATED = "createdAt"
    private const val KEY_WEEKDAYS = "weekdays"
    private const val KEY_ARCHIVED = "archived"
    private const val KEY_SKIP_DAYS = "skipDays"
    private const val KEY_SKIP_WEEKDAYS = "skipWeekdays"
    private const val KEY_TRASH_HABIT = "h"
    private const val KEY_DELETED_AT = "d"

    /** Where in the list the habit was before it was trashed. */
    private const val KEY_TRASH_INDEX = "i"

    /** The days of a trashed habit — same name as the top-level key, on purpose. */
    private const val KEY_COMPLETIONS = "completions"
}
