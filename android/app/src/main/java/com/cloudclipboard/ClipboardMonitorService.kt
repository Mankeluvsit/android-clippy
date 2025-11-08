package com.cloudclipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.UUID

class ClipboardMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "ClipboardMonitorChannel"
        const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.cloudclipboard.STOP_SERVICE"
    }

    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardContent: String? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var driveHelper: GoogleDriveHelper

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipboardChange()
    }

    override fun onCreate() {
        super.onCreate()
        driveHelper = GoogleDriveHelper(this)
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, createNotification())
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
    }

    private fun handleClipboardChange() {
        val clipData = clipboardManager?.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: return
            
            // Avoid processing same content repeatedly
            if (text == lastClipboardContent || text.isBlank()) {
                return
            }
            
            lastClipboardContent = text
            
            // Upload to Google Drive
            serviceScope.launch {
                try {
                    val item = ClipboardItem(
                        id = UUID.randomUUID().toString(),
                        content = text,
                        timestamp = System.currentTimeMillis()
                    )
                    driveHelper.uploadClipboardItem(item)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ClipboardMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .addAction(R.drawable.ic_notification, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
}
