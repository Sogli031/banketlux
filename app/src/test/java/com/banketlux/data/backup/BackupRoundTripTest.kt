package com.banketlux.data.backup

import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import com.banketlux.data.local.entity.BusinessSettingsEntity
import com.banketlux.data.local.entity.CustomerEntity
import com.banketlux.data.local.entity.EarningEntity
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.data.repository.isSameJob
import com.banketlux.domain.model.BookingStatus
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup -> sveža instalacija -> vraćanje mora dati IDENTIČNO stanje:
 * svaku kolonu, brojače id-eva i podešavanja iz SharedPreferences.
 */
class BackupRoundTripTest {

    private fun populatedTables() = BackupTables(
        equipment = listOf(
            EquipmentEntity(1, "Banket sto 180", "Stolovi", 40, 900, "sklopivi", true, PriceCurrency.RSD, PriceUnit.PER_DAY),
            EquipmentEntity(7, "Šator 10x15", "Šatori", 2, 350, "", false, PriceCurrency.EUR, PriceUnit.PER_EVENT)
        ),
        customers = listOf(CustomerEntity(3, "Đorđe Šćepanović", "064 111 2233", "stalni kupac")),
        bookings = listOf(
            BookingEntity(
                id = 12, customerName = "Đorđe Šćepanović", customerPhone = "064 111 2233", location = "Čačak",
                eventDate = "2026-10-03", rentalStartDate = "2026-10-02", rentalEndDate = "2026-10-04",
                status = BookingStatus.CONFIRMED, notes = "svadba", totalPriceRsd = 123456, advancePaidRsd = 20000,
                amountPaidRsd = 20000, remainingDebtRsd = 103456, createdAtEpochMillis = 1_700_000_000_000,
                updatedAtEpochMillis = 1_700_000_500_000, calendarEventId = "evt_abc"
            ),
            BookingEntity(
                id = 13, customerName = "Ana", customerPhone = "", location = "", eventDate = "2026-11-01",
                rentalStartDate = "2026-11-01", rentalEndDate = "2026-11-01", status = BookingStatus.INQUIRY,
                notes = "", totalPriceRsd = 0, advancePaidRsd = 0, amountPaidRsd = 0, remainingDebtRsd = 0,
                createdAtEpochMillis = 1_700_001_000_000, updatedAtEpochMillis = 1_700_001_000_000, calendarEventId = null
            )
        ),
        bookingLines = listOf(
            BookingLineEntity(31, 12, 1, "Banket sto 180", 20, 900, 54000, "", PriceCurrency.RSD, PriceUnit.PER_DAY, 117.2),
            BookingLineEntity(32, 12, 7, "Šator 10x15", 1, 350, 41020, "montaža", PriceCurrency.EUR, PriceUnit.PER_EVENT, 117.2)
        ),
        earnings = listOf(
            EarningEntity(
                id = 5, bookingId = 40, customerName = "Stari posao", customerPhone = "061", location = "Užice",
                eventDate = "2026-06-01", rentalStartDate = "2026-05-31", rentalEndDate = "2026-06-02",
                equipmentSummary = "Banket sto 180 x10", amountRsd = 27000, status = BookingStatus.COMPLETED,
                notes = "", archivedAtEpochMillis = 1_690_000_000_000
            )
        ),
        settings = BusinessSettingsEntity(1, "BanketLux", "060 380 8175", 117.35, 1_700_000_123_000, "API"),
        // Brojač zakazivanja je veći od svih id-eva: poslednja zakazivanja su obrisana/arhivirana.
        sequences = mapOf("equipment" to 7L, "customers" to 3L, "bookings" to 41L, "booking_lines" to 90L, "earnings" to 5L)
    )

    private val prefs = BackupUserPrefs(autoBackupEnabled = false, calendarSyncEnabled = true)

    @Test
    fun roundTripKeepsEveryColumnSequenceAndSetting() {
        val original = populatedTables()

        val json = BackupMapper.encode(BackupMapper.toBackup(original, prefs, exportedAtEpochMillis = 1_700_000_900_000))
        val decoded = BackupMapper.decode(json)
        val restored = BackupMapper.toTables(decoded)

        assertEquals(original, restored)
        assertEquals(prefs, BackupMapper.prefsOf(decoded))
        // Drugi izvoz iz vraćenog stanja je bajt-identičan prvom.
        assertEquals(json, BackupMapper.encode(BackupMapper.toBackup(restored, prefs, exportedAtEpochMillis = 1_700_000_900_000)))
    }

    @Test
    fun defaultValuesAreWrittenExplicitly() {
        val json = BackupMapper.encode(BackupMapper.toBackup(populatedTables(), prefs, 1L))
        assertTrue(json.contains("\"currency\": \"RSD\""))
        assertTrue(json.contains("\"priceUnit\": \"PER_DAY\""))
    }

    @Test
    fun schemaV1FileWithoutNewerFieldsStillImports() {
        // Format prve verzije: bez valute, načina naplate, kursa, zarade, brojača i podešavanja.
        val v1 = """
            {
              "schemaVersion": 1,
              "exportedAtEpochMillis": 1690000000000,
              "equipment": [{"id": 2, "name": "Stolica", "category": "Stolice", "totalQuantity": 100,
                             "defaultUnitPriceRsd": 150, "notes": "", "active": true}],
              "customers": [],
              "bookings": [{"id": 4, "customerName": "Mika", "customerPhone": "063", "location": "Beograd",
                            "eventDate": "2025-05-10", "rentalStartDate": "2025-05-09", "rentalEndDate": "2025-05-11",
                            "status": "CONFIRMED", "notes": "", "totalPriceRsd": 15000, "advancePaidRsd": 0,
                            "amountPaidRsd": 0, "remainingDebtRsd": 15000, "createdAtEpochMillis": 1, "updatedAtEpochMillis": 2}],
              "bookingLines": [{"id": 9, "bookingId": 4, "equipmentItemId": 2, "displayNameSnapshot": "Stolica",
                                "quantity": 100, "unitPriceSnapshotRsd": 150, "lineTotalRsd": 15000, "notes": ""}],
              "settings": {"businessName": "BanketLux", "phone": "060 380 8175"}
            }
        """.trimIndent()

        val decoded = BackupMapper.decode(v1)
        val tables = BackupMapper.toTables(decoded)

        assertEquals(PriceCurrency.RSD, tables.equipment[0].currency)
        assertEquals(PriceUnit.PER_DAY, tables.bookingLines[0].priceUnit)
        assertNull(tables.bookings[0].calendarEventId)
        assertTrue(tables.earnings.isEmpty())
        // Stari backup nema podešavanja: lokalna vrednost se ne dira.
        assertEquals(BackupUserPrefs(null, null), BackupMapper.prefsOf(decoded))
        // Brojači se izvode iz id-eva kad ih backup nema.
        assertEquals(4L, tables.sequences["bookings"])
        assertEquals(9L, tables.sequences["booking_lines"])
    }

    @Test
    fun bookingSequenceNeverDropsBelowArchivedBookingIds() {
        // Sva zakazivanja su arhivirana (tabela prazna), a backup nema sačuvane brojače.
        val backup = BackupMapper.toBackup(
            populatedTables().copy(bookings = emptyList(), bookingLines = emptyList(), sequences = emptyMap()),
            prefs,
            1L
        )

        val sequences = BackupMapper.sequencesAfterRestore(backup)

        // earnings.bookingId = 40 -> sledeće zakazivanje mora dobiti id > 40, ne 1.
        assertEquals(40L, sequences["bookings"])
    }

    @Test
    fun invalidRecordFailsWholeImportInsteadOfBeingDefaulted() {
        val good = BackupMapper.toBackup(populatedTables(), prefs, 1L)

        val unknownEarningStatus = good.copy(earnings = good.earnings.map { it.copy(status = "NEPOZNATO") })
        assertThrows(BackupImportException::class.java) { BackupMapper.toTables(unknownEarningStatus) }

        val unknownCurrency = good.copy(equipment = good.equipment.map { it.copy(currency = "USD") })
        assertThrows(BackupImportException::class.java) { BackupMapper.toTables(unknownCurrency) }

        val orphanLine = good.copy(bookings = good.bookings.filter { it.id != 12L })
        assertThrows(BackupImportException::class.java) { BackupMapper.toTables(orphanLine) }

        val duplicateIds = good.copy(customers = good.customers + good.customers)
        assertThrows(BackupImportException::class.java) { BackupMapper.toTables(duplicateIds) }

        assertThrows(BackupImportException::class.java) { BackupMapper.decode("{\"nije\": \"backup\"}") }
        assertThrows(BackupImportException::class.java) { BackupMapper.decode(BackupMapper.encode(good.copy(schemaVersion = 99))) }
    }

    @Test
    fun seededPriceListAloneDoesNotCountAsUserData() {
        val fresh = BackupMapper.toBackup(
            populatedTables().copy(customers = emptyList(), bookings = emptyList(), bookingLines = emptyList(), earnings = emptyList()),
            prefs,
            1L
        )
        assertFalse(BackupMapper.hasUserRecords(fresh))
        assertTrue(BackupMapper.hasUserRecords(BackupMapper.toBackup(populatedTables(), prefs, 1L)))
    }

    @Test
    fun archiveGuardTellsSameJobFromIdCollision() {
        val archived = populatedTables().earnings[0]
        assertTrue(isSameJob(archived, archived.copy(id = 0, archivedAtEpochMillis = 99)))
        // Isti bookingId, ali drugi posao (id ponovo dodeljen posle vraćanja starog backup-a).
        assertFalse(isSameJob(archived, archived.copy(id = 0, customerName = "Novi kupac", eventDate = "2027-01-15")))
    }
}
