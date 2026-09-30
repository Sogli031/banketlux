package com.banketlux.data.repository

import com.banketlux.data.local.entity.label
import androidx.room.withTransaction
import com.banketlux.data.local.BanketLuxDatabase
import com.banketlux.data.local.dao.BookingDao
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.local.dao.EarningDao
import com.banketlux.data.local.entity.EarningEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Spisak opreme koji se pamti uz arhivirani posao, jedna stavka po redu. */
internal fun equipmentSummaryOf(bookingWithLines: BookingWithLines): String =
    bookingWithLines.lines.joinToString(separator = "\n") { it.label() }

internal fun toEarning(
    bookingWithLines: BookingWithLines,
    archivedAtEpochMillis: Long
): EarningEntity {
    val booking = bookingWithLines.booking
    return EarningEntity(
        id = 0,
        bookingId = booking.id,
        customerName = booking.customerName,
        customerPhone = booking.customerPhone,
        location = booking.location,
        eventDate = booking.eventDate,
        rentalStartDate = booking.rentalStartDate,
        rentalEndDate = booking.rentalEndDate,
        equipmentSummary = equipmentSummaryOf(bookingWithLines),
        // Iznos je snimljen u trenutku posla i ne menja se kad se izmeni cenovnik.
        amountRsd = booking.totalPriceRsd,
        status = booking.status,
        notes = booking.notes,
        archivedAtEpochMillis = archivedAtEpochMillis
    )
}

/**
 * Da li arhivirani zapis opisuje ISTI posao kao kandidat. Sam `bookingId` nije dovoljan:
 * posle vraćanja starog backup-a novo zakazivanje je moglo da dobije id već arhiviranog
 * posla, pa poklapanje id-a ne znači da je posao već upisan u zaradu.
 */
internal fun isSameJob(existing: EarningEntity, candidate: EarningEntity): Boolean =
    existing.bookingId == candidate.bookingId &&
        existing.customerName == candidate.customerName &&
        existing.eventDate == candidate.eventDate &&
        existing.rentalStartDate == candidate.rentalStartDate &&
        existing.rentalEndDate == candidate.rentalEndDate

class EarningsRepository(
    private val database: BanketLuxDatabase,
    private val bookingDao: BookingDao,
    private val earningDao: EarningDao
) {
    fun observeEarnings(): Flow<List<EarningEntity>> = earningDao.observeEarnings()

    /** Briše posao koji je greškom ušao u zaradu. */
    suspend fun deleteEarning(id: Long) = earningDao.deleteById(id)

    /**
     * Prebacuje zakazivanja kojima je najam prošao u arhivu zarade i sklanja ih sa liste.
     * Radi u jednoj transakciji, pa zakazivanje ne može nestati bez upisane zarade.
     * Vraća broj arhiviranih poslova.
     */
    suspend fun archiveEndedBookings(today: LocalDate = LocalDate.now()): Int {
        val ended = bookingDao.getBookingsEndedBefore(today.toString())
        if (ended.isEmpty()) return 0

        val now = System.currentTimeMillis()
        var archived = 0
        database.withTransaction {
            ended.forEach { bookingWithLines ->
                // Zaštita od duplog upisa ako se arhiviranje pokrene dvaput — ali SAMO za isti
                // posao. Tuđi zapis sa istim bookingId (sudar id-a) ne sme da spreči upis:
                // zakazivanje se briše tek kad je njegova zarada sigurno u arhivi.
                val candidate = toEarning(bookingWithLines, now)
                val alreadyArchived = earningDao.getForBooking(bookingWithLines.booking.id)
                    .any { isSameJob(it, candidate) }
                if (!alreadyArchived) {
                    earningDao.insertAll(listOf(candidate))
                    archived++
                }
                bookingDao.deleteBooking(bookingWithLines.booking.id)
            }
        }
        return archived
    }
}
