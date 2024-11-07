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

class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EXPENSE_TRACKER NotificationReceiver"

    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            Log.e(TAG, "Context is null in onReceive")
            return
        }

        // Retrieve the notification text from the intent extras
        val notificationText = intent?.getStringExtra("message") ?: ""

        // Perform the desired action with the notification text
        Log.d(TAG, "Received: $notificationText")

        val regex = "(\\d){1,7}\\.\\d\\d".toRegex() // matches 12.00, 120.00, 1200.00, etc
        val matchResult = regex.find(notificationText) ?: return
        val match = matchResult.value

        showNotification(context, match)
    }

    private fun showNotification(context: Context, amount: String) {
        val channelId = "quick_add_expense"
        val channelName = "Expense Quick Add"

        // Create NotificationChannel (only for API 26+)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            description = "Notifications with controls to quickly add expenses detected"
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val uniqueId = System.currentTimeMillis().toInt()

        val popupIntent = Intent(context, PopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("amount", amount)
            putExtra("notification_id", uniqueId)
        }
        val popupPendingIntent = PendingIntent.getActivity(
            context, uniqueId, popupIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.baseline_attach_money_24)
            .setContentTitle("New Expense")
            .setContentText("$$amount")
            .setContentIntent(popupPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // Show the notification
        with(NotificationManagerCompat.from(context)) {
            notify(uniqueId, notificationBuilder.build())
        }
    }
}