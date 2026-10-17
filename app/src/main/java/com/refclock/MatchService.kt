package com.refclock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.refclock.match.MatchState
import com.refclock.match.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs from kickoff to full time. Its Ongoing Activity puts a match indicator on the watch face
 * that reopens the app, and keeps the process alive between alerts.
 */
class MatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(getString(R.string.channel_match))
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must happen on every start, even if the match just ended, or startForegroundService() crashes the app.
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(MatchController.state.value), type)

        if (watcher == null) {
            watcher = scope.launch {
                MatchController.state.collect { state ->
                    if (state.isActive) {
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, buildNotification(state))
                    } else {
                        ServiceCompat.stopForeground(this@MatchService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(state: MatchState): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val halfLabel = getString(if (state.half == 1) R.string.status_first_half else R.string.status_second_half)
        val text = when (state.phase) {
            Phase.RUNNING -> halfLabel
            Phase.PAUSED -> getString(R.string.status_paused, halfLabel)
            else -> getString(R.string.halftime_short)
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)

        val status = when (state.phase) {
            Phase.RUNNING -> Status.Builder()
                .addTemplate("#half# #clock#")
                .addPart("half", Status.TextPart(halfLabel))
                .addPart("clock", Status.StopwatchPart(state.clockZero))
                .build()
            Phase.HALFTIME -> Status.Builder()
                .addTemplate(getString(R.string.halftime_short) + " #clock#")
                .addPart(
                    "clock",
                    Status.TimerPart(state.halftimeStartedAt + state.profile.halftimeLengthMs),
                )
                .build()
            else -> Status.Builder().addTemplate(text).build()
        }
        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_stat_clock)
            .setTouchIntent(openApp)
            .setStatus(status)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "match"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MatchService::class.java))
        }
    }
}
