package com.lozog.expensetracker.util.expenserow

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.work.Data
import androidx.work.workDataOf
import java.util.UUID

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
    @ColumnInfo(name = "submission_id") var submissionId: String = UUID.randomUUID().toString(),
    ) {
    companion object {
        const val STATUS_DONE = "DONE" // TODO: maybe a better word would be "synced"
        const val STATUS_DELETED = "DELETED"
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
    }

    constructor(input: List<Any>) : this(
        expenseDate=input.getOrElse(0) { "" }.toString(),
        expenseItem=input.getOrElse(1) { "" }.toString(),
        expenseCategoryValue=input.getOrElse(2) { "" }.toString(),
        expenseAmount=input.getOrElse(3) { "" }.toString(),
        expenseAmountOthers=input.getOrElse(4) { "" }.toString(),
        expenseTotal=input.getOrElse(5) { "" }.toString(),
        expenseNotes=input.getOrElse(6) { "" }.toString(),
        currency=input.getOrElse(7) { "" }.toString(),
        exchangeRate=input.getOrElse(8) { "" }.toString(),
        submissionId=input.getOrElse(9) { "" }.toString(),
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
            exchangeRate,
            submissionId,
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExpenseRow) return false

        return expenseDate == other.expenseDate &&
                expenseItem == other.expenseItem &&
                expenseCategoryValue == other.expenseCategoryValue &&
                expenseAmount == other.expenseAmount &&
                expenseAmountOthers == other.expenseAmountOthers &&
                expenseTotal == other.expenseTotal &&
                expenseNotes == other.expenseNotes &&
                currency == other.currency &&
                exchangeRate == other.exchangeRate &&
                submissionId == other.submissionId
    }
}