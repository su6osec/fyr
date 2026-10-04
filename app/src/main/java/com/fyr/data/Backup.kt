package com.fyr.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Base64
import java.util.Locale

/**
 * A portable copy of everything Fyr holds — the one way data ever leaves
 * this phone, and only ever in the person's own hands.
 *
 * The app has no cloud, no account and (by manifest) no backup agent, which
 * is a promise with a consequence: if the phone is lost, the file is the
 * history. So the export is whole — every habit, every tick, the trash and
 * the picture — in one human-readable JSON document that a text editor
 * could in principle open, and the import offers both honest options,
 * spelled out, before anything is touched.
 *
 * Pure: strings and byte arrays only. The file I/O and the avatar's life
 * cycle stay in [Store], which owns them.
 */
object Backup {

    const val FORMAT = "fyr-backup"

    /**
     * The document's own version, independent of the store's schema: the
     * store's number says how the *prefs* document is shaped, this one says
     * what a file handed across a phone boundary promises. Readers accept
     * anything they can decode — every field they do not know is ignored and
     * every field they need has a default — so a newer file degrades to what
     * this build understands rather than being refused for being newer.
     */
    const val VERSION = 1

    /** Hard ceiling on an imported file: a wrong pick must not be a crash. */
    const val MAX_BYTES: Int = 8 * 1024 * 1024

    /** What an import will do — offered, never guessed. */
    enum class Mode {
        /** The archive becomes the document. Everything else is cleared first. */
        REPLACE,

        /** Union: this device keeps everything it holds and gains what it is missing. */
        MERGE,
    }

    /**
     * A parsed backup, ready to be shown (so the choice is made with counts
     * in front of the person) and then applied.
     *
     * [avatarPhoto] is deliberately empty: a file *name* is an address inside
     * one phone's private storage and means nothing on another. The bytes
     * travel as [avatarJpeg]; [Store] gives them a new local name when they
     * land.
     */
    data class Archive(
        val state: FyrState,
        val avatarJpeg: ByteArray? = null,
        /** The day the file was written, for the dialog's caption — 0 if unknown. */
        val exportedAt: Int = 0,
    ) {
        /**
         * Every mark stored in the file, counted raw. Data-fidelity, not UI:
         * screens that say "N ticks" (masthead, Insights, the import dialog)
         * count *due* days via [com.fyr.domain.Stats.total], which can be one
         * lower if history holds a mark on an excused or impossible day. Both
         * numbers describe the same file; this one is the stricter claim
         * about bytes, that one the app-wide claim about ticks.
         */
        val tickCount: Int get() = state.completions.values.sumOf { it.size }
    }

    /** `fyr-backup-2026-10-04.json` — sorts by date, recognisable at a glance. */
    fun suggestedName(today: Int): String {
        val d = Dates.of(today)
        return String.format(
            Locale.ROOT,
            "fyr-backup-%04d-%02d-%02d.json",
            d.year,
            d.monthValue,
            d.dayOfMonth,
        )
    }

    /** The whole document, plus the picture it names, as one portable string. */
    fun export(state: FyrState, avatarJpeg: ByteArray? = null): String =
        JSONObject().apply {
            put(KEY_FORMAT, FORMAT)
            put(KEY_VERSION, VERSION)
            put(KEY_EXPORTED_AT, Dates.today())
            // Nested as real JSON rather than as encoded strings: the file is
            // meant to be openable, and a document full of escaped blobs is a
            // document nobody will ever read.
            put(KEY_HABITS, JSONArray(FyrCodec.encodeHabits(state.habits)))
            put(KEY_COMPLETIONS, JSONObject(FyrCodec.encodeCompletions(state.completions)))
            put(
                KEY_TRASH,
                if (state.trash.isEmpty()) JSONArray()
                else JSONArray(FyrCodec.encodeTrash(state.trash)),
            )
            put(
                KEY_PROFILE,
                JSONObject().apply {
                    put(KEY_USER_NAME, state.userName)
                    put(KEY_NAME_ASKED, state.nameAsked)
                    put(KEY_WEEK_START, state.weekStartDay)
                    put(KEY_SHELF_HIDDEN, state.shelfHidden.joinToString(","))
                },
            )
            avatarJpeg
                ?.takeIf { it.isNotEmpty() }
                ?.let { put(KEY_AVATAR, Base64.getEncoder().encodeToString(it)) }
        }.toString()

    /**
     * Parses a backup, or returns null when it is not one.
     *
     * Null means three different things that all deserve the same answer —
     * not this app's file, not JSON at all, or a file whose contents had to
     * be skipped to get this far. A backup Fyr wrote itself never trips any
     * of them, so anything that does is a file this build must not import:
     * a partial read of a damaged archive is exactly the kind of quiet loss
     * the export exists to prevent.
     */
    fun parse(raw: String): Archive? {
        val o = try {
            JSONObject(raw)
        } catch (e: JSONException) {
            return null
        }
        if (o.optString(KEY_FORMAT) != FORMAT) return null
        if (o.optInt(KEY_VERSION, -1) < 1) return null

        // Typed extraction rather than optString coercion: the shapes this
        // file must have are the shapes export wrote, and anything else — a
        // missing key, a blob where a list belongs, a profile that is not an
        // object — is a file to refuse, not to quietly import as empty.
        val habitsJson = o.optJSONArray(KEY_HABITS)?.toString() ?: return null
        val completionsJson = o.optJSONObject(KEY_COMPLETIONS)?.toString() ?: return null
        val trashJson = o.optJSONArray(KEY_TRASH)?.toString() ?: return null
        val profile = o.optJSONObject(KEY_PROFILE) ?: return null

        val habits = FyrCodec.decodeHabits(habitsJson)
        val completions = FyrCodec.decodeCompletions(completionsJson)
        val trash = FyrCodec.decodeTrash(trashJson)
        if (habits.unread || completions.unread || trash.unread) return null

        val avatar = o.optString(KEY_AVATAR, "")
            .takeIf { it.isNotEmpty() }
            ?.let { encoded -> runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() }

        return Archive(
            state = FyrState(
                habits = habits.value,
                completions = completions.value,
                userName = profile.optString(KEY_USER_NAME, "").take(FyrState.FULL_NAME_LIMIT),
                nameAsked = profile.optBoolean(KEY_NAME_ASKED, false),
                weekStart = profile.optInt(KEY_WEEK_START, FyrState.DEFAULT_WEEK_START),
                shelfHidden = profile.optString(KEY_SHELF_HIDDEN, "")
                    .split(",")
                    .filter { it.isNotBlank() }
                    .toSet(),
                trash = trash.value,
                avatarPhoto = "",
            ),
            avatarJpeg = avatar,
            exportedAt = o.optInt(KEY_EXPORTED_AT, 0),
        )
    }

    /**
     * Union semantics — **nothing this device already holds is removed.**
     *
     * The rules, in the order they can surprise someone:
     *
     * - Habits are keyed by id: the copy already on the phone wins, because
     *   it is this phone's live record (renames and retires included), and
     *   everything the archive knows and the phone does not is appended in
     *   the archive's order. A habit this phone has *deleted* is not
     *   "missing", it is decided: the archive predates that decision, and a
     *   merge that quietly restored it would hand back something the person
     *   explicitly threw away.
     * - Ticks are a union per habit: a day ticked in either copy happened,
     *   and a habit renamed here still collects the history it had there.
     * - Trash follows the same key, newest first — a habit cannot be
     *   deleted here and alive from the file at once.
     * - Profile: this device keeps its own name, week origin and picture;
     *   an empty name is filled from the archive, and the archive's picture
     *   only counts when there is none here ([Store] holds those bytes).
     */
    fun merge(current: FyrState, incoming: FyrState): FyrState {
        val heldTrash = current.trash.map { it.habit.id }.toSet()
        // Deleted-here counts as known: a merge must never list one habit as
        // both alive and deleted, and must never undo a deletion the person
        // made on this phone.
        val known = current.habits.map { it.id }.toSet() + heldTrash
        val habits = current.habits + incoming.habits.filterNot { it.id in known }
        val ids = habits.map { it.id }.toSet()

        val ticks = HashMap<String, Set<Int>>(current.completions.size + incoming.completions.size)
        current.completions.forEach { (id, days) -> if (id in ids) ticks[id] = days }
        incoming.completions.forEach { (id, days) ->
            if (id in ids) ticks[id] = ticks[id].orEmpty() + days
        }

        val trash = (current.trash + incoming.trash.filterNot { it.habit.id in heldTrash })
            .sortedByDescending { it.deletedAt }

        return FyrState(
            habits = habits,
            completions = ticks.filterValues { it.isNotEmpty() },
            userName = current.userName.ifBlank { incoming.userName },
            nameAsked = current.nameAsked || incoming.nameAsked,
            weekStart = current.weekStartDay,
            shelfHidden = current.shelfHidden + incoming.shelfHidden,
            trash = trash,
            avatarPhoto = current.avatarPhoto,
        )
    }

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_EXPORTED_AT = "exportedAt"
    private const val KEY_HABITS = "habits"
    private const val KEY_COMPLETIONS = "completions"
    private const val KEY_TRASH = "trash"
    private const val KEY_PROFILE = "profile"
    private const val KEY_AVATAR = "avatar"
    private const val KEY_USER_NAME = "userName"
    private const val KEY_NAME_ASKED = "nameAsked"
    private const val KEY_WEEK_START = "weekStart"
    private const val KEY_SHELF_HIDDEN = "shelfHidden"
}
