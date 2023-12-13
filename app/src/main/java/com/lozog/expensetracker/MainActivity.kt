package com.lozog.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
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
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.lozog.expensetracker.databinding.MainActivityBinding // generated based on xml file name
import com.lozog.expensetracker.ui.account.AccountViewModel
import net.yslibrary.android.keyboardvisibilityevent.KeyboardVisibilityEvent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding
    private lateinit var navController: NavController

    private lateinit var bottomNav: BottomNavigationView

    /********** GOOGLE SIGN-IN **********/
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    lateinit var startForSignInResult: ActivityResultLauncher<Intent>
    lateinit var startForRequestAuthorizationResult: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "EXPENSE_TRACKER MAIN_ACTIVITY"

        /********** GOOGLE STATUS CODES **********/
        const val RC_SIGN_IN: Int = 0
        const val RC_REQUEST_AUTHORIZATION: Int = 1

        private var JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
        private var SCOPES = listOf(
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE_METADATA_READONLY
        )

        const val QUEUED_REQUEST_NOTIFICATION_CHANNEL_ID = "queued_request"
    }

    /********** OVERRIDE METHODS **********/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        (applicationContext as ExpenseTrackerApplication).sheetsRepository.setPreferences(sharedPreferences)
        (applicationContext as ExpenseTrackerApplication).sheetsRepository.checkInternetConnectivityAsync()

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
                R.id.navigation_new_expense,
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

        startForSignInResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(RC_SIGN_IN, result)
        }
        startForRequestAuthorizationResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onActivityResult(RC_REQUEST_AUTHORIZATION, result)
        }
    }

    override fun onStart() {
        super.onStart()

        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        // TODO: just check GoogleSheetsInterface.googleAccount != null?
        val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            onSignInSuccess(account)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
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
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    fun showKeyboard(view: View) {

        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, InputMethodManager.HIDE_NOT_ALWAYS)

        // inputManager.showSoftInput doesn't work, for some reason. RIP
        // val resultReceiver = ResultReceiver(Handler())
        // inputManager.showSoftInput(view, InputMethodManager.SHOW_FORCED, resultReceiver)
    }

    fun hideKeyboard(view: View) {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            view.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
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

                        (applicationContext as ExpenseTrackerApplication).googleAccount = null
                        (applicationContext as ExpenseTrackerApplication).spreadsheetService = null
                        (applicationContext as ExpenseTrackerApplication).driveService = null
                    }
            }
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account: GoogleSignInAccount? = completedTask.getResult(ApiException::class.java)
            account ?: return

            onSignInSuccess(account)
        } catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.d(TAG, "signInResult: failed. code: ${e.statusCode}")
        }
    }

    private fun onSignInSuccess(account: GoogleSignInAccount) {
        Log.d(TAG, "signed into account: ${account.email}")

        val httpTransport = NetHttpTransport()
        val credential = GoogleAccountCredential.usingOAuth2(this, SCOPES)
        credential.selectedAccount = account.account

        // get sheet service object
        val spreadsheetService: Sheets = Sheets.Builder(httpTransport, JSON_FACTORY, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        val driveService: Drive = Drive.Builder(httpTransport, JSON_FACTORY, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        (applicationContext as ExpenseTrackerApplication).googleAccount = account
        (applicationContext as ExpenseTrackerApplication).spreadsheetService = spreadsheetService
        (applicationContext as ExpenseTrackerApplication).driveService = driveService

        val accountViewModel: AccountViewModel by viewModels()
        accountViewModel.setSignInStatus("signed into account: ${account.email}") // TODO: don't put UI string into the viewmodel
    }
}
