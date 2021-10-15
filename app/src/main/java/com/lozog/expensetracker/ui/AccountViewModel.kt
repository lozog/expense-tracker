package com.lozog.expensetracker.ui

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AccountViewModel : ViewModel() {
//    val text = MutableLiveData<String>()

    companion object {
        private const val TAG = "ACCOUNT_VIEW_MODEL"
    }

    fun setText(newText: String) {
        Log.d(TAG, "setting text: $newText")
        text.value = newText
    }

    val text = MutableLiveData<String>()
}