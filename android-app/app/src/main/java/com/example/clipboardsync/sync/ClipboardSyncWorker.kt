package com.example.clipboardsync.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.clipboardsync.data.ClipboardEntry
import com.example.clipboardsync.data.LocalClipboardRepository
import com.example.clipboardsync.data.clipboardDataStore
import com.example.clipboardsync.drive.DriveAuthManager
import com.example.clipboardsync.drive.DriveSyncManager
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

class ClipboardSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val repository = LocalClipboardRepository(appContext.clipboardDataStore)
    private val driveAuthManager = DriveAuthManager(appContext)
    private val driveSyncManager = DriveSyncManager(appContext, driveAuthManager)

    override suspend fun doWork(): Result {
        val account = driveAuthManager.getLastSignedInAccount()
            ?: return Result.retry()

        val localEntries = repository.entries.first()

        // Merge with remote
        val envelopeResult = driveSyncManager.syncDown()
        val merged = envelopeResult
            .map { envelope ->
                mergeEntries(localEntries, envelope.entries)
            }
            .getOrElse { mergeEntries(localEntries, emptyList()) }

        repository.upsertAll(merged)

        val uploadResult = driveSyncManager.syncUp(merged)
        return uploadResult.fold(
            onSuccess = {
                repository.markSynced(
                    ids = merged.map { it.id }.toSet(),
                    syncedAt = Instant.now()
                )
                Result.success()
            },
            onFailure = { error ->
                Result.retry()
            }
        )
    }

    private fun mergeEntries(local: List<ClipboardEntry>, remote: List<ClipboardEntry>): List<ClipboardEntry> {
        return (local + remote)
            .groupBy { it.id }
            .map { (_, entries) ->
                entries.maxByOrNull { it.createdAt }!!
            }
            .sortedByDescending { it.createdAt }
    }
}

private const val ONE_OFF_WORK_NAME = "clipboard-sync-once"
private const val PERIODIC_WORK_NAME = "clipboard-sync-periodic"

fun enqueueSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = OneTimeWorkRequestBuilder<ClipboardSyncWorker>()
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(5))
        .build()

    WorkManager.getInstance(context).enqueueUniqueWork(
        ONE_OFF_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request
    )
}

fun schedulePeriodicSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val request = PeriodicWorkRequestBuilder<ClipboardSyncWorker>(Duration.ofHours(1))
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        PERIODIC_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
