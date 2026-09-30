package com.banketlux.data.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import java.util.concurrent.TimeUnit

/**
 * Zakazuje pozadinske backup-ove:
 *  - [scheduleAfterChange]: posle izmene podataka, sa odlaganjem i politikom
 *    REPLACE, pa se niz brzih izmena spoji u jedan backup.
 *  - [schedulePeriodic]: dnevni backup ako se dugo ništa ne menja.
 *
 * Oba traže mrežu. Worker sam odustane ako korisnik nije prijavljen
 * ili je automatski backup isključen.
 */
object BackupScheduler {

    private const val AFTER_CHANGE_WORK = "banketlux_cloud_backup_after_change"
    private const val PERIODIC_WORK = "banketlux_cloud_backup_periodic"
    private const val DEBOUNCE_DELAY_MINUTES = 10L
    private const val PERIODIC_INTERVAL_HOURS = 24L

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    fun scheduleAfterChange(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInitialDelay(DEBOUNCE_DELAY_MINUTES, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(AFTER_CHANGE_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            PERIODIC_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
