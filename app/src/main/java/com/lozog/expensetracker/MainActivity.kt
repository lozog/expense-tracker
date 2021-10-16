package com.lozog.expensetracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.room.Room
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*
import com.lozog.expensetracker.databinding.MainActivityBinding // generated based on xml file name
import com.lozog.expensetracker.ui.AccountViewModel


class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding

    /********** GOOGLE SIGN-IN **********/
    lateinit var mGoogleSignInClient: GoogleSignInClient
    lateinit var startForSignInResult: ActivityResultLauncher<Intent>
    lateinit var startForRequestAuthorizationResult: ActivityResultLauncher<Intent>

    /********** ROOM DB **********/
    private lateinit var addRowRequestDB: AddRowRequestDB
    private lateinit var networkReceiver: NetworkReceiver

    /********** CONCURRENCY **********/
    private val parentJob = Job()
    val coroutineScope = CoroutineScope(Dispatchers.Main + parentJob)

    // TODO: dynamically find category cell
    private val CATEGORY_ROW_MAP = mapOf(
        "Groceries" to "20",
        "Dining Out" to "21",
        "Drinks" to "22",
        "Material Items" to "23",
        "Entertainment" to "24",
        "Transit" to "25",
        "Personal/Medical" to "26",
        "Gifts" to "27",
        "Travel" to "28",
        "Miscellaneous" to "29",
        "Film" to "30",
        "Household" to "31",
        "Other Income" to "5"
    )
    val CATEGORIES = arrayOf(
        "Groceries",
        "Dining Out",
        "Drinks",
        "Material Items",
        "Entertainment",
        "Transit",
        "Personal/Medical",
        "Gifts",
        "Travel",
        "Miscellaneous",
        "Film",
        "Household",
        "Other Income"
    )

    companion object {
        private const val TAG = "MAIN_ACTIVITY"

        /********** GOOGLE STATUS CODES **********/
        const val RC_SIGN_IN: Int = 0
        const val RC_REQUEST_AUTHORIZATION: Int = 1

        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"

        private var JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
        private var SCOPES:List<String> = Collections.singletonList(SheetsScopes.SPREADSHEETS)

        private const val QUEUED_REQUEST_NOTIFICATION_CHANNEL_ID = "queued_request"

        // January -> column C, etc
        // TODO: dynamically find month columns
        private val MONTH_COLUMNS = listOf(
            "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"
        )
    }

    class NotSignedInException : Exception()

    /********** OVERRIDE METHODS **********/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(SheetsScopes.SPREADSHEETS))
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        addRowRequestDB = Room.databaseBuilder(
            applicationContext,
            AddRowRequestDB::class.java, "add-row-request-db"
        ).build()

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        networkReceiver = NetworkReceiver()
        registerReceiver(networkReceiver, filter)

        createNotificationChannel()

        // set up bottom nav
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val bottomNav: BottomNavigationView = binding.bottomNav

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_form, R.id.navigation_settings
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav.setupWithNavController(navController)

//        navController.addOnDestinationChangedListener { _, destination, _ ->
//            Log.d(TAG, "destination: $destination")
//        }

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

    override fun onResume() {
        super.onResume()

        sendQueuedRequests()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Unregisters BroadcastReceiver when app is destroyed.
        this.unregisterReceiver(networkReceiver)
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

    /********** GOOGLE SIGN-IN METHODS **********/

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

                        GoogleSheetsInterface.googleAccount = null
                        GoogleSheetsInterface.spreadsheetService = null
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
        val sheetService: Sheets = Sheets.Builder(httpTransport, JSON_FACTORY, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        GoogleSheetsInterface.googleAccount = account
        GoogleSheetsInterface.spreadsheetService = sheetService

        val accountViewModel: AccountViewModel by viewModels()
        accountViewModel.setSignInStatus("signed into account: ${account.email}")
    }

    /********** GOOGLE SHEETS METHODS **********/

    fun addExpenseRowToSheetAsync(
        spreadsheetId: String,
        sheetName: String,
        expenseDate: String,
        expenseItem: String,
        expenseCategoryValue: String,
        expenseAmount: String,
        expenseAmountOthers: String,
        expenseNotes: String,
        currency: String,
        exchangeRate: String
    ): Deferred<AppendValuesResponse> = coroutineScope.async (Dispatchers.IO) {
        Log.d(TAG, "addExpenseRowToSheetAsync")

        if (GoogleSheetsInterface.spreadsheetService == null) {
            throw NotSignedInException()
        }

        val nextRow = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().get(spreadsheetId, sheetName).execute().getValues().size + 1
        val expenseTotal = "=(\$D$nextRow - \$E$nextRow)*IF(NOT(ISBLANK(\$I$nextRow)), \$I$nextRow, 1)"

        val rowData = mutableListOf(mutableListOf(
            expenseDate, expenseItem, expenseCategoryValue, expenseAmount, expenseAmountOthers, expenseTotal, expenseNotes, currency, exchangeRate
        ))
        val requestBody = ValueRange()
        requestBody.setValues(rowData as List<MutableList<String>>?)

        val request = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().append(spreadsheetId, sheetName, requestBody)
        request.valueInputOption = SHEETS_VALUE_INPUT_OPTION
        request.insertDataOption = SHEETS_INSERT_DATA_OPTION

        return@async request.execute()
    }

    fun getCategorySpendingAsync(
        spreadsheetId: String,
        expenseCategoryValue: String
    ): Deferred<String> = coroutineScope.async (Dispatchers.IO) {
        Log.d(TAG, "getCategorySpending")

        val curMonthColumn = MONTH_COLUMNS[Calendar.getInstance().get(Calendar.MONTH)]

        val categoryCell = CATEGORY_ROW_MAP[expenseCategoryValue]

        if (categoryCell == null) {
            Log.e(TAG, "Category $expenseCategoryValue not found")
            return@async "$??"
        }

        val overviewSheetName = "Overview" // TODO: move to user pref. or dynamically read sheet

        val categorySpendingCell = "'$overviewSheetName'!$curMonthColumn$categoryCell"
        val data = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().get(spreadsheetId, categorySpendingCell).execute().getValues()

        val spentSoFar = data[0][0].toString()
//        Log.d(TAG, "$spentSoFar spent so far in $expenseCategoryValue")

        return@async spentSoFar
    }

    /********** HELPER METHODS **********/

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

    private fun isInternetConnected(): Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    fun insertRowIntoDBAsync(
        addRowRequest: AddRowRequest
    ): Deferred<List<AddRowRequest>> = coroutineScope.async (Dispatchers.IO) {
//        Log.d(TAG, "insertRowIntoDBAsync")

        addRowRequestDB.addRowRequestDao().insert(addRowRequest)

        return@async addRowRequestDB.addRowRequestDao().getAll()
    }

    private fun getAllRowsAsync(): Deferred<List<AddRowRequest>> = coroutineScope.async (Dispatchers.IO) {
//        Log.d(TAG, "getAllRowsAsync")

        return@async addRowRequestDB.addRowRequestDao().getAll()
    }

    private fun deleteRowAsync(addRowRequest: AddRowRequest) = coroutineScope.launch (Dispatchers.IO) {
//        Log.d(TAG, "deleteRowAsync: deleting queued request with id ${addRowRequest.id}")

        addRowRequestDB.addRowRequestDao().delete(addRowRequest)
    }

    /********** PUBLIC METHODS **********/

    fun sendQueuedRequests() {
        if (!isInternetConnected()) {
            return
        }

        coroutineScope.launch (Dispatchers.Main) {
            val queuedRequests = getAllRowsAsync().await()

            queuedRequests.forEach { addRowRequest ->
                Log.d(TAG, "sending queued requests")

                try {
                    addExpenseRowToSheetAsync(
                        addRowRequest.spreadsheetId,
                        addRowRequest.sheetName,
                        addRowRequest.expenseDate,
                        addRowRequest.expenseItem,
                        addRowRequest.expenseCategoryValue,
                        addRowRequest.expenseAmount,
                        addRowRequest.expenseAmountOthers,
                        addRowRequest.expenseNotes,
                        addRowRequest.currency,
                        addRowRequest.exchangeRate
                    ).await()

                    deleteRowAsync(addRowRequest)
                } catch (e: UserRecoverableAuthIOException) {
                    startForRequestAuthorizationResult.launch(e.intent)
                    Log.e(TAG, getString(R.string.status_need_permission))
                } catch (e: IOException) {
                    Log.e(TAG, getString(R.string.status_google_error))
                } catch (e: NotSignedInException) {
                    Log.d(TAG, getString(R.string.status_not_signed_in))
                } finally {
                    Log.d(TAG, "creating notification of sent requests")

                    val builder = NotificationCompat.Builder(this@MainActivity, QUEUED_REQUEST_NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(getString(R.string.notification_queued_requests_title))
                        .setContentText(getString(R.string.notification_queued_requests_content, addRowRequest.expenseItem))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)

                    with(NotificationManagerCompat.from(this@MainActivity)) {
                        // notificationId is a unique int for each notification that you must define
                        val notificationId = 0 // I'm using the same id for each notification, so it only shows the last one
                        notify(notificationId, builder.build())
                    }
                }
            }
        }
    }
}
