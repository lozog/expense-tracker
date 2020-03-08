package com.lozog.expensetracker

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.services.sheets.v4.Sheets

class GoogleSheetsInterface {
    companion object {
        private const val TAG = "GOOGLE_SHEETS_INTERFACE"

        var googleAccount: GoogleSignInAccount? = null
        var spreadsheetService: Sheets? = null
    }
}