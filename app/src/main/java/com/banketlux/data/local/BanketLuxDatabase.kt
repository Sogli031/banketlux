package com.banketlux.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.banketlux.data.local.dao.BookingDao
import com.banketlux.data.local.dao.BusinessSettingsDao
import com.banketlux.data.local.dao.CustomerDao
import com.banketlux.data.local.dao.EarningDao
import com.banketlux.data.local.dao.EquipmentDao
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import com.banketlux.data.local.entity.BusinessSettingsEntity
import com.banketlux.data.local.entity.CustomerEntity
import com.banketlux.data.local.entity.EarningEntity
import com.banketlux.data.local.entity.EquipmentEntity

@Database(
    entities = [
        BookingEntity::class,
        BookingLineEntity::class,
        EquipmentEntity::class,
        CustomerEntity::class,
        BusinessSettingsEntity::class,
        EarningEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class BanketLuxDatabase : RoomDatabase() {
    abstract fun bookingDao(): BookingDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun customerDao(): CustomerDao
    abstract fun businessSettingsDao(): BusinessSettingsDao
    abstract fun earningDao(): EarningDao
}
