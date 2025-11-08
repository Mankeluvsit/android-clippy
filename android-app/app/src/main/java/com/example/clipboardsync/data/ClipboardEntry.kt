package com.example.clipboardsync.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
data class ClipboardEnvelope(
    val version: Int = 1,
    @SerialName("updatedAt") val updatedAt: String = Instant.now().toString(),
    val entries: List<ClipboardEntry> = emptyList()
)

@Serializable
data class ClipboardEntry(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: String = "text/plain",
    @SerialName("createdAt") val createdAt: String = Instant.now().toString(),
    val source: String = "android",
    val device: String,
    @SerialName("syncedAt") val syncedAt: String? = null,
    val pendingSync: Boolean = true
)
