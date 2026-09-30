package com.banketlux.ui.components

/**
 * Redosled kategorija opreme po tome šta se prvo bira u praksi — šatra je početak
 * svakog dogovora. Kategorije koje nisu na spisku idu na kraj, azbučno.
 * Isti redosled koristi i katalog opreme i izbornik pri zakazivanju.
 */
val CATEGORY_ORDER: List<String> = listOf(
    "Šatre",
    "Rashladna oprema",
    "Stolice",
    "Stolovi"
)

const val FALLBACK_CATEGORY = "Ostalo"

fun categoryRank(category: String): Int {
    val index = CATEGORY_ORDER.indexOfFirst { it.equals(category, ignoreCase = true) }
    return if (index >= 0) index else CATEGORY_ORDER.size
}

/**
 * Grupiše stavke po kategoriji u dogovorenom redosledu; unutar grupe sortira po nazivu.
 * Prazna kategorija pada na [FALLBACK_CATEGORY].
 */
fun <T> groupByCategoryOrder(
    items: List<T>,
    category: (T) -> String,
    name: (T) -> String
): List<Pair<String, List<T>>> = items
    .groupBy { category(it).trim().ifBlank { FALLBACK_CATEGORY } }
    .entries
    .sortedWith(
        compareBy<Map.Entry<String, List<T>>> { categoryRank(it.key) }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.key }
    )
    .map { (categoryName, grouped) ->
        categoryName to grouped.sortedBy { name(it).lowercase() }
    }
