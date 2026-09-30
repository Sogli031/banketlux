package com.banketlux.data.local

import androidx.room.TypeConverter
import com.banketlux.domain.model.BookingStatus
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit

class Converters {
    @TypeConverter
    fun bookingStatusToString(status: BookingStatus): String = status.name

    @TypeConverter
    fun bookingStatusFromString(value: String): BookingStatus = BookingStatus.valueOf(value)

    @TypeConverter
    fun priceCurrencyToString(currency: PriceCurrency): String = currency.name

    @TypeConverter
    fun priceCurrencyFromString(value: String?): PriceCurrency = PriceCurrency.fromStorage(value)

    @TypeConverter
    fun priceUnitToString(unit: PriceUnit): String = unit.name

    @TypeConverter
    fun priceUnitFromString(value: String?): PriceUnit = PriceUnit.fromStorage(value)
}
