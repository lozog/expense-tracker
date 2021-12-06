package com.lozog.expensetracker.ui

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.R
import com.lozog.expensetracker.databinding.FragmentSetupBinding


class SetupFragment : Fragment() {
    private var _binding: FragmentSetupBinding? = null
    private lateinit var mainActivity: MainActivity
    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {


        _binding = FragmentSetupBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        return root
    }

    companion object {
        private const val TAG = "EXPENSE_TRACKER SETUP_FRAGMENT"
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment SetupFragment.
         */
        // TODO: Rename and change types and number of parameters
        // @JvmStatic
        // fun newInstance(param1: String, param2: String) =
        //     SetupFragment().apply {
        //         arguments = Bundle().apply {
        //             putString(ARG_PARAM1, param1)
        //             putString(ARG_PARAM2, param2)
        //         }
        //     }
    }
}