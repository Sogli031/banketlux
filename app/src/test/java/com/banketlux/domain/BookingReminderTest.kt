package com.banketlux.domain

import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.domain.model.BookingStatus
import com.banketlux.notifications.needsReminder
import com.banketlux.notifications.reminderKey
import java.time.LocalDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingReminderTest {
    private fun booking(
        start: String = "2026-09-28",
        status: BookingStatus = BookingStatus.CONFIRMED
    ) = BookingWithLines(
        BookingEntity(
            id = 7,
            customerName = "Pera",
            customerPhone = "",
            location = "",
            eventDate = start,
            rentalStartDate = start,
            rentalEndDate = start,
            status = status,
            notes = "",
            totalPriceRsd = 0,
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            remainingDebtRsd = 0,
            createdAtEpochMillis = 1,
            updatedAtEpochMillis = 1
        ),
        emptyList()
    )

    private val afterNoon = LocalDateTime.of(2026, 9, 27, 12, 5)

    @Test
    fun remindsTheDayBeforeAfterNoon() {
        assertTrue(needsReminder(booking(), afterNoon, emptySet()))
    }

    @Test
    fun waitsUntilNoon() {
        assertFalse(needsReminder(booking(), LocalDateTime.of(2026, 9, 27, 11, 59), emptySet()))
    }

    @Test
    fun onlyForTomorrow() {
        assertFalse(needsReminder(booking(start = "2026-09-27"), afterNoon, emptySet()))
        assertFalse(needsReminder(booking(start = "2026-09-29"), afterNoon, emptySet()))
    }

    @Test
    fun notTwiceForTheSameDate() {
        val b = booking()
        assertFalse(needsReminder(b, afterNoon, setOf(reminderKey(b))))
    }

    @Test
    fun movedBookingGetsANewReminder() {
        val old = booking(start = "2026-09-20")
        assertTrue(needsReminder(booking(), afterNoon, setOf(reminderKey(old))))
    }

    @Test
    fun cancelledBookingIsSkipped() {
        assertFalse(needsReminder(booking(status = BookingStatus.CANCELLED), afterNoon, emptySet()))
    }
}
