package com.lozog.expensetracker

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import com.android.volley.DefaultRetryPolicy
import com.android.volley.VolleyError
import com.android.volley.Request as VolleyRequest
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.DeleteDimensionRequest
import com.google.api.services.sheets.v4.model.DimensionRange
import com.google.api.services.sheets.v4.model.Request as SheetsRequest
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.ValueRange
import com.lozog.expensetracker.util.NoInternetException
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.expenserow.ExpenseRowDao
import kotlinx.coroutines.*
import java.util.*


class SheetsRepository(private val expenseRowDao: ExpenseRowDao, private val application: ExpenseTrackerApplication) {
    private lateinit var sharedPreferences: SharedPreferences

    /********** CONCURRENCY **********/
    private val parentJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    companion object {
        private const val TAG = "EXPENSE_TRACKER SheetsRepository"

        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"

        // TODO: put this in a setting
        private val CATEGORY_ROW_MAP = mapOf(
            "Other Income" to "5",
            "Rent" to "12",
            "Utilities" to "13",
            "Donations" to "14",
            "Debt Repayment" to "15",
            "Groceries" to "19",
            "Dining Out" to "20",
            "Drinks" to "21",
            "Material Items" to "22",
            "Entertainment" to "23",
            "Transit" to "24",
            "Personal/Medical" to "25",
            "Gifts" to "26",
            "Travel" to "27",
            "Miscellaneous" to "28",
            "Film" to "29",
            "Household" to "30",
            "Music" to "31"
        )
    }

    fun setPreferences(newPrefs: SharedPreferences) {
        sharedPreferences = newPrefs
    }

    fun getRecentHistory(): LiveData<List<ExpenseRow>> {
        val res = MediatorLiveData<List<ExpenseRow>>()

        val pendingExpenses = expenseRowDao.getPendingExpenseRowsLiveData()
        val syncedExpenses = expenseRowDao.getExpenseRows(sharedPreferences.getString("history_length", "5")!!.toInt())

        res.addSource(syncedExpenses) { filteredList ->
            // Get the latest value of the pending list
            val pendingList = pendingExpenses.value ?: emptyList()
            // Combine the lists and update MediatorLiveData
            res.value = (pendingList + filteredList).distinctBy { it.id }
        }

        res.addSource(pendingExpenses) { pendingList ->
            // Get the latest value of the filtered list
            val filteredList = syncedExpenses.value ?: emptyList()
            // Combine the lists and update MediatorLiveData
            res.value = (pendingList + filteredList).distinctBy { it.id }
        }

        return res
    }

    fun getExpenseRowByIdAsync(id: Int): Deferred<ExpenseRow> = coroutineScope.async {
        return@async expenseRowDao.getById(id).first()
    }

    /**
     * Make a ping call to google to test for internet connectivity
     */
    private suspend fun checkInternetConnectivity(): Boolean = suspendCancellableCoroutine { continuation ->
//        Log.d(TAG, "checkInternetConnectivity")

        val queue = Volley.newRequestQueue(application)

        val request = StringRequest(
            VolleyRequest.Method.GET, "https://google.com/",
            {
//                Log.d(TAG, "found internet!")
                continuation.resumeWith(Result.success(true)) // Internet is available
            },
            { error: VolleyError? ->
//                Log.d(TAG, "no internet :(")
                Log.e(TAG, error?.message ?: "Unknown error")
                continuation.resumeWith(Result.success(false)) // No internet connection
            })

        request.retryPolicy = DefaultRetryPolicy(
            5000, // timeout in milliseconds
            2,    // number of retries
            1.5f  // backoff multiplier
        )

        queue.add(request)
    }

    /*
     * Throws if there is no internet connection or the spreadsheetService isn't set up
     */
    private suspend fun checkSpreadsheetConnection() = withContext(Dispatchers.IO) {
//        Log.d(TAG, "checkSpreadsheetConnection")
        val hasInternetConnection = checkInternetConnectivity()
        if (!hasInternetConnection) {
            Log.d(TAG, "no internet")
            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "no spreadsheetService")
            throw CancellationException("no spreadsheetService")
        }
    }

    /********** GOOGLE SHEETS METHODS **********/

    /**
     * Upserts an ExpenseRow into the spreadsheet
     */
    private fun sendExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
        Log.d(TAG, "sendExpenseRowAsync")

        checkSpreadsheetConnection()

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)
        val row: Int // row number in sheet
        var isNewRow = false

         if (expenseRow.row == 0) {
             // if new row, call spreadsheet service to get an up-to-date row count
             isNewRow = true

             // TODO: break out into helper
             row = application.spreadsheetService!!
                .spreadsheets()
                .values()
                .get(spreadsheetId, sheetName)
                .execute()
                .getValues()
                .size + 1
        } else {
            row = expenseRow.row
        }

        val expenseTotal = "=(\$D$row - \$E$row)*IF(NOT(ISBLANK(\$I$row)), \$I$row, 1)"
        expenseRow.expenseTotal = expenseTotal

        val rowData = listOf(expenseRow.toList())
        val requestBody = ValueRange()
        requestBody.setValues(rowData)

        if (isNewRow) {
//             Log.d(TAG, "inserting a new row")

            // insert new row
            application.spreadsheetService!!
                .spreadsheets()
                .values()
                .append(spreadsheetId, sheetName, requestBody)
                .setValueInputOption(SHEETS_VALUE_INPUT_OPTION)
                .setInsertDataOption(SHEETS_INSERT_DATA_OPTION)
                .execute()

            expenseRow.row = row
        } else {
//             Log.d(TAG, "updating row $row - $expenseRow")

            // update existing row
            application.spreadsheetService!!
                .spreadsheets()
                .values()
                .update(spreadsheetId, "'$sheetName'!$row:$row", requestBody)
                .setValueInputOption(SHEETS_VALUE_INPUT_OPTION)
                .execute()
        }

        expenseRow.syncStatus = ExpenseRow.STATUS_DONE
//        Log.d(TAG, "done")
        expenseRowDao.update(expenseRow)
    }

    /**
     * sends all pending ExpenseRows to the sheet
     */
    fun sendPendingExpenseRowsAsync() = coroutineScope.async {
        val pendingExpenseRows = expenseRowDao.getPendingExpenseRows()
        coroutineScope {
            pendingExpenseRows.map { expense ->
                async(Dispatchers.IO) {
                    sendExpenseRowAsync(expense)
                }
            }.awaitAll()
        }
    }

    /**
     * Wraps sendExpenseRowAsync by upserting expenseRow into DB and checking for connection to spreadsheet service
     */
    fun upsertExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
        Log.d(TAG, "upsertExpenseRowAsync")

        if (expenseRow.id == 0) { // not in DB
            val expenseRowId = expenseRowDao.insert(expenseRow)

            expenseRow.id = expenseRowId.toInt()
            Log.d(TAG, "inserted into db with id $expenseRowId")
        } else {
            expenseRowDao.update(expenseRow)
        }

        sendExpenseRowAsync(expenseRow).await()
    }

    /**
     * Given a category, fetches the amount spent in that category so far this month
     * TODO: instead of querying the sheet, guesstimate by using local data
     */
    fun fetchCategorySpendingAsync(
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async {
        Log.d(TAG, "fetchCategorySpendingAsync")
        checkSpreadsheetConnection()

        val janColumnPref = sharedPreferences.getString("month_column", null)

        if (janColumnPref == null) {
            Log.d(TAG, "fetchCategorySpendingAsync - no January Column")
            throw Exception("fetchCategorySpendingAsync - no January Column")
        }

        val curMonthColumn = (janColumnPref.first().code + Calendar.getInstance().get(Calendar.MONTH)).toChar()
        val categoryCell = CATEGORY_ROW_MAP[expenseCategoryValue]
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val overviewSheetName = sharedPreferences.getString("overview_sheet_name", null)

        if (categoryCell == null) {
            Log.e(TAG, "Category $expenseCategoryValue not found")
            throw Exception("Category $expenseCategoryValue not found")
        }

        val categorySpendingCell = "'$overviewSheetName'!$curMonthColumn$categoryCell"
        val data = application.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, categorySpendingCell)
            .execute()
            .getValues()

        val spentSoFar = data[0][0]

        return@async "$spentSoFar".trim()
    }

    /**
     * Fetches all expense rows from the sheet, then replaces local DB with fetched rows
     */
    fun fetchExpenseRowsFromSheetAsync() = coroutineScope.async {
        Log.d(TAG, "fetchExpenseRowsFromSheetAsync")

        checkSpreadsheetConnection()

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

        sendPendingExpenseRowsAsync().await()

        val values = application
            .spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, sheetName)
            .setValueRenderOption("UNFORMATTED_VALUE")
            .setDateTimeRenderOption("FORMATTED_STRING")
            .execute()
            .getValues()

        val allExpensesFromSheet = values.map { value -> ExpenseRow(value) }
        allExpensesFromSheet.forEachIndexed {i, expenseRow ->
            // go through each one. make sure it's in the DB
            expenseRow.row = i + 1
            expenseRow.syncStatus = ExpenseRow.STATUS_DONE
        }
        expenseRowDao.deleteAllDoneAndInsertMany(allExpensesFromSheet)
    }

    /**
     * Deletes expense row at given row number from spreadsheet and local DB
     */
    fun deleteRowAsync(expenseId: Int) = coroutineScope.async {
        Log.d(TAG, "deleteRowAsync")
        val expenseRow = expenseRowDao.getById(expenseId).first()
        val row = expenseRow.row

        if (row == 0) {
            // has not been sent to sheet yet
            expenseRowDao.setDeletedById(expenseId)
            return@async
        }

        checkSpreadsheetConnection()

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetId = sharedPreferences.getString("data_sheet_id", "0")?.toInt()

        val deleteRequest: SheetsRequest = SheetsRequest()
            .setDeleteDimension(
                DeleteDimensionRequest()
                    .setRange(
                        DimensionRange()
                            .setSheetId(sheetId)
                            .setDimension("ROWS")
                            .setStartIndex(row-1)
                            .setEndIndex(row)
                    )
            )
        val updateRequest = BatchUpdateSpreadsheetRequest()
        updateRequest.requests = listOf(
            deleteRequest
        )

        application.spreadsheetService!!
            .spreadsheets()
            .batchUpdate(spreadsheetId, updateRequest)
            .execute()

        expenseRowDao.setDeletedByRow(row)
    }

    /**
     * Fetches all spreadsheets on the account
     */
    fun fetchSpreadsheetsAsync(): Deferred<List<File>> = coroutineScope.async {
        Log.d(TAG, "fetchSpreadsheetsAsync")
        checkSpreadsheetConnection()

        val data: FileList = application.driveService!!
            .files()
            .list()
            .setQ("mimeType='application/vnd.google-apps.spreadsheet'")
            .execute()

        return@async data.files as List<File>
    }

    /**
     * Fetches all sheets within the given spreadsheet
     */
    fun fetchSheetsAsync(spreadsheetId: String): Deferred<List<Sheet>> = coroutineScope.async {
        Log.d(TAG, "fetchSheetsAsync")
        checkSpreadsheetConnection()

        val sheets = application.spreadsheetService!!
            .spreadsheets()
            .get(spreadsheetId)
            .execute()

        return@async sheets.sheets
    }
}