package com.lozog.expensetracker

import android.app.Application
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.sheets.v4.Sheets
import com.lozog.expensetracker.util.NetworkMonitor
import com.lozog.expensetracker.util.expenserow.ExpenseRowDB

class ExpenseTrackerApplication : Application() {

    val database by lazy { ExpenseRowDB.getDatabase(this) }
    val sheetsRepository by lazy { SheetsRepository(database.expenseRowDao(), this) }
    var googleAccount: GoogleSignInAccount? = null
    var spreadsheetService: Sheets? = null

    override fun onCreate() {
        super.onCreate()
        NetworkMonitor(this).startNetworkCallback()
    }

    override fun onTerminate() {
        super.onTerminate()
        NetworkMonitor(this).stopNetworkCallback()
    }
}