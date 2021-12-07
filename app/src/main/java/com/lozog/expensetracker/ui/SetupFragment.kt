package com.lozog.expensetracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.R
import com.lozog.expensetracker.SheetsRepository
import com.lozog.expensetracker.databinding.FragmentSetupBinding


class SetupFragment : Fragment() {
    companion object {
        private const val TAG = "EXPENSE_TRACKER SETUP_FRAGMENT"
    }

    private var _binding: FragmentSetupBinding? = null
    private lateinit var mainActivity: MainActivity
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var spreadsheetIdButton: Button
    private lateinit var overviewSheetButton: Button
    private lateinit var dataSheetButton: Button
    private lateinit var monthlySummarySheetButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        spreadsheetIdButton = binding.spreadsheetIdButton
        overviewSheetButton = binding.overviewSheetButton
        dataSheetButton = binding.dataSheetButton
        monthlySummarySheetButton = binding.monthlySummarySheetButton

        spreadsheetIdButton.setOnClickListener{
            val builder = AlertDialog.Builder(mainActivity)
            builder.setTitle("Select Budget Spreadsheet")
            builder.setItems(R.array.categories) {_, which ->
                spreadsheetIdButton.text = SheetsRepository.CATEGORIES[which]
            }
            val dialog = builder.create()
            dialog.show()
        }

        return root
    }
}