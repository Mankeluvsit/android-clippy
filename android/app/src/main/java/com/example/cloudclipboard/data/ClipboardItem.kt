package com.example.cloudclipboard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class ClipboardPayload(
    @SerialName("version") val version: Int = 1,
    @SerialName("items") val items: List<ClipboardItem> = emptyList()
)

@Serializable
data class ClipboardItem(
    @SerialName("id") val id: String = UUID.randomUUID().toString(),
    @SerialName("text") val text: String,
    @SerialName("mimeType") val mimeType: String = "text/plain",
    @SerialName("createdAt") val createdAt: String = Instant.now().toString(),
    @SerialName("deviceName") val deviceName: String,
    @SerialName("encrypted") val encrypted: Boolean = false
)

@Entity(tableName = "clipboard_entries")
data class ClipboardItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "device_name") val deviceName: String,
    @ColumnInfo(name = "encrypted") val encrypted: Boolean,
    @ColumnInfo(name = "needs_upload") val needsUpload: Boolean
)

fun ClipboardItemEntity.toDomain(): ClipboardItem =
    ClipboardItem(
        id = id,
        text = text,
        mimeType = mimeType,
        createdAt = createdAt,
        deviceName = deviceName,
        encrypted = encrypted
    )

fun ClipboardItem.toEntity(needsUpload: Boolean = false): ClipboardItemEntity =
    ClipboardItemEntity(
        id = id,
        text = text,
        mimeType = mimeType,
        createdAt = createdAt,
        deviceName = deviceName,
        encrypted = encrypted,
        needsUpload = needsUpload
    )
