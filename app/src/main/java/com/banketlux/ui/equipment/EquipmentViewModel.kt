package com.banketlux.ui.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.data.repository.EquipmentRepository
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EquipmentUiState(
    val items: List<EquipmentEntity> = emptyList(),
    val isEditorOpen: Boolean = false,
    val isSaving: Boolean = false,
    val editingItem: EquipmentEntity? = null,
    val nameInput: String = "",
    val categoryInput: String = "Ostalo",
    val quantityInput: String = "",
    val priceInput: String = "",
    val currencyInput: PriceCurrency = PriceCurrency.RSD,
    val priceUnitInput: PriceUnit = PriceUnit.PER_DAY,
    val notesInput: String = "",
    val errorMessage: String? = null
)

class EquipmentViewModel(
    private val repository: EquipmentRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(EquipmentUiState())
    val uiState: StateFlow<EquipmentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeAllEquipment().collect { items ->
                _uiState.update { it.copy(items = items) }
            }
        }
    }

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editingItem = null,
                nameInput = "",
                categoryInput = "Ostalo",
                quantityInput = "",
                priceInput = "",
                currencyInput = PriceCurrency.RSD,
                priceUnitInput = PriceUnit.PER_DAY,
                notesInput = "",
                errorMessage = null
            )
        }
    }

    fun openEditDialog(item: EquipmentEntity) {
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editingItem = item,
                nameInput = item.name,
                categoryInput = item.category,
                quantityInput = item.totalQuantity.toString(),
                priceInput = item.defaultUnitPrice.toString(),
                currencyInput = item.currency,
                priceUnitInput = item.priceUnit,
                notesInput = item.notes,
                errorMessage = null
            )
        }
    }

    fun closeEditor() {
        _uiState.update { it.copy(isEditorOpen = false, errorMessage = null) }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(nameInput = value) }
    }

    fun onCategoryChange(value: String) {
        _uiState.update { it.copy(categoryInput = value) }
    }

    fun onQuantityChange(value: String) {
        _uiState.update { it.copy(quantityInput = value) }
    }

    fun onPriceChange(value: String) {
        _uiState.update { it.copy(priceInput = value) }
    }

    fun onCurrencyChange(value: PriceCurrency) {
        _uiState.update { it.copy(currencyInput = value) }
    }

    fun onPriceUnitChange(value: PriceUnit) {
        _uiState.update { it.copy(priceUnitInput = value) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notesInput = value) }
    }

    fun saveEditor() {
        val state = _uiState.value
        if (state.isSaving) return

        val quantity = state.quantityInput.trim().toIntOrNull()
        val validationError = validateEquipment(state.nameInput, quantity)
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        val parsedPrice = parsePriceInput(state.priceInput)
        if (parsedPrice == null) {
            _uiState.update {
                it.copy(errorMessage = "Cena mora biti broj 0 ili veći.")
            }
            return
        }

        val trimmedNotes = state.notesInput.trim()
        val trimmedName = state.nameInput.trim()
        val trimmedCategory = state.categoryInput.trim().ifBlank { "Ostalo" }

        val edited = state.editingItem
        val updatedItem = if (edited == null) {
            EquipmentEntity(
                name = trimmedName,
                category = trimmedCategory,
                totalQuantity = quantity ?: 0,
                defaultUnitPrice = parsedPrice,
                notes = trimmedNotes,
                active = true,
                currency = state.currencyInput,
                priceUnit = state.priceUnitInput
            )
        } else {
            edited.copy(
                name = trimmedName,
                category = trimmedCategory,
                totalQuantity = quantity ?: 0,
                defaultUnitPrice = parsedPrice,
                notes = trimmedNotes,
                currency = state.currencyInput,
                priceUnit = state.priceUnitInput
            )
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.save(updatedItem)
                _uiState.update {
                    it.copy(
                        isEditorOpen = false,
                        editingItem = null,
                        nameInput = "",
                        categoryInput = "Ostalo",
                        quantityInput = "",
                        priceInput = "",
                        currencyInput = PriceCurrency.RSD,
                        priceUnitInput = PriceUnit.PER_DAY,
                        notesInput = "",
                        errorMessage = null
                    )
                }
            } catch (_: Throwable) {
                _uiState.update { current ->
                    current.copy(errorMessage = "Čuvanje opreme nije uspelo.")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deactivate(itemId: Long) {
        viewModelScope.launch {
            runCatching { repository.deactivate(itemId) }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(errorMessage = "Deaktiviranje opreme nije uspelo.")
                    }
                }
        }
    }

    fun activate(itemId: Long) {
        viewModelScope.launch {
            runCatching { repository.activate(itemId) }
                .onFailure {
                    _uiState.update { current ->
                        current.copy(errorMessage = "Aktiviranje opreme nije uspelo.")
                    }
                }
        }
    }

    private fun validateEquipment(name: String, quantity: Int?): String? = when {
        name.isBlank() -> "Unesi naziv opreme."
        quantity == null || quantity < 0 -> "Količina mora biti 0 ili veća."
        else -> null
    }

    private fun parsePriceInput(priceInput: String): Int? {
        val trimmed = priceInput.trim()
        if (trimmed.isEmpty()) return 0
        val parsed = trimmed.toIntOrNull() ?: return null
        return parsed.takeIf { it >= 0 }
    }
}
