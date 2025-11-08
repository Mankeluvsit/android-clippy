package com.example.cloudclipboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cloudclipboard.R
import com.example.cloudclipboard.clipboard.ClipboardForegroundService
import com.example.cloudclipboard.data.ClipboardItem
import com.example.cloudclipboard.ui.theme.CloudClipboardTheme
import com.example.cloudclipboard.work.SyncScheduler
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.api.services.drive.DriveScopes
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var signInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        signInClient = GoogleSignIn.getClient(this, gso)

        val existingAccount = GoogleSignIn.getLastSignedInAccount(this)
        viewModel.initialize(existingAccount)

        val signInLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val task: Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                viewModel.onSignedIn(account)
            } catch (exception: Exception) {
                Timber.e(exception, "Google sign-in failed")
                viewModel.showError("Google sign-in failed: ${exception.message}")
            }
        }

        setContent {
            CloudClipboardTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                MainScreen(
                    state = uiState,
                    onSignIn = { signInLauncher.launch(signInClient.signInIntent) },
                    onSignOut = {
                        signInClient.signOut().addOnCompleteListener {
                            viewModel.initialize(null)
                            ClipboardForegroundService.stop(applicationContext)
                            SyncScheduler.cancel(applicationContext)
                        }
                    },
                    onToggleMonitoring = { enabled ->
                        viewModel.toggleService(applicationContext, enabled)
                    },
                    onSyncNow = { viewModel.requestSync(applicationContext) }
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: MainViewModel.UiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onToggleMonitoring: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    if (state.accountEmail != null) {
                        TextButton(onClick = onSignOut) {
                            Text("Sign out")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.accountEmail == null) {
                SignInCard(onSignIn = onSignIn)
            } else {
                AccountSummary(
                    email = state.accountEmail,
                    serviceRunning = state.serviceRunning,
                    onToggleMonitoring = onToggleMonitoring,
                    onSyncNow = onSyncNow
                )
                ClipboardList(items = state.items)
            }

            state.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Connect your Google account to start syncing clipboard items to Drive.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onSignIn) {
                Text("Sign in with Google")
            }
        }
    }
}

@Composable
private fun AccountSummary(
    email: String,
    serviceRunning: Boolean,
    onToggleMonitoring: (Boolean) -> Unit,
    onSyncNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Signed in as $email", style = MaterialTheme.typography.bodyLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Clipboard listener")
                Switch(
                    checked = serviceRunning,
                    onCheckedChange = onToggleMonitoring
                )
            }
            Button(
                onClick = onSyncNow
            ) {
                Text("Sync now")
            }
        }
    }
}

@Composable
private fun ClipboardList(items: List<ClipboardItem>) {
    val clipboardManager = LocalClipboardManager.current
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm") }

    Card(
        modifier = Modifier.fillMaxSize()
    ) {
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No clipboard entries yet. Copy text on your device to see it here.")
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                items(items) { item ->
                    ClipboardItemRow(
                        item = item,
                        formatter = formatter,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(item.text))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardItemRow(
    item: ClipboardItem,
    formatter: DateTimeFormatter,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.text.take(500),
                style = MaterialTheme.typography.bodyMedium
            )
            if (item.text.length > 500) {
                Text(
                    text = "…",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = metadataLabel(item, formatter),
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedButton(onClick = onCopy) {
                    Text("Copy")
                }
            }
        }
    }
}

private fun metadataLabel(item: ClipboardItem, formatter: DateTimeFormatter): String {
    val timestamp = runCatching {
        Instant.parse(item.createdAt)
            .atZone(ZoneId.systemDefault())
            .format(formatter)
    }.getOrElse { item.createdAt }
    return "$timestamp • ${item.deviceName}"
}

@Preview(showBackground = true)
@Composable
private fun SignedOutPreview() {
    CloudClipboardTheme {
        MainScreen(
            state = MainViewModel.UiState(),
            onSignIn = {},
            onSignOut = {},
            onToggleMonitoring = {},
            onSyncNow = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SignedInPreview() {
    CloudClipboardTheme {
        MainScreen(
            state = MainViewModel.UiState(
                accountEmail = "user@example.com",
                serviceRunning = true,
                items = listOf(
                    ClipboardItem(
                        text = "Example clipboard text",
                        deviceName = "Pixel 8",
                        createdAt = Instant.now().toString(),
                        mimeType = "text/plain",
                        encrypted = false
                    )
                )
            ),
            onSignIn = {},
            onSignOut = {},
            onToggleMonitoring = {},
            onSyncNow = {}
        )
    }
}
