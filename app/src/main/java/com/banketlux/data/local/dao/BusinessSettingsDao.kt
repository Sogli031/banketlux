package com.banketlux.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.banketlux.data.local.entity.BusinessSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessSettingsDao {
    @Query("SELECT * FROM business_settings ORDER BY id LIMIT 1")
    suspend fun getSettings(): BusinessSettingsEntity?

    @Query("SELECT * FROM business_settings ORDER BY id LIMIT 1")
    fun observeSettings(): Flow<BusinessSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: BusinessSettingsEntity)

    @Query("DELETE FROM business_settings")
    suspend fun deleteAll()
}
