package com.lozog.expensetracker

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PopupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EXPENSE_TRACKER PopupActivity"

        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            showDialogOverlay()
        } else {
            // TODO: put in a dialog here
            // Request overlay permission if not granted
            requestOverlayPermission()
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
    }

    private fun showDialogOverlay() {
        setContentView(R.layout.activity_popup)

        val amount = intent.getStringExtra("amount") ?: ""
        val notificationId = intent.getIntExtra("notification_id", -1)

        val textInput = findViewById<EditText>(R.id.text_input)
        val dropdown = findViewById<Spinner>(R.id.dropdown)
        val submitButton = findViewById<Button>(R.id.submit_button)
        val amountView = findViewById<TextView>(R.id.expense_amount)
        amountView.text = "$$amount"

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val categoriesPrefs = sharedPreferences.getString("categories", null)?: ""
        val categories = categoriesPrefs.split(",").map { it.trim() }.toTypedArray()
        dropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        submitButton.setOnClickListener {
            val inputText = textInput.text.toString()
            val selectedOption = dropdown.selectedItem.toString()

            val sheetsRepository = (applicationContext as ExpenseTrackerApplication).sheetsRepository

            Toast.makeText(this, "Submitted $inputText - $$amount", Toast.LENGTH_SHORT).show()

            val expenseRow = ExpenseRow(
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                inputText,
                selectedOption,
                amount,
                "",
                "",
                "",
                "",
                "",
                ExpenseRow.STATUS_PENDING
            )

            sheetsRepository.addExpenseRowAsync(expenseRow)

            // clear the notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)

            finish() // Close the activity after submitting
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                // Permission granted; show the overlay
                showDialogOverlay()
            } else {
                // Permission not granted; show a message and close the activity
                Toast.makeText(this, "Overlay permission is required to show popup", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}