package com.banketlux.ui.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.banketlux.domain.model.PriceCurrency
import com.banketlux.domain.model.PriceUnit
import com.banketlux.ui.components.BanketAlertPanel
import com.banketlux.ui.components.BanketTone

@Composable
fun EquipmentEditorDialog(
    uiState: EquipmentUiState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onCurrencyChange: (PriceCurrency) -> Unit,
    onPriceUnitChange: (PriceUnit) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!uiState.isSaving) {
                onDismiss()
            }
        },
        confirmButton = {
            TextButton(
                enabled = !uiState.isSaving,
                onClick = onSave
            ) {
                Text("Sačuvaj")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !uiState.isSaving,
                onClick = onDismiss
            ) {
                Text("Poništi")
            }
        },
        title = {
            Text(if (uiState.editingItem == null) "Dodaj opremu" else "Izmeni opremu")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.nameInput,
                    onValueChange = onNameChange,
                    label = { Text("Naziv") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.categoryInput,
                    onValueChange = onCategoryChange,
                    label = { Text("Kategorija") },
                    singleLine = true
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.quantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text("Količina") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = uiState.priceInput,
                        onValueChange = onPriceChange,
                        label = { Text("Cena") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    EnumDropdown(
                        modifier = Modifier.weight(1f),
                        label = "Valuta",
                        value = uiState.currencyInput.label,
                        options = PriceCurrency.entries.map { it to it.label },
                        onSelected = onCurrencyChange
                    )
                }

                EnumDropdown(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Naplata",
                    value = uiState.priceUnitInput.label,
                    options = PriceUnit.entries.map { it to it.label },
                    onSelected = onPriceUnitChange
                )

                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = uiState.notesInput,
                    onValueChange = onNotesChange,
                    label = { Text("Napomena") },
                    minLines = 3,
                    maxLines = 5
                )
                uiState.errorMessage?.let { message ->
                    BanketAlertPanel(
                        title = "Proveri opremu",
                        message = message,
                        tone = BanketTone.Error
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    modifier: Modifier,
    label: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            value = value,
            onValueChange = {},
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (option, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
