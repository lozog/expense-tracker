package com.lozog.expensetracker.util.expenserow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRow WHERE sync_status!='DELETED' ORDER BY `row` DESC LIMIT (:historyLength)")
    fun getN(historyLength: Int): Flow<List<ExpenseRow>>
    // TODO: rename getHistory

    @Query("SELECT * FROM expenseRow WHERE sync_status='PENDING' ORDER BY `row` DESC")
    fun getAllPending(): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE `row`=(:row)")
    fun getByRow(row: Int): List<ExpenseRow>

    @Update(onConflict=OnConflictStrategy.REPLACE)
    fun update(expenseRow: ExpenseRow): Int

    @Query("UPDATE expenseRow SET sync_status='DELETED' WHERE `row`=(:row)")
    fun setDeleted(row: Int): Int

    @Insert
    fun insert(expenseRow: ExpenseRow): Long

    @Insert
    fun insertMany(expenseRows: List<ExpenseRow>)

    @Delete
    fun delete(expenseRow: ExpenseRow)

    @Query("DELETE FROM expenseRow WHERE sync_status='DONE' OR sync_status='DELETED'")
    fun deleteAllDone()

    @Transaction
    fun deleteAllDoneAndInsertMany(expenseRows: List<ExpenseRow>) {
        deleteAllDone()
        insertMany(expenseRows)
    }

    @Query("DELETE FROM expenseRow")
    fun deleteAll()
}