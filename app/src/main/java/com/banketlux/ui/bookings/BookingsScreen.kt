package com.banketlux.ui.bookings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banketlux.data.repository.BookingRepository
import com.banketlux.ui.components.BanketCard
import com.banketlux.ui.components.BanketChip
import com.banketlux.ui.components.BanketEmptyState
import com.banketlux.ui.components.BanketTopBar
import com.banketlux.ui.components.formatRsd
import com.banketlux.ui.theme.BanketLabelCaps
import com.banketlux.ui.theme.banketItemMotion
import com.banketlux.ui.theme.BanketMono
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingsScreen(
    bookingRepository: BookingRepository,
    onCreateBooking: () -> Unit,
    onEditBooking: (Long) -> Unit
) {
    val factory = remember(bookingRepository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BookingListViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BookingListViewModel(bookingRepository) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val viewModel: BookingListViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    val listState = rememberLazyListState()
    // FAB se skuplja u ikonicu čim lista krene da se skroluje, a vraća na vrhu (Material obrazac).
    val fabExpanded by remember { derivedStateOf { !listState.canScrollBackward } }

    // Pri svakom povratku na ekran pomeri granicu "danas" — bitno posle ponoći.
    LaunchedEffect(Unit) { viewModel.refreshToday() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BanketTopBar(
                title = "Zakazivanja",
                subtitle = upcomingLabel(uiState.items.size),
                showLogo = true
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                expanded = fabExpanded,
                text = { Text("Novo zakazivanje") },
                icon = {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = if (fabExpanded) null else "Novo zakazivanje"
                    )
                },
                onClick = onCreateBooking,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large
            )
        }
    ) { innerPadding ->
        if (uiState.items.isEmpty()) {
            BanketEmptyState(
                title = "Nema budućih zakazivanja",
                message = "Dodaj zakazivanje preko dugmeta dole desno.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.items, key = { it.bookingId }) { item ->
                    BookingListRow(
                        modifier = banketItemMotion(),
                        item = item,
                        onClick = { onEditBooking(item.bookingId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingListRow(
    item: BookingListItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rentalLabel = remember(item.rentalStartDate, item.rentalEndDate) {
        formatRentalPeriod(item.rentalStartDate, item.rentalEndDate)
    }
    val daysLabel = remember(item.rentalStartDate, item.rentalEndDate) {
        rentalDays(item.rentalStartDate, item.rentalEndDate)?.let { "$it ${dayWord(it)}" }
    }

    BanketCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Period najma je zlatan, monospace, u jednom redu; trajanje kao čip desno.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = rentalLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = BanketMono),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (daysLabel != null) {
                    BanketChip(daysLabel)
                }
            }

            if (item.customerName.isNotBlank()) {
                Text(
                    text = item.customerName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactLine(icon = Icons.Default.Phone, value = item.customerPhone)
                ContactLine(icon = Icons.Default.Place, value = item.location)
            }

            if (item.equipmentLines.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.equipmentLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UKUPNO",
                    style = BanketLabelCaps,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatRsd(item.totalPriceRsd),
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = BanketMono),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Ikonica + vrednost u jednom redu. Prazne vrednosti se ne prikazuju. */
@Composable
private fun ContactLine(icon: ImageVector, value: String) {
    if (value.isBlank()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val BanketDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.")

internal fun formatDate(rawDate: String): String =
    runCatching { LocalDate.parse(rawDate).format(BanketDateFormatter) }
        .getOrElse { rawDate }

internal fun formatRentalPeriod(rawStart: String, rawEnd: String): String {
    val start = formatDate(rawStart)
    val end = formatDate(rawEnd)
    return if (start == end) start else "$start – $end"
}

/** Broj dana najma (uključivo), ili null ako datumi nisu ispravni. */
internal fun rentalDays(rawStart: String, rawEnd: String): Int? =
    runCatching {
        (ChronoUnit.DAYS.between(LocalDate.parse(rawStart), LocalDate.parse(rawEnd)) + 1).toInt()
    }.getOrNull()?.takeIf { it >= 1 }

internal fun upcomingLabel(count: Int): String = when {
    count == 0 -> "Nema predstojećih"
    count % 10 == 1 && count % 100 != 11 -> "$count predstojeće zakazivanje"
    count % 10 in 2..4 && count % 100 !in 12..14 -> "$count predstojeća zakazivanja"
    else -> "$count predstojećih zakazivanja"
}
