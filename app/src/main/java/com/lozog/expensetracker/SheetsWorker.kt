package com.lozog.expensetracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SheetsWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SHEETS_WORKER"
    }
    private val sheetsRepository = SheetsRepository()

    override suspend fun doWork(): Result {

        Log.d(TAG, "SheetsWorker.doWork()")
        sheetsRepository.addExpenseRowToSheetAsync(
            inputData.getString("spreadsheetId")!!,
            inputData.getString("sheetName")!!,
            inputData.getString("expenseDate")!!,
            inputData.getString("expenseItem")!!,
            inputData.getString("expenseCategory")!!,
            inputData.getString("expenseAmount")!!,
            inputData.getString("expenseAmountOthers")!!,
            inputData.getString("expenseNotes")!!,
            inputData.getString("currency")!!,
            inputData.getString("exchangeRate")!!
        )

        return Result.success()
    }
}
