package com.lozog.expensetracker

import android.util.Log
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*


class SheetsRepository {

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

    /********** GOOGLE SHEETS METHODS **********/

    suspend fun addExpenseRowToSheetAsync(
        spreadsheetId: String,
        sheetName: String,
        expenseDate: String,
        expenseItem: String,
        expenseCategoryValue: String,
        expenseAmount: String,
        expenseAmountOthers: String,
        expenseNotes: String,
        currency: String,
        exchangeRate: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "in sheetsRepository.addExpenseRowToSheetAsync")

            if (GoogleSheetsInterface.spreadsheetService == null) {
                throw MainActivity.NotSignedInException()
            }

            val nextRow = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values()
                .get(spreadsheetId, sheetName).execute().getValues().size + 1
            Log.d(TAG, "nextRow")

            val expenseTotal =
                "=(\$D$nextRow - \$E$nextRow)*IF(NOT(ISBLANK(\$I$nextRow)), \$I$nextRow, 1)"
            Log.d(TAG, "expenseTotal")

            val rowData = mutableListOf(
                mutableListOf(
                    expenseDate,
                    expenseItem,
                    expenseCategoryValue,
                    expenseAmount,
                    expenseAmountOthers,
                    expenseTotal,
                    expenseNotes,
                    currency,
                    exchangeRate
                )
            )
            val requestBody = ValueRange()
            requestBody.setValues(rowData as List<MutableList<String>>?)

            val request = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values()
                .append(spreadsheetId, sheetName, requestBody)
            request.valueInputOption = SHEETS_VALUE_INPUT_OPTION
            request.insertDataOption = SHEETS_INSERT_DATA_OPTION

            Log.d(TAG, "excecuting")
            request.execute()
            Log.d(TAG, "doneexcecuting")
    }

//    fun getCategorySpendingAsync(
//        spreadsheetId: String,
//        expenseCategoryValue: String
//    ): String? {
//        Log.d(TAG, "getCategorySpending")
//
//        val curMonthColumn = MONTH_COLUMNS[Calendar.getInstance().get(Calendar.MONTH)]
//
//        val categoryCell = CATEGORY_ROW_MAP[expenseCategoryValue]
//
//        if (categoryCell == null) {
//            Log.e(TAG, "Category $expenseCategoryValue not found")
//            // TODO: WHY CANT I ASYNC
//            return@async null
//        }
//
//        val overviewSheetName = "Overview" // TODO: move to user pref. or dynamically read sheet
//
//        val categorySpendingCell = "'$overviewSheetName'!$curMonthColumn$categoryCell"
//        val data = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values()
//            .get(spreadsheetId, categorySpendingCell).execute().getValues()
//
//        val spentSoFar = data[0][0].toString()
//
////        withContext(Dispatchers.Main) {
////            setStatusText("$spentSoFar spent so far in $expenseCategoryValue")
////        }
//
//        return@async spentSoFar
//    }
}