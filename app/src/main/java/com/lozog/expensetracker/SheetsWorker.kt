package com.lozog.expensetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lozog.expensetracker.ui.form.FormFragment
import com.lozog.expensetracker.util.ConnectivityHelper
import com.lozog.expensetracker.util.ExpenseRow

class SheetsWorker(
    appContext: Context, workerParams: WorkerParameters
): CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SHEETS_WORKER"
    }
    private val sheetsRepository = SheetsRepository()

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")

        if (!ConnectivityHelper.isInternetConnected(applicationContext)) {
            return Result.retry()
        }

        sheetsRepository.addExpenseRowToSheetAsync(
            inputData.getString("spreadsheetId")!!,
            inputData.getString("sheetName")!!,
            ExpenseRow(inputData)
        ).await()

        val outputData = workDataOf("expenseItem" to inputData.getString("expenseItem")!!)

        return Result.success(outputData)
    }
}
