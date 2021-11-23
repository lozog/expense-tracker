package com.lozog.expensetracker.util.expenserow

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseRowDao {
    @Query("SELECT * FROM expenseRowEntity")
    fun getAll(): Flow<List<ExpenseRowEntity>>

    @Query("SELECT * FROM expenseRowEntity WHERE id IN (:expenseRowEntityIds)")
    fun loadAllByIds(expenseRowEntityIds: IntArray): List<ExpenseRowEntity>

    @Insert
    fun insert(expenseRowEntity: ExpenseRowEntity)

    @Delete
    fun delete(expenseRowEntity: ExpenseRowEntity)

    @Query("DELETE FROM expenseRowEntity")
    fun deleteAll()
}