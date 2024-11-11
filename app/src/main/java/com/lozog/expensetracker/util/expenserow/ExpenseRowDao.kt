package com.lozog.expensetracker.util.expenserow

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRow WHERE sync_status=(:syncStatusDone) ORDER BY `row` DESC LIMIT (:historyLength)")
    fun getExpenseRows(historyLength: Int, syncStatusDone: String = ExpenseRow.STATUS_DONE): LiveData<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow WHERE sync_status=(:syncStatusPending)")
    fun getAllPendingExpenseRows(syncStatusPending: String = ExpenseRow.STATUS_PENDING): LiveData<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow WHERE `id`=(:id)")
    fun getById(id: Int): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `row`=(:row)")
    fun getByRow(row: Int): List<ExpenseRow>

    @Update(onConflict=OnConflictStrategy.REPLACE)
    fun update(expenseRow: ExpenseRow): Int

    @Query("UPDATE expenseRow SET sync_status=(:syncStatusDeleted) WHERE `id`=(:id)")
    fun setDeletedById(id: Int, syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): Int

    @Query("UPDATE expenseRow SET sync_status=(:syncStatusDeleted) WHERE `row`=(:row)")
    fun setDeletedByRow(row: Int, syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): Int

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

    @Transaction
    fun deleteAllDoneAndInsertMany(expenseRows: List<ExpenseRow>) {
        deleteAllDone()
        insertMany(expenseRows)
    }

    @Query("DELETE FROM expenseRow")
    fun deleteAll()
}