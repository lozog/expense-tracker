package com.lozog.expensetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.util.Log

class NetworkReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // TODO: move this into a util
        val conn = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo: NetworkInfo? = conn.activeNetworkInfo
        val isInternetConnected = networkInfo?.isConnectedOrConnecting == true

        if (isInternetConnected) {
            // broadcast that internet is now available
            Log.d("BROADCAST_RECEIVER", "internet connection detected")

            if (context is MainActivity) {
                context.sendQueuedRequests()
            }
        }
    }
}
