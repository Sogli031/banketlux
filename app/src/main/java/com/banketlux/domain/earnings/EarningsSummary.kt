package com.banketlux.domain.earnings

import com.banketlux.data.local.entity.EarningEntity
import java.time.LocalDate

private val MONTH_NAMES = listOf(
    "Januar", "Februar", "Mart", "April", "Maj", "Jun",
    "Jul", "Avgust", "Septembar", "Oktobar", "Novembar", "Decembar"
)

fun monthName(month: Int): String = MONTH_NAMES.getOrElse(month - 1) { "Mesec $month" }

data class MonthEarnings(
    val year: Int,
    val month: Int,
    val amountRsd: Int,
    val jobCount: Int,
    /** Pojedinačni poslovi u mesecu, najnoviji prvi. */
    val entries: List<EarningEntity> = emptyList()
) {
    val label: String get() = monthName(month)
}

data class YearEarnings(
    val year: Int,
    val amountRsd: Int,
    val jobCount: Int,
    val months: List<MonthEarnings>
)

data class EarningsOverview(
    val totalRsd: Int,
    val jobCount: Int,
    val years: List<YearEarnings>,
    /** Poslovi čiji se datum ne može pročitati: u ukupnom zbiru su, ali ne u nijednoj godini. */
    val undatedRsd: Int = 0,
    val undatedCount: Int = 0
)

/**
 * Grupiše arhivirane poslove po godini i mesecu završetka najma.
 * Novije prvo — najskorija godina i mesec su na vrhu.
 * Otkazana zakazivanja se preskaču (vidi [EarningEntity.countsAsEarned]).
 * Unos čiji datum ne može da se pročita (npr. ručno menjan backup) ne može
 * u mesečni prikaz, ali njegov iznos ipak ulazi u ukupan zbir.
 */
fun summarizeEarnings(entries: List<EarningEntity>): EarningsOverview {
    val earned = entries.filter { it.countsAsEarned }
    val dated = earned
        .mapNotNull { entry ->
            val date = parseDateOrNull(entry.rentalEndDate)
                ?: parseDateOrNull(entry.eventDate)
                ?: return@mapNotNull null
            date to entry
        }
    val undatedCount = earned.size - dated.size
    val undatedAmount = earned.sumOf { it.amountRsd } - dated.sumOf { (_, entry) -> entry.amountRsd }

    val years = dated
        .groupBy { (date, _) -> date.year }
        .map { (year, yearRows) ->
            val months = yearRows
                .groupBy { (date, _) -> date.monthValue }
                .map { (month, monthRows) ->
                    MonthEarnings(
                        year = year,
                        month = month,
                        amountRsd = monthRows.sumOf { (_, entry) -> entry.amountRsd },
                        jobCount = monthRows.size,
                        entries = monthRows
                            .sortedWith(
                                compareByDescending<Pair<LocalDate, EarningEntity>> { it.first }
                                    .thenByDescending { it.second.id }
                            )
                            .map { (_, entry) -> entry }
                    )
                }
                .sortedByDescending { it.month }

            YearEarnings(
                year = year,
                amountRsd = months.sumOf { it.amountRsd },
                jobCount = months.sumOf { it.jobCount },
                months = months
            )
        }
        .sortedByDescending { it.year }

    return EarningsOverview(
        totalRsd = years.sumOf { it.amountRsd } + undatedAmount,
        jobCount = years.sumOf { it.jobCount } + undatedCount,
        years = years,
        undatedRsd = undatedAmount,
        undatedCount = undatedCount
    )
}

private fun parseDateOrNull(value: String): LocalDate? =
    runCatching { LocalDate.parse(value) }.getOrNull()
