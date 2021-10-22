package com.lozog.expensetracker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class SheetsWorker(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {
    companion object {
        private const val TAG = "SHEETS_WORKER"
    }
    override fun doWork(): Result {

        Log.d(TAG, "SheetsWorker.doWork()")

        return Result.success()
    }
}
