package com.example.clipboardsync.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.clipboardsync.clipboard.ClipboardWatcherService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        schedulePeriodicSync(context)
        ClipboardWatcherService.start(context)
    }
}
