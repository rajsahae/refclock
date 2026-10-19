package com.refclock.match

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An alert's rhythm, followed by the vibration and the beep alike: alternating on/off ms, starting "on". */
enum class Pattern(internal val onOff: List<Long>) {
    QUICK_2(listOf(150, 250, 150)),
    QUICK_3(listOf(150, 250, 150, 250, 150)),
    QUICK_4(listOf(150, 250, 150, 250, 150, 250, 150)),
    LONG_2(listOf(800, 300, 800)),
    LONG_3(listOf(800, 300, 800, 300, 800)),
    SUSTAINED(listOf(1500)),
    LONG_QUICK(listOf(800, 300, 150, 200, 150)),
}

data class Pulse(val startMs: Long, val durationMs: Long)

object AlertTiming {
    const val GAP_MS = 3000L

    /** Repeats until acknowledged, but capped so an unnoticed alert can't buzz all half. */
    const val CAP_MS = 60_000L

    /** The pattern once, from 0. */
    fun pulses(pattern: Pattern): List<Pulse> {
        var t = 0L
        return pattern.onOff.mapIndexedNotNull { i, ms ->
            val pulse = if (i % 2 == 0) Pulse(t, ms) else null
            t += ms
            pulse
        }
    }

    /** One repeat: the pattern, then [GAP_MS] of quiet. */
    fun cycleMs(pattern: Pattern): Long = pattern.onOff.sum() + GAP_MS

    /** As many whole repeats as fit in [capMs]; the last one's trailing gap may run past it. */
    fun repeats(pattern: Pattern, capMs: Long = CAP_MS): Int =
        ((capMs + GAP_MS) / cycleMs(pattern)).toInt().coerceAtLeast(1)

    fun repeatedPulses(pattern: Pattern, capMs: Long = CAP_MS): List<Pulse> {
        val once = pulses(pattern)
        val cycle = cycleMs(pattern)
        return (0 until repeats(pattern, capMs)).flatMap { r -> once.map { it.copy(startMs = it.startMs + r * cycle) } }
    }

    /** The vibrator's waveform format: alternating off/on durations, starting with "off". */
    fun waveform(pulses: List<Pulse>): List<Long> {
        var t = 0L
        return pulses.flatMap { p ->
            listOf(p.startMs - t, p.durationMs).also { t = p.startMs + p.durationMs }
        }
    }
}

/** The four alerts that can be set up separately. */
enum class AlertSlot { QUARTER_BREAK, HALF_TIME, FULL_TIME, HALFTIME_OVER }

/** With both [vibrate] and [beep] off, the alert only shows on screen. */
@Serializable
data class AlertStyle(val pattern: Pattern, val vibrate: Boolean = true, val beep: Boolean = false)

/** App-wide settings; the defaults are the patterns the app shipped with. */
@Serializable
data class AppSettings(
    val quarterBreak: AlertStyle = AlertStyle(Pattern.QUICK_4),
    val halfTime: AlertStyle = AlertStyle(Pattern.LONG_2),
    val fullTime: AlertStyle = AlertStyle(Pattern.LONG_3),
    val halftimeOver: AlertStyle = AlertStyle(Pattern.SUSTAINED),
    val halftimeCountUp: Boolean = false,
) {
    fun styleFor(kind: AlertKind, half: Int): AlertStyle = style(
        when (kind) {
            AlertKind.CHECKPOINT -> AlertSlot.QUARTER_BREAK
            AlertKind.HALF_END -> if (half == 1) AlertSlot.HALF_TIME else AlertSlot.FULL_TIME
            AlertKind.HALFTIME_END -> AlertSlot.HALFTIME_OVER
        },
    )

    fun style(slot: AlertSlot): AlertStyle = when (slot) {
        AlertSlot.QUARTER_BREAK -> quarterBreak
        AlertSlot.HALF_TIME -> halfTime
        AlertSlot.FULL_TIME -> fullTime
        AlertSlot.HALFTIME_OVER -> halftimeOver
    }

    fun withStyle(slot: AlertSlot, style: AlertStyle): AppSettings = when (slot) {
        AlertSlot.QUARTER_BREAK -> copy(quarterBreak = style)
        AlertSlot.HALF_TIME -> copy(halfTime = style)
        AlertSlot.FULL_TIME -> copy(fullTime = style)
        AlertSlot.HALFTIME_OVER -> copy(halftimeOver = style)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun encode(settings: AppSettings): String = json.encodeToString(settings)

        /** Null if [text] isn't readable (say, a pattern a newer version added), so the caller can use defaults. */
        fun decode(text: String): AppSettings? = runCatching { json.decodeFromString<AppSettings>(text) }.getOrNull()
    }
}
