package com.refclock.match

object Presets {
    val all = listOf(
        ayso(id = "ayso-10u", name = "AYSO 10U", halfMinutes = 25),
        ayso(id = "ayso-12u", name = "AYSO 12U", halfMinutes = 30),
        // For feeling the vibration patterns without waiting out a real half.
        Preset(
            id = "test",
            name = "Test (30 s)",
            halfLengthMs = mmss(0, 30),
            checkpoints = listOf(Checkpoint(mmss(0, 15), "Checkpoint")),
        ),
    )

    val default: Preset get() = all.first()

    fun byId(id: String?): Preset = all.firstOrNull { it.id == id } ?: default

    /**
     * The clock keeps running through a 1-minute water break mid-half, and both quarters get equal
     * playing time, so the break starts at (half - 1 min) / 2: 12:00 in a 25-minute half.
     */
    private fun ayso(id: String, name: String, halfMinutes: Int): Preset {
        val half = mmss(halfMinutes)
        return Preset(id, name, half, listOf(Checkpoint((half - WATER_BREAK_MS) / 2, "Quarter break")))
    }

    private const val WATER_BREAK_MS = 60_000L

    private fun mmss(minutes: Int, seconds: Int = 0): Long = (minutes * 60L + seconds) * 1000
}
