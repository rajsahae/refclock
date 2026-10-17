package com.refclock.match

enum class AlertKind { CHECKPOINT, HALF_END, HALFTIME_END }

/** [atMs] is on the half clock, or for [AlertKind.HALFTIME_END] on the halftime clock. */
data class Alert(val atMs: Long, val label: String, val kind: AlertKind) {
    val isHalfEnd: Boolean get() = kind == AlertKind.HALF_END
}

enum class Phase { IDLE, RUNNING, PAUSED, HALFTIME, FINISHED }

/**
 * Timestamps are SystemClock.elapsedRealtime(), so a wall-clock sync mid-match can't shift the clock.
 * Transitions that don't apply to the current phase return the state unchanged.
 */
data class MatchState(
    val phase: Phase = Phase.IDLE,
    /** The profile chosen on the setup screen. */
    val profileId: String = Profiles.defaults.first().id,
    /** Copied at kickoff, so editing or deleting the profile mid-match can't move the clock or its alerts. */
    val profile: Profile = Profiles.defaults.first(),
    val half: Int = 1,
    val halfStartedAt: Long = 0,
    val pausedTotalMs: Long = 0,
    val pausedAt: Long = 0,
    /** Final clock of the half just ended, shown at halftime and full time. */
    val endedHalfMs: Long = 0,
    val halftimeStartedAt: Long = 0,
    /** For the Full time summary: the first half's final clock and how long halftime actually ran. */
    val firstHalfMs: Long = 0,
    val halftimeMs: Long = 0,
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

    /** The match clock is stopped at halftime; this is the separate halftime clock. */
    fun halftimeElapsedMs(now: Long): Long =
        if (phase == Phase.HALFTIME) (now - halftimeStartedAt).coerceAtLeast(0) else 0

    /** Earlier halves count at their scheduled length, not as played. */
    fun gameElapsedMs(now: Long): Long =
        (half - 1) * profile.halfLengthMs + halfElapsedMs(now)

    fun selectProfile(id: String) =
        if (phase == Phase.IDLE) copy(profileId = id) else this

    fun kickOff(now: Long, profile: Profile) =
        if (phase == Phase.IDLE) {
            copy(
                phase = Phase.RUNNING, profile = profile, half = 1,
                halfStartedAt = now, pausedTotalMs = 0, endedHalfMs = 0,
            )
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
                halftimeStartedAt = if (half == 1) now else halftimeStartedAt,
                firstHalfMs = if (half == 1) halfElapsedMs(now) else firstHalfMs,
            )
        } else this

    fun startSecondHalf(now: Long) =
        if (phase == Phase.HALFTIME) {
            copy(
                phase = Phase.RUNNING, half = 2, halfStartedAt = now, pausedTotalMs = 0,
                halftimeMs = halftimeElapsedMs(now),
            )
        } else this

    fun newMatch() = MatchState(profileId = profileId)
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
