package com.lozog.expensetracker.ui.categories

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.lozog.expensetracker.R
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.NumberFormat

class CategoriesAdapter(
    private val categories: List<String>,
    private val onItemClicked: (ExpenseRow) -> Unit
): RecyclerView.Adapter<CategoriesAdapter.ViewHolder>() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER CATEGORIES_ADAPTER"
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryNameTextView: TextView = view.findViewById(R.id.categoryName)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.category_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val categoryName = categories[position]
        viewHolder.categoryNameTextView.text = categoryName
//        val expenseRow: ExpenseRow = recentHistory[position]
//
//        viewHolder.expenseItemTextView.text = expenseRow.expenseItem
//
//        val numberFormat = NumberFormat.getCurrencyInstance()
//        numberFormat.maximumFractionDigits = 2
//
//        try {
//            viewHolder.expenseTotalTextView.text = numberFormat.format(expenseRow.expenseTotal.toFloat())
//        } catch (e: Exception) {
//            viewHolder.expenseTotalTextView.text = expenseRow.expenseTotal
//        }

//        viewHolder.detailButton.setOnClickListener {
////            Log.d(TAG, "clicked row ${expenseRow.row}")
//            val action = HistoryFragmentDirections.actionNavigationHistoryToExpenseFragment(expenseRow.row)
//            it.findNavController().navigate(action)
//        }
//
//        viewHolder.deleteButton.setOnClickListener {
//            Log.d(TAG, "deleting row ${expenseRow.row}")
//            onItemClicked(expenseRow)
//        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = categories.size

}