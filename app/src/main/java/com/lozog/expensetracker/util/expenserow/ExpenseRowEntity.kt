package com.lozog.expensetracker.util.expenserow

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ExpenseRowEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "spreadsheet_id") val spreadsheetId: String,
    @ColumnInfo(name = "sheet_name") val sheetName: String,
    @ColumnInfo(name = "expense_date") val expenseDate: String,
    @ColumnInfo(name = "expense_item") val expenseItem: String,
    @ColumnInfo(name = "expense_category_value") val expenseCategoryValue: String,
    @ColumnInfo(name = "expense_amount") val expenseAmount: String,
    @ColumnInfo(name = "expense_amount_others") val expenseAmountOthers: String,
    @ColumnInfo(name = "expense_total") val expenseTotal: String,
    @ColumnInfo(name = "expense_notes") val expenseNotes: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "exchange_rate") val exchangeRate: String,
    @ColumnInfo(name = "row") val row: Int
)