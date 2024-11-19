package com.lozog.expensetracker.util

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.lozog.expensetracker.ExpenseTrackerApplication
import kotlinx.coroutines.runBlocking

class NetworkAvailableWorker(context: Context, workerParams: WorkerParameters) :
    Worker(context, workerParams) {

    companion object {
        private const val TAG = "EXPENSE_TRACKER NetworkAvailableWorker"
    }

    override fun doWork(): Result {
        // Task to perform when triggered
        Log.d(TAG, "Network is now available!")
        runBlocking {
            (applicationContext as ExpenseTrackerApplication).sheetsRepository.sendPendingExpenseRowsAsync().await()
        }
        return Result.success()
    }
}
