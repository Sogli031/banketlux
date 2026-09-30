package com.banketlux.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.banketlux.BanketLuxApplication

/**
 * Pozadinski posao koji šalje backup na Google Drive. Pokreće ga [BackupScheduler]
 * (posle izmene + dnevno), uz uslov da ima mreže. Ako korisnik nije prijavljen ili
 * je automatski backup isključen, posao se tiho preskače.
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? BanketLuxApplication ?: return Result.success()
        val drive = app.googleDriveBackup

        if (!drive.autoBackupEnabled.value) {
            Log.d(TAG, "Automatski backup je isključen")
            return Result.success()
        }
        if (drive.isRestorePromptPending()) {
            Log.d(TAG, "Preskačem auto-backup: čeka se odluka o vraćanju zatečenog backup-a")
            return Result.success()
        }
        if (!drive.isSignedIn()) {
            Log.d(TAG, "Preskačem auto-backup: korisnik nije prijavljen")
            return Result.success()
        }

        // "Prazno ne gazi popunjeno": baza bez zakazivanja, zarade i kupaca nema šta da sačuva,
        // a kao najnovija verzija u oblaku samo bi zaklonila poslednji dobar backup.
        if (!app.backupRepository.hasUserRecords()) {
            Log.d(TAG, "Preskačem auto-backup: nema korisničkih podataka")
            return Result.success()
        }

        val outcome = drive.backup { app.backupRepository.exportForUpload() }
        return if (outcome.isSuccess) {
            Log.d(TAG, "Auto-backup uspešan")
            Result.success()
        } else {
            Log.w(TAG, "Auto-backup neuspešan: ${outcome.exceptionOrNull()?.message}")
            if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
                // Trajna greška — odustani; sledeća izmena ili dnevni posao zakazuju nov backup.
                Log.w(TAG, "Odustajem posle ${runAttemptCount + 1} pokušaja")
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val MAX_RETRY_ATTEMPTS = 7
    }
}
