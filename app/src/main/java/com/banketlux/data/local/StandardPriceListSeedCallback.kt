package com.banketlux.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.banketlux.data.catalog.StandardPriceList

/**
 * Puni katalog opreme standardnim cenovnikom pri prvom kreiranju baze,
 * tj. odmah po instalaciji. Postojeće instalacije ne dira — njihova baza
 * je već kreirana, pa se `onCreate` više ne poziva.
 */
object StandardPriceListSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        StandardPriceList.items().forEach { item ->
            db.execSQL(
                """
                INSERT INTO equipment
                    (name, category, totalQuantity, defaultUnitPriceRsd, notes, active, currency, priceUnit)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """.trimIndent(),
                arrayOf<Any>(
                    item.name,
                    item.category,
                    item.totalQuantity,
                    item.defaultUnitPrice,
                    item.notes,
                    item.currency.name,
                    item.priceUnit.name
                )
            )
        }
    }
}
