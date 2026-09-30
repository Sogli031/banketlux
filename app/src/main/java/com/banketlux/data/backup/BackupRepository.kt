package com.banketlux.data.backup

import androidx.room.withTransaction
import com.banketlux.data.local.BanketLuxDatabase
import com.banketlux.data.local.dao.BookingDao
import com.banketlux.data.local.dao.BusinessSettingsDao
import com.banketlux.data.local.dao.CustomerDao
import com.banketlux.data.local.dao.EarningDao
import com.banketlux.data.local.dao.EquipmentDao
import kotlinx.coroutines.CancellationException

class BackupRepository(
    private val database: BanketLuxDatabase,
    private val bookingDao: BookingDao,
    private val equipmentDao: EquipmentDao,
    private val customerDao: CustomerDao,
    private val businessSettingsDao: BusinessSettingsDao,
    private val earningDao: EarningDao,
    /** Podešavanja koja žive u SharedPreferences (auto-backup, upis u kalendar). */
    private val readUserPrefs: () -> BackupUserPrefs = { BackupUserPrefs(null, null) },
    private val applyUserPrefs: (BackupUserPrefs) -> Unit = {}
) {
    /** Ceo sadržaj baze kao JSON tekst — koristi ga i lokalni izvoz i Drive backup. */
    suspend fun exportToJson(): String = BackupMapper.encode(createBackupSnapshot())

    /** JSON + brojevi zapisa koji se upisuju uz verziju u oblaku (za izbor starije verzije). */
    suspend fun exportForUpload(): BackupUpload {
        val backup = createBackupSnapshot()
        return BackupUpload(json = BackupMapper.encode(backup), counts = BackupMapper.counts(backup))
    }

    /** Ima li lokalno zakazivanja, zarade ili kupaca (sam cenovnik se ne računa). */
    suspend fun hasUserRecords(): Boolean = BackupMapper.hasUserRecords(createBackupSnapshot())

    /**
     * Da li je backup "prazan" (bez zakazivanja, zarade i kupaca). Baca [BackupImportException]
     * ako fajl ne može da se pročita.
     */
    fun backupHasUserRecords(payload: String): Boolean = BackupMapper.hasUserRecords(BackupMapper.decode(payload))

    /**
     * Uvoz iz JSON teksta — put kojim se vraćaju podaci sa Google Drive-a. Sve-ili-ništa:
     * prvo se dekodira, proverava i mapira CEO sadržaj, a tek onda se u JEDNOJ transakciji
     * zamenjuju tabele i AUTOINCREMENT brojači. Greška nikad ne ostavlja polovično stanje.
     */
    suspend fun importFromJson(payload: String): BackupValidationResult {
        val (decoded, tables) = try {
            val backup = BackupMapper.decode(payload)
            backup to BackupMapper.toTables(backup)
        } catch (e: BackupImportException) {
            return BackupValidationResult(false, e.message)
        }

        try {
            database.withTransaction {
                bookingDao.deleteAllBookingLines()
                bookingDao.deleteAllBookings()
                equipmentDao.deleteAll()
                customerDao.deleteAll()
                earningDao.deleteAll()
                businessSettingsDao.deleteAll()

                if (tables.equipment.isNotEmpty()) equipmentDao.insertAll(tables.equipment)
                if (tables.customers.isNotEmpty()) customerDao.insertAll(tables.customers)
                if (tables.bookings.isNotEmpty()) bookingDao.insertBookings(tables.bookings)
                if (tables.bookingLines.isNotEmpty()) bookingDao.insertBookingLines(tables.bookingLines)
                if (tables.earnings.isNotEmpty()) earningDao.insertAll(tables.earnings)
                tables.settings?.let { businessSettingsDao.upsert(it) }

                writeSequences(tables.sequences)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BackupValidationResult(
                false,
                "Upis u bazu nije uspeo — podaci na telefonu NISU promenjeni (${e.message ?: e.javaClass.simpleName})."
            )
        }

        // Tek kad je baza uspešno zamenjena: podešavanja iz backup-a (stariji backup-i ih nemaju).
        applyUserPrefs(BackupMapper.prefsOf(decoded))

        return BackupValidationResult(true, "Vraćeno — ${BackupMapper.summary(decoded)}.")
    }

    /** Sve tabele i brojači se čitaju u JEDNOJ transakciji, pa je snimak konzistentan. */
    private suspend fun createBackupSnapshot(): BanketLuxBackup {
        val tables = database.withTransaction {
            BackupTables(
                equipment = equipmentDao.getAllForBackup(),
                customers = customerDao.getAllForBackup(),
                bookings = bookingDao.getAllBookingsForBackup(),
                bookingLines = bookingDao.getAllBookingLinesForBackup(),
                earnings = earningDao.getAllForBackup(),
                settings = businessSettingsDao.getSettings(),
                sequences = readSequences()
            )
        }
        return BackupMapper.toBackup(tables, readUserPrefs(), System.currentTimeMillis())
    }

    // sqlite_sequence nije Room entitet, pa se čita/piše direktno; oba poziva idu ISKLJUČIVO
    // iz withTransaction bloka (ista konekcija i nit kao ostatak transakcije).
    private fun readSequences(): Map<String, Long> {
        val sequences = LinkedHashMap<String, Long>()
        database.openHelper.writableDatabase.query("SELECT name, seq FROM sqlite_sequence").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name in BackupMapper.SEQUENCE_TABLES) sequences[name] = cursor.getLong(1)
            }
        }
        return sequences
    }

    private fun writeSequences(sequences: Map<String, Long>) {
        val db = database.openHelper.writableDatabase
        sequences.forEach { (table, seq) ->
            if (table in BackupMapper.SEQUENCE_TABLES) {
                db.execSQL("DELETE FROM sqlite_sequence WHERE name = ?", arrayOf<Any>(table))
                db.execSQL("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)", arrayOf<Any>(table, seq))
            }
        }
    }
}
