package com.lozog.expensetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.PreferenceManager
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

    private var expenseCategoryValue: String = ""

    /********** GOOGLE SIGN-IN **********/
    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private lateinit var googleAccount: GoogleSignInAccount

    private lateinit var spreadsheetService: Sheets

    companion object {
        private const val TAG = "MAIN_ACTIVITY"

        private const val RC_SIGN_IN: Int = 0
        private const val RC_REQUEST_AUTHORIZATION: Int = 1

        private var JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
        private var SCOPES:List<String> = Collections.singletonList(SheetsScopes.SPREADSHEETS)
    }

    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + parentJob)

    /********** OVERRIDE METHODS **********/

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        expenseItem = findViewById(R.id.expenseItem)
        expenseCategory = findViewById(R.id.expenseCategory)
        expenseAmount = findViewById(R.id.expenseAmount)
        expenseAmountOthers = findViewById(R.id.expenseAmountOthers)
        expenseDate = findViewById(R.id.expenseDate)
        expenseNotes = findViewById(R.id.expenseNotes)
        currencyLabel = findViewById(R.id.currencyLabel)
        currencyExchangeRate = findViewById(R.id.currencyExchangeRate)

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        expenseDate.setText(todayDate)

        // Set up the categories dropdown
        ArrayAdapter.createFromResource(
            this,
            R.array.categories_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            // Apply the adapter to the spinner
            expenseCategory.adapter = adapter
        }

        expenseCategory.onItemSelectedListener = this

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInButton = findViewById<SignInButton>(R.id.sign_in_button)
        signInButton.setOnClickListener{view ->
            when (view.id) {
                R.id.sign_in_button -> {
                    val signInIntent = mGoogleSignInClient.signInIntent
                    startActivityForResult(signInIntent, RC_SIGN_IN)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(this)

        if (account != null) {
            onSignInSuccess(account)
        }
    }

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        expenseCategoryValue = parent.getItemAtPosition(pos).toString()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
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
            Log.d(TAG, "signInResult: failed. code=" + e.statusCode)
        }
    }

    private fun onSignInSuccess(account: GoogleSignInAccount) {
        googleAccount = account

        Log.d(TAG, "signed into account: " + googleAccount.email)

        getSheetService()

        // remove Google Sign-in button from view if already signed in
        val signInButton = findViewById<SignInButton>(R.id.sign_in_button)
        val contentMainLayout = findViewById<ConstraintLayout>(R.id.content_main_layout)
        contentMainLayout.removeView(signInButton)
    }

    private fun getSheetService() {
        val httpTransport = NetHttpTransport()

        val credential = GoogleAccountCredential.usingOAuth2(this, SCOPES)
        credential.selectedAccount = googleAccount.account

        spreadsheetService = Sheets.Builder(httpTransport, JSON_FACTORY, credential)
            .setApplicationName(getString(R.string.app_name))
            .build()
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
        var nextRow = 0;

        try {
            nextRow = spreadsheetService.spreadsheets().values().get(spreadsheetId, sheetName).execute().getValues().size + 1
        } catch (e: UserRecoverableAuthIOException) {
            startActivityForResult(e.intent, RC_REQUEST_AUTHORIZATION)
            // TODO: what happens if we get to this point? will it cancel the rest of the fn?
        }

        Log.d(TAG, "addExpenseRowToSheetAsync")

        val valueInputOption = "USER_ENTERED"
        val insertDataOption = "INSERT_ROWS"

        val expenseTotal = "=(\$D$nextRow - \$E$nextRow)*IF(NOT(ISBLANK(\$I$nextRow)), \$I$nextRow, 1)"

        val rowData = mutableListOf(mutableListOf(
            expenseDate, expenseItem, expenseCategoryValue, expenseAmount, expenseAmountOthers, expenseTotal, expenseNotes, currency, exchangeRate
        ))
        val requestBody = ValueRange()
        requestBody.setValues(rowData as List<MutableList<Any>>?)

        val request = spreadsheetService.spreadsheets().values().append(spreadsheetId, sheetName, requestBody)
        request.valueInputOption = valueInputOption
        request.insertDataOption = insertDataOption

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
            expenseItem.error = "Item cannot be blank"
            isValid = false
        }

        if (expenseAmount.text.isBlank()) {
            expenseAmount.error = "Amount cannot be blank"
            isValid = false
        }

        if (expenseCategoryValue.isBlank()) {
            (expenseCategory.selectedView as TextView).error = "Category cannot be blank"
            isValid = false
        }

        if (expenseDate.text.isBlank()) {
            expenseDate.error = "Date cannot be blank"
            isValid = false
        }

        return isValid
    }

    /********** PUBLIC METHODS **********/

    fun submitExpense(view: View) {
        hideKeyboard(view)

        val submitButton = findViewById<Button>(R.id.expenseSubmitButton)
        submitButton.text = getString(R.string.button_expense_submitting)

        if (!validateInput()) {
            Snackbar.make(view, "Could not send request", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            submitButton.text = getString(R.string.button_expense_submit)
            return
        }

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
        val sheetName = sharedPreferences.getString("google_sheet_name", null)

        if (spreadsheetId == null) {
            Snackbar.make(view, "Set a spreadsheet Id", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        if (sheetName == null) {
            Snackbar.make(view, "Set a data sheet name", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        var currency = currencyLabel.text.toString()
        var exchangeRate = currencyExchangeRate.text.toString()

        if (currency == "") {
            val defaultCurrency = sharedPreferences.getString("currency", getString(R.string.default_currency))

            if (defaultCurrency == null) {
                Snackbar.make(view, "Set a currency", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return
            }

            currency = defaultCurrency
        }

        if (exchangeRate == "") {
            val defaultExchangeRate = sharedPreferences.getString("exchange_rate", getString(R.string.default_exchange_rate))

            if (defaultExchangeRate == null) {
                Snackbar.make(view, "Set a currency", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
                return
            }

            exchangeRate = defaultExchangeRate
        }

        coroutineScope.launch (Dispatchers.Main) {
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

            expenseItem.setText("")
            expenseAmount.setText("")
            expenseAmountOthers.setText("")
            expenseNotes.setText("")
            currencyLabel.setText("")
            currencyExchangeRate.setText("")

            val updatedRange = appendResponse.updates.updatedRange.split("!")[1]
            val statusText = "Updated range: $updatedRange"

            Snackbar.make(view, statusText, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

            val statusTextView = findViewById<TextView>(R.id.statusText)
            statusTextView.text = statusText

            submitButton.text = getString(R.string.button_expense_submit)
        }
    }
}
