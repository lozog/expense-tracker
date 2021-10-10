package com.lozog.expensetracker.ui

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.gms.common.SignInButton
import com.google.android.material.snackbar.Snackbar
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.lozog.expensetracker.AddRowRequest
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.R
import com.lozog.expensetracker.databinding.FragmentFormBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FormFragment : Fragment() {
    private var _binding: FragmentFormBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    companion object {
        private const val TAG = "FORM_FRAGMENT"
    }

    private lateinit var mainActivity: MainActivity

    /********** UI Widgets **********/
    private lateinit var expenseItem: EditText
    private lateinit var expenseCategory: Button
    private lateinit var expenseAmount: EditText
    private lateinit var expenseAmountOthers: EditText
    private lateinit var expenseDate: EditText
    private lateinit var expenseNotes: EditText
    private lateinit var currencyLabel: EditText
    private lateinit var currencyExchangeRate: EditText
//    private lateinit var signInButton: SignInButton
//    private lateinit var signOutButton: Button
    private lateinit var submitButton: Button
    private lateinit var statusTextView: TextView

    private var expenseCategoryValue: String = ""

    /********** OVERRIDE METHODS **********/

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFormBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        expenseItem = binding.expenseItem
        expenseCategory = binding.expenseCategory
        expenseAmount = binding.expenseAmount
        expenseAmountOthers = binding.expenseAmountOthers
        expenseDate = binding.expenseDate
        expenseNotes = binding.expenseNotes
        currencyLabel = binding.currencyLabel
        currencyExchangeRate = binding.currencyExchangeRate
        submitButton = binding.expenseSubmitButton
        statusTextView = binding.statusText

        expenseCategory.setOnClickListener{view ->
            when (view.id) {
                R.id.expenseCategory -> {
                    val builder = AlertDialog.Builder(mainActivity)
                    builder.setTitle(R.string.expense_category)
                    builder.setItems(R.array.categories) {_, which ->
                        Log.d(TAG, "chose ${mainActivity.CATEGORIES[which]} as the category")
                        expenseCategoryValue = mainActivity.CATEGORIES[which]
                        expenseCategory.text = expenseCategoryValue
                    }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }

        // set default category
        expenseCategory.text = mainActivity.CATEGORIES[0]

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        expenseDate.setText(todayDate)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /********** HELPER METHODS **********/

    private fun hideKeyboard(view: View) {
        val inputManager = mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            view.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    private fun isInternetConnected(): Boolean {
        val cm = mainActivity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (expenseItem.text.isBlank()) {
            expenseItem.error = getString(R.string.form_no_item)
            isValid = false
        }

        if (expenseAmount.text.isBlank()) {
            expenseAmount.error = getString(R.string.form_no_amount)
            isValid = false
        }

        if (expenseDate.text.isBlank()) {
            expenseDate.error = getString(R.string.form_no_date)
            isValid = false
        }

        return isValid
    }

    private fun clearInputs() {
        expenseItem.setText("")
        expenseAmount.setText("")
        expenseAmountOthers.setText("")
        expenseNotes.setText("")
        currencyLabel.setText("")
        currencyExchangeRate.setText("")
    }

    /********** PUBLIC METHODS **********/

    fun submitExpense(view: View) {
        hideKeyboard(view)

        submitButton.text = getString(R.string.button_expense_submitting)

        if (!validateInput()) {
            submitButton.text = getString(R.string.button_expense_submit)
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

        if (spreadsheetId == null) {
            Snackbar.make(view, getString(R.string.form_no_spreadsheet_id), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        if (sheetName == null) {
            Snackbar.make(view, getString(R.string.form_no_data_sheet_name), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        var currency = currencyLabel.text.toString()
        var exchangeRate = currencyExchangeRate.text.toString()

        if (currency == "") {
            val defaultCurrency = sharedPreferences.getString("currency", getString(R.string.default_currency))

            if (defaultCurrency == null) {
                Snackbar.make(view, getString(R.string.form_no_currency), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return
            }

            currency = defaultCurrency
        }

        if (exchangeRate == "") {
            val defaultExchangeRate = sharedPreferences.getString("exchange_rate", getString(R.string.default_exchange_rate))

            if (defaultExchangeRate == null) {
                Snackbar.make(view, getString(R.string.form_no_exchange_rate), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return
            }

            exchangeRate = defaultExchangeRate
        }

        if (isInternetConnected()) {
//            Log.d(TAG, "there is an internet connection!")
            mainActivity.coroutineScope.launch (Dispatchers.Main) {
                var statusText: String

                try {
//                    val appendResponse = addExpenseRowToSheetAsync(
                    mainActivity.addExpenseRowToSheetAsync(
                        spreadsheetId,
                        sheetName,
                        expenseDate.text.toString(),
                        expenseItem.text.toString(),
                        expenseCategoryValue,
                        expenseAmount.text.toString(),
                        expenseAmountOthers.text.toString(),
                        expenseNotes.text.toString(),
                        currency,
                        exchangeRate
                    ).await()

                    val spentSoFar = mainActivity.getCategorySpendingAsync(spreadsheetId, expenseCategoryValue).await()
                    statusText = getString(R.string.status_spent_so_far, spentSoFar, expenseCategoryValue)

                    clearInputs()
                } catch (e: UserRecoverableAuthIOException) {
                    Log.e(TAG, getString(R.string.status_need_permission))
                    startActivityForResult(e.intent, MainActivity.RC_REQUEST_AUTHORIZATION)
                    statusText = getString(R.string.status_need_permission)
                } catch (e: IOException) {
                    Log.e(TAG, e.toString())
                    statusText = getString(R.string.status_google_error)
                }

                Snackbar.make(view, statusText, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

                statusTextView.text = statusText
                submitButton.text = getString(R.string.button_expense_submit)
            }
        } else {
//            Log.d(TAG, "no internet connection!")

            mainActivity.coroutineScope.launch (Dispatchers.Main) {
                val addRowRequest = AddRowRequest(
                    0,
                    spreadsheetId,
                    sheetName,
                    expenseDate.text.toString(),
                    expenseItem.text.toString(),
                    expenseCategoryValue,
                    expenseAmount.text.toString(),
                    expenseAmountOthers.text.toString(),
                    expenseNotes.text.toString(),
                    currency,
                    exchangeRate
                )

                mainActivity.insertRowIntoDBAsync(addRowRequest).await()

                clearInputs()

                statusTextView.text = getString(R.string.status_no_internet)
                submitButton.text = getString(R.string.button_expense_submit)
            }
        }
    }
}