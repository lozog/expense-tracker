package com.lozog.expensetracker.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.util.Log

class ConnectivityHelper {
    companion object {
        private const val TAG = "CONNECTIVITY_HELPER"
        private const val MODE_DEPRECATED = 0
        private const val MODE_TRANSPORT = 1
        private const val MODE_CAPABILITIES = 2
        private const val INTERNET_CHECK_MODE = MODE_DEPRECATED

        fun isInternetConnected(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            return when (INTERNET_CHECK_MODE) {
                MODE_DEPRECATED -> isInternetConnectedWithActiveNetworkInfo(connectivityManager)
                MODE_TRANSPORT -> isInternetConnectedWithTransport(connectivityManager)
                MODE_CAPABILITIES -> isInternetConnectedWithCapabilities(connectivityManager)
                else -> false
            }
        }

        // this version of the code uses deprecated classes
        // but it appears to be more reliable (e.g. in the case of a VPN)
        private fun isInternetConnectedWithActiveNetworkInfo(connectivityManager: ConnectivityManager): Boolean {
            return connectivityManager.activeNetworkInfo?.isConnectedOrConnecting ?: false
        }

        // this version is more modern, but doesn't seem to work in all cases
        // e.g. when connected to a VPN, TRANSPORT_WIFI is not present on my phone
        private fun isInternetConnectedWithTransport(connectivityManager: ConnectivityManager): Boolean {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            Log.d(TAG, "capabilities: $networkCapabilities")
            Log.d(TAG, "wifi ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
            Log.d(TAG, "cellular ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
            Log.d(TAG, "ethernet ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)}")

            val result = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }

            return result
        }

        // this version is more modern, but doesn't seem to work in all cases
        // e.g. when connected to a VPN, TRANSPORT_WIFI is not present
        private fun isInternetConnectedWithCapabilities(connectivityManager: ConnectivityManager): Boolean {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            Log.d(TAG, "capabilities: $networkCapabilities")
            Log.d(TAG, "internet ${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
            Log.d(TAG, "validated ${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")

            val result = when {
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> true
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> true
                else -> false
            }

            return result
        }
    }
}