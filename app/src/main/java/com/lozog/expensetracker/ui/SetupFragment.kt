package com.lozog.expensetracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentSetupBinding
import com.lozog.expensetracker.util.SheetsStatus
import kotlinx.android.synthetic.main.fragment_form.*


class SetupFragment : Fragment() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SETUP_FRAGMENT"
    }

    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }

    private var _binding: FragmentSetupBinding? = null
    private lateinit var mainActivity: MainActivity
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var statusText: TextView
    private lateinit var spreadsheetIdButton: Button
    private lateinit var overviewSheetButton: Button
    private lateinit var dataSheetButton: Button
    private lateinit var monthlySummarySheetButton: Button
    private lateinit var monthColumnButton: Button
    private lateinit var categoriesButton: Button
    private lateinit var spreadsheets: List<Map<String, String>>
    private lateinit var sheets: List<Map<String, String>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        sheetsViewModel.fetchSpreadsheets()

        statusText = binding.statusText
        spreadsheetIdButton = binding.spreadsheetIdButton
        overviewSheetButton = binding.overviewSheetButton
        dataSheetButton = binding.dataSheetButton
        monthlySummarySheetButton = binding.monthlySummarySheetButton
        monthColumnButton = binding.monthColumnButton
        categoriesButton = binding.categoriesButton

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)

        if (spreadsheetId != null) {
            sheetsViewModel.fetchSheets(spreadsheetId)
        }

        spreadsheetIdButton.setOnClickListener{
            if (!this::spreadsheets.isInitialized) {
                return@setOnClickListener
            }

            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle("Select Budget Spreadsheet")
            val spreadsheetNames = spreadsheets.map { it["name"] ?: "name not found" }.toTypedArray()
            builder.setItems(spreadsheetNames) { _, which ->
                Log.d(TAG, "chose spreadsheet ${spreadsheets[which]["name"]} with id ${spreadsheets[which]["id"]}")
                // set preference
                val preferenceEditor = sharedPreferences.edit()
                val spreadsheetId = spreadsheets[which]["id"]!!
                preferenceEditor.putString("google_spreadsheet_id", spreadsheetId)
                preferenceEditor.apply()
                spreadsheetIdButton.text = spreadsheets[which]["name"]
                sheetsViewModel.fetchSheets(spreadsheetId)
            }
            val dialog = builder.create()
            dialog.show()
        }

        overviewSheetButton.setOnClickListener {
            if (!this::sheets.isInitialized) {
                return@setOnClickListener
            }

            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle("Select Overview sheet")
            val sheetNames = sheets.map { it["title"] ?: "title not found" }.toTypedArray()
            builder.setItems(sheetNames) { _, which ->
                Log.d(TAG, "chose sheet ${sheets[which]["title"]} with id ${sheets[which]["id"]}")

                // set preferences
                val preferenceEditor = sharedPreferences.edit()
                val sheetId = sheets[which]["id"]!!
                val title = sheets[which]["title"]!!
                preferenceEditor.putString("overview_sheet_name", title)
                preferenceEditor.putString("overview_sheet_id", sheetId)
                preferenceEditor.apply()

                overviewSheetButton.text = sheets[which]["title"]

                sheetsViewModel.findMonthColumns()
            }
            val dialog = builder.create()
            dialog.show()
        }

        dataSheetButton.setOnClickListener {
            if (!this::sheets.isInitialized) {
                return@setOnClickListener
            }

            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle("Select Data sheet")
            val sheetNames = sheets.map { it["title"] ?: "title not found" }.toTypedArray()
            builder.setItems(sheetNames) { _, which ->
                Log.d(TAG, "chose sheet ${sheets[which]["title"]} with id ${sheets[which]["id"]}")

                // set preferences
                val preferenceEditor = sharedPreferences.edit()
                val sheetId = sheets[which]["id"]!!
                val title = sheets[which]["title"]!!
                preferenceEditor.putString("data_sheet_name", title)
                preferenceEditor.putString("data_sheet_id", sheetId)
                preferenceEditor.apply()

                dataSheetButton.text = sheets[which]["title"]
            }
            val dialog = builder.create()
            dialog.show()
        }

        monthlySummarySheetButton.setOnClickListener {
            if (!this::sheets.isInitialized) {
                return@setOnClickListener
            }

            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle("Select Monthly Summary sheet")
            val sheetNames = sheets.map { it["title"] ?: "title not found" }.toTypedArray()
            builder.setItems(sheetNames) { _, which ->
                Log.d(TAG, "chose sheet ${sheets[which]["title"]} with id ${sheets[which]["id"]}")

                // set preferences
                val preferenceEditor = sharedPreferences.edit()
                val sheetId = sheets[which]["id"]!!
                val title = sheets[which]["title"]!!
                preferenceEditor.putString("monthly_summary_sheet_name", title)
                preferenceEditor.putString("monthly_summary_sheet_id", sheetId)
                preferenceEditor.apply()

                monthlySummarySheetButton.text = sheets[which]["title"]
            }
            val dialog = builder.create()
            dialog.show()
        }

        monthColumnButton.setOnClickListener {
            if (sharedPreferences.getString("overview_sheet_name", null) == null) {
                return@setOnClickListener
            }

            sheetsViewModel.findMonthColumns()
        }

        categoriesButton.setOnClickListener {
            if (sharedPreferences.getString("overview_sheet_name", null) == null) {
                return@setOnClickListener
            }

            sheetsViewModel.findCategories()
        }

        sheetsViewModel.status.observe(viewLifecycleOwner, {
            when (it) {
                SheetsStatus.IN_PROGRESS -> statusText.text = "loading..."
                SheetsStatus.DONE -> statusText.text = "done"
                else -> statusText.text = "error"
            }
        })

        sheetsViewModel.error.observe(viewLifecycleOwner, {
            mainActivity.startForRequestAuthorizationResult.launch(it.intent)
        })

        sheetsViewModel.spreadsheets.observe(viewLifecycleOwner, { files ->
            spreadsheets = files.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name
                )
            }
        })

        sheetsViewModel.sheets.observe(viewLifecycleOwner, { sheets ->
            this.sheets = sheets.map {
                mapOf(
                    "id" to it.properties.sheetId.toString(),
                    "title" to it.properties.title.toString()
                )
            }
        })

        return root
    }
}