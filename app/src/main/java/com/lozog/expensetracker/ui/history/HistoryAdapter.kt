package com.lozog.expensetracker.ui.history

import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.lozog.expensetracker.R
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.NumberFormat

class HistoryAdapter(
    private val recentHistory: List<ExpenseRow>,
    private val onClickDeleteRow: (ExpenseRow) -> Unit
): RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER HistoryAdapter"
    }
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deleteButton: Button = view.findViewById(R.id.deleteButton)

        val expenseItemTextView: TextView = view.findViewById(R.id.historyExpenseItem)
        val expenseTotalTextView: TextView = view.findViewById(R.id.historyExpenseTotal)
        val expenseCategoryTextView: TextView = view.findViewById(R.id.historyExpenseCategory)
        val expenseDateTextView: TextView = view.findViewById(R.id.historyExpenseDate)
        val syncIcon: ImageView = view.findViewById(R.id.syncIcon)
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

        viewHolder.expenseItemTextView.text = expenseRow.expenseItem
        viewHolder.expenseCategoryTextView.text = expenseRow.expenseCategoryValue
        viewHolder.expenseDateTextView.text = expenseRow.expenseDate.dropLast(6) // removes ", YYYY" from datestring

        val numberFormat = NumberFormat.getCurrencyInstance()
        numberFormat.maximumFractionDigits = 2

        if (position == 0) {
            Log.d(TAG, "looking at first item: ${expenseRow.expenseAmount}, ${expenseRow.expenseAmountOthers}, ${expenseRow.exchangeRate}")
        }

        val expenseAmountFinal = ((expenseRow.expenseAmount.toFloatOrNull() ?: 0.0f) - (expenseRow.expenseAmountOthers.toFloatOrNull() ?: 0.0f)) * (expenseRow.exchangeRate.toFloatOrNull() ?: 1.0f)
        viewHolder.expenseTotalTextView.text = numberFormat.format(expenseAmountFinal)

        val shouldShowSyncIcon = expenseRow.syncStatus == ExpenseRow.STATUS_PENDING
        viewHolder.syncIcon.visibility = if (shouldShowSyncIcon) View.VISIBLE else View.GONE

        viewHolder.itemView.setOnClickListener {
//            Log.d(TAG, "clicked row ${expenseRow.row}")
            val action = HistoryFragmentDirections.actionNavigationHistoryToExpenseFragment(expenseRow.id)
            it.findNavController().navigate(action)
        }

        if (position % 2 == 1) {
            // TODO: get colour from theme
            viewHolder.itemView.setBackgroundColor(Color.parseColor("#404040"))
        }

        viewHolder.deleteButton.setOnClickListener {
            Log.d(TAG, "deleting row ${expenseRow.row}")
            onClickDeleteRow(expenseRow)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = recentHistory.size

}