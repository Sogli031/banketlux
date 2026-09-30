package com.banketlux.ui.earnings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banketlux.data.backup.BackupScheduler
import com.banketlux.data.local.entity.EarningEntity
import com.banketlux.data.repository.EarningsRepository
import com.banketlux.domain.earnings.MonthEarnings
import com.banketlux.domain.earnings.YearEarnings
import com.banketlux.ui.components.BanketCard
import com.banketlux.ui.components.BanketEmptyState
import com.banketlux.ui.components.BanketSectionLabel
import com.banketlux.ui.components.BanketTopBar
import com.banketlux.ui.bookings.formatDate
import com.banketlux.ui.components.formatRsd
import com.banketlux.ui.theme.BanketGold
import com.banketlux.ui.theme.BanketIconMuted
import com.banketlux.ui.theme.BanketInkSoft
import com.banketlux.ui.theme.BanketMono

@Composable
fun EarningsScreen(
    earningsRepository: EarningsRepository
) {
    val factory = remember(earningsRepository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(EarningsViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return EarningsViewModel(earningsRepository) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val viewModel: EarningsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val overview = uiState.overview
    val context = LocalContext.current
    var entryToDelete by remember { mutableStateOf<EarningEntity?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BanketTopBar(
                title = "Zarada",
                subtitle = "Završeni poslovi",
                showLogo = true
            )
        }
    ) { innerPadding ->
        if (overview.years.isEmpty() && overview.undatedCount == 0) {
            BanketEmptyState(
                title = "Još nema zarade",
                message = "Kad se najam završi, zakazivanje se automatski prebacuje ovde " +
                    "sa cenom koja je važila u tom trenutku.",
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
                    top = 6.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "total") {
                    TotalCard(
                        totalRsd = overview.totalRsd,
                        jobCount = overview.jobCount
                    )
                }

                if (overview.undatedCount > 0) {
                    item(key = "undated") {
                        Text(
                            text = "Uključeno u ukupno, ali bez čitljivog datuma (nije u godinama): " +
                                "${formatRsd(overview.undatedRsd)} · " +
                                "${overview.undatedCount} ${jobWord(overview.undatedCount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                overview.years.forEach { year ->
                    item(key = "year-${year.year}") {
                        YearCard(
                            year = year,
                            isExpanded = year.year in uiState.expandedYears,
                            onToggle = { viewModel.toggleYear(year.year) },
                            expandedMonths = uiState.expandedMonths,
                            onToggleMonth = viewModel::toggleMonth,
                            onDeleteEntry = { entryToDelete = it }
                        )
                    }
                }
            }
        }
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Obriši iz zarade?") },
            text = {
                Text(
                    "${entry.customerName.ifBlank { "Bez imena" }} " +
                        "(${formatDate(entry.rentalEndDate)}), ${formatRsd(entry.amountRsd)}.\n\n" +
                        "Posao se briše iz zarade i ne može se vratiti."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    entryToDelete = null
                    viewModel.deleteEarning(entry.id) {
                        BackupScheduler.scheduleAfterChange(context)
                    }
                }) {
                    Text("Obriši", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Otkaži")
                }
            }
        )
    }
}

/** Hero blok: zlatni naslov, iznos u monospace, broj poslova ispod. */
@Composable
private fun TotalCard(totalRsd: Int, jobCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, BanketGold.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BanketSectionLabel("Ukupno zarađeno")
            Text(
                text = formatRsd(totalRsd),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = "$jobCount ${jobWord(jobCount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YearCard(
    year: YearEarnings,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    expandedMonths: Set<String>,
    onToggleMonth: (String) -> Unit,
    onDeleteEntry: (EarningEntity) -> Unit
) {
    BanketCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${year.year}.",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${year.jobCount} ${jobWord(year.jobCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatRsd(year.amountRsd),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = BanketMono),
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) {
                    "Sakrij mesece za ${year.year}."
                } else {
                    "Prikaži mesece za ${year.year}."
                },
                tint = BanketIconMuted
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            ) {
                year.months.forEach { month ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val key = "${month.year}-${month.month}"
                    val monthExpanded = key in expandedMonths
                    MonthRow(month, isExpanded = monthExpanded, onToggle = { onToggleMonth(key) })
                    AnimatedVisibility(visible = monthExpanded) {
                        Column {
                            month.entries.forEach { entry ->
                                EntryRow(entry, onDelete = { onDeleteEntry(entry) })
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Jedan posao u otvorenom mesecu, sa dugmetom za brisanje. */
@Composable
private fun EntryRow(entry: EarningEntity, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.customerName.ifBlank { "Bez imena" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatDate(entry.rentalEndDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatRsd(entry.amountRsd),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = BanketMono),
            color = BanketInkSoft
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Obriši ${entry.customerName} iz zarade",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun MonthRow(month: MonthEarnings, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = month.label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${month.jobCount} ${jobWord(month.jobCount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatRsd(month.amountRsd),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = BanketMono),
            color = BanketInkSoft
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Sakrij poslove" else "Prikaži poslove",
            tint = BanketIconMuted
        )
    }
}

internal fun jobWord(count: Int): String = when {
    count % 10 == 1 && count % 100 != 11 -> "posao"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "posla"
    else -> "poslova"
}
