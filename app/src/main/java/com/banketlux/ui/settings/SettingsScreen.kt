package com.banketlux.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.banketlux.data.backup.BackupMapper
import com.banketlux.data.backup.BackupRepository
import com.banketlux.data.backup.DriveSyncState
import com.banketlux.data.backup.DriveVersion
import com.banketlux.data.backup.GoogleDriveBackup
import com.banketlux.data.calendar.CalendarImportCandidate
import com.banketlux.data.calendar.GoogleCalendarSync
import com.banketlux.data.repository.BookingRepository
import com.banketlux.ui.components.BanketAlertPanel
import com.banketlux.ui.components.BanketInfoRow
import com.banketlux.ui.components.BanketSection
import com.banketlux.ui.components.BanketSwitchRow
import com.banketlux.ui.components.BanketTone
import com.banketlux.ui.components.BanketTopBar
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(
    backupRepository: BackupRepository,
    driveBackup: GoogleDriveBackup,
    calendarSync: GoogleCalendarSync,
    bookingRepository: BookingRepository
) {
    val context = LocalContext.current
    val factory = remember(backupRepository, driveBackup, calendarSync, bookingRepository) {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return SettingsViewModel(
                        backupRepository,
                        driveBackup,
                        calendarSync,
                        bookingRepository
                    ) as T
                }
                error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    // Upis u kalendar radi u pozadini — status se osvežava dok je ekran otvoren.
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshCalendarState()
            kotlinx.coroutines.delay(3_000)
        }
    }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        }.getOrNull()
        viewModel.onSignInResult(account)
    }

    val buttonsEnabled = !uiState.isBusy
    val isSyncing = uiState.syncState is DriveSyncState.Syncing

    Scaffold(
        topBar = {
            BanketTopBar(
                title = "Podešavanja",
                subtitle = "Backup podataka"
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BanketSection("Google Drive", modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val email = uiState.signedInEmail
                    // Adresa ide u svoj red preko cele širine, da ne prelama na dva reda.
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Nalog",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = email ?: "Nisi prijavljen",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (email != null) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BanketInfoRow("Poslednji backup", formatTimestamp(uiState.lastSyncTime))

                    if (email == null) {
                        Text(
                            text = "Prijavi se Google nalogom da bi se podaci sami čuvali u oblaku. " +
                                "Backup ide u privatni folder aplikacije — ne vidiš ga među svojim " +
                                "fajlovima i druge aplikacije mu ne pristupaju.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled,
                            onClick = { signInLauncher.launch(driveBackup.getSignInIntent()) }
                        ) {
                            Text("Prijavi se Google nalogom")
                        }
                    } else {
                        BanketSwitchRow(
                            title = "Automatski backup",
                            description = "Šalje se sam, jednom dnevno i posle izmena.",
                            checked = uiState.autoBackupEnabled,
                            onCheckedChange = { viewModel.setAutoBackupEnabled(context, it) }
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled && !isSyncing,
                            onClick = viewModel::backupToDrive
                        ) {
                            Text(if (isSyncing) "Slanje..." else "Pošalji backup sada")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled && !isSyncing,
                            onClick = { showRestoreConfirm = true }
                        ) {
                            Text("Vrati podatke sa Drive-a")
                        }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled && !isSyncing,
                            onClick = viewModel::loadVersions
                        ) {
                            Text("Vrati stariju verziju")
                        }
                        Text(
                            text = "U oblaku ostaje 10 najnovijih verzija, po jedna dnevno za 14 dana " +
                                "i po jedna mesečno za 12 meseci.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled,
                            onClick = viewModel::signOut
                        ) {
                            Text("Odjavi se")
                        }
                    }
                }
            }

            BanketSection("Google kalendar", modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BanketSwitchRow(
                        title = "Upisuj zakazivanja u kalendar",
                        description = "Svako zakazivanje postaje celodnevni događaj sa " +
                            "imenom mušterije. Podsetnik dan ranije šalje aplikacija.",
                        checked = uiState.calendarSyncEnabled,
                        onCheckedChange = { viewModel.setCalendarSyncEnabled(context, it) }
                    )

                    if (uiState.calendarSyncEnabled &&
                        uiState.signedInEmail != null &&
                        !uiState.calendarPermissionMissing
                    ) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled,
                            onClick = { viewModel.syncAllBookingsToCalendar(context) }
                        ) {
                            Text("Upiši sva zakazivanja u kalendar")
                        }
                    }

                    if (uiState.calendarCanImport) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = buttonsEnabled && !uiState.isLoadingCalendarImport,
                            onClick = viewModel::loadCalendarImport
                        ) {
                            Text(
                                if (uiState.isLoadingCalendarImport) {
                                    "Čitam kalendar..."
                                } else {
                                    "Uvezi iz kalendara"
                                }
                            )
                        }
                    }

                    val calendarStatus = uiState.calendarStatus
                    if (uiState.calendarSyncEnabled && calendarStatus != null) {
                        BanketAlertPanel(
                            title = if (calendarStatus.success) {
                                "Poslednji upis uspeo · ${formatTimestamp(calendarStatus.timeMillis)}"
                            } else {
                                "Upis u kalendar ne radi · ${formatTimestamp(calendarStatus.timeMillis)}"
                            },
                            message = calendarStatus.message,
                            tone = if (calendarStatus.success) BanketTone.Info else BanketTone.Error
                        )
                    }

                    if (uiState.calendarSyncEnabled && uiState.signedInEmail == null) {
                        BanketAlertPanel(
                            title = "Nisi prijavljen",
                            message = "Prijavi se Google nalogom da bi upis u kalendar radio.",
                            tone = BanketTone.Warning
                        )
                    }

                    if (uiState.calendarPermissionMissing) {
                        BanketAlertPanel(
                            title = "Potrebna dozvola za kalendar",
                            message = "Tvoj nalog je prijavljen pre nego što je kalendar dodat. " +
                                "Odjavi se pa se prijavi ponovo da odobriš pristup kalendaru.",
                            tone = BanketTone.Warning
                        )
                    }
                }
            }

            if (uiState.isCheckingExistingBackup) {
                BanketAlertPanel(
                    title = "Provera oblaka",
                    message = "Gledam ima li već backup-a na ovom nalogu...",
                    tone = BanketTone.Info
                )
            }

            if (uiState.isBusy || isSyncing) {
                BanketAlertPanel(
                    title = "Radnja je u toku",
                    message = "Sačekaj da se trenutna radnja završi.",
                    tone = BanketTone.Info
                )
            }

            (uiState.resultMessage ?: (uiState.syncState as? DriveSyncState.Error)?.message)
                ?.let { message ->
                    val success = uiState.resultMessage != null && uiState.isSuccessMessage
                    BanketAlertPanel(
                        title = if (success) "Uspešno" else "Nije uspelo",
                        message = message,
                        tone = if (success) BanketTone.Success else BanketTone.Error
                    )
                }
        }
    }

    uiState.foundExistingBackup?.let { found ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { /* namerno prazno: odluka je obavezna */ },
            title = { Text("Nađen backup u oblaku") },
            text = {
                Text(
                    "Na ovom Google nalogu već postoji backup od ${formatTimestamp(found.createdAtMillis)}. " +
                        "Hoćeš li da vratiš te podatke? Trenutni podaci na telefonu biće zamenjeni.\n\n" +
                        "Dok ne odlučiš, automatski backup je zaustavljen da ne pregazi zatečene podatke."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::restoreExistingBackup) {
                    Text("Vrati iz oblaka")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::declineExistingBackup) {
                    Text("Ne, zadrži ove")
                }
            }
        )
    }

    uiState.backupDecision?.let { decision ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelBackupDecision,
            title = {
                Text(if (decision.cloudNotAdopted) "U oblaku već postoji backup" else "Na telefonu nema podataka")
            },
            text = {
                // Material dijalog ima najviše dve radnje; rizična treća ide u telo, kao tekstualno dugme.
                Column {
                    Text(
                        if (decision.cloudNotAdopted) {
                            "Na ovom Google nalogu postoji backup od ${formatTimestamp(decision.latest.createdAtMillis)}, " +
                                "a ova instalacija ga još nije preuzela" +
                                (if (decision.localIsEmpty) " — i na telefonu trenutno NEMA zakazivanja ni zarade" else "") +
                                ". Ako sada pošalješ backup, on postaje najnovija verzija. Preporuka: prvo vrati podatke iz oblaka."
                        } else {
                            "U oblaku postoji backup od ${formatTimestamp(decision.latest.createdAtMillis)}, a na telefonu " +
                                "trenutno nema zakazivanja, zarade ni kupaca. Ako nastaviš, prazan backup postaje najnovija verzija."
                        }
                    )
                    TextButton(
                        modifier = Modifier.align(Alignment.End),
                        onClick = viewModel::confirmBackupAnyway
                    ) {
                        Text("Ipak napravi backup", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::restoreInsteadOfBackup) {
                    Text("Vrati iz oblaka")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelBackupDecision) {
                    Text("Otkaži")
                }
            }
        )
    }

    if (uiState.emptyRestorePending) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelEmptyRestore,
            title = { Text("Backup je prazan") },
            text = {
                Text(
                    "Ovaj backup nema nijedno zakazivanje, zaradu ni kupca, a na telefonu postoje podaci. " +
                        "Ako nastaviš, podaci na telefonu biće obrisani."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEmptyRestore) {
                    Text("Ipak vrati", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelEmptyRestore) {
                    Text("Otkaži")
                }
            }
        )
    }

    uiState.versions?.let { versions ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissVersions,
            title = { Text("Verzije u oblaku") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Izaberi verziju koju vraćaš. Trenutni podaci u aplikaciji biće zamenjeni.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    versions.forEach { version ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.restoreVersion(version) }
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(formatTimestamp(version.createdAtMillis), fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = describeVersion(version),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissVersions) {
                    Text("Otkaži")
                }
            }
        )
    }

    if (showRestoreConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("Vratiti podatke sa Drive-a?") },
            text = {
                Text(
                    "Trenutni podaci u aplikaciji biće zamenjeni poslednjim backup-om iz oblaka. " +
                        "Ova radnja se ne može poništiti."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    viewModel.restoreFromDrive()
                }) {
                    Text("Vrati", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text("Otkaži")
                }
            }
        )
    }

    uiState.calendarImport?.let { events ->
        CalendarImportDialog(
            events = events,
            onImport = { selected -> viewModel.importCalendarEvents(context, selected) },
            onDismiss = viewModel::dismissCalendarImport
        )
    }
}

/** Spisak budućih događaja iz kalendara; uvoze se samo štiklirani. */
@Composable
private fun CalendarImportDialog(
    events: List<CalendarImportCandidate>,
    onImport: (List<CalendarImportCandidate>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIds by remember(events) { mutableStateOf(emptySet<String>()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy.") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uvezi iz kalendara") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Štikliraj poslove. Uvoze se samo naziv i datum, opremu dodaješ posle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                events.forEach { event ->
                    val checked = event.eventId in selectedIds
                    val toggle = {
                        selectedIds = if (checked) {
                            selectedIds - event.eventId
                        } else {
                            selectedIds + event.eventId
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = toggle),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = checked, onCheckedChange = { toggle() })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title.ifBlank { "Bez naziva" },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = if (event.startDate == event.endDate) {
                                    event.startDate.format(dateFormatter)
                                } else {
                                    "${event.startDate.format(dateFormatter)} – " +
                                        event.endDate.format(dateFormatter)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedIds.isNotEmpty(),
                onClick = { onImport(events.filter { it.eventId in selectedIds }) }
            ) {
                Text("Uvezi (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Otkaži")
            }
        }
    )
}

/** Brojevi zapisa se upisuju uz verziju pri otpremi; starije verzije imaju samo veličinu. */
private fun describeVersion(version: DriveVersion): String {
    val counts = version.counts
    val size = "${(version.sizeBytes + 1023) / 1024} KB"
    return if (counts == null) {
        "$size (sadržaj nije poznat — starija verzija)"
    } else {
        "zakazivanja: ${counts[BackupMapper.COUNT_BOOKINGS] ?: "?"}, " +
            "zarada: ${counts[BackupMapper.COUNT_EARNINGS] ?: "?"}, " +
            "kupaca: ${counts[BackupMapper.COUNT_CUSTOMERS] ?: "?"}, " +
            "opreme: ${counts[BackupMapper.COUNT_EQUIPMENT] ?: "?"} · $size"
    }
}

private fun formatTimestamp(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0) return "Još nijedan"
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy. HH:mm")
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
