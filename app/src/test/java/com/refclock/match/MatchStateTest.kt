package com.refclock.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchStateTest {
    private val profile = Profiles.defaults.single { it.id == "ayso-12u" }
    private val min = 60_000L

    @Test
    fun clockRunsFromKickOff() {
        val s = MatchState().kickOff(now = 1_000, profile)
        assertEquals(90_000, s.halfElapsedMs(91_000))
    }

    @Test
    fun pausedTimeIsExcluded() {
        val s = MatchState().kickOff(0, profile).pause(10_000)
        assertEquals(10_000, s.halfElapsedMs(20_000))
        assertEquals(15_000, s.resume(25_000).halfElapsedMs(30_000))
    }

    @Test
    fun clockKeepsRunningPastHalfLength() {
        val s = MatchState().kickOff(0, profile)
        assertEquals(31 * min, s.halfElapsedMs(31 * min))
    }

    @Test
    fun secondHalfRestartsAndGameCountsScheduledFirstHalf() {
        val halftime = MatchState().kickOff(0, profile).endHalf(31 * min)
        assertEquals(Phase.HALFTIME, halftime.phase)
        assertEquals(31 * min, halftime.halfElapsedMs(35 * min))

        val second = halftime.startSecondHalf(40 * min)
        assertEquals(0, second.halfElapsedMs(40 * min))
        assertEquals(30 * min + 5_000, second.gameElapsedMs(40 * min + 5_000))
        assertEquals(Phase.FINISHED, second.endHalf(70 * min).phase)
    }

    @Test
    fun halftimeClockRunsFromEndOfFirstHalfOnly() {
        val halftime = MatchState().kickOff(0, profile).endHalf(26 * min)
        assertEquals(3 * min, halftime.halftimeElapsedMs(29 * min))
        assertEquals(0, halftime.startSecondHalf(31 * min).halftimeElapsedMs(32 * min))
        assertEquals(5 * min, profile.halftimeAlert.atMs)
    }

    @Test
    fun secondHalfEndDoesNotRestartHalftime() {
        val finished = MatchState().kickOff(0, profile).endHalf(30 * min)
            .startSecondHalf(35 * min).endHalf(65 * min)
        assertEquals(30 * min, finished.halftimeStartedAt)
        assertEquals(0, finished.halftimeElapsedMs(66 * min))
    }

    @Test
    fun fullTimeSummaryHasEachHalfAndTheActualHalftime() {
        val finished = MatchState().kickOff(0, profile).pause(10 * min).resume(11 * min).endHalf(32 * min)
            .startSecondHalf(38 * min).endHalf(69 * min)
        assertEquals(31 * min, finished.firstHalfMs) // 32 minutes minus the 1-minute pause
        assertEquals(6 * min, finished.halftimeMs)
        assertEquals(31 * min, finished.endedHalfMs)
    }

    @Test
    fun nextAlertSkipsPassedCheckpoints() {
        assertEquals("Quarter break", profile.nextAlert(0)?.label)
        assertTrue(profile.nextAlert(15 * min)!!.isHalfEnd)
        assertNull(profile.nextAlert(30 * min))
    }

    @Test
    fun matchPlaysTheProfileItKickedOffWith() {
        val longer = profile.copy(halfLengthMs = 40 * min)
        val second = MatchState(profileId = profile.id).kickOff(0, longer)
            .endHalf(40 * min).startSecondHalf(45 * min)
        assertEquals(longer, second.profile)
        // The first half counts at the kicked-off profile's length, whatever the stored one says now.
        assertEquals(40 * min + 5_000, second.gameElapsedMs(45 * min + 5_000))
        assertEquals(profile.id, second.newMatch().profileId)
    }

    @Test
    fun invalidTransitionsAreNoOps() {
        val idle = MatchState()
        assertEquals(idle, idle.pause(5).resume(5).endHalf(5).startSecondHalf(5))
        val running = idle.kickOff(0, profile)
        assertEquals(running, running.kickOff(10, Profiles.test).selectProfile("test"))
    }

    @Test
    fun formatting() {
        assertEquals("0:59", formatElapsed(59_999))
        assertEquals("62:05", formatElapsed(62 * min + 5_000))
        assertEquals("0:01", formatRemaining(1))
        assertEquals("0:00", formatRemaining(0))
        assertEquals("25m", formatLength(25 * min))
        assertEquals("30s", formatLength(30_000))
        assertEquals("27m 30s", formatLength(27 * min + 30_000))
    }
}
