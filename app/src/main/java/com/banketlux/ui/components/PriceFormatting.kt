package com.banketlux.ui.components

import com.banketlux.domain.model.CurrencyConverter
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit

/** "300 EUR za celo veselje" */
fun formatUnitPrice(amount: Int, currency: PriceCurrency, priceUnit: PriceUnit): String =
    "$amount ${currency.label} ${priceUnit.label}"

/** "300 EUR (≈ 35.199 RSD)" — dinarski protivvrednost samo kad je stavka u evrima. */
fun formatPriceWithRsd(amount: Int, currency: PriceCurrency, eurToRsdRate: Double): String =
    if (currency == PriceCurrency.RSD) {
        "$amount RSD"
    } else {
        val rsd = CurrencyConverter.toRsd(amount, currency, eurToRsdRate)
        "$amount EUR (≈ ${formatRsd(rsd)})"
    }

/** "35.199 RSD" */
fun formatRsd(amount: Int): String {
    val digits = amount.toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    val sign = if (amount < 0) "-" else ""
    return "$sign$grouped RSD"
}
