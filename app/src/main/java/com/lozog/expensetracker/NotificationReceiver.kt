package com.lozog.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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

        showNotification(context, "Amount: $$match")
    }

    private fun showNotification(context: Context, content: String) {
        val channelId = "default_channel_id"
        val channelName = "Default Channel"

        // Create NotificationChannel (only for API 26+)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = "Channel description"
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val popupIntent = Intent(context, PopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val popupPendingIntent = PendingIntent.getActivity(context, 0, popupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your app's icon
            .setContentTitle("New Expense")
            .setContentText(content)
            .setContentIntent(popupPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            notify(1, notificationBuilder.build()) // Notification ID should be unique if you want multiple notifications
        }
    }
}