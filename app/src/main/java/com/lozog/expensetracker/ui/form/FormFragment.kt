package com.lozog.expensetracker.ui.form

import android.app.AlertDialog
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import androidx.work.*
import com.google.android.material.snackbar.Snackbar
import com.lozog.expensetracker.*
import com.lozog.expensetracker.R
import com.lozog.expensetracker.databinding.FragmentFormBinding
import com.lozog.expensetracker.SheetsViewModel
import com.lozog.expensetracker.util.ExpenseRow
import com.lozog.expensetracker.util.SheetsStatus
import kotlinx.android.synthetic.main.fragment_form.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class FormFragment : Fragment() {
    private val sheetsViewModel: SheetsViewModel by viewModels()
    private var _binding: FragmentFormBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var mainActivity: MainActivity

    companion object {
        private const val TAG = "FORM_FRAGMENT"
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

        expenseCategory.setOnClickListener{view ->
            when (view.id) {
                R.id.expenseCategory -> {
                    val builder = AlertDialog.Builder(mainActivity)
                    builder.setTitle(R.string.expense_category)
                    builder.setItems(R.array.categories) {_, which ->
//                        Log.d(TAG, "chose ${MainActivity.CATEGORIES[which]} as the category")
                        expenseCategory.text = SheetsRepository.CATEGORIES[which]
                    }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        }

        // set default category
        expenseCategory.text = SheetsRepository.CATEGORIES[0]

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        expenseDate.setText(todayDate)

        sheetsViewModel.status.observe(viewLifecycleOwner, {
            when (it) {
                SheetsStatus.IN_PROGRESS -> {
                    expenseSubmitButton.text = getString(R.string.button_expense_submitting)
                }
                SheetsStatus.DONE -> {
                    clearInputs()
                    expenseSubmitButton.text = getString(R.string.button_expense_submit)
                }
                null -> {
                    expenseSubmitButton.text = getString(R.string.button_expense_submit)
                }
            }
        })

        sheetsViewModel.statusText.observe(viewLifecycleOwner, {
            statusTextView.text = it
            Snackbar.make(expenseSubmitButton, it, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        })

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

    private fun isInternetConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCapabilities = connectivityManager.activeNetwork ?: return false
        val actNw =
            connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false

        Log.d(TAG, "capabilities: $actNw")

        val result = when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }

        return result
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

    private fun workManagerObserver(workInfo: WorkInfo?) {
        if (workInfo != null) {
            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    Log.d(TAG, getString(
                        R.string.notification_queued_requests_content,
                        workInfo.outputData.getString("expenseItem")
                    ))

                    // send notification
                    val builder = NotificationCompat
                        .Builder(mainActivity, MainActivity.QUEUED_REQUEST_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_notify)
                        .setContentTitle(getString(R.string.notification_queued_requests_title))
                        .setContentText(
                            getString(
                                R.string.notification_queued_requests_content,
                                workInfo.outputData.getString("expenseItem")
                            )
                        )
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                    with(NotificationManagerCompat.from(mainActivity)) {
                        // notificationId is a unique int for each notification that you must define
                        val notificationId = 0 // I'm using the same id for each notification, so it only shows the last one
                        notify(notificationId, builder.build())
                    }

                }
                // TODO: handle failure
                else -> {}
            }
        }
    }

    private fun submitExpense(view: View) {
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
            val defaultCurrency = sharedPreferences.getString("currency", getString(R.string.default_currency))

            if (defaultCurrency == null) {
                Snackbar.make(view, getString(R.string.form_no_currency), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                sheetsViewModel.resetView()
                return
            }

            currency = defaultCurrency
        }

        if (exchangeRate == "") {
            val defaultExchangeRate = sharedPreferences.getString("exchange_rate", getString(R.string.default_exchange_rate))

            if (defaultExchangeRate == null) {
                Snackbar.make(view, getString(R.string.form_no_exchange_rate), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                sheetsViewModel.resetView()
                return
            }

            exchangeRate = defaultExchangeRate
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
            expenseNotesText,
            currency,
            exchangeRate
        )

        if (isInternetConnected(mainActivity)) {
            Log.d(TAG, "internet")

            try {
                Log.d(TAG, "calling sheetsViewModel.addExpenseRowToSheetAsync")
                sheetsViewModel.addExpenseRowToSheetAsync(
                    spreadsheetId,
                    dataSheetName,
                    overviewSheetName,
                    expenseRow
                )
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
                    .setInputData(expenseRow.toWorkData(spreadsheetId, dataSheetName))
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
                    workManagerObserver(workInfo)
                })

            sheetsViewModel.resetView()
            sheetsViewModel.setStatusText("no internet - $expenseItemText request queued")
        }
    }
}