package com.lozog.expensetracker.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Log

class ConnectivityHelper {
    companion object {
        private const val TAG = "CONNECTIVITY_HELPER"

        // this version of the code uses deprecated classes
        // but it appears to be more reliable (e.g. in the case of a VPN)
        fun isInternetConnected(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo

            Log.d(TAG, "isConnectedOrConnecting: ${activeNetwork?.isConnectedOrConnecting}")

            return activeNetwork?.isConnectedOrConnecting == true
        }

        // this version is more modern, but doesn't seem to work in all cases
        // e.g. when connected to a VPN, TRANSPORT_WIFI is not present
//        fun isInternetConnected(context: Context): Boolean {
//            val connectivityManager =
//                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//
//            val networkCapabilities = connectivityManager.activeNetwork ?: return false
//            val actNw =
//                connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
//
//            Log.d(TAG, "capabilities: $actNw")
//            Log.d(TAG, "wifi ${$actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
//            Log.d(TAG, "cellular ${$actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
//            Log.d(TAG, "ethernet ${$actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")
//
//            val result = when {
//                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
//                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
//                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
//                else -> false
//            }
//
//            return result
//        }
    }
}