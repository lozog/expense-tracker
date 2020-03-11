package com.lozog.expensetracker

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface AddRowRequestDao {
    @Query("SELECT * FROM addRowRequest")
    fun getAll(): List<AddRowRequest>

    @Query("SELECT * FROM addRowRequest WHERE id IN (:addRowRequestIds)")
    fun loadAllByIds(addRowRequestIds: IntArray): List<AddRowRequest>

    @Insert
    fun insert(addRowRequest: AddRowRequest)

    @Delete
    fun delete(addRowRequest: AddRowRequest)
}
