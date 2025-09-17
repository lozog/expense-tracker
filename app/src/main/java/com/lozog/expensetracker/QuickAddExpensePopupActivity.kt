package com.lozog.expensetracker

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.lozog.expensetracker.util.KeyboardManager
import com.lozog.expensetracker.util.expenserow.ExpenseRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QuickAddExpensePopupActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EXPENSE_TRACKER QuickAddExpensePopupActivity"
    }

    // Define the ActivityResultLauncher to handle the result
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    private val sheetsViewModel: SheetsViewModel by viewModels {
        SheetsViewModelFactory((applicationContext as ExpenseTrackerApplication).sheetsRepository, (applicationContext as ExpenseTrackerApplication).applicationScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Register the overlay permission launcher
        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // This block is called when the permission activity finishes
            if (Settings.canDrawOverlays(this)) {
                showDialogOverlay()
            } else {
                Toast.makeText(this, "Overlay permission is required to show popup", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        // Check if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            showDialogOverlay()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Overlay Permission Needed")
                .setMessage("This permission allows us to display a popup over other apps. Please grant it to continue.")
                .setPositiveButton("OK") { _, _ ->
                    requestOverlayPermission()
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    finish() // Close the activity if permission is declined
                }
                .show()
        }

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent) // Launch permission request
    }

    private fun showDialogOverlay() {
        setContentView(R.layout.activity_quick_add_expense_popup)

        val amount = intent.getStringExtra("amount") ?: ""
        val notificationId = intent.getIntExtra("notification_id", -1)

        val expenseItem = findViewById<EditText>(R.id.text_input)
        val dropdown = findViewById<Spinner>(R.id.dropdown)
        val submitButton = findViewById<Button>(R.id.submit_button)
        val amountView = findViewById<TextView>(R.id.expense_amount)
        amountView.text = "$$amount"

        KeyboardManager.showKeyboard(expenseItem)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val categoriesPrefs = sharedPreferences.getString("categories", null)?: ""
        val categories = categoriesPrefs.split(",").map { it.trim() }.toTypedArray()
        dropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)

        submitButton.setOnClickListener {
            val inputText = expenseItem.text.toString()
            val selectedOption = dropdown.selectedItem.toString()

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

            sheetsViewModel.addExpenseRowToSheetAsync(expenseRow)

            // clear the notification
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)

            finish() // Close the activity after submitting
        }
    }
}