package com.example.clipboardsync.drive

import android.content.Context
import com.example.clipboardsync.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class DriveAuthManager(private val context: Context) {

    private val client: GoogleSignInClient by lazy {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DRIVE_APPDATA_SCOPE))
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        GoogleSignIn.getClient(context, options)
    }

    fun getClient(): GoogleSignInClient = client

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    suspend fun silentSignIn(): GoogleSignInAccount? =
        client.silentSignIn().await()

    fun signOut() {
        client.signOut()
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
