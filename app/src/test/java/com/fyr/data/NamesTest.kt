package com.fyr.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two shapes a name passes through: [nameAsTyped] while it is being
 * typed, [normalizeName] when it is stored.
 *
 * The contract each half owns: the field takes **letters only, twelve of
 * them** — no digit, symbol, or space ever lands in it, so the trailing
 * space a keyboard suggestion appends dies on arrival; the write edge
 * takes letters and single spaces, because that is where the two fields
 * meet and the greeting is joined.
 */
class NamesTest {

    @Test
    fun `a stored name has no stray space and a capital at every word`() {
        assertEquals("Deepanshu Chauhan", normalizeName("  deepanshu   chauhan  "))
        assertEquals("Deepanshu", normalizeName("deepanshu"))
        assertEquals("Deepanshu", normalizeName("deepanshu   "))
        assertEquals("Deepanshu", normalizeName("   deepanshu"))
    }

    @Test
    fun `capitals are added, never removed`() {
        assertEquals("DEEPA NSHAU", normalizeName("DEEPA NSHAU"))
        // Only a word's first letter is ever touched, and nothing is ever
        // lowercased — a name the person capitalised themselves survives.
        assertEquals("McDonald", normalizeName("McDonald"))
        assertEquals("Mcdonald", normalizeName("mcdonald"))
    }

    @Test
    fun `a field takes letters and nothing else`() {
        // The keyboard's suggestion lands "deepanshu " — the space is
        // dropped before the person can even see it.
        assertEquals("Deepanshu", nameAsTyped("deepanshu "))
        assertEquals("Deepanshu", nameAsTyped(" deepanshu"))
        // A digit or symbol is not refused with a beep; it simply never
        // enters the field.
        assertEquals("Abc", nameAsTyped("a1b2c!"))
        assertEquals("Deepanshu", nameAsTyped("deepanshu42"))
        assertEquals("", nameAsTyped(" "))
    }

    @Test
    fun `a field stops at twelve letters`() {
        assertEquals("Abcdefghijkl", nameAsTyped("abcdefghijklmn")) // 14 -> 12, capped
        assertEquals("Deepanshu", nameAsTyped("Deepanshu")) // 9 letters, untouched
        // The write edge's own ceiling is separate and larger: the joined
        // full name, first and last with the space between.
        val long = "x".repeat(FyrState.FULL_NAME_LIMIT)
        assertEquals(FyrState.FULL_NAME_LIMIT, normalizeName(long).length)
    }

    @Test
    fun `the storage edge drops what no field should have let through`() {
        // Belt for paths that skipped the field: an old install, a paste.
        assertEquals("Deepanshu Chauhan", normalizeName("deepanshu 42 chauhan!"))
        assertEquals("", normalizeName(" 🔥 "))
    }
}
