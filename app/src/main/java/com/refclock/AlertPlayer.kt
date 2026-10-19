package com.refclock

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.refclock.match.AlertStyle
import com.refclock.match.AlertTiming
import com.refclock.match.BeepTone

/** Plays an alert's pattern, repeating until it's acknowledged or the cap runs out. Main thread only. */
object AlertPlayer {
    private var beep: AudioTrack? = null

    fun play(context: Context, style: AlertStyle, capMs: Long = AlertTiming.CAP_MS) {
        stop(context)
        if (style.vibrate) vibrate(context, AlertTiming.waveform(AlertTiming.repeatedPulses(style.pattern, capMs)))
        if (style.beep) beep = startBeep(BeepTone.cycle(style.pattern), AlertTiming.repeats(style.pattern, capMs))
    }

    fun stop(context: Context) {
        vibrator(context).cancel()
        beep?.let {
            runCatching { it.stop() }
            it.release()
        }
        beep = null
    }

    private fun vibrate(context: Context, waveform: List<Long>) {
        val timings = waveform.toLongArray()
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

    /**
     * One repeat is loaded once and looped by the audio system, so the beeps stay in step with the
     * vibration without the app having to keep the CPU awake. Alarm usage: it follows the alarm volume
     * and still sounds in silent mode.
     */
    private fun startBeep(cycle: ShortArray, repeats: Int): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(BeepTone.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(cycle.size * 2)
            .build()
        track.write(cycle, 0, cycle.size)
        if (repeats > 1) track.setLoopPoints(0, cycle.size, repeats - 1)
        track.play()
        return track
    }

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
