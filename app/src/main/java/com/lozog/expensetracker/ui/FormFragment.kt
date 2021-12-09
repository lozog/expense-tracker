package com.lozog.expensetracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.lozog.expensetracker.*
import com.lozog.expensetracker.R
import com.lozog.expensetracker.databinding.FragmentFormBinding
import com.lozog.expensetracker.SheetsViewModel
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import com.lozog.expensetracker.util.SheetsStatus
import kotlinx.android.synthetic.main.fragment_form.*
import java.text.SimpleDateFormat
import java.util.*

class FormFragment : Fragment() {
    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }
    private var _binding: FragmentFormBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var mainActivity: MainActivity

    companion object {
        private const val TAG = "EXPENSE_TRACKER FORM_FRAGMENT"
    }

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

    /********** OVERRIDE METHODS **********/

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
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

        submitButton.setOnClickListener{view ->
            submitExpense(view)
        }

        expenseCategory.setOnClickListener{
            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle(R.string.expense_category)
            builder.setItems(R.array.categories) {_, which ->
                expenseCategory.text = SheetsRepository.CATEGORIES[which]
            }
            val dialog = builder.create()
            dialog.show()
        }

        // set default category
        expenseCategory.text = SheetsRepository.CATEGORIES[0]

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        expenseDate.setText(todayDate)

        sheetsViewModel.status.observe(viewLifecycleOwner, {
            when (it) {
                SheetsStatus.IN_PROGRESS -> expenseSubmitButton.text = getString(R.string.button_expense_submitting)
                SheetsStatus.DONE -> {
                    clearInputs()
                    expenseSubmitButton.text = getString(R.string.button_expense_submit)
                }
                else -> expenseSubmitButton.text = getString(R.string.button_expense_submit)
            }
        })

        sheetsViewModel.statusText.observe(viewLifecycleOwner, {
            // TODO: observe status instead of statusText
            statusTextView.text = it
//            Snackbar.make(expenseSubmitButton, it, Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
        })

        expenseItem.requestFocus()
        mainActivity.showKeyboard(expenseItem)

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /********** HELPER METHODS **********/

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

    private fun submitExpense(view: View) {
        mainActivity.hideKeyboard(view)

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
            ExpenseRow.STATUS_PENDING
        )

        try {
            Log.d(TAG, "addExpenseRowToSheetAsync")
            sheetsViewModel.addExpenseRowToSheetAsync(expenseRow)
        } catch (e: Exception) {
            Log.d(TAG, "exception: $e")
            sheetsViewModel.setStatusText(e.toString())
        }
    }
}