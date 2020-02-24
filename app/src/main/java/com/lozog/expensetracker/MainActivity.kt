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
import androidx.preference.PreferenceManager
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
//import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
//import com.google.api.client.http.javanet.NetHttpTransport
//import com.google.api.client.json.JsonFactory
//import com.google.api.client.json.jackson2.JacksonFactory
//import com.google.api.services.sheets.v4.Sheets
//import com.google.api.services.sheets.v4.SheetsScopes
//import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.gson.JsonParser
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private lateinit var expenseItem: EditText
    private lateinit var expenseCategory: Spinner
    private lateinit var expenseAmount: EditText
    private lateinit var expenseAmountOthers: EditText
    private lateinit var expenseDate: EditText
    private lateinit var expenseNotes: EditText
    private lateinit var currencyLabel: EditText
    private lateinit var currencyExchangeRate: EditText

    private lateinit var mGoogleSignInClient: GoogleSignInClient
    private var RC_SIGN_IN: Int = 0
    private lateinit var googleAccount: GoogleSignInAccount

    private var expenseCategoryValue: String = ""

    companion object {
        private const val TAG = "MAIN_ACTIVITY"
//
//        private var JSON_FACTORY: JsonFactory = JacksonFactory.getDefaultInstance()
//        private var SCOPES:List<String> = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY)
    }

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

//        // Configure sign-in to request the user's ID, email address, and basic
//        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
//        val gso =
//            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//                .requestEmail()
//                .build()
//
//        // Build a GoogleSignInClient with the options specified by gso.
//        mGoogleSignInClient = GoogleSignIn.getClient(this, gso)
//
//        val signInButton = findViewById<SignInButton>(R.id.sign_in_button)
//        signInButton.setOnClickListener{view ->
//            when (view.id) {
//                R.id.sign_in_button -> {
//                    signIn()
//                }
//            }
//        }
    }

    private fun signIn() {
        val signInIntent = mGoogleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

//    override fun onStart() {
//        super.onStart()
//
//        // Check for existing Google Sign In account, if the user is already signed in
//        // the GoogleSignInAccount will be non-null.
//        val account: GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(this)
//
//        if (account != null) {
//            googleAccount = account
//
//            // remove Google Sign-in button from view if already signed in
////            val signInButton = findViewById<SignInButton>(R.id.sign_in_button)
////            val contentMainLayout = findViewById<ConstraintLayout>(R.id.content_main_layout)
////            contentMainLayout.removeView(signInButton)
//        }
//    }
//
//    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
//        super.onActivityResult(requestCode, resultCode, data)
//
//        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
//        if (requestCode == RC_SIGN_IN) {
//            // The Task returned from this call is always completed, no need to attach
//            // a listener.
//            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
//            handleSignInResult(task)
//        }
//    }
//
//    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
//        try {
//            val account: GoogleSignInAccount? = completedTask.getResult(ApiException::class.java)
//
//            account ?: return
//
//            // Signed in successfully
//            googleAccount = account
//            Log.d(TAG, "signed into account: " + googleAccount.email)
//
//            val httpTransport = NetHttpTransport()
//
//            val credential = GoogleAccountCredential.usingOAuth2(this, SCOPES)
//            credential.selectedAccount = googleAccount.account
//
//            val service = Sheets.Builder(httpTransport, JSON_FACTORY, credential)
//                .setApplicationName(getString(R.string.app_name))
//                .build()
//            testSheet(service)
//        } catch (e: ApiException) {
//            // The ApiException status code indicates the detailed failure reason.
//            // Please refer to the GoogleSignInStatusCodes class reference for more information.
//            Log.d(TAG, "signInResult:failed code=" + e.statusCode)
//        }
//    }
//
//    private fun testSheet(service: Sheets) {
////        var spreadsheet = Spreadsheet()
////            .setProperties(
////                SpreadsheetProperties()
////                    .setTitle("CreateNewSpreadsheet")
////            )
////
////        spreadsheet = service.spreadsheets().create(spreadsheet).execute()
////        Log.d(TAG, "ID: ${spreadsheet.spreadsheetId}")
//
//        // TODO: the following line causes an IllegalStateException because it's being called from the UI thread, I think
//        // so I need to refactor it into a background task maybe?
////        val spreadsheet: Spreadsheet = service.spreadsheets().get("1KcUmWhrTFRe4cb0JBr7G1jJMHFKvu-PcFdhVv9AuNwo").execute()
////        Log.d(TAG, spreadsheet.toString())
////        Log.d(TAG, "IDK")
//
//    }

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

    private fun hideKeyboard(view: View) {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            view.windowToken,
            HIDE_NOT_ALWAYS
        )
    }

    fun submitExpense(view: View) {
        hideKeyboard(view)

        val submitButton = findViewById<Button>(R.id.expenseSubmitButton)
        submitButton.text = "Submitting..."

        if (!validateInput()) {
            Snackbar.make(view, "Could not send request", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

            return
        }

        // when button is pressed, call sheets api and send data
        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(this)

        val url = buildFormUrl(this, view)

        if (url == "") {
            Snackbar.make(view, "You must set a Google Form URL", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
            return
        }

        Log.d(TAG, "URL is: $url")

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(Request.Method.GET, url,
            Response.Listener<String> { response ->
                Log.d(TAG, "Response is: $response")

                expenseItem.setText("")
                expenseAmount.setText("")
                expenseAmountOthers.setText("")
                expenseNotes.setText("")
                currencyLabel.setText("")
                currencyExchangeRate.setText("")

                // TODO: response still might be an error

                val jsonResponse = JsonParser().parse(response).asJsonObject

                val statusText = "Added as row ${jsonResponse["row"]}"

                Snackbar.make(view, statusText, Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

                val statusTextView = findViewById<TextView>(R.id.statusText)
                statusTextView.text = statusText

                submitButton.text = getString(R.string.button_expense_submit)
            },
            Response.ErrorListener {
                val statusText = "$it"

                Log.d(TAG, statusText)

                Snackbar.make(view, "Could not complete request", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

                val statusTextView = findViewById<TextView>(R.id.statusText)
                statusTextView.text = statusText

                submitButton.text = getString(R.string.button_expense_submit)
            })

        val mRetryPolicy = DefaultRetryPolicy(
            30 * 1000,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
        )

        stringRequest.retryPolicy = mRetryPolicy

        // Add the request to the RequestQueue.
        queue.add(stringRequest)
    }

    private fun buildFormUrl(context: Context, view: View): String {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val baseUrl = sharedPreferences.getString("google_form_url", "")

        if (baseUrl == null || baseUrl == "") {
            return ""
        }

        var currency = sharedPreferences.getString("currency", getString(R.string.default_currency))
        var exchangeRate = sharedPreferences.getString("exchange_rate", getString(R.string.default_exchange_rate))

        val currencyLabelText = currencyLabel.text.toString()
        val currencyExchangeRateText = currencyExchangeRate.text.toString()

        if (currencyLabelText != "") {
            currency = currencyLabelText
        }

        if (currencyExchangeRateText != "") {
            exchangeRate = currencyExchangeRateText
        }

        if (currency == null || exchangeRate == null) {
            Snackbar.make(view, "Set a currency and exchange rate", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

            return ""
        }

        return baseUrl +
                "?" +
                "Date=" +
                expenseDate.text +
                "&Item=" +
                expenseItem.text +
                "&Category=" +
                expenseCategoryValue +
                "&Amount=" +
                expenseAmount.text +
                "&Split=" +
                expenseAmountOthers.text +
                "&Notes=" +
                expenseNotes.text +
                "&Currency=" +
                currency +
                "&Exchange=" +
                exchangeRate
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

    override fun onItemSelected(parent: AdapterView<*>, view: View, pos: Int, id: Long) {
        expenseCategoryValue = parent.getItemAtPosition(pos).toString()
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Another interface callback
    }
}
