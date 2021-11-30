package com.lozog.expensetracker

import android.util.Log
import androidx.lifecycle.*
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.SheetsStatus
import com.lozog.expensetracker.util.NotSignedInException
import kotlinx.coroutines.*
import java.io.IOException


class SheetsViewModel(private val sheetsRepository: SheetsRepository) : ViewModel() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SHEETS_VIEW_MODEL"
    }

    val status = MutableLiveData<SheetsStatus>()
    val statusText = MutableLiveData<String>()
    val recentHistory: LiveData<List<ExpenseRow>> = sheetsRepository.recentHistory.asLiveData()
    val detailExpenseRow = MutableLiveData<ExpenseRow>()

    fun setStatusText(newSignInStatus: String) {
        statusText.value = newSignInStatus
    }

    fun setStatus(newStatus: SheetsStatus) {
        status.value = newStatus
    }

    fun resetView() {
        status.value = SheetsStatus.DONE
    }

    fun getExpenseRowByRow(row: Int) {
        var expenseRow: List<ExpenseRow>
        viewModelScope.launch (Dispatchers.Main){
            expenseRow = sheetsRepository.getExpenseRowByRowAsync(row).await()
            detailExpenseRow.value = expenseRow.first()
        }
    }

    fun getRecentExpenseHistory() {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            try {
                Log.d(TAG, "calling sheetsRepository.getRecentExpenseHistoryAsync")
                sheetsRepository.getRecentExpenseHistoryAsync().await()
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
            }

            withContext(Dispatchers.Main) {
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun addExpenseRowToSheetAsync(
        expenseRow: ExpenseRow
    ) {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            var statusText: String

            try {
                Log.d(TAG, "addExpenseRowToSheetAsync")
                sheetsRepository.addExpenseRowAsync(expenseRow).await()

                sheetsRepository.getRecentExpenseHistoryAsync().await()

                val spentSoFar = sheetsRepository
                    .getCategorySpendingAsync(expenseRow.expenseCategoryValue)
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
                throw e
            }

            withContext(Dispatchers.Main) {
                setStatusText(statusText)
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun deleteRowAsync(row: Int) {
        viewModelScope.launch (Dispatchers.IO) {
            sheetsRepository.deleteRowAsync(row).await()
            sheetsRepository.getRecentExpenseHistoryAsync().await()
        }
    }
}

class SheetsViewModelFactory(private val sheetsRepository: SheetsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SheetsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SheetsViewModel(sheetsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
