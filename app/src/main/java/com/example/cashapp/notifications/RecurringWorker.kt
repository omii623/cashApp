package com.example.cashapp.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.cashapp.CashApplication
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Napi háttérfeladat: létrehozza az esedékes ismétlődő tételeket, és ha van jóváhagyandó,
 * értesítést küld (ha a felhasználó nem kapcsolta ki).
 */
class RecurringWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CashApplication).container
        val created = container.recurringRepository.generateDue()
        val pending = container.recurringRepository.observePendingCount().first()
        // Csak akkor jelzünk, ha jött új tétel – így nem kap minden nap ugyanarról értesítést.
        if (created > 0 && container.settingsRepository.recurringNotifications.first()) {
            RecurringNotifications.showPending(applicationContext, pending)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "recurring_daily"

        /** Az értesítés kb. ebben az időpontban érkezik (a rendszer pár percet csúsztathat). */
        private val NOTIFY_AT: LocalTime = LocalTime.of(8, 0)

        /** Naponta egyszer fut; többszöri hívás nem hoz létre újabb feladatot. */
        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var nextRun = now.toLocalDate().atTime(NOTIFY_AT)
            if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
            val request = PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, nextRun).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
