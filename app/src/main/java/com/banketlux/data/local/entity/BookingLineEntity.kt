package com.banketlux.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.banketlux.domain.model.CurrencyConverter
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import com.banketlux.domain.model.TableclothWashing

@Entity(
    tableName = "booking_lines",
    foreignKeys = [
        ForeignKey(
            entity = BookingEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookingId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookingId"), Index("equipmentItemId")]
)
data class BookingLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookingId: Long,
    val equipmentItemId: Long,
    val displayNameSnapshot: String,
    val quantity: Int,
    // Kolona je istorijski nazvana `unitPriceSnapshotRsd`; od šeme v2 iznos je u valuti iz `currency`.
    @ColumnInfo(name = "unitPriceSnapshotRsd") val unitPriceSnapshot: Int,
    val lineTotalRsd: Int,
    val notes: String,
    val currency: PriceCurrency = PriceCurrency.RSD,
    val priceUnit: PriceUnit = PriceUnit.PER_DAY,
    // Kurs korišćen pri čuvanju, da stari obračun ostane nepromenjen kad se kurs promeni.
    val eurRateSnapshot: Double = CurrencyConverter.DEFAULT_EUR_TO_RSD_RATE
)

/** Tekst stavke za liste i kalendar; usluga (pranje stolnjaka) nema količinu. */
fun BookingLineEntity.label(): String =
    if (equipmentItemId == TableclothWashing.EQUIPMENT_ID) {
        displayNameSnapshot
    } else {
        "$displayNameSnapshot x$quantity"
    }
