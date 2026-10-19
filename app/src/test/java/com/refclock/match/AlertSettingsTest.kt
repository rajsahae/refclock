package com.refclock.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertSettingsTest {
    @Test
    fun defaultsAreTheOriginalPatterns() {
        val s = AppSettings()
        assertEquals(Pattern.QUICK_4, s.styleFor(AlertKind.CHECKPOINT, half = 1).pattern)
        assertEquals(Pattern.LONG_2, s.styleFor(AlertKind.HALF_END, half = 1).pattern)
        assertEquals(Pattern.LONG_3, s.styleFor(AlertKind.HALF_END, half = 2).pattern)
        assertEquals(Pattern.SUSTAINED, s.styleFor(AlertKind.HALFTIME_END, half = 1).pattern)
        assertTrue(AppSettings().run { listOf(quarterBreak, halfTime, fullTime, halftimeOver) }.none { it.beep })
    }

    @Test
    fun pulsesFollowThePattern() {
        assertEquals(
            listOf(Pulse(0, 800), Pulse(1100, 150), Pulse(1450, 150)),
            AlertTiming.pulses(Pattern.LONG_QUICK),
        )
        assertEquals(emptyList<Pulse>(), AlertTiming.pulses(Pattern.OFF))
    }

    @Test
    fun repeatsAreSeparatedByTheGapAndCappedAtAboutAMinute() {
        val repeated = AlertTiming.repeatedPulses(Pattern.SUSTAINED)
        // 1.5 s buzz + 3 s gap: 4.5 s per repeat, 14 repeats inside the minute.
        assertEquals(14, repeated.size)
        assertEquals(Pulse(4500, 1500), repeated[1])
        val last = repeated.last()
        assertTrue(last.startMs + last.durationMs <= AlertTiming.CAP_MS)
        assertEquals(emptyList<Pulse>(), AlertTiming.repeatedPulses(Pattern.OFF))
    }

    @Test
    fun waveformAlternatesOffAndOn() {
        assertEquals(listOf(0L, 800, 300, 150, 200, 150), AlertTiming.waveform(AlertTiming.pulses(Pattern.LONG_QUICK)))
        assertEquals(listOf(0L, 1500, 3000, 1500), AlertTiming.waveform(AlertTiming.repeatedPulses(Pattern.SUSTAINED).take(2)))
    }

    @Test
    fun jsonRoundTripAndUnreadableInput() {
        val custom = AppSettings(quarterBreak = AlertStyle(Pattern.QUICK_2, beep = true), halftimeCountUp = true)
        assertEquals(custom, AppSettings.decode(AppSettings.encode(custom)))
        assertNull(AppSettings.decode("""{"quarterBreak":{"pattern":"FUTURE_PATTERN"}}"""))
        assertEquals(AppSettings(), AppSettings.decode("{}"))
    }
}
