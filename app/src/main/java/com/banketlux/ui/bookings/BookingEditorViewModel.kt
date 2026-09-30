package com.banketlux.ui.bookings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banketlux.data.local.dao.BookingWithLines
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.local.entity.BookingLineEntity
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.data.repository.BookingRepository
import com.banketlux.data.repository.EquipmentRepository
import com.banketlux.data.repository.SettingsRepository
import com.banketlux.domain.model.BookingStatus
import com.banketlux.domain.model.CurrencyConverter
import com.banketlux.domain.model.DateRange
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import com.banketlux.domain.model.TableclothWashing
import com.banketlux.domain.pricing.BookingLineInput
import com.banketlux.domain.pricing.BookingTotals
import com.banketlux.domain.validation.BookingDraft
import com.banketlux.domain.validation.BookingValidator
import com.banketlux.domain.validation.ValidationError
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal fun mapBookingLineDraftsToEntities(
    bookingId: Long,
    lines: List<BookingLineDraft>,
    rentalDays: Int = 1,
    eurToRsdRate: Double = CurrencyConverter.DEFAULT_EUR_TO_RSD_RATE,
    isTent: (BookingLineDraft) -> Boolean = { false }
): List<BookingLineEntity> {
    val days = rentalDays.coerceAtLeast(1)
    return lines.flatMap { line ->
        val equipmentId = line.equipmentItemId ?: return@flatMap emptyList()
        val quantity = line.effectiveQuantity() ?: return@flatMap emptyList()
        val unitPrice = line.unitPriceInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
        // Pranje stolnjaka ide odmah iza svoje šatre — po tom redosledu se vraća pri učitavanju.
        val washing = if (isTent(line) && line.washing) {
            BookingLineEntity(
                id = 0,
                bookingId = bookingId,
                equipmentItemId = TableclothWashing.EQUIPMENT_ID,
                displayNameSnapshot = TableclothWashing.NAME,
                quantity = 1,
                unitPriceSnapshot = line.washingPriceRsd(),
                lineTotalRsd = line.washingPriceRsd(),
                notes = "",
                currency = PriceCurrency.RSD,
                priceUnit = PriceUnit.PER_EVENT,
                eurRateSnapshot = CurrencyConverter.safeRate(eurToRsdRate)
            )
        } else {
            null
        }
        listOfNotNull(BookingLineEntity(
            id = 0,
            bookingId = bookingId,
            equipmentItemId = equipmentId,
            displayNameSnapshot = line.equipmentName,
            quantity = quantity,
            unitPriceSnapshot = unitPrice,
            lineTotalRsd = BookingTotals.lineTotalRsd(
                line = BookingLineInput(
                    quantity = quantity,
                    unitPrice = unitPrice,
                    currency = line.currency,
                    priceUnit = line.priceUnit
                ),
                rentalDays = days,
                eurToRsdRate = eurToRsdRate
            ),
            notes = line.notes.trim(),
            currency = line.currency,
            priceUnit = line.priceUnit,
            eurRateSnapshot = CurrencyConverter.safeRate(eurToRsdRate)
        ), washing)
    }
}

/**
 * Količina i cena su celi brojevi. Unos se seče na prvom decimalnom znaku ("12,5" → "12"),
 * jer bi inače "12,5" tiho postalo 0 RSD, a "125" bi bila pogrešna cena. Dužina je
 * ograničena da zbir ne može da prebaci opseg.
 */
internal fun sanitizeWholeNumber(value: String, maxDigits: Int): String =
    value.takeWhile { it != '.' && it != ',' }.filter(Char::isDigit).take(maxDigits)

private const val MAX_QUANTITY_DIGITS = 5
private const val MAX_PRICE_DIGITS = 7

/** Polja zakazivanja koja korisnik menja — za proveru nesačuvanih izmena (bez id-eva i zbirova). */
internal fun BookingEditorUiState.editFingerprint(): List<Any?> = listOf(
    customerName, customerPhone, location, rentalStartDate, rentalEndDate, status, notes,
    lines.map {
        listOf(
            it.equipmentItemId, it.quantityInput, it.unitPriceInput, it.notes,
            it.washing, it.washingPriceInput
        )
    }
)

internal fun computeRentalDays(start: LocalDate, end: LocalDate): Int {
    if (end.isBefore(start)) return 1
    return (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
}

data class BookingLineDraft(
    val id: Long,
    val equipmentItemId: Long? = null,
    val equipmentName: String = "",
    val quantityInput: String = "",
    val unitPriceInput: String = "",
    val notes: String = "",
    val currency: PriceCurrency = PriceCurrency.RSD,
    val priceUnit: PriceUnit = PriceUnit.PER_DAY,
    /** Pranje stolnjaka uz ovu šatru (nudi se samo za stavke iz kategorije "Šatre"). */
    val washing: Boolean = false,
    val washingPriceInput: String = TableclothWashing.DEFAULT_PRICE_RSD.toString()
)

internal fun BookingLineDraft.washingPriceRsd(): Int =
    washingPriceInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0

/**
 * Količina koja se stvarno računa. Prazno polje znači 1 komad — ranije je takva stavka
 * tiho ispadala iz zbira i iz sačuvanog zakazivanja. Vraća null samo ako je ukucana 0.
 */
internal fun BookingLineDraft.effectiveQuantity(): Int? {
    val trimmed = quantityInput.trim()
    if (trimmed.isEmpty()) return 1
    return trimmed.toIntOrNull()?.takeIf { it > 0 }
}

/** Stavka ulazi u obračun i čuvanje tek kad je izabrana oprema. */
internal fun BookingLineDraft.isComplete(): Boolean =
    equipmentItemId != null && effectiveQuantity() != null

data class AvailabilityWarning(
    val equipmentItemId: Long,
    val equipmentName: String,
    val requestedQuantity: Int,
    val availableQuantity: Int,
    val conflictLabels: List<String>,
    /** Koliko je na ove datume već zauzeto drugim zakazivanjima. */
    val reservedQuantity: Int = 0,
    val totalQuantity: Int = 0
) {
    /** Traži se više nego što je slobodno. */
    val isShortage: Boolean get() = requestedQuantity > availableQuantity
}

data class BookingEditorUiState(
    val bookingId: Long? = null,
    val customerName: String = "",
    val customerPhone: String = "",
    val location: String = "",
    val eventDate: LocalDate = LocalDate.now(),
    val rentalStartDate: LocalDate = LocalDate.now(),
    val rentalEndDate: LocalDate = LocalDate.now(),
    val status: BookingStatus = BookingStatus.CONFIRMED,
    val lines: List<BookingLineDraft> = emptyList(),
    val totalPriceRsd: Int = 0,
    val notes: String = "",
    val validationErrors: List<ValidationError> = emptyList(),
    val availabilityWarnings: List<AvailabilityWarning> = emptyList(),
    /** Druga zakazivanja čiji se najam preklapa sa izabranim datumima. */
    val dateConflicts: List<String> = emptyList(),
    /** Pri čuvanju nađeno preklapanje — čeka se potvrda "Sačuvaj ipak". */
    val overlapToConfirm: List<String>? = null,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val eurToRsdRate: Double = CurrencyConverter.DEFAULT_EUR_TO_RSD_RATE,
    val eurRateProblem: String? = null
) {
    val rentalDays: Int
        get() = computeRentalDays(rentalStartDate, rentalEndDate)
}

class BookingEditorViewModel(
    private val bookingRepository: BookingRepository,
    private val equipmentRepository: EquipmentRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        BookingEditorUiState(
            lines = listOf(BookingLineDraft(id = 1))
        )
    )
    val uiState: StateFlow<BookingEditorUiState> = _uiState.asStateFlow()

    private val _equipmentItems = MutableStateFlow<List<EquipmentEntity>>(emptyList())
    val equipmentItems: StateFlow<List<EquipmentEntity>> = _equipmentItems.asStateFlow()

    /**
     * Id-evi šatri iz CELOG kataloga, i deaktiviranih: staro zakazivanje sa deaktiviranom
     * šatrom mora da zadrži pranje stolnjaka pri izmeni.
     */
    private val _tentEquipmentIds = MutableStateFlow<Set<Long>>(emptySet())
    val tentEquipmentIds: StateFlow<Set<Long>> = _tentEquipmentIds.asStateFlow()

    /** Otisak sačuvanog stanja; razlika od trenutnog znači nesačuvane izmene. */
    private var savedFingerprint: List<Any?> = _uiState.value.editFingerprint()

    fun hasUnsavedChanges(state: BookingEditorUiState): Boolean =
        state.editFingerprint() != savedFingerprint

    private var initializedBookingId: Long? = null
    private var createdAtEpochMillis: Long? = null
    private var nextLineId: Long = 2
    private var refreshWarningsJob: Job? = null
    private var warningRefreshVersion: Long = 0

    /**
     * Kurs zaključan na sačuvanom zakazivanju: stari obračun ne sme da se promeni
     * samo zato što se kurs u međuvremenu pomerio.
     */
    private var lockedEurRate: Double? = null

    init {
        viewModelScope.launch {
            equipmentRepository.observeActiveEquipment().collect { items ->
                _equipmentItems.value = items
                // Kategorija (šatra) se zna tek kad stigne katalog.
                recomputeTotals()
            }
        }
        viewModelScope.launch {
            equipmentRepository.observeAllEquipment().collect { items ->
                _tentEquipmentIds.value = items
                    .filter { it.category.trim() == TableclothWashing.TENT_CATEGORY }
                    .map { it.id }
                    .toSet()
                recomputeTotals()
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                val problem = settingsRepository.rateProblem(settings)
                if (lockedEurRate != null) {
                    _uiState.update { it.copy(eurRateProblem = problem) }
                    return@collect
                }
                _uiState.update {
                    it.copy(
                        eurToRsdRate = CurrencyConverter.safeRate(settings.eurToRsdRate),
                        eurRateProblem = problem
                    )
                }
                recomputeTotals()
            }
        }
    }

    fun initialize(bookingId: Long?) {
        if (initializedBookingId == bookingId) return
        initializedBookingId = bookingId
        if (bookingId == null) {
            createdAtEpochMillis = null
            lockedEurRate = null
            _uiState.value = BookingEditorUiState(
                lines = listOf(BookingLineDraft(id = 1)),
                eurToRsdRate = _uiState.value.eurToRsdRate
            )
            nextLineId = 2
            savedFingerprint = _uiState.value.editFingerprint()
            refreshAvailabilityWarnings()
            return
        }

        viewModelScope.launch {
            val loaded = bookingRepository.getBookingById(bookingId)
            if (loaded != null) {
                applyLoadedBooking(loaded)
                refreshAvailabilityWarnings()
            }
        }
    }

    fun onCustomerNameChange(value: String) = updateEditorState { it.copy(customerName = value) }

    fun onCustomerPhoneChange(value: String) = updateEditorState { it.copy(customerPhone = value) }

    fun onLocationChange(value: String) = updateEditorState { it.copy(location = value) }

    fun onNotesChange(value: String) = updateEditorState { it.copy(notes = value) }

    fun onLineWashingChange(lineId: Long, value: Boolean) = updateEditorState { state ->
        state.copy(lines = state.lines.map { if (it.id == lineId) it.copy(washing = value) else it })
    }

    fun onLineWashingPriceChange(lineId: Long, value: String) = updateEditorState { state ->
        state.copy(
            lines = state.lines.map {
                if (it.id == lineId) it.copy(washingPriceInput = sanitizeWholeNumber(value, MAX_PRICE_DIGITS)) else it
            }
        )
    }

    /** Stavka iz kategorije "Šatre" — uz nju se nudi pranje stolnjaka. */
    fun isTentLine(line: BookingLineDraft): Boolean {
        val id = line.equipmentItemId ?: return false
        return id in _tentEquipmentIds.value
    }

    fun onEventDatePicked(date: LocalDate) = updateEditorState { it.copy(eventDate = date) }

    fun onRentalStartPicked(date: LocalDate) {
        if (isSavingInProgress()) return
        _uiState.update {
            val newEnd = if (it.rentalEndDate.isBefore(date)) date else it.rentalEndDate
            it.copy(rentalStartDate = date, rentalEndDate = newEnd, eventDate = date)
        }
        recomputeTotals()
        refreshAvailabilityWarnings()
    }

    fun onRentalEndPicked(date: LocalDate) {
        if (isSavingInProgress()) return
        _uiState.update { it.copy(rentalEndDate = date) }
        recomputeTotals()
        refreshAvailabilityWarnings()
    }

    fun addLine() {
        if (isSavingInProgress()) return
        _uiState.update { state ->
            state.copy(lines = state.lines + BookingLineDraft(id = nextLineId++))
        }
        recomputeTotals()
        refreshAvailabilityWarnings()
    }

    fun removeLine(lineId: Long) {
        if (isSavingInProgress()) return
        _uiState.update { state ->
            state.copy(lines = state.lines.filterNot { it.id == lineId })
        }
        recomputeTotals()
        refreshAvailabilityWarnings()
    }

    fun onLineEquipmentSelected(lineId: Long, equipmentItemId: Long) {
        if (isSavingInProgress()) return
        val equipment = _equipmentItems.value.firstOrNull { it.id == equipmentItemId }
        _uiState.update { state ->
            state.copy(
                lines = state.lines.map { line ->
                    if (line.id != lineId) {
                        line
                    } else {
                        line.copy(
                            equipmentItemId = equipmentItemId,
                            equipmentName = equipment?.name ?: line.equipmentName,
                            unitPriceInput = equipment?.defaultUnitPrice?.toString() ?: line.unitPriceInput,
                            // Bez ovoga stavka sa praznom količinom ne bi ušla u zbir.
                            quantityInput = line.quantityInput.ifBlank { "1" },
                            currency = equipment?.currency ?: line.currency,
                            priceUnit = equipment?.priceUnit ?: line.priceUnit
                        )
                    }
                }
            )
        }
        recomputeTotals()
        refreshAvailabilityWarnings()
    }

    fun onLineQuantityChange(lineId: Long, value: String) {
        if (isSavingInProgress()) return
        _uiState.update { state ->
            state.copy(lines = state.lines.map { if (it.id == lineId) it.copy(quantityInput = sanitizeWholeNumber(value, MAX_QUANTITY_DIGITS)) else it })
        }
        recomputeTotals()
        refreshAvailabilityWarnings()
    }

    fun onLinePriceChange(lineId: Long, value: String) {
        if (isSavingInProgress()) return
        _uiState.update { state ->
            state.copy(lines = state.lines.map { if (it.id == lineId) it.copy(unitPriceInput = sanitizeWholeNumber(value, MAX_PRICE_DIGITS)) else it })
        }
        recomputeTotals()
    }

    fun onLineNotesChange(lineId: Long, value: String) {
        if (isSavingInProgress()) return
        _uiState.update { state ->
            state.copy(lines = state.lines.map { if (it.id == lineId) it.copy(notes = value) else it })
        }
    }

    fun dismissOverlapConfirm() = _uiState.update { it.copy(overlapToConfirm = null) }

    /**
     * [overlapConfirmed] = korisnik je već potvrdio da zna za preklapanje sa drugim
     * zakazivanjem. Bez toga čuvanje staje i traži potvrdu.
     */
    fun saveBooking(overlapConfirmed: Boolean = false, onSaved: (Long) -> Unit) {
        val stateSnapshot = snapshotForCalculations().let {
            it.copy(eventDate = it.rentalStartDate)
        }
        if (stateSnapshot.isSaving) return

        val validationErrors = BookingValidator.validate(
            BookingDraft(
                customerName = stateSnapshot.customerName,
                customerPhone = stateSnapshot.customerPhone,
                eventDate = stateSnapshot.eventDate,
                rentalRange = DateRange(stateSnapshot.rentalStartDate, stateSnapshot.rentalEndDate)
            )
        )
        // Zakazivanje bez ijedne kompletne stavke bi se sačuvalo kao prazno ("Bez stavki", 0 RSD).
        val lineErrors = if (stateSnapshot.lines.none { it.isComplete() }) {
            listOf(
                ValidationError(
                    "lines",
                    "Dodaj bar jednu stavku i izaberi opremu iz liste."
                )
            )
        } else {
            emptyList()
        }

        // Stavka sa izabranom opremom ali neispravnom količinom ("0", slova...) bi inače
        // tiho ispala iz čuvanja čim postoji bar jedna druga ispravna stavka.
        val quantityErrors = stateSnapshot.lines
            .filter { it.equipmentItemId != null && it.effectiveQuantity() == null }
            .map { line ->
                ValidationError(
                    "lineQuantity-${line.id}",
                    "Količina za „${line.equipmentName}“ mora biti broj veći od 0."
                )
            }

        val allErrors = validationErrors + lineErrors + quantityErrors
        if (allErrors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = allErrors) }
            return
        }

        _uiState.update {
            it.copy(isSaving = true, validationErrors = emptyList(), overlapToConfirm = null)
        }
        viewModelScope.launch {
            try {
                val overlaps = findOverlaps(stateSnapshot)
                if (!overlapConfirmed && overlaps.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            overlapToConfirm = overlapLabels(overlaps),
                            dateConflicts = overlapLabels(overlaps)
                        )
                    }
                    return@launch
                }
                val equipmentSnapshot = _equipmentItems.value.toList()
                val warnings = calculateWarnings(stateSnapshot, equipmentSnapshot, overlaps)
                val totals = calculateTotals(stateSnapshot)
                val rentalDays = computeRentalDays(stateSnapshot.rentalStartDate, stateSnapshot.rentalEndDate)
                val now = System.currentTimeMillis()
                val createdAt = createdAtEpochMillis ?: now
                // Postojeći događaj u kalendaru se ažurira, ne pravi se novi.
                val calendarEventId = stateSnapshot.bookingId
                    ?.let { bookingRepository.getBookingById(it)?.booking?.calendarEventId }

                val bookingEntity = BookingEntity(
                    id = stateSnapshot.bookingId ?: 0,
                    customerName = stateSnapshot.customerName.trim(),
                    customerPhone = stateSnapshot.customerPhone.trim(),
                    location = stateSnapshot.location.trim(),
                    eventDate = stateSnapshot.eventDate.toString(),
                    rentalStartDate = stateSnapshot.rentalStartDate.toString(),
                    rentalEndDate = stateSnapshot.rentalEndDate.toString(),
                    status = stateSnapshot.status,
                    notes = stateSnapshot.notes.trim(),
                    totalPriceRsd = totals.totalPriceRsd,
                    advancePaidRsd = 0,
                    amountPaidRsd = 0,
                    remainingDebtRsd = 0,
                    createdAtEpochMillis = createdAt,
                    updatedAtEpochMillis = now,
                    calendarEventId = calendarEventId
                )

                val lineEntities = mapBookingLineDraftsToEntities(
                    bookingId = stateSnapshot.bookingId ?: 0,
                    lines = stateSnapshot.lines,
                    rentalDays = rentalDays,
                    eurToRsdRate = stateSnapshot.eurToRsdRate,
                    isTent = ::isTentLine
                )

                val savedId = bookingRepository.saveBookingWithLines(bookingEntity, lineEntities)
                createdAtEpochMillis = createdAt
                savedFingerprint = stateSnapshot.editFingerprint()
                lockedEurRate = stateSnapshot.eurToRsdRate
                _uiState.update {
                    it.copy(
                        bookingId = savedId,
                        totalPriceRsd = totals.totalPriceRsd,
                        availabilityWarnings = warnings
                    )
                }
                onSaved(savedId)
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        validationErrors = listOf(
                            ValidationError("save", "Čuvanje zakazivanja nije uspelo.")
                        )
                    )
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    /**
     * [onDeleted] dobija id događaja u kalendaru (ako ga je bilo), da bi pozivalac
     * mogao da ga skloni iz kalendara pošto je red već obrisan iz baze.
     */
    fun deleteBooking(onDeleted: (bookingId: Long, calendarEventId: String?) -> Unit) {
        val bookingId = _uiState.value.bookingId ?: return
        if (_uiState.value.isSaving || _uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            try {
                val calendarEventId = bookingRepository.getBookingById(bookingId)
                    ?.booking
                    ?.calendarEventId
                bookingRepository.deleteBooking(bookingId)
                _uiState.update { it.copy(isDeleting = false) }
                onDeleted(bookingId, calendarEventId)
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        validationErrors = listOf(
                            ValidationError("delete", "Brisanje zakazivanja nije uspelo.")
                        )
                    )
                }
            }
        }
    }

    /** Ponovni pokušaj preuzimanja kursa kad je prijavljen problem. */
    fun retryEurRate() {
        viewModelScope.launch {
            runCatching { settingsRepository.refreshRateFromNetwork() }
        }
    }

    fun refreshAvailabilityWarnings() {
        val stateSnapshot = snapshotForCalculations()
        val equipmentSnapshot = _equipmentItems.value.toList()
        val version = ++warningRefreshVersion
        refreshWarningsJob?.cancel()
        refreshWarningsJob = viewModelScope.launch {
            val overlaps = findOverlaps(stateSnapshot)
            val warnings = calculateWarnings(stateSnapshot, equipmentSnapshot, overlaps)
            if (version != warningRefreshVersion) return@launch
            _uiState.update {
                it.copy(availabilityWarnings = warnings, dateConflicts = overlapLabels(overlaps))
            }
        }
    }

    private fun updateEditorState(transform: (BookingEditorUiState) -> BookingEditorUiState) {
        if (isSavingInProgress()) return
        _uiState.update(transform)
        recomputeTotals()
    }

    private fun recomputeTotals() {
        val totals = calculateTotals(_uiState.value)
        _uiState.update { it.copy(totalPriceRsd = totals.totalPriceRsd) }
    }

    private fun calculateTotals(state: BookingEditorUiState) = BookingTotals.calculate(
        lines = state.lines.flatMap { line ->
            if (line.equipmentItemId == null) return@flatMap emptyList()
            val quantity = line.effectiveQuantity() ?: return@flatMap emptyList()
            val unitPrice = line.unitPriceInput.toIntOrNull()?.coerceAtLeast(0) ?: 0
            listOfNotNull(
                BookingLineInput(
                    quantity = quantity,
                    unitPrice = unitPrice,
                    currency = line.currency,
                    priceUnit = line.priceUnit
                ),
                if (line.washing && isTentLine(line)) {
                    BookingLineInput(
                        quantity = 1,
                        unitPrice = line.washingPriceRsd(),
                        currency = PriceCurrency.RSD,
                        priceUnit = PriceUnit.PER_EVENT
                    )
                } else {
                    null
                }
            )
        },
        advancePaidRsd = 0,
        amountPaidRsd = 0,
        rentalDays = computeRentalDays(state.rentalStartDate, state.rentalEndDate),
        eurToRsdRate = state.eurToRsdRate
    )

    private suspend fun findOverlaps(state: BookingEditorUiState): List<BookingWithLines> =
        bookingRepository.getBookingsOverlappingRange(
            startDate = state.rentalStartDate.toString(),
            endDate = state.rentalEndDate.toString(),
            currentBookingId = state.bookingId
        )

    private fun overlapLabels(overlaps: List<BookingWithLines>): List<String> =
        overlaps.map { overlapLabel(it) }

    private fun overlapLabel(bookingWithLines: BookingWithLines): String {
        val booking = bookingWithLines.booking
        val name = booking.customerName.ifBlank { "Bez imena" }
        val dates = if (booking.rentalStartDate == booking.rentalEndDate) {
            formatDate(booking.rentalStartDate)
        } else {
            "${formatDate(booking.rentalStartDate)} – ${formatDate(booking.rentalEndDate)}"
        }
        return "$name ($dates)"
    }

    private fun calculateWarnings(
        state: BookingEditorUiState,
        equipmentItems: List<EquipmentEntity>,
        overlaps: List<BookingWithLines>
    ): List<AvailabilityWarning> {
        val requestedByEquipment = linkedMapOf<Long, Pair<String, Int>>()
        state.lines.forEach { line ->
            val equipmentId = line.equipmentItemId ?: return@forEach
            val quantity = line.effectiveQuantity() ?: return@forEach
            val existing = requestedByEquipment[equipmentId]
            val currentName = line.equipmentName
            if (existing == null) {
                requestedByEquipment[equipmentId] = currentName to quantity
            } else {
                val mergedName = if (existing.first.isNotBlank()) existing.first else currentName
                requestedByEquipment[equipmentId] = mergedName to (existing.second + quantity)
            }
        }

        return requestedByEquipment.mapNotNull { (equipmentId, requested) ->
            val equipment = equipmentItems.firstOrNull { it.id == equipmentId } ?: return@mapNotNull null
            val requestedQuantity = requested.second
            val conflictingBookings = overlaps.filter { bookingWithLines ->
                bookingWithLines.lines.any { it.equipmentItemId == equipmentId }
            }

            val alreadyReserved = conflictingBookings.sumOf { bookingWithLines ->
                bookingWithLines.lines
                    .filter { it.equipmentItemId == equipmentId }
                    .sumOf { it.quantity }
            }

            val availableQuantity = (equipment.totalQuantity - alreadyReserved).coerceAtLeast(0)
            // Prikazuje se i kad ima dovoljno, čim je deo već zauzet na ove datume.
            if (requestedQuantity <= availableQuantity && alreadyReserved == 0) {
                null
            } else {
                AvailabilityWarning(
                    equipmentItemId = equipmentId,
                    equipmentName = requested.first.ifBlank { equipment.name },
                    requestedQuantity = requestedQuantity,
                    availableQuantity = availableQuantity,
                    conflictLabels = conflictingBookings.map(::overlapLabel),
                    reservedQuantity = alreadyReserved,
                    totalQuantity = equipment.totalQuantity
                )
            }
        }
    }

    private fun snapshotForCalculations(): BookingEditorUiState {
        val state = _uiState.value
        return state.copy(
            lines = state.lines.toList(),
            validationErrors = state.validationErrors.toList(),
            availabilityWarnings = state.availabilityWarnings.toList()
        )
    }

    private fun applyLoadedBooking(loaded: BookingWithLines) {
        createdAtEpochMillis = loaded.booking.createdAtEpochMillis
        // Pranje stolnjaka je sačuvano odmah iza svoje šatre — vraća se na tu stavku.
        val drafts = mutableListOf<BookingLineDraft>()
        loaded.lines.sortedBy { it.id }.forEach {
            if (it.equipmentItemId == TableclothWashing.EQUIPMENT_ID) {
                val owner = drafts.lastOrNull() ?: return@forEach
                drafts[drafts.lastIndex] = owner.copy(
                    washing = true,
                    washingPriceInput = it.unitPriceSnapshot.toString()
                )
            } else {
                drafts += BookingLineDraft(
                    id = nextLineId++,
                    equipmentItemId = it.equipmentItemId,
                    equipmentName = it.displayNameSnapshot,
                    quantityInput = it.quantity.toString(),
                    unitPriceInput = it.unitPriceSnapshot.toString(),
                    notes = it.notes,
                    currency = it.currency,
                    priceUnit = it.priceUnit
                )
            }
        }
        val mappedLines = drafts.toList().ifEmpty {
            listOf(BookingLineDraft(id = nextLineId++))
        }

        // Zadrži kurs po kom je zakazivanje već obračunato.
        val snapshotRate = loaded.lines.firstOrNull { it.currency == PriceCurrency.EUR }?.eurRateSnapshot
        lockedEurRate = snapshotRate?.let { CurrencyConverter.safeRate(it) }

        _uiState.value = BookingEditorUiState(
            bookingId = loaded.booking.id,
            customerName = loaded.booking.customerName,
            customerPhone = loaded.booking.customerPhone,
            location = loaded.booking.location,
            eventDate = parseDate(loaded.booking.eventDate),
            rentalStartDate = parseDate(loaded.booking.rentalStartDate),
            rentalEndDate = parseDate(loaded.booking.rentalEndDate),
            status = loaded.booking.status,
            lines = mappedLines,
            totalPriceRsd = loaded.booking.totalPriceRsd,
            notes = loaded.booking.notes,
            eurToRsdRate = lockedEurRate ?: _uiState.value.eurToRsdRate
        )
        savedFingerprint = _uiState.value.editFingerprint()
        refreshAvailabilityWarnings()
    }

    private fun parseDate(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrElse {
        LocalDate.now()
    }

    private fun isSavingInProgress(): Boolean = _uiState.value.isSaving || _uiState.value.isDeleting
}
