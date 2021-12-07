package com.lozog.expensetracker

import android.content.SharedPreferences
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.google.api.services.sheets.v4.model.*
import com.lozog.expensetracker.util.ConnectivityHelper
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.NotSignedInException
import com.lozog.expensetracker.util.expenserow.ExpenseRowDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.*
import kotlin.coroutines.coroutineContext


class SheetsRepository(private val expenseRowDao: ExpenseRowDao, private val application: ExpenseTrackerApplication) {
    lateinit var recentHistory: Flow<List<ExpenseRow>>
    private lateinit var sharedPreferences: SharedPreferences

    /********** CONCURRENCY **********/
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    companion object {
        private const val TAG = "EXPENSE_TRACKER SHEETS_REPOSITORY"

        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"

        // January -> column C, etc
        // TODO: dynamically find month columns
        private val MONTH_COLUMNS = listOf(
            "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"
        )

        // TODO: dynamically find category cell
        private val CATEGORY_ROW_MAP = mapOf(
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
            "Household" to "31",
            "Other Income" to "5"
        )
        val CATEGORIES = arrayOf(
            "Groceries",
            "Dining Out",
            "Drinks",
            "Material Items",
            "Entertainment",
            "Transit",
            "Personal/Medical",
            "Gifts",
            "Travel",
            "Miscellaneous",
            "Film",
            "Household",
            "Other Income"
        )
    }

    fun setPreferences(newPrefs: SharedPreferences) {
        sharedPreferences = newPrefs
        recentHistory = expenseRowDao.getN(sharedPreferences.getString("history_length", "5")!!.toInt())
    }

    fun getExpenseRowByRowAsync(row: Int): Deferred<List<ExpenseRow>> = coroutineScope.async {
        return@async expenseRowDao.getByRow(row)
    }

    /********** GOOGLE SHEETS METHODS **********/

    fun sendExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
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
        Log.d(TAG, "addExpenseRowToSheetAsync")
        if (expenseRow.id == 0) { // already in DB
            val expenseRowId = expenseRowDao.insert(expenseRow)
            expenseRow.id = expenseRowId.toInt()
        }

        // TODO: if no internet, skip
        if (!ConnectivityHelper.isInternetConnected(application)) {
            Log.d(TAG, "addExpenseRowToSheetAsync - no internet")
            return@async

            // TODO: this doesn't work
//            throw NoInternetException()
        }


        if (application.spreadsheetService == null) {
            Log.d(TAG, "addExpenseRowToSheetAsync - no spreadsheetservice")
            return@async

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        sendExpenseRowAsync(expenseRow).await()

        Log.d(TAG, "addExpenseRowToSheetAsync done")
    }

    fun addExpenseRowsAsync(expenseRows: List<ExpenseRow>) = coroutineScope.async {
        expenseRows.forEach {
            addExpenseRowAsync(it).await()
        }
    }

    // TODO: rename (fetchCategorySpendingAsync)
    fun getCategorySpendingAsync(
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async {
        if (!ConnectivityHelper.isInternetConnected(application)) {
            Log.d(TAG, "getRecentExpenseHistoryAsync - no internet")
            return@async "no internet"

            // TODO: this doesn't work
//            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "getRecentExpenseHistoryAsync - no spreadsheetservice")
            return@async "no spreadsheetservice"

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        val curMonthColumn = MONTH_COLUMNS[Calendar.getInstance().get(Calendar.MONTH)]
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

    // TODO: rename (fetchExpenseRowsFromSheetAsync)
    fun getRecentExpenseHistoryAsync() = coroutineScope.async {
        Log.d(TAG, "getRecentExpenseHistoryAsync")

        if (!ConnectivityHelper.isInternetConnected(application)) {
            Log.d(TAG, "getRecentExpenseHistoryAsync - no internet")
            return@async

            // TODO: this doesn't work
//            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "getRecentExpenseHistoryAsync - no spreadsheetservice")
            return@async

            // TODO: this doesn't work
//            throw NotSignedInException()
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

        expenseRowDao.setDeleted(row)

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
//        val sheetName = sharedPreferences.getString("data_sheet_name", null)
        val sheetId = 1283738573 // TODO: dynamically get sheetId

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
    }

    fun sendPendingRowsToSheetAsync() = coroutineScope.async {
        Log.d(TAG, "sendPendingRowsToSheetAsync")
        val pendingExpenseRows = expenseRowDao.getAllPending()

        try {
            coroutineScope {
                addExpenseRowsAsync(pendingExpenseRows).await()
                getRecentExpenseHistoryAsync()
            }
        } catch (e: Exception) {
            // TODO: this doesn't work
            Log.d(TAG, "caught $e")
            throw e
        }
    }

    fun fetchSpreadsheetsAsync(): Deferred<List<File>> = coroutineScope.async {
        if (!ConnectivityHelper.isInternetConnected(application)) {
            Log.d(TAG, "getRecentExpenseHistoryAsync - no internet")

            return@async listOf()
            // TODO: this doesn't work
//            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "getRecentExpenseHistoryAsync - no spreadsheetservice")
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
}