package com.banketlux.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RetentionPolicyTest {
    private val zone: ZoneId = ZoneId.of("Europe/Belgrade")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun toDelete(versions: List<Long>, now: Long): List<Long> =
        RetentionPolicy.selectToDelete(versions, now, zone) { it }

    @Test
    fun tenQuickEditsDoNotRotateOutYesterdaysGoodBackup() {
        val goodYesterday = at(2026, 9, 19, 20)
        // Danas 15 verzija u razmaku od 10 minuta (auto-backup posle svake izmene).
        val today = (0 until 15).map { at(2026, 9, 20, 8, it * 4) }
        val now = at(2026, 9, 20, 10)

        val deleted = toDelete(today + goodYesterday, now)

        assertFalse("jučerašnja verzija mora da ostane", goodYesterday in deleted)
        // Od današnjih ostaje 10 najnovijih; 5 najstarijih današnjih se briše.
        assertEquals(today.sorted().take(5), deleted.sorted())
    }

    @Test
    fun keepsOnePerDayForTwoWeeksAndOnePerMonthForAYear() {
        val now = at(2026, 9, 20)
        val daily = (0L until 30L).map { at(2026, 9, 20) - it * 24 * 60 * 60 * 1000 }
        val monthly = listOf(at(2026, 3, 5), at(2026, 3, 25), at(2025, 11, 1), at(2025, 8, 1))

        val deleted = toDelete(daily + monthly, now)

        // Poslednjih 14 dana: sve ostaje (po jedna verzija dnevno).
        daily.take(14).forEach { assertFalse(it in deleted) }
        // Mart 2026: ostaje samo najnovija verzija tog meseca.
        assertTrue(at(2026, 3, 5) in deleted)
        assertFalse(at(2026, 3, 25) in deleted)
        // Novembar 2025 je unutar 12 meseci, avgust 2025 nije.
        assertFalse(at(2025, 11, 1) in deleted)
        assertTrue(at(2025, 8, 1) in deleted)
    }

    @Test
    fun versionsWithUnknownTimeAreNeverDeleted() {
        val now = at(2026, 9, 20)
        val deleted = toDelete(listOf(0L, 0L) + (0 until 12).map { at(2024, 1, 1, 0, it) }, now)
        assertFalse(0L in deleted)
    }
}
