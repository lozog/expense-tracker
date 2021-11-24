package com.lozog.expensetracker.util.expenserow

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRow")
    fun getAll(): Flow<List<ExpenseRow>>

    @Query("SELECT * FROM expenseRow WHERE id IN (:expenseRowIds)")
    fun loadAllByIds(expenseRowIds: IntArray): List<ExpenseRow>

    @Insert
    fun insert(expenseRow: ExpenseRow)

    @Delete
    fun delete(expenseRow: ExpenseRow)

    @Query("DELETE FROM expenseRow")
    fun deleteAll()
}