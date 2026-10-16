package com.refclock.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchStateTest {
    private val preset = Presets.byId("ayso-12u")
    private val min = 60_000L

    @Test
    fun clockRunsFromKickOff() {
        val s = MatchState(presetId = preset.id).kickOff(now = 1_000)
        assertEquals(90_000, s.halfElapsedMs(91_000))
    }

    @Test
    fun pausedTimeIsExcluded() {
        val s = MatchState().kickOff(0).pause(10_000)
        assertEquals(10_000, s.halfElapsedMs(20_000))
        assertEquals(15_000, s.resume(25_000).halfElapsedMs(30_000))
    }

    @Test
    fun clockKeepsRunningPastHalfLength() {
        val s = MatchState(presetId = preset.id).kickOff(0)
        assertEquals(31 * min, s.halfElapsedMs(31 * min))
    }

    @Test
    fun secondHalfRestartsAndGameCountsScheduledFirstHalf() {
        val halftime = MatchState(presetId = preset.id).kickOff(0).endHalf(31 * min)
        assertEquals(Phase.HALFTIME, halftime.phase)
        assertEquals(31 * min, halftime.halfElapsedMs(35 * min))

        val second = halftime.startSecondHalf(40 * min)
        assertEquals(0, second.halfElapsedMs(40 * min))
        assertEquals(30 * min + 5_000, second.gameElapsedMs(preset, 40 * min + 5_000))
        assertEquals(Phase.FINISHED, second.endHalf(70 * min).phase)
    }

    @Test
    fun nextAlertSkipsPassedCheckpoints() {
        assertEquals("Quarter break", preset.nextAlert(0)?.label)
        assertTrue(preset.nextAlert(15 * min)!!.isHalfEnd)
        assertNull(preset.nextAlert(30 * min))
    }

    @Test
    fun invalidTransitionsAreNoOps() {
        val idle = MatchState()
        assertEquals(idle, idle.pause(5).resume(5).endHalf(5).startSecondHalf(5))
        val running = idle.kickOff(0)
        assertEquals(running, running.kickOff(10).selectPreset("test"))
    }

    @Test
    fun formatting() {
        assertEquals("0:59", formatElapsed(59_999))
        assertEquals("62:05", formatElapsed(62 * min + 5_000))
        assertEquals("0:01", formatRemaining(1))
        assertEquals("0:00", formatRemaining(0))
    }
}
