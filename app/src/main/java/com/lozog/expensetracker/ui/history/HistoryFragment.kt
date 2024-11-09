package com.lozog.expensetracker.ui.history

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentHistoryBinding
import com.lozog.expensetracker.util.SheetsStatus

class HistoryFragment: Fragment() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER HISTORY_FRAGMENT"
    }
    private var _binding: FragmentHistoryBinding? = null
    private lateinit var mainActivity: MainActivity
    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var statusText: TextView
    private lateinit var addNewRowButton: FloatingActionButton
    private lateinit var recentHistoryView: RecyclerView
    private var historyAdapter = HistoryAdapter(listOf()) { }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        recentHistoryView = binding.recentHistory
        recentHistoryView.layoutManager = LinearLayoutManager(mainActivity)
        recentHistoryView.adapter = historyAdapter

        // we set this here so it has the latest recentHistory length (which can be updated via prefs)
        sheetsViewModel.setRecentHistory()

        sheetsViewModel.recentHistory.observe(viewLifecycleOwner) {
            historyAdapter = HistoryAdapter(it) { expenseRow ->
                sheetsViewModel.deleteRowAsync(expenseRow.id)
            }
            recentHistoryView.adapter = historyAdapter
        }

        statusText = binding.statusText
        sheetsViewModel.statusText.observe(viewLifecycleOwner) {
            statusText.text = it
        }

        addNewRowButton = binding.addNewRowButton
        addNewRowButton.setOnClickListener { view ->
            Log.d(TAG, "new row button clicked")
            val action = HistoryFragmentDirections.actionNavigationHistoryToNewExpenseFragment()
            view.findNavController().navigate(action)
        }

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
