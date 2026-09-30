package com.banketlux.domain

import com.banketlux.ui.components.categoryRank
import com.banketlux.ui.components.groupByCategoryOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private data class Row(val name: String, val category: String)

class CategoryOrderTest {
    private fun group(rows: List<Row>) =
        groupByCategoryOrder(rows, category = { it.category }, name = { it.name })

    @Test
    fun knownCategoriesKeepTheAgreedOrder() {
        val rows = listOf(
            Row("Barski sto", "Stolovi"),
            Row("Stolica", "Stolice"),
            Row("Šatra 8x5 m", "Šatre"),
            Row("Hladnjača", "Rashladna oprema")
        )

        assertEquals(
            listOf("Šatre", "Rashladna oprema", "Stolice", "Stolovi"),
            group(rows).map { it.first }
        )
    }

    @Test
    fun unknownCategoriesFollowKnownOnesAlphabetically() {
        val rows = listOf(
            Row("Agregat", "Ostalo"),
            Row("Paviljon", "Paviljoni"),
            Row("Šatra", "Šatre"),
            Row("Bina", "Bine")
        )

        assertEquals(
            listOf("Šatre", "Bine", "Ostalo", "Paviljoni"),
            group(rows).map { it.first }
        )
    }

    @Test
    fun blankCategoryBecomesOstalo() {
        assertEquals("Ostalo", group(listOf(Row("Nešto", "   "))).single().first)
    }

    @Test
    fun categoryMatchIgnoresCase() {
        assertEquals(categoryRank("Šatre"), categoryRank("ŠATRE"))
        assertTrue(categoryRank("Šatre") < categoryRank("Stolovi"))
        assertTrue(categoryRank("Stolovi") < categoryRank("Nepoznato"))
    }

    @Test
    fun itemsAreSortedByNameInsideGroup() {
        val rows = listOf(
            Row("Šatra 8x5 m", "Šatre"),
            Row("Šatra 6x12 m", "Šatre")
        )

        assertEquals(
            listOf("Šatra 6x12 m", "Šatra 8x5 m"),
            group(rows).single().second.map { it.name }
        )
    }
}
