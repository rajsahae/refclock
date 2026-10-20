package com.refclock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.wear.ambient.AmbientLifecycleObserver
import com.refclock.ui.RefClockApp

class MainActivity : ComponentActivity() {
    private var ambient by mutableStateOf(false)
    private var ambientTick by mutableIntStateOf(0)

    // Being ambient-aware is what makes the watch return to this screen on wrist raise
    // instead of falling back to the watch face.
    private val ambientObserver = AmbientLifecycleObserver(
        this,
        object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambient = true
            }

            override fun onExitAmbient() {
                ambient = false
            }

            override fun onUpdateAmbient() {
                ambientTick++
            }
        },
    )

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(ambientObserver)

        // The watch-face match indicator is a notification; without this permission it never shows.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { RefClockApp(ambient = ambient, ambientTick = ambientTick) }
    }

    override fun onStart() {
        super.onStart()
        // Brings back the service, and with it the watch-face indicator, if a match outlived it, such as
        // after a reboot at halftime. Starting it from the app on screen is always allowed.
        if (MatchController.state.value.isActive) MatchService.start(this)
    }
}
