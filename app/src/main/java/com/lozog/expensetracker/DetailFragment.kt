package com.lozog.expensetracker

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import com.lozog.expensetracker.databinding.FragmentFormBinding
import com.lozog.expensetracker.util.expenserow.ExpenseRow

private const val ROW_PARAM = "row"

class DetailFragment : Fragment() {
    private var row: Int = 0
    private var _binding: FragmentFormBinding? = null
    private val binding get() = _binding!!
    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((context?.applicationContext as ExpenseTrackerApplication).sheetsRepository)
    }
    private lateinit var expenseRow: ExpenseRow

    companion object {
        private const val TAG = "DETAIL_FRAGMENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            row = it.getInt(ROW_PARAM)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFormBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val detailText = binding.statusText

        sheetsViewModel.getExpenseRowByRow(row)

        sheetsViewModel.detailExpenseRow.observe(viewLifecycleOwner, {
            expenseRow = it
            detailText.text = expenseRow.toString()
        })

        return root
    }
}