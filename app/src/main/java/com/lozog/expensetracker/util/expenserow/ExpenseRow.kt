package com.lozog.expensetracker.util.expenserow

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.work.Data
import androidx.work.workDataOf

@Entity
data class ExpenseRow(
    @ColumnInfo(name = "expense_date") val expenseDate: String,
    @ColumnInfo(name = "expense_item") val expenseItem: String,
    @ColumnInfo(name = "expense_category_value") val expenseCategoryValue: String,
    @ColumnInfo(name = "expense_amount") val expenseAmount: String,
    @ColumnInfo(name = "expense_amount_others") val expenseAmountOthers: String,
    @ColumnInfo(name = "expense_total") var expenseTotal: String,
    @ColumnInfo(name = "expense_notes") val expenseNotes: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "exchange_rate") val exchangeRate: String,
    @ColumnInfo(name = "sync_status") var syncStatus: String = STATUS_DONE,
    @ColumnInfo(name = "row") var row: Int = 0,
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    ) {
    companion object {
        const val STATUS_DONE = "DONE"
        const val STATUS_DELETED = "DELETED"
        const val STATUS_PENDING = "PENDING"
    }

    constructor(workData: Data) : this(
        workData.getString("expenseDate")!!,
        workData.getString("expenseItem")!!,
        workData.getString("expenseCategory")!!,
        workData.getString("expenseAmount")!!,
        workData.getString("expenseAmountOthers")!!,
        workData.getString("expenseTotal")!!,
        workData.getString("expenseNotes")!!,
        workData.getString("currency")!!,
        workData.getString("exchangeRate")!!
    )

    constructor(input: List<String>) : this(
        input[0],
        input[1],
        input[2],
        input[3],
        input[4],
        input[5],
        input.getOrElse(6) { "" },
        input.getOrElse(7) { "" },
        input.getOrElse(8) { "" },
    )

    // return as a List, to be sent to the spreadsheet
    fun toList(): List<String> {
        return listOf(
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
    }

    fun toWorkData(): Data {
        return workDataOf(
            "expenseDate" to expenseDate,
            "expenseItem" to expenseItem,
            "expenseCategory" to expenseCategoryValue,
            "expenseAmount" to expenseAmount,
            "expenseAmountOthers" to expenseAmountOthers,
            "expenseTotal" to expenseTotal,
            "expenseNotes" to expenseNotes,
            "currency" to currency,
            "exchangeRate" to exchangeRate,
        )
    }
}