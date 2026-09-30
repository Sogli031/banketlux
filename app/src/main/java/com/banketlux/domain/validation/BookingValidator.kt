package com.banketlux.domain.validation

import com.banketlux.domain.model.DateRange
import java.time.LocalDate

data class ValidationError(val field: String, val message: String)

data class BookingDraft(
    val customerName: String,
    val customerPhone: String,
    val eventDate: LocalDate,
    val rentalRange: DateRange
)

object BookingValidator {
    fun validate(draft: BookingDraft): List<ValidationError> = buildList {
        if (draft.customerName.isBlank()) add(
            ValidationError("customerName", "Unesi ime mušterije.")
        )
        if (!draft.rentalRange.isValid()) add(
            ValidationError("rentalEndDate", "Kraj iznajmljivanja ne može biti pre početka.")
        )
    }
}
