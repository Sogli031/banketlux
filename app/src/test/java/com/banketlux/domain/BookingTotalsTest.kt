package com.banketlux.domain

import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import com.banketlux.domain.pricing.BookingLineInput
import com.banketlux.domain.pricing.BookingTotals
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingTotalsTest {
    @Test
    fun calculatesTotalAndRemainingDebt() {
        val result = BookingTotals.calculate(
            lines = listOf(
                BookingLineInput(quantity = 10, unitPrice = 500),
                BookingLineInput(quantity = 2, unitPrice = 1500)
            ),
            advancePaidRsd = 2000,
            amountPaidRsd = 1000
        )

        assertEquals(8000, result.totalPriceRsd)
        assertEquals(5000, result.remainingDebtRsd)
    }

    @Test
    fun remainingDebtNeverGoesBelowZero() {
        val result = BookingTotals.calculate(
            lines = listOf(BookingLineInput(quantity = 1, unitPrice = 1000)),
            advancePaidRsd = 1000,
            amountPaidRsd = 500
        )

        assertEquals(0, result.remainingDebtRsd)
    }

    @Test
    fun multipliesLineTotalByRentalDays() {
        val result = BookingTotals.calculate(
            lines = listOf(BookingLineInput(quantity = 10, unitPrice = 500)),
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            rentalDays = 3
        )

        assertEquals(15000, result.totalPriceRsd)
    }

    @Test
    fun zeroOrNegativeRentalDaysFallsBackToSingleDay() {
        val result = BookingTotals.calculate(
            lines = listOf(BookingLineInput(quantity = 2, unitPrice = 1000)),
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            rentalDays = 0
        )

        assertEquals(2000, result.totalPriceRsd)
    }

    @Test
    fun perEventLineIgnoresRentalDays() {
        val result = BookingTotals.calculate(
            lines = listOf(
                BookingLineInput(
                    quantity = 1,
                    unitPrice = 200,
                    currency = PriceCurrency.EUR,
                    priceUnit = PriceUnit.PER_EVENT
                )
            ),
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            rentalDays = 5,
            eurToRsdRate = 117.0
        )

        assertEquals(23400, result.totalPriceRsd)
    }

    @Test
    fun perDayEurLineIsConvertedAndMultiplied() {
        val result = BookingTotals.calculate(
            lines = listOf(
                BookingLineInput(
                    quantity = 2,
                    unitPrice = 15,
                    currency = PriceCurrency.EUR,
                    priceUnit = PriceUnit.PER_DAY
                )
            ),
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            rentalDays = 3,
            eurToRsdRate = 117.5
        )

        // 15 EUR -> 1763 RSD (zaokruženo) * 2 kom * 3 dana
        assertEquals(10578, result.totalPriceRsd)
    }

    @Test
    fun mixesRsdAndEurLinesInOneTotal() {
        val result = BookingTotals.calculate(
            lines = listOf(
                // Šatra 6x12 komplet: 300 EUR paušalno
                BookingLineInput(
                    quantity = 1,
                    unitPrice = 300,
                    currency = PriceCurrency.EUR,
                    priceUnit = PriceUnit.PER_EVENT
                ),
                // 100 stolica po 100 din za celo veselje
                BookingLineInput(
                    quantity = 100,
                    unitPrice = 100,
                    currency = PriceCurrency.RSD,
                    priceUnit = PriceUnit.PER_EVENT
                ),
                // 4 barska stola po 1000 din dnevno
                BookingLineInput(
                    quantity = 4,
                    unitPrice = 1000,
                    currency = PriceCurrency.RSD,
                    priceUnit = PriceUnit.PER_DAY
                )
            ),
            advancePaidRsd = 10000,
            amountPaidRsd = 0,
            rentalDays = 2,
            eurToRsdRate = 117.0
        )

        assertEquals(35100 + 10000 + 8000, result.totalPriceRsd)
        assertEquals(43100, result.remainingDebtRsd)
    }

    @Test
    fun hugeQuantityAndPriceClampInsteadOfOverflowingToNegative() {
        val result = BookingTotals.calculate(
            lines = listOf(BookingLineInput(quantity = 99_999, unitPrice = 9_999_999)),
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            rentalDays = 30
        )

        assertEquals(Int.MAX_VALUE, result.totalPriceRsd)
    }

    @Test
    fun invalidRateFallsBackToDefault() {
        val result = BookingTotals.calculate(
            lines = listOf(
                BookingLineInput(
                    quantity = 1,
                    unitPrice = 10,
                    currency = PriceCurrency.EUR,
                    priceUnit = PriceUnit.PER_EVENT
                )
            ),
            advancePaidRsd = 0,
            amountPaidRsd = 0,
            eurToRsdRate = 0.0
        )

        assertEquals(1170, result.totalPriceRsd)
    }
}
