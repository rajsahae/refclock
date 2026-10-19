package com.refclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.refclock.match.Phase

class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = MatchController.state.value
        val profile = state.profile
        val index = intent.getIntExtra(EXTRA_ALERT, -1)
        val now = SystemClock.elapsedRealtime()
        // Drops an alarm that was already in flight when its phase ended, including one that lands
        // just after a quick pause/resume, before its checkpoint is due again.
        val (alert, elapsed) = when {
            index == HALFTIME_ALERT && state.phase == Phase.HALFTIME ->
                profile.halftimeAlert to state.halftimeElapsedMs(now)
            state.phase == Phase.RUNNING && state.half == intent.getIntExtra(EXTRA_HALF, 0) ->
                (profile.alerts.getOrNull(index) ?: return) to state.halfElapsedMs(now)
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
