package com.lozog.expensetracker

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import androidx.preference.PreferenceManager
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.sheets.v4.model.BatchGetValuesResponse
import kotlinx.coroutines.*
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Implementation of App Widget functionality.
 */
class SummaryWidget : AppWidgetProvider() {
    private val categoryIds = listOf(
        R.id.summary_groceries,
        R.id.summary_dining_out,
        R.id.summary_drinks,
        R.id.summary_material_items,
        R.id.summary_entertainment,
        R.id.summary_transit,
        R.id.summary_personal_medical,
        R.id.summary_gifts,
        R.id.summary_travel,
        R.id.summary_miscellaneous,
        R.id.summary_film
    )

    private val categoryPercentageIds = listOf(
        R.id.summary_groceries_percentage,
        R.id.summary_dining_out_percentage,
        R.id.summary_drinks_percentage,
        R.id.summary_material_items_percentage,
        R.id.summary_entertainment_percentage,
        R.id.summary_transit_percentage,
        R.id.summary_personal_medical_percentage,
        R.id.summary_gifts_percentage,
        R.id.summary_travel_percentage,
        R.id.summary_miscellaneous_percentage,
        R.id.summary_film_percentage
    )

    private var categories = ArrayList<String>()
    private var amounts = ArrayList<String>()
    private var percentages = ArrayList<String>()
    private var widgetStatus: String = "Never updated"

    companion object {
        private const val TAG = "SUMMARY_WIDGET"
        private const val ACTION_UPDATE = "action.UPDATE"

        // January -> column C, etc
        // TODO: this sucks, make it better
        private val MONTH_COLUMNS = listOf(
            "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"
        )
    }

    private val parentJob = Job()
    private val coroutineScope = CoroutineScope(Dispatchers.Main + parentJob)

    /********** OVERRIDE METHODS **********/

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Perform this loop procedure for each App Widget that belongs to this provider
        appWidgetIds.forEach { appWidgetId ->
            // Get the layout for the SummaryWidget and attach an on-click listener
            // to the button
            val views: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.summary_widget
            ).apply {
                setOnClickPendingIntent(R.id.update_button, getPendingSelfIntent(context));
            }

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)


        if (ACTION_UPDATE == intent.action) {
//            Log.d(TAG, "button pressed")
            widgetStatus = "Updating..."
            updateWidget(context, ::setWidgetStatus)

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)

            if (spreadsheetId == null) {
                widgetStatus = "Failed - set spreadsheet id"
                updateWidget(context, ::setWidgetStatus)
                return
            }

            coroutineScope.launch(Dispatchers.Main)  {
                try {
                    val response = spreadsheetId.let { getMonthlyCategoryAmountsAsync(it).await() }

                    val categoriesFromSheet = response.valueRanges[0]["values"] as List<List<String>>
                    val targetsFromSheet = response.valueRanges[1]["values"] as List<List<String>>
                    val valuesFromSheet = response.valueRanges[2]["values"] as List<List<String>>
//                    Log.d(TAG, response.toString())
//                    Log.d(TAG, categoriesFromSheet.toString())
//                    Log.d(TAG, targetsFromSheet.toString())
//                    Log.d(TAG, valuesFromSheet.toString())

                    categoriesFromSheet[0].forEach { category ->
                        categories.add(category)
                    }

                    valuesFromSheet[0].forEach { value ->
                        amounts.add(value)
                    }

                    targetsFromSheet[0].forEachIndexed { i, value ->
                        val current = stringToDouble(valuesFromSheet[0][i])
                        val monthlyTarget = stringToDouble(value)
//                        Log.d(TAG, "$current, $monthlyTarget")
                        val percentageRemaining = "${String.format("%.1f", getPercentage(current, monthlyTarget))}%"
                        percentages.add(percentageRemaining)
                    }

                    updateWidget(context, ::setWidgetValues)
                } catch (e: UserRecoverableAuthIOException) {
                    // TODO: handle this case
//                    startActivityForResult(e.intent, MainActivity.RC_REQUEST_AUTHORIZATION)

                    Log.d(TAG, "Need more permissions")
                    widgetStatus = "Failed - need permissions"
                    updateWidget(context, ::setWidgetStatus)
                } catch (e: IOException) {
                    Log.d(TAG, "Network error: Could not connect to Google")
                    widgetStatus = "Failed - no Google connection"
                    updateWidget(context, ::setWidgetStatus)
                }
            }
        }
    }

    private fun getMonthlyCategoryAmountsAsync(
        spreadsheetId: String
    ): Deferred<BatchGetValuesResponse> = coroutineScope.async (Dispatchers.IO) {
        val categoryLabelsRange = "'Monthly Budget Items'!A3:A13"
        val categoryTargetRange = "'Monthly Budget Items'!B3:B13"

        val curMonthColumn = MONTH_COLUMNS[Calendar.MONTH]
        val categoryValuesRange = "'Monthly Budget Items'!${curMonthColumn}3:${curMonthColumn}13"

        if (GoogleSheetsInterface.spreadsheetService == null) {
            // TODO: handle this case
            Log.d(TAG, "spreedsheet service is null!")
        }

        val request = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().batchGet(spreadsheetId)
        request.ranges = mutableListOf(categoryLabelsRange, categoryTargetRange, categoryValuesRange) // this order is important - matches the order of the result
        request.majorDimension = "COLUMNS"

        return@async request.execute()
    }

    private fun setWidgetValues(remoteViews: RemoteViews) {
        // set the text for each of the hard-coded categories
        categoryIds.forEachIndexed {index, categoryId ->
            remoteViews.setTextViewText(
                categoryId,
                amounts[index]
            )
        }

        // set the text for each of the hard-coded percentages
        categoryPercentageIds.forEachIndexed {index, categoryPercentageId ->
            remoteViews.setTextViewText(
                categoryPercentageId,
                percentages[index]
            )
        }

        remoteViews.setTextViewText(
            R.id.updated_at,
            "Updated at " + getLocalizedDateTimeString()
        )
    }

    private fun setWidgetStatus(remoteViews: RemoteViews) {
        remoteViews.setTextViewText(
            R.id.updated_at,
            widgetStatus
        )
    }

    private fun updateWidget(context: Context, updateUIElements: (remoteViews: RemoteViews) -> Unit) {
        val appWidgetManager = AppWidgetManager.getInstance(
            context
                .applicationContext
        )

        val thisWidget = ComponentName(
            context,
            SummaryWidget::class.java
        )
        val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)

        allWidgetIds.forEach { widgetId ->
            val remoteViews = RemoteViews(
                context
                    .applicationContext.packageName,
                R.layout.summary_widget
            )

            updateUIElements(remoteViews)

            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }

    /********** HELPER METHODS **********/

    private fun getPendingSelfIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, javaClass) // An intent directed at the current class (the "self").
        intent.action = ACTION_UPDATE
        return PendingIntent.getBroadcast(context, 0, intent, 0)
    }

    private fun getLocalizedDateTimeString(): String {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentDateTime = Calendar.getInstance().time

        return formatter.format(currentDateTime)
    }

    private fun getPercentage(quotient: Double, divisor: Double): Double {
        if (divisor.toInt() == 0) {
            return 0.toDouble()
        }

        return (quotient / divisor) * 100
    }

    private fun stringToDouble(string_in: String): Double {
        return string_in.replace("[^\\d-.]+".toRegex(), "").toDouble()
    }
}

