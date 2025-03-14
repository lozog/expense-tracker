package com.lozog.expensetracker.util

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lozog.expensetracker.ExpenseTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkAvailableWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    companion object {
        private const val TAG = "EXPENSE_TRACKER NetworkAvailableWorker"
    }

    override fun doWork(): Result {
        Log.d(TAG, "Network is now available!")
        val appContext = applicationContext as ExpenseTrackerApplication

        val job = CoroutineScope(Dispatchers.IO).launch {
            appContext.sheetsRepository.sendPendingExpenseRowsAsync().await()
        }

        return try {
            job.invokeOnCompletion { Log.d(TAG, "Network worker completed") }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Network worker failed", e)
            Result.retry() // Retry if something went wrong
        }
    }
}
