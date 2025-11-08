package com.example.cloudclipboard.drive

import android.content.Context
import com.example.cloudclipboard.R
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes

object DriveServiceFactory {
    fun create(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            setOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive
            .Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
            .setApplicationName(context.getString(R.string.app_name))
            .build()
    }
}
