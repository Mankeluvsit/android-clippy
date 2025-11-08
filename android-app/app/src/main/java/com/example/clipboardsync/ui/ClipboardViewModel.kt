package com.example.clipboardsync.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.clipboardsync.data.ClipboardEntry
import com.example.clipboardsync.data.ClipboardRepository
import com.example.clipboardsync.data.LocalClipboardRepository
import com.example.clipboardsync.data.clipboardDataStore
import com.example.clipboardsync.drive.DriveAuthManager
import com.example.clipboardsync.drive.DriveSyncManager
import com.example.clipboardsync.sync.enqueueSync
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant

data class ClipboardUiState(
    val entries: List<ClipboardEntry> = emptyList(),
    val isWatcherEnabled: Boolean = false,
    val isSignedIn: Boolean = false,
    val statusMessage: String = ""
)

class ClipboardViewModel(
    application: Application,
    private val repository: ClipboardRepository,
    private val driveAuthManager: DriveAuthManager,
    private val driveSyncManager: DriveSyncManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ClipboardUiState())
    val uiState: StateFlow<ClipboardUiState> = _uiState.asStateFlow()

    var onServiceStateChanged: ((Boolean) -> Unit)? = null

    init {
        repository.entries
            .onEach { entries ->
                _uiState.value = _uiState.value.copy(entries = entries)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val account = driveAuthManager.silentSignIn()
            _uiState.value = _uiState.value.copy(
                isSignedIn = account != null,
                statusMessage = if (account != null) "Signed in as ${account.email}" else "Sign in to sync with Drive"
            )
        }
    }

    fun signInIntent(): Intent = driveAuthManager.getClient().signInIntent

    fun handleSignInResult(data: Intent?) {
        viewModelScope.launch {
            if (data == null) {
                _uiState.value = _uiState.value.copy(
                    statusMessage = "Sign-in cancelled"
                )
                return@launch
            }
            runCatching {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                task.await()
            }.onSuccess { account ->
                _uiState.value = _uiState.value.copy(
                    isSignedIn = true,
                    statusMessage = "Signed in as ${account.email}"
                )
                enqueueSync(getApplication())
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isSignedIn = false,
                    statusMessage = "Sign-in failed: ${error.message}"
                )
            }
        }
    }

    fun signOut() {
        driveAuthManager.signOut()
        _uiState.value = _uiState.value.copy(
            isSignedIn = false,
            statusMessage = "Signed out"
        )
    }

    fun enableWatcher(context: Context) {
        _uiState.value = _uiState.value.copy(
            isWatcherEnabled = true,
            statusMessage = "Clipboard watcher active"
        )
        onServiceStateChanged?.invoke(true)
        enqueueSync(context)
    }

    fun disableWatcher(context: Context) {
        _uiState.value = _uiState.value.copy(
            isWatcherEnabled = false,
            statusMessage = "Clipboard watcher stopped"
        )
        onServiceStateChanged?.invoke(false)
    }

    fun enqueueSync(context: Context) {
        enqueueSync(context)
        _uiState.value = _uiState.value.copy(
            statusMessage = "Sync scheduled"
        )
    }

    suspend fun syncNow() {
        val entries = uiState.value.entries
        driveSyncManager.syncUp(entries)
        repository.markSynced(entries.map { it.id }.toSet(), Instant.now())
    }
}

class ClipboardViewModelFactory(
    private val appContext: Context
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ClipboardViewModel::class.java)) {
            val application = appContext.applicationContext as Application
            val repository = LocalClipboardRepository(application.clipboardDataStore)
            val driveAuthManager = DriveAuthManager(appContext)
            val driveSyncManager = DriveSyncManager(appContext, driveAuthManager)
            @Suppress("UNCHECKED_CAST")
            return ClipboardViewModel(application, repository, driveAuthManager, driveSyncManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
