package com.lozog.expensetracker.util.expenserow

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRow WHERE sync_status!=(:syncStatusDeleted) ORDER BY `row` DESC LIMIT (:historyLength)")
    fun getN(historyLength: Int, syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): LiveData<List<ExpenseRow>>
    // TODO: rename getHistory

    @Query("SELECT * FROM expenseRow WHERE sync_status=(:syncStatusPending) ORDER BY `row` DESC")
    fun getAllPending(syncStatusPending: String = ExpenseRow.STATUS_PENDING): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `row`=(:row)")
    fun getByRow(row: Int): List<ExpenseRow>

    @Update(onConflict=OnConflictStrategy.REPLACE)
    fun update(expenseRow: ExpenseRow): Int

    @Query("UPDATE expenseRow SET sync_status=(:syncStatusDeleted) WHERE `row`=(:row)")
    fun setDeleted(row: Int, syncStatusDeleted: String = ExpenseRow.STATUS_DELETED): Int

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