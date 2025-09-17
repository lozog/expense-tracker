package com.lozog.expensetracker.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lozog.expensetracker.ExpenseTrackerApplication

class NetworkAvailableWorker(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "EXPENSE_TRACKER NetworkAvailableWorker"
        const val PERIODIC_UNIQUE = "PeriodicNetworkSync"
        const val IMMEDIATE_UNIQUE = "ImmediateNetworkSync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork id=$id attempt=${runAttemptCount + 1}")

        return try {
            val app = applicationContext as ExpenseTrackerApplication
            // Your sequential sender throws only for transient errors.
//            app.sheetsRepository.syncExpenseRowsAsync()

            Log.d(TAG, "Success")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Failure -> retry", t)
            // Transient error path; permanent errors should be handled in the repo
            Result.retry()
        }
    }
}
