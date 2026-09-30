package com.banketlux.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.banketlux.domain.model.CurrencyConverter

@Entity(tableName = "business_settings")
data class BusinessSettingsEntity(
    @PrimaryKey val id: Long = 1,
    val businessName: String,
    val phone: String,
    val eurToRsdRate: Double = CurrencyConverter.DEFAULT_EUR_TO_RSD_RATE,
    val eurRateUpdatedAtEpochMillis: Long = 0,
    // "API" kad je kurs preuzet sa interneta, "MANUAL" kad je ručno unet.
    val eurRateSource: String = "MANUAL"
)
