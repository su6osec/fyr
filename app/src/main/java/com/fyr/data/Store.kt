package com.fyr.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Where a pick waits until Save — the profile picture one keystroke short
 * of existing.
 *
 * A file in the same directory as the real pictures, under a name that
 * matches no prefix they are collected by ([Store] sweeps old avatars by
 * prefix, so nothing that sweeps can touch this), and that no renderer
 * ever reads: the editor previews it directly, but the profile's own
 * `avatarPhoto` — the name the rest of the app follows — does not move
 * until Save adopts it.
 */
const val STAGED_AVATAR = "staged-pick.jpg"

/**
 * Single source of truth for the app.
 *
 * Deliberately dependency-free: plain SharedPreferences plus org.json, both of
 * which ship with the platform. No Room, no KSP, no DataStore — this app keeps
 * one small document, and a document wants one atomic write, not a schema.
 *
 * Every mutation goes through [update], which swaps the immutable state and
 * writes it in the same step, so the UI can never observe a half-applied change.
 *
 * Three promises this class makes to the rest of the app, in order of how
 * expensive they are to break:
 *
 * 1. **It starts.** No document — however wrong-shaped, wrong-typed or
 *    truncated — can throw its way out of [load], because that is a crash
 *    loop only clearing app data can end.
 * 2. **It does not lose history quietly.** A value that could not be read is
 *    *kept* first ([quarantine] copies the original bytes aside, and
 *    [keepUnreadable] keeps writing them back until the data itself changes)
 *    and only then defaulted. A bad read must never be persisted as an empty
 *    one — that is how a momentary glitch becomes permanent loss.
 * 3. **One writer.** [update] is serialised, because mutations arrive from
 *    the UI on the main thread and from avatar work on IO, and an
 *    unsynchronised read-modify-write over the whole document can drop one of
 *    them or leave memory and disk describing different worlds.
 */
class Store(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val appContext: Context = context.applicationContext

    /**
     * Where the profile picture is decoded and written.
     *
     * A picture arrives as a content URI that belongs to someone else's
     * provider and is only lent to us for a moment, so it is copied into
     * [appContext] straight away — on the IO dispatcher, because the frame is
     * megapixels and this is not a background thread by default.
     */
    private val io = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            // Nothing on this scope may take the process down with it. Every
            // job here is file tidy-up; a failed delete must never cost the
            // user the app it was tidying. The jobs also guard themselves
            // with runCatching — this is the net under the net.
            CoroutineExceptionHandler { _, _ -> },
    )

    /** Serialises read-modify-write over the document. See promise 3 above. */
    private val lock = Any()

    /** Keys whose bytes could not be fully read, with the original payload. */
    private val poisoned = HashMap<String, String>()

    /** What a poisoned key encoded to from the state as it was *loaded*. */
    private val baseline = HashMap<String, String>()

    /**
     * The schema the document on disk claims. Stamped on every write; a
     * *newer* number than this build knows is preserved rather than
     * downgraded, so a document that survives a downgrade is still marked
     * for the build that wrote it when it comes back.
     */
    private var schema: Int = 0

    private val _state = MutableStateFlow(load())
    val state: StateFlow<FyrState> = _state

    init {
        // A pick that was never saved dies with the session that made it:
        // staging exists only while a dialog is open to accept it, so the
        // first thing a new session does is make sure no half-edit from a
        // killed one can surface later.
        //
        // And the mirror-image sweep: any picture the document does not name
        // is an orphan — a write that did not finish, or a restore that
        // arrived with its preferences but without its files — and keeping it
        // would hold bytes on disk that nothing can any longer attribute.
        io.launch {
            runCatching { File(appContext.filesDir, STAGED_AVATAR).delete() }
            dropAvatarsExcept(_state.value.avatarPhoto.ifEmpty { null })
        }
    }

    // ── mutations ────────────────────────────────────────────────────────

    fun update(transform: (FyrState) -> FyrState) {
        synchronized(lock) {
            val next = transform(_state.value)
            if (next == _state.value) return
            _state.value = next
            persist(next)
        }
    }

    /**
     * [update] that does not return until the bytes have reached disk.
     *
     * Used only where a file is about to be deleted on the strength of this
     * write: with the ordinary asynchronous write, a process death in the gap
     * would leave `avatarPhoto` naming a picture that has already been
     * removed. Returns false when a durable write failed, which is the signal
     * to *keep* the files rather than orphan the reference to them.
     */
    private fun updateDurable(transform: (FyrState) -> FyrState): Boolean = synchronized(lock) {
        val next = transform(_state.value)
        if (next == _state.value) return true
        _state.value = next
        persist(next, durable = true)
    }

    /**
     * Flip whether [day] counts as completed for [habitId]. Strict: no grace, no carry-over.
     *
     * A day the habit was excused from can be *un*ticked but never ticked: a
     * completion written under an excuse counts for nothing today and
     * everything the moment the excuse is lifted, which is a tick the user
     * never knowingly made appearing in their history.
     */
    fun toggle(habitId: String, day: Int) = update { s ->
        val current = s.completionsFor(habitId)
        if (current.contains(day)) {
            val left = current - day
            s.copy(
                completions = if (left.isEmpty()) s.completions - habitId
                else s.completions + (habitId to left),
            )
        } else {
            val habit = s.habits.firstOrNull { it.id == habitId }
            if (habit != null && habit.isSkipDay(day)) s
            else s.copy(completions = s.completions + (habitId to (current + day)))
        }
    }

    /**
     * Creates or replaces a habit.
     *
     * [Habit.createdAt] is clamped to today at this, the write edge: the add
     * sheet seeds it from a day that can go stale while the sheet is open
     * (opened before midnight, submitted after), and a habit born *yesterday*
     * would be due for a day it never existed on. Every other path into this
     * method is already sane — clamping here means the invariant holds no
     * matter which one is not.
     */
    fun setHabit(habit: Habit) = update { s ->
        val born = habit.copy(createdAt = habit.createdAt.coerceAtMost(Dates.today()))
        val exists = s.habits.any { it.id == born.id }
        s.copy(habits = if (exists) s.habits.map { if (it.id == born.id) born else it } else s.habits + born)
    }

    fun setArchived(habitId: String, archived: Boolean) = update { s ->
        s.copy(habits = s.habits.map { if (it.id == habitId) it.copy(archived = archived) else it })
    }

    /**
     * Deletes a habit by **moving it to the trash** rather than destroying it.
     *
     * Everything the habit was — its schedule, its ticks, the day it was made
     * — is carried over intact so that either the five-second Undo bar or the
     * trash list itself can put it back whole. Permanent erasure is now a
     * separate, deliberate act ([emptyTrash] / [purgeTrash]), which is what
     * makes it safe to keep a record of what the user has deleted.
     */
    fun deleteHabit(habitId: String) = update { s ->
        val habit = s.habits.firstOrNull { it.id == habitId }
        if (habit == null) {
            s.copy(
                habits = s.habits.filterNot { it.id == habitId },
                completions = s.completions - habitId,
                shelfHidden = s.shelfHidden - habitId,
            )
        } else {
            val already = s.trash.any { it.habit.id == habitId }
            s.copy(
                habits = s.habits.filterNot { it.id == habitId },
                completions = s.completions - habitId,
                shelfHidden = s.shelfHidden - habitId,
                trash = if (already) s.trash else listOf(
                    TrashedHabit(
                        habit = habit,
                        completions = s.completionsFor(habitId),
                        deletedAt = todayEpoch(),
                        index = s.habits.indexOfFirst { it.id == habitId },
                    ),
                ) + s.trash,
            )
        }
    }

    /**
     * Takes a habit's pill off the Add sheet's shelf — and touches nothing
     * else.
     *
     * This used to be wired straight to [deleteHabit], so tidying a row of
     * shortcuts was deleting the habit itself: the row went from Today, the
     * ticks went with it, and the only sign of what had happened was an undo
     * bar rendered behind a sheet that was covering it. The shelf is now
     * dismissed independently of the record it points at.
     */
    fun hideShelfPill(habitId: String) = update { s ->
        s.copy(shelfHidden = s.shelfHidden + habitId)
    }

    /**
     * Puts back exactly what [deleteHabit] took: the habit *and* the days it
     * recorded.
     *
     * The undo offered after a long-press delete is the only guard against a
     * thumb landing one line low, so it has to be able to return the history
     * as well as the row — a restored habit with a blank record would be a
     * worse surprise than the deletion it replaced. Written back where it was,
     * because the position in the list is part of what the user was looking at.
     */
    fun restoreHabit(habit: Habit, completions: Set<Int>) = update { s ->
        if (s.habits.any { it.id == habit.id }) {
            // Already back — but take it out of the trash either way, or the
            // same habit would be listed alive and dead at once.
            if (s.trash.any { it.habit.id == habit.id }) s.copy(trash = s.trash.filterNot { it.habit.id == habit.id })
            else s
        } else {
            // Back where it was, not appended at the back: the position is
            // part of what was removed, and a restore that quietly reorders
            // the list hands the user a change they did not make.
            val savedIndex = s.trash.firstOrNull { it.habit.id == habit.id }?.index ?: -1
            val restored = if (savedIndex in s.habits.indices) {
                s.habits.toMutableList().also { it.add(savedIndex, habit) }
            } else {
                s.habits + habit
            }
            s.copy(
                habits = restored,
                completions = if (completions.isEmpty()) s.completions
                else s.completions + (habit.id to completions),
                trash = s.trash.filterNot { it.habit.id == habit.id },
                shelfHidden = s.shelfHidden - habit.id,
            )
        }
    }

    /**
     * Destroys one trashed habit for good. Irreversible, and deliberately only
     * reachable from inside the trash list, where the thing being lost is on
     * screen while the decision is made.
     */
    fun purgeTrash(habitId: String) = update { s ->
        s.copy(
            trash = s.trash.filterNot { it.habit.id == habitId },
            // Its id can never come back, so neither should the shelf state
            // that names it.
            shelfHidden = s.shelfHidden - habitId,
        )
    }

    /** Empties the trash. The one remaining way to lose history permanently. */
    fun emptyTrash() = update { s ->
        s.copy(
            trash = emptyList(),
            shelfHidden = s.shelfHidden - s.trash.map { it.habit.id }.toSet(),
        )
    }

    private fun todayEpoch(): Int = Dates.today()

    /**
     * Stores the name asked for on first launch. Truncated rather than
     * rejected: the limit is a display constraint, and silently clipping a
     * paste is friendlier than refusing it.
     */
    fun setUserName(raw: String) = update {
        it.copy(
            // Normalised rather than merely trimmed: one space between the
            // words, a capital at each, nothing at either end — the same
            // shape every other path into this field produces.
            userName = normalizeName(raw),
            nameAsked = true,
        )
    }

    /**
     * Moves the first weekday of every week in the app.
     *
     * Clamped rather than validated: an out-of-range day is a bug somewhere
     * else, and falling back to Sunday keeps the calendars rendering instead of
     * turning a bad value into a seven-day grid with no origin.
     */
    fun setWeekStart(day: Int) = update {
        it.copy(weekStart = if (day in 1..7) day else FyrState.DEFAULT_WEEK_START)
    }

    /**
     * Copy a picked picture into [STAGED_AVATAR] — held *beside* the profile
     * rather than as it.
     *
     * The dialog that offers the picker promises two things it used to break:
     * nothing reaches the profile until **Save**, and coming back from the
     * picker shows what was picked. Staging keeps both: the grant a picker
     * lends lasts only while it can be spent, so the bytes are copied the
     * moment the URI arrives, but the profile's own file name — the one the
     * rest of the app reads — does not move until [adoptStagedAvatar].
     * [onStaged] is called once the copy is on disk, which is the only safe
     * moment for a preview to point at the file.
     */
    fun stageAvatar(uri: Uri, onStaged: () -> Unit = {}) {
        io.launch {
            val staged = runCatching { writeAvatar(uri, STAGED_AVATAR) }.getOrDefault(false)
            // The callback lands in Compose state (the dialog's preview), so
            // it is delivered on the main thread — the copy happened on IO,
            // but nothing the UI is listening to is *announced* from here.
            withContext(Dispatchers.Main) {
                if (staged) onStaged()
            }
        }
    }

    /**
     * The staged pick becomes the picture the profile shows — the one place
     * a staged file is allowed to become a real one. A no-op when there is
     * nothing staged, so a save that only touched the name never has to
     * know whether a picture was involved.
     *
     * Each adoption gets a *new* name, because the stored name is the only
     * thing the UI can see change, and [update] does not publish equal
     * states. The previous picture is dropped only once the new reference is
     * **on disk** ([updateDurable]), so a failed read or a dead process
     * leaves the old picture in use rather than replacing it with a hole.
     */
    fun adoptStagedAvatar() {
        io.launch {
            val staged = File(appContext.filesDir, STAGED_AVATAR)
            if (!staged.exists() || staged.length() == 0L) return@launch
            val name = "$AVATAR_PREFIX${System.currentTimeMillis()}.jpg"
            val target = File(appContext.filesDir, name)
            // renameTo is same-directory and normally infallible; the copy
            // fallback covers the cases where it is not (a read-only moment,
            // a provider-held file), because a silently discarded pick is
            // indistinguishable from a broken picker to the person using it.
            val moved = staged.renameTo(target) || runCatching {
                staged.copyTo(target, overwrite = true)
                staged.delete()
            }.isSuccess
            if (!moved) return@launch
            if (updateDurable { it.copy(avatarPhoto = name) }) {
                dropAvatarsExcept(name)
            } else {
                // The write did not land, so the name still points at the old
                // picture: keep that one, and take the new file back out.
                runCatching { target.delete() }
            }
        }
    }

    /** Whether a staged pick is really on disk — after process death it is not. */
    fun hasStagedAvatar(): Boolean =
        File(appContext.filesDir, STAGED_AVATAR).let { it.exists() && it.length() > 0L }

    /** Forget the pick. The profile keeps exactly the picture it had. */
    fun dropStagedAvatar() {
        io.launch { runCatching { File(appContext.filesDir, STAGED_AVATAR).delete() } }
    }

    /** Back to the mark Fyr draws itself. The file goes too — nothing lingers. */
    fun clearAvatarPhoto() {
        io.launch {
            // Durable for the same reason adoption is: the files below are
            // deleted on the strength of this write.
            if (updateDurable { it.copy(avatarPhoto = "") }) {
                dropAvatarsExcept(keep = null)
                // Including any staged pick: clearing is a decision about the
                // picture, and the staged one *is* a picture this dialog was
                // holding.
                runCatching { File(appContext.filesDir, STAGED_AVATAR).delete() }
            }
        }
    }

    /** Every picture Fyr has ever written except [keep], the one in use. */
    private fun dropAvatarsExcept(keep: String?) {
        appContext.filesDir
            .listFiles()
            ?.filter { it.name.startsWith(AVATAR_PREFIX) }
            ?.forEach { file -> if (file.name != keep) runCatching { file.delete() } }
    }

    /**
     * Copies [uri] down to a thumbnail-sized JPEG under [name].
     *
     * Sampled to at most ~512px a side before it is decoded, because the
     * picture is displayed in a 46dp circle: a full-resolution frame would
     * cost tens of megabytes of heap to render something four hundred pixels
     * wide, and the sample is a power of two so the decoder does it in one
     * pass rather than scaling afterwards.
     */
    private fun writeAvatar(uri: Uri, name: String): Boolean {
        val resolver = appContext.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2

        val bitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } ?: return false

        val written = runCatching {
            File(appContext.filesDir, name).outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }.isSuccess
        bitmap.recycle()
        return written
    }

    /**
     * Wipes every habit, tick and trashed record but keeps your name.
     * Exposed as an explicit, separate action so it can never be reached by a
     * stray tap — the trash included, because "start over" that leaves the old
     * history one tap away was not starting over.
     */
    fun clearAll() = update {
        it.copy(
            habits = emptyList(),
            completions = emptyMap(),
            trash = emptyList(),
            shelfHidden = emptySet(),
        )
    }

    fun newId(): String = UUID.randomUUID().toString()

    // ── backup ──────────────────────────────────────────────────────────
    //
    // The app's manifest refuses every automatic exit for this document —
    // no cloud, no backup agent, no device transfer — so these three are
    // the whole story of how data leaves Fyr, and none of them run without
    // the person choosing a file first.

    /**
     * Writes the whole document — picture included — to [uri], a file chosen
     * in the system's own document picker.
     *
     * `"wt"` matters: the picker can hand back a file that already exists,
     * and appending a second document to it would produce a file no parser
     * accepts — a save that silently succeeds at creating something
     * unreadable is worse than a failure that says so.
     */
    fun exportBackup(uri: Uri, onDone: (Boolean) -> Unit) {
        io.launch {
            val ok = runCatching {
                val picture = _state.value.avatarPhoto
                    .takeIf { it.isNotBlank() }
                    ?.let { name ->
                        runCatching { File(appContext.filesDir, name).readBytes() }.getOrNull()
                    }
                val json = Backup.export(_state.value, picture)
                val stream = appContext.contentResolver.openOutputStream(uri, "wt")
                    ?: return@runCatching false
                stream.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                true
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /**
     * Reads and parses a picked file. Null means "not a Fyr backup" — the
     * wrong file, a damaged one, or one this build refuses to import even
     * partially — and the screen says exactly that. The dialog that follows
     * is where a *mistake* would have become data loss, so nothing here
     * writes anything at all.
     */
    fun readBackup(uri: Uri, onReady: (Backup.Archive?) -> Unit) {
        io.launch {
            val archive = runCatching {
                val stream = appContext.contentResolver.openInputStream(uri)
                    ?: return@runCatching null
                val text = stream.use { input ->
                    // Bounded: the picker offers every file on the phone, so
                    // a wrong pick of a video has to be a refusal rather than
                    // an out-of-memory crash. A truncated tail parses to the
                    // same null an unreadable file does.
                    val buf = ByteArrayOutputStream()
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    var read = input.read(chunk)
                    while (read > 0 && total < Backup.MAX_BYTES) {
                        buf.write(chunk, 0, read)
                        total += read
                        read = input.read(chunk)
                    }
                    buf.toString("UTF-8")
                }
                Backup.parse(text)
            }.getOrNull()
            withContext(Dispatchers.Main) { onReady(archive) }
        }
    }

    /**
     * Applies an archive that was parsed and then *confirmed* — the dialog
     * has already shown what it holds and which of the two honest options
     * was chosen. Either way, the picture's bytes reach disk before the
     * state that names them, and the write is durable before any existing
     * picture is deleted: the same order [adoptStagedAvatar] keeps, for the
     * same reason.
     */
    fun applyBackup(archive: Backup.Archive, mode: Backup.Mode, onDone: (Boolean) -> Unit) {
        io.launch {
            val ok = runCatching {
                when (mode) {
                    Backup.Mode.REPLACE -> replaceAll(archive)
                    Backup.Mode.MERGE -> mergeInto(archive)
                }
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** The archive becomes the document; what was here goes first. */
    private fun replaceAll(archive: Backup.Archive): Boolean {
        val incoming = archive.avatarJpeg
            ?.takeIf { it.isNotEmpty() }
            ?.let { bytes -> writePicture(bytes) }
            .orEmpty()
        val saved = updateDurable { archive.state.copy(avatarPhoto = incoming) }
        if (!saved) {
            // The state did not land, so the file it would have named is an
            // orphan the moment this returns — take it straight back out.
            if (incoming.isNotEmpty()) runCatching { File(appContext.filesDir, incoming).delete() }
            return false
        }
        dropAvatarsExcept(incoming.ifEmpty { null })
        return true
    }

    /** Union: what is here stays, and the archive only ever adds. */
    private fun mergeInto(archive: Backup.Archive): Boolean {
        val current = _state.value
        // The phone's own picture wins; the archived one is used only when
        // there is none here — which is exactly [Backup.merge]'s rule, with
        // the bytes handled where the bytes live.
        val picture = if (current.avatarPhoto.isNotBlank()) {
            current.avatarPhoto
        } else {
            archive.avatarJpeg
                ?.takeIf { it.isNotEmpty() }
                ?.let { bytes -> writePicture(bytes) }
                .orEmpty()
        }
        val saved = updateDurable { Backup.merge(current, archive.state).copy(avatarPhoto = picture) }
        val added = picture != current.avatarPhoto && picture.isNotEmpty()
        when {
            saved && added -> dropAvatarsExcept(picture)
            !saved && added -> runCatching { File(appContext.filesDir, picture).delete() }
        }
        return saved
    }

    private fun writePicture(bytes: ByteArray): String {
        val name = "$AVATAR_PREFIX${System.currentTimeMillis()}.jpg"
        val written = runCatching {
            File(appContext.filesDir, name).writeBytes(bytes)
        }.isSuccess
        return if (written) name else ""
    }

    // ── persistence ──────────────────────────────────────────────────────

    /**
     * The document, read defensively.
     *
     * Every field is read *by type* from one [SharedPreferences.getAll]
     * snapshot rather than through the typed getters, which throw when the
     * stored type is not the expected one — and a throw here is not a handled
     * error, it is a crash loop in [Application.onCreate] that only clearing
     * app data ends. A wrong-typed value is preserved, then defaulted; an
     * unreadable blob is preserved twice ([quarantine] to a file, and the raw
     * bytes kept for [keepUnreadable]) before a default is taken.
     */
    private fun load(): FyrState {
        val all = try {
            prefs.all
        } catch (e: Exception) {
            return FyrState()
        }
        schema = number(all, KEY_SCHEMA, 0)

        val habitsRaw = blob(all, KEY_HABITS)
        val completionsRaw = blob(all, KEY_COMPLETIONS)
        val trashRaw = blob(all, KEY_TRASH)

        val habits = FyrCodec.decodeHabits(habitsRaw)
        val completions = FyrCodec.decodeCompletions(completionsRaw)
        val trash = FyrCodec.decodeTrash(trashRaw)

        val loaded = FyrState(
            habits = habits.value,
            completions = completions.value,
            userName = text(all, KEY_USER_NAME).take(FyrState.FULL_NAME_LIMIT),
            nameAsked = flag(all, KEY_NAME_ASKED, false),
            weekStart = number(all, KEY_WEEK_START, FyrState.DEFAULT_WEEK_START),
            avatarPhoto = text(all, KEY_AVATAR_PHOTO),
            shelfHidden = text(all, KEY_SHELF_HIDDEN)
                .split(",")
                .filter { it.isNotBlank() }
                .toSet(),
            trash = trash.value,
        )

        if (habits.unread) unreadable(KEY_HABITS, habitsRaw, FyrCodec.encodeHabits(loaded.habits))
        if (completions.unread) {
            unreadable(KEY_COMPLETIONS, completionsRaw, FyrCodec.encodeCompletions(loaded.completions))
        }
        if (trash.unread) unreadable(KEY_TRASH, trashRaw, FyrCodec.encodeTrash(loaded.trash))

        return loaded
    }

    /** Type-safe scalar reads: a value of the wrong type preserves, then defaults. */

    private fun text(all: Map<String, *>, key: String, default: String = ""): String {
        val v = all[key] ?: return default
        if (v is String) return v
        quarantine(key, v.toString())
        return default
    }

    private fun flag(all: Map<String, *>, key: String, default: Boolean): Boolean {
        val v = all[key] ?: return default
        if (v is Boolean) return v
        quarantine(key, v.toString())
        return default
    }

    private fun number(all: Map<String, *>, key: String, default: Int): Int {
        val v = all[key] ?: return default
        if (v is Int) return v
        quarantine(key, v.toString())
        return default
    }

    private fun blob(all: Map<String, *>, key: String): String? {
        val v = all[key] ?: return null
        if (v is String) return v
        quarantine(key, v.toString())
        return null
    }

    /**
     * A payload this build could not fully read is set aside *before* anything
     * can overwrite it: the original bytes go to
     * `filesDir/quarantine/<key>.json`, and — for the document's blobs — into
     * [poisoned] with the state they decoded to as [baseline].
     *
     * Synchronous on purpose: it runs during [load], and the first write that
     * would replace the bytes can only happen after a user action, so the
     * ordering has to be established here rather than hoped for later.
     */
    private fun unreadable(key: String, raw: String?, encoded: String) {
        if (raw.isNullOrEmpty()) return
        quarantine(key, raw)
        poisoned[key] = raw
        baseline[key] = encoded
    }

    /** One quarantined payload per key — the last failure, kept whole. */
    private fun quarantine(key: String, raw: String?) {
        if (raw.isNullOrEmpty()) return
        runCatching {
            val dir = File(appContext.filesDir, QUARANTINE_DIR)
            dir.mkdirs()
            File(dir, "$key.json").writeText(raw)
        }
    }

    /**
     * While [key] is poisoned, the bytes that could not be read are what gets
     * written back — as long as the value itself has not moved since that
     * read. The first real change to the key's data (a new habit, a new tick)
     * stops the poisoning: the original is by then both superseded in memory
     * and sitting in its quarantine file, so nothing is lost by letting the
     * new value land.
     */
    private fun keepUnreadable(key: String, encoded: String): String {
        val raw = poisoned[key] ?: return encoded
        if (baseline[key] == encoded) return raw
        poisoned.remove(key)
        baseline.remove(key)
        return encoded
    }

    /**
     * Writes the whole document — one edit, one transaction, the platform's
     * own atomicity behind it.
     *
     * [durable] swaps the asynchronous [SharedPreferences.Editor.apply] for a
     * blocking commit and returns its verdict; only the avatar paths pay for
     * that, because only they delete files on the strength of this write.
     * Everything else stays on apply: the platform flushes queued writes when
     * the activity pauses, and blocking the main thread on disk for every
     * tick would be trading a hypothetical lost write for a visible one.
     *
     * The lint warning this raises — "prefer apply()" — is exactly the
     * default this function implements everywhere *except* the durable path,
     * where commit's blocking is the entire point and apply would defeat it.
     */
    @SuppressLint("ApplySharedPref")
    private fun persist(s: FyrState, durable: Boolean = false): Boolean {
        val habits = FyrCodec.encodeHabits(s.habits)
        val completions = FyrCodec.encodeCompletions(s.completions)
        val trash = FyrCodec.encodeTrash(s.trash)

        val edit = prefs.edit()
            .putString(KEY_HABITS, keepUnreadable(KEY_HABITS, habits))
            .putString(KEY_COMPLETIONS, keepUnreadable(KEY_COMPLETIONS, completions))
            .putString(KEY_TRASH, keepUnreadable(KEY_TRASH, trash))
            .putString(KEY_USER_NAME, s.userName)
            .putBoolean(KEY_NAME_ASKED, s.nameAsked)
            .putInt(KEY_WEEK_START, s.weekStartDay)
            .putString(KEY_AVATAR_PHOTO, s.avatarPhoto)
            .putString(KEY_SHELF_HIDDEN, s.shelfHidden.joinToString(","))
            // Stamped, never read as a gate: forward changes ride on the
            // document's own version so a build that has not seen them can
            // still hand the file back intact. maxOf keeps a newer stamp
            // newer across a downgrade.
            .putInt(KEY_SCHEMA, maxOf(schema, SCHEMA_VERSION))

        return if (durable) {
            edit.commit()
        } else {
            edit.apply()
            true
        }
    }

    private companion object {
        const val FILE = "fyr"

        /**
         * The document's schema. Bumped when the shape changes and a real
         * migration is needed; a build that only *adds* an optional field
         * needs no bump, because every reader defaults what it does not know.
         */
        const val SCHEMA_VERSION = 1

        /** Where payloads this build could not read are preserved. */
        const val QUARANTINE_DIR = "quarantine"

        const val KEY_HABITS = "habits"
        const val KEY_COMPLETIONS = "completions"
        const val KEY_USER_NAME = "userName"
        const val KEY_NAME_ASKED = "nameAsked"
        const val KEY_WEEK_START = "weekStart"
        const val KEY_AVATAR_PHOTO = "avatarPhoto"
        const val KEY_SHELF_HIDDEN = "shelfHidden"
        const val KEY_TRASH = "trash"
        const val KEY_SCHEMA = "schema"

        /**
         * The prefix every profile picture is written under — today
         * `avatar<millis>.jpg`, previously the fixed `avatar.jpg`. Matching on
         * the prefix is what lets one upload clear away the other.
         */
        const val AVATAR_PREFIX = "avatar"
    }
}
