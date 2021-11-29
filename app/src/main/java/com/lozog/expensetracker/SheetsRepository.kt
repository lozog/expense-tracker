package com.lozog.expensetracker

import android.content.SharedPreferences
import android.util.Log
import com.google.api.services.sheets.v4.model.*
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.NotSignedInException
import com.lozog.expensetracker.util.SheetsInterface
import com.lozog.expensetracker.util.expenserow.ExpenseRowDao
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.util.*


class SheetsRepository(private val expenseRowDao: ExpenseRowDao) {
    lateinit var recentHistory: Flow<List<ExpenseRow>>
    private lateinit var sharedPreferences: SharedPreferences

    /********** CONCURRENCY **********/
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    companion object {
        private const val TAG = "SHEETS_REPOSITORY"

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

    fun addExpenseRowToSheetAsync(
        expenseRow: ExpenseRow
    ) = coroutineScope.async {
        Log.d(TAG, "sheetsRepository.addExpenseRowToSheetAsync()")
        val expenseRowId = expenseRowDao.insert(expenseRow)
        expenseRow.id = expenseRowId.toInt()

        if (SheetsInterface.spreadsheetService == null) {
            throw NotSignedInException()
        }

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

        val nextRow = SheetsInterface.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, sheetName)
            .execute()
            .getValues()
            .size + 1

        val expenseTotal = // TODO: make this a pref string
            "=(\$D$nextRow - \$E$nextRow)*IF(NOT(ISBLANK(\$I$nextRow)), \$I$nextRow, 1)"
        expenseRow.expenseTotal = expenseTotal

        val rowData = listOf(
            expenseRow.toList()
        )
        val requestBody = ValueRange()
        requestBody.setValues(rowData)
        val request = SheetsInterface.spreadsheetService!!
            .spreadsheets()
            .values()
            .append(spreadsheetId, sheetName, requestBody)
        request.valueInputOption = SHEETS_VALUE_INPUT_OPTION
        request.insertDataOption = SHEETS_INSERT_DATA_OPTION
        request.execute()

        expenseRow.row = nextRow
        expenseRow.syncStatus = "DONE"
        expenseRowDao.update(expenseRow)

        Log.d(TAG, "sheetsRepository.addExpenseRowToSheetAsync() done")
    }

    fun getCategorySpendingAsync(
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async {
        val curMonthColumn = MONTH_COLUMNS[Calendar.getInstance().get(Calendar.MONTH)]
        val categoryCell = CATEGORY_ROW_MAP[expenseCategoryValue]
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val overviewSheetName = sharedPreferences.getString("overview_sheet_name", null)

        if (categoryCell == null) {
            Log.e(TAG, "Category $expenseCategoryValue not found")
            throw Exception("Category $expenseCategoryValue not found")
        }

        val categorySpendingCell = "'$overviewSheetName'!$curMonthColumn$categoryCell"
        val data = SheetsInterface.spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, categorySpendingCell)
            .execute()
            .getValues()

        val spentSoFar = data[0][0]

        return@async "$spentSoFar"
    }

    fun getRecentExpenseHistoryAsync() = coroutineScope.async {
        Log.d(TAG, "getRecentExpenseHistoryAsync")

        if (SheetsInterface.spreadsheetService == null) {
            throw NotSignedInException()
        }

        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)
//        val historyLength = sharedPreferences.getString("history_length", "25")?.toInt() ?: 25

        val values = SheetsInterface
            .spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, sheetName)
            .execute()
            .getValues()

//        expenseRowDao.deleteAllDone()
        val allExpensesFromSheet = values.map{ value -> ExpenseRow(value as List<String>) }
        allExpensesFromSheet.forEachIndexed {i, expenseRow ->
            // go through each one. make sure it's in the DB
            expenseRow.row = i + 1
            expenseRow.syncStatus = "DONE"
        }
        expenseRowDao.deleteAllDoneAndInsertMany(allExpensesFromSheet)

    }

    fun deleteRowAsync(row: Int) = coroutineScope.async {
        if (SheetsInterface.spreadsheetService == null) {
            throw NotSignedInException()
        }

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

        SheetsInterface.spreadsheetService!!
            .spreadsheets()
            .batchUpdate(spreadsheetId, updateRequest)
            .execute()
    }
}