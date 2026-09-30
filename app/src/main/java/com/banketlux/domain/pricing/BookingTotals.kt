package com.banketlux.domain.pricing

import com.banketlux.domain.model.CurrencyConverter
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit

object BookingTotals {
    fun calculate(
        lines: List<BookingLineInput>,
        advancePaidRsd: Int,
        amountPaidRsd: Int,
        rentalDays: Int = 1,
        eurToRsdRate: Double = CurrencyConverter.DEFAULT_EUR_TO_RSD_RATE
    ): BookingTotalsResult {
        val days = rentalDays.coerceAtLeast(1)
        val total = lines.sumOf { lineTotalRsd(it, days, eurToRsdRate).toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return BookingTotalsResult(
            totalPriceRsd = total,
            remainingDebtRsd = (total - advancePaidRsd - amountPaidRsd).coerceAtLeast(0)
        )
    }

    /**
     * Ukupno za jednu stavku, u dinarima.
     * Po danu se množi brojem dana; paušal za celo veselje se ne množi.
     */
    fun lineTotalRsd(
        line: BookingLineInput,
        rentalDays: Int,
        eurToRsdRate: Double
    ): Int {
        val days = if (line.priceUnit == PriceUnit.PER_DAY) rentalDays.coerceAtLeast(1) else 1
        val unitPriceRsd = CurrencyConverter.toRsd(line.unitPrice, line.currency, eurToRsdRate)
        // Long i ograničenje: ogromna količina ili cena ne sme da prebaci Int u negativan iznos.
        return (line.quantity.toLong() * unitPriceRsd.toLong() * days.toLong())
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }
}

data class BookingLineInput(
    val quantity: Int,
    val unitPrice: Int,
    val currency: PriceCurrency = PriceCurrency.RSD,
    val priceUnit: PriceUnit = PriceUnit.PER_DAY
)

data class BookingTotalsResult(val totalPriceRsd: Int, val remainingDebtRsd: Int)
