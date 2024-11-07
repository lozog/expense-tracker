package com.lozog.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val sheetsRepository = (context?.applicationContext as ExpenseTrackerApplication).sheetsRepository

        // Retrieve the notification text from the intent extras
        val notificationText = intent?.getStringExtra("message") ?: ""

        // Perform the desired action with the notification text
        Log.d("EXPENSE_TRACKER NotificationReceiver", "Received: $notificationText")

        val regex = "(\\d){1,7}\\.\\d\\d".toRegex() // matches 12.00, 120.00, 1200.00, etc
        val matchResult = regex.find(notificationText) ?: return
        val match = matchResult.value

        val expenseRow = ExpenseRow(
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            "TODO",
            "Miscellaneous",
            match,
            "",
            "",
            "",
            "",
            "",
            ExpenseRow.STATUS_PENDING
        )
        sheetsRepository.addExpenseRowAsync(expenseRow)
    }
}