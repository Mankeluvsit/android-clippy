package com.example.cloudclipboard.clipboard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cloudclipboard.CloudClipboardApp
import com.example.cloudclipboard.R
import com.example.cloudclipboard.data.ClipboardRepository
import com.example.cloudclipboard.work.ClipboardSyncWorker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

class ClipboardForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var clipboardMonitor: ClipboardMonitor
    private var repository: ClipboardRepository? = null

    override fun onCreate() {
        super.onCreate()

        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            Timber.w("No signed-in account, stopping service")
            stopSelf()
            return
        }

        val app = application as CloudClipboardApp
        repository = app.container.createRepository(account)
        clipboardMonitor = ClipboardMonitor(this)

        startForeground(NOTIFICATION_ID, buildNotification())
        observeClipboard()
        clipboardMonitor.start()
        Timber.d("Clipboard foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        clipboardMonitor.stop()
        serviceScope.cancel()
        super.onDestroy()
        Timber.d("Clipboard foreground service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeClipboard() {
        val repo = repository ?: return
        serviceScope.launch {
            clipboardMonitor.events.collect { event ->
                when (event) {
                    is ClipboardMonitor.ClipboardEvent.Text -> {
                        repo.enqueueLocal(event.text)
                        enqueueSync()
                    }
                }
            }
        }
    }

    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<ClipboardSyncWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            ClipboardSyncWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun buildNotification(): Notification {
        ensureChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_foreground_title))
            .setContentText(getString(R.string.notification_foreground_text))
            .setOngoing(true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "cloud_clipboard_monitor"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ClipboardForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
