package com.banketlux.data.repository

import com.banketlux.data.local.dao.BookingDao
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import kotlinx.coroutines.flow.Flow

class BookingRepository(private val dao: BookingDao) {
    fun observeBookingsForEventDate(eventDate: String): Flow<List<BookingWithLines>> =
        dao.observeBookingsForEventDate(eventDate)

    fun observeUpcomingBookings(today: String): Flow<List<BookingWithLines>> =
        dao.observeUpcomingBookings(today)

    suspend fun getBookingsOverlappingRange(
        startDate: String,
        endDate: String,
        currentBookingId: Long? = null
    ): List<BookingWithLines> = dao.getBookingsOverlappingRange(
        startDate = startDate,
        endDate = endDate,
        currentBookingId = currentBookingId
    )

    suspend fun saveBookingWithLines(
        booking: BookingEntity,
        lines: List<BookingLineEntity>
    ): Long = dao.upsertBookingWithLines(booking, lines)

    suspend fun getBookingById(bookingId: Long): BookingWithLines? = dao.getBookingWithLinesById(bookingId)

    suspend fun getAllBookingsWithLines(): List<BookingWithLines> = dao.getAllBookingsWithLines()

    suspend fun deleteBooking(bookingId: Long) = dao.deleteBooking(bookingId)

    suspend fun updateCalendarEventId(bookingId: Long, eventId: String?) =
        dao.updateCalendarEventId(bookingId, eventId)
}
