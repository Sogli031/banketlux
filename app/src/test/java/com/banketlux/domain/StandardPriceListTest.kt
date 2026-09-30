package com.banketlux.domain

import com.banketlux.data.catalog.StandardPriceList
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandardPriceListTest {
    private val items = StandardPriceList.items()

    private fun item(name: String) = items.first { it.name == name }

    @Test
    fun namesAreUniqueSoSeedingIsIdempotent() {
        val names = items.map { it.name.trim().lowercase() }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun allItemsHavePositivePriceAndQuantity() {
        assertTrue(items.all { it.defaultUnitPrice > 0 })
        assertTrue(items.all { it.totalQuantity > 0 })
    }

    @Test
    fun tentsArePerEventInEuros() {
        val big = item("Šatra 6x12 m — komplet")
        assertEquals(300, big.defaultUnitPrice)
        assertEquals(PriceCurrency.EUR, big.currency)
        assertEquals(PriceUnit.PER_EVENT, big.priceUnit)

        val small = item("Šatra 8x5 m")
        assertEquals(200, small.defaultUnitPrice)
        assertEquals(PriceUnit.PER_EVENT, small.priceUnit)
    }

    @Test
    fun barTableIsPerDayInDinars() {
        val barTable = item("Barski sto")
        assertEquals(1000, barTable.defaultUnitPrice)
        assertEquals(PriceCurrency.RSD, barTable.currency)
        assertEquals(PriceUnit.PER_DAY, barTable.priceUnit)
    }

    @Test
    fun chairIsPricedPerPieceForWholeEvent() {
        val chair = item("Stolica")
        assertEquals(100, chair.defaultUnitPrice)
        assertEquals(PriceCurrency.RSD, chair.currency)
        assertEquals(PriceUnit.PER_EVENT, chair.priceUnit)
    }

    @Test
    fun coldStorageHasBothFlatAndDailyEntries() {
        val flat = item("Hladnjača — celo veselje")
        assertEquals(150, flat.defaultUnitPrice)
        assertEquals(PriceUnit.PER_EVENT, flat.priceUnit)

        val daily = item("Hladnjača — dnevno")
        assertEquals(70, daily.defaultUnitPrice)
        assertEquals(PriceUnit.PER_DAY, daily.priceUnit)
    }

    @Test
    fun roundTableSetsMatchTentSize() {
        assertEquals(400, item("Okrugli stolovi sa stolicama (uz veliku šatru)").defaultUnitPrice)
        assertEquals(280, item("Okrugli stolovi sa stolicama (uz malu šatru)").defaultUnitPrice)
    }

    @Test
    fun displayCasesAndPavilionArePerDayInEuros() {
        assertEquals(15, item("Rashladna vitrina (mala)").defaultUnitPrice)
        assertEquals(20, item("Rashladna vitrina (velika)").defaultUnitPrice)
        assertEquals(15, item("Paviljon").defaultUnitPrice)
        listOf("Rashladna vitrina (mala)", "Rashladna vitrina (velika)", "Paviljon").forEach {
            assertEquals(PriceCurrency.EUR, item(it).currency)
            assertEquals(PriceUnit.PER_DAY, item(it).priceUnit)
        }
    }
}
