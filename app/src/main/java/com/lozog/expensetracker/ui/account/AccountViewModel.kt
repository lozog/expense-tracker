package com.lozog.expensetracker.ui.account

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AccountViewModel : ViewModel() {
    val signInStatus = MutableLiveData<String>()

    companion object {
        private const val TAG = "EXPENSE_TRACKER AccountViewModel"
    }

    fun setSignInStatus(newSignInStatus: String) {
//        Log.d(TAG, "setting text: $newSignInStatus")
        signInStatus.value = newSignInStatus
    }

}