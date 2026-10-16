package com.refclock

import android.app.Application

class RefClockApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MatchController.init(this)
    }
}
