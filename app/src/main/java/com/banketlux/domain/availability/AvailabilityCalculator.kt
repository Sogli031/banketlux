package com.banketlux.domain.availability

import com.banketlux.domain.model.BookingStatus
import com.banketlux.domain.model.DateRange

data class Reservation(
    val bookingId: Long,
    val customerName: String,
    val quantity: Int,
    val range: DateRange,
    val status: BookingStatus
)

data class AvailabilityResult(
    val totalQuantity: Int,
    val alreadyReservedQuantity: Int,
    val availableQuantity: Int,
    val isShortage: Boolean,
    val conflicts: List<Reservation>
)

object AvailabilityCalculator {
    fun calculate(
        totalQuantity: Int,
        requestedQuantity: Int,
        requestedRange: DateRange,
        reservations: List<Reservation>,
        currentBookingId: Long?
    ): AvailabilityResult {
        val conflicts = reservations.filter {
            it.bookingId != currentBookingId &&
                it.status.reservesEquipment &&
                it.range.overlaps(requestedRange)
        }
        val reserved = conflicts.sumOf { it.quantity }
        val available = (totalQuantity - reserved).coerceAtLeast(0)
        return AvailabilityResult(
            totalQuantity = totalQuantity,
            alreadyReservedQuantity = reserved,
            availableQuantity = available,
            isShortage = requestedQuantity > available,
            conflicts = conflicts
        )
    }
}
