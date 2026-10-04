package com.fyr.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Two themes, one accent.
 *
 * The palette is deliberately narrow: near-black or near-white ground, ink or
 * paper text, and a single ember accent reserved for *completed* work — the one
 * moment in the interface worth colouring. Everything else is neutral, which is
 * what keeps the app reading as a tool rather than a toy.
 */

// ── ember accent ─────────────────────────────────────────────────────────
// Dark enough that white sits above 3:1 on it, which is the bar for a graphic
// control like the tick. Light enough to still read as fire on a black ground.
val Ember = Color(0xFFE8590C)
val EmberDim = Color(0xFFB23F06)
val EmberSoft = Color(0xFFFF8A3D)

// ── dark ─────────────────────────────────────────────────────────────────
val NightGround = Color(0xFF0A0A0B)      // window / screen
val NightSurface = Color(0xFF141416)     // cards, rows
val NightSurfaceHigh = Color(0xFF1D1D20) // raised, sheets
val NightRim = Color(0xFF2A2A2E)         // hairlines, borders
val NightInk = Color(0xFFF5F5F7)         // primary text
val NightInkMuted = Color(0xFF9A9AA1)    // secondary
val NightInkFaint = Color(0xFF63636A)    // tertiary / disabled

// ── light ────────────────────────────────────────────────────────────────
val DayGround = Color(0xFFFAFAFB)
val DaySurface = Color(0xFFFFFFFF)
val DaySurfaceHigh = Color(0xFFFFFFFF)
val DayRim = Color(0xFFE6E6EA)
val DayInk = Color(0xFF131316)
val DayInkMuted = Color(0xFF6A6A73)
val DayInkFaint = Color(0xFF9C9CA4)

/** Heatmap ramp: bare track, then four ember steps. Never red, never alarming. */
val HeatTrack = Color(0xFF1B1B1F)
val Heat0 = Color(0xFF3A2213)
val Heat1 = Color(0xFF7A3A10)
val Heat2 = Color(0xFFB2470D)
val Heat3 = Color(0xFFD4540C)
val Heat4 = Color(0xFFFF7A2E)

val HeatTrackLight = Color(0xFFECECEF)
val Heat0Light = Color(0xFFFFE8D8)
val Heat1Light = Color(0xFFFFD0B0)
val Heat2Light = Color(0xFFFFAD76)
val Heat3Light = Color(0xFFF5843C)
val Heat4Light = Color(0xFFE8590C)

/**
 * Frozen (excused) days sit outside the ember ramp entirely: ice, in the two
 * weights the frozen flame itself uses — bright frost that reads on the
 * near-black ground, deep ice that reads on the near-white one. Deliberately
 * not a warm step, so no day of rest can be mistaken for a day of fire.
 */
val HeatFrozen = Color(0xFF55BCEF)
val HeatFrozenLight = Color(0xFF1B74B5)
