package com.example.cloudclipboard.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cloudclipboard.CloudClipboardApp
import com.example.cloudclipboard.clipboard.ClipboardForegroundService
import com.example.cloudclipboard.data.ClipboardItem
import com.example.cloudclipboard.data.ClipboardRepository
import com.example.cloudclipboard.work.ClipboardSyncWorker
import com.example.cloudclipboard.work.SyncScheduler
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class MainViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val app: CloudClipboardApp
        get() = getApplication()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var repository: ClipboardRepository? = null
    private var itemsJob: Job? = null

    fun initialize(account: GoogleSignInAccount?) {
        if (account != null) {
            connectRepository(account)
        } else {
            repository = null
            itemsJob?.cancel()
            _uiState.update { UiState() }
        }
    }

    fun onSignedIn(account: GoogleSignInAccount) {
        connectRepository(account)
    }

    fun toggleService(context: Context, enable: Boolean) {
        if (repository == null) return
        if (enable) {
            ClipboardForegroundService.start(context)
            SyncScheduler.schedule(context)
        } else {
            ClipboardForegroundService.stop(context)
            SyncScheduler.cancel(context)
        }
        _uiState.update { it.copy(serviceRunning = enable) }
    }

    fun requestSync(context: Context) {
        if (repository == null) return
        enqueueSync(context)
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun connectRepository(account: GoogleSignInAccount) {
        Timber.d("Connecting repository for ${account.email}")
        val repo = app.container.createRepository(account)
        repository = repo
        _uiState.update {
            it.copy(
                accountEmail = account.email.orEmpty(),
                errorMessage = null
            )
        }

        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            repo.items.collect { items ->
                _uiState.update { state ->
                    state.copy(items = items)
                }
            }
        }
    }

    private fun enqueueSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<ClipboardSyncWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ClipboardSyncWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    data class UiState(
        val accountEmail: String? = null,
        val items: List<ClipboardItem> = emptyList(),
        val serviceRunning: Boolean = false,
        val errorMessage: String? = null
    )
}
