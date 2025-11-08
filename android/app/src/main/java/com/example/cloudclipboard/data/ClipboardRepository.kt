package com.example.cloudclipboard.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.LinkedHashMap
import com.example.cloudclipboard.drive.DriveClipboardDataSource
import com.example.cloudclipboard.drive.DriveFileSnapshot
import com.google.api.client.googleapis.json.GoogleJsonResponseException

class ClipboardRepository(
    private val dao: ClipboardDao,
    private val driveDataSource: DriveClipboardDataSource,
    private val deviceName: String,
    private val maxItems: Int = 100,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    val items: Flow<List<ClipboardItem>> =
        dao.observeEntries().map { entities -> entities.map { it.toDomain() } }

    suspend fun enqueueLocal(text: String, encrypted: Boolean = false) = withContext(dispatcher) {
        val trimmedText = text.take(MAX_TEXT_LENGTH)
        val item = ClipboardItem(
            text = trimmedText,
            deviceName = deviceName,
            encrypted = encrypted
        )
        dao.upsert(item.toEntity(needsUpload = true))
        pruneToLimit()
        Timber.d("Enqueued clipboard item ${item.id} for upload")
    }

    suspend fun sync(): SyncResult = withContext(dispatcher) {
        try {
            val snapshot = driveDataSource.pull()
            val pending = dao.getPendingUploads()
            val merged = mergeItems(snapshot, pending.map { it.toDomain() })
            val trimmed = merged.take(maxItems)

            val nextSnapshot = snapshot.copy(
                payload = snapshot.payload.copy(items = trimmed),
                version = snapshot.version
            )

            driveDataSource.push(nextSnapshot)

            dao.upsert(trimmed.map { it.toEntity(needsUpload = false) })
            dao.markSynced(pending.map { it.id })
            pruneWithKeepIds(trimmed.map { it.id })

            SyncResult.Success
        } catch (ex: GoogleJsonResponseException) {
            return@withContext when (ex.statusCode) {
                412 -> {
                    Timber.w(ex, "Drive conflict detected, will retry later")
                    SyncResult.Conflict
                }
                else -> {
                    Timber.e(ex, "Drive sync failed")
                    SyncResult.Failure(ex)
                }
            }
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Clipboard sync failed")
            SyncResult.Failure(throwable)
        }
    }

    private suspend fun pruneToLimit() {
        val latest = dao.getLatest(maxItems)
        val keepIds = latest.map { it.id }
        pruneWithKeepIds(keepIds)
    }

    private suspend fun pruneWithKeepIds(keepIds: List<String>) {
        if (keepIds.isEmpty()) {
            dao.clear()
        } else {
            dao.pruneToIds(keepIds)
        }
    }

    private fun mergeItems(
        snapshot: DriveFileSnapshot,
        pending: List<ClipboardItem>
    ): List<ClipboardItem> {
        val combined = LinkedHashMap<String, ClipboardItem>()

        snapshot.payload.items.forEach { item ->
            combined[item.id] = item
        }

        pending.forEach { pendingItem ->
            combined[pendingItem.id] = pendingItem
        }

        return combined.values
            .sortedByDescending { parseInstant(it.createdAt) }
    }

    private fun parseInstant(value: String): Instant =
        try {
            Instant.parse(value)
        } catch (ex: DateTimeParseException) {
            Timber.w(ex, "Unable to parse instant: $value, defaulting to epoch")
            Instant.EPOCH
        }

    sealed interface SyncResult {
        data object Success : SyncResult
        data object Conflict : SyncResult
        data class Failure(val throwable: Throwable) : SyncResult
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 50_000
    }
}
