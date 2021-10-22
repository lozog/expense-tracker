package com.lozog.expensetracker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class SheetsWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "SHEETS_WORKER"
    }
    private val sheetsRepository = SheetsRepository()

    override suspend fun doWork(): Result {

        // TODO: bug when multiple requests are queued, it doesn't get the correct row
        // it uses the same row for each

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

        val outputData = workDataOf("expenseItem" to inputData.getString("expenseItem")!!)

        return Result.success(outputData)
    }
}
