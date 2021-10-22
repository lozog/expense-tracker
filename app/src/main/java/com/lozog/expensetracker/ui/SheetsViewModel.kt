package com.lozog.expensetracker.ui

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lozog.expensetracker.SheetsRepository
import kotlinx.coroutines.launch


class SheetsViewModel : ViewModel() {
    companion object {
        private const val TAG = "SHEETS_VIEW_MODEL"

        enum class SHEETS_STATUS {
            DONE, IN_PROGRESS
        }
//
//        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
//        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"
//
//        // January -> column C, etc
//        // TODO: dynamically find month columns
//        private val MONTH_COLUMNS = listOf(
//            "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"
//        )
//
//        // TODO: dynamically find category cell
//        private val CATEGORY_ROW_MAP = mapOf(
//            "Groceries" to "20",
//            "Dining Out" to "21",
//            "Drinks" to "22",
//            "Material Items" to "23",
//            "Entertainment" to "24",
//            "Transit" to "25",
//            "Personal/Medical" to "26",
//            "Gifts" to "27",
//            "Travel" to "28",
//            "Miscellaneous" to "29",
//            "Film" to "30",
//            "Household" to "31",
//            "Other Income" to "5"
//        )
//        val CATEGORIES = arrayOf(
//            "Groceries",
//            "Dining Out",
//            "Drinks",
//            "Material Items",
//            "Entertainment",
//            "Transit",
//            "Personal/Medical",
//            "Gifts",
//            "Travel",
//            "Miscellaneous",
//            "Film",
//            "Household",
//            "Other Income"
//        )
    }

//    private val job = Job()
//    private val coroutineScope = CoroutineScope(Dispatchers.IO + job)

    private val sheetsRepository = SheetsRepository()

    val statusText = MutableLiveData<String>()

    fun setStatusText(newSignInStatus: String) {
        statusText.value = newSignInStatus
    }

//    override fun onCleared() {
//        super.onCleared()
//        job.cancel()
//    }

//    /********** GOOGLE SHEETS METHODS **********/

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
        Log.d(TAG, "in sheetsViewModel.addExpenseRowToSheetAsync")
        viewModelScope.launch {
            Log.d(TAG, "calling sheetsRepository.addExpenseRowToSheetAsync")

//            sheetsRepository.addExpenseRowToSheetAsync(
//                spreadsheetId,
//                sheetName,
//                expenseDate,
//                expenseItem,
//                expenseCategoryValue,
//                expenseAmount,
//                expenseAmountOthers,
//                expenseNotes,
//                currency,
//                exchangeRate
//            )
//            Log.d(TAG, "done sheetsRepository.addExpenseRowToSheetAsync")
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

//                val spentSoFar = withContext(Dispatchers.IO) {
//                    sheetsRepository.getCategorySpendingAsync(spreadsheetId, expenseCategoryValue)
//                }
//                statusText = getString(R.string.status_spent_so_far, spentSoFar, expenseCategoryValue)
//
//                clearInputs()
//            } catch (e: UserRecoverableAuthIOException) {
//                Log.e(TAG, getString(R.string.status_need_permission))
////                mainActivity.startForRequestAuthorizationResult.launch(e.intent)
//                statusText = getString(R.string.status_need_permission)
//            } catch (e: IOException) {
//                Log.e(TAG, e.toString())
//                statusText = getString(R.string.status_google_error)
//            } catch (e: MainActivity.NotSignedInException) {
//                Log.d(TAG, getString(R.string.status_not_signed_in))
//                statusText = getString(R.string.status_not_signed_in)
//            }

//            sheetsViewModel.setStatusText(statusText)
//            submitButton.text = getString(R.string.button_expense_submit)
        }
    }
}