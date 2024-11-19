package com.lozog.expensetracker

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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
//        Log.d(TAG, "onCreate")

        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        this.sheetsRepository.setPreferences(sharedPreferences)

        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        // TODO: just check GoogleSheetsInterface.googleAccount != null?
        val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            onSignInSuccess(account)
        }

        setupNetworkWorker(this)
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

    private fun setupNetworkWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<NetworkAvailableWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "NetworkAvailableWorker",
            ExistingPeriodicWorkPolicy.KEEP, // Ensures only one instance runs
            workRequest
        )
    }
}