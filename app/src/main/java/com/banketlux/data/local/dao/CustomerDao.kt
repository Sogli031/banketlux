package com.banketlux.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

data class CustomerWithBookingSummaryRow(
    @Embedded val customer: CustomerEntity,
    val lastBookingDate: String?,
    val bookingCount: Long
)

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name COLLATE NOCASE")
    fun observeAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY id")
    suspend fun getAllForBackup(): List<CustomerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: CustomerEntity): Long

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<CustomerEntity>)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    @Query(
        """
        SELECT c.*, MAX(b.eventDate) AS lastBookingDate, COUNT(b.id) AS bookingCount
        FROM customers c
        LEFT JOIN bookings b ON b.customerPhone = c.phone
        WHERE (
            :normalizedQuery = '' OR
            LOWER(c.name) LIKE '%' || :normalizedQuery || '%' OR
            c.phone LIKE '%' || :normalizedQuery || '%'
        )
        GROUP BY c.id, c.name, c.phone, c.notes
        ORDER BY c.name COLLATE NOCASE
        """
    )
    fun searchCustomers(normalizedQuery: String): Flow<List<CustomerWithBookingSummaryRow>>

    @Query(
        """
        SELECT * FROM bookings
        WHERE customerPhone = :phone
        ORDER BY eventDate DESC, createdAtEpochMillis DESC
        """
    )
    fun observeBookingsByCustomerPhone(phone: String): Flow<List<BookingEntity>>
}
