package com.lozog.expensetracker.util.expenserow

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRow WHERE sync_status=(:syncStatusDone) ORDER BY `row` DESC LIMIT (:historyLength)")
    fun getExpenseRows(historyLength: Int, syncStatusDone: String = ExpenseRow.STATUS_DONE): LiveData<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow WHERE sync_status=(:syncStatusPending) ORDER BY `id` DESC")
    fun getPendingExpenseRowsLiveData(syncStatusPending: String = ExpenseRow.STATUS_PENDING): LiveData<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow WHERE sync_status=(:syncStatusPending) ORDER BY `row` DESC")
    suspend fun getPendingExpenseRows(syncStatusPending: String = ExpenseRow.STATUS_PENDING): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `id`=(:id)")
    fun getById(id: Int): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `submission_id`=(:submissionId) LIMIT 1")
    fun getBySubmissionId(submissionId: String): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `expense_category_value`=(:category)")
    fun getExpensesByCategory(category: String): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `sync_status`!=(:syncStatusDeleted)")
    suspend fun getAllExpenseRows(syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): List<ExpenseRow>

    @Update(onConflict=OnConflictStrategy.REPLACE)
    fun update(expenseRow: ExpenseRow): Int

    @Query("UPDATE expenseRow SET sync_status=(:syncStatusDeleted) WHERE `id`=(:id)")
    fun setDeletedById(id: Int, syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): Int

    @Query("UPDATE expenseRow SET sync_status=(:syncStatusDeleted) WHERE `row`=(:row)")
    fun setDeletedByRow(row: Int, syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): Int

    @Query("UPDATE expenseRow SET sync_status=(:syncStatusDeleted) WHERE `sync_status`=(:syncStatusDone)")
    fun setAllDoneToDeleted(
        syncStatusDone: String = ExpenseRow.STATUS_DONE,
        syncStatusDeleted: String = ExpenseRow.STATUS_DELETED
    ): Int

    @Query("""
        UPDATE expenseRow SET
            expense_date = :expenseDate,
            expense_item = :expenseItem,
            expense_category_value = :expenseCategoryValue,
            expense_amount = :expenseAmount,
            expense_amount_others = :expenseAmountOthers,
            expense_total = :expenseTotal,
            expense_notes = :expenseNotes,
            currency = :currency,
            exchange_rate = :exchangeRate,
            sync_status = :syncStatus,
            `row` = :row
        WHERE submission_id = :submissionId
    """)
    fun updateBySubmissionId(
        expenseDate: String,
        expenseItem: String,
        expenseCategoryValue: String,
        expenseAmount: String,
        expenseAmountOthers: String,
        expenseTotal: String,
        expenseNotes: String,
        currency: String,
        exchangeRate: String,
        syncStatus: String,
        row: Int,
        submissionId: String,
    ): Int

    @Insert
    fun insert(expenseRow: ExpenseRow): Long

    @Insert
    fun insertMany(expenseRows: List<ExpenseRow>)

    @Delete
    fun delete(expenseRow: ExpenseRow)

    @Query("DELETE FROM expenseRow WHERE sync_status=(:syncStatusDone) OR sync_status=(:syncStatusDeleted)")
    fun deleteAllDone(
        syncStatusDone: String = ExpenseRow.STATUS_DONE,
        syncStatusDeleted: String = ExpenseRow.STATUS_DELETED
    )

    @Query("DELETE FROM expenseRow WHERE sync_status=(:syncStatusDeleted)")
    fun removeDeleted(
        syncStatusDeleted: String = ExpenseRow.STATUS_DELETED
    )

    @Transaction
    fun deleteAllDoneAndInsertMany(expenseRows: List<ExpenseRow>) {
        deleteAllDone()
        insertMany(expenseRows)
    }

    @Query("DELETE FROM expenseRow")
    fun deleteAll()
}