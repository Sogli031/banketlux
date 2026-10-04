package com.banketlux.ui.bookings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.ui.components.formatUnitPrice
import com.banketlux.ui.components.groupByCategoryOrder

/**
 * Izbornik opreme preko celog dna ekrana — zamena za uski padajući meni.
 * Ima pretragu po nazivu i kategoriji, a stavke su grupisane po kategoriji.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquipmentPickerSheet(
    equipmentOptions: List<EquipmentEntity>,
    selectedEquipmentId: Long?,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val rows = remember(equipmentOptions, query) {
        buildPickerRows(equipmentOptions, query)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Izaberi opremu",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )

            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = { query = it },
                label = { Text("Pretraga") },
                placeholder = { Text("npr. šatra, stolica, hladnjača") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )

            if (rows.isEmpty()) {
                Text(
                    text = if (equipmentOptions.isEmpty()) {
                        "Katalog opreme je prazan. Dodaj opremu u ekranu \"Oprema\"."
                    } else {
                        "Nema opreme koja odgovara pretrazi \"$query\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is PickerRow.Header -> CategoryHeader(row.category)
                            is PickerRow.Item -> EquipmentPickerRow(
                                item = row.equipment,
                                isSelected = row.equipment.id == selectedEquipmentId,
                                onClick = { onSelected(row.equipment.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(category: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier
                .padding(top = 12.dp, bottom = 4.dp)
                .semantics { heading() },
            text = category.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EquipmentPickerRow(
    item: EquipmentEntity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isSelected },
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatUnitPrice(item.defaultUnitPrice, item.currency, item.priceUnit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Na stanju: ${item.totalQuantity} kom",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(modifier = Modifier.size(24.dp)) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

internal sealed interface PickerRow {
    val key: String

    data class Header(val category: String) : PickerRow {
        override val key: String get() = "header-$category"
    }

    data class Item(val equipment: EquipmentEntity) : PickerRow {
        override val key: String get() = "item-${equipment.id}"
    }
}

/** Filtrira po nazivu i kategoriji, pa ubacuje zaglavlje ispred svake grupe. */
internal fun buildPickerRows(
    equipment: List<EquipmentEntity>,
    query: String
): List<PickerRow> {
    val trimmed = query.trim()
    val matching = if (trimmed.isEmpty()) {
        equipment
    } else {
        equipment.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.category.contains(trimmed, ignoreCase = true)
        }
    }

    return groupByCategoryOrder(matching, category = { it.category }, name = { it.name })
        .flatMap { (category, items) ->
            listOf(PickerRow.Header(category)) + items.map(PickerRow::Item)
        }
}
