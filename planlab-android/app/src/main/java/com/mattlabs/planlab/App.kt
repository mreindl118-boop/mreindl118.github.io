package com.mattlabs.planlab

import android.app.Application
import com.mattlabs.planlab.data.Store
import com.mattlabs.planlab.data.SyncWorker
import com.mattlabs.planlab.notify.Alarms
import com.mattlabs.planlab.notify.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.channels(this)
        Alarms.scheduleAll(this)
        SyncWorker.schedulePeriodic(this)
    }
}
