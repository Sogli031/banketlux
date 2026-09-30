package com.banketlux.ui.equipment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banketlux.data.backup.BackupScheduler
import com.banketlux.data.local.entity.EquipmentEntity
import com.banketlux.data.repository.EquipmentRepository
import com.banketlux.ui.components.BanketCard
import com.banketlux.ui.components.BanketChip
import com.banketlux.ui.components.BanketEmptyState
import com.banketlux.ui.components.BanketSectionLabel
import com.banketlux.ui.components.BanketTopBar
import com.banketlux.ui.components.formatUnitPrice
import com.banketlux.ui.components.groupByCategoryOrder
import com.banketlux.ui.theme.BanketGoldLine
import com.banketlux.ui.theme.BanketIconMuted
import com.banketlux.ui.theme.BanketMono

@Composable
fun EquipmentScreen(
    equipmentRepository: EquipmentRepository
) {
    val factory = remember(equipmentRepository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(EquipmentViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return EquipmentViewModel(equipmentRepository) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val context = LocalContext.current
    val viewModel: EquipmentViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    // Deaktiviranje se traži potvrdom da promašen klik ne skloni opremu iz kataloga.
    var pendingDeactivation by remember { mutableStateOf<EquipmentEntity?>(null) }
    val groupedItems = remember(uiState.items) {
        groupByCategoryOrder(uiState.items, category = { it.category }, name = { it.name })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BanketTopBar(
                title = "Oprema",
                subtitle = "Inventar za iznajmljivanje · ${uiState.items.size} ${itemWord(uiState.items.size)}",
                showLogo = true
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Dodaj opremu") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = viewModel::openCreateDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            BanketEmptyState(
                title = "Nema opreme",
                message = "Dodaj opremu koju BanketLux iznajmljuje.",
                actionLabel = "Dodaj opremu",
                onAction = viewModel::openCreateDialog,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 4.dp,
                    end = 16.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groupedItems.forEach { (category, itemsInCategory) ->
                    item(key = "header-$category") {
                        CategoryHeader(
                            category = category,
                            count = itemsInCategory.size
                        )
                    }
                    // Cela kategorija je jedna kartica; stavke su redovi razdvojeni linijom.
                    item(key = "group-$category") {
                        BanketCard {
                            itemsInCategory.forEachIndexed { index, item ->
                                if (index > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                                EquipmentRow(
                                    item = item,
                                    onEdit = { viewModel.openEditDialog(item) },
                                    onDeactivate = { pendingDeactivation = item },
                                    onActivate = { viewModel.activate(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isEditorOpen) {
            EquipmentEditorDialog(
                uiState = uiState,
                onNameChange = viewModel::onNameChange,
                onCategoryChange = viewModel::onCategoryChange,
                onQuantityChange = viewModel::onQuantityChange,
                onPriceChange = viewModel::onPriceChange,
                onCurrencyChange = viewModel::onCurrencyChange,
                onPriceUnitChange = viewModel::onPriceUnitChange,
                onNotesChange = viewModel::onNotesChange,
                onDismiss = viewModel::closeEditor,
                onSave = {
                    viewModel.saveEditor()
                    BackupScheduler.scheduleAfterChange(context)
                }
            )
        }

        pendingDeactivation?.let { item ->
            AlertDialog(
                onDismissRequest = { pendingDeactivation = null },
                title = { Text("Deaktiviraj opremu?") },
                text = {
                    Text(
                        "\"${item.name}\" više neće biti ponuđena pri zakazivanju. " +
                            "Postojeća zakazivanja ostaju netaknuta."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deactivate(item.id)
                        pendingDeactivation = null
                    }) {
                        Text("Deaktiviraj", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeactivation = null }) {
                        Text("Otkaži")
                    }
                }
            )
        }
    }
}

@Composable
private fun CategoryHeader(category: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 14.dp, end = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BanketSectionLabel(category)
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = BanketMono),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = BanketGoldLine
        )
    }
}

@Composable
fun EquipmentRow(
    item: EquipmentEntity,
    onEdit: () -> Unit,
    onDeactivate: () -> Unit,
    onActivate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // Cena je zlatna i monospace — najbrže se čita pri dogovoru sa mušterijom.
            Text(
                text = formatUnitPrice(item.defaultUnitPrice, item.currency, item.priceUnit),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = BanketMono),
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BanketChip("${item.totalQuantity} kom na stanju")
                // "Aktivno" se ne ispisuje — to je normalno stanje; vidi se samo izuzetak.
                if (!item.active) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                            text = "Neaktivno",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Izmeni opremu: ${item.name}",
                tint = BanketIconMuted
            )
        }
        // Deaktiviranje je povratno: neaktivna stavka se istim dugmetom vraća u upotrebu.
        if (item.active) {
            IconButton(onClick = onDeactivate) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Deaktiviraj opremu: ${item.name}",
                    tint = BanketIconMuted
                )
            }
        } else {
            IconButton(onClick = onActivate) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = "Vrati u upotrebu: ${item.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

internal fun itemWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "stavka"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "stavke"
    else -> "stavki"
}
