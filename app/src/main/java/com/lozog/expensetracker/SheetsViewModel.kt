package com.lozog.expensetracker

import android.util.Log
import androidx.lifecycle.*
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.model.Sheet
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
    val error = MutableLiveData<UserRecoverableAuthIOException>()
    val spreadsheets = MutableLiveData<List<File>>()
    val sheets = MutableLiveData<List<Sheet>>()

    fun setStatusText(signInStatus: String) {
        statusText.value = signInStatus
    }

    fun setStatus(status: SheetsStatus) {
        this.status.value = status
    }

    fun setError(e: UserRecoverableAuthIOException) {
        error.value = e
    }

    fun setSpreadsheets(spreadsheets: List<File>) {
        this.spreadsheets.value = spreadsheets
    }

    fun setSheets(sheets: List<Sheet>) {
        this.sheets.value = sheets
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
            Log.d(TAG, "calling sheetsRepository.getRecentExpenseHistoryAsync")
            sheetsRepository.getRecentExpenseHistoryAsync().await()

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
//                Log.d(TAG, "addExpenseRowToSheetAsync")
                sheetsRepository.addExpenseRowAsync(expenseRow).await()

                val spentSoFar = sheetsRepository
                    .getCategorySpendingAsync(expenseRow.expenseCategoryValue)
                    .await()
//                statusText = getString(R.string.status_spent_so_far, spentSoFar, expenseCategoryValue)
                statusText = "$spentSoFar spent so far in ${expenseRow.expenseCategoryValue}"
                sheetsRepository.getRecentExpenseHistoryAsync()
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
                throw e // TODO: doesn't work
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

    fun fetchSpreadsheets() {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            Log.d(TAG, "calling sheetsRepository.fetchSpreadsheets")
            var spreadsheets: List<File> = listOf()
            try {
                spreadsheets = sheetsRepository.fetchSpreadsheetsAsync().await()
            } catch(e: UserRecoverableAuthIOException) {
                Log.d(TAG, "UserRecoverableAuthIOException")
                withContext(Dispatchers.Main) {
                    setError(e)
                }
            }

            withContext(Dispatchers.Main) {
                setSpreadsheets(spreadsheets)
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun fetchSheets(spreadsheetId: String) {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            Log.d(TAG, "calling sheetsRepository.fetchSheets")
            var sheets: List<Sheet> = listOf()
            try {
                sheets = sheetsRepository.fetchSheetsAsync(spreadsheetId).await()
            } catch(e: UserRecoverableAuthIOException) {
                Log.d(TAG, "UserRecoverableAuthIOException")
                withContext(Dispatchers.Main) {
                    setError(e)
                }
            }

            withContext(Dispatchers.Main) {
                setSheets(sheets)
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun findMonthColumns() {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch (Dispatchers.IO) {
            Log.d(TAG, "calling sheetsRepository.fetchSheets")
            // var sheets: List<Sheet> = listOf()
            try {
                sheetsRepository.findMonthColumnsAsync()
            } catch(e: UserRecoverableAuthIOException) {
                Log.d(TAG, "UserRecoverableAuthIOException")
                withContext(Dispatchers.Main) {
                    setError(e)
                }
            }

            withContext(Dispatchers.Main) {
                // setSheets(sheets)
                setStatus(SheetsStatus.DONE)
            }
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
