package com.banketlux.domain

import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import com.banketlux.ui.bookings.BookingLineDraft
import com.banketlux.ui.bookings.computeRentalDays
import com.banketlux.ui.bookings.mapBookingLineDraftsToEntities
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingDraftMappingTest {
    @Test
    fun washingIsSavedRightAfterItsOwnTent() {
        val bigTent = BookingLineDraft(
            id = 1, equipmentItemId = 10, equipmentName = "Šatra 6x12 m",
            quantityInput = "1", unitPriceInput = "100",
            washing = true, washingPriceInput = "2500"
        )
        val smallTent = BookingLineDraft(
            id = 2, equipmentItemId = 11, equipmentName = "Šatra 8x5 m",
            quantityInput = "1", unitPriceInput = "50", washing = false
        )
        val chair = BookingLineDraft(
            id = 3, equipmentItemId = 20, equipmentName = "Stolica",
            quantityInput = "10", unitPriceInput = "100", washing = true
        )

        val entities = mapBookingLineDraftsToEntities(
            bookingId = 1,
            lines = listOf(bigTent, smallTent, chair),
            isTent = { it.equipmentItemId == 10L || it.equipmentItemId == 11L }
        )

        // Pranje samo uz veliku šatru, odmah iza nje; stolica ga nema iako je "washing" upaljen.
        assertEquals(listOf(10L, -1L, 11L, 20L), entities.map { it.equipmentItemId })
        assertEquals(2500, entities[1].lineTotalRsd)
        assertEquals(PriceUnit.PER_EVENT, entities[1].priceUnit)
    }

    @Test
    fun snapshotsLineNameAndPriceForSavedBooking() {
        val mapped = mapBookingLineDraftsToEntities(
            bookingId = 42L,
            lines = listOf(
                BookingLineDraft(
                    id = 1,
                    equipmentItemId = 10L,
                    equipmentName = "Šator 5x10",
                    quantityInput = "2",
                    unitPriceInput = "15000",
                    notes = "Premium"
                )
            )
        )

        assertEquals(1, mapped.size)
        assertEquals(42L, mapped.first().bookingId)
        assertEquals("Šator 5x10", mapped.first().displayNameSnapshot)
        assertEquals(15000, mapped.first().unitPriceSnapshot)
        assertEquals(30000, mapped.first().lineTotalRsd)
    }

    @Test
    fun lineTotalMultipliedByRentalDays() {
        val mapped = mapBookingLineDraftsToEntities(
            bookingId = 1L,
            lines = listOf(
                BookingLineDraft(
                    id = 1,
                    equipmentItemId = 5L,
                    equipmentName = "Stolice",
                    quantityInput = "20",
                    unitPriceInput = "100"
                )
            ),
            rentalDays = 4
        )

        assertEquals(8000, mapped.first().lineTotalRsd)
    }

    @Test
    fun rentalDaysIsInclusive() {
        assertEquals(1, computeRentalDays(LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 24)))
        assertEquals(3, computeRentalDays(LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 26)))
        assertEquals(1, computeRentalDays(LocalDate.of(2026, 5, 26), LocalDate.of(2026, 5, 24)))
    }

    @Test
    fun perEventEurLineStoresConvertedTotalAndRateSnapshot() {
        val mapped = mapBookingLineDraftsToEntities(
            bookingId = 7L,
            lines = listOf(
                BookingLineDraft(
                    id = 1,
                    equipmentItemId = 3L,
                    equipmentName = "Šatra 6x12 m — komplet",
                    quantityInput = "1",
                    unitPriceInput = "300",
                    currency = PriceCurrency.EUR,
                    priceUnit = PriceUnit.PER_EVENT
                )
            ),
            rentalDays = 3,
            eurToRsdRate = 117.0
        )

        val line = mapped.first()
        assertEquals(300, line.unitPriceSnapshot)
        assertEquals(PriceCurrency.EUR, line.currency)
        assertEquals(PriceUnit.PER_EVENT, line.priceUnit)
        assertEquals(117.0, line.eurRateSnapshot, 0.001)
        // Paušal se ne množi brojem dana.
        assertEquals(35100, line.lineTotalRsd)
    }

    @Test
    fun blankQuantityCountsAsOnePieceInsteadOfDroppingTheLine() {
        val mapped = mapBookingLineDraftsToEntities(
            bookingId = 1L,
            lines = listOf(
                BookingLineDraft(
                    id = 1,
                    equipmentItemId = 9L,
                    equipmentName = "Paviljon",
                    quantityInput = "",
                    unitPriceInput = "15",
                    currency = PriceCurrency.EUR,
                    priceUnit = PriceUnit.PER_DAY
                )
            ),
            rentalDays = 2,
            eurToRsdRate = 117.0
        )

        assertEquals(1, mapped.size)
        assertEquals(1, mapped.first().quantity)
        assertEquals(3510, mapped.first().lineTotalRsd)
    }

    @Test
    fun lineWithoutSelectedEquipmentIsSkipped() {
        val mapped = mapBookingLineDraftsToEntities(
            bookingId = 1L,
            lines = listOf(BookingLineDraft(id = 1, quantityInput = "3", unitPriceInput = "100"))
        )

        assertEquals(0, mapped.size)
    }

    @Test
    fun explicitZeroQuantityIsSkipped() {
        val mapped = mapBookingLineDraftsToEntities(
            bookingId = 1L,
            lines = listOf(
                BookingLineDraft(
                    id = 1,
                    equipmentItemId = 4L,
                    equipmentName = "Stolica",
                    quantityInput = "0",
                    unitPriceInput = "100"
                )
            )
        )

        assertEquals(0, mapped.size)
    }
}
