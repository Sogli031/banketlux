package com.banketlux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banketlux.data.local.entity.EarningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EarningDao {
    @Query("SELECT * FROM earnings ORDER BY rentalEndDate DESC, id DESC")
    fun observeEarnings(): Flow<List<EarningEntity>>

    @Query("SELECT * FROM earnings ORDER BY id")
    suspend fun getAllForBackup(): List<EarningEntity>

    @Query("SELECT COUNT(*) FROM earnings WHERE bookingId = :bookingId")
    suspend fun countForBooking(bookingId: Long): Int

    @Query("SELECT * FROM earnings WHERE bookingId = :bookingId")
    suspend fun getForBooking(bookingId: Long): List<EarningEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<EarningEntity>)

    @Query("DELETE FROM earnings")
    suspend fun deleteAll()

    @Query("DELETE FROM earnings WHERE id = :id")
    suspend fun deleteById(id: Long)
}
