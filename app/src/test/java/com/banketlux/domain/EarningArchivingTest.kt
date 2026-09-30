package com.banketlux.domain

import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import com.banketlux.data.repository.toEarning
import com.banketlux.domain.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class EarningArchivingTest {
    private fun booking(totalPriceRsd: Int) = BookingEntity(
        id = 5,
        customerName = "Pera",
        customerPhone = "060",
        location = "Zlakusa",
        eventDate = "2026-08-10",
        rentalStartDate = "2026-08-10",
        rentalEndDate = "2026-08-12",
        status = BookingStatus.CONFIRMED,
        notes = "bez muzike",
        totalPriceRsd = totalPriceRsd,
        advancePaidRsd = 0,
        amountPaidRsd = 0,
        remainingDebtRsd = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2
    )

    private fun line(name: String, quantity: Int, lineTotalRsd: Int) = BookingLineEntity(
        id = 0,
        bookingId = 5,
        equipmentItemId = 1,
        displayNameSnapshot = name,
        quantity = quantity,
        unitPriceSnapshot = 0,
        lineTotalRsd = lineTotalRsd,
        notes = ""
    )

    @Test
    fun keepsThePriceThatAppliedAtTheTimeOfTheJob() {
        val archived = toEarning(
            BookingWithLines(booking(52_797), listOf(line("Šatra 6x12 m", 1, 35_100))),
            archivedAtEpochMillis = 999
        )

        assertEquals(52_797, archived.amountRsd)
        assertEquals(5, archived.bookingId)
        assertEquals(999, archived.archivedAtEpochMillis)
    }

    @Test
    fun keepsCustomerLocationDatesAndNotes() {
        val archived = toEarning(
            BookingWithLines(booking(1_000), emptyList()),
            archivedAtEpochMillis = 0
        )

        assertEquals("Pera", archived.customerName)
        assertEquals("060", archived.customerPhone)
        assertEquals("Zlakusa", archived.location)
        assertEquals("2026-08-10", archived.rentalStartDate)
        assertEquals("2026-08-12", archived.rentalEndDate)
        assertEquals("bez muzike", archived.notes)
    }

    @Test
    fun equipmentIsStoredOnePerLine() {
        val archived = toEarning(
            BookingWithLines(
                booking(1_000),
                listOf(
                    line("Šatra 6x12 m — komplet", 1, 35_100),
                    line("Stolica", 100, 10_000)
                )
            ),
            archivedAtEpochMillis = 0
        )

        assertEquals(
            "Šatra 6x12 m — komplet x1\nStolica x100",
            archived.equipmentSummary
        )
    }

    @Test
    fun confirmedJobCountsAsEarnedAndCancelledDoesNot() {
        val confirmed = toEarning(BookingWithLines(booking(1_000), emptyList()), 0)
        assertEquals(true, confirmed.countsAsEarned)

        val cancelled = toEarning(
            BookingWithLines(booking(1_000).copy(status = BookingStatus.CANCELLED), emptyList()),
            0
        )
        assertEquals(false, cancelled.countsAsEarned)
    }
}
