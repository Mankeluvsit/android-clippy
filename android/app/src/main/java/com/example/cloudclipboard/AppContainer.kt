package com.example.cloudclipboard

import android.app.Application
import android.os.Build
import com.example.cloudclipboard.data.ClipboardDatabase
import com.example.cloudclipboard.data.ClipboardRepository
import com.example.cloudclipboard.drive.DriveClipboardDataSource
import com.example.cloudclipboard.drive.DriveServiceFactory
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.serialization.json.Json
import timber.log.Timber

class CloudClipboardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        container = AppContainer(this)
    }
}

class AppContainer(private val application: Application) {

    private val database by lazy { ClipboardDatabase.instance(application) }
    private val dao by lazy { database.clipboardDao() }

    val json: Json by lazy {
        Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }

    val deviceName: String by lazy { Build.MODEL ?: "Android" }

    fun createRepository(account: GoogleSignInAccount): ClipboardRepository {
        val drive = DriveServiceFactory.create(application, account)
        val driveSource = DriveClipboardDataSource(drive, json)
        return ClipboardRepository(
            dao = dao,
            driveDataSource = driveSource,
            deviceName = deviceName
        )
    }
}
