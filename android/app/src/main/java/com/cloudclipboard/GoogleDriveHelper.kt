package com.cloudclipboard

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class GoogleDriveHelper(private val context: Context) {

    companion object {
        private const val FOLDER_NAME = "CloudClipboard"
        private const val FILE_NAME = "clipboard_data.json"
    }

    private var driveService: Drive? = null

    fun getSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(com.google.android.gms.common.api.Scope(DriveScopes.DRIVE_FILE))
            .build()
    }

    fun initializeDriveService(account: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        driveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Cloud Clipboard")
            .build()
    }

    suspend fun uploadClipboardItem(item: ClipboardItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext false
            
            // Get or create folder
            val folderId = getOrCreateFolder(service)
            
            // Get existing file or create new one
            val fileId = getClipboardFileId(service, folderId)
            
            // Read existing content
            val existingItems = if (fileId != null) {
                readClipboardData(service, fileId)
            } else {
                mutableListOf()
            }
            
            // Add new item (prevent duplicates)
            if (existingItems.none { it.content == item.content && 
                    (System.currentTimeMillis() - it.timestamp) < 5000 }) {
                existingItems.add(0, item)
                
                // Keep only last 100 items
                if (existingItems.size > 100) {
                    existingItems.subList(100, existingItems.size).clear()
                }
            }
            
            // Convert to JSON
            val jsonArray = JSONArray()
            existingItems.forEach { clipItem ->
                jsonArray.put(JSONObject().apply {
                    put("id", clipItem.id)
                    put("content", clipItem.content)
                    put("timestamp", clipItem.timestamp)
                    put("deviceId", clipItem.deviceId)
                })
            }
            
            val content = jsonArray.toString()
            val contentStream = content.byteInputStream()
            
            if (fileId != null) {
                // Update existing file
                val fileMetadata = File()
                service.files().update(fileId, fileMetadata, 
                    com.google.api.client.http.InputStreamContent("application/json", contentStream))
                    .execute()
            } else {
                // Create new file
                val fileMetadata = File().apply {
                    name = FILE_NAME
                    parents = listOf(folderId)
                    mimeType = "application/json"
                }
                
                service.files().create(fileMetadata,
                    com.google.api.client.http.InputStreamContent("application/json", contentStream))
                    .setFields("id")
                    .execute()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getClipboardItems(): List<ClipboardItem> = withContext(Dispatchers.IO) {
        try {
            val service = driveService ?: return@withContext emptyList()
            
            val folderId = getOrCreateFolder(service)
            val fileId = getClipboardFileId(service, folderId) ?: return@withContext emptyList()
            
            readClipboardData(service, fileId)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun getOrCreateFolder(service: Drive): String {
        // Search for existing folder
        val result = service.files().list()
            .setQ("name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        return if (result.files.isNotEmpty()) {
            result.files[0].id
        } else {
            // Create folder
            val folderMetadata = File().apply {
                name = FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            val folder = service.files().create(folderMetadata)
                .setFields("id")
                .execute()
            folder.id
        }
    }

    private fun getClipboardFileId(service: Drive, folderId: String): String? {
        val result = service.files().list()
            .setQ("name='$FILE_NAME' and '$folderId' in parents and trashed=false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        return if (result.files.isNotEmpty()) result.files[0].id else null
    }

    private fun readClipboardData(service: Drive, fileId: String): MutableList<ClipboardItem> {
        val outputStream = ByteArrayOutputStream()
        service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        
        val jsonString = outputStream.toString()
        val jsonArray = JSONArray(jsonString)
        
        val items = mutableListOf<ClipboardItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            items.add(ClipboardItem(
                id = obj.getString("id"),
                content = obj.getString("content"),
                timestamp = obj.getLong("timestamp"),
                deviceId = obj.optString("deviceId", "Unknown")
            ))
        }
        
        return items
    }
}
