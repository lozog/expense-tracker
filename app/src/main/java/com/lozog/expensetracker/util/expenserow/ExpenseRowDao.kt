package com.lozog.expensetracker.util.expenserow

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRow ORDER BY `row` DESC")
    fun getAll(): Flow<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow ORDER BY `row` DESC")
    fun getAllStatic(): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow ORDER BY `row` DESC LIMIT (:historyLength)")
    fun getN(historyLength: Int): Flow<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow WHERE `row`=(:row)")
    fun getByRow(row: Int): List<ExpenseRow>

    @Query("SELECT * FROM expenseRow WHERE id IN (:expenseRowIds)")
    fun loadAllByIds(expenseRowIds: IntArray): List<ExpenseRow>

    @Update(onConflict=OnConflictStrategy.REPLACE)
    fun update(expenseRow: ExpenseRow): Int

    @Insert
    fun insert(expenseRow: ExpenseRow): Long

    @Insert
    fun insertMany(expenseRows: List<ExpenseRow>)

    @Delete
    fun delete(expenseRow: ExpenseRow)

    @Query("DELETE FROM expenseRow WHERE sync_status='DONE'")
    fun deleteAllDone()

    @Transaction
    fun deleteAllDoneAndInsertMany(expenseRows: List<ExpenseRow>) {
        deleteAllDone()
        insertMany(expenseRows)
    }

    @Query("DELETE FROM expenseRow")
    fun deleteAll()
}