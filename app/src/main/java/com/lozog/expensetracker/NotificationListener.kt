package com.lozog.expensetracker

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationListener : NotificationListenerService() {
    private lateinit var sheetsRepository: SheetsRepository

    companion object {
        private const val TAG = "EXPENSE_TRACKER NOTIFICATION_LISTENER"
    }

    override fun onCreate() {
        super.onCreate()
        sheetsRepository = (applicationContext as ExpenseTrackerApplication).sheetsRepository
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn);
        Log.d(TAG, "onNotificationPosted")
        if (sbn != null) {
            val packageName = sbn.packageName
            Log.d(TAG, "from $packageName")

            if (packageName == "ca.pcfinancial.bank" || packageName == this.packageName || packageName == "com.arlosoft.macrodroid") {

                val extras: Bundle = sbn.notification.extras
                val notificationText = extras["android.text"] as String
                Log.d(TAG, notificationText)

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
    }
}