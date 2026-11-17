package com.shigu.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shigu.ShiguApp
import com.shigu.core.SyncMode
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class DailySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ShiguApp ?: return Result.failure()
        if (!app.settings.autoUpdate) return Result.success()
        app.strategies.ensureBuiltins()
        val strategies = app.strategies.list()
        val universe = if (strategies.any { it.universeSize > 800 }) {
            com.shigu.core.Universe.TOP_1500
        } else {
            app.settings.dailyUniverse
        }
        val result = app.syncManager.sync(SyncMode.DAILY, universe)
        if (result.isSuccess) {
            val refresh = app.paper.refreshAll(strategies)
            PaperNotifier.notify(app, refresh.changes)
        }
        return if (result.isSuccess) Result.success() else Result.retry()
    }
}

object SyncScheduler {
    private const val WORK = "shigu-daily-sync"

    fun schedule(context: Context, settings: Settings) {
        val wm = WorkManager.getInstance(context)
        if (!settings.autoUpdate) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(16, 10)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = PeriodicWorkRequestBuilder<DailySyncWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
