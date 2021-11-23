package com.lozog.expensetracker

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.lozog.expensetracker.util.expenserow.ExpenseRow
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
    val recentHistory = MutableLiveData<List<ExpenseRow>>()

    fun setStatusText(newSignInStatus: String) {
        statusText.value = newSignInStatus
    }

    fun setRecentHistory(history: List<ExpenseRow>) {
        recentHistory.value = history
    }

    fun setStatus(newStatus: SheetsStatus) {
        status.value = newStatus
    }

    fun resetView() {
        status.value = SheetsStatus.DONE
    }

    fun getRecentExpenseHistory(
        spreadsheetId: String,
        sheetName: String
    ) {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            var recentHistory: List<ExpenseRow>?

            try {
                Log.d(TAG, "calling sheetsRepository.getRecentExpenseHistoryAsync")
                val res = sheetsRepository.getRecentExpenseHistoryAsync(
                    spreadsheetId,
                    sheetName
                ).await()
                recentHistory = res
                Log.d(TAG, "got history: $recentHistory")
            } catch (e: Exception) {
                recentHistory = null
                Log.e(TAG, e.toString())
            }

            withContext(Dispatchers.Main) {
                if (recentHistory != null) {
                    setRecentHistory(recentHistory)
                }
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun addExpenseRowToSheetAsync(
        spreadsheetId: String,
        sheetName: String,
        overviewSheetName: String,
        expenseRow: ExpenseRow
    ) {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            var statusText: String

            try {
                sheetsRepository.addExpenseRowToSheetAsync(
                    spreadsheetId,
                    sheetName,
                    expenseRow
                ).await()

                val spentSoFar = sheetsRepository
                    .getCategorySpendingAsync(spreadsheetId, overviewSheetName, expenseRow.expenseCategoryValue)
                    .await()
//                statusText = getString(R.string.status_spent_so_far, spentSoFar, expenseCategoryValue)
                statusText = "$spentSoFar spent so far in ${expenseRow.expenseCategoryValue}"
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