package com.refclock

import android.app.Application

class RefClockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProfileStore.init(this)
        MatchController.init(this)
    }
}
