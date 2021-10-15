package com.lozog.expensetracker.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.common.SignInButton
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.databinding.FragmentAccountBinding

class AccountFragment : Fragment() {
    private val accountViewModel: AccountViewModel by activityViewModels()
    private var _binding: FragmentAccountBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private lateinit var mainActivity: MainActivity

    private lateinit var signInButton: SignInButton
    private lateinit var signOutButton: Button
    private lateinit var accountInfo: TextView

    companion object {
        private const val TAG = "ACCOUNT_FRAGMENT"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        signInButton = binding.signInButton
        signOutButton = binding.signOutButton
        accountInfo = binding.accountInfo

        signInButton.setOnClickListener{view ->
            mainActivity.signInButtonClick(view)
        }

        signOutButton.setOnClickListener{view ->
            mainActivity.signOutButtonClick(view)
        }

        accountViewModel.signInStatus.observe(viewLifecycleOwner, {
//            Log.d(TAG, "observing: $it")
            accountInfo.text = it
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}