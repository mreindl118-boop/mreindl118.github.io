package com.mattlabs.planlab.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Pushes taps (one-off) or does a full refresh (every 2 hours: plan, log, Health Connect, widgets). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Store.init(applicationContext)
        val full = inputData.getBoolean("full", false)
        Store.refresh(full = full)
        if (full) runCatching { com.mattlabs.planlab.update.AutoUpdate.check(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedulePeriodic(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
                .setInputData(workDataOf("full" to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
