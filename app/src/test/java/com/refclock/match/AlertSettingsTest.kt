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
        val styles = AppSettings().run { listOf(quarterBreak, halfTime, fullTime, halftimeOver) }
        assertTrue(styles.all { it.vibrate && !it.beep })
    }

    @Test
    fun pulsesFollowThePattern() {
        assertEquals(
            listOf(Pulse(0, 800), Pulse(1100, 150), Pulse(1450, 150)),
            AlertTiming.pulses(Pattern.LONG_QUICK),
        )
    }

    @Test
    fun repeatsAreSeparatedByTheGapAndCappedAtAboutAMinute() {
        val repeated = AlertTiming.repeatedPulses(Pattern.SUSTAINED)
        // 1.5 s buzz + 3 s gap: 4.5 s per repeat, 14 repeats inside the minute.
        assertEquals(14, repeated.size)
        assertEquals(Pulse(4500, 1500), repeated[1])
        val last = repeated.last()
        assertTrue(last.startMs + last.durationMs <= AlertTiming.CAP_MS)
        assertEquals(14, AlertTiming.repeats(Pattern.SUSTAINED))
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

    @Test
    fun beepIsToneDuringPulsesAndSilenceBetween() {
        val samples = BeepTone.cycle(Pattern.LONG_QUICK)
        val perMs = BeepTone.SAMPLE_RATE / 1000.0
        fun at(ms: Int) = samples[(ms * perMs).toInt()].toInt()
        fun loudest(fromMs: Int, toMs: Int) = (((fromMs * perMs).toInt())..((toMs * perMs).toInt())).maxOf { kotlin.math.abs(samples[it].toInt()) }

        assertEquals((AlertTiming.cycleMs(Pattern.LONG_QUICK) * perMs).toInt(), samples.size)
        assertEquals(0, at(0)) // faded in, so the first pulse doesn't click
        assertTrue(loudest(100, 200) > 10_000) // inside the first, long pulse
        assertEquals(0, loudest(810, 1090)) // the 300 ms pause after it
        assertTrue(loudest(1150, 1220) > 10_000) // the first quick pulse
        assertEquals(0, loudest(1610, 4590)) // the gap before the next repeat
    }

    @Test
    fun eachSlotIsSetIndependently() {
        val beepy = AlertStyle(Pattern.QUICK_2, vibrate = false, beep = true)
        val s = AppSettings().withStyle(AlertSlot.FULL_TIME, beepy)
        assertEquals(beepy, s.style(AlertSlot.FULL_TIME))
        assertEquals(beepy, s.styleFor(AlertKind.HALF_END, half = 2))
        assertEquals(AppSettings().halfTime, s.styleFor(AlertKind.HALF_END, half = 1))
        AlertSlot.entries.filter { it != AlertSlot.FULL_TIME }.forEach {
            assertEquals(AppSettings().style(it), s.style(it))
        }
    }
}
