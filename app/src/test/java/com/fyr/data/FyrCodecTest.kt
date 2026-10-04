package com.fyr.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The document codecs, with the failure modes they exist for.
 *
 * The contract under test is the one [Store] relies on to keep its promise
 * that a bad read is never persisted as an empty one: decoding is partial
 * (one bad entry costs that entry), and *anything* unreadable is reported
 * through [FyrCodec.Decoded.unread] so the raw bytes can be preserved first.
 */
class FyrCodecTest {

    // Thu 1 Oct 2026 = epoch day 20727. All October dates in these tests are
    // derived from it, so the arithmetic stays legible.
    private val thu1 = 20727

    private fun habit(
        id: String,
        name: String,
        createdAt: Int = thu1,
        category: Category = Category.HEALTH,
    ) = Habit(
        id = id,
        name = name,
        emoji = "🔥",
        category = category,
        createdAt = createdAt,
        weekdays = emptySet(),
    )

    // ── round trips ─────────────────────────────────────────────────────

    @Test
    fun `a habit survives encode and decode unchanged`() {
        val original = habit("a", "Republic Fitness").copy(
            weekdays = setOf(1, 3, 5),
            skipDays = setOf(20731),
            skipWeekdays = setOf(7),
            archived = true,
        )
        val decoded = FyrCodec.decodeHabits(FyrCodec.encodeHabits(listOf(original)))

        assertEquals(listOf(original), decoded.value)
        assertFalse(decoded.unread, "a faithful round trip is not a failure")
    }

    @Test
    fun `completions round-trip sorted and complete`() {
        val c = mapOf("a" to setOf(20731, 20727, 20729), "b" to setOf(20728))
        val decoded = FyrCodec.decodeCompletions(FyrCodec.encodeCompletions(c))
        assertEquals(c, decoded.value)
        assertFalse(decoded.unread)
    }

    @Test
    fun `an empty document is a first run, not an error`() {
        assertFalse(FyrCodec.decodeHabits(null).unread)
        assertFalse(FyrCodec.decodeHabits("").unread)
        assertFalse(FyrCodec.decodeHabits("[]").unread)
        assertFalse(FyrCodec.decodeCompletions(null).unread)
        assertFalse(FyrCodec.decodeCompletions("{}").unread)
        assertFalse(FyrCodec.decodeTrash("").unread)
    }

    // ── partial recovery: one bad entry costs one entry ─────────────────

    @Test
    fun `one habit without an id costs that habit and nothing else`() {
        val json = """
            [
                {"id":"a","name":"Alpha","category":"HEALTH","createdAt":$thu1},
                {"name":"No id","category":"MIND","createdAt":$thu1},
                {"id":"c","name":"Charlie","category":"LIFESTYLE","createdAt":$thu1}
            ]
        """.trimIndent()

        val decoded = FyrCodec.decodeHabits(json)

        assertEquals(listOf("Alpha", "Charlie"), decoded.value.map { it.name })
        assertTrue(decoded.unread, "the skipped entry must be reported so its bytes are kept")
    }

    @Test
    fun `one malformed completions entry keeps every other habit's ticks`() {
        // The old shape of this decode put the whole loop under one catch:
        // "b" not being an array discarded Alpha's and Charlie's history too,
        // which is a glitch becoming a wiped history.
        val json = """{"a":[20727,20728],"b":"not-an-array","c":[20730]}"""

        val decoded = FyrCodec.decodeCompletions(json)

        assertEquals(setOf(20727, 20728), decoded.value["a"])
        assertEquals(setOf(20730), decoded.value["c"])
        assertNull(decoded.value["b"])
        assertTrue(decoded.unread)
    }

    @Test
    fun `a truncated document reports unread instead of passing as empty`() {
        val decoded = FyrCodec.decodeHabits("""[{"id":"a","name":"Alpha","category":"HEALTH"""")
        assertTrue(decoded.unread, "truncation must never look like a clean first run")
        assertTrue(decoded.value.isEmpty())
    }

    // ── forward and backward tolerance ──────────────────────────────────

    @Test
    fun `an unknown category keeps the habit instead of dropping it`() {
        val json = """[{"id":"a","name":"Alpha","category":"A_CATEGORY_FROM_THE_FUTURE","createdAt":$thu1}]"""

        val decoded = FyrCodec.decodeHabits(json)

        assertEquals(1, decoded.value.size)
        assertEquals(Category.LIFESTYLE, decoded.value.first().category)
        assertEquals("Alpha", decoded.value.first().name)
        assertFalse(decoded.unread, "a repaired field is a repair, not a loss")
    }

    @Test
    fun `fields written by older builds are upgraded in place`() {
        // No weekdays, no archived, no skipDays, no skipWeekdays — the shape
        // before those four existed.
        val json = """[{"id":"a","name":"Alpha","category":"MIND","createdAt":$thu1}]"""

        val h = FyrCodec.decodeHabits(json).value.single()

        assertEquals(emptySet(), h.weekdays)
        assertFalse(h.archived)
        assertEquals(emptySet(), h.skipDays)
        assertEquals(emptySet(), h.skipWeekdays)
    }

    @Test
    fun `day sets read the sloppy forms identically`() {
        assertEquals(emptySet<Int>(), FyrCodec.readDaySet(""))
        assertEquals(setOf(1, 2, 3), FyrCodec.readDaySet("1,2,3"))
        assertEquals(setOf(1, 2, 3), FyrCodec.readDaySet(" 1 , 2 , 3 "))
        assertEquals(setOf(1, 3), FyrCodec.readDaySet("1,junk,,3,-"))
    }

    @Test
    fun `the trash round-trips whole`() {
        val entry = TrashedHabit(
            habit = habit("x", "Old habit", createdAt = 20000),
            completions = setOf(20727, 20728),
            deletedAt = 20740,
            index = 2,
        )
        val decoded = FyrCodec.decodeTrash(FyrCodec.encodeTrash(listOf(entry)))

        assertEquals(listOf(entry), decoded.value)
        assertFalse(decoded.unread)
    }
}
