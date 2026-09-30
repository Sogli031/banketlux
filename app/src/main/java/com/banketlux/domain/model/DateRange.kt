package com.banketlux.domain.model

import java.time.LocalDate

data class DateRange(val start: LocalDate, val end: LocalDate) {
    fun overlaps(other: DateRange): Boolean = start <= other.end && other.start <= end

    fun isValid(): Boolean = start <= end
}
