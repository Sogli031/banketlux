package com.banketlux.domain

import com.banketlux.ui.bookings.BookingEditorUiState
import com.banketlux.ui.bookings.BookingLineDraft
import com.banketlux.ui.bookings.editFingerprint
import com.banketlux.ui.bookings.sanitizeWholeNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BookingEditorInputTest {
    @Test
    fun decimalInputIsCutAtTheSeparatorInsteadOfBecomingZeroOrAnotherPrice() {
        assertEquals("12", sanitizeWholeNumber("12,5", 7))
        assertEquals("12", sanitizeWholeNumber("12.5", 7))
        assertEquals("", sanitizeWholeNumber(",5", 7))
    }

    @Test
    fun lettersAndSignsAreDropped() {
        assertEquals("150", sanitizeWholeNumber("-1a5 0", 7))
        assertEquals("", sanitizeWholeNumber("abc", 7))
    }

    @Test
    fun lengthIsLimited() {
        assertEquals("12345", sanitizeWholeNumber("1234567890", 5))
    }

    @Test
    fun fingerprintIgnoresLineIdsAndTotalsButSeesRealEdits() {
        val base = BookingEditorUiState(lines = listOf(BookingLineDraft(id = 1)))
        val sameContent = base.copy(
            lines = listOf(BookingLineDraft(id = 7)),
            totalPriceRsd = 123
        )
        assertEquals(base.editFingerprint(), sameContent.editFingerprint())

        assertNotEquals(base.editFingerprint(), base.copy(customerName = "Marko").editFingerprint())
        assertNotEquals(
            base.editFingerprint(),
            base.copy(lines = listOf(BookingLineDraft(id = 1, quantityInput = "3"))).editFingerprint()
        )
    }
}
