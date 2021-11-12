package com.lozog.expensetracker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

        val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

        connectivityManager?.activeNetwork?.let { activeNetwork ->
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return Result.retry()

            Log.d(TAG, "capabilities: $capabilities")

            Log.d(TAG, "wifi ${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
            Log.d(TAG, "cellular ${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
            Log.d(TAG, "ethernet ${capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")

            if (!(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                Log.d(TAG, "no internet - will retry")

                return Result.retry()
            }
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
