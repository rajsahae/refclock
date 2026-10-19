package com.refclock

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.refclock.match.AlertStyle
import com.refclock.match.AlertTiming

/** Plays an alert's pattern, repeating until it's acknowledged or the cap runs out. */
object AlertPlayer {
    fun play(context: Context, style: AlertStyle) {
        val pulses = AlertTiming.repeatedPulses(style.pattern)
        if (pulses.isEmpty()) return
        val timings = AlertTiming.waveform(pulses).toLongArray()
        val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 1) 255 else 0 }
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        val vibrator = vibrator(context)
        // Alarm usage: the system may silence lower-priority vibrations from an app that isn't on screen.
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    fun stop(context: Context) = vibrator(context).cancel()

    // Always via the application context: cancel() only stops vibrations started through the same
    // service instance, and the receiver and the activity each get their own otherwise.
    private fun vibrator(context: Context): Vibrator {
        val app = context.applicationContext
        return if (Build.VERSION.SDK_INT >= 31) {
            app.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            app.getSystemService(Vibrator::class.java)
        }
    }
}
