package com.banketlux.domain

import com.banketlux.data.local.entity.EarningEntity
import com.banketlux.domain.earnings.monthName
import com.banketlux.domain.earnings.summarizeEarnings
import com.banketlux.domain.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarningsSummaryTest {
    private fun earning(
        rentalEndDate: String,
        amountRsd: Int,
        status: BookingStatus = BookingStatus.CONFIRMED,
        eventDate: String = rentalEndDate
    ) = EarningEntity(
        id = 0,
        bookingId = 1,
        customerName = "Test",
        customerPhone = "",
        location = "",
        eventDate = eventDate,
        rentalStartDate = rentalEndDate,
        rentalEndDate = rentalEndDate,
        equipmentSummary = "",
        amountRsd = amountRsd,
        status = status,
        notes = "",
        archivedAtEpochMillis = 0
    )

    @Test
    fun sumsPerMonthAndPerYear() {
        val overview = summarizeEarnings(
            listOf(
                earning("2026-08-12", 35_000),
                earning("2026-08-25", 15_000),
                earning("2026-09-03", 20_000),
                earning("2025-07-10", 10_000)
            )
        )

        assertEquals(80_000, overview.totalRsd)
        assertEquals(4, overview.jobCount)

        val y2026 = overview.years.first { it.year == 2026 }
        assertEquals(70_000, y2026.amountRsd)
        assertEquals(50_000, y2026.months.first { it.month == 8 }.amountRsd)
        assertEquals(2, y2026.months.first { it.month == 8 }.jobCount)
        assertEquals(20_000, y2026.months.first { it.month == 9 }.amountRsd)

        assertEquals(10_000, overview.years.first { it.year == 2025 }.amountRsd)
    }

    @Test
    fun newestYearAndMonthComeFirst() {
        val overview = summarizeEarnings(
            listOf(
                earning("2024-03-01", 1_000),
                earning("2026-01-01", 1_000),
                earning("2026-11-01", 1_000)
            )
        )

        assertEquals(listOf(2026, 2024), overview.years.map { it.year })
        assertEquals(listOf(11, 1), overview.years.first().months.map { it.month })
    }

    @Test
    fun cancelledBookingsDoNotCountAsEarnings() {
        val overview = summarizeEarnings(
            listOf(
                earning("2026-08-12", 35_000, status = BookingStatus.CONFIRMED),
                earning("2026-08-13", 99_000, status = BookingStatus.CANCELLED),
                earning("2026-08-14", 5_000, status = BookingStatus.INQUIRY),
                earning("2026-08-15", 1_000, status = BookingStatus.COMPLETED)
            )
        )

        assertEquals(36_000, overview.totalRsd)
        assertEquals(2, overview.jobCount)
    }

    @Test
    fun fallsBackToEventDateWhenRentalEndIsBroken() {
        val overview = summarizeEarnings(
            listOf(earning("nije datum", 7_000, eventDate = "2026-05-04"))
        )

        assertEquals(7_000, overview.totalRsd)
        assertEquals(2026, overview.years.single().year)
        assertEquals(5, overview.years.single().months.single().month)
    }

    @Test
    fun entryWithNoUsableDateStillCountsInTotalButNotInMonths() {
        val broken = earning("nije datum", 7_000, eventDate = "ni ovo")
        val overview = summarizeEarnings(listOf(broken))

        // Iznos ne sme da nestane iz ukupnog zbira samo zato što je datum neispravan;
        // u mesečni prikaz ne može jer nema gde da se svrsta.
        assertEquals(7_000, overview.totalRsd)
        assertEquals(1, overview.jobCount)
        assertTrue(overview.years.isEmpty())
        // Prikaz mora da zna koliko ukupnog zbira nije u godinama.
        assertEquals(7_000, overview.undatedRsd)
        assertEquals(1, overview.undatedCount)
    }

    @Test
    fun emptyArchiveGivesEmptyOverview() {
        val overview = summarizeEarnings(emptyList())

        assertEquals(0, overview.totalRsd)
        assertEquals(0, overview.jobCount)
        assertTrue(overview.years.isEmpty())
    }

    @Test
    fun monthsAreNamedInSerbian() {
        assertEquals("Januar", monthName(1))
        assertEquals("Avgust", monthName(8))
        assertEquals("Decembar", monthName(12))
    }
}
