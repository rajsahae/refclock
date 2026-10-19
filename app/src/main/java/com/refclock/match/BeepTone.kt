package com.refclock.match

import kotlin.math.PI
import kotlin.math.sin

/** The beep: one repeat of a pattern as 16-bit mono PCM, a tone during each pulse and silence otherwise. */
object BeepTone {
    const val SAMPLE_RATE = 22_050

    // Near a referee whistle's pitch, and well within what a watch speaker reproduces.
    private const val FREQUENCY_HZ = 2_800.0
    private const val AMPLITUDE = 0.7

    // Ramps each pulse in and out so it doesn't click.
    private const val FADE_MS = 5L

    fun cycle(pattern: Pattern): ShortArray {
        val samples = ShortArray(toSamples(AlertTiming.cycleMs(pattern)))
        val fade = toSamples(FADE_MS).toDouble()
        for (pulse in AlertTiming.pulses(pattern)) {
            val start = toSamples(pulse.startMs)
            val length = toSamples(pulse.durationMs)
            for (i in 0 until length) {
                val envelope = minOf(1.0, i / fade, (length - 1 - i) / fade)
                val n = start + i
                val value = sin(2 * PI * FREQUENCY_HZ * n / SAMPLE_RATE) * AMPLITUDE * envelope
                samples[n] = (value * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return samples
    }

    private fun toSamples(ms: Long) = (ms * SAMPLE_RATE / 1000).toInt()
}
