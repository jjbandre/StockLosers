package com.example.stocklosers

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val UNIQUE_NAME = "losers-poller"

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<LosersWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runOnceNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<LosersWorker>().build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
