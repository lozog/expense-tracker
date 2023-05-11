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
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentExpenseBinding
import com.lozog.expensetracker.util.SheetsStatus
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import kotlinx.android.synthetic.main.fragment_expense.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val ROW_PARAM = "row"

class ExpenseFragment : Fragment() {
    private var row: Int = 0
    private var _binding: FragmentExpenseBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity
    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }
    private lateinit var expenseRow: ExpenseRow
    private var expenseAmountTextCurrent = ""
    private var expenseAmountOthersTextCurrent = ""

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
        private const val TAG = "EXPENSE_TRACKER EXPENSE_FRAGMENT"
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
        _binding = FragmentExpenseBinding.inflate(inflater, container, false)
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

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val categoriesPrefs = sharedPreferences.getString("categories", null)?: ""
        val categories = categoriesPrefs.split(",").map { it.trim() }.toTypedArray()

        expenseCategory.setOnClickListener{view ->
            when (view.id) {
                R.id.expenseCategory -> {
                    val builder = AlertDialog.Builder(mainActivity)
                    builder.setTitle(R.string.expense_category)
                    builder.setItems(categories) {_, which ->
                        expenseCategory.text = categories[which]
                    }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }

        expenseAmount.addTextChangedListener{
            val s = it.toString()
            if (s != "" && s != expenseAmountTextCurrent) {
//            expenseAmount.removeTextChangedListener(this)

                val cleanString: String = s.replace("$", "")
                    .replace(".", "")
                    .replace(",", "")

                val parsed = cleanString.toDouble()
                var formatted: String = NumberFormat.getCurrencyInstance().format(parsed / 100)
                formatted = formatted.drop(1) // drop leading $

                expenseAmountTextCurrent = formatted
                expenseAmount.setText(formatted)
                expenseAmount.setSelection(formatted.length)

//            expenseAmount.addTextChangedListener(this)
            }
        }

        // TODO: refactor into a helper function, for DRY
        expenseAmountOthers.addTextChangedListener{
            val s = it.toString()
            if (s != "" && s != expenseAmountOthersTextCurrent) {
//            expenseAmountOthers.removeTextChangedListener(this)

                val cleanString: String = s.replace("$", "")
                    .replace(".", "")
                    .replace(",", "")

                val parsed = cleanString.toDouble()
                var formatted: String = NumberFormat.getCurrencyInstance().format(parsed / 100)
                formatted = formatted.drop(1) // drop leading $

                expenseAmountOthersTextCurrent = formatted
                expenseAmountOthers.setText(formatted)
                expenseAmountOthers.setSelection(formatted.length)

//            expenseAmountOthers.addTextChangedListener(this)
            }
        }

        if (row != 0) {
            // editing existing ExpenseRow
            sheetsViewModel.getExpenseRowByRow(row)

            sheetsViewModel.detailExpenseRow.observe(viewLifecycleOwner) {
                expenseRow = it

                expenseDate.setText(expenseRow.expenseDate)
                expenseItem.setText(expenseRow.expenseItem)
                expenseCategory.text = expenseRow.expenseCategoryValue
                expenseAmount.setText(expenseRow.expenseAmount)
                expenseAmountOthers.setText(expenseRow.expenseAmountOthers)
                expenseNotes.setText(expenseRow.expenseNotes)
                currencyLabel.setText(expenseRow.currency)
                currencyExchangeRate.setText(expenseRow.exchangeRate)
            }
        } else {
            // new ExpenseRow

            // Set default value of expenseDate input as today's date
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            expenseDate.setText(todayDate)

            // set default category
            expenseCategory.text = categories[0]
        }

        submitButton.setOnClickListener { view ->
            updateExpense(view)
        }

        sheetsViewModel.status.observe(viewLifecycleOwner) {
            when (it) {
                SheetsStatus.IN_PROGRESS -> submitButton.text =
                    getString(R.string.button_expense_submitting)

                SheetsStatus.DONE -> {
                    if (row == 0) {
                        clearInputs()
                    }
                    expenseSubmitButton.text = getString(R.string.button_expense_submit)
                }

                null -> submitButton.text = getString(R.string.button_expense_submit)
            }
        }

        expenseItem.requestFocus()
        mainActivity.showKeyboard(expenseItem)

        return root
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

    private fun updateExpense(view: View) {
        mainActivity.hideKeyboard(view)

        if (!validateInput()) {
            sheetsViewModel.resetView()
            return
        }

        val expenseDateText = expenseDate.text.toString()
        val expenseItemText = expenseItem.text.toString()
        val expenseCategoryText = expenseCategory.text.toString()
        val expenseAmountText = expenseAmount.text.toString()
        val expenseAmountOthersText = expenseAmountOthers.text.toString()
        val expenseNotesText = expenseNotes.text.toString()
        val currency = currencyLabel.text.toString()
        val exchangeRate = currencyExchangeRate.text.toString()

        if (row != 0) {
            expenseRow.expenseDate = expenseDateText
            expenseRow.expenseItem = expenseItemText
            expenseRow.expenseCategoryValue = expenseCategoryText
            expenseRow.expenseAmount = expenseAmountText
            expenseRow.expenseAmountOthers = expenseAmountOthersText
            expenseRow.expenseNotes = expenseNotesText
            expenseRow.currency = currency
            expenseRow.exchangeRate = exchangeRate
            expenseRow.syncStatus = ExpenseRow.STATUS_PENDING
        } else {
            expenseRow = ExpenseRow(
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
        }

        try {
           sheetsViewModel.addExpenseRowToSheetAsync(expenseRow)
        } catch (e: Exception) {
            Log.d(TAG, "exception: $e")
            sheetsViewModel.setStatusText(e.toString())
        }
    }
}