package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View

import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.JsonParser

import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*
import android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.TextView
import androidx.preference.PreferenceManager
import com.android.volley.DefaultRetryPolicy
class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {

    private lateinit var expenseItem: EditText
    private lateinit var expenseCategory: Spinner
    private lateinit var expenseAmount: EditText
    private lateinit var expenseAmountOthers: EditText
    private lateinit var expenseDate: EditText
    private lateinit var expenseNotes: EditText
    private lateinit var currencyLabel: EditText
    private lateinit var currencyExchangeRate: EditText

    private var expenseCategoryValue: String = ""

    companion object {
        private const val TAG = "MainActivity"
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
                val statusText = "It didn't work: $it"

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
            Snackbar.make(view, "You must set a Google Form URL", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

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
