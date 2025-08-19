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
import com.lozog.expensetracker.util.CalendarHelper
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
            application.setupNetworkWorker()
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
    private fun sendExpenseRowAsync(expenseRow: ExpenseRow, rowToUse: Int? = null) = coroutineScope.async {
        Log.d(TAG, "sendExpenseRowAsync $rowToUse ${expenseRow.expenseItem}")

        checkSpreadsheetConnection()

        expenseRowDao.update(expenseRow)

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)
        val row: Int // row number in sheet
        val idColumn = "J"

        if (rowToUse != null) {
            row = rowToUse
        } else {
            val idRange = "$sheetName!A$idColumn:$idColumn"
            val existingIds = application.spreadsheetService!!
                .spreadsheets()
                .values()
                .get(spreadsheetId, idRange)
                .execute()
                .getValues()
                ?.mapNotNull { it.firstOrNull()?.toString() }
                ?: emptyList()

            val existingRowIndex = existingIds.indexOf(expenseRow.submissionId)
            if (existingRowIndex >= 0) {
                // row already exists
                row = existingRowIndex + 1
                expenseRow.row = row
            } else {
                // Doesn't exist; treat as new
                row = existingIds.size + 1
            }
        }

        val expenseTotal = "=(\$D$row - \$E$row)*IF(NOT(ISBLANK(\$I$row)), \$I$row, 1)"
        expenseRow.expenseTotal = expenseTotal

        val rowData = listOf(expenseRow.toList())
        val requestBody = ValueRange()
        requestBody.setValues(rowData)

        application.spreadsheetService!!
            .spreadsheets()
            .values()
            .update(spreadsheetId, "'$sheetName'!$row:$row", requestBody)
            .setValueInputOption(SHEETS_VALUE_INPUT_OPTION)
            .execute()

        expenseRow.syncStatus = ExpenseRow.STATUS_DONE
        expenseRowDao.update(expenseRow)
    }

    /**
     * sends all pending ExpenseRows to the sheet
     */
    fun sendPendingExpenseRowsAsync() = coroutineScope.async {
        Log.d(TAG, "sendPendingExpenseRowsAsync")
        val pendingExpenseRows = expenseRowDao.getPendingExpenseRows()
        coroutineScope {
            pendingExpenseRows.map { expense ->
                async(Dispatchers.IO) {
                    sendExpenseRowAsync(expense)
                }
            }.awaitAll()
            Log.d(TAG, "sendPendingExpenseRowsAsync done")
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
     * Given a category, returns the amount spent in that category so far this month
     * based on local data
     */
    fun getCategorySpending(
        expenseCategoryValue: String
    ): Float {
        Log.d(TAG, "getCategorySpending")
        val curMonth = Calendar.getInstance().get(Calendar.MONTH)

        val expenses = expenseRowDao.getExpensesByCategory(expenseCategoryValue)
        // find expenses of current month, add them to total

        var sum = 0.0f
        expenses.forEach {
            val expenseMonth = (CalendarHelper.parseDatestring(it.expenseDate)?.monthValue ?: 0) - 1

            if (expenseMonth == curMonth) {
                sum += it.expenseAmount.toFloatOrNull() ?: 0.0f
            }
        }
        return sum
    }

    /**
     * Returns a map (category -> amount) of all category spending totals.
     * Filters by month, if provided.
     */
    suspend fun getAllCategorySpending(month: Int?): MutableMap<String, Float> {
        Log.d(TAG, "getAllCategorySpending")

        val expenses = withContext(Dispatchers.IO) {
            expenseRowDao.getAllExpenseRows()
        }

        val categorySums = mutableMapOf<String, Float>()

        for (expenseRow in expenses) {
            val expenseMonth = (CalendarHelper.parseDatestring(expenseRow.expenseDate)?.monthValue ?: 0) - 1

            if (month != null && expenseMonth != month) continue
            val amount = expenseRow.expenseAmount.toFloatOrNull() ?: 0.0f
            categorySums[expenseRow.expenseCategoryValue.lowercase()] = categorySums.getOrPut(expenseRow.expenseCategoryValue.lowercase()) { 0f } + amount
        }

        return categorySums
    }

    /**
     * Syncs the local DB with the remote sheet, which is the source of truth.
     *
     * 1. Sends all pending rows to the sheet
     * 2. Fetches all rows from sheet
     * 3. For every row in the sheet, ensure an associated row exists in local DB
     * 4. If not, create it. Send submissionId to the sheet.
     * 5. Delete any rows in local DB that didn't have an associated row in the DB or has been soft-deleted
     */
    fun syncExpenseRowsAsync() = coroutineScope.async {
        Log.d(TAG, "syncExpenseRowsAsync")

        checkSpreadsheetConnection()

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

        sendPendingExpenseRowsAsync().await()

        val rowsFromSheet = application
            .spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, sheetName)
            .setValueRenderOption("UNFORMATTED_VALUE")
            .setDateTimeRenderOption("FORMATTED_STRING")
            .execute()
            .getValues()


        val expenseRowsInDb: List<ExpenseRow> = expenseRowDao.getAll()
        val expenseRowsInDbBySubmissionId: Map<String, ExpenseRow> = expenseRowsInDb.associateBy { it.submissionId }

        val allExpensesFromSheet = rowsFromSheet.map { rowFromSheet -> ExpenseRow(rowFromSheet) }

        // Keep track of all submissionIds to keep
        val submissionIds = allExpensesFromSheet.map { it.submissionId }.toMutableList()

        allExpensesFromSheet.forEachIndexed {i, expenseRow ->
            expenseRow.row = i + 1
            expenseRow.syncStatus = ExpenseRow.STATUS_DONE

            val foundExpenseRow = expenseRowsInDbBySubmissionId[expenseRow.submissionId]

            val existsInSheet = foundExpenseRow != null

            if (existsInSheet && isValidUuid(expenseRow.submissionId)) {
                if (foundExpenseRow != expenseRow) {
                    Log.d(TAG, "Updating existing row at ${expenseRow.row} - ${expenseRow.submissionId}")
                    expenseRowDao.updateBySubmissionId(
                        expenseRow.expenseDate,
                        expenseRow.expenseItem,
                        expenseRow.expenseCategoryValue,
                        expenseRow.expenseAmount,
                        expenseRow.expenseAmountOthers,
                        expenseRow.expenseTotal,
                        expenseRow.expenseNotes,
                        expenseRow.currency,
                        expenseRow.exchangeRate,
                        ExpenseRow.STATUS_DONE,
                        expenseRow.row,
                        expenseRow.submissionId
                    )
                }
            } else if (expenseRow.submissionId == "Id") {
//                Log.d(TAG, "skipping Id row")

                return@forEachIndexed
            } else {
//                Log.d(TAG, "New row at ${expenseRow.row}, ${expenseRow.syncStatus}")
                expenseRow.syncStatus = ExpenseRow.STATUS_DONE

                if (!isValidUuid(expenseRow.submissionId)) {
                    expenseRow.submissionId = UUID.randomUUID().toString()

                    // upsert to set the submissionId
                    sendExpenseRowAsync(expenseRow, rowToUse = expenseRow.row).await()
                }

                expenseRowDao.insert(expenseRow)
                submissionIds += expenseRow.submissionId
            }
        }

        var numDeleted = expenseRowDao.deleteAllNotIn(submissionIds)
        numDeleted += expenseRowDao.removeDeleted()
        Log.d(TAG, "$numDeleted were deleted")
        Log.d(TAG, "syncExpenseRowsAsync done")
    }

    private fun isValidUuid(input: String?): Boolean {
        if (input == null) return false
        return try {
            UUID.fromString(input)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
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