package com.lozog.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Retrieve the notification text from the intent extras
        val notificationText = intent?.getStringExtra("message")

        // Perform the desired action with the notification text
        Log.d("EXPENSE_TRACKER NotificationReceiver", "Received: $notificationText")
    }
}