package com.lozog.expensetracker

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.lozog.expensetracker.util.SheetsStatus
import com.lozog.expensetracker.util.NotSignedInException
import kotlinx.coroutines.*
import java.io.IOException


class SheetsViewModel : ViewModel() {
    companion object {
        private const val TAG = "SHEETS_VIEW_MODEL"
    }

    private val sheetsRepository = SheetsRepository()

    val status = MutableLiveData<SheetsStatus>()
    val statusText = MutableLiveData<String>()

    fun setStatusText(newSignInStatus: String) {
        statusText.value = newSignInStatus
    }

    fun setStatus(newStatus: SheetsStatus) {
        status.value = newStatus
    }

    fun resetView() {
        status.value = SheetsStatus.DONE
    }

    fun addExpenseRowToSheetAsync(
        spreadsheetId: String,
        sheetName: String,
        expenseDate: String,
        expenseItem: String,
        expenseCategoryValue: String,
        expenseAmount: String,
        expenseAmountOthers: String,
        expenseNotes: String,
        currency: String,
        exchangeRate: String
    ) {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            var statusText: String

            try {
                sheetsRepository.addExpenseRowToSheetAsync(
                    spreadsheetId,
                    sheetName,
                    expenseDate,
                    expenseItem,
                    expenseCategoryValue,
                    expenseAmount,
                    expenseAmountOthers,
                    expenseNotes,
                    currency,
                    exchangeRate
                )

                val spentSoFar = sheetsRepository
                    .getCategorySpendingAsync(spreadsheetId, expenseCategoryValue)
                    .await()
//                statusText = getString(R.string.status_spent_so_far, spentSoFar, expenseCategoryValue)
                statusText = "$spentSoFar spent so far in $expenseCategoryValue"
            } catch (e: UserRecoverableAuthIOException) {
//                Log.e(TAG, getString(R.string.status_need_permission))
//                mainActivity.startForRequestAuthorizationResult.launch(e.intent)
                statusText = "need google permission"
            } catch (e: IOException) {
                Log.e(TAG, e.toString())
//                statusText = getString(R.string.status_google_error)
                statusText = "google error"
            } catch (e: NotSignedInException) {
//                Log.d(TAG, getString(R.string.status_not_signed_in))
                statusText = "not signed in"
            } catch (e: Exception) {
                statusText = "something went wrong"
            }

            withContext(Dispatchers.Main) {
                setStatusText(statusText)
                setStatus(SheetsStatus.DONE)
            }
        }
    }
}