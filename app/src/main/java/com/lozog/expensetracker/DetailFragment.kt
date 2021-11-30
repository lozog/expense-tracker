package com.lozog.expensetracker

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.work.*
import com.google.android.material.snackbar.Snackbar
import com.lozog.expensetracker.databinding.FragmentFormBinding
import com.lozog.expensetracker.ui.form.FormFragment
import com.lozog.expensetracker.util.ConnectivityHelper
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.util.*
import java.util.concurrent.TimeUnit

private const val ROW_PARAM = "row"

class DetailFragment : Fragment() {
    private var row: Int = 0
    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity
    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }
    private lateinit var expenseRow: ExpenseRow

    /********** UI Widgets **********/
    private lateinit var expenseItem: EditText
    private lateinit var expenseCategory: Button
    private lateinit var expenseAmount: EditText
    private lateinit var expenseAmountOthers: EditText
    private lateinit var expenseDate: EditText
    private lateinit var expenseNotes: EditText
    private lateinit var currencyLabel: EditText
    private lateinit var currencyExchangeRate: EditText
    private lateinit var submitButton: Button
    private lateinit var statusTextView: TextView

    companion object {
        private const val TAG = "EXPENSE_TRACKER DETAIL_FRAGMENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            row = it.getInt(ROW_PARAM)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity
        val root: View = binding.root
        statusTextView = binding.statusText
        expenseDate = binding.expenseDate
        expenseItem = binding.expenseItem
        expenseCategory = binding.expenseCategory
        expenseAmount = binding.expenseAmount
        expenseAmountOthers = binding.expenseAmountOthers
        expenseNotes = binding.expenseNotes
        currencyLabel = binding.currencyLabel
        currencyExchangeRate = binding.currencyExchangeRate
        submitButton = binding.expenseSubmitButton

        sheetsViewModel.getExpenseRowByRow(row)

        sheetsViewModel.detailExpenseRow.observe(viewLifecycleOwner, {
            expenseRow = it
            statusTextView.text = expenseRow.toString()
            expenseDate.setText(expenseRow.expenseDate)
            expenseItem.setText(expenseRow.expenseItem)
            expenseCategory.text = expenseRow.expenseCategoryValue
            expenseAmount.setText(expenseRow.expenseAmount)
            expenseAmountOthers.setText(expenseRow.expenseAmountOthers)
            expenseNotes.setText(expenseRow.expenseNotes)
            currencyLabel.setText(expenseRow.currency)
            currencyExchangeRate.setText(expenseRow.exchangeRate)
        })

        submitButton.setOnClickListener { view ->
            updateExpense(view)
        }

        return root
    }

    private fun hideKeyboard(view: View) {
        val inputManager = mainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            view.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
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

    private fun updateExpense(view: View) {
        hideKeyboard(view)

        // TODO: observe sheetsViewModel
        submitButton.text = getString(R.string.button_expense_submitting)

        if (!validateInput()) {
            sheetsViewModel.resetView()
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val dataSheetName = sharedPreferences.getString("data_sheet_name", null)
        val overviewSheetName = sharedPreferences.getString("overview_sheet_name", null)

        // TODO: move to validatePrefs()
        if (spreadsheetId == null || spreadsheetId == "") {
            Snackbar.make(view, getString(R.string.form_no_spreadsheet_id), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            sheetsViewModel.resetView()
            return
        }

        if (dataSheetName == null || dataSheetName == "") {
            Snackbar.make(view, getString(R.string.form_no_data_sheet_name), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            sheetsViewModel.resetView()
            return
        }

        if (overviewSheetName == null || overviewSheetName == "") {
            Snackbar.make(view, getString(R.string.form_no_overview_sheet_name), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            sheetsViewModel.resetView()
            return
        }

        var currency = currencyLabel.text.toString()
        var exchangeRate = currencyExchangeRate.text.toString()

        if (currency == "") {
            currency = sharedPreferences.getString("currency", null)?: ""
        }

        if (exchangeRate == "") {
            exchangeRate = sharedPreferences.getString("exchange_rate", null)?: ""
        }

        val expenseDateText = expenseDate.text.toString()
        val expenseItemText = expenseItem.text.toString()
        val expenseCategoryText = expenseCategory.text.toString()
        val expenseAmountText = expenseAmount.text.toString()
        val expenseAmountOthersText = expenseAmountOthers.text.toString()
        val expenseNotesText = expenseNotes.text.toString()

        val expenseRow = ExpenseRow(
            expenseDateText,
            expenseItemText,
            expenseCategoryText,
            expenseAmountText,
            expenseAmountOthersText,
            "",
            expenseNotesText,
            currency,
            exchangeRate,
            "UPDATED"
        )

        if (ConnectivityHelper.isInternetConnected(mainActivity)) {
            Log.d(TAG, "internet")

            try {
                Log.d(TAG, "calling sheetsViewModel.updateExpenseRowAsync")
//                sheetsViewModel.addExpenseRowToSheetAsync(
//                    expenseRow
//                )
//                sheetsViewModel.updateExpenseRowAsync(expenseRow)
            } catch (e: Exception) {
                Log.d(TAG, "exception: $e")
                sheetsViewModel.setStatusText(e.toString())
            }
        } else {
            Log.d(TAG, "no internet")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val sheetsWorkRequest: OneTimeWorkRequest =
                OneTimeWorkRequestBuilder<SheetsWorker>()
                    .setConstraints(constraints)
                    .setInputData(expenseRow.toWorkData())
                    .setBackoffCriteria(
                        BackoffPolicy.LINEAR,
                        OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS)
                    .build()

            WorkManager
                .getInstance(mainActivity)
                .enqueueUniqueWork(
                    UUID.randomUUID().toString(),
                    ExistingWorkPolicy.APPEND,
                    sheetsWorkRequest
                )

            WorkManager
                .getInstance(mainActivity)
                .getWorkInfoByIdLiveData(sheetsWorkRequest.id)
                .observe(viewLifecycleOwner, { workInfo: WorkInfo ->
                    // TODO: refactor Worker stuff
//                    workManagerObserver(workInfo)
                })

            sheetsViewModel.resetView()
            sheetsViewModel.setStatusText("no internet - $expenseItemText request queued")
        }
    }
}