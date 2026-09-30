package com.banketlux.data.calendar

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.banketlux.BanketLuxApplication
import com.banketlux.data.backup.BackupScheduler
import java.util.concurrent.TimeUnit

private const val TAG = "CalendarSyncWorker"
internal const val KEY_BOOKING_ID = "bookingId"
internal const val KEY_EVENT_ID = "eventId"

/**
 * Posle ovoliko neuspelih pokušaja posao odustaje. Prolazni problemi (mreža) se
 * reše mnogo ranije; trajni (npr. ukinuta dozvola) ne treba da se ponavljaju doveka.
 */
private const val MAX_RETRY_ATTEMPTS = 7

private fun CoroutineWorker.retryOrGiveUp(reason: String): androidx.work.ListenableWorker.Result =
    if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
        Log.w(TAG, "Odustajem posle ${runAttemptCount + 1} pokušaja: $reason")
        androidx.work.ListenableWorker.Result.failure()
    } else {
        androidx.work.ListenableWorker.Result.retry()
    }

/**
 * Upisuje jedno zakazivanje u Google kalendar. Radi kroz WorkManager da bi
 * preživelo zatvaranje ekrana i da bi se samo ponovilo kad se vrati mreža.
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BanketLuxApplication ?: return Result.success()
        val calendar = app.calendarSync
        calendar.notReadyReason()?.let { reason ->
            Log.d(TAG, "Preskačem: $reason")
            // Isključen prekidač je izbor korisnika, ne greška.
            if (calendar.enabled.value) calendar.recordStatus(false, reason)
            return Result.success()
        }

        val bookingId = inputData.getLong(KEY_BOOKING_ID, -1L)
        if (bookingId <= 0) return Result.success()

        // Čitanje, upis i čuvanje id-a idu pod istim zaključavanjem kao naknadni upis,
        // da dva posla ne vide prazan id istog zakazivanja i ne naprave dupli događaj.
        return calendar.withWriteLock {
            val booking = app.bookingRepository.getBookingById(bookingId)
            if (booking == null) {
                Log.d(TAG, "Zakazivanje $bookingId više ne postoji")
                return@withWriteLock Result.success()
            }

            val outcome = calendar.upsertEvent(booking, booking.booking.calendarEventId)
            outcome.fold(
                onSuccess = { eventId ->
                    if (app.bookingRepository.getBookingById(bookingId) == null) {
                        // Obrisano dok je upis bio u toku — ne ostavljaj događaj bez vlasnika.
                        CalendarSyncScheduler.deleteEvent(applicationContext, eventId)
                        return@fold Result.success()
                    }
                    app.bookingRepository.updateCalendarEventId(bookingId, eventId)
                    // Id događaja mora da stigne i u backup — inače bi posle vraćanja sledeća
                    // izmena napravila DUPLI događaj u kalendaru.
                    BackupScheduler.scheduleAfterChange(applicationContext)
                    Log.d(TAG, "Zakazivanje $bookingId upisano u kalendar kao $eventId")
                    calendar.recordStatus(true, "Upisano: ${buildEventSummary(booking)}")
                    Result.success()
                },
                onFailure = {
                    Log.w(TAG, "Upis u kalendar nije uspeo: ${it.message}")
                    calendar.recordStatus(false, it.message ?: "Upis u kalendar nije uspeo.")
                    retryOrGiveUp("upis zakazivanja $bookingId u kalendar")
                }
            )
        }
    }
}

/**
 * Prolazi kroz sva postojeća zakazivanja i upisuje ih u kalendar. Pokreće se kad
 * se sinhronizacija uključi, da zakazivanja napravljena ranije ne ostanu izvan kalendara.
 * Zakazivanja koja već imaju događaj se ažuriraju, ne dupliraju.
 */
class CalendarBackfillWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BanketLuxApplication ?: return Result.success()
        val calendar = app.calendarSync
        calendar.notReadyReason()?.let { reason ->
            if (calendar.enabled.value) calendar.recordStatus(false, reason)
            return Result.success()
        }

        val bookings = app.bookingRepository.getAllBookingsWithLines()
        var failed = 0
        var lastError: String? = null
        bookings.forEach { listed ->
            // Pod zaključavanjem se zakazivanje čita iznova: pojedinačni upis je možda u
            // međuvremenu napravio događaj (novi id), a zakazivanje je možda i obrisano.
            calendar.withWriteLock {
                val booking = app.bookingRepository.getBookingById(listed.booking.id)
                    ?: return@withWriteLock
                val outcome = calendar.upsertEvent(booking, booking.booking.calendarEventId)
                outcome.fold(
                    onSuccess = { eventId ->
                        if (app.bookingRepository.getBookingById(booking.booking.id) == null) {
                            CalendarSyncScheduler.deleteEvent(applicationContext, eventId)
                        } else {
                            app.bookingRepository.updateCalendarEventId(booking.booking.id, eventId)
                        }
                    },
                    onFailure = {
                        failed++
                        lastError = it.message
                    }
                )
            }
        }

        Log.d(TAG, "Naknadni upis: ${bookings.size - failed}/${bookings.size} zakazivanja")
        calendar.recordStatus(
            failed == 0,
            if (failed == 0) {
                "Upisano ${bookings.size} zakazivanja."
            } else {
                "Upisano ${bookings.size - failed}/${bookings.size}. Greška: $lastError"
            }
        )
        if (bookings.size > failed) BackupScheduler.scheduleAfterChange(applicationContext)
        // Neuspeli se hvataju u sledećem pokušaju; već upisani se neće duplirati.
        return if (failed > 0) retryOrGiveUp("naknadni upis u kalendar") else Result.success()
    }
}

/** Briše događaj iz kalendara kad se zakazivanje obriše iz aplikacije. */
class CalendarDeleteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BanketLuxApplication ?: return Result.success()
        val calendar = app.calendarSync
        // Dovoljna je dozvola za kalendar, ne i uključen prekidač: događaj obrisanog
        // zakazivanja ne sme da ostane u kalendaru samo zato što je upis u međuvremenu
        // isključen. Bez dozvole brisanje ionako nije moguće.
        if (!calendar.hasCalendarPermission()) return Result.success()

        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.success()
        val outcome = calendar.deleteEvent(eventId)
        return if (outcome.isSuccess) {
            calendar.recordStatus(true, "Obrisan događaj iz kalendara.")
            Result.success()
        } else {
            calendar.recordStatus(
                false,
                "Brisanje iz kalendara nije uspelo: ${outcome.exceptionOrNull()?.message}"
            )
            retryOrGiveUp("brisanje događaja $eventId iz kalendara")
        }
    }
}

object CalendarSyncScheduler {

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    /** Jedinstven posao po zakazivanju: brze uzastopne izmene se spoje u jedan upis. */
    fun syncBooking(context: Context, bookingId: Long) {
        val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
            .setInputData(Data.Builder().putLong(KEY_BOOKING_ID, bookingId).build())
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context)
            // APPEND_OR_REPLACE, ne REPLACE: REPLACE prekida upis koji je u toku, pa događaj
            // napravljen u Google-u ostane bez sačuvanog id-a i sledeći upis ga duplira.
            // Posao čita zakazivanje tek kad se pokrene, pa uvek upiše najnovije stanje.
            .enqueueUniqueWork("calendar_sync_$bookingId", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /**
     * Naknadni upis svih postojećih zakazivanja; KEEP da se ne pokrene dvaput uporedo.
     * [replace] za ručno dugme: KEEP bi zadržao posao koji posle greške čeka ponavljanje
     * (eksponencijalno, i do sat vremena), pa dugme ne bi radilo ništa.
     */
    fun backfillAll(context: Context, replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<CalendarBackfillWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "calendar_backfill",
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
    }

    /**
     * Zakazivanje je obrisano: otkaži upis koji možda još čeka, pa obriši događaj.
     * Bez otkazivanja bi upis na čekanju (npr. bez mreže) našao obrisano zakazivanje
     * tek posle — ili, ako je već u toku, napravio događaj koji niko više ne briše.
     */
    fun bookingDeleted(context: Context, bookingId: Long, eventId: String?) {
        WorkManager.getInstance(context).cancelUniqueWork("calendar_sync_$bookingId")
        eventId?.let { deleteEvent(context, it) }
    }

    fun deleteEvent(context: Context, eventId: String) {
        val request = OneTimeWorkRequestBuilder<CalendarDeleteWorker>()
            .setInputData(Data.Builder().putString(KEY_EVENT_ID, eventId).build())
            .setConstraints(networkConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("calendar_delete_$eventId", ExistingWorkPolicy.KEEP, request)
    }
}
