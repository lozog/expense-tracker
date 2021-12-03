package com.lozog.expensetracker.util

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.sheets.v4.Sheets

class SheetsInterface {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SHEETS_INTERFACE"

        var googleAccount: GoogleSignInAccount? = null
        var spreadsheetService: Sheets? = null
    }
}