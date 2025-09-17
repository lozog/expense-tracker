package com.lozog.expensetracker.ui

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentExpenseBinding
import com.lozog.expensetracker.util.KeyboardManager
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import kotlinx.android.synthetic.main.fragment_expense.*
import java.text.SimpleDateFormat
import java.util.*

private const val ID_PARAM = "id"

class ExpenseFragment : Fragment() {
    private var id: Int = 0
    private var _binding: FragmentExpenseBinding? = null
    private val binding get() = _binding!!
    private lateinit var mainActivity: MainActivity
    private val sheetsViewModel: SheetsViewModel by activityViewModels {
        SheetsViewModelFactory(
            (context?.applicationContext as ExpenseTrackerApplication).sheetsRepository,
            (context?.applicationContext as ExpenseTrackerApplication).applicationScope
        )
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

    private var notificationId: Int = -1

    companion object {
        private const val TAG = "EXPENSE_TRACKER ExpenseFragment"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            id = it.getInt(ID_PARAM)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExpenseBinding.inflate(inflater, container, false)
        mainActivity = activity as MainActivity
        val root: View = binding.root

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

        if (!isNewExpenseRow()) {
            // editing existing ExpenseRow
            sheetsViewModel.getExpenseRowById(id)

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

            // set form data from intent, if applicable
            arguments?.let {
                expenseAmount.setText(it.getString("amount", ""))
                notificationId = it.getInt("notification_id")
            }

            // clear the notification
            if (notificationId != -1) {
                val notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(notificationId)
            }
        }

        submitButton.setOnClickListener { view ->
            upsertExpense(view)

            // TODO: change text if it was a new row or updated
            // and also, it should wait for success or failure from all the other things
            Toast.makeText(requireContext(), "Added expense", Toast.LENGTH_SHORT).show()

            if (isNewExpenseRow()) {
                clearInputs()
            }
        }

        sheetsViewModel.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        setHasOptionsMenu(true)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        KeyboardManager.showKeyboard(expenseItem)

        // Enable the action bar back button, since navigation_new_expense is the start destination in the nav
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                findNavController().navigate(R.id.navigation_history)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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

    private fun isNewExpenseRow(): Boolean {
        return id == 0
    }

    private fun upsertExpense(view: View) {
        KeyboardManager.hideKeyboard(view)

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

        if (!isNewExpenseRow()) {
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

        sheetsViewModel.addExpenseRowToSheetAsync(expenseRow)
    }
}