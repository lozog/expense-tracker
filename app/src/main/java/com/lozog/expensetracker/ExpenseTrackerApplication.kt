package com.lozog.expensetracker

import android.app.Application
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.lozog.expensetracker.util.NetworkAvailableWorker
import com.lozog.expensetracker.util.expenserow.ExpenseRowDB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class ExpenseTrackerApplication : Application() {

    private val database by lazy { ExpenseRowDB.getDatabase(this) }
    val sheetsRepository by lazy { SheetsRepository(database.expenseRowDao(), this) }
    var googleAccount: GoogleSignInAccount? = null
    var spreadsheetService: Sheets? = null
    var driveService: Drive? = null

    private var jsonFactory: JsonFactory = JacksonFactory.getDefaultInstance()
    private var googleScopes = listOf(
        SheetsScopes.SPREADSHEETS,
        DriveScopes.DRIVE_METADATA_READONLY
    )

    companion object {
        private const val TAG = "EXPENSE_TRACKER ExpenseTrackerApplication"
    }

    override fun onCreate() {
        super.onCreate()
        if (!isDefaultProcess()) {
            Log.d(TAG, "onCreate - detected non-default process")
         return // prevents double scheduling
        }
//        Log.d(TAG, "onCreate")

        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        this.sheetsRepository.setPreferences(sharedPreferences)

        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        // TODO: just check GoogleSheetsInterface.googleAccount != null?
        val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            onSignInSuccess(account)
        }

        // disabled these for now because there were job storms
//        setupPeriodicSync()
//        kickoffImmediateSync()
    }

    private fun isDefaultProcess(): Boolean {
        val name = try {
            getProcessName()
        } catch (_: Throwable) {
            null
        }
        return name == packageName
    }

    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // min interval is 15 minutes
        val periodic = PeriodicWorkRequestBuilder<NetworkAvailableWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
//            .setFlexInterval(5, TimeUnit.MINUTES) // flex lets Android choose the exact time within the window, helps batching
            .addTag("NetworkSync")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            NetworkAvailableWorker.PERIODIC_UNIQUE,
            ExistingPeriodicWorkPolicy.KEEP,  // don’t reset if already scheduled
            periodic
        )
    }

    private fun kickoffImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val once = OneTimeWorkRequestBuilder<NetworkAvailableWorker>()
            .setConstraints(constraints)
            .addTag("NetworkSyncImmediate")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            NetworkAvailableWorker.IMMEDIATE_UNIQUE,
            ExistingWorkPolicy.KEEP,
            once
        )
    }

    fun onSignInSuccess(account: GoogleSignInAccount) {
        Log.d(TAG, "signed into account: ${account.email}")

        val httpTransport = NetHttpTransport()
        val credential = GoogleAccountCredential.usingOAuth2(this, googleScopes)
        credential.selectedAccount = account.account

        val spreadsheetService: Sheets = Sheets.Builder(httpTransport,
            jsonFactory, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        val driveService: Drive = Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        this.googleAccount = account
        this.spreadsheetService = spreadsheetService
        this.driveService = driveService
    }
}