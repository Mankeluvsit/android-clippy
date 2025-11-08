package com.example.clipboardsync.drive

import android.content.Context
import com.example.clipboardsync.data.ClipboardEntry
import com.example.clipboardsync.data.ClipboardEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class DriveSyncManager(
    private val context: Context,
    private val authManager: DriveAuthManager = DriveAuthManager(context),
    private val network: DriveTransport = DriveTransport()
) {

    suspend fun syncUp(entries: List<ClipboardEntry>): Result<Unit> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
            ?: return@withContext Result.failure(IllegalStateException("Google account not connected"))

        val envelope = ClipboardEnvelope(
            updatedAt = Instant.now().toString(),
            entries = entries
        )

        network.ensureFile(account, DRIVE_FILE_NAME).fold(
            onSuccess = { fileId ->
                network.uploadJson(account, fileId, envelope)
            },
            onFailure = { throwable -> Result.failure(throwable) }
        )
    }

    suspend fun syncDown(): Result<ClipboardEnvelope> = withContext(Dispatchers.IO) {
        val account = authManager.getLastSignedInAccount()
            ?: return@withContext Result.failure(IllegalStateException("Google account not connected"))

        network.ensureFile(account, DRIVE_FILE_NAME).fold(
            onSuccess = { fileId ->
                network.downloadJson(account, fileId)
            },
            onFailure = { throwable -> Result.failure(throwable) }
        )
    }

    companion object {
        const val DRIVE_FILE_NAME = "clipboard.json"
    }
}

/**
 * A lightweight wrapper for talking to Google Drive AppData.
 * The networking calls are intentionally left as TODOs. Implement them with Retrofit or OkHttp
 * using googleapis.com/drive/v3 endpoints (`files.list`, `files.create`, `files.update`).
 */
class DriveTransport {

    suspend fun ensureFile(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount, name: String): Result<String> {
        // TODO: Call Drive `files.list` in the appDataFolder to check if the file exists.
        // If not present, call `files.create` with parents=["appDataFolder"] and name.
        return Result.success("placeholder-file-id")
    }

    suspend fun uploadJson(
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        fileId: String,
        envelope: ClipboardEnvelope
    ): Result<Unit> {
        // TODO: Use the account's GoogleAuth token to POST the JSON via
        // https://www.googleapis.com/upload/drive/v3/files/<fileId>?uploadType=media
        return Result.success(Unit)
    }

    suspend fun downloadJson(
        account: com.google.android.gms.auth.api.signin.GoogleSignInAccount,
        fileId: String
    ): Result<ClipboardEnvelope> {
        // TODO: Fetch file metadata to obtain the file ID, then GET
        // https://www.googleapis.com/drive/v3/files/<fileId>?alt=media
        return Result.success(ClipboardEnvelope())
    }
}
