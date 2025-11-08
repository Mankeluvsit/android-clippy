package com.example.cloudclipboard.drive

import com.example.cloudclipboard.data.ClipboardPayload
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.nio.charset.StandardCharsets

private const val FOLDER_NAME = "Cloud Clipboard"
private const val FILE_NAME = "clipboard.json"

data class DriveFileSnapshot(
    val fileId: String,
    val payload: ClipboardPayload,
    val version: Long
)

class DriveClipboardDataSource(
    private val drive: Drive,
    private val json: Json,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun pull(): DriveFileSnapshot = withContext(dispatcher) {
        val folderId = ensureFolder()
        val fileMetadata = ensureFile(folderId)

        val outputStream = java.io.ByteArrayOutputStream()
        drive.files().get(fileMetadata.id)
            .setFields("id, name, version")
            .executeMediaAndDownloadTo(outputStream)

        val payload = runCatching {
            json.decodeFromString<ClipboardPayload>(
                outputStream.toString(StandardCharsets.UTF_8)
            )
        }.getOrElse {
            Timber.e(it, "Failed to decode clipboard payload, falling back to empty list")
            ClipboardPayload()
        }

        val version = (fileMetadata.version ?: 0).toLong()
        DriveFileSnapshot(
            fileId = fileMetadata.id,
            payload = payload,
            version = version
        )
    }

    suspend fun push(snapshot: DriveFileSnapshot) = withContext(dispatcher) {
        val content = json.encodeToString(ClipboardPayload.serializer(), snapshot.payload)
        val mediaContent = ByteArrayContent.fromString("application/json", content)

        val metadataUpdate = File().apply {
            // no metadata updates for now
        }

        Timber.d("Uploading ${snapshot.payload.items.size} items to Drive")
        val request = drive.files()
            .update(snapshot.fileId, metadataUpdate, mediaContent)
            .setFields("id, version, modifiedTime")
        if (snapshot.version > 0) {
            request.set("If-Match", snapshot.version.toString())
        }
        request.execute()
    }

    private suspend fun ensureFolder(): String = withContext(dispatcher) {
        val query = "name = '$FOLDER_NAME' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val result: FileList = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setPageSize(1)
            .execute()

        val existing = result.files?.firstOrNull()
        if (existing != null) {
            existing.id
        } else {
            val folderMetadata = File().apply {
                name = FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(folderMetadata)
                .setFields("id")
                .execute()
                .id
        }
    }

    private suspend fun ensureFile(folderId: String): File = withContext(dispatcher) {
        val query =
            "name = '$FILE_NAME' and '$folderId' in parents and trashed = false and mimeType = 'application/json'"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name, version)")
            .setPageSize(1)
            .execute()

        val existing = result.files?.firstOrNull()
        if (existing != null) {
            existing
        } else {
            val initialPayload = ClipboardPayload()
            val content = json.encodeToString(ClipboardPayload.serializer(), initialPayload)
            val fileMetadata = File().apply {
                name = FILE_NAME
                parents = listOf(folderId)
                mimeType = "application/json"
            }

            drive.files()
                .create(fileMetadata, ByteArrayContent.fromString("application/json", content))
                .setFields("id, version")
                .execute()
        }
    }
}
