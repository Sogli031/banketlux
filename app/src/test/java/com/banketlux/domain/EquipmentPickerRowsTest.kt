package com.banketlux.domain

import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.ui.bookings.PickerRow
import com.banketlux.ui.bookings.buildPickerRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipmentPickerRowsTest {
    private fun equipment(id: Long, name: String, category: String) = EquipmentEntity(
        id = id,
        name = name,
        category = category,
        totalQuantity = 1,
        defaultUnitPrice = 100,
        notes = ""
    )

    private val catalog = listOf(
        equipment(1, "Šatra 8x5 m", "Šatre"),
        equipment(2, "Barski sto", "Stolovi"),
        equipment(3, "Šatra 6x12 m — komplet", "Šatre"),
        equipment(4, "Paviljon", "Paviljoni"),
        equipment(5, "Stolica", "Stolice"),
        equipment(6, "Hladnjača", "Rashladna oprema")
    )

    @Test
    fun groupsByCategoryWithHeaderBeforeEachGroup() {
        val rows = buildPickerRows(catalog, query = "")

        // 5 kategorija + 6 stavki
        assertEquals(11, rows.size)
        assertTrue(rows.first() is PickerRow.Header)
    }

    @Test
    fun tentsComeFirstThenColdStorageChairsAndTables() {
        val headers = buildPickerRows(catalog, query = "")
            .filterIsInstance<PickerRow.Header>()
            .map { it.category }

        assertEquals(
            listOf("Šatre", "Rashladna oprema", "Stolice", "Stolovi", "Paviljoni"),
            headers
        )
    }

    @Test
    fun unlistedCategoriesGoToTheEndAlphabetically() {
        val extras = catalog + listOf(
            equipment(7, "Bina", "Bine"),
            equipment(8, "Agregat", "Ostalo")
        )
        val headers = buildPickerRows(extras, query = "")
            .filterIsInstance<PickerRow.Header>()
            .map { it.category }

        assertEquals(
            listOf("Šatre", "Rashladna oprema", "Stolice", "Stolovi", "Bine", "Ostalo", "Paviljoni"),
            headers
        )
    }

    @Test
    fun sortsItemsAlphabeticallyInsideGroup() {
        val rows = buildPickerRows(catalog, query = "šatra")
        val names = rows.filterIsInstance<PickerRow.Item>().map { it.equipment.name }

        assertEquals(listOf("Šatra 6x12 m — komplet", "Šatra 8x5 m"), names)
    }

    @Test
    fun searchMatchesCategoryToo() {
        val rows = buildPickerRows(catalog, query = "stolovi")
        val names = rows.filterIsInstance<PickerRow.Item>().map { it.equipment.name }

        assertEquals(listOf("Barski sto"), names)
    }

    @Test
    fun searchIsCaseInsensitiveAndTrimmed() {
        val rows = buildPickerRows(catalog, query = "  PAVILJON  ")
        val names = rows.filterIsInstance<PickerRow.Item>().map { it.equipment.name }

        assertEquals(listOf("Paviljon"), names)
    }

    @Test
    fun noMatchReturnsNoRowsAtAll() {
        assertEquals(emptyList<PickerRow>(), buildPickerRows(catalog, query = "traktor"))
    }

    @Test
    fun blankCategoryFallsBackToOstalo() {
        val rows = buildPickerRows(listOf(equipment(9, "Nešto", "  ")), query = "")

        assertEquals("Ostalo", (rows.first() as PickerRow.Header).category)
    }
}
