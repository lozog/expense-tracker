package com.lozog.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.api.services.sheets.v4.SheetsScopes
import com.lozog.expensetracker.databinding.MainActivityBinding // generated based on xml file name
import com.lozog.expensetracker.ui.account.AccountViewModel
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent

class MainActivity : AppCompatActivity() {

    private val expenseTrackerApplication: ExpenseTrackerApplication
        get() = application as ExpenseTrackerApplication
    private lateinit var binding: MainActivityBinding
    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView

    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory(expenseTrackerApplication.sheetsRepository, expenseTrackerApplication.applicationScope)
    }

    /********** GOOGLE SIGN-IN **********/
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    lateinit var startForSignInResult: ActivityResultLauncher<Intent>
    lateinit var startForRequestAuthorizationResult: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "EXPENSE_TRACKER MainActivity"

        /********** GOOGLE STATUS CODES **********/
        const val RC_SIGN_IN: Int = 0
        const val RC_REQUEST_AUTHORIZATION: Int = 1

        const val QUEUED_REQUEST_NOTIFICATION_CHANNEL_ID = "queued_request"
    }

    /********** OVERRIDE METHODS **********/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = GoogleSignInOptions
                .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        createNotificationChannel()

        // set up bottom nav
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bottomNav = binding.bottomNav

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        navController = navHostFragment.navController

        // each view passed here will be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_history,
                R.id.navigation_settings,
                R.id.navigation_account
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav.setupWithNavController(navController)

        KeyboardVisibilityEvent.setEventListener(this) { isOpen ->
            when (isOpen) {
                true -> bottomNav.visibility = View.GONE
                false -> bottomNav.visibility = View.VISIBLE
            }
        }

        if (intent.getStringExtra("navigateTo") == "ExpenseFragment") {
            navigateToExpenseFragmentWithArgs(intent)
        }

        startForSignInResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(RC_SIGN_IN, result)
        }
        startForRequestAuthorizationResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(RC_REQUEST_AUTHORIZATION, result)
        }

        updateHistory()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_bar, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_refresh -> {
            updateHistory()
            true
        }

        else -> {
            // The user's action isn't recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getStringExtra("navigateTo") == "ExpenseFragment") {
            navigateToExpenseFragmentWithArgs(intent)
        }
    }

    private fun navigateToExpenseFragmentWithArgs(intent: Intent) {
        // Extract data from the intent
        val amount = intent.getStringExtra("amount")
        val notificationId = intent.getIntExtra("notification_id", -1)

        // Create a bundle with the data
        val bundle = Bundle().apply {
            putString("amount", amount)
            putInt("notification_id", notificationId)
        }

        navController.navigate(R.id.navigation_new_expense, bundle)
    }

    private fun onActivityResult(requestCode: Int, result: ActivityResult) {
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task)
        }
        else if (requestCode == RC_REQUEST_AUTHORIZATION) {
            Log.e(TAG, "unhandled authorization request: ${result.data}")
        }
    }

    private fun createNotificationChannel() {
        val name = getString(R.string.notification_queued_requests_channel_name)
        val descriptionText = getString(R.string.notification_queued_requests_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(QUEUED_REQUEST_NOTIFICATION_CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun updateHistory() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("data_sheet_name", null)

        if (spreadsheetId == null) {
            Toast.makeText(this, getString(R.string.form_no_spreadsheet_id), Toast.LENGTH_SHORT).show()
            return
        }

        if (sheetName == null) {
            Toast.makeText(this, getString(R.string.form_no_data_sheet_name), Toast.LENGTH_SHORT).show()
            return
        }

        sheetsViewModel.getRecentExpenseHistory()
    }

    /********** GOOGLE SIGN-IN METHODS **********/
    // TODO: move to AccountFragment

    fun signInButtonClick(view: View) {
        when (view.id) {
            R.id.signInButton -> {
                val signInIntent = mGoogleSignInClient.signInIntent
                startForSignInResult.launch(signInIntent)
            }
        }
    }

    fun signOutButtonClick(view: View) {
        when (view.id) {
            R.id.signOutButton -> {
                mGoogleSignInClient.signOut()
                    .addOnCompleteListener(this) {
                        Log.d(TAG, "signed out")

                        val accountViewModel: AccountViewModel by viewModels()
                        accountViewModel.setSignInStatus("not signed in")

                        expenseTrackerApplication.googleAccount = null
                        expenseTrackerApplication.spreadsheetService = null
                        expenseTrackerApplication.driveService = null
                    }
            }
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account: GoogleSignInAccount? = completedTask.getResult(ApiException::class.java)
            account ?: return

            expenseTrackerApplication.onSignInSuccess(account)
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.d(TAG, "signInResult: failed. code: ${e.statusCode}")
        }
    }
}
