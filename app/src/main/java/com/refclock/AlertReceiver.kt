package com.refclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.refclock.match.Alert
import com.refclock.match.AlertKind
import com.refclock.match.Phase
import com.refclock.match.Presets

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = MatchController.state.value
        val preset = Presets.byId(state.presetId)
        val index = intent.getIntExtra(EXTRA_ALERT, -1)
        val now = SystemClock.elapsedRealtime()
        // Drops an alarm that was already in flight when its phase ended, including one that lands
        // just after a quick pause/resume, before its checkpoint is due again.
        val (alert, elapsed) = when {
            index == HALFTIME_ALERT && state.phase == Phase.HALFTIME ->
                preset.halftimeAlert to state.halftimeElapsedMs(now)
            state.phase == Phase.RUNNING && state.half == intent.getIntExtra(EXTRA_HALF, 0) ->
                (preset.alerts.getOrNull(index) ?: return) to state.halfElapsedMs(now)
            else -> return
        }
        if (elapsed < alert.atMs - DUE_SLACK_MS) return
        MatchController.alertFired(context, alert)
    }

    companion object {
        const val EXTRA_HALF = "half"
        const val EXTRA_ALERT = "alert"
        const val HALFTIME_ALERT = -2
        private const val DUE_SLACK_MS = 1000L
    }
}

object Haptics {
    private val CHECKPOINT = longArrayOf(0, 150, 250, 150, 250, 150, 250, 150)

    // Long pulses, like the referee's whistle: two at half time, three at full time.
    private val HALF_TIME = longArrayOf(0, 800, 300, 800)
    private val FULL_TIME = longArrayOf(0, 800, 300, 800, 300, 800)

    // One sustained pulse, unlike any whistle pattern.
    private val HALFTIME_OVER = longArrayOf(0, 1500)

    // Repeats until acknowledged, but capped at about a minute so an unnoticed alert can't buzz all half.
    private const val GAP_MS = 3000L
    private const val REPEATS = 12

    fun buzzUntilAcknowledged(context: Context, alert: Alert, half: Int) {
        val pattern = when (alert.kind) {
            AlertKind.CHECKPOINT -> CHECKPOINT
            AlertKind.HALF_END -> if (half == 1) HALF_TIME else FULL_TIME
            AlertKind.HALFTIME_END -> HALFTIME_OVER
        }
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
