package com.lozog.expensetracker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.SignInButton
import com.lozog.expensetracker.MainActivity
import com.lozog.expensetracker.R
import com.lozog.expensetracker.databinding.FragmentAccountBinding
import java.text.SimpleDateFormat
import java.util.*

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
        private const val TAG = "SIGN_IN_FRAGMENT"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
//        accountViewModel = ViewModelProvider(this).get(AccountViewModel::class.java)

        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        val root: View = binding.root
        mainActivity = activity as MainActivity

        signInButton = binding.signInButton
        signOutButton = binding.signOutButton
        accountInfo = binding.accountInfo

//        signInButton.setOnClickListener{view ->
//            when (view.id) {
//                R.id.signInButton -> {
//                    val signInIntent = mainActivity.mGoogleSignInClient.signInIntent
//                    startActivityForResult(signInIntent, MainActivity.RC_SIGN_IN)
//                }
//            }
//        }
//
//        signOutButton.setOnClickListener{view ->
//            when (view.id) {
//                R.id.signOutButton -> {
//                    mainActivity.mGoogleSignInClient.signOut()
//                        .addOnCompleteListener(mainActivity) {
//                            Log.d(TAG, "signing out")
//                            mainActivity.finish()
//                            mainActivity.overridePendingTransition(0, 0)
//                            startActivity(mainActivity.intent)
//                            mainActivity.overridePendingTransition(0, 0)
//                        }
//                }
//            }
//        }

        signInButton.setOnClickListener{view ->
            mainActivity.signInButtonClick(view)
        }

        signOutButton.setOnClickListener{view ->
            mainActivity.signOutButtonClick(view)
        }

        accountViewModel.text.observe(viewLifecycleOwner, {
            Log.d(TAG, "observing: $it")
            accountInfo.text = it
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

//    fun onSignInSuccess() {
//        signInButton.visibility = View.GONE
//        signOutButton.visibility = View.VISIBLE
//    }
}