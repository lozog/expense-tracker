package com.lozog.expensetracker.util.expenserow

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add the new column with a default value
        database.execSQL("ALTER TABLE expenseRow ADD COLUMN submission_id TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [ExpenseRow::class], version = 2)
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }

}