package com.banketlux.notifications

import com.banketlux.data.local.entity.label
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.banketlux.BanketLuxApplication
import com.banketlux.MainActivity
import com.banketlux.R
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.domain.model.BookingStatus
import com.banketlux.ui.components.formatRsd
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

private const val TAG = "BookingReminder"
private const val CHANNEL_ID = "booking_reminders"
private const val PREFS_NAME = "banketlux_reminder_prefs"
private const val KEY_NOTIFIED = "notified"
private const val PERIODIC_WORK = "booking_reminder_periodic"
private const val CHECK_NOW_WORK = "booking_reminder_check_now"

/** Obaveštenje stiže dan pre početka najma, od ovog sata nadalje. */
internal const val REMINDER_HOUR = 12

/**
 * Da li zakazivanje treba sada da dobije podsetnik: najam počinje sutra, podne je prošlo,
 * nije otkazano i za taj datum podsetnik još nije poslat.
 */
internal fun needsReminder(
    booking: BookingWithLines,
    now: LocalDateTime,
    alreadyNotified: Set<String>
): Boolean {
    val entity = booking.booking
    if (entity.status == BookingStatus.CANCELLED) return false
    if (now.hour < REMINDER_HOUR) return false
    if (entity.rentalStartDate != now.toLocalDate().plusDays(1).toString()) return false
    return reminderKey(booking) !in alreadyNotified
}

/** Ključ uključuje datum: pomereno zakazivanje dobija nov podsetnik. */
internal fun reminderKey(booking: BookingWithLines): String =
    "${booking.booking.id}|${booking.booking.rentalStartDate}"

internal fun reminderText(booking: BookingWithLines): String {
    val entity = booking.booking
    return buildList {
        if (entity.location.isNotBlank()) add(entity.location)
        if (entity.customerPhone.isNotBlank()) add("Tel: ${entity.customerPhone}")
        if (booking.lines.isNotEmpty()) {
            add(booking.lines.joinToString { it.label() })
        }
        add("Ukupno: ${formatRsd(entity.totalPriceRsd)}")
    }.joinToString("\n")
}

object BookingReminders {

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Podsetnici za zakazivanja",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Obaveštenje dan pre početka najma" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Provera na svakih sat vremena. Dnevni posao u tačno vreme bi Android (Doze) umeo
     * da pomeri za sate i promaši dan; ovako podsetnik stiže u prvoj proveri posle podneva.
     */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<BookingReminderWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Odmah posle čuvanja: zakazivanje za sutra, uneto posle podneva, ne čeka sledeću proveru. */
    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BookingReminderWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CHECK_NOW_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

class BookingReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? BanketLuxApplication ?: return Result.success()
        if (!BookingReminders.canNotify(applicationContext)) {
            Log.d(TAG, "Obaveštenja nisu dozvoljena")
            return Result.success()
        }

        val now = LocalDateTime.now()
        val notified = prefs.getStringSet(KEY_NOTIFIED, emptySet()).orEmpty()
        val due = app.bookingRepository.getAllBookingsWithLines()
            .filter { needsReminder(it, now, notified) }
        if (due.isEmpty()) return Result.success()

        due.forEach { notify(it) }

        // Stari ključevi (najam je već počeo) više ne trebaju.
        val today = LocalDate.now().toString()
        val kept = notified.filter { it.substringAfter('|') >= today }.toSet()
        prefs.edit().putStringSet(KEY_NOTIFIED, kept + due.map(::reminderKey)).apply()
        return Result.success()
    }

    @Suppress("MissingPermission") // proverava se u canNotify()
    private fun notify(booking: BookingWithLines) {
        val entity = booking.booking
        val name = entity.customerName.trim().ifEmpty { "Zakazivanje" }
        val text = reminderText(booking)
        val open = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sutra: $name")
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(entity.id.toInt(), notification)
        Log.d(TAG, "Podsetnik poslat za zakazivanje ${entity.id}")
    }
}
