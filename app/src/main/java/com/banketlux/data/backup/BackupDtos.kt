package com.banketlux.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BanketLuxBackup(
    val schemaVersion: Int,
    val exportedAtEpochMillis: Long,
    val equipment: List<BackupEquipment>,
    val customers: List<BackupCustomer>,
    val bookings: List<BackupBooking>,
    val bookingLines: List<BackupBookingLine>,
    val settings: BackupSettings,
    val earnings: List<BackupEarning> = emptyList(),
    /**
     * AUTOINCREMENT brojači (sqlite_sequence) po tabeli. Bez njih bi posle vraćanja novo
     * zakazivanje moglo da dobije id već arhiviranog posla. Stariji backup-i ih nemaju.
     */
    val sequences: Map<String, Long> = emptyMap()
)

@Serializable
data class BackupEarning(
    val id: Long,
    val bookingId: Long,
    val customerName: String,
    val customerPhone: String,
    val location: String,
    val eventDate: String,
    val rentalStartDate: String,
    val rentalEndDate: String,
    val equipmentSummary: String,
    val amountRsd: Int,
    val status: String,
    val notes: String,
    val archivedAtEpochMillis: Long
)

@Serializable
data class BackupEquipment(
    val id: Long,
    val name: String,
    val category: String,
    val totalQuantity: Int,
    // Ime polja je nasleđeno iz šeme v1; od v2 je iznos u valuti iz `currency`.
    val defaultUnitPriceRsd: Int,
    val notes: String,
    val active: Boolean,
    val currency: String = "RSD",
    val priceUnit: String = "PER_DAY"
)

@Serializable
data class BackupCustomer(
    val id: Long,
    val name: String,
    val phone: String,
    val notes: String
)

@Serializable
data class BackupBooking(
    val id: Long,
    val customerName: String,
    val customerPhone: String,
    val location: String,
    val eventDate: String,
    val rentalStartDate: String,
    val rentalEndDate: String,
    val status: String,
    val notes: String,
    val totalPriceRsd: Int,
    val advancePaidRsd: Int,
    val amountPaidRsd: Int,
    val remainingDebtRsd: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val calendarEventId: String? = null
)

@Serializable
data class BackupBookingLine(
    val id: Long,
    val bookingId: Long,
    val equipmentItemId: Long,
    val displayNameSnapshot: String,
    val quantity: Int,
    // Ime polja je nasleđeno iz šeme v1; od v2 je iznos u valuti iz `currency`.
    val unitPriceSnapshotRsd: Int,
    val lineTotalRsd: Int,
    val notes: String,
    val currency: String = "RSD",
    val priceUnit: String = "PER_DAY",
    val eurRateSnapshot: Double = 117.0
)

@Serializable
data class BackupSettings(
    val businessName: String,
    val phone: String,
    val eurToRsdRate: Double = 117.0,
    val eurRateUpdatedAtEpochMillis: Long = 0,
    val eurRateSource: String = "MANUAL",
    // Korisnička podešavanja koja žive samo u SharedPreferences. null = backup ih nema
    // (stariji format) — tada se pri vraćanju lokalna vrednost ne dira.
    val autoBackupEnabled: Boolean? = null,
    val calendarSyncEnabled: Boolean? = null
)
