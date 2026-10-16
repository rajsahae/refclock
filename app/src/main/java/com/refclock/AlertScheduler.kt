package com.refclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.refclock.match.MatchState
import com.refclock.match.Phase
import com.refclock.match.Presets

/**
 * Checkpoint alerts are system alarms rather than in-process timers so they fire on time
 * with the screen off and the CPU asleep.
 */
object AlertScheduler {
    // Request codes 0 until MAX_ALERTS are cancelled blindly, so nothing needs to track what's pending.
    private const val MAX_ALERTS = 16

    fun schedule(context: Context, state: MatchState) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX_ALERTS) {
            PendingIntent.getBroadcast(
                context, i, Intent(context, AlertReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
        if (state.phase != Phase.RUNNING) return

        val elapsed = state.halfElapsedMs(SystemClock.elapsedRealtime())
        Presets.byId(state.presetId).alerts.take(MAX_ALERTS).forEachIndexed { i, alert ->
            if (alert.atMs <= elapsed) return@forEachIndexed
            val intent = Intent(context, AlertReceiver::class.java)
                .putExtra(AlertReceiver.EXTRA_HALF, state.half)
                .putExtra(AlertReceiver.EXTRA_ALERT, i)
            val pending = PendingIntent.getBroadcast(
                context, i, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val at = state.clockZero + alert.atMs
            if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
            }
        }
    }
}
