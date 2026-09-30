package com.banketlux.data.backup

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Koje verzije backup-a ostaju u oblaku. Samo "poslednjih 10" nije dovoljno: svaka izmena
 * pravi novu verziju, pa bi deset brzih izmena izbacilo poslednji dobar backup.
 *
 * Čuva se:
 *  - [KEEP_NEWEST] najnovijih verzija,
 *  - najnovija verzija svakog dana, za poslednjih [KEEP_DAILY_DAYS] dana,
 *  - najnovija verzija svakog meseca, za poslednjih [KEEP_MONTHLY_MONTHS] meseci.
 * Verzije bez poznatog vremena se nikad ne brišu.
 */
object RetentionPolicy {
    const val KEEP_NEWEST = 10
    const val KEEP_DAILY_DAYS = 14L
    const val KEEP_MONTHLY_MONTHS = 12L

    fun <T> selectToDelete(
        versions: List<T>,
        nowMillis: Long,
        zone: ZoneId,
        timestampOf: (T) -> Long
    ): List<T> {
        val dated = versions.filter { timestampOf(it) > 0L }.sortedByDescending(timestampOf)
        val keep = HashSet<T>()

        keep.addAll(dated.take(KEEP_NEWEST))

        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val oldestDay = today.minusDays(KEEP_DAILY_DAYS - 1)
        val oldestMonth = YearMonth.from(today).minusMonths(KEEP_MONTHLY_MONTHS - 1)

        // Lista je sortirana od najnovije, pa je prvi element grupe najnovija verzija tog dana/meseca.
        dated.groupBy { Instant.ofEpochMilli(timestampOf(it)).atZone(zone).toLocalDate() }
            .forEach { (day, items) -> if (!day.isBefore(oldestDay)) keep.add(items.first()) }

        dated.groupBy { YearMonth.from(Instant.ofEpochMilli(timestampOf(it)).atZone(zone)) }
            .forEach { (month, items) -> if (!month.isBefore(oldestMonth)) keep.add(items.first()) }

        return dated.filter { it !in keep }
    }
}
