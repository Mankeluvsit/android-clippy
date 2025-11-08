package com.example.clipboardsync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.clipboardsync.clipboard.ClipboardWatcherService
import com.example.clipboardsync.ui.ClipboardUiState
import com.example.clipboardsync.ui.ClipboardViewModel
import com.example.clipboardsync.ui.ClipboardViewModelFactory
import com.example.clipboardsync.ui.theme.ClipboardTheme
import com.example.clipboardsync.sync.schedulePeriodicSync

class MainActivity : ComponentActivity() {

    private val viewModel: ClipboardViewModel by viewModels {
        ClipboardViewModelFactory(applicationContext)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.enableWatcher(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClipboardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val signInLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        viewModel.handleSignInResult(result.data)
                    }
                    ClipboardScreen(
                        uiState = state,
                        onToggleWatcher = { enabled ->
                            if (enabled) {
                                maybeRequestNotificationPermission()
                            } else {
                                viewModel.disableWatcher(applicationContext)
                            }
                        },
                        onSignIn = { signInLauncher.launch(viewModel.signInIntent()) },
                        onSignOut = { viewModel.signOut() },
                        onForceSync = { viewModel.enqueueSync(applicationContext) }
                    )
                }
            }
        }

        schedulePeriodicSync(applicationContext)
        viewModel.onServiceStateChanged = { enabled ->
            if (enabled) {
                ClipboardWatcherService.start(applicationContext)
            } else {
                ClipboardWatcherService.stop(applicationContext)
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.enableWatcher(applicationContext)
        }
    }
}

@Composable
private fun ClipboardScreen(
    uiState: ClipboardUiState,
    onToggleWatcher: (Boolean) -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onForceSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.isWatcherEnabled) "Sync on" else "Sync off",
                style = MaterialTheme.typography.titleLarge
            )
            Button(onClick = { onToggleWatcher(!uiState.isWatcherEnabled) }) {
                Text(if (uiState.isWatcherEnabled) "Disable" else "Enable")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.isSignedIn) "Signed in" else "Signed out",
                style = MaterialTheme.typography.bodyLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onForceSync, enabled = uiState.isSignedIn) {
                    Text("Sync now")
                }
                if (uiState.isSignedIn) {
                    TextButton(onClick = onSignOut) {
                        Text("Sign out")
                    }
                } else {
                    Button(onClick = onSignIn) {
                        Text("Sign in")
                    }
                }
            }
        }

        Text("Recent clipboard items", style = MaterialTheme.typography.titleMedium)

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(uiState.entries) { entry ->
                ClipboardCard(
                    content = entry.content,
                    timestamp = entry.createdAt,
                    pendingSync = entry.pendingSync
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = uiState.statusMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun ClipboardCard(
    content: String,
    timestamp: String,
    pendingSync: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.bodySmall
                )
                if (pendingSync) {
                    Text(
                        text = "Pending sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}
