package com.example.cloudclipboard.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cloudclipboard.CloudClipboardApp
import com.example.cloudclipboard.data.ClipboardRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import timber.log.Timber

class ClipboardSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val account = GoogleSignIn.getLastSignedInAccount(applicationContext)
        if (account == null) {
            Timber.w("No signed-in account available for sync")
            return Result.retry()
        }

        val app = applicationContext as CloudClipboardApp
        val repository = app.container.createRepository(account)
        return when (val result = repository.sync()) {
            is ClipboardRepository.SyncResult.Success -> {
                Timber.d("Clipboard sync succeeded")
                Result.success()
            }

            is ClipboardRepository.SyncResult.Conflict -> {
                Timber.w("Clipboard sync hit a conflict, retrying later")
                Result.retry()
            }

            is ClipboardRepository.SyncResult.Failure -> {
                Timber.e(result.throwable, "Clipboard sync failed")
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "clipboard_sync"
    }
}
