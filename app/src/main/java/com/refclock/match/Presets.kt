package com.refclock.match

object Presets {
    val all = listOf(
        Preset(
            id = "ayso-10u",
            name = "AYSO 10U",
            halfLengthMs = mmss(25),
            checkpoints = listOf(Checkpoint(mmss(12, 30), "Quarter break")),
        ),
        Preset(
            id = "ayso-12u",
            name = "AYSO 12U",
            halfLengthMs = mmss(30),
            checkpoints = listOf(Checkpoint(mmss(15), "Quarter break")),
        ),
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

    private fun mmss(minutes: Int, seconds: Int = 0): Long = (minutes * 60L + seconds) * 1000
}
