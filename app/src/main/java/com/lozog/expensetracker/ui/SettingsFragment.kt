package com.lozog.expensetracker.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.lozog.expensetracker.R

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}