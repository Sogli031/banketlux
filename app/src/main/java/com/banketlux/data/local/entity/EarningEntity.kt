package com.banketlux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banketlux.domain.model.BookingStatus

/**
 * Arhiva završenog zakazivanja. Zakazivanje nestaje sa liste kad najam prođe,
 * a ovde ostaje iznos onakav kakav je bio u trenutku posla — kasnija izmena
 * cenovnika ne dira već zarađeno.
 */
@Entity(tableName = "earnings")
data class EarningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val customerName: String,
    val customerPhone: String,
    val location: String,
    val eventDate: String,
    val rentalStartDate: String,
    val rentalEndDate: String,
    val equipmentSummary: String,
    val amountRsd: Int,
    val status: BookingStatus,
    val notes: String,
    val archivedAtEpochMillis: Long
) {
    /** Otkazana zakazivanja i upiti se arhiviraju, ali se ne broje u zaradu. */
    val countsAsEarned: Boolean
        get() = status == BookingStatus.CONFIRMED || status == BookingStatus.COMPLETED
}
