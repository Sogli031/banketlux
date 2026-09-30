package com.banketlux.ui.bookings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.banketlux.domain.model.TableclothWashing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.banketlux.data.calendar.CalendarSyncScheduler
import com.banketlux.notifications.BookingReminders
import com.banketlux.data.repository.BookingRepository
import com.banketlux.data.repository.EquipmentRepository
import com.banketlux.data.repository.SettingsRepository
import com.banketlux.ui.components.BanketAlertPanel
import com.banketlux.ui.components.BanketSection
import com.banketlux.ui.components.BanketSectionLabel
import com.banketlux.ui.components.BanketTone
import com.banketlux.ui.components.BanketTopBar
import com.banketlux.ui.components.formatRsd
import com.banketlux.ui.theme.BanketGold
import com.banketlux.ui.theme.BanketMono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingEditorScreen(
    bookingRepository: BookingRepository,
    equipmentRepository: EquipmentRepository,
    settingsRepository: SettingsRepository,
    bookingId: Long?,
    onBack: () -> Unit
) {
    val factory = remember(bookingRepository, equipmentRepository, settingsRepository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(BookingEditorViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return BookingEditorViewModel(
                        bookingRepository,
                        equipmentRepository,
                        settingsRepository
                    ) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val context = LocalContext.current
    val viewModel: BookingEditorViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()
    val equipmentItems by viewModel.equipmentItems.collectAsState()

    val tentIds by viewModel.tentEquipmentIds.collectAsState()

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    // Nazad (dugme i sistemski gest) ne baca unos tiho: ako ima izmena, pita se.
    val hasUnsavedChanges = viewModel.hasUnsavedChanges(uiState) && !uiState.isSaving && !uiState.isDeleting
    BackHandler(enabled = hasUnsavedChanges) { showDiscardDialog = true }
    val requestBack: () -> Unit = { if (hasUnsavedChanges) showDiscardDialog = true else onBack() }
    val onSaved: (Long) -> Unit = { savedId ->
        BackupScheduler.scheduleAfterChange(context)
        CalendarSyncScheduler.syncBooking(context, savedId)
        BookingReminders.checkNow(context)
        onBack()
    }

    LaunchedEffect(bookingId) {
        viewModel.initialize(bookingId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BanketTopBar(
                title = if (bookingId == null) "Novo zakazivanje" else "Izmeni zakazivanje",
                subtitle = uiState.customerName.takeIf { it.isNotBlank() },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Nazad"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                BanketSection("Mušterija", modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.customerName,
                            onValueChange = viewModel::onCustomerNameChange,
                            label = { Text("Ime i prezime") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.customerPhone,
                            onValueChange = viewModel::onCustomerPhoneChange,
                            label = { Text("Telefon") },
                            singleLine = true
                        )
                    }
                }
            }

            item {
                BanketSection("Događaj", modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Početak i kraj najma stoje jedan pored drugog — kraća sekcija.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DatePickerField(
                                modifier = Modifier.weight(1f),
                                label = "Početak najma",
                                value = uiState.rentalStartDate,
                                onDatePicked = viewModel::onRentalStartPicked
                            )
                            DatePickerField(
                                modifier = Modifier.weight(1f),
                                label = "Kraj najma",
                                value = uiState.rentalEndDate,
                                onDatePicked = viewModel::onRentalEndPicked
                            )
                        }
                        Text(
                            text = "Trajanje najma: ${uiState.rentalDays} ${dayWord(uiState.rentalDays)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        if (uiState.dateConflicts.isNotEmpty()) {
                            BanketAlertPanel(
                                title = "Datum je već zauzet",
                                message = "Na ove datume već imaš: " +
                                    uiState.dateConflicts.joinToString() + ".",
                                tone = BanketTone.Warning
                            )
                        }
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.location,
                            onValueChange = viewModel::onLocationChange,
                            label = { Text("Lokacija") },
                            singleLine = true
                        )
                    }
                }
            }

            item {
                BanketSection("Oprema i usluge", modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.lines.forEach { line ->
                            BookingLineEditor(
                                line = line,
                                equipmentOptions = equipmentItems,
                                rentalDays = uiState.rentalDays,
                                eurToRsdRate = uiState.eurToRsdRate,
                                onEquipmentSelected = { selectedId ->
                                    viewModel.onLineEquipmentSelected(line.id, selectedId)
                                },
                                onQuantityChange = { viewModel.onLineQuantityChange(line.id, it) },
                                onPriceChange = { viewModel.onLinePriceChange(line.id, it) },
                                onNotesChange = { viewModel.onLineNotesChange(line.id, it) },
                                onRemove = { viewModel.removeLine(line.id) },
                                // Pranje stolnjaka se nudi posebno uz svaku šatru, u njenom okviru.
                                isTent = line.equipmentItemId in tentIds,
                                onWashingChange = { viewModel.onLineWashingChange(line.id, it) },
                                onWashingPriceChange = { viewModel.onLineWashingPriceChange(line.id, it) }
                            )
                        }
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, BanketGold.copy(alpha = 0.5f)),
                            onClick = viewModel::addLine
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("Dodaj stavku", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            item {
                // Blok ukupne cene: istaknuta površina sa zlatnim okvirom i iznosom u monospace.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, BanketGold.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BanketSectionLabel("Ukupno za ${uiState.rentalDays} ${dayWord(uiState.rentalDays)}")
                        Text(
                            text = formatRsd(uiState.totalPriceRsd),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Zbir se ažurira dok dodaješ stavke. Evro cene su preračunate " +
                                "po kursu 1 EUR = ${"%.2f".format(uiState.eurToRsdRate)} RSD.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.eurRateProblem?.let { problem ->
                            BanketAlertPanel(
                                title = "Problem sa kursom evra",
                                message = problem,
                                tone = BanketTone.Warning
                            )
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = viewModel::retryEurRate
                            ) {
                                Text("Pokušaj ponovo da preuzmeš kurs")
                            }
                        }
                    }
                }
            }

            item {
                BanketSection("Napomena", modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.notes,
                        onValueChange = viewModel::onNotesChange,
                        label = { Text("Napomena") }
                    )
                }
            }

            items(uiState.validationErrors, key = { "${it.field}-${it.message}" }) { error ->
                BanketAlertPanel(
                    title = "Proveri unos",
                    message = error.message,
                    tone = BanketTone.Error
                )
            }

            items(uiState.availabilityWarnings, key = { it.equipmentItemId }) { warning ->
                BanketAlertPanel(
                    title = if (warning.isShortage) {
                        "Nema dovoljno: ${warning.equipmentName}"
                    } else {
                        "Delom zauzeto: ${warning.equipmentName}"
                    },
                    message = warning.message(),
                    tone = if (warning.isShortage) BanketTone.Warning else BanketTone.Info
                )
            }

            item {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !uiState.isSaving && !uiState.isDeleting,
                    onClick = { viewModel.saveBooking(onSaved = onSaved) }
                ) {
                    Text(
                        text = if (uiState.isSaving) "Čuvanje..." else "Sačuvaj zakazivanje",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (uiState.bookingId != null) {
                item {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isSaving && !uiState.isDeleting,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = { showDeleteDialog = true }
                    ) {
                        Text(if (uiState.isDeleting) "Brisanje..." else "Obriši zakazivanje")
                    }
                }
            }
        }
    }

    uiState.overlapToConfirm?.let { conflicts ->
        AlertDialog(
            onDismissRequest = viewModel::dismissOverlapConfirm,
            title = { Text("Datum je već zauzet") },
            text = {
                val equipmentLines = uiState.availabilityWarnings.joinToString("\n") { warning ->
                    "• ${warning.equipmentName}: zauzeto ${warning.reservedQuantity} od " +
                        "${warning.totalQuantity}, slobodno ${warning.availableQuantity}" +
                        if (warning.isShortage) {
                            " — fali ${warning.requestedQuantity - warning.availableQuantity}"
                        } else {
                            ""
                        }
                }
                Text(
                    "Na ove datume već imaš:\n" +
                        conflicts.joinToString("\n") { "• $it" } +
                        (if (equipmentLines.isNotEmpty()) "\n\nOprema:\n$equipmentLines" else "") +
                        "\n\nDa li ipak da sačuvam?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissOverlapConfirm()
                    viewModel.saveBooking(overlapConfirmed = true, onSaved = onSaved)
                }) {
                    Text("Sačuvaj ipak")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissOverlapConfirm) {
                    Text("Otkaži")
                }
            }
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Odbaciti izmene?") },
            text = { Text("Imaš nesačuvane izmene. Ako izađeš, biće izgubljene.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text("Odbaci", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Nastavi uređivanje")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Obriši zakazivanje?") },
            text = { Text("Ova radnja se ne može poništiti.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteBooking { bookingId, calendarEventId ->
                        BackupScheduler.scheduleAfterChange(context)
                        CalendarSyncScheduler.bookingDeleted(context, bookingId, calendarEventId)
                        onBack()
                    }
                }) {
                    Text("Obriši", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Otkaži")
                }
            }
        )
    }
}

internal fun dayWord(days: Int): String = when {
    days % 10 == 1 && days % 100 != 11 -> "dan"
    else -> "dana"
}

/** Pločica sa datumom; ceo dodir otvara kalendar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    label: String,
    value: LocalDate,
    onDatePicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy.") }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    Surface(
        onClick = { showDialog = true },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = BanketMono)
                )
            }
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Otvori kalendar za $label",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDialog) {
        val initialSelectedDateMillis = remember(value) {
            value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedDateMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            val selectedDate = Instant.ofEpochMilli(selectedMillis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDatePicked(selectedDate)
                        }
                        showDialog = false
                    }
                ) {
                    Text("U redu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Otkaži")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun AvailabilityWarning.message(): String = buildString {
    append("Ukupno $totalQuantity, zauzeto $reservedQuantity, slobodno $availableQuantity. ")
    append("Tražiš $requestedQuantity")
    if (isShortage) append(", fali ${requestedQuantity - availableQuantity}")
    append(".")
    if (conflictLabels.isNotEmpty()) {
        append("\nZauzeto kod: ${conflictLabels.joinToString()}.")
    }
}
