package com.banketlux.domain.model

data class Money(val rsd: Int) {
    operator fun plus(other: Money): Money = Money(rsd + other.rsd)

    operator fun minus(other: Money): Money = Money(rsd - other.rsd)

    fun coerceAtLeastZero(): Money = Money(rsd.coerceAtLeast(0))
}
