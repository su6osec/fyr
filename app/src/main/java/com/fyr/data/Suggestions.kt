package com.fyr.data

/**
 * The prebuilt shelf shown the moment the user taps +.
 *
 * Every entry is phrased as the behaviour being *kept*, never the behaviour
 * being avoided in the raw — so ticking one always reads as a success. That is
 * what lets a strict-streak app be strict about the number without being harsh
 * about the person.
 *
 * Emoji are Unicode strings here; the renderer resolves them to art through the
 * override folder first (see Emoji.kt).
 */
object Suggestions {

    data class Suggestion(
        val name: String,
        val emoji: String,
        val category: Category,
    )

    val all: List<Suggestion> = listOf(
        // ── Health ─────────────────────────────────────────────
        Suggestion("Gym workout", "🏋️", Category.HEALTH),
        Suggestion("Seven-hour sleep", "😴", Category.HEALTH),
        Suggestion("10K steps", "👣", Category.HEALTH),
        Suggestion("Drink 3L water", "💧", Category.HEALTH),
        Suggestion("Brush twice", "🦷", Category.HEALTH),

        // ── Sports ─────────────────────────────────────────────
        Suggestion("Run 5 km", "🏃", Category.SPORTS),
        Suggestion("Play a sport", "⚽", Category.SPORTS),
        Suggestion("Stretch", "🤸", Category.SPORTS),

        // ── Lifestyle ──────────────────────────────────────────
        Suggestion("Read a book", "📖", Category.LIFESTYLE),
        Suggestion("Journal", "📓", Category.LIFESTYLE),
        Suggestion("Clean for 10 min", "🧹", Category.LIFESTYLE),
        Suggestion("Make the bed", "🛏️", Category.LIFESTYLE),

        // ── Mind growth ────────────────────────────────────────
        Suggestion("Meditation", "🧘", Category.MIND),
        Suggestion("Learn a language", "🔤", Category.MIND),
        Suggestion("Practice gratitude", "🙏", Category.MIND),
        Suggestion("Deep work 1 hour", "🎯", Category.MIND),

        // ── Break bad habits ───────────────────────────────────
        Suggestion("Social media limit", "📵", Category.QUIT),
        Suggestion("No junk food", "🍔", Category.QUIT),
        Suggestion("Quit smoking", "🚭", Category.QUIT),
        Suggestion("No late-night scrolling", "🌙", Category.QUIT),
    )

    /**
     * Emoji offered in the picker. Kept deliberately short — this is a picker
     * for labelling a habit, not a keyboard.
     */
    val palette: List<String> = listOf(
        "🏋️", "😴", "👣", "💧", "🦷",
        "🏃", "⚽", "🤸", "🏆", "👟",
        "📖", "📓", "🧹", "🛏️", "📚",
        "🧘", "🔤", "🙏", "🎯", "🧠",
        "📵", "🍔", "🚭", "🌙", "🍬",
        "🔥", "⭐", "✅", "💤", "🚶",
    )

    /** The flame, used by streak readouts and empty states. */
    const val FLAME: String = "🔥"
}
