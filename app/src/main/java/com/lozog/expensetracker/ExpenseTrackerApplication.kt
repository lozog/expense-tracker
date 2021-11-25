package com.lozog.expensetracker

import android.app.Application
import com.lozog.expensetracker.util.expenserow.ExpenseRowDB

class ExpenseTrackerApplication : Application() {
    val database by lazy { ExpenseRowDB.getDatabase(this) }
    val sheetsRepository by lazy { SheetsRepository(database.expenseRowDao()) }
}