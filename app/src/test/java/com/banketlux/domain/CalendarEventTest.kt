package com.banketlux.domain

import com.banketlux.data.calendar.buildEventDescription
import com.banketlux.data.calendar.buildEventSummary
import com.banketlux.data.calendar.exclusiveEndDate
import com.banketlux.data.calendar.importDates
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import com.banketlux.domain.model.BookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventTest {
    private fun booking(
        customerName: String = "Pera Perić",
        customerPhone: String = "060345781",
        notes: String = "",
        totalPriceRsd: Int = 52_797
    ) = BookingEntity(
        id = 5,
        customerName = customerName,
        customerPhone = customerPhone,
        location = "Zlakusa",
        eventDate = "2026-08-10",
        rentalStartDate = "2026-08-10",
        rentalEndDate = "2026-08-12",
        status = BookingStatus.CONFIRMED,
        notes = notes,
        totalPriceRsd = totalPriceRsd,
        advancePaidRsd = 0,
        amountPaidRsd = 0,
        remainingDebtRsd = 0,
        createdAtEpochMillis = 1,
        updatedAtEpochMillis = 2
    )

    private fun line(name: String, quantity: Int) = BookingLineEntity(
        id = 0,
        bookingId = 5,
        equipmentItemId = 1,
        displayNameSnapshot = name,
        quantity = quantity,
        unitPriceSnapshot = 0,
        lineTotalRsd = 0,
        notes = ""
    )

    @Test
    fun summaryIsJustTheCustomerNameWithoutEquipment() {
        assertEquals(
            "Pera Perić",
            buildEventSummary(BookingWithLines(booking(customerName = " Pera Perić "), emptyList()))
        )
    }

    @Test
    fun summaryListsEquipmentNextToTheName() {
        assertEquals(
            "Pera Perić: Šatra 6x12 m — komplet x1, Stolica x100",
            buildEventSummary(
                BookingWithLines(
                    booking(),
                    listOf(line("Šatra 6x12 m — komplet", 1), line("Stolica", 100))
                )
            )
        )
    }

    @Test
    fun tableclothWashingIsListedWithoutQuantity() {
        val washing = line(com.banketlux.domain.model.TableclothWashing.NAME, 1)
            .copy(equipmentItemId = com.banketlux.domain.model.TableclothWashing.EQUIPMENT_ID)
        assertEquals(
            "Pera Perić: Šatra 8x5 m x1, Pranje stolnjaka",
            buildEventSummary(BookingWithLines(booking(), listOf(line("Šatra 8x5 m", 1), washing)))
        )
    }

    @Test
    fun importDatesTreatAllDayEndAsExclusive() {
        val zone = java.time.ZoneId.of("Europe/Belgrade")
        assertEquals(
            java.time.LocalDate.of(2026, 10, 5) to java.time.LocalDate.of(2026, 10, 5),
            importDates("2026-10-05", "2026-10-06", null, null, zone)
        )
        assertEquals(
            java.time.LocalDate.of(2026, 10, 5) to java.time.LocalDate.of(2026, 10, 7),
            importDates("2026-10-05", "2026-10-08", null, null, zone)
        )
    }

    @Test
    fun importDatesForTimedEventEndingAtMidnightStayOnOneDay() {
        val zone = java.time.ZoneId.of("Europe/Belgrade")
        val start = java.time.ZonedDateTime.of(2026, 10, 5, 18, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = java.time.ZonedDateTime.of(2026, 10, 6, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(
            java.time.LocalDate.of(2026, 10, 5) to java.time.LocalDate.of(2026, 10, 5),
            importDates(null, null, start, end, zone)
        )
    }

    @Test
    fun summaryStaysUsableWhenNameIsMissing() {
        assertEquals(
            "Zakazivanje",
            buildEventSummary(BookingWithLines(booking(customerName = "   "), emptyList()))
        )
    }

    @Test
    fun descriptionListsPhoneEquipmentAndTotal() {
        val description = buildEventDescription(
            BookingWithLines(
                booking(),
                listOf(line("Šatra 6x12 m — komplet", 1), line("Stolica", 100))
            )
        )

        assertTrue(description.contains("Telefon: 060345781"))
        assertTrue(description.contains("• Šatra 6x12 m — komplet x1"))
        assertTrue(description.contains("• Stolica x100"))
        assertTrue(description.contains("Ukupno: 52.797 RSD"))
    }

    @Test
    fun descriptionSkipsEmptyPhoneAndNotes() {
        val description = buildEventDescription(
            BookingWithLines(booking(customerPhone = "", notes = ""), emptyList())
        )

        assertTrue(!description.contains("Telefon"))
        assertTrue(!description.contains("Napomena"))
    }

    @Test
    fun descriptionIncludesNotesWhenPresent() {
        val description = buildEventDescription(
            BookingWithLines(booking(notes = "bez muzike posle ponoći"), emptyList())
        )

        assertTrue(description.contains("Napomena: bez muzike posle ponoći"))
    }

    @Test
    fun allDayEndDateIsPushedOneDayBecauseGoogleTreatsItAsExclusive() {
        assertEquals("2026-08-13", exclusiveEndDate("2026-08-12", "2026-08-10"))
    }

    @Test
    fun singleDayRentalStillCoversThatDay() {
        assertEquals("2026-08-11", exclusiveEndDate("2026-08-10", "2026-08-10"))
    }

    @Test
    fun brokenEndDateFallsBackToStartDate() {
        assertEquals("2026-08-11", exclusiveEndDate("nije datum", "2026-08-10"))
    }
}
