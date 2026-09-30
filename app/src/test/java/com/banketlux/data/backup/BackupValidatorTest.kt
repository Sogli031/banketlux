package com.banketlux.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {
    @Test
    fun acceptsMatchingSchemaVersion() {
        val backup = BanketLuxBackup(
            schemaVersion = 1,
            exportedAtEpochMillis = 1L,
            equipment = emptyList(),
            customers = emptyList(),
            bookings = emptyList(),
            bookingLines = emptyList(),
            settings = BackupSettings(businessName = "BanketLux", phone = "060 380 8175")
        )

        assertTrue(BackupValidator.validate(backup).isValid)
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val backup = BanketLuxBackup(
            schemaVersion = 99,
            exportedAtEpochMillis = 1L,
            equipment = emptyList(),
            customers = emptyList(),
            bookings = emptyList(),
            bookingLines = emptyList(),
            settings = BackupSettings(businessName = "BanketLux", phone = "060 380 8175")
        )

        assertFalse(BackupValidator.validate(backup).isValid)
    }
}
