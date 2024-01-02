package com.lozog.expensetracker

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.LiveData
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
import com.lozog.expensetracker.util.NotSignedInException
import com.lozog.expensetracker.util.expenserow.ExpenseRowDao
import kotlinx.coroutines.*
import java.util.*


class SheetsRepository(private val expenseRowDao: ExpenseRowDao, private val application: ExpenseTrackerApplication) {
    private lateinit var sharedPreferences: SharedPreferences

    /********** CONCURRENCY **********/
    private val parentJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    private var hasInternetConnection = false

    companion object {
        private const val TAG = "EXPENSE_TRACKER SHEETS_REPOSITORY"

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
        return expenseRowDao.getExpenseRows(sharedPreferences.getString("history_length", "5")!!.toInt())
    }

    fun getExpenseRowByRowAsync(row: Int): Deferred<List<ExpenseRow>> = coroutineScope.async {
        return@async expenseRowDao.getByRow(row)
    }

    /********** GOOGLE SHEETS METHODS **********/

    /**
     * Make a ping call to google to test for internet connectivity
     */
    fun checkInternetConnectivityAsync() = coroutineScope.async {
        Log.d(TAG, "checkInternetConnectivityAsync")

        val queue = Volley.newRequestQueue(application)

        val stringRequest = StringRequest(
            VolleyRequest.Method.GET, "https://google.com/",
            {
                Log.d(TAG, "found internet!")

                hasInternetConnection = true
            },
            { error: VolleyError? ->
                // Handle errors here
                Log.d(TAG, "no internet :(")

                hasInternetConnection = false
                Log.e(TAG, error?.message ?: "Unknown error")
            })

        queue.add(stringRequest)
    }

    /**
     * Upserts an ExpenseRow into the spreadsheet
     */
    private fun sendExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)
        val row: Int // row number in sheet
        var isNewRow = false

         if (expenseRow.row == 0) {
             // if new row, call spreadsheet service to get an up-to-date row count
             isNewRow = true
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
            // Log.d(TAG, "inserting a new row")

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
            // Log.d(TAG, "updating row $row - $expenseRow")

            // update existing row
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

    /**
     * Wraps sendExpenseRowAsync by inserting expenseRow into DB and checking for connection to spreadsheet service
     */
    fun addExpenseRowAsync(expenseRow: ExpenseRow) = coroutineScope.async {
        Log.d(TAG, "addExpenseRowAsync")
        if (expenseRow.id == 0) { // already in DB - TODO: wait is this comment accurate? surely this means it's not already in DB, which is why the code block inserts it?
            val expenseRowId = expenseRowDao.insert(expenseRow)

            expenseRow.id = expenseRowId.toInt()
            Log.d(TAG, "inserted into db with id $expenseRowId")
        }

        checkInternetConnectivityAsync().await()
        if (!hasInternetConnection) {
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

    /**
     * Given a category, fetches the amount spent in that category so far this month
     */
    fun fetchCategorySpendingAsync(
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async {
        checkInternetConnectivityAsync().await()
        if (!hasInternetConnection) {
            Log.d(TAG, "fetchCategorySpendingAsync - no internet")
            return@async "no internet"
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "fetchCategorySpendingAsync - no spreadsheetservice")
            return@async "no spreadsheetservice"

            // TODO: this doesn't work
//            throw NotSignedInException()
        }

        val janColumnPref = sharedPreferences.getString("month_column", null)

        if (janColumnPref == null) {
            Log.d(TAG, "fetchCategorySpendingAsync - no January Column")
            return@async "no January Column"
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

        return@async "$spentSoFar"
    }

    /**
     * Fetches all expense rows from the sheet, then replaces local DB with fetched rows
     */
    fun fetchExpenseRowsFromSheetAsync() = coroutineScope.async {
        Log.d(TAG, "fetchExpenseRowsFromSheetAsync")

        checkInternetConnectivityAsync().await()
        if (!hasInternetConnection) {
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

    /**
     * Deletes expense row at given row number from spreadsheet and local DB
     */
    fun deleteRowAsync(row: Int) = coroutineScope.async {
        if (application.spreadsheetService == null) {
            throw NotSignedInException()
        }

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

        expenseRowDao.setDeleted(row)
    }

    /**
     * Fetches all spreadsheets on the account
     */
    fun fetchSpreadsheetsAsync(): Deferred<List<File>> = coroutineScope.async {
        checkInternetConnectivityAsync().await()
        if (!hasInternetConnection) {
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

    /**
     * Fetches all sheets within the given spreadsheet
     */
    fun fetchSheetsAsync(spreadsheetId: String): Deferred<List<Sheet>> = coroutineScope.async {
        checkInternetConnectivityAsync().await()
        if (!hasInternetConnection) {
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

    /**
     * Finds the column of the Overview sheet which holds the January column
     * For example, if January is in the 'C' column, the app will use this to infer that February is in the 'D' column, etc.
     */
    fun findMonthColumnsAsync() = coroutineScope.launch {
        Log.d(TAG, "findMonthColumnsAsync")

        checkInternetConnectivityAsync().await()
        if (!hasInternetConnection) {
            Log.d(TAG, "findMonthColumnsAsync - no internet")
            throw NoInternetException()
        }

        if (application.spreadsheetService == null) {
            Log.d(TAG, "findMonthColumnsAsync - no spreadsheetservice")

            // TODO: this doesn't work
            throw NotSignedInException()
        }

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
        // Log.d(TAG, "jan column: $januaryColumn")

        val preferenceEditor = sharedPreferences.edit()
        preferenceEditor.putString("month_column", januaryColumn.toString())
        preferenceEditor.apply()
    }
}