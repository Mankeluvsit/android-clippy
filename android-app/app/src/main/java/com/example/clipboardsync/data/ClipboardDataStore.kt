package com.example.clipboardsync.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

val Context.clipboardDataStore: DataStore<ClipboardEnvelope> by dataStore(
    fileName = "clipboard_history.json",
    serializer = ClipboardEnvelopeSerializer
)

object ClipboardEnvelopeSerializer : Serializer<ClipboardEnvelope> {
    override val defaultValue: ClipboardEnvelope = ClipboardEnvelope()

    override suspend fun readFrom(input: InputStream): ClipboardEnvelope =
        try {
            Json.decodeFromString(
                ClipboardEnvelope.serializer(),
                input.readBytes().decodeToString()
            )
        } catch (error: SerializationException) {
            defaultValue
        }

    override suspend fun writeTo(t: ClipboardEnvelope, output: OutputStream) {
        output.use { stream ->
            val json = Json.encodeToString(ClipboardEnvelope.serializer(), t)
            stream.write(json.toByteArray())
        }
    }
}

interface ClipboardRepository {
    val entries: Flow<List<ClipboardEntry>>
    suspend fun add(entry: ClipboardEntry)
    suspend fun upsertAll(entries: List<ClipboardEntry>)
    suspend fun markSynced(ids: Set<String>, syncedAt: Instant)
}

class LocalClipboardRepository(
    private val store: DataStore<ClipboardEnvelope>
) : ClipboardRepository {

    override val entries: Flow<List<ClipboardEntry>> =
        store.data.map { envelope ->
            envelope.entries.sortedByDescending { it.createdAt }
        }

    override suspend fun add(entry: ClipboardEntry) {
        store.updateData { envelope ->
            val filtered = envelope.entries.filterNot { it.id == entry.id }
            envelope.copy(
                updatedAt = Instant.now().toString(),
                entries = listOf(entry) + filtered
            )
        }
    }

    override suspend fun upsertAll(entries: List<ClipboardEntry>) {
        store.updateData { envelope ->
            envelope.copy(
                updatedAt = Instant.now().toString(),
                entries = entries
            )
        }
    }

    override suspend fun markSynced(ids: Set<String>, syncedAt: Instant) {
        store.updateData { envelope ->
            val updated = envelope.entries.map { entry ->
                if (entry.id in ids) entry.copy(pendingSync = false, syncedAt = syncedAt.toString())
                else entry
            }
            envelope.copy(
                updatedAt = Instant.now().toString(),
                entries = updated
            )
        }
    }
}
