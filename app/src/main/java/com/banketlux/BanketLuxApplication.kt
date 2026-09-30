package com.banketlux

import android.app.Application
import androidx.room.Room
import com.banketlux.data.backup.BackupRepository
import com.banketlux.data.backup.BackupScheduler
import com.banketlux.data.backup.BackupUserPrefs
import com.banketlux.data.backup.GoogleDriveBackup
import com.banketlux.data.calendar.CalendarSyncScheduler
import com.banketlux.data.calendar.GoogleCalendarSync
import com.banketlux.data.local.BANKETLUX_MIGRATIONS
import com.banketlux.data.local.BanketLuxDatabase
import com.banketlux.data.local.StandardPriceListSeedCallback
import com.banketlux.data.repository.BookingRepository
import com.banketlux.data.repository.EarningsRepository
import com.banketlux.data.repository.EquipmentRepository
import com.banketlux.data.repository.SettingsRepository
import com.banketlux.notifications.BookingReminders

/**
 * Drži bazu i repozitorijume na nivou procesa. Potrebno je jer pozadinski
 * backup (WorkManager) radi i kad nijedan ekran nije otvoren.
 */
class BanketLuxApplication : Application() {

    val database: BanketLuxDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            BanketLuxDatabase::class.java,
            "banketlux.db"
        )
            .addMigrations(*BANKETLUX_MIGRATIONS)
            // Standardni cenovnik se unosi sam, pri prvom pokretanju nakon instalacije.
            .addCallback(StandardPriceListSeedCallback)
            .build()
    }

    val equipmentRepository: EquipmentRepository by lazy {
        EquipmentRepository(database.equipmentDao())
    }

    val bookingRepository: BookingRepository by lazy {
        BookingRepository(database.bookingDao())
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(database.businessSettingsDao())
    }

    val earningsRepository: EarningsRepository by lazy {
        EarningsRepository(
            database = database,
            bookingDao = database.bookingDao(),
            earningDao = database.earningDao()
        )
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            database = database,
            bookingDao = database.bookingDao(),
            equipmentDao = database.equipmentDao(),
            customerDao = database.customerDao(),
            businessSettingsDao = database.businessSettingsDao(),
            earningDao = database.earningDao(),
            // Podešavanja iz SharedPreferences ulaze u backup i primenjuju se pri vraćanju.
            readUserPrefs = {
                BackupUserPrefs(
                    autoBackupEnabled = googleDriveBackup.autoBackupEnabled.value,
                    calendarSyncEnabled = calendarSync.enabled.value
                )
            },
            applyUserPrefs = { prefs ->
                prefs.autoBackupEnabled?.let { googleDriveBackup.setAutoBackupEnabled(it) }
                prefs.calendarSyncEnabled?.let { enabled ->
                    calendarSync.setEnabled(enabled)
                    // Vraćena zakazivanja nose id događaja, pa naknadni upis ažurira, ne duplira.
                    if (enabled && calendarSync.hasCalendarPermission()) {
                        CalendarSyncScheduler.backfillAll(this)
                    }
                }
            }
        )
    }

    val googleDriveBackup: GoogleDriveBackup by lazy { GoogleDriveBackup(applicationContext) }

    val calendarSync: GoogleCalendarSync by lazy { GoogleCalendarSync(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        // Dnevni backup je "mreža za hvatanje" ako se dugo ništa ne menja.
        // Worker sam odustane ako korisnik nije prijavljen na Google nalog.
        BackupScheduler.schedulePeriodic(this)
        BookingReminders.createChannel(this)
        BookingReminders.schedulePeriodic(this)
        // Novi izgled događaja (samo ime, bez podsetnika) — postojeći se prepišu jednom.
        calendarSync.refreshEventsIfFormatChanged()
    }
}
