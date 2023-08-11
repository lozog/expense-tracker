package com.lozog.expensetracker.util.expenserow

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.work.Data
import androidx.work.workDataOf

@Entity
data class ExpenseRow(
    @ColumnInfo(name = "expense_date") var expenseDate: String,
    @ColumnInfo(name = "expense_item") var expenseItem: String,
    @ColumnInfo(name = "expense_category_value") var expenseCategoryValue: String,
    @ColumnInfo(name = "expense_amount") var expenseAmount: String,
    @ColumnInfo(name = "expense_amount_others") var expenseAmountOthers: String,
    @ColumnInfo(name = "expense_total") var expenseTotal: String,
    @ColumnInfo(name = "expense_notes") var expenseNotes: String,
    @ColumnInfo(name = "currency") var currency: String,
    @ColumnInfo(name = "exchange_rate") var exchangeRate: String,
    @ColumnInfo(name = "sync_status") var syncStatus: String = STATUS_DONE,
    @ColumnInfo(name = "row") var row: Int = 0,
    @PrimaryKey(autoGenerate = true) var id: Int = 0,
    ) {
    companion object {
        const val STATUS_DONE = "DONE"
        const val STATUS_DELETED = "DELETED"
        const val STATUS_PENDING = "PENDING"
    }

    constructor(input: List<Any>) : this(
        input.getOrElse(0) { "" }.toString(),
        input.getOrElse(1) { "" }.toString(),
        input.getOrElse(2) { "" }.toString(),
        input.getOrElse(3) { "" }.toString(),
        input.getOrElse(4) { "" }.toString(),
        input.getOrElse(5) { "" }.toString(),
        input.getOrElse(6) { "" }.toString(),
        input.getOrElse(7) { "" }.toString(),
        input.getOrElse(8) { "" }.toString(),
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
}