package com.lozog.expensetracker.ui

import android.R
import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListAdapter
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.model.File
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentSetupBinding
import com.lozog.expensetracker.ui.history.HistoryAdapter
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
    private lateinit var spreadsheets: List<Map<String, String>>

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

        val sharedPreferencesEditor = PreferenceManager.getDefaultSharedPreferences(mainActivity).edit()

        spreadsheetIdButton.setOnClickListener{
            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle("Select Budget Spreadsheet")
            val spreadsheetNames = spreadsheets.map { it["name"] ?: "name not found" }.toTypedArray()
            builder.setItems(spreadsheetNames) { _, which ->
                Log.d(TAG, "chose spreadsheet ${spreadsheets[which]["name"]} with id ${spreadsheets[which]["id"]}")
                // set preference
                sharedPreferencesEditor.putString("google_spreadsheet_id", spreadsheets[which]["id"])
                sharedPreferencesEditor.apply()
                spreadsheetIdButton.text = spreadsheets[which]["name"]
            }
            val dialog = builder.create()
            dialog.show()
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

        return root
    }
}