package com.lozog.expensetracker.util

import android.content.Context
import android.net.*
import android.util.Log
import com.lozog.expensetracker.ExpenseTrackerApplication

class NetworkMonitor
constructor(private val application: ExpenseTrackerApplication) {

    companion object {
        private const val TAG = "EXPENSE_TRACKER NETWORK_MONITOR"
    }

    fun startNetworkCallback() {
        val cm: ConnectivityManager =
            application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        cm.allNetworks.forEach{
            val info = cm.getNetworkCapabilities(it)
            Log.d(TAG, "found network $it with $info")
        }

        Log.d(TAG, "active network is ${cm.activeNetwork}")

        val builder: NetworkRequest.Builder = NetworkRequest
            .Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        cm.registerNetworkCallback(
            builder.build(), networkCallback
        )
    }

    fun stopNetworkCallback() {
        val cm: ConnectivityManager =
            application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.unregisterNetworkCallback(ConnectivityManager.NetworkCallback())
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val cm: ConnectivityManager =
                application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val isVPN = cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?: false

            if (!isVPN) {
                Log.d(TAG, "non-vpn network now available")
                try {
                    application.sheetsRepository.sendPendingRowsToSheetAsync()
                } catch (e: Exception) {
                    Log.d(TAG, e.toString())
                    throw e
                }
            }
        }
    }
}