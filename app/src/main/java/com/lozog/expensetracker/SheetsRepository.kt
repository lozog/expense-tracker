package com.lozog.expensetracker

import android.util.Log
import com.google.api.services.sheets.v4.model.ValueRange
import com.lozog.expensetracker.util.ExpenseRow
import com.lozog.expensetracker.util.NotSignedInException
import com.lozog.expensetracker.util.SheetsInterface
import kotlinx.coroutines.*
import java.util.*


class SheetsRepository {

    /********** CONCURRENCY **********/
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + parentJob)

    companion object {
        private const val TAG = "SHEETS_REPOSITORY"

        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"

        private const val HISTORY_LENGTH = 10

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

    /********** GOOGLE SHEETS METHODS **********/

    fun addExpenseRowToSheetAsync(
        spreadsheetId: String,
        sheetName: String,
        expenseRow: ExpenseRow
    ) = coroutineScope.async {
        Log.d(TAG, "sheetsRepository.addExpenseRowToSheetAsync()")

            if (SheetsInterface.spreadsheetService == null) {
                throw NotSignedInException()
            }

            val nextRow = SheetsInterface.spreadsheetService!!.spreadsheets().values()
                .get(spreadsheetId, sheetName).execute().getValues().size + 1

            val expenseTotal =
                "=(\$D$nextRow - \$E$nextRow)*IF(NOT(ISBLANK(\$I$nextRow)), \$I$nextRow, 1)"

            expenseRow.expenseTotal = expenseTotal

            val rowData = mutableListOf(
                expenseRow.toList()
            )
            val requestBody = ValueRange()
            requestBody.setValues(rowData as List<List<String>>?)

            val request = SheetsInterface.spreadsheetService!!.spreadsheets().values()
                .append(spreadsheetId, sheetName, requestBody)
            request.valueInputOption = SHEETS_VALUE_INPUT_OPTION
            request.insertDataOption = SHEETS_INSERT_DATA_OPTION

            request.execute()
            Log.d(TAG, "sheetsRepository.addExpenseRowToSheetAsync() done")
    }

    fun getCategorySpendingAsync(
        spreadsheetId: String,
        overviewSheetName: String,
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async {
        val curMonthColumn = MONTH_COLUMNS[Calendar.getInstance().get(Calendar.MONTH)]

        val categoryCell = CATEGORY_ROW_MAP[expenseCategoryValue]

        if (categoryCell == null) {
            Log.e(TAG, "Category $expenseCategoryValue not found")
            throw Exception("Category $expenseCategoryValue not found")
        }

        val categorySpendingCell = "'$overviewSheetName'!$curMonthColumn$categoryCell"
        val data = SheetsInterface.spreadsheetService!!.spreadsheets().values()
            .get(spreadsheetId, categorySpendingCell).execute().getValues()

        val spentSoFar = data[0][0]

        return@async "$spentSoFar"
    }

    fun getRecentExpenseHistoryAsync(
        spreadsheetId: String,
        sheetName: String
    ): Deferred<List<ExpenseRow>> = coroutineScope.async {
        if (SheetsInterface.spreadsheetService == null) {
            throw NotSignedInException()
        }

        Log.d(TAG, "getRecentExpenseHistoryAsync")

        val res = SheetsInterface
            .spreadsheetService!!
            .spreadsheets()
            .values()
            .get(spreadsheetId, sheetName)
            .execute()
        val values = res.getValues()
        val recentHistory = values.takeLast(HISTORY_LENGTH) as List<ExpenseRow>

        Log.d(TAG, "first row: ${values[0]}")
        Log.d(TAG, "last row: ${values.last()}")

        return@async recentHistory
    }
}