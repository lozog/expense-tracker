package com.lozog.expensetracker.util

import androidx.work.Data
import androidx.work.workDataOf

data class ExpenseRow(
    val expenseDate: String,
    val expenseItem: String,
    val expenseCategoryValue: String,
    val expenseAmount: String,
    val expenseAmountOthers: String,
    val expenseNotes: String,
    val currency: String,
    val exchangeRate: String,
    var expenseTotal: String = "",
    var row: String? = null
) {
    constructor(workData: Data) : this(
        workData.getString("expenseDate")!!,
        workData.getString("expenseItem")!!,
        workData.getString("expenseCategory")!!,
        workData.getString("expenseAmount")!!,
        workData.getString("expenseAmountOthers")!!,
        workData.getString("expenseNotes")!!,
        workData.getString("currency")!!,
        workData.getString("exchangeRate")!!
    )

    // return as a List, to be sent to the spreadsheet
    fun toList(): List<String> {
        return listOf(
            expenseDate,
            expenseItem,
            expenseCategoryValue,
            expenseAmount,
            expenseAmountOthers,
            expenseTotal, // order matters because this is left to right in the sheet, so expenseTotal needs to go 6th (col F)
            expenseNotes,
            currency,
            exchangeRate
        )
    }

    fun toWorkData(spreadsheetId: String, dataSheetName: String): Data {
        return workDataOf(
            "spreadsheetId" to spreadsheetId,
            "sheetName" to dataSheetName,
            "expenseDate" to expenseDate,
            "expenseItem" to expenseItem,
            "expenseCategory" to expenseCategoryValue,
            "expenseAmount" to expenseAmount,
            "expenseAmountOthers" to expenseAmountOthers,
            "expenseNotes" to expenseNotes,
            "currency" to currency,
            "exchangeRate" to exchangeRate,
        )
    }
}