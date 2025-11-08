package com.example.cloudclipboard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard_entries ORDER BY datetime(created_at) DESC")
    fun observeEntries(): Flow<List<ClipboardItemEntity>>

    @Query("SELECT * FROM clipboard_entries WHERE needs_upload = 1 ORDER BY datetime(created_at)")
    suspend fun getPendingUploads(): List<ClipboardItemEntity>

    @Query("SELECT * FROM clipboard_entries ORDER BY datetime(created_at) DESC LIMIT :limit")
    suspend fun getLatest(limit: Int): List<ClipboardItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ClipboardItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entries: List<ClipboardItemEntity>)

    @Query("UPDATE clipboard_entries SET needs_upload = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM clipboard_entries WHERE id NOT IN (:ids)")
    suspend fun pruneToIds(ids: List<String>)

    @Query("DELETE FROM clipboard_entries")
    suspend fun clear()
}
