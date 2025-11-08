package com.example.clipboardsync.clipboard

import android.app.Notification
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.clipboardsync.ClipboardSyncApp
import com.example.clipboardsync.MainActivity
import com.example.clipboardsync.R
import com.example.clipboardsync.data.ClipboardEntry
import com.example.clipboardsync.data.LocalClipboardRepository
import com.example.clipboardsync.data.clipboardDataStore
import com.example.clipboardsync.sync.enqueueSync
import kotlinx.coroutines.launch
import java.time.Instant

class ClipboardWatcherService : LifecycleService() {

    private val clipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val repository by lazy {
        LocalClipboardRepository(applicationContext.clipboardDataStore)
    }

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboardManager.primaryClip ?: return@OnPrimaryClipChangedListener
        val item = clip.getItemAt(0) ?: return@OnPrimaryClipChangedListener
        val text = item.text?.toString() ?: return@OnPrimaryClipChangedListener

        lifecycleScope.launch {
            val entry = ClipboardEntry(
                content = text,
                device = android.os.Build.MODEL,
                createdAt = Instant.now().toString()
            )
            repository.add(entry)
            enqueueSync(applicationContext)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        clipboardManager.addPrimaryClipChangedListener(listener)
    }

    override fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(listener)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ClipboardSyncApp.CLIPBOARD_CHANNEL_ID)
            .setContentTitle(getString(R.string.clipboard_service_notification_title))
            .setContentText(getString(R.string.clipboard_service_notification_text))
            .setContentIntent(intent)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ClipboardWatcherService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardWatcherService::class.java)
            context.stopService(intent)
        }
    }
}
