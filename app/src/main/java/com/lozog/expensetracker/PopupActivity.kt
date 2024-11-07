package com.lozog.expensetracker

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class PopupActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the dialog theme with no title
//        setTheme(R.style.PopupTheme)
//        requestWindowFeature(Window.FEATURE_NO_TITLE) // Hide title bar
        super.onCreate(savedInstanceState)

        // Check if overlay permission is granted
        if (Settings.canDrawOverlays(this)) {
            showDialogOverlay()
        } else {
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

        // Get references to UI elements
        val textInput = findViewById<EditText>(R.id.text_input)
        val dropdown = findViewById<Spinner>(R.id.dropdown)
        val submitButton = findViewById<Button>(R.id.submit_button)

        // Set up dropdown options
        val options = arrayOf("Option 1", "Option 2", "Option 3")
        dropdown.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)

        // Handle submit button click
        submitButton.setOnClickListener {
            val inputText = textInput.text.toString()
            val selectedOption = dropdown.selectedItem.toString()

            // Handle the submit action, e.g., display a Toast
            Toast.makeText(this, "Submitted: $inputText, $selectedOption", Toast.LENGTH_SHORT).show()
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