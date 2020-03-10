package com.lozog.expensetracker

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = arrayOf(AddRowRequest::class), version = 1)
abstract class AddRowRequestDB : RoomDatabase() {
    abstract fun addRowRequestDao(): AddRowRequestDao
}
