package com.lozog.expensetracker

import android.util.Log
import androidx.lifecycle.*
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import com.google.api.services.sheets.v4.model.Sheet
import com.lozog.expensetracker.util.Event
import com.lozog.expensetracker.util.NoInternetException
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.SheetsStatus
import kotlinx.coroutines.*

class SheetsViewModel(private val sheetsRepository: SheetsRepository) : ViewModel() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SheetsViewModel"
    }

    var recentHistory: LiveData<List<ExpenseRow>> = sheetsRepository.getRecentHistory()

    val status = MutableLiveData<SheetsStatus>()
    val statusText = MutableLiveData<String>()
    val detailExpenseRow = MutableLiveData<ExpenseRow>()
    val error = MutableLiveData<UserRecoverableAuthIOException>()
    val spreadsheets = MutableLiveData<List<File>>()
    val sheets = MutableLiveData<List<Sheet>>()

    private val _toastEvent = MutableLiveData<Event<String>>()
    val toastEvent: LiveData<Event<String>> get() = _toastEvent

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

    fun getExpenseRowById(id: Int) {
        var expenseRow: ExpenseRow
        viewModelScope.launch (Dispatchers.Main){
            expenseRow = sheetsRepository.getExpenseRowByIdAsync(id).await()
            detailExpenseRow.value = expenseRow
            Log.d(TAG, "getExpenseRowById: ${detailExpenseRow.value}")
        }
    }

    fun getRecentExpenseHistory() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    withTimeout(10000) {
                        sheetsRepository.fetchExpenseRowsFromSheetAsync().await()
                    }
                }
            } catch (e: NoInternetException) {
                _toastEvent.value = Event(e.message ?: "Something went wrong")
            }
        }
    }

    fun addExpenseRowToSheetAsync(
        expenseRow: ExpenseRow
    ) {
        setStatus(SheetsStatus.IN_PROGRESS)
        viewModelScope.launch {
            Log.d(TAG, "addExpenseRowToSheetAsync")
            try {
                // create the expense row
                withContext(Dispatchers.IO) {
                    sheetsRepository.addExpenseRowAsync(expenseRow).await()

                    // fetch up to date spending for category
                    val spentSoFar = sheetsRepository
                        .fetchCategorySpendingAsync(expenseRow.expenseCategoryValue)
                        .await()

                    // TODO: kinda going crazy with the contexts here
                    withContext(Dispatchers.Main) {
                        _toastEvent.value = Event("$spentSoFar spent so far in ${expenseRow.expenseCategoryValue}")
                    }

                    // fetch up to date recent history
                    sheetsRepository.fetchExpenseRowsFromSheetAsync()
                }
            } catch (e: NoInternetException) {
                _toastEvent.value = Event(e.message ?: "Something went wrong")
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
                setStatus(SheetsStatus.DONE)
            }
        }
    }

    fun deleteRowAsync(expenseId: Int) {
        viewModelScope.launch {
            try {
                sheetsRepository.deleteRowAsync(expenseId).await()
                sheetsRepository.fetchExpenseRowsFromSheetAsync().await()
                _toastEvent.value = Event("Deleted row with id $expenseId")
            } catch (e: NoInternetException) {
                _toastEvent.value = Event(e.message ?: "Something went wrong")
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
                withContext(Dispatchers.Main) {
                    _toastEvent.value = Event(e.message ?: "Something went wrong")
                }
            } catch(e: NoInternetException) {
                withContext(Dispatchers.Main) {
                    _toastEvent.value = Event(e.message ?: "Something went wrong")
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
                withContext(Dispatchers.Main) {
                    _toastEvent.value = Event(e.message ?: "Something went wrong")
                }
            } catch(e: NoInternetException) {
                withContext(Dispatchers.Main) {
                    _toastEvent.value = Event(e.message ?: "Something went wrong")
                }
            }

            withContext(Dispatchers.Main) {
                setSheets(sheets)
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
