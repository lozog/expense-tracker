package com.example.expensetracker

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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Implementation of App Widget functionality.
 */
class SummaryWidget : AppWidgetProvider() {

    private val LOG = "SUMMARY_WIDGET"
    private val ACTION_UPDATE = "action.UPDATE"

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

    private lateinit var results: Array<String>
    private var numCols: Int = 0
    private var numRows: Int = 0
    private var categories = ArrayList<String>()
    private var amounts = ArrayList<String>()
    private var percentages = ArrayList<String>()

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
                setOnClickPendingIntent(R.id.update_button, getPendingSelfIntent(context, ACTION_UPDATE));
            }

            // Tell the AppWidgetManager to perform an update on the current app widget
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

//        Log.d(LOG, "button pressed")

        if (ACTION_UPDATE == intent.action) {
            callSheetsAPI(context)
        }
    }

    private fun getPendingSelfIntent(context: Context, action: String): PendingIntent {
        val intent =
            Intent(context, javaClass) // An intent directed at the current class (the "self").
        intent.action = action
        return PendingIntent.getBroadcast(context, 0, intent, 0)
    }

    private fun callSheetsAPI(context: Context) {
        // Instantiate the RequestQueue
        val queue = Volley.newRequestQueue(context)

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val url = sharedPreferences.getString("google_sheets_url", "")

        // Request a string response from the provided URL
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener { response ->
//                Log.d(LOG, "got a response")

                results =
                    response.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                numRows = results.size - 1 // TODO: I added a "total" count so -1 for that
                numCols =
                    results[0].split("\t".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
                        .size

                categories.clear()
                amounts.clear()
                percentages.clear()
                val cal = Calendar.getInstance()
                val monthCol = cal.get(Calendar.MONTH) + 2 // add offset of 2 (Jan = col 2)

                for (row in 0 until numRows) {
                    var col = 0 // category
                    val rowArr = results[row].split("\t".toRegex()).dropLastWhile({ it.isEmpty() })
                        .toTypedArray()
                    categories.add(rowArr[col])
                    col = monthCol
                    amounts.add(rowArr[col])
                    if (row > 1) {
                        val monthlyTargetAmount = stringToDouble(rowArr[1])
                        val currentAmount = stringToDouble(rowArr[col])
                        var percentageRemaining = "${String.format("%.1f", getPercentage(currentAmount, monthlyTargetAmount))}%"
                        percentages.add(percentageRemaining)

//                        val percentageRemaining = 0
//                        Log.d(LOG, "$currentAmount / $monthlyTargetAmount = $percentageRemaining")
                    }

//                    Log.d(LOG, (rowArr[1].toInt() / rowArr[col].toInt()).toString())
                }

                updateUI(context)
            }, Response.ErrorListener { error -> Log.e(LOG, error.toString()) })

        // add the request to the RequestQueue
        queue.add(stringRequest)
    }

    private fun updateUI(context: Context) {
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

//            Log.d(LOG, amounts.toString())

            // set the text for each of the hard-coded categories
            categoryIds.forEachIndexed {index, categoryId ->
                remoteViews.setTextViewText(
                    categoryId,
                    amounts[index+2]
                )
//                Log.d(LOG, (index+2).toString())
//                Log.d(LOG, amounts[index+2])
            }

//            Log.d(LOG, percentages.toString())

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

            appWidgetManager.updateAppWidget(widgetId, remoteViews)
        }
    }

    private fun getLocalizedDateTimeString(): String {
        val formatter = SimpleDateFormat("kk:mm", Locale.getDefault())
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

