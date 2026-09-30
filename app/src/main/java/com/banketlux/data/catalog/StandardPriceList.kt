package com.banketlux.data.catalog

import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit

/**
 * Standardni cenovnik BanketLux-a (stanje 2026-08-10). Unosi se automatski pri prvoj instalaciji.
 * Količine su početne procene — proveri ih i izmeni u ekranu "Oprema".
 * Tamo gde je cena bila raspon, uneta je gornja granica.
 */
object StandardPriceList {

    fun items(): List<EquipmentEntity> = listOf(
        EquipmentEntity(
            name = "Šatra 6x12 m — komplet",
            category = "Šatre",
            totalQuantity = 1,
            defaultUnitPrice = 300,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_EVENT,
            notes = ""
        ),
        EquipmentEntity(
            name = "Šatra 8x5 m",
            category = "Šatre",
            totalQuantity = 1,
            defaultUnitPrice = 200,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_EVENT,
            notes = ""
        ),
        EquipmentEntity(
            name = "Okrugli stolovi sa stolicama (uz veliku šatru)",
            category = "Stolovi",
            totalQuantity = 1,
            defaultUnitPrice = 400,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_EVENT,
            notes = ""
        ),
        EquipmentEntity(
            name = "Okrugli stolovi sa stolicama (uz malu šatru)",
            category = "Stolovi",
            totalQuantity = 1,
            defaultUnitPrice = 280,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_EVENT,
            notes = ""
        ),
        EquipmentEntity(
            name = "Barski sto",
            category = "Stolovi",
            totalQuantity = 10,
            defaultUnitPrice = 1000,
            currency = PriceCurrency.RSD,
            priceUnit = PriceUnit.PER_DAY,
            notes = ""
        ),
        EquipmentEntity(
            name = "Stolica",
            category = "Stolice",
            totalQuantity = 200,
            defaultUnitPrice = 100,
            currency = PriceCurrency.RSD,
            priceUnit = PriceUnit.PER_EVENT,
            notes = ""
        ),
        EquipmentEntity(
            name = "Rashladna vitrina (mala)",
            category = "Rashladna oprema",
            totalQuantity = 1,
            defaultUnitPrice = 15,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_DAY,
            notes = ""
        ),
        EquipmentEntity(
            name = "Rashladna vitrina (velika)",
            category = "Rashladna oprema",
            totalQuantity = 1,
            defaultUnitPrice = 20,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_DAY,
            notes = ""
        ),
        EquipmentEntity(
            name = "Hladnjača — celo veselje",
            category = "Rashladna oprema",
            totalQuantity = 1,
            defaultUnitPrice = 150,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_EVENT,
            notes = ""
        ),
        EquipmentEntity(
            name = "Hladnjača — dnevno",
            category = "Rashladna oprema",
            totalQuantity = 1,
            defaultUnitPrice = 70,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_DAY,
            notes = ""
        ),
        EquipmentEntity(
            name = "Paviljon",
            category = "Paviljoni",
            totalQuantity = 1,
            defaultUnitPrice = 15,
            currency = PriceCurrency.EUR,
            priceUnit = PriceUnit.PER_DAY,
            notes = ""
        )
    )
}
