package com.lozog.expensetracker

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.api.services.sheets.v4.Sheets

class GoogleSheetsInterface (var googleAccount: GoogleSignInAccount, var spreadsheetService: Sheets) {
    companion object {
        private const val TAG = "GOOGLE_SHEETS_INTERFACE"
    }
}