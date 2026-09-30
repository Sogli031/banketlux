package com.banketlux.domain.model

/** Da li se cena naplaćuje po danu iznajmljivanja ili paušalno za celo veselje. */
enum class PriceUnit {
    PER_DAY,
    PER_EVENT;

    val label: String
        get() = when (this) {
            PER_DAY -> "po danu"
            PER_EVENT -> "za celo veselje"
        }

    companion object {
        fun fromStorage(value: String?): PriceUnit =
            entries.firstOrNull { it.name == value } ?: PER_DAY
    }
}

/** Valuta u kojoj je cena stavke uneta. Zbirovi se uvek prikazuju u dinarima. */
enum class PriceCurrency {
    RSD,
    EUR;

    val label: String
        get() = when (this) {
            RSD -> "RSD"
            EUR -> "EUR"
        }

    companion object {
        fun fromStorage(value: String?): PriceCurrency =
            entries.firstOrNull { it.name == value } ?: RSD
    }
}

/**
 * Pretvara iznos iz valute stavke u dinare.
 * `eurToRsdRate` je kurs sačuvan u podešavanjima (poslednji preuzeti ili ručno unet).
 */
object CurrencyConverter {
    const val DEFAULT_EUR_TO_RSD_RATE: Double = 117.0

    fun toRsd(amount: Int, currency: PriceCurrency, eurToRsdRate: Double): Int = when (currency) {
        PriceCurrency.RSD -> amount
        PriceCurrency.EUR -> Math.round(amount * safeRate(eurToRsdRate)).toInt()
    }

    fun safeRate(rate: Double): Double =
        if (rate.isFinite() && rate > 0.0) rate else DEFAULT_EUR_TO_RSD_RATE
}
