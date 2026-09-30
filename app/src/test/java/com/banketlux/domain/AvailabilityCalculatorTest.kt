package com.banketlux.domain

import com.banketlux.domain.availability.AvailabilityCalculator
import com.banketlux.domain.availability.Reservation
import com.banketlux.domain.model.BookingStatus
import com.banketlux.domain.model.DateRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityCalculatorTest {
    private val requested = DateRange(
        start = LocalDate.of(2026, 6, 10),
        end = LocalDate.of(2026, 6, 12)
    )

    @Test
    fun confirmedOverlappingReservationsReduceAvailability() {
        val result = AvailabilityCalculator.calculate(
            totalQuantity = 20,
            requestedQuantity = 15,
            requestedRange = requested,
            reservations = listOf(
                Reservation(
                    bookingId = 1,
                    customerName = "Milan",
                    quantity = 8,
                    range = DateRange(LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 13)),
                    status = BookingStatus.CONFIRMED
                )
            ),
            currentBookingId = null
        )

        assertEquals(8, result.alreadyReservedQuantity)
        assertEquals(12, result.availableQuantity)
        assertTrue(result.isShortage)
    }

    @Test
    fun inquiryAndCancelledReservationsDoNotReserveEquipment() {
        val result = AvailabilityCalculator.calculate(
            totalQuantity = 20,
            requestedQuantity = 20,
            requestedRange = requested,
            reservations = listOf(
                Reservation(1, "Upit", 20, requested, BookingStatus.INQUIRY),
                Reservation(2, "Otkazano", 20, requested, BookingStatus.CANCELLED)
            ),
            currentBookingId = null
        )

        assertEquals(0, result.alreadyReservedQuantity)
        assertFalse(result.isShortage)
    }
}
