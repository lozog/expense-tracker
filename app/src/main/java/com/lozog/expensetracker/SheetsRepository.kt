package com.lozog.expensetracker

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.api.services.sheets.v4.model.*
import com.lozog.expensetracker.util.ConnectivityHelper
import com.lozog.expensetracker.util.NoInternetException
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.NotSignedInException
import com.lozog.expensetracker.util.expenserow.ExpenseRowDao
import kotlinx.coroutines.*
import java.util.*


class SheetsRepository(private val expenseRowDao: ExpenseRowDao, private val application: ExpenseTrackerApplication) {
    private lateinit var sharedPreferences: SharedPreferences

    private var monthColumns: List<String> = listOf()

    /********** CONCURRENCY **********/
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    companion object {
        private const val TAG = "EXPENSE_TRACKER SHEETS_REPOSITORY"

        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"

        // TODO: dynamically find category cell
        private val CATEGORY_ROW_MAP = mapOf(
            "Other Income" to "5",
            "Rent" to "12",
            "Internet" to "13",
            "Hydro" to "14",
            "Tenant Insurance" to "15",
            "Phone" to "16",
            "Spotify" to "17",
            "Debt Repayment" to "18",
            "Groceries" to "20",
            "Dining Out" to "21",
            "Drinks" to "22",
            "Material Items" to "23",
            "Entertainment" to "24",
            "Transit" to "25",
            "Personal/Medical" to "26",
            "Gifts" to "27",
            "Travel" to "28",
            "Miscellaneous" to "29",
            "Film" to "30",
            "Household" to "31"
        )
    }

    fun setPreferences(newPrefs: SharedPreferences) {
        sharedPreferences = newPrefs
    }

    fun getRecentHistory(): LiveData<List<ExpenseRow>> {
        return expenseRowDao.getExpenseRows(sharedPreferences.getString("history_length", "5")!!.toInt())
    }

    fun getExpenseRowByRowAsync(row: Int): Deferred<List<ExpenseRow>> = coroutineScope.async {
        return@async expenseRowDao.getByRow(row)
    }

    /********** GOOGLE SHEETS METHODS **********/

    /**
     * Make a ping call to the spreadsheet service to test for internet connectivity
     */
    private fun checkInternetConnectivityAsync(): Deferred<Boolean> = coroutineScope.async {
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
//        Log.d(TAG, "checkInternetConnectivityAsync")

        try {
            withTimeout(1000L) {
                application.spreadsheetService!!.spreadsheets().get(spreadsheetId).execute()
            }
        } catch (e: CancellationException) {
            return@async false

        }

        return@async true
    }

    private fun sendExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)
        val row: Int;
        var newRow = false

         if (expenseRow.row == 0) {
             newRow = true
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

        val expenseTotal =
            "=(\$D$row - \$E$row)*IF(NOT(ISBLANK(\$I$row)), \$I$row, 1)"
        expenseRow.expenseTotal = expenseTotal

        val rowData = listOf(
            expenseRow.toList()
        )
        val requestBody = ValueRange()
        requestBody.setValues(rowData)

        if (newRow) {
            // Log.d(TAG, "inserting a new row")
            application.spreadsheetService!!
                .spreadsheets()
                .values()
                .append(spreadsheetId, sheetName, requestBody)
                .setValueInputOption(SHEETS_VALUE_INPUT_OPTION)
                .setInsertDataOption(SHEETS_INSERT_DATA_OPTION)
                .execute()

            expenseRow.row = row
        } else {
            // Log.d(TAG, "updating row $row - $expenseRow")

            application.spreadsheetService!!
                .spreadsheets()
                .values()
                .update(spreadsheetId, "'$sheetName'!$row:$row", requestBody)
                .setValueInputOption(SHEETS_VALUE_INPUT_OPTION)
                .execute()
        }

        expenseRow.syncStatus = ExpenseRow.STATUS_DONE
        expenseRowDao.update(expenseRow)
    }

    fun addExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
        Log.d(TAG, "addExpenseRowAsync")
        if (expenseRow.id == 0) { // already in DB
            val expenseRowId = expenseRowDao.insert(expenseRow)
            expenseRow.id = expenseRowId.toInt()
        }

        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "addExpenseRowAsync - no internet")
            return@async
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "addExpenseRowAsync - no spreadsheetservice")
            return@async

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        sendExpenseRowAsync(expenseRow).await()

        Log.d(TAG, "addExpenseRowAsync done")
    }

    private fun addExpenseRowsAsync(expenseRows: List<ExpenseRow>) = coroutineScope.async {
        expenseRows.forEach {
            addExpenseRowAsync(it).await()
        }
    }

    fun fetchCategorySpendingAsync(
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async {
        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "fetchCategorySpendingAsync - no internet")
            return@async "no internet"
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "fetchCategorySpendingAsync - no spreadsheetservice")
            return@async "no spreadsheetservice"

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        if (monthColumns.isEmpty()) {
            Log.d(TAG, "fetchCategorySpendingAsync - no monthColumns")
            return@async "no monthColumns"

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        val curMonthColumn = monthColumns[Calendar.getInstance().get(Calendar.MONTH)]
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

        return@async "$spentSoFar"
    }

    fun fetchExpenseRowsFromSheetAsync() = coroutineScope.async {
        Log.d(TAG, "fetchExpenseRowsFromSheetAsync")

        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "fetchExpenseRowsFromSheetAsync - no internet")
            return@async
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "fetchExpenseRowsFromSheetAsync - no spreadsheet service")
            throw CancellationException("no spreadsheet service")
        }

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

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

    fun deleteRowAsync(row: Int) = coroutineScope.async {
        if (application.spreadsheetService == null) {
            throw NotSignedInException()
        }

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetId = sharedPreferences.getString("data_sheet_id", "0")?.toInt()

        val deleteRequest: Request = Request()
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

        expenseRowDao.setDeleted(row)
    }

    fun sendPendingRowsToSheetAsync() = coroutineScope.async {
        Log.d(TAG, "sendPendingRowsToSheetAsync")
        val pendingExpenseRows = expenseRowDao.getAllPendingExpenseRows()

        try {
            coroutineScope {
                addExpenseRowsAsync(pendingExpenseRows).await()
                fetchExpenseRowsFromSheetAsync()
            }
        } catch (e: Exception) {
            // TODO: figure out why you get the parent job is cancelled exception sometimes
            Log.d(TAG, "exception in sendPendingRowsToSheetAsync")
            Log.d(TAG, "caught $e")
            throw e
        }
    }

    fun fetchSpreadsheetsAsync(): Deferred<List<File>> = coroutineScope.async {
        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "fetchSpreadsheetsAsync - no internet")
            return@async listOf()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "fetchSpreadsheetsAsync - no spreadsheetservice")
            return@async listOf()

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        Log.d(TAG, "fetchSpreadsheets")

        val data: FileList = application.driveService!!
            .files()
            .list()
            .setQ("mimeType='application/vnd.google-apps.spreadsheet'")
            .execute()

        return@async data.files as List<File>
    }

    fun fetchSheetsAsync(spreadsheetId: String): Deferred<List<Sheet>> = coroutineScope.async {
        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "fetchSheetsAsync - no internet")
            return@async listOf()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "fetchSheetsAsync - no spreadsheetservice")
            return@async listOf()

            // TODO: this doesn't work
            // throw NotSignedInException()
        }

        Log.d(TAG, "fetchSheets")

        val sheets = application.spreadsheetService!!
            .spreadsheets()
            .get(spreadsheetId)
            .execute()

        return@async sheets.sheets
    }

    fun findMonthColumnsAsync() = coroutineScope.launch {
        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "findMonthColumnsAsync - no internet")
            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "findMonthColumnsAsync - no spreadsheetservice")

            // TODO: this doesn't work
            throw NotSignedInException()
        }

        Log.d(TAG, "findMonthColumnsAsync")

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val overviewSheetName = sharedPreferences.getString("overview_sheet_name", null)

        val firstRowRange = "'$overviewSheetName'!1:1"
        val firstRow = application.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, firstRowRange)
            .execute()
            .getValues()
            .first()

        // we'll search the first row for "January", and assume that the next column is "February", etc.
        val januaryColumn = ('A'.code + firstRow.indexOf("January")).toChar()
        // Log.d(TAG, "jan col code: $januaryColumn")

        val preferenceEditor = sharedPreferences.edit()
        preferenceEditor.putString("month_column", januaryColumn.toString())
        preferenceEditor.apply()

        // TODO: this doesn't persist
        monthColumns = (0..11).map {
            (januaryColumn.code + it).toChar().toString()
        }
        // Log.d(TAG, monthColumns.toString())
    }

    fun findCategoriesAsync() = coroutineScope.launch {
        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "findCategoriesAsync - no internet")
            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "findCategoriesAsync - no spreadsheetservice")

            // TODO: this doesn't work
            throw NotSignedInException()
        }

        Log.d(TAG, "findCategoriesAsync")

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val overviewSheetName = sharedPreferences.getString("overview_sheet_name", null)

        val firstColRange = "'$overviewSheetName'!A:A"
        val firstColValues = application.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, firstColRange)
            .execute()
            .getValues()

        firstColValues.forEach {
            Log.d(TAG, it.toString())
        }

        val firstCategoryRow = firstColValues.indexOfFirst { it as List<*>
            it.isNotEmpty() && it.first() == "Variable Expenses"
        } + 1 // rows start at 1
        val lastCategoryRow = firstColValues.indexOfFirst { it as List<*>
            it.isNotEmpty() && it.first() == "vee"
        } - 1 // last category row is 2 before this one

        Log.d(TAG, "first row: $firstCategoryRow, last row: $lastCategoryRow")

        val categoriesRange = "'$overviewSheetName'!B$firstCategoryRow:B$lastCategoryRow"
        val categoriesValues = application.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, categoriesRange)
            .execute()
            .getValues()

        Log.d(TAG, categoriesValues.toString())
        // TODO: this doesn't include Other Income as a category
        // maybe we just need to show the user everything in the B column and let them pick which ones are categories
        // and also let them reorder them
        // multi select alertdialog?

    }

    fun fetchCategoriesAsync(): Deferred<List<String>> = coroutineScope.async {
        val hasInternet = checkInternetConnectivityAsync().await()
        if (!hasInternet) {
            Log.d(TAG, "fetchCategoriesAsync - no internet")
            return@async listOf()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "fetchCategoriesAsync - no spreadsheetservice")
            return@async listOf()

            // TODO: this doesn't work
            // throw NotSignedInException()
        }

        Log.d(TAG, "fetchCategoriesAsync")

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val overviewSheetName = sharedPreferences.getString("overview_sheet_name", null)
        val monthColumn = sharedPreferences.getString("month_column", null)?: "C"

        // assume categories will be in the column before the January column
        val categoriesColumn = (monthColumn.single().code - 1).toChar()

        val categoriesColRange = "'$overviewSheetName'!$categoriesColumn:$categoriesColumn"
        val categoriesColValues = application.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, categoriesColRange)
            .execute()
            .getValues()

        // categoriesColValues.forEach {
        //     Log.d(TAG, it.toString())
        // }

        val categories = categoriesColValues
            .filter { it.isNotEmpty() }
            .map { it.first().toString() }

        return@async categories
    }
}