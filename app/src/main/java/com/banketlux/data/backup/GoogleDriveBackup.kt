package com.banketlux.data.backup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.drive.DriveScopes
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "GoogleDriveBackup"
private const val APP_DATA_FOLDER = "appDataFolder"
private const val PREFS_NAME = "banketlux_backup_prefs"
private const val KEY_LAST_SYNC_TIME = "last_sync_time"
private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
private const val KEY_RESTORE_PROMPT_PENDING = "restore_prompt_pending"
private const val NETWORK_TIMEOUT_MS = 30_000L
private const val FILE_PREFIX = "banketlux_backup_"
private const val FILE_SUFFIX = ".json"


sealed interface DriveSignInState {
    data object SignedOut : DriveSignInState
    data class SignedIn(val email: String) : DriveSignInState
}

sealed interface DriveSyncState {
    data object Idle : DriveSyncState
    data object Syncing : DriveSyncState
    data object Success : DriveSyncState
    data class Error(val message: String) : DriveSyncState
}

data class DriveVersion(
    val fileId: String,
    val fileName: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    /** Brojevi zapisa upisani uz verziju pri otpremi; null za verzije napravljene ranije. */
    val counts: Map<String, String>? = null
)

/** Ishod traženja najnovije verzije: razlikuje "nema backup-a" od neuspelog preuzimanja. */
private sealed interface LatestBackup {
    data object None : LatestBackup
    data class Found(val content: String) : LatestBackup
}

/**
 * Backup u privatni Drive folder aplikacije (`appDataFolder`) — korisnik ga ne
 * vidi među svojim fajlovima i nijedna druga aplikacija mu ne pristupa.
 * Svaki backup je nov fajl sa vremenskom oznakom, pa se dobra kopija nikad
 * ne pregazi; koje verzije ostaju određuje [RetentionPolicy] (najnovije + dnevne + mesečne).
 *
 * `restore_prompt_pending` je ujedno i oznaka da ova instalacija još NIJE "usvojila" oblak:
 * dok je uključena, ništa se ne otprema bez izričite potvrde korisnika.
 */
class GoogleDriveBackup(private val context: Context) {

    private val _signInState = MutableStateFlow<DriveSignInState>(DriveSignInState.SignedOut)
    val signInState: StateFlow<DriveSignInState> = _signInState.asStateFlow()

    private val _syncState = MutableStateFlow<DriveSyncState>(DriveSyncState.Idle)
    val syncState: StateFlow<DriveSyncState> = _syncState.asStateFlow()

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(true)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    /**
     * Posle prijave, dok korisnik ne odluči da li vraća zatečeni backup, automatski
     * backup je zamrznut — inače bi sveža prazna baza otišla u oblak kao nova verzija.
     */
    private val _restorePromptPending = MutableStateFlow(false)
    val restorePromptPending: StateFlow<Boolean> = _restorePromptPending.asStateFlow()

    private var driveService: Drive? = null

    /** Sprečava da se ručni i pozadinski backup preklope. */
    private val syncMutex = Mutex()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    private val googleSignInClient: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_APPDATA),
                Scope(CalendarScopes.CALENDAR_EVENTS)
            )
            .build()
        GoogleSignIn.getClient(context, options)
    }

    init {
        prefs.getLong(KEY_LAST_SYNC_TIME, 0L).takeIf { it > 0 }?.let { _lastSyncTime.value = it }
        _autoBackupEnabled.value = prefs.getBoolean(KEY_AUTO_BACKUP, true)
        if (!prefs.contains(KEY_RESTORE_PROMPT_PENDING)) {
            // Migracija postojećih instalacija: oblak je "usvojen" ako je ikad uspešno sinhronizovano.
            val adopted = prefs.getLong(KEY_LAST_SYNC_TIME, 0L) > 0L
            prefs.edit().putBoolean(KEY_RESTORE_PROMPT_PENDING, !adopted).apply()
        }
        _restorePromptPending.value = prefs.getBoolean(KEY_RESTORE_PROMPT_PENDING, false)

        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            _signInState.value = DriveSignInState.SignedIn(account.email ?: "Nepoznat nalog")
            initDriveService(account)
        }
    }

    fun getSignInIntent(): Intent = googleSignInClient.signInIntent

    fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account == null) {
            _signInState.value = DriveSignInState.SignedOut
            _syncState.value = DriveSyncState.Error("Prijava na Google nalog nije uspela.")
            return
        }
        _signInState.value = DriveSignInState.SignedIn(account.email ?: "Nepoznat nalog")
        initDriveService(account)
        _syncState.value = DriveSyncState.Idle
        // Zamrzni auto-backup dok se ne proveri ima li već nečega u oblaku.
        setRestorePromptPending(true)
    }

    suspend fun signOut() {
        syncMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { googleSignInClient.signOut() }
                setRestorePromptPending(false)
                _signInState.value = DriveSignInState.SignedOut
                _syncState.value = DriveSyncState.Idle
                driveService = null
            }
        }
    }

    fun isSignedIn(): Boolean = _signInState.value is DriveSignInState.SignedIn

    fun setAutoBackupEnabled(enabled: Boolean) {
        _autoBackupEnabled.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_BACKUP, enabled).apply()
    }

    fun clearSyncState() {
        _syncState.value = DriveSyncState.Idle
    }

    private fun setRestorePromptPending(pending: Boolean) {
        _restorePromptPending.value = pending
        prefs.edit().putBoolean(KEY_RESTORE_PROMPT_PENDING, pending).apply()
    }

    /** Korisnik je odlučio (vratio ili odbio) — auto-backup se odmrzava. */
    fun dismissRestorePrompt() = setRestorePromptPending(false)

    fun isRestorePromptPending(): Boolean = _restorePromptPending.value

    /**
     * Otprema sadržaj kao novu verziju. [buildUpload] se poziva tek kad je sve spremno.
     * Dok odluka o zatečenom backup-u nije doneta, otprema se odbija osim ako je korisnik
     * izričito potvrdio ([userConfirmed]) — ni ručni backup ne sme tiho da postane "najnoviji".
     */
    suspend fun backup(
        userConfirmed: Boolean = false,
        buildUpload: suspend () -> BackupUpload
    ): Result<Unit> {
        if (!isNetworkAvailable()) {
            return fail("Nema internet konekcije.")
        }
        if (_restorePromptPending.value && !userConfirmed) {
            return fail("Prvo odluči da li vraćaš backup koji već postoji u oblaku.")
        }
        return syncMutex.withLock {
            val drive = ensureValidDriveService() ?: return@withLock fail("Nisi prijavljen na Google nalog.")
            _syncState.value = DriveSyncState.Syncing
            val payload = try {
                buildUpload()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Priprema podataka nije uspela", e)
                return@withLock fail("Priprema podataka nije uspela.")
            }
            withContext(Dispatchers.IO) {
                runUpload(drive, payload)
            }
        }
    }

    private suspend fun runUpload(drive: Drive, payload: BackupUpload): Result<Unit> = try {
        val now = System.currentTimeMillis()
        // Blokirajuće Drive pozive tajmaut ne može da prekine — `uploaded` pamti da li je
        // fajl stvarno otišao, da uspešan (samo spor) upload ne bude prijavljen kao greška.
        var uploaded = false
        withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
            createVersionFile(drive, now, payload)
            uploaded = true
            // Čišćenje starih verzija nije deo backup-a: njegov pad ne sme da poništi uspeh.
            runCatching { pruneOldVersions(drive) }
                .onFailure { Log.w(TAG, "Čišćenje starih verzija nije uspelo", it) }
        }
        if (!uploaded) {
            fail("Isteklo vreme — spora mreža.")
        } else {
            _lastSyncTime.value = now
            prefs.edit().putLong(KEY_LAST_SYNC_TIME, now).apply()
            setRestorePromptPending(false)
            _syncState.value = DriveSyncState.Success
            Result.success(Unit)
        }
    } catch (e: UserRecoverableAuthIOException) {
        Log.w(TAG, "Auth istekao pri backup-u", e)
        _signInState.value = DriveSignInState.SignedOut
        driveService = null
        fail("Sesija je istekla — prijavi se ponovo.")
    } catch (e: Exception) {
        Log.e(TAG, "Backup nije uspeo", e)
        fail("Backup nije uspeo: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Preuzima tačno određenu verziju (izbor starije verzije u Podešavanjima). */
    suspend fun restoreVersion(fileId: String): Result<String> {
        if (!isNetworkAvailable()) {
            return fail("Nema internet konekcije.")
        }
        return syncMutex.withLock {
            val drive = ensureValidDriveService() ?: return@withLock fail("Nisi prijavljen na Google nalog.")
            _syncState.value = DriveSyncState.Syncing
            withContext(Dispatchers.IO) {
                try {
                    var content: String? = null
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) { content = downloadContent(drive, fileId) }
                    val downloaded = content
                    if (downloaded == null) {
                        fail("Preuzimanje backup fajla nije uspelo.")
                    } else {
                        _syncState.value = DriveSyncState.Success
                        Result.success(downloaded)
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    _signInState.value = DriveSignInState.SignedOut
                    driveService = null
                    fail("Sesija je istekla — prijavi se ponovo.")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Vraćanje verzije nije uspelo", e)
                    fail("Vraćanje nije uspelo: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    /** Vraća sadržaj najnovije verzije iz oblaka, ili null ako je nema. */
    suspend fun restoreLatest(): Result<String?> {
        if (!isNetworkAvailable()) {
            return fail("Nema internet konekcije.")
        }
        return syncMutex.withLock {
            val drive = ensureValidDriveService() ?: return@withLock fail("Nisi prijavljen na Google nalog.")
            _syncState.value = DriveSyncState.Syncing
            withContext(Dispatchers.IO) {
                try {
                    // `success(null)` sme da znači samo "u oblaku stvarno nema backup-a".
                    // Tajmaut i neuspelo preuzimanje moraju biti greška — inače bi se korisniku
                    // na sporoj mreži lažno javilo da backup ne postoji.
                    var outcome: LatestBackup? = null
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) {
                        val latest = listVersionsInternal(drive).maxByOrNull { it.createdAtMillis }
                        outcome = if (latest == null) {
                            LatestBackup.None
                        } else {
                            val content = downloadContent(drive, latest.fileId)
                                ?: error("Preuzimanje backup fajla nije uspelo.")
                            LatestBackup.Found(content)
                        }
                    }
                    when (val found = outcome) {
                        null -> fail("Isteklo vreme — spora mreža.")
                        LatestBackup.None -> {
                            _syncState.value = DriveSyncState.Success
                            Result.success(null)
                        }
                        is LatestBackup.Found -> {
                            _syncState.value = DriveSyncState.Success
                            Result.success(found.content)
                        }
                    }
                } catch (e: UserRecoverableAuthIOException) {
                    _signInState.value = DriveSignInState.SignedOut
                    driveService = null
                    fail("Sesija je istekla — prijavi se ponovo.")
                } catch (e: Exception) {
                    Log.e(TAG, "Vraćanje nije uspelo", e)
                    fail("Vraćanje nije uspelo: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    /**
     * Lista verzija u oblaku. Greška (nema mreže, tajmaut, odjavljen nalog) se vraća kao
     * `failure`, a NE kao prazna lista — pozivalac na osnovu prazne liste odmrzava
     * auto-backup, pa bi progutan pad mreže mogao da pregazi dobar backup praznom bazom.
     */
    suspend fun listVersions(): Result<List<DriveVersion>> {
        if (!isNetworkAvailable()) {
            return Result.failure(IllegalStateException("Nema internet konekcije."))
        }
        return syncMutex.withLock {
            val drive = ensureValidDriveService()
                ?: return@withLock Result.failure(IllegalStateException("Nisi prijavljen na Google nalog."))
            withContext(Dispatchers.IO) {
                runCatching {
                    var listed: List<DriveVersion>? = null
                    withTimeoutOrNull(NETWORK_TIMEOUT_MS) { listed = listVersionsInternal(drive) }
                    listed ?: error("Isteklo vreme — spora mreža.")
                }
            }
        }
    }

    private fun <T> fail(message: String): Result<T> {
        _syncState.value = DriveSyncState.Error(message)
        return Result.failure(IllegalStateException(message))
    }

    private fun createVersionFile(drive: Drive, timestamp: Long, payload: BackupUpload) {
        val metadata = com.google.api.services.drive.model.File().apply {
            name = "$FILE_PREFIX$timestamp$FILE_SUFFIX"
            parents = listOf(APP_DATA_FOLDER)
            // Brojevi zapisa uz verziju: izbor starije verzije ih prikazuje bez preuzimanja fajla.
            appProperties = payload.counts
        }
        val content = ByteArrayContent("application/json", payload.json.toByteArray(Charsets.UTF_8))
        drive.files().create(metadata, content).setFields("id").execute()
    }

    private fun listVersionsInternal(drive: Drive): List<DriveVersion> {
        // Sa dnevnim i mesečnim verzijama lista može da pređe jednu stranu — čitaju se sve.
        val files = mutableListOf<com.google.api.services.drive.model.File>()
        var pageToken: String? = null
        do {
            val result = drive.files().list()
                .setSpaces(APP_DATA_FOLDER)
                .setFields("nextPageToken, files(id, name, createdTime, size, appProperties)")
                .setPageSize(100)
                .setPageToken(pageToken)
                .execute()
            files.addAll(result.files.orEmpty())
            pageToken = result.nextPageToken
        } while (pageToken != null)

        return files
            .filter { it.name.orEmpty().startsWith(FILE_PREFIX) }
            .map { file ->
                val fromName = file.name
                    .removePrefix(FILE_PREFIX)
                    .removeSuffix(FILE_SUFFIX)
                    .toLongOrNull()
                DriveVersion(
                    fileId = file.id,
                    fileName = file.name,
                    createdAtMillis = fromName ?: file.createdTime?.value ?: 0L,
                    sizeBytes = file.getSize() ?: 0L,
                    counts = file.appProperties?.takeIf { it.isNotEmpty() }
                )
            }
            .sortedByDescending { it.createdAtMillis }
    }

    private fun downloadContent(drive: Drive, fileId: String): String? = runCatching {
        ByteArrayOutputStream().use { output ->
            drive.files().get(fileId).executeMediaAndDownloadTo(output)
            output.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    private fun pruneOldVersions(drive: Drive) {
        val versions = listVersionsInternal(drive)
        val toDelete = RetentionPolicy.selectToDelete(
            versions = versions,
            nowMillis = System.currentTimeMillis(),
            zone = java.time.ZoneId.systemDefault()
        ) { it.createdAtMillis }
        toDelete.forEach { old ->
            runCatching { drive.files().delete(old.fileId).execute() }
                .onFailure { Log.w(TAG, "Brisanje stare verzije nije uspelo: ${old.fileName}") }
        }
    }

    private fun initDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("BanketLux")
            .build()
    }

    private fun ensureValidDriveService(): Drive? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account == null || !GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            _signInState.value = DriveSignInState.SignedOut
            driveService = null
            return null
        }
        if (driveService == null) {
            initDriveService(account)
        }
        return driveService
    }

    private fun isNetworkAvailable(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
