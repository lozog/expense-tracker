package com.lozog.expensetracker.ui.history

import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lozog.expensetracker.*
import com.lozog.expensetracker.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

class HistoryFragment: Fragment() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER HistoryFragment"
    }
    private var _binding: FragmentHistoryBinding? = null
    private lateinit var mainActivity: MainActivity
    private val sheetsViewModel: SheetsViewModel by activityViewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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

        sheetsViewModel.toastEvent.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        addNewRowButton = binding.addNewRowButton
        addNewRowButton.setOnClickListener { view ->
            Log.d(TAG, "new row button clicked")
            val action = HistoryFragmentDirections.actionNavigationHistoryToNewExpenseFragment()
            view.findNavController().navigate(action)
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val barChart = view.findViewById<BarChart>(R.id.bar_chart)

        lifecycleScope.launch {
            val categorySpending =
                (context?.applicationContext as ExpenseTrackerApplication).sheetsRepository.getAllCategorySpending()

            // TODO move these to a setting
            val keysToShow = listOf("Groceries", "Dining Out", "Drinks", "Material Items", "Entertainment")
                .map { it.lowercase() } // Convert to lowercase for case-insensitive comparison
                .toList()

            val sortedCategories = categorySpending.entries
                .filter { it.key.lowercase() in keysToShow }
                .sortedByDescending { it.value }  // Sort by amount in descending order

            // Extract the sorted keys (categories) and values (amounts)
            val categories = sortedCategories.map { it.key.lowercase() }
            val actualAmounts = sortedCategories.map { it.value }
            val targetAmounts = listOf(322f, 543f, 239f, 272f, 190f) // TODO read these dynamically

            // Create bar entries for actual amounts
            val actualEntries = actualAmounts.mapIndexed { index, value ->
                BarEntry(index.toFloat(), value)
            }

            // Create bar entries for target amounts
            val targetEntries = targetAmounts.mapIndexed { index, value ->
                BarEntry(index.toFloat(), value)
            }

            // Create datasets
            val actualDataSet = BarDataSet(actualEntries, "Actual")
            actualDataSet.color = ColorTemplate.COLORFUL_COLORS[0]
//            actualDataSet.setColor(ColorTemplate.COLORFUL_COLORS[0], 128) // Semi-transparent

            val targetDataSet = BarDataSet(targetEntries, "Average")
            targetDataSet.color = ColorTemplate.COLORFUL_COLORS[1]
            targetDataSet.setColor(ColorTemplate.COLORFUL_COLORS[2], 64) // Semi-transparent

            // Combine data into BarData
            val data = BarData(actualDataSet, targetDataSet)
            data.barWidth = 0.4f // Adjust bar width

            // get colour for the text
            val typedValue = TypedValue()
            val theme = view.context.theme
            theme.resolveAttribute(android.R.attr.textColorHint, typedValue, true)
            val textColorHint = if (typedValue.resourceId != 0) {
                ContextCompat.getColor(
                    view.context,
                    typedValue.resourceId
                ) // Resolve the resource ID
            } else {
                typedValue.data // Use the raw color value if no resource ID
            }

            data.setValueTextColor(textColorHint)
            data.setValueTextSize(12f)

            // Configure chart
            barChart.data = data
            barChart.description.isEnabled = false
            barChart.setFitBars(true)

            // Configure X-axis labels
            val xAxis = barChart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.valueFormatter = IndexAxisValueFormatter(categories)
            xAxis.granularity = 1f
            xAxis.setDrawGridLines(false)
            xAxis.textColor = textColorHint

            // Configure Y-axis
            barChart.axisLeft.setDrawGridLines(true)
            barChart.axisLeft.textColor = textColorHint
            barChart.axisRight.isEnabled = false

            barChart.legend.textColor = textColorHint
//            barChart.legend.isEnabled = false

            // Refresh chart
            barChart.invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
