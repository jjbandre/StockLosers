package com.example.stocklosers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class LosersWorker(
    private val appCtx: Context,
    params: WorkerParameters
) : CoroutineWorker(appCtx, params) {

    private val settings by lazy { SettingsRepository(appCtx) }
    val api: MarketApi = YahooMarketApi()

    private val history by lazy { AlertHistory(appCtx) }

    override suspend fun doWork(): Result {
        val threshold = settings.alertDropPercent.first()
        val losers = api.getDayLosers(limit = 100)
// ...
        // In LosersWorker.kt
        // ...
        val hits = losers
            .filter { it.percentChange <= -threshold }
            .filter { !history.hasBeenNotified(it.symbol, LocalDate.now()) } // <<< CORRECTED CALL
        // ...// ...
        if (hits.isNotEmpty()) {
            Notifier.notifyLosers(appCtx, threshold, hits)
            history.markNotified(hits.map { it.symbol }, LocalDate.now())
        }
        return Result.success()
    }
}
