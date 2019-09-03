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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        // Set default value of expenseDate input as today's date
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val expenseDate = findViewById<EditText>(R.id.expenseDate)
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

    fun hideKeyboard() {
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputManager.hideSoftInputFromWindow(
            currentFocus!!.windowToken,
            HIDE_NOT_ALWAYS
        )
    }

    fun submitExpense(view: View) {
        hideKeyboard()

        // when button is pressed, call sheets api and send data
        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(this)

        var expenseItem = findViewById<EditText>(R.id.expenseItem)
        var expenseCategory = findViewById<EditText>(R.id.expenseCategory)
        var expenseAmount = findViewById<EditText>(R.id.expenseAmount)
        var expenseDate = findViewById<EditText>(R.id.expenseDate)

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
}
