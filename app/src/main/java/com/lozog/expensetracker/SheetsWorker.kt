package com.lozog.expensetracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lozog.expensetracker.util.ConnectivityHelper
import com.lozog.expensetracker.util.expenserow.ExpenseRow

class SheetsWorker(
    appContext: Context, workerParams: WorkerParameters
): CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SHEETS_WORKER"
    }
    private val sheetsRepository = (applicationContext as ExpenseTrackerApplication).sheetsRepository

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")

        if (!ConnectivityHelper.isInternetConnected(applicationContext)) {
            return Result.retry()
        }

        try {
            sheetsRepository.addExpenseRowToSheetAsync(
                ExpenseRow(inputData)
            ).await()
        } catch (e: Exception) {
            Log.d(TAG, e.toString())
            return Result.failure()
        }

        val outputData = workDataOf("expenseItem" to inputData.getString("expenseItem")!!)

        return Result.success(outputData)
    }
}
