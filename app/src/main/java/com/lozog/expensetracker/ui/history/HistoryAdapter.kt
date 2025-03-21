package com.lozog.expensetracker.ui.history

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
import com.lozog.expensetracker.util.CalendarHelper
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

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

        val numberFormat = NumberFormat.getCurrencyInstance()
        numberFormat.maximumFractionDigits = 2

        val expenseAmountFinal = ((expenseRow.expenseAmount.toFloatOrNull() ?: 0.0f) - (expenseRow.expenseAmountOthers.toFloatOrNull() ?: 0.0f)) * (expenseRow.exchangeRate.toFloatOrNull() ?: 1.0f)
        viewHolder.expenseTotalTextView.text = numberFormat.format(expenseAmountFinal)

        val shouldShowSyncIcon = expenseRow.syncStatus == ExpenseRow.STATUS_PENDING
        viewHolder.syncIcon.visibility = if (shouldShowSyncIcon) View.VISIBLE else View.GONE

        val dateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        val expenseDateFormatted = CalendarHelper.parseDatestring(expenseRow.expenseDate)?.format(dateFormat) ?: "--"
        viewHolder.expenseDateTextView.text = expenseDateFormatted

        viewHolder.itemView.setOnClickListener {
            val action = HistoryFragmentDirections.actionNavigationHistoryToExpenseFragment(expenseRow.id)
            it.findNavController().navigate(action)
        }

        if (position % 2 == 1) {
            viewHolder.itemView.setBackgroundColor(viewHolder.itemView.context.resources.getColor(R.color.colorSecondaryRow, null))
        } else {
            viewHolder.itemView.setBackgroundColor(0)
        }

        viewHolder.deleteButton.setOnClickListener {
            Log.d(TAG, "deleting row ${expenseRow.row}")
            onClickDeleteRow(expenseRow)
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = recentHistory.size

}