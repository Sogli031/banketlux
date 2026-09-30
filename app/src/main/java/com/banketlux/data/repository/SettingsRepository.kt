package com.banketlux.data.repository

import com.banketlux.data.local.dao.BusinessSettingsDao
import com.banketlux.data.local.entity.BusinessSettingsEntity
import com.banketlux.data.remote.ExchangeRateService
import com.banketlux.domain.model.CurrencyConverter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val DEFAULT_BUSINESS_NAME = "BanketLux"
private const val DEFAULT_PHONE = "060 380 8175"

/** Kurs stariji od ovoga se smatra sumnjivim i prijavljuje korisniku. */
private val RATE_STALE_AFTER_HOURS = 48L

class SettingsRepository(
    private val dao: BusinessSettingsDao,
    private val exchangeRateService: ExchangeRateService = ExchangeRateService()
) {
    fun observeSettings(): Flow<BusinessSettingsEntity> =
        dao.observeSettings().map { it ?: defaultSettings() }

    suspend fun getSettings(): BusinessSettingsEntity = dao.getSettings() ?: defaultSettings()

    suspend fun getEurToRsdRate(): Double = CurrencyConverter.safeRate(getSettings().eurToRsdRate)

    /**
     * Kurs se ne prikazuje nigde u podešavanjima, pa problem mora da ispliva sam.
     * Vraća poruku o grešci kad kurs nikad nije preuzet ili je zastareo, inače null.
     */
    fun rateProblem(settings: BusinessSettingsEntity): String? {
        if (settings.eurRateUpdatedAtEpochMillis <= 0) {
            return "Kurs evra nikad nije preuzet — nema interneta ili servis ne odgovara. " +
                "Evro stavke se računaju po približnom kursu " +
                "1 EUR = ${"%.2f".format(CurrencyConverter.safeRate(settings.eurToRsdRate))} RSD."
        }
        val ageHours = (System.currentTimeMillis() - settings.eurRateUpdatedAtEpochMillis) /
            TimeUnit.HOURS.toMillis(1)
        if (ageHours >= RATE_STALE_AFTER_HOURS) {
            return "Kurs evra nije osvežen ${ageHours / 24} dana — proveri internet. " +
                "Računa se po poslednjem poznatom kursu " +
                "1 EUR = ${"%.2f".format(CurrencyConverter.safeRate(settings.eurToRsdRate))} RSD."
        }
        return null
    }

    /** Ručni unos kursa; gasi automatsku oznaku izvora. */
    suspend fun setManualRate(rate: Double): Boolean {
        if (!rate.isFinite() || rate <= 0.0) return false
        val current = getSettings()
        dao.upsert(
            current.copy(
                eurToRsdRate = rate,
                eurRateUpdatedAtEpochMillis = System.currentTimeMillis(),
                eurRateSource = "MANUAL"
            )
        )
        return true
    }

    /**
     * Preuzima kurs sa interneta i pamti ga. Ako nema mreže, vraća grešku
     * a poslednji sačuvani kurs ostaje na snazi.
     */
    suspend fun refreshRateFromNetwork(): Result<Double> {
        val fetched = exchangeRateService.fetchEurToRsd()
        return fetched.mapCatching { rate ->
            val current = getSettings()
            dao.upsert(
                current.copy(
                    eurToRsdRate = rate.eurToRsd,
                    eurRateUpdatedAtEpochMillis = rate.fetchedAtEpochMillis,
                    eurRateSource = "API"
                )
            )
            rate.eurToRsd
        }
    }

    /** Osvežava kurs samo ako je stariji od 12 sati. Poziva se pri pokretanju aplikacije. */
    suspend fun refreshRateIfStale(): Result<Double>? {
        val current = getSettings()
        val age = System.currentTimeMillis() - current.eurRateUpdatedAtEpochMillis
        if (current.eurRateUpdatedAtEpochMillis > 0 && age < TimeUnit.HOURS.toMillis(12)) {
            return null
        }
        return refreshRateFromNetwork()
    }

    private fun defaultSettings() = BusinessSettingsEntity(
        id = 1L,
        businessName = DEFAULT_BUSINESS_NAME,
        phone = DEFAULT_PHONE
    )
}
