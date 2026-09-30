package com.banketlux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banketlux.data.local.entity.EquipmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment WHERE active = 1 ORDER BY category, name")
    fun observeActiveEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment ORDER BY active DESC, category, name")
    fun observeAllEquipment(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment ORDER BY id")
    suspend fun getAllForBackup(): List<EquipmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: EquipmentEntity): Long

    @Query("UPDATE equipment SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)

    @Query("UPDATE equipment SET active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("SELECT * FROM equipment WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EquipmentEntity>)

    @Query("DELETE FROM equipment")
    suspend fun deleteAll()
}
