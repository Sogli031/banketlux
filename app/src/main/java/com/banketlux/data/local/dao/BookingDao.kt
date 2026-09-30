package com.banketlux.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import kotlinx.coroutines.flow.Flow

data class BookingWithLines(
    @Embedded val booking: BookingEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookingId"
    )
    val lines: List<BookingLineEntity>
)

@Dao
interface BookingDao {
    @Transaction
    @Query("SELECT * FROM bookings WHERE eventDate = :eventDate ORDER BY createdAtEpochMillis DESC")
    fun observeBookingsForEventDate(eventDate: String): Flow<List<BookingWithLines>>

    /**
     * Zakazivanja koja još traju ili tek dolaze. Filtrira se po kraju najma, ne po
     * datumu veselja — višednevni najam koji je već počeo mora ostati na listi
     * sve dok se ne završi (tada ga arhiviranje seli u zaradu).
     */
    @Transaction
    @Query(
        """
        SELECT * FROM bookings
        WHERE rentalEndDate >= :today
        ORDER BY eventDate ASC, rentalStartDate ASC, id ASC
        """
    )
    fun observeUpcomingBookings(today: String): Flow<List<BookingWithLines>>

    /** Sva zakazivanja sa stavkama — koristi se za naknadni upis u kalendar. */
    @Transaction
    @Query("SELECT * FROM bookings ORDER BY rentalStartDate ASC")
    suspend fun getAllBookingsWithLines(): List<BookingWithLines>

    /** Zakazivanja čiji je najam prošao — kandidati za arhiviranje u zaradu. */
    @Transaction
    @Query("SELECT * FROM bookings WHERE rentalEndDate < :today ORDER BY rentalEndDate ASC")
    suspend fun getBookingsEndedBefore(today: String): List<BookingWithLines>

    @Query("SELECT * FROM bookings ORDER BY id")
    suspend fun getAllBookingsForBackup(): List<BookingEntity>

    @Query("SELECT * FROM booking_lines ORDER BY id")
    suspend fun getAllBookingLinesForBackup(): List<BookingLineEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM bookings
        WHERE rentalStartDate <= :endDate
          AND :startDate <= rentalEndDate
          AND status IN ('CONFIRMED', 'COMPLETED')
          AND (:currentBookingId IS NULL OR id != :currentBookingId)
        ORDER BY rentalStartDate ASC
        """
    )
    suspend fun getBookingsOverlappingRange(
        startDate: String,
        endDate: String,
        currentBookingId: Long? = null
    ): List<BookingWithLines>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooking(booking: BookingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookingLines(lines: List<BookingLineEntity>)

    @Query("DELETE FROM booking_lines WHERE bookingId = :bookingId")
    suspend fun deleteBookingLinesForBooking(bookingId: Long)

    @Query("DELETE FROM bookings WHERE id = :bookingId")
    suspend fun deleteBooking(bookingId: Long)

    @Query("UPDATE bookings SET calendarEventId = :eventId WHERE id = :bookingId")
    suspend fun updateCalendarEventId(bookingId: Long, eventId: String?)

    @Query("DELETE FROM booking_lines")
    suspend fun deleteAllBookingLines()

    @Query("DELETE FROM bookings")
    suspend fun deleteAllBookings()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookings(bookings: List<BookingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookingLines(lines: List<BookingLineEntity>)

    @Transaction
    @Query("SELECT * FROM bookings WHERE id = :bookingId LIMIT 1")
    suspend fun getBookingWithLinesById(bookingId: Long): BookingWithLines?

    @Transaction
    suspend fun upsertBookingWithLines(
        booking: BookingEntity,
        lines: List<BookingLineEntity>
    ): Long {
        val insertedId = upsertBooking(booking)
        val persistedBookingId = if (booking.id == 0L) insertedId else booking.id
        deleteBookingLinesForBooking(persistedBookingId)
        if (lines.isNotEmpty()) {
            upsertBookingLines(lines.map { it.copy(bookingId = persistedBookingId) })
        }
        return persistedBookingId
    }
}
