package com.lozog.expensetracker.ui.categories

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentCategoriesBinding
import com.lozog.expensetracker.ui.history.HistoryFragment

class CategoriesFragment: Fragment() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER CATEGORIES_FRAGMENT"
    }
    private var _binding: FragmentCategoriesBinding? = null
    private lateinit var mainActivity: MainActivity
    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var updateCategoriesButton: Button
    private lateinit var addNewRowButton: FloatingActionButton
    private lateinit var categoriesListView: RecyclerView
    private var categoriesAdapter = CategoriesAdapter(listOf("foo", "bar")) { }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        categoriesListView = binding.categoriesList
        categoriesListView.layoutManager = LinearLayoutManager(mainActivity)
        categoriesListView.adapter = categoriesAdapter

        updateCategoriesButton = binding.updateCategoriesButton
        updateCategoriesButton.setOnClickListener{view ->
            updateCategories(view)
        }

        addNewRowButton = binding.addNewRowButton
        addNewRowButton.setOnClickListener { view ->
            val action = CategoriesFragmentDirections.actionNavigationToNewExpenseFragment()
            view.findNavController().navigate(action)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateCategories(view: View) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mainActivity)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

        if (spreadsheetId == null) {
            Snackbar.make(view, getString(R.string.form_no_spreadsheet_id), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        if (sheetName == null) {
            Snackbar.make(view, getString(R.string.form_no_data_sheet_name), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        try {
            Log.d(TAG, "calling sheetsViewModel.fetchMonthSpendingAsync()")
            sheetsViewModel.fetchMonthSpendingAsync()
        } catch (e: Exception) {
            Log.d(TAG, "exception: $e")
            sheetsViewModel.setStatusText(e.toString())
        }
    }
}