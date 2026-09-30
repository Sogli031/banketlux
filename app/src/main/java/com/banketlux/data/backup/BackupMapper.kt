package com.banketlux.data.backup

import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import com.banketlux.data.local.entity.BusinessSettingsEntity
import com.banketlux.data.local.entity.CustomerEntity
import com.banketlux.data.local.entity.EarningEntity
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.domain.model.BookingStatus
import com.banketlux.domain.model.CurrencyConverter
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import kotlinx.serialization.json.Json

/** Backup ne može da se pročita ili sadrži neispravan zapis. Poruka je za korisnika. */
class BackupImportException(message: String) : Exception(message)

/** Ceo sadržaj baze u obliku entiteta — ono što se čita pri izvozu i upisuje pri vraćanju. */
data class BackupTables(
    val equipment: List<EquipmentEntity>,
    val customers: List<CustomerEntity>,
    val bookings: List<BookingEntity>,
    val bookingLines: List<BookingLineEntity>,
    val earnings: List<EarningEntity>,
    val settings: BusinessSettingsEntity?,
    val sequences: Map<String, Long>
)

/** Korisnička podešavanja iz SharedPreferences. null = nije poznato / ne diraj. */
data class BackupUserPrefs(
    val autoBackupEnabled: Boolean?,
    val calendarSyncEnabled: Boolean?
)

/** Sadržaj za otpremu: JSON + brojevi zapisa koji se upisuju uz verziju u oblaku. */
data class BackupUpload(
    val json: String,
    val counts: Map<String, String>
)

/**
 * Pretvaranje baza <-> backup dokument. Čist Kotlin (bez Androida i Room-a u radu),
 * da round-trip i stari formati mogu da se testiraju na JVM-u.
 *
 * Pravilo vraćanja: SVE se dekodira, proverava i mapira PRE prvog upisa u bazu;
 * bilo koji neispravan zapis je greška celog vraćanja — nikad delimičan "uspeh".
 */
object BackupMapper {

    /** Tabele sa AUTOINCREMENT ključem, imenima kakva su u sqlite_sequence. */
    val SEQUENCE_TABLES = listOf("equipment", "customers", "bookings", "booking_lines", "earnings")

    const val COUNT_BOOKINGS = "bookings"
    const val COUNT_EARNINGS = "earnings"
    const val COUNT_CUSTOMERS = "customers"
    const val COUNT_EQUIPMENT = "equipment"

    // encodeDefaults = true: vrednost se UVEK upisuje u fajl, pa promena podrazumevane
    // vrednosti u nekoj budućoj verziji ne može tiho da promeni stare backup-e.
    val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(backup: BanketLuxBackup): String = json.encodeToString(BanketLuxBackup.serializer(), backup)

    fun decode(payload: String): BanketLuxBackup {
        val decoded = try {
            json.decodeFromString(BanketLuxBackup.serializer(), payload)
        } catch (e: Exception) {
            throw BackupImportException("Backup fajl nije podržan u ovoj verziji aplikacije.")
        }
        val validation = BackupValidator.validate(decoded)
        if (!validation.isValid) {
            throw BackupImportException(validation.message ?: "Backup fajl nije podržan u ovoj verziji aplikacije.")
        }
        return decoded
    }

    fun toBackup(tables: BackupTables, prefs: BackupUserPrefs, exportedAtEpochMillis: Long): BanketLuxBackup {
        val settings = tables.settings
        return BanketLuxBackup(
            schemaVersion = BackupValidator.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMillis = exportedAtEpochMillis,
            equipment = tables.equipment.map { item ->
                BackupEquipment(
                    id = item.id,
                    name = item.name,
                    category = item.category,
                    totalQuantity = item.totalQuantity,
                    defaultUnitPriceRsd = item.defaultUnitPrice,
                    notes = item.notes,
                    active = item.active,
                    currency = item.currency.name,
                    priceUnit = item.priceUnit.name
                )
            },
            customers = tables.customers.map { customer ->
                BackupCustomer(
                    id = customer.id,
                    name = customer.name,
                    phone = customer.phone,
                    notes = customer.notes
                )
            },
            bookings = tables.bookings.map { booking ->
                BackupBooking(
                    id = booking.id,
                    customerName = booking.customerName,
                    customerPhone = booking.customerPhone,
                    location = booking.location,
                    eventDate = booking.eventDate,
                    rentalStartDate = booking.rentalStartDate,
                    rentalEndDate = booking.rentalEndDate,
                    status = booking.status.name,
                    notes = booking.notes,
                    totalPriceRsd = booking.totalPriceRsd,
                    advancePaidRsd = booking.advancePaidRsd,
                    amountPaidRsd = booking.amountPaidRsd,
                    remainingDebtRsd = booking.remainingDebtRsd,
                    createdAtEpochMillis = booking.createdAtEpochMillis,
                    updatedAtEpochMillis = booking.updatedAtEpochMillis,
                    calendarEventId = booking.calendarEventId
                )
            },
            bookingLines = tables.bookingLines.map { line ->
                BackupBookingLine(
                    id = line.id,
                    bookingId = line.bookingId,
                    equipmentItemId = line.equipmentItemId,
                    displayNameSnapshot = line.displayNameSnapshot,
                    quantity = line.quantity,
                    unitPriceSnapshotRsd = line.unitPriceSnapshot,
                    lineTotalRsd = line.lineTotalRsd,
                    notes = line.notes,
                    currency = line.currency.name,
                    priceUnit = line.priceUnit.name,
                    eurRateSnapshot = line.eurRateSnapshot
                )
            },
            settings = BackupSettings(
                businessName = settings?.businessName ?: "BanketLux",
                phone = settings?.phone ?: "060 380 8175",
                eurToRsdRate = settings?.eurToRsdRate ?: CurrencyConverter.DEFAULT_EUR_TO_RSD_RATE,
                eurRateUpdatedAtEpochMillis = settings?.eurRateUpdatedAtEpochMillis ?: 0,
                eurRateSource = settings?.eurRateSource ?: "MANUAL",
                autoBackupEnabled = prefs.autoBackupEnabled,
                calendarSyncEnabled = prefs.calendarSyncEnabled
            ),
            earnings = tables.earnings.map { entry ->
                BackupEarning(
                    id = entry.id,
                    bookingId = entry.bookingId,
                    customerName = entry.customerName,
                    customerPhone = entry.customerPhone,
                    location = entry.location,
                    eventDate = entry.eventDate,
                    rentalStartDate = entry.rentalStartDate,
                    rentalEndDate = entry.rentalEndDate,
                    equipmentSummary = entry.equipmentSummary,
                    amountRsd = entry.amountRsd,
                    status = entry.status.name,
                    notes = entry.notes,
                    archivedAtEpochMillis = entry.archivedAtEpochMillis
                )
            },
            sequences = tables.sequences
        )
    }

    private fun fail(section: String, id: Long, what: String): Nothing =
        throw BackupImportException("Backup je oštećen: $section (id $id) — $what.")

    private fun requireUniqueIds(section: String, ids: List<Long>) {
        val seen = HashSet<Long>()
        ids.forEach { id -> if (!seen.add(id)) fail(section, id, "dupli identifikator") }
    }

    private fun currencyOf(section: String, id: Long, value: String): PriceCurrency =
        PriceCurrency.entries.firstOrNull { it.name == value } ?: fail(section, id, "nepoznata valuta „$value“")

    private fun priceUnitOf(section: String, id: Long, value: String): PriceUnit =
        PriceUnit.entries.firstOrNull { it.name == value } ?: fail(section, id, "nepoznat način naplate „$value“")

    private fun statusOf(section: String, id: Long, value: String): BookingStatus =
        BookingStatus.entries.firstOrNull { it.name == value } ?: fail(section, id, "nepoznat status „$value“")

    /** Strogo mapiranje u entitete. Baca [BackupImportException] na prvi neispravan zapis. */
    fun toTables(backup: BanketLuxBackup): BackupTables {
        requireUniqueIds("oprema", backup.equipment.map { it.id })
        requireUniqueIds("kupci", backup.customers.map { it.id })
        requireUniqueIds("zakazivanja", backup.bookings.map { it.id })
        requireUniqueIds("stavke zakazivanja", backup.bookingLines.map { it.id })
        requireUniqueIds("zarada", backup.earnings.map { it.id })

        val bookingIds = backup.bookings.map { it.id }.toHashSet()
        backup.bookingLines.forEach { line ->
            if (line.bookingId !in bookingIds) {
                fail("stavke zakazivanja", line.id, "zakazivanje ${line.bookingId} ne postoji u backup-u")
            }
        }

        return BackupTables(
            equipment = backup.equipment.map { item ->
                EquipmentEntity(
                    id = item.id,
                    name = item.name,
                    category = item.category,
                    totalQuantity = item.totalQuantity,
                    defaultUnitPrice = item.defaultUnitPriceRsd,
                    notes = item.notes,
                    active = item.active,
                    currency = currencyOf("oprema", item.id, item.currency),
                    priceUnit = priceUnitOf("oprema", item.id, item.priceUnit)
                )
            },
            customers = backup.customers.map { customer ->
                CustomerEntity(
                    id = customer.id,
                    name = customer.name,
                    phone = customer.phone,
                    notes = customer.notes
                )
            },
            bookings = backup.bookings.map { booking ->
                BookingEntity(
                    id = booking.id,
                    customerName = booking.customerName,
                    customerPhone = booking.customerPhone,
                    location = booking.location,
                    eventDate = booking.eventDate,
                    rentalStartDate = booking.rentalStartDate,
                    rentalEndDate = booking.rentalEndDate,
                    status = statusOf("zakazivanja", booking.id, booking.status),
                    notes = booking.notes,
                    totalPriceRsd = booking.totalPriceRsd,
                    advancePaidRsd = booking.advancePaidRsd,
                    amountPaidRsd = booking.amountPaidRsd,
                    remainingDebtRsd = booking.remainingDebtRsd,
                    createdAtEpochMillis = booking.createdAtEpochMillis,
                    updatedAtEpochMillis = booking.updatedAtEpochMillis,
                    calendarEventId = booking.calendarEventId
                )
            },
            bookingLines = backup.bookingLines.map { line ->
                BookingLineEntity(
                    id = line.id,
                    bookingId = line.bookingId,
                    equipmentItemId = line.equipmentItemId,
                    displayNameSnapshot = line.displayNameSnapshot,
                    quantity = line.quantity,
                    unitPriceSnapshot = line.unitPriceSnapshotRsd,
                    lineTotalRsd = line.lineTotalRsd,
                    notes = line.notes,
                    currency = currencyOf("stavke zakazivanja", line.id, line.currency),
                    priceUnit = priceUnitOf("stavke zakazivanja", line.id, line.priceUnit),
                    eurRateSnapshot = CurrencyConverter.safeRate(line.eurRateSnapshot)
                )
            },
            earnings = backup.earnings.map { entry ->
                EarningEntity(
                    id = entry.id,
                    bookingId = entry.bookingId,
                    customerName = entry.customerName,
                    customerPhone = entry.customerPhone,
                    location = entry.location,
                    eventDate = entry.eventDate,
                    rentalStartDate = entry.rentalStartDate,
                    rentalEndDate = entry.rentalEndDate,
                    equipmentSummary = entry.equipmentSummary,
                    amountRsd = entry.amountRsd,
                    status = statusOf("zarada", entry.id, entry.status),
                    notes = entry.notes,
                    archivedAtEpochMillis = entry.archivedAtEpochMillis
                )
            },
            settings = BusinessSettingsEntity(
                id = 1L,
                businessName = backup.settings.businessName,
                phone = backup.settings.phone,
                eurToRsdRate = CurrencyConverter.safeRate(backup.settings.eurToRsdRate),
                eurRateUpdatedAtEpochMillis = backup.settings.eurRateUpdatedAtEpochMillis,
                eurRateSource = backup.settings.eurRateSource
            ),
            sequences = sequencesAfterRestore(backup)
        )
    }

    fun prefsOf(backup: BanketLuxBackup): BackupUserPrefs = BackupUserPrefs(
        autoBackupEnabled = backup.settings.autoBackupEnabled,
        calendarSyncEnabled = backup.settings.calendarSyncEnabled
    )

    /**
     * Brojači koje baza mora da ima POSLE vraćanja: najveće od (brojač iz backup-a,
     * najveći id u tabeli). Za zakazivanja se gleda i najveći `earnings.bookingId` — arhivirana
     * zakazivanja su obrisana iz tabele, ali njihov id više nikad ne sme da se dodeli
     * (arhiviranje prepoznaje posao po bookingId). Ovo štiti i backup-e bez sačuvanih brojača.
     */
    fun sequencesAfterRestore(backup: BanketLuxBackup): Map<String, Long> {
        val maxIds = mapOf(
            "equipment" to (backup.equipment.maxOfOrNull { it.id } ?: 0L),
            "customers" to (backup.customers.maxOfOrNull { it.id } ?: 0L),
            "bookings" to maxOf(
                backup.bookings.maxOfOrNull { it.id } ?: 0L,
                backup.earnings.maxOfOrNull { it.bookingId } ?: 0L
            ),
            "booking_lines" to (backup.bookingLines.maxOfOrNull { it.id } ?: 0L),
            "earnings" to (backup.earnings.maxOfOrNull { it.id } ?: 0L)
        )
        return SEQUENCE_TABLES.associateWith { table ->
            maxOf(backup.sequences[table] ?: 0L, maxIds[table] ?: 0L)
        }
    }

    /** Zakazivanja, zarada i kupci. Oprema se ne broji — cenovnik se unosi sam pri instalaciji. */
    fun hasUserRecords(backup: BanketLuxBackup): Boolean =
        backup.bookings.isNotEmpty() || backup.earnings.isNotEmpty() || backup.customers.isNotEmpty()

    fun counts(backup: BanketLuxBackup): Map<String, String> = mapOf(
        COUNT_BOOKINGS to backup.bookings.size.toString(),
        COUNT_EARNINGS to backup.earnings.size.toString(),
        COUNT_CUSTOMERS to backup.customers.size.toString(),
        COUNT_EQUIPMENT to backup.equipment.size.toString()
    )

    fun summary(backup: BanketLuxBackup): String =
        "zakazivanja: ${backup.bookings.size}, zarada: ${backup.earnings.size}, " +
            "kupaca: ${backup.customers.size}, opreme: ${backup.equipment.size}"
}
