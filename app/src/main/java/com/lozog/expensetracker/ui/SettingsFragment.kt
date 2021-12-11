package com.lozog.expensetracker.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentSetupBinding

class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SETTINGS_FRAGMENT"
    }

    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }

    private var _binding: FragmentSetupBinding? = null
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var spreadsheets: List<Map<String, String>>
    // private lateinit var sheets: List<Map<String, String>>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        sheetsViewModel.fetchSpreadsheets()

        sheetsViewModel.spreadsheets.observe(viewLifecycleOwner, { files ->
            Log.d(TAG, "fetched spreadsheets")
            spreadsheets = files.map {
                mapOf(
                    "id" to it.id,
                    "name" to it.name
                )
            }
        })

        val spreadsheetIdPreference = preferenceManager.findPreference<ListPreference>("google_spreadsheet_id")

        spreadsheetIdPreference?.setOnPreferenceClickListener { _ ->
            Log.d(TAG, "ok we gonna try to set it")
            Log.d(TAG, spreadsheets.toString())
            spreadsheetIdPreference.entries = spreadsheets.map { it["name"] }.toTypedArray()
            spreadsheetIdPreference.entryValues = spreadsheets.map { it["id"] }.toTypedArray()
            Log.d(TAG, "we set it")
            return@setOnPreferenceClickListener false
        }

        return view
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}