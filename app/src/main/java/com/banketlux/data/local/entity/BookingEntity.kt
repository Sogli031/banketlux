package com.banketlux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banketlux.domain.model.BookingStatus

@Entity(tableName = "bookings")
data class BookingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerName: String,
    val customerPhone: String,
    val location: String,
    val eventDate: String,
    val rentalStartDate: String,
    val rentalEndDate: String,
    val status: BookingStatus,
    val notes: String,
    val totalPriceRsd: Int,
    val advancePaidRsd: Int,
    val amountPaidRsd: Int,
    val remainingDebtRsd: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    /** Id događaja u Google kalendaru; null dok zakazivanje nije upisano u kalendar. */
    val calendarEventId: String? = null
)
