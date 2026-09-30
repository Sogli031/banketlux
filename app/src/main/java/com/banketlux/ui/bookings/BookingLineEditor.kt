package com.banketlux.ui.bookings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.domain.model.PriceUnit
import com.banketlux.domain.pricing.BookingLineInput
import com.banketlux.domain.pricing.BookingTotals
import com.banketlux.ui.components.formatRsd

@Composable
fun BookingLineEditor(
    line: BookingLineDraft,
    equipmentOptions: List<EquipmentEntity>,
    rentalDays: Int,
    eurToRsdRate: Double,
    onEquipmentSelected: (Long) -> Unit,
    onQuantityChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onRemove: () -> Unit,
    /** Stavka je šatra — u okviru se nudi pranje stolnjaka. */
    isTent: Boolean = false,
    onWashingChange: (Boolean) -> Unit = {},
    onWashingPriceChange: (String) -> Unit = {}
) {
    var showEquipmentPicker by remember(line.id) { mutableStateOf(false) }
    val days = rentalDays.coerceAtLeast(1)
    val lineAmountRsd = line.effectiveQuantity()?.let { quantity ->
        line.unitPriceInput.toIntOrNull()?.let { price ->
            BookingTotals.lineTotalRsd(
                line = BookingLineInput(
                    quantity = quantity,
                    unitPrice = price,
                    currency = line.currency,
                    priceUnit = line.priceUnit
                ),
                rentalDays = days,
                eurToRsdRate = eurToRsdRate
            )
        }
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EquipmentSelectorField(
                    modifier = Modifier.weight(1f),
                    selectedName = line.equipmentName,
                    onClick = { showEquipmentPicker = true }
                )

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Obriši stavku")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = line.quantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text("Količina") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = line.unitPriceInput,
                    onValueChange = onPriceChange,
                    label = { Text("Cena (${line.currency.label} ${line.priceUnit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            if (line.equipmentItemId != null) {
                Text(
                    text = when (line.priceUnit) {
                        PriceUnit.PER_DAY -> "Naplata po danu — množi se sa $days ${dayWord(days)}."
                        PriceUnit.PER_EVENT -> "Paušal za celo veselje — broj dana ne utiče na cenu."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            lineAmountRsd?.let { amount ->
                Text(
                    text = "Ukupno stavka: ${formatRsd(amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (isTent) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sami peru stolnjake",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (line.washing) {
                                "Vraćaju prljave — naplaćuje se pranje."
                            } else {
                                "Bez naplate pranja."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Uključeno = mušterija pere sama (bez naplate); `washing` znači naplatu.
                    Switch(checked = !line.washing, onCheckedChange = { onWashingChange(!it) })
                }
                if (line.washing) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = line.washingPriceInput,
                        onValueChange = onWashingPriceChange,
                        label = { Text("Cena pranja (RSD)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = line.notes,
                onValueChange = onNotesChange,
                label = { Text("Napomena stavke") }
            )
        }
    }

    if (showEquipmentPicker) {
        EquipmentPickerSheet(
            equipmentOptions = equipmentOptions,
            selectedEquipmentId = line.equipmentItemId,
            onSelected = { selectedId ->
                onEquipmentSelected(selectedId)
                showEquipmentPicker = false
            },
            onDismiss = { showEquipmentPicker = false }
        )
    }
}

/** Polje koje izgleda kao unos, a otvara izbornik opreme preko dna ekrana. */
@Composable
private fun EquipmentSelectorField(
    modifier: Modifier,
    selectedName: String,
    onClick: () -> Unit
) {
    val hasSelection = selectedName.isNotBlank()

    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Oprema",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (hasSelection) selectedName else "Dodirni da izabereš",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasSelection) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasSelection) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = "Otvori listu opreme",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
