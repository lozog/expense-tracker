package com.lozog.expensetracker.ui.categories

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lozog.expensetracker.ExpenseTrackerApplication
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.SheetsViewModel
import com.lozog.expensetracker.SheetsViewModelFactory
import com.lozog.expensetracker.databinding.FragmentCategoriesBinding
import com.lozog.expensetracker.ui.history.HistoryFragmentDirections

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
}