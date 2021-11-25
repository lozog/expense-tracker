package com.lozog.expensetracker.util

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lozog.expensetracker.R
import com.lozog.expensetracker.util.expenserow.ExpenseRow

class HistoryAdapter(private val recentHistory: List<ExpenseRow>): RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    companion object {
        private const val TAG = "HISTORY_ADAPTER"
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val expenseDateTextView: TextView = view.findViewById(R.id.historyExpenseDate)
        val expenseCategoryValueTextView: TextView = view.findViewById(R.id.historyExpenseCategoryValue)
        val expenseItemTextView: TextView = view.findViewById(R.id.historyExpenseItem)
        val expenseTotalTextView: TextView = view.findViewById(R.id.historyExpenseTotal)
    }

    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.history_item, viewGroup, false)

        return ViewHolder(view)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val expenseRow: ExpenseRow = recentHistory[position]
        viewHolder.expenseDateTextView.text = expenseRow.expenseDate
        viewHolder.expenseCategoryValueTextView.text = expenseRow.expenseCategoryValue
        viewHolder.expenseItemTextView.text = expenseRow.expenseItem
        viewHolder.expenseTotalTextView.text = expenseRow.expenseTotal
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = recentHistory.size

}