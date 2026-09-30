package com.banketlux.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: uvodi valutu (EUR/RSD) i jedinicu cene (po danu / za celo veselje)
 * na opremi i na stavkama zakazivanja, plus kurs evra u podešavanjima.
 * Postojeći podaci ostaju netaknuti: sve postaje RSD i "po danu", tj. dosadašnje ponašanje.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE equipment ADD COLUMN currency TEXT NOT NULL DEFAULT 'RSD'")
        db.execSQL("ALTER TABLE equipment ADD COLUMN priceUnit TEXT NOT NULL DEFAULT 'PER_DAY'")

        db.execSQL("ALTER TABLE booking_lines ADD COLUMN currency TEXT NOT NULL DEFAULT 'RSD'")
        db.execSQL("ALTER TABLE booking_lines ADD COLUMN priceUnit TEXT NOT NULL DEFAULT 'PER_DAY'")
        db.execSQL("ALTER TABLE booking_lines ADD COLUMN eurRateSnapshot REAL NOT NULL DEFAULT 117.0")

        db.execSQL("ALTER TABLE business_settings ADD COLUMN eurToRsdRate REAL NOT NULL DEFAULT 117.0")
        db.execSQL("ALTER TABLE business_settings ADD COLUMN eurRateUpdatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE business_settings ADD COLUMN eurRateSource TEXT NOT NULL DEFAULT 'MANUAL'")
    }
}

/**
 * Napomene koje je aplikacija sama upisala uz standardni cenovnik.
 * Brišu se doslovnim poređenjem, da se ne dira ništa što je korisnik sam otkucao.
 */
private val SEEDED_NOTES = listOf(
    "Komplet: šatra, stolovi, klupe i escajg. Cena važi za celo veselje.",
    "Cena važi za celo veselje.",
    "Važi samo uz zakup velike šatre (6x12 m).",
    "Važi samo uz zakup male šatre (8x5 m).",
    "Raspon 800–1000 din dnevno; uneta gornja granica. Proveri količinu na stanju.",
    "Cena po stolici za celo veselje. Raspon 80–100 din; uneta gornja granica. " +
        "Proveri količinu na stanju.",
    "Ista hladnjača kao i dnevni zakup — izaberi samo jednu od dve stavke.",
    "Raspon 60–70 € dnevno; uneta gornja granica. " +
        "Ista hladnjača kao i paušal za celo veselje — izaberi samo jednu od dve stavke."
)

/**
 * v2 -> v3: uvodi arhivu zarade. Završena zakazivanja se prebacuju u tabelu `earnings`
 * sa iznosom koji je važio u trenutku posla. Usput briše napomene koje je sama
 * aplikacija upisala uz standardni cenovnik.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `earnings` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `bookingId` INTEGER NOT NULL,
                `customerName` TEXT NOT NULL,
                `customerPhone` TEXT NOT NULL,
                `location` TEXT NOT NULL,
                `eventDate` TEXT NOT NULL,
                `rentalStartDate` TEXT NOT NULL,
                `rentalEndDate` TEXT NOT NULL,
                `equipmentSummary` TEXT NOT NULL,
                `amountRsd` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `archivedAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        val placeholders = SEEDED_NOTES.joinToString(",") { "?" }
        db.execSQL(
            "UPDATE equipment SET notes = '' WHERE notes IN ($placeholders)",
            SEEDED_NOTES.toTypedArray()
        )
    }
}

/** v3 -> v4: pamti id događaja u Google kalendaru uz zakazivanje. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bookings ADD COLUMN calendarEventId TEXT DEFAULT NULL")
    }
}

val BANKETLUX_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
