package com.refclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.refclock.match.Alert
import com.refclock.match.Phase
import com.refclock.match.Presets

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = MatchController.state.value
        // Drops an alarm that was already in flight when the half was paused or ended.
        if (state.phase != Phase.RUNNING || state.half != intent.getIntExtra(EXTRA_HALF, 0)) return
        val alert = Presets.byId(state.presetId).alerts.getOrNull(intent.getIntExtra(EXTRA_ALERT, -1)) ?: return
        MatchController.alertFired(context, alert)
    }

    companion object {
        const val EXTRA_HALF = "half"
        const val EXTRA_ALERT = "alert"
    }
}

object Haptics {
    private val CHECKPOINT = longArrayOf(0, 500, 250, 500, 250, 500)

    // Long-then-short so it can't be mistaken for a checkpoint while running.
    private val HALF_END = longArrayOf(0, 1200, 300, 200, 150, 200, 150, 200)

    // Repeats until acknowledged, but capped at about a minute so an unnoticed alert can't buzz all half.
    private const val GAP_MS = 3000L
    private const val REPEATS = 12

    fun buzzUntilAcknowledged(context: Context, alert: Alert) {
        val pattern = if (alert.isHalfEnd) HALF_END else CHECKPOINT
        // Each repeat starts with an "off" segment, so even indices stay off and odd ones on.
        val timings = (0 until REPEATS).flatMap { i ->
            listOf(if (i == 0) 0L else GAP_MS) + pattern.drop(1)
        }.toLongArray()
        val amplitudes = IntArray(timings.size) { i -> if (i % 2 == 1) 255 else 0 }
        val vibrator = vibrator(context)
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
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
