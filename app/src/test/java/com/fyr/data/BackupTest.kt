package com.fyr.data

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Export → parse → merge, the whole round trip a person's only copy of their
 * history takes when they move phones.
 *
 * The invariants, in order of how expensive breaking one would be:
 *
 *  1. What Fyr writes, Fyr reads — byte for byte, field for field.
 *  2. What Fyr does *not* recognise, it refuses — a damaged archive is never
 *     imported as a smaller one, because replace-then-partial-restore is
 *     data loss wearing a confirmation dialog.
 *  3. Merge never removes — union only, across every field it touches.
 */
class BackupTest {

    // Thu 1 Oct 2026 = 20727; see StatsTest for the October date table.
    private val thu1 = 20727

    private fun fixture(withAvatar: Boolean = false): FyrState {
        val habits = listOf(
            Habit(
                id = "a",
                name = "Republic Fitness",
                emoji = "🏋️",
                category = Category.SPORTS,
                createdAt = thu1,
                weekdays = setOf(1, 3, 5),
                skipDays = setOf(20731),
            ),
            Habit(
                id = "b",
                name = "Duolingo",
                emoji = "🦉",
                category = Category.MIND,
                createdAt = thu1 + 2,
                archived = true,
            ),
        )
        val trashed = TrashedHabit(
            habit = Habit(
                id = "gone",
                name = "Was here",
                emoji = "🔥",
                category = Category.LIFESTYLE,
                createdAt = 20000,
            ),
            completions = setOf(20001, 20002),
            deletedAt = 20740,
            index = 4,
        )
        return FyrState(
            habits = habits,
            completions = mapOf("a" to setOf(thu1, thu1 + 7), "b" to setOf(thu1 + 3)),
            userName = "Deepanshu Chauhan",
            nameAsked = true,
            weekStart = 3,
            shelfHidden = setOf("b"),
            trash = listOf(trashed),
            avatarPhoto = if (withAvatar) "avatar1.jpg" else "",
        )
    }

    // ── round trip ─────────────────────────────────────────────────────

    @Test
    fun `what Fyr exports, Fyr parses back unchanged`() {
        val original = fixture()

        val parsed = assertNotNull(
            Backup.parse(Backup.export(original)),
            "an archive Fyr wrote must always be one Fyr can read",
        )

        assertEquals(original.habits, parsed.state.habits)
        assertEquals(original.completions, parsed.state.completions)
        assertEquals(original.trash, parsed.state.trash)
        assertEquals(original.userName, parsed.state.userName)
        assertEquals(original.nameAsked, parsed.state.nameAsked)
        assertEquals(original.weekStartDay, parsed.state.weekStartDay)
        assertEquals(original.shelfHidden, parsed.state.shelfHidden)
        assertTrue(parsed.state.avatarPhoto.isEmpty(), "a file name is an address, not data")
        assertTrue(parsed.exportedAt > 0, "the dialog says when the file was written")
    }

    @Test
    fun `the picture travels as bytes and counts what it holds`() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x42, 0x00, 0x7F)

        val parsed = assertNotNull(Backup.parse(Backup.export(fixture(withAvatar = true), jpeg)))

        assertContentEquals(jpeg, parsed.avatarJpeg)
        assertEquals(3, parsed.tickCount, "two ticks on a, one on b")
    }

    @Test
    fun `an empty archive is still a faithful one`() {
        val parsed = assertNotNull(Backup.parse(Backup.export(FyrState())))
        assertTrue(parsed.state.habits.isEmpty())
        assertEquals(0, parsed.tickCount)
        assertTrue(parsed.state.trash.isEmpty())
    }

    // ── refusal: a damaged file is never partially imported ────────────

    @Test
    fun `something that is not JSON, and not a Fyr file, are the same refusal`() {
        assertNull(Backup.parse("not json at all"))
        assertNull(Backup.parse("""{"some":"other app's export"}"""))
        assertNull(Backup.parse(Backup.export(fixture()).dropLast(40)), "truncated")
        assertNull(Backup.parse(""), "empty file")
    }

    @Test
    fun `a habit Fyr cannot fully read rejects the whole file`() {
        val json = Backup.export(fixture())
        // The same file Fyr just wrote, with habit "a"'s id stripped out:
        // parse must refuse the whole archive rather than import "b" and
        // quietly drop one — replace-then-partial-restore is exactly the
        // loss the confirmation dialog exists to prevent.
        val damaged = json.replace("\"id\":\"a\",", "")

        assertNull(Backup.parse(damaged))
    }

    @Test
    fun `a newer file's version does not refuse it by itself`() {
        val newer = Backup.export(fixture()).replace("\"version\":1", "\"version\":99")
        assertNotNull(
            Backup.parse(newer),
            "unknown future fields are ignored; only unreadable content is refused",
        )
    }

    // ── merge: union, never removal ────────────────────────────────────

    @Test
    fun `merge keeps everything this phone holds and adds what it is missing`() {
        val here = fixture()
        val fromOldPhone = FyrState(
            habits = listOf(
                // The same habit, renamed on *this* phone — here wins.
                here.habits.first().copy(name = "Gym (old name)"),
                // One this phone has never seen.
                Habit(
                    id = "c",
                    name = "Read",
                    emoji = "📚",
                    category = Category.MIND,
                    createdAt = thu1 + 1,
                ),
            ),
            completions = mapOf(
                // A day ticked only there — union keeps it.
                "a" to setOf(thu1 + 1),
                "c" to setOf(thu1 + 2),
            ),
            userName = "",
            weekStart = 1,
            trash = emptyList(),
        )

        val merged = Backup.merge(here, fromOldPhone)

        assertEquals(listOf("a", "b", "c"), merged.habits.map { it.id }, "added, never reordered")
        assertEquals("Republic Fitness", merged.habits.first().name, "this phone's record wins")
        assertEquals(setOf(thu1, thu1 + 1, thu1 + 7), merged.completions["a"], "ticks union")
        assertEquals(setOf(thu1 + 2), merged.completions["c"])
        assertEquals("Deepanshu Chauhan", merged.userName, "an empty incoming name cannot blank this one")
        assertEquals(3, merged.weekStartDay, "this phone's preference holds")
        assertEquals(listOf("gone"), merged.trash.map { it.habit.id }, "and the trash still holds")
        assertEquals(setOf("b"), merged.shelfHidden)
        assertEquals(here.avatarPhoto, merged.avatarPhoto, "the picture never changes hands in a merge")
    }

    @Test
    fun `merge fills an empty profile from the archive`() {
        val blank = FyrState()
        val filled = fixture()

        val merged = Backup.merge(blank, filled)

        assertEquals("Deepanshu Chauhan", merged.userName)
        assertTrue(merged.nameAsked)
        assertEquals(filled.habits, merged.habits)
        assertEquals(filled.trash, merged.trash)
    }

    @Test
    fun `merge does not list one habit as deleted and alive at once`() {
        val here = FyrState(
            trash = listOf(
                TrashedHabit(
                    habit = Habit("x", "X", "🔥", Category.HEALTH, thu1),
                    completions = emptySet(),
                    deletedAt = 20740,
                    index = 0,
                ),
            ),
        )
        val incoming = FyrState(
            habits = listOf(Habit("x", "X", "🔥", Category.HEALTH, thu1)),
            trash = here.trash,
        )

        val merged = Backup.merge(here, incoming)

        assertTrue(merged.habits.isEmpty(), "this phone deleted it; the file does not revive it")
        assertEquals(1, merged.trash.size, "and it is not counted as deleted twice")
    }

    @Test
    fun `ticks for habits that exist nowhere are not carried as orphans`() {
        val merged = Backup.merge(
            FyrState(habits = listOf(Habit("keep", "K", "🔥", Category.HEALTH, thu1))),
            FyrState(completions = mapOf("nobody" to setOf(thu1))),
        )

        assertFalse(merged.completions.containsKey("nobody"))
    }
}
