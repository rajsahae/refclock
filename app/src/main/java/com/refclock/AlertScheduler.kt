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
 * Alerts are system alarms rather than in-process timers so they fire on time
 * with the screen off and the CPU asleep.
 */
object AlertScheduler {
    // Request codes 0..MAX_ALERTS are cancelled blindly, so nothing needs to track what's pending.
    // Half alerts use 0 until MAX_ALERTS; the halftime alert uses MAX_ALERTS itself.
    private const val MAX_ALERTS = 16
    private const val HALFTIME_REQUEST = MAX_ALERTS

    fun schedule(context: Context, state: MatchState) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        for (i in 0..MAX_ALERTS) {
            PendingIntent.getBroadcast(
                context, i, Intent(context, AlertReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }

        val preset = Presets.byId(state.presetId)
        val now = SystemClock.elapsedRealtime()
        when (state.phase) {
            Phase.RUNNING -> {
                val elapsed = state.halfElapsedMs(now)
                preset.alerts.take(MAX_ALERTS).forEachIndexed { i, alert ->
                    if (alert.atMs > elapsed) {
                        set(context, alarms, i, state.clockZero + alert.atMs, state.half, alertIndex = i)
                    }
                }
            }
            Phase.HALFTIME -> {
                if (preset.halftimeAlert.atMs > state.halftimeElapsedMs(now)) {
                    set(
                        context, alarms, HALFTIME_REQUEST, state.halftimeStartedAt + preset.halftimeAlert.atMs,
                        state.half, alertIndex = AlertReceiver.HALFTIME_ALERT,
                    )
                }
            }
            else -> Unit
        }
    }

    private fun set(context: Context, alarms: AlarmManager, request: Int, at: Long, half: Int, alertIndex: Int) {
        val intent = Intent(context, AlertReceiver::class.java)
            .putExtra(AlertReceiver.EXTRA_HALF, half)
            .putExtra(AlertReceiver.EXTRA_ALERT, alertIndex)
        val pending = PendingIntent.getBroadcast(
            context, request, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending)
        }
    }
}
