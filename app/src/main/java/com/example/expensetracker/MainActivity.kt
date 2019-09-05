package com.example.expensetracker

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText

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

class MainActivity : AppCompatActivity() {
    lateinit var expenseItem: EditText
    lateinit var expenseCategory: EditText
    lateinit var expenseAmount: EditText
    lateinit var expenseDate: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        expenseItem = findViewById<EditText>(R.id.expenseItem)
        expenseCategory = findViewById<EditText>(R.id.expenseCategory)
        expenseAmount = findViewById<EditText>(R.id.expenseAmount)
        expenseDate = findViewById<EditText>(R.id.expenseDate)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        expenseDate.setText(todayDate)
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
            R.id.action_settings -> true
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

        if (!validateInput()) {
            Snackbar.make(view, "Could not send request", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()

            return
        }

        // when button is pressed, call sheets api and send data
        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(this)

        val url = buildFormUrl(view)

        Log.d("MAIN_ACTIVITY", "URL is: $url")

        // Request a string response from the provided URL.
        val stringRequest = StringRequest(Request.Method.GET, url,
            Response.Listener<String> { response ->
                // Display the first 500 characters of the response string.
                Log.d("MAIN_ACTIVITY", "Response is: $response")

                expenseItem.setText("")
                expenseCategory.setText("")
                expenseAmount.setText("")

                val jsonResponse = JsonParser().parse(response).asJsonObject

                Snackbar.make(view, "Added as row ${jsonResponse["row"]}", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
            },
            Response.ErrorListener {
                Log.d("MAIN_ACTIVITY", "It didn't work: $it")
            })

        // Add the request to the RequestQueue.
        queue.add(stringRequest)

    }

    private fun buildFormUrl(view: View): String {
        // TODO: validate that these have values
        // TODO: categories should be a dropdown

        val url = getString(R.string.google_form_url) +
                "?" +
                "Date=" +
                expenseDate.text +
                "&Item=" +
                expenseItem.text +
                "&Category=" +
                expenseCategory.text +
                "&Amount=" +
                expenseAmount.text +
                "&Notes=" +
                "&Split="

        return url
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

        if (expenseCategory.text.isBlank()) {
            expenseCategory.error = "Category cannot be blank"
            isValid = false
        }

        if (expenseDate.text.isBlank()) {
            expenseDate.error = "Date cannot be blank"
            isValid = false
        }

        return isValid
    }
}
