package com.lozog.expensetracker.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceFragmentCompat
import com.lozog.expensetracker.*

class SettingsFragment : PreferenceFragmentCompat() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SETTINGS_FRAGMENT"
    }

    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }

    private lateinit var categories: List<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)

        sheetsViewModel.categories.observe(viewLifecycleOwner, {
            Log.d(TAG, "fetched categories")
            categories = it
        })

        sheetsViewModel.fetchCategories()

        val categoriesListPreference = preferenceManager.findPreference<MultiSelectListPreference>("categories_list")

        categoriesListPreference?.setOnPreferenceClickListener { _ ->
            Log.d(TAG, "ok we gonna try to set it")
            Log.d(TAG, categories.toString())
            categoriesListPreference.entries = categories.map { it }.toTypedArray()
            categoriesListPreference.entryValues = categories.map { it }.toTypedArray()
            Log.d(TAG, "we set it")
            return@setOnPreferenceClickListener false
        }

        return view
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}