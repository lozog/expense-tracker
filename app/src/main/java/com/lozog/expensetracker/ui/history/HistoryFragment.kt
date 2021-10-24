package com.lozog.expensetracker.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.databinding.FragmentHistoryBinding

class HistoryFragment: Fragment() {
    companion object {
        private const val TAG = "HISTORY_FRAGMENT"
    }

    private var _binding: FragmentHistoryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        val root: View = binding.root
//        mainActivity = activity as MainActivity

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}