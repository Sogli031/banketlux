package com.banketlux.domain

import com.banketlux.ui.bookings.formatDate
import com.banketlux.ui.bookings.formatRentalPeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingDateFormattingTest {
    @Test
    fun formatsIsoDateAsDayMonthYear() {
        assertEquals("10.08.2026.", formatDate("2026-08-10"))
        assertEquals("01.01.2027.", formatDate("2027-01-01"))
    }

    @Test
    fun brokenDateFallsBackToRawValue() {
        assertEquals("nije datum", formatDate("nije datum"))
    }

    @Test
    fun rangeShowsBothEnds() {
        assertEquals(
            "10.08.2026. \u2013 12.08.2026.",
            formatRentalPeriod("2026-08-10", "2026-08-12")
        )
    }

    @Test
    fun singleDayRentalIsNotRepeatedTwice() {
        assertEquals("10.08.2026.", formatRentalPeriod("2026-08-10", "2026-08-10"))
    }
}
