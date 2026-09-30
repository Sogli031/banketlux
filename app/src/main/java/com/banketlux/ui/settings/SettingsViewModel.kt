package com.banketlux.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.banketlux.data.backup.BackupImportException
import com.banketlux.data.backup.BackupRepository
import com.banketlux.data.backup.BackupScheduler
import com.banketlux.data.backup.DriveSignInState
import com.banketlux.data.backup.DriveSyncState
import com.banketlux.data.backup.DriveVersion
import com.banketlux.data.backup.GoogleDriveBackup
import com.banketlux.data.calendar.CalendarImportCandidate
import com.banketlux.data.calendar.CalendarStatus
import com.banketlux.data.calendar.CalendarSyncScheduler
import com.banketlux.data.calendar.GoogleCalendarSync
import com.banketlux.data.local.entity.BookingEntity
import com.banketlux.data.repository.BookingRepository
import com.banketlux.domain.model.BookingStatus
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ručni backup koji bi zaklonio postojeći backup u oblaku — čeka izričitu odluku korisnika. */
data class BackupDecision(
    /** Ova instalacija još nije usvojila oblak (odluka o zatečenom backup-u nije doneta). */
    val cloudNotAdopted: Boolean,
    /** Na telefonu nema zakazivanja, zarade ni kupaca. */
    val localIsEmpty: Boolean,
    val latest: DriveVersion
)

data class SettingsUiState(
    val signInState: DriveSignInState = DriveSignInState.SignedOut,
    val syncState: DriveSyncState = DriveSyncState.Idle,
    val lastSyncTime: Long? = null,
    val autoBackupEnabled: Boolean = true,
    val resultMessage: String? = null,
    val isSuccessMessage: Boolean = false,
    val isBusy: Boolean = false,
    /** Backup zatečen u oblaku odmah po prijavi — korisnik bira da li ga vraća. */
    val foundExistingBackup: DriveVersion? = null,
    val isCheckingExistingBackup: Boolean = false,
    val backupDecision: BackupDecision? = null,
    /** Backup je prazan, a na telefonu ima podataka — čeka se potvrda. */
    val emptyRestorePending: Boolean = false,
    /** Lista verzija za izbor starije verzije; null = lista nije otvorena. */
    val versions: List<DriveVersion>? = null,
    val calendarSyncEnabled: Boolean = false,
    /** Nalozi prijavljeni pre uvođenja kalendara nemaju tu dozvolu. */
    val calendarPermissionMissing: Boolean = false,
    /** Ishod poslednjeg upisa u kalendar (iz pozadinskog posla); null = još nije bilo pokušaja. */
    val calendarStatus: CalendarStatus? = null,
    /** Prijavljen nalog ima dozvolu za kalendar, pa uvoz može da radi. */
    val calendarCanImport: Boolean = false,
    /** Događaji ponuđeni za uvoz; null = prozor nije otvoren. */
    val calendarImport: List<CalendarImportCandidate>? = null,
    val isLoadingCalendarImport: Boolean = false
) {
    val signedInEmail: String?
        get() = (signInState as? DriveSignInState.SignedIn)?.email
}

class SettingsViewModel(
    private val backupRepository: BackupRepository,
    private val driveBackup: GoogleDriveBackup,
    private val calendarSync: GoogleCalendarSync,
    private val bookingRepository: BookingRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pendingEmptyRestorePayload: String? = null

    init {
        viewModelScope.launch {
            combine(
                driveBackup.signInState,
                driveBackup.syncState,
                driveBackup.lastSyncTime,
                driveBackup.autoBackupEnabled
            ) { signIn, sync, lastSync, auto ->
                listOf(signIn, sync, lastSync, auto)
            }.collect { values ->
                _uiState.update {
                    it.copy(
                        signInState = values[0] as DriveSignInState,
                        syncState = values[1] as DriveSyncState,
                        lastSyncTime = values[2] as Long?,
                        autoBackupEnabled = values[3] as Boolean
                    )
                }
            }
        }

        // Ako je aplikacija zatvorena pre nego što je korisnik odgovorio na pitanje
        // o zatečenom backup-u, odluka se nudi ponovo — inače bi auto-backup ostao
        // trajno zamrznut, a korisnik ne bi znao da backup ne radi.
        if (driveBackup.isRestorePromptPending() && driveBackup.isSignedIn()) {
            checkForExistingBackup()
        }
    }

    fun refreshCalendarState() {
        _uiState.update {
            it.copy(
                calendarSyncEnabled = calendarSync.enabled.value,
                calendarPermissionMissing = calendarSync.enabled.value &&
                    driveBackup.isSignedIn() &&
                    !calendarSync.hasCalendarPermission(),
                calendarStatus = calendarSync.lastStatus(),
                calendarCanImport = driveBackup.isSignedIn() && calendarSync.hasCalendarPermission()
            )
        }
    }

    /** Otvara spisak budućih događaja iz kalendara koji još nisu u aplikaciji. */
    fun loadCalendarImport() {
        if (_uiState.value.isLoadingCalendarImport) return
        _uiState.update { it.copy(isLoadingCalendarImport = true) }
        viewModelScope.launch {
            val known = runCatching {
                bookingRepository.getAllBookingsWithLines()
                    .mapNotNull { it.booking.calendarEventId }
                    .toSet()
            }.getOrDefault(emptySet())
            calendarSync.listUpcomingEvents()
                .onSuccess { events ->
                    // Događaje koje je upisala sama aplikacija (ili već uvezene) ne nudi ponovo.
                    val fresh = events.filter { it.eventId !in known }
                    if (fresh.isEmpty()) {
                        report(true, "U kalendaru nema budućih događaja koji već nisu u aplikaciji.")
                    } else {
                        _uiState.update { it.copy(calendarImport = fresh) }
                    }
                }
                .onFailure { report(false, it.message ?: "Čitanje kalendara nije uspelo.") }
            _uiState.update { it.copy(isLoadingCalendarImport = false) }
        }
    }

    fun dismissCalendarImport() = _uiState.update { it.copy(calendarImport = null) }

    /**
     * Upisuje izabrane događaje kao zakazivanja — samo naziv i datumi, bez opreme.
     * Zakazivanje pamti id događaja, pa se u kalendaru ne pravi duplikat.
     */
    fun importCalendarEvents(context: Context, selected: List<CalendarImportCandidate>) {
        _uiState.update { it.copy(calendarImport = null) }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                selected.forEach { event ->
                    bookingRepository.saveBookingWithLines(
                        BookingEntity(
                            customerName = event.title.ifBlank { "Bez imena" },
                            customerPhone = "",
                            location = "",
                            eventDate = event.startDate.toString(),
                            rentalStartDate = event.startDate.toString(),
                            rentalEndDate = event.endDate.toString(),
                            status = BookingStatus.CONFIRMED,
                            notes = "",
                            totalPriceRsd = 0,
                            advancePaidRsd = 0,
                            amountPaidRsd = 0,
                            remainingDebtRsd = 0,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            calendarEventId = event.eventId
                        ),
                        emptyList()
                    )
                }
                selected.size
            }
                .onSuccess { count ->
                    BackupScheduler.scheduleAfterChange(context)
                    report(true, "Uvezeno iz kalendara: $count. Opremu dodaj u svakom zakazivanju.")
                }
                .onFailure { report(false, "Uvoz iz kalendara nije uspeo.") }
        }
    }

    fun setCalendarSyncEnabled(context: Context, enabled: Boolean) {
        calendarSync.setEnabled(enabled)
        refreshCalendarState()
        val ready = enabled && calendarSync.hasCalendarPermission()
        if (ready) {
            // Postojeća zakazivanja se upisuju odmah, da ne ostanu izvan kalendara.
            CalendarSyncScheduler.backfillAll(context)
        }
        val message = when {
            !enabled -> "Upis u Google kalendar je isključen."
            !driveBackup.isSignedIn() -> "Prvo se prijavi Google nalogom."
            !calendarSync.hasCalendarPermission() ->
                "Potrebna je dozvola za kalendar — odjavi se pa se prijavi ponovo."
            else -> "Uključeno. Upisujem i sva postojeća zakazivanja u kalendar."
        }
        report(ready, message)
    }

    /** Ručno ponavljanje naknadnog upisa, ako je nešto ostalo neupisano. */
    fun syncAllBookingsToCalendar(context: Context) {
        if (!calendarSync.hasCalendarPermission()) {
            report(false, "Potrebna je dozvola za kalendar — odjavi se pa se prijavi ponovo.")
            return
        }
        CalendarSyncScheduler.backfillAll(context, replace = true)
        report(true, "Upisujem sva zakazivanja u kalendar. Ishod se vidi ispod dugmeta.")
    }

    fun clearMessage() {
        _uiState.update { it.copy(resultMessage = null) }
        driveBackup.clearSyncState()
    }

    fun onSignInResult(account: GoogleSignInAccount?) {
        driveBackup.handleSignInResult(account)
        if (account == null) return
        report(true, "Prijavljen kao ${account.email ?: "Google nalog"}.")
        refreshCalendarState()
        checkForExistingBackup()
    }

    /**
     * Posle prijave proveri ima li već backup-a u oblaku. Ako ima, ponudi vraćanje —
     * inače bi sveža instalacija posle nekog vremena poslala praznu bazu kao novu verziju.
     */
    private fun checkForExistingBackup() {
        _uiState.update { it.copy(isCheckingExistingBackup = true) }
        viewModelScope.launch {
            val result = driveBackup.listVersions()
            val latest = result.getOrNull()?.maxByOrNull { it.createdAtMillis }
            if (result.isSuccess && latest == null) {
                // Pouzdano znamo da u oblaku nema ničega — auto-backup može slobodno da krene.
                driveBackup.dismissRestorePrompt()
            }
            if (result.isFailure) {
                // Provera nije uspela (mreža, tajmaut) — auto-backup ostaje zamrznut,
                // pitanje se ponavlja pri sledećem otvaranju Podešavanja.
                report(
                    false,
                    "Provera backup-a u oblaku nije uspela — pokušaću ponovo " +
                        "pri sledećem otvaranju Podešavanja."
                )
            }
            _uiState.update {
                it.copy(isCheckingExistingBackup = false, foundExistingBackup = latest)
            }
        }
    }

    /** Korisnik je odbio vraćanje — zatečeni backup ostaje, auto-backup se odmrzava. */
    fun declineExistingBackup() {
        driveBackup.dismissRestorePrompt()
        _uiState.update { it.copy(foundExistingBackup = null) }
        report(true, "Zadržani su podaci sa telefona. Backup u oblaku nije obrisan.")
    }

    fun restoreExistingBackup() {
        _uiState.update { it.copy(foundExistingBackup = null) }
        restoreFromDrive()
    }

    fun signOut() {
        viewModelScope.launch {
            driveBackup.signOut()
            report(true, "Odjavljen sa Google naloga.")
        }
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        driveBackup.setAutoBackupEnabled(enabled)
        if (enabled) {
            BackupScheduler.schedulePeriodic(context)
            report(true, "Automatski backup je uključen.")
        } else {
            report(true, "Automatski backup je isključen.")
        }
    }

    /** Pokreće radnju uz zauzetost ekrana; greška postaje poruka, a otkazivanje se propušta. */
    private fun launchBusy(block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, resultMessage = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                report(false, "Radnja nije uspela: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    /**
     * Ručni backup. "Prazno ne gazi popunjeno": dok oblak nije usvojen (odluka o zatečenom
     * backup-u nije doneta) ili je telefon bez podataka, a u oblaku već postoji backup —
     * ništa se ne šalje bez izričite potvrde. Neuspela provera oblaka nikad ne znači "prazno".
     */
    fun backupToDrive() = launchBusy {
        val pending = driveBackup.isRestorePromptPending()
        val localIsEmpty = !backupRepository.hasUserRecords()
        if (pending || localIsEmpty) {
            val versions = driveBackup.listVersions()
            if (versions.isFailure) {
                report(
                    false,
                    "Provera oblaka nije uspela (${versions.exceptionOrNull()?.message}). " +
                        "Backup NIJE poslat, da ne bi zaklonio backup koji možda već postoji."
                )
                return@launchBusy
            }
            val latest = versions.getOrNull()?.maxByOrNull { it.createdAtMillis }
            if (latest != null) {
                _uiState.update {
                    it.copy(backupDecision = BackupDecision(pending, localIsEmpty, latest))
                }
                return@launchBusy
            }
        }
        // Ovde je oblak pouzdano prazan ili je ova instalacija već usklađena sa njim.
        uploadBackup(userConfirmed = pending)
    }

    /** "Ipak napravi backup": korisnik izričito usvaja stanje sa telefona. */
    fun confirmBackupAnyway() {
        _uiState.update { it.copy(backupDecision = null) }
        launchBusy { uploadBackup(userConfirmed = true) }
    }

    fun cancelBackupDecision() {
        _uiState.update { it.copy(backupDecision = null) }
    }

    fun restoreInsteadOfBackup() {
        _uiState.update { it.copy(backupDecision = null) }
        restoreFromDrive()
    }

    private suspend fun uploadBackup(userConfirmed: Boolean) {
        val result = driveBackup.backup(userConfirmed = userConfirmed) { backupRepository.exportForUpload() }
        report(
            result.isSuccess,
            if (result.isSuccess) {
                "Backup je poslat na Google Drive."
            } else {
                result.exceptionOrNull()?.message ?: "Backup na Google Drive nije uspeo."
            }
        )
    }

    /** Vraća najnoviju verziju, ili tačno izabranu [version]. */
    fun restoreFromDrive(version: DriveVersion? = null) = launchBusy {
        val payload: String? = if (version != null) {
            val result = driveBackup.restoreVersion(version.fileId)
            if (result.isFailure) {
                report(false, result.exceptionOrNull()?.message ?: "Vraćanje sa Google Drive-a nije uspelo.")
                return@launchBusy
            }
            result.getOrNull()
        } else {
            val result = driveBackup.restoreLatest()
            if (result.isFailure) {
                report(false, result.exceptionOrNull()?.message ?: "Vraćanje sa Google Drive-a nije uspelo.")
                return@launchBusy
            }
            result.getOrNull()
        }
        if (payload == null) {
            report(false, "Na Google Drive-u još nema nijednog backup-a.")
            return@launchBusy
        }
        applyPayload(payload, confirmedEmpty = false)
    }

    private suspend fun applyPayload(payload: String, confirmedEmpty: Boolean) {
        if (!confirmedEmpty) {
            val backupHasRecords = try {
                backupRepository.backupHasUserRecords(payload)
            } catch (e: BackupImportException) {
                report(false, e.message ?: "Backup fajl nije podržan u ovoj verziji aplikacije.")
                return
            }
            // Prazan backup preko popunjenog telefona traži izričitu potvrdu.
            if (!backupHasRecords && backupRepository.hasUserRecords()) {
                pendingEmptyRestorePayload = payload
                _uiState.update { it.copy(emptyRestorePending = true) }
                return
            }
        }

        val validation = backupRepository.importFromJson(payload)
        if (validation.isValid) {
            driveBackup.dismissRestorePrompt()
            // Vraćanje primenjuje i podešavanja iz backup-a (kalendar, auto-backup).
            refreshCalendarState()
            report(true, "Podaci su vraćeni sa Google Drive-a. ${validation.message.orEmpty()}".trim())
        } else {
            report(false, validation.message ?: "Backup fajl nije podržan u ovoj verziji aplikacije.")
        }
    }

    fun confirmEmptyRestore() {
        val payload = pendingEmptyRestorePayload ?: return
        pendingEmptyRestorePayload = null
        _uiState.update { it.copy(emptyRestorePending = false) }
        launchBusy { applyPayload(payload, confirmedEmpty = true) }
    }

    fun cancelEmptyRestore() {
        pendingEmptyRestorePayload = null
        _uiState.update { it.copy(emptyRestorePending = false) }
    }

    /** Lista verzija u oblaku, za vraćanje starije verzije. */
    fun loadVersions() = launchBusy {
        val result = driveBackup.listVersions()
        val versions = result.getOrNull()
        when {
            versions == null ->
                report(false, result.exceptionOrNull()?.message ?: "Lista verzija nije učitana.")
            versions.isEmpty() -> report(false, "Na Google Drive-u još nema nijednog backup-a.")
            else -> {
                driveBackup.clearSyncState()
                _uiState.update { it.copy(versions = versions) }
            }
        }
    }

    fun dismissVersions() {
        _uiState.update { it.copy(versions = null) }
    }

    fun restoreVersion(version: DriveVersion) {
        _uiState.update { it.copy(versions = null) }
        restoreFromDrive(version)
    }

    private fun report(success: Boolean, message: String) {
        _uiState.update { it.copy(isSuccessMessage = success, resultMessage = message) }
    }
}
