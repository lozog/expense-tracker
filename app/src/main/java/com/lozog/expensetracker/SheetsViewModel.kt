package com.lozog.expensetracker

import android.util.Log
import android.widget.Toast
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

    var recentHistory: LiveData<List<ExpenseRow>> = sheetsRepository.getRecentHistory()

    val status = MutableLiveData<SheetsStatus>()
    val statusText = MutableLiveData<String>()
    val detailExpenseRow = MutableLiveData<ExpenseRow>()
    val error = MutableLiveData<UserRecoverableAuthIOException>()
    val spreadsheets = MutableLiveData<List<File>>()
    val sheets = MutableLiveData<List<Sheet>>()

    fun setStatusText(signInStatus: String) {
        statusText.value = signInStatus
    }

    private fun setStatus(status: SheetsStatus) {
        this.status.value = status
    }

    private fun setError(e: UserRecoverableAuthIOException) {
        error.value = e
    }

    private fun setSpreadsheets(spreadsheets: List<File>) {
        this.spreadsheets.value = spreadsheets
    }

    private fun setSheets(sheets: List<Sheet>) {
        this.sheets.value = sheets
    }

    fun resetView() {
        status.value = SheetsStatus.DONE
    }

    fun setRecentHistory() {
        recentHistory = sheetsRepository.getRecentHistory()
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
                withTimeout(10000) {
                    sheetsRepository.fetchExpenseRowsFromSheetAsync().await()
                }
            } catch (e: Exception) {
                Log.d(TAG, e.toString())
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "setting status text")
                    setStatusText(e.toString()) // TODO: this doesn't work
                }
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
            Log.d(TAG, "addExpenseRowToSheetAsync")
            var statusText: String

            try {
                // create the expense row
                sheetsRepository.addExpenseRowAsync(expenseRow).await()

                // fetch up to date spending for category
                val spentSoFar = sheetsRepository
                    .fetchCategorySpendingAsync(expenseRow.expenseCategoryValue)
                    .await()
                statusText = "$spentSoFar spent so far in ${expenseRow.expenseCategoryValue}"

                // fetch up to date recent history
                sheetsRepository.fetchExpenseRowsFromSheetAsync()

            } catch (e: Exception) {
                statusText = "exception: ${e.message}"
            }

            // TODO: catch these errors
//            catch (e: UserRecoverableAuthIOException) {
////                Log.e(TAG, getString(R.string.status_need_permission))
////                mainActivity.startForRequestAuthorizationResult.launch(e.intent)
//                statusText = "need google permission"
//            } catch (e: IOException) {
//                Log.e(TAG, e.toString())
////                statusText = getString(R.string.status_google_error)
//                statusText = "google error"
//            } catch (e: NotSignedInException) {
////                Log.d(TAG, getString(R.string.status_not_signed_in))
//                statusText = "not signed in"
//            }


            withContext(Dispatchers.Main) {
                setStatusText(statusText)
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun deleteRowAsync(expenseId: Int) {
        viewModelScope.launch (Dispatchers.IO) {
            try {
                sheetsRepository.deleteRowAsync(expenseId).await()
                sheetsRepository.fetchExpenseRowsFromSheetAsync().await()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatusText("no internet")
                }
            }
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
