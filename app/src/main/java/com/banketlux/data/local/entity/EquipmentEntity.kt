package com.banketlux.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit

@Entity(tableName = "equipment")
data class EquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val totalQuantity: Int,
    // Kolona je istorijski nazvana `defaultUnitPriceRsd`; od šeme v2 iznos je u valuti iz `currency`.
    @ColumnInfo(name = "defaultUnitPriceRsd") val defaultUnitPrice: Int,
    val notes: String,
    val active: Boolean = true,
    val currency: PriceCurrency = PriceCurrency.RSD,
    val priceUnit: PriceUnit = PriceUnit.PER_DAY
)
