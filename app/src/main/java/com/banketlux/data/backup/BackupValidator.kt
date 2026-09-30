package com.banketlux.data.backup

data class BackupValidationResult(val isValid: Boolean, val message: String?)

object BackupValidator {
    /** Šeme koje ova verzija aplikacije ume da uveze. v1 fajlovi se čitaju kao RSD / "po danu". */
    val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2, 3)
    const val CURRENT_SCHEMA_VERSION = 3

    fun validate(backup: BanketLuxBackup): BackupValidationResult {
        if (backup.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) {
            return BackupValidationResult(false, "Backup fajl nije podržan u ovoj verziji aplikacije.")
        }
        if (backup.exportedAtEpochMillis <= 0) {
            return BackupValidationResult(false, "Backup fajl nema ispravan datum izvoza.")
        }
        return BackupValidationResult(true, null)
    }
}
