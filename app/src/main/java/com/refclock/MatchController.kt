package com.refclock

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import com.refclock.match.Alert
import com.refclock.match.MatchState
import com.refclock.match.Phase
import com.refclock.match.Presets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single owner of match state, shared by the UI, the service and the alarm receiver. Main thread only. */
object MatchController {
    private lateinit var prefs: SharedPreferences
    private var bootCount = 0

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    private val _pendingAlert = MutableStateFlow<Alert?>(null)

    /** The alert that last fired and hasn't been tapped away yet. */
    val pendingAlert: StateFlow<Alert?> = _pendingAlert.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("match", Context.MODE_PRIVATE)
        bootCount = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
        _state.value = load()
    }

    fun selectPreset(context: Context, id: String) = update(context) { it.selectPreset(id) }
    fun kickOff(context: Context) = update(context) { it.kickOff(now()) }
    fun pause(context: Context) = update(context) { it.pause(now()) }
    fun resume(context: Context) = update(context) { it.resume(now()) }
    fun endHalf(context: Context) = update(context) { it.endHalf(now()) }
    fun startSecondHalf(context: Context) = update(context) { it.startSecondHalf(now()) }
    fun newMatch(context: Context) = update(context) { it.newMatch() }

    fun alertFired(context: Context, alert: Alert) {
        _pendingAlert.value = alert
        Haptics.buzzUntilAcknowledged(context, alert, _state.value.half)
    }

    fun acknowledge(context: Context) {
        if (_pendingAlert.value == null) return
        _pendingAlert.value = null
        Haptics.stop(context)
    }

    private fun now() = SystemClock.elapsedRealtime()

    private fun update(context: Context, transition: (MatchState) -> MatchState) {
        val next = transition(_state.value)
        if (next == _state.value) return
        acknowledge(context)
        _state.value = next
        save(next)
        val app = context.applicationContext
        AlertScheduler.schedule(app, next)
        if (next.isActive) MatchService.start(app)
    }

    private fun save(s: MatchState) = prefs.edit {
        putString("phase", s.phase.name)
        putString("presetId", s.presetId)
        putInt("half", s.half)
        putLong("halfStartedAt", s.halfStartedAt)
        putLong("pausedTotalMs", s.pausedTotalMs)
        putLong("pausedAt", s.pausedAt)
        putLong("endedHalfMs", s.endedHalfMs)
        putInt("bootCount", bootCount)
    }

    private fun load(): MatchState {
        val phase = prefs.getString("phase", null)
            ?.let { name -> Phase.entries.firstOrNull { it.name == name } }
            ?: return MatchState()
        val s = MatchState(
            phase = phase,
            presetId = Presets.byId(prefs.getString("presetId", null)).id,
            half = prefs.getInt("half", 1),
            halfStartedAt = prefs.getLong("halfStartedAt", 0),
            pausedTotalMs = prefs.getLong("pausedTotalMs", 0),
            pausedAt = prefs.getLong("pausedAt", 0),
            endedHalfMs = prefs.getLong("endedHalfMs", 0),
        )
        // elapsedRealtime() restarts at boot, so a half that was running before a reboot can't be recovered.
        // BOOT_COUNT can read 0 on some builds; a start time in the future is a reboot regardless.
        val rebooted = prefs.getInt("bootCount", bootCount) != bootCount ||
            s.halfStartedAt > SystemClock.elapsedRealtime()
        return if (rebooted && (phase == Phase.RUNNING || phase == Phase.PAUSED)) s.newMatch() else s
    }
}
