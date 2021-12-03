package com.lozog.expensetracker.util.expenserow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ExpenseRow::class], version = 1)
abstract class ExpenseRowDB : RoomDatabase() {
    abstract fun expenseRowDao(): ExpenseRowDao

    companion object {
        // Singleton prevents multiple instances of database opening at the
        // same time.
        @Volatile
        private var INSTANCE: ExpenseRowDB? = null

        fun getDatabase(context: Context): ExpenseRowDB {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseRowDB::class.java,
                    "expense_row_database"
                )
//                    .fallbackToDestructiveMigration() // DANGER: don't uncomment this unless you want to clear all data
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

}