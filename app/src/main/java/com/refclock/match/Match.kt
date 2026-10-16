package com.refclock.match

data class Checkpoint(val atMs: Long, val label: String)

data class Alert(val atMs: Long, val label: String, val isHalfEnd: Boolean)

data class Preset(
    val id: String,
    val name: String,
    val halfLengthMs: Long,
    val checkpoints: List<Checkpoint>,
    /** Listed only while the watch has Developer options turned on. */
    val devOnly: Boolean = false,
) {
    val alerts: List<Alert> =
        (checkpoints.map { Alert(it.atMs, it.label, isHalfEnd = false) } +
            Alert(halfLengthMs, "Half ends", isHalfEnd = true))
            .sortedBy { it.atMs }

    fun nextAlert(halfElapsedMs: Long): Alert? = alerts.firstOrNull { it.atMs > halfElapsedMs }
}

enum class Phase { IDLE, RUNNING, PAUSED, HALFTIME, FINISHED }

/**
 * Timestamps are SystemClock.elapsedRealtime(), so a wall-clock sync mid-match can't shift the clock.
 * Transitions that don't apply to the current phase return the state unchanged.
 */
data class MatchState(
    val phase: Phase = Phase.IDLE,
    val presetId: String = Presets.default.id,
    val half: Int = 1,
    val halfStartedAt: Long = 0,
    val pausedTotalMs: Long = 0,
    val pausedAt: Long = 0,
    /** Final clock of the half just ended, shown at halftime and full time. */
    val endedHalfMs: Long = 0,
) {
    val isActive: Boolean
        get() = phase == Phase.RUNNING || phase == Phase.PAUSED || phase == Phase.HALFTIME

    /** elapsedRealtime() at which the current half's clock read 0:00, net of pauses. */
    val clockZero: Long
        get() = halfStartedAt + pausedTotalMs

    fun halfElapsedMs(now: Long): Long = when (phase) {
        Phase.IDLE -> 0
        Phase.RUNNING -> now - clockZero
        Phase.PAUSED -> pausedAt - clockZero
        Phase.HALFTIME, Phase.FINISHED -> endedHalfMs
    }.coerceAtLeast(0)

    /** Earlier halves count at their scheduled length, not as played. */
    fun gameElapsedMs(preset: Preset, now: Long): Long =
        (half - 1) * preset.halfLengthMs + halfElapsedMs(now)

    fun selectPreset(id: String) =
        if (phase == Phase.IDLE) copy(presetId = id) else this

    fun kickOff(now: Long) =
        if (phase == Phase.IDLE) {
            copy(phase = Phase.RUNNING, half = 1, halfStartedAt = now, pausedTotalMs = 0, endedHalfMs = 0)
        } else this

    fun pause(now: Long) =
        if (phase == Phase.RUNNING) copy(phase = Phase.PAUSED, pausedAt = now) else this

    fun resume(now: Long) =
        if (phase == Phase.PAUSED) {
            copy(phase = Phase.RUNNING, pausedTotalMs = pausedTotalMs + (now - pausedAt))
        } else this

    fun endHalf(now: Long) =
        if (phase == Phase.RUNNING || phase == Phase.PAUSED) {
            copy(
                phase = if (half == 1) Phase.HALFTIME else Phase.FINISHED,
                endedHalfMs = halfElapsedMs(now),
            )
        } else this

    fun startSecondHalf(now: Long) =
        if (phase == Phase.HALFTIME) {
            copy(phase = Phase.RUNNING, half = 2, halfStartedAt = now, pausedTotalMs = 0)
        } else this

    fun newMatch() = MatchState(presetId = presetId)
}

/** Compact length for menus, e.g. "25m", "30s" or "27m 30s". */
fun formatLength(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes == 0L -> "${seconds}s"
        seconds == 0L -> "${minutes}m"
        else -> "${minutes}m ${seconds}s"
    }
}

fun formatElapsed(ms: Long): String = formatSeconds(ms.coerceAtLeast(0) / 1000)

/** Rounds up, so a countdown reaches 0:00 exactly when its alert fires. */
fun formatRemaining(ms: Long): String = formatSeconds((ms.coerceAtLeast(0) + 999) / 1000)

private fun formatSeconds(totalSeconds: Long) = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
