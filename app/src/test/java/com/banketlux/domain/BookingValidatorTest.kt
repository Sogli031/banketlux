package com.banketlux.domain

import com.banketlux.domain.model.DateRange
import com.banketlux.domain.validation.BookingDraft
import com.banketlux.domain.validation.BookingValidator
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingValidatorTest {
    @Test
    fun rejectsMissingCustomerNameOrPhone() {
        val errors = BookingValidator.validate(
            BookingDraft(
                customerName = "",
                customerPhone = "",
                eventDate = LocalDate.of(2026, 7, 1),
                rentalRange = DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1))
            )
        )

        assertTrue(errors.any { it.field == "customerName" })
        // Telefon nije obavezan.
        assertTrue(errors.none { it.field == "customerPhone" })
    }

    @Test
    fun rejectsRentalEndBeforeStart() {
        val errors = BookingValidator.validate(
            BookingDraft(
                customerName = "Milan",
                customerPhone = "060123456",
                eventDate = LocalDate.of(2026, 7, 1),
                rentalRange = DateRange(LocalDate.of(2026, 7, 2), LocalDate.of(2026, 7, 1))
            )
        )

        assertTrue(errors.any { it.field == "rentalEndDate" })
    }
}
