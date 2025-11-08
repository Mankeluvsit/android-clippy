package com.cloudclipboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var driveHelper: GoogleDriveHelper
    private lateinit var statusText: TextView
    private lateinit var accountText: TextView
    private lateinit var signInButton: MaterialButton
    private lateinit var toggleMonitorButton: MaterialButton
    private lateinit var clipboardRecyclerView: RecyclerView
    private lateinit var clipboardAdapter: ClipboardAdapter
    
    private var isMonitoring = false
    private var isSignedIn = false

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        driveHelper = GoogleDriveHelper(this)
        
        // Initialize views
        statusText = findViewById(R.id.statusText)
        accountText = findViewById(R.id.accountText)
        signInButton = findViewById(R.id.signInButton)
        toggleMonitorButton = findViewById(R.id.toggleMonitorButton)
        clipboardRecyclerView = findViewById(R.id.clipboardRecyclerView)

        // Setup RecyclerView
        clipboardAdapter = ClipboardAdapter(this, emptyList()) { item ->
            deleteClipboardItem(item)
        }
        clipboardRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = clipboardAdapter
        }

        // Setup Google Sign-In
        googleSignInClient = GoogleSignIn.getClient(this, driveHelper.getSignInOptions())
        
        // Check if already signed in
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            handleSignedIn(account.email)
            driveHelper.initializeDriveService(account)
            loadClipboardHistory()
        }

        // Setup button listeners
        signInButton.setOnClickListener {
            if (isSignedIn) {
                signOut()
            } else {
                signIn()
            }
        }

        toggleMonitorButton.setOnClickListener {
            toggleMonitoring()
        }
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            isSignedIn = false
            accountText.text = "Not signed in"
            signInButton.text = getString(R.string.sign_in)
            toggleMonitorButton.isEnabled = false
            clipboardAdapter.updateItems(emptyList())
            
            if (isMonitoring) {
                toggleMonitoring()
            }
        }
    }

    private fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            
            driveHelper.initializeDriveService(account)
            handleSignedIn(account.email)
            loadClipboardHistory()
            
        } catch (e: ApiException) {
            Toast.makeText(this, "Sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSignedIn(email: String?) {
        isSignedIn = true
        accountText.text = "Signed in as: $email"
        signInButton.text = getString(R.string.sign_out)
        toggleMonitorButton.isEnabled = true
    }

    private fun toggleMonitoring() {
        if (isMonitoring) {
            // Stop monitoring
            stopService(Intent(this, ClipboardMonitorService::class.java))
            isMonitoring = false
            statusText.text = getString(R.string.status_stopped)
            toggleMonitorButton.text = getString(R.string.start_monitoring)
        } else {
            // Start monitoring
            val serviceIntent = Intent(this, ClipboardMonitorService::class.java)
            startForegroundService(serviceIntent)
            isMonitoring = true
            statusText.text = getString(R.string.status_monitoring)
            toggleMonitorButton.text = getString(R.string.stop_monitoring)
        }
    }

    private fun loadClipboardHistory() {
        lifecycleScope.launch {
            try {
                val items = driveHelper.getClipboardItems()
                clipboardAdapter.updateItems(items)
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "Failed to load clipboard history",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun deleteClipboardItem(item: ClipboardItem) {
        // Note: This is a simplified version. In production, you'd want to
        // implement proper deletion from Google Drive
        Toast.makeText(this, "Delete functionality not yet implemented", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        if (isSignedIn) {
            loadClipboardHistory()
        }
    }
}
