package com.lozog.expensetracker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AppendValuesResponse
import com.google.api.services.sheets.v4.model.ValueRange
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    /********** UI Widgets **********/
    private lateinit var expenseItem: EditText
    private lateinit var expenseCategory: Spinner
    private lateinit var expenseAmount: EditText
    private lateinit var expenseAmountOthers: EditText
    private lateinit var expenseDate: EditText
    private lateinit var expenseNotes: EditText
    private lateinit var currencyLabel: EditText
    private lateinit var currencyExchangeRate: EditText
    private lateinit var signInButton: SignInButton
    private lateinit var submitButton: Button
    private lateinit var statusTextView: TextView

    private var expenseCategoryValue: String = ""

    /********** GOOGLE SIGN-IN **********/
    private lateinit var mGoogleSignInClient: GoogleSignInClient

    /********** ROOM DB **********/
    private lateinit var addRowRequestDB: AddRowRequestDB
    private lateinit var networkReceiver: NetworkReceiver

    /********** CONCURRENCY **********/
    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + parentJob)

    companion object {
        private const val TAG = "MAIN_ACTIVITY"

        private const val RC_SIGN_IN: Int = 0
        private const val RC_REQUEST_AUTHORIZATION: Int = 1

        private const val SHEETS_VALUE_INPUT_OPTION = "USER_ENTERED"
        private const val SHEETS_INSERT_DATA_OPTION = "INSERT_ROWS"

        private var JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
        private var SCOPES:List<String> = Collections.singletonList(SheetsScopes.SPREADSHEETS)
    }

    /********** OVERRIDE METHODS **********/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        // UI element handles
        expenseItem = findViewById(R.id.expenseItem)
        expenseCategory = findViewById(R.id.expenseCategory)
        expenseAmount = findViewById(R.id.expenseAmount)
        expenseAmountOthers = findViewById(R.id.expenseAmountOthers)
        expenseDate = findViewById(R.id.expenseDate)
        expenseNotes = findViewById(R.id.expenseNotes)
        currencyLabel = findViewById(R.id.currencyLabel)
        currencyExchangeRate = findViewById(R.id.currencyExchangeRate)
        signInButton = findViewById(R.id.sign_in_button)
        submitButton = findViewById(R.id.expenseSubmitButton)
        statusTextView = findViewById(R.id.statusText)

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        expenseDate.setText(todayDate)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val userCategories = sharedPreferences.getString("categories", getString(R.string.default_categories))!!.split(",")

        // Set up the categories dropdown
        val categoriesAdapter = ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, userCategories)
        categoriesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        expenseCategory.adapter = categoriesAdapter

        expenseCategory.onItemSelectedListener = this

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        signInButton.setOnClickListener{view ->
            when (view.id) {
                R.id.sign_in_button -> {
                    val signInIntent = mGoogleSignInClient.signInIntent
                    startActivityForResult(signInIntent, RC_SIGN_IN)
                }
            }
        }

        addRowRequestDB = Room.databaseBuilder(
            applicationContext,
            AddRowRequestDB::class.java, "add-row-request-db"
        ).build()

        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        networkReceiver = NetworkReceiver()
        registerReceiver(networkReceiver, filter)
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

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        expenseCategoryValue = parent.getItemAtPosition(pos).toString()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        expenseCategoryValue = ""
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
//        else if (requestCode == RC_REQUEST_AUTHORIZATION) {}
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                this.startActivity(Intent(this, SettingsActivity::class.java))
                return true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    /********** GOOGLE SIGN-IN METHODS **********/

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
//        googleSheetsInterface.googleAccount = account

        Log.d(TAG, "signed into account: " + account.email)

        val httpTransport = NetHttpTransport()
        val credential = GoogleAccountCredential.usingOAuth2(this, SCOPES)
        credential.selectedAccount = account.account

        // get sheet service object
        val sheetService: Sheets = Sheets.Builder(httpTransport, JSON_FACTORY, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()

        GoogleSheetsInterface.googleAccount = account
        GoogleSheetsInterface.spreadsheetService = sheetService

        // remove Google Sign-in button from view if already signed in
        val signInButton = findViewById<SignInButton>(R.id.sign_in_button)
        val contentMainLayout = findViewById<ConstraintLayout>(R.id.content_main_layout)
        contentMainLayout.removeView(signInButton)
    }

    /********** GOOGLE SHEETS METHODS **********/

    private fun addExpenseRowToSheetAsync(
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

        val nextRow = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().get(spreadsheetId, sheetName).execute().getValues().size + 1
        val expenseTotal = "=(\$D$nextRow - \$E$nextRow)*IF(NOT(ISBLANK(\$I$nextRow)), \$I$nextRow, 1)"

        val rowData = mutableListOf(mutableListOf(
            expenseDate, expenseItem, expenseCategoryValue, expenseAmount, expenseAmountOthers, expenseTotal, expenseNotes, currency, exchangeRate
        ))
        val requestBody = ValueRange()
        requestBody.setValues(rowData as MutableList<MutableList<Any>>?)

        val request = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().append(spreadsheetId, sheetName, requestBody)
        request.valueInputOption = SHEETS_VALUE_INPUT_OPTION
        request.insertDataOption = SHEETS_INSERT_DATA_OPTION

        return@async request.execute()
    }

    /********** HELPER METHODS **********/

    private fun hideKeyboard(view: View) {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            view.windowToken,
            HIDE_NOT_ALWAYS
        )
    }

    private fun validateInput(): Boolean {
        var isValid = true

        if (expenseItem.text.isBlank()) {
            expenseItem.error = getString(R.string.form_no_item)
            isValid = false
        }

        if (expenseAmount.text.isBlank()) {
            expenseAmount.error = getString(R.string.form_no_amount)
            isValid = false
        }

        if (expenseCategoryValue.isBlank()) {
            (expenseCategory.selectedView as TextView).error = getString(R.string.form_no_category)
            isValid = false
        }

        if (expenseDate.text.isBlank()) {
            expenseDate.error = getString(R.string.form_no_date)
            isValid = false
        }

        return isValid
    }

    private fun isInternetConnected(): Boolean {
        val cm = this.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork: NetworkInfo? = cm.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    private fun clearInputs() {
        expenseItem.setText("")
        expenseAmount.setText("")
        expenseAmountOthers.setText("")
        expenseNotes.setText("")
        currencyLabel.setText("")
        currencyExchangeRate.setText("")
    }

    private fun insertRowIntoDBAsync(
        addRowRequest: AddRowRequest
    ): Deferred<List<AddRowRequest>> = coroutineScope.async (Dispatchers.IO) {
        Log.d(TAG, "insertRowIntoDBAsync")

        addRowRequestDB.addRowRequestDao().insert(addRowRequest)

        return@async addRowRequestDB.addRowRequestDao().getAll()
    }

    private fun getAllRowsAsync(): Deferred<List<AddRowRequest>> = coroutineScope.async (Dispatchers.IO) {
        Log.d(TAG, "getAllRowsAsync")

        return@async addRowRequestDB.addRowRequestDao().getAll()
    }

    private fun deleteRowAsync(addRowRequest: AddRowRequest) = coroutineScope.launch (Dispatchers.IO) {
        Log.d(TAG, "deleteRowAsync: ${addRowRequest.id}")

        addRowRequestDB.addRowRequestDao().delete(addRowRequest)
    }

    /********** PUBLIC METHODS **********/

    fun submitExpense(view: View) {
        hideKeyboard(view)

        submitButton.text = getString(R.string.button_expense_submitting)

        if (!validateInput()) {
            submitButton.text = getString(R.string.button_expense_submit)
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("google_sheet_name", null)

        if (spreadsheetId == null) {
            Snackbar.make(view, getString(R.string.form_no_spreadsheet_id), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        if (sheetName == null) {
            Snackbar.make(view, getString(R.string.form_no_data_sheet_name), Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        var currency = currencyLabel.text.toString()
        var exchangeRate = currencyExchangeRate.text.toString()

        if (currency == "") {
            val defaultCurrency = sharedPreferences.getString("currency", getString(R.string.default_currency))

            if (defaultCurrency == null) {
                Snackbar.make(view, getString(R.string.form_no_currency), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return
            }

            currency = defaultCurrency
        }

        if (exchangeRate == "") {
            val defaultExchangeRate = sharedPreferences.getString("exchange_rate", getString(R.string.default_exchange_rate))

            if (defaultExchangeRate == null) {
                Snackbar.make(view, getString(R.string.form_no_exchange_rate), Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return
            }

            exchangeRate = defaultExchangeRate
        }

        if (isInternetConnected()) {
//            Log.d(TAG, "there is an internet connection!")
            coroutineScope.launch (Dispatchers.Main) {
                var statusText: String

                try {
                    val appendResponse = addExpenseRowToSheetAsync(
                        spreadsheetId,
                        sheetName,
                        expenseDate.text.toString(),
                        expenseItem.text.toString(),
                        expenseCategoryValue,
                        expenseAmount.text.toString(),
                        expenseAmountOthers.text.toString(),
                        expenseNotes.text.toString(),
                        currency,
                        exchangeRate
                    ).await()

                    clearInputs()

                    val updatedRange = appendResponse.updates.updatedRange.split("!")[1]
                    statusText = getString(R.string.status_updated_range, updatedRange)
                } catch (e: UserRecoverableAuthIOException) {
                    startActivityForResult(e.intent, RC_REQUEST_AUTHORIZATION)
                    statusText = getString(R.string.status_need_permission)
                } catch (e: IOException) {
                    statusText = getString(R.string.status_no_google_connection)
                }

                Snackbar.make(view, statusText, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

                statusTextView.text = statusText
                submitButton.text = getString(R.string.button_expense_submit)
            }
        } else {
//            Log.d(TAG, "no internet connection!")

            coroutineScope.launch (Dispatchers.Main) {
                val addRowRequest = AddRowRequest(
                    0,
                    spreadsheetId,
                    sheetName,
                    expenseDate.text.toString(),
                    expenseItem.text.toString(),
                    expenseCategoryValue,
                    expenseAmount.text.toString(),
                    expenseAmountOthers.text.toString(),
                    expenseNotes.text.toString(),
                    currency,
                    exchangeRate
                )

                insertRowIntoDBAsync(addRowRequest).await()

                clearInputs()

                statusTextView.text = getString(R.string.status_no_internet)
                submitButton.text = getString(R.string.button_expense_submit)
            }
        }
    }

    fun sendQueuedRequests() {
        if (!isInternetConnected()) {
            return
        }

        Log.d(TAG, "sending queued requests")

        coroutineScope.launch (Dispatchers.Main) {
            val queuedRequests = getAllRowsAsync().await()

            queuedRequests.forEach { addRowRequest ->
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
                    startActivityForResult(e.intent, RC_REQUEST_AUTHORIZATION)
                    Log.d(TAG,  getString(R.string.status_need_permission))
                } catch (e: IOException) {
                    Log.d(TAG,  getString(R.string.status_no_google_connection))
                }
            }
        }
    }
}
