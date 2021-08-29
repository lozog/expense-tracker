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
    private var categories = ArrayList<String>()
    private var amounts = ArrayList<String>()
    private var percentages = ArrayList<String>()
    private var widgetStatus: String = ""
    private var summarySheetName: String? = ""

    companion object {
        private const val TAG = "SUMMARY_WIDGET"
        private const val ACTION_UPDATE = "action.UPDATE"
        private const val SHEETS_MAJOR_DIMENSION = "COLUMNS"

        // January -> column C, etc
        // TODO: dynamically find month columns
        private val MONTH_COLUMNS = listOf(
            "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N"
        )

        // first row of sheet that actually contains data (i.e. not a header)
        private const val FIRST_DATA_ROW = 3;
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
            Log.d(TAG, "button pressed")
            widgetStatus = context.getString(R.string.widget_status_updating)
            updateWidget(context, ::setWidgetStatus)

            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val spreadsheetId = sharedPreferences.getString("google_spreadsheet_id", null)
            summarySheetName = sharedPreferences.getString("monthly_summary_sheet_name", "")

            if (spreadsheetId == null) {
                widgetStatus = context.getString(R.string.form_no_spreadsheet_id)
                updateWidget(context, ::setWidgetStatus)
                return
            }

            if (summarySheetName == "") {
                widgetStatus = context.getString(R.string.form_no_monthly_summary_sheet_name)
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

                    Log.d(TAG, context.getString(R.string.status_need_permission))
                    widgetStatus = context.getString(R.string.status_need_permission)
                    updateWidget(context, ::setWidgetStatus)
                } catch (e: IOException) {
                    Log.d(TAG, e.toString())
                    widgetStatus = context.getString(R.string.status_google_error)
                    updateWidget(context, ::setWidgetStatus)
                } catch (e: Exception) {
                    Log.d(TAG, e.toString())
                    widgetStatus = e.toString()
                    updateWidget(context, ::setWidgetStatus)
                }
            }
        }
    }

    private fun getMonthlyCategoryAmountsAsync(
        spreadsheetId: String
    ): Deferred<BatchGetValuesResponse> = coroutineScope.async (Dispatchers.IO) {
        val categoryLabelsRange = "'${summarySheetName}'!A${FIRST_DATA_ROW}:A"
        val categoryTargetRange = "'${summarySheetName}'!B${FIRST_DATA_ROW}:B"

        val curMonthColumn = MONTH_COLUMNS[Calendar.getInstance().get(Calendar.MONTH)]

        Log.d(TAG, "month: ${Calendar.getInstance().get(Calendar.MONTH)}}")
        val categoryValuesRange = "'${summarySheetName}'!${curMonthColumn}${FIRST_DATA_ROW}:${curMonthColumn}"

        if (GoogleSheetsInterface.spreadsheetService == null) {
            throw Exception("spreadsheet service is null")
        }

        val request = GoogleSheetsInterface.spreadsheetService!!.spreadsheets().values().batchGet(spreadsheetId)
        request.ranges = mutableListOf(categoryLabelsRange, categoryTargetRange, categoryValuesRange) // this order is important - matches the order of the result
        request.majorDimension = SHEETS_MAJOR_DIMENSION

        return@async request.execute()
    }

    private fun setWidgetValues(remoteViews: RemoteViews, context: Context) {
        // clear previous data
        remoteViews.removeAllViews(R.id.summary_labels)
        remoteViews.removeAllViews(R.id.summary_amounts)
        remoteViews.removeAllViews(R.id.summary_percentages)

        categories.forEach { category ->
            val textView = RemoteViews(context.packageName, R.layout.summary_widget_textview)
            textView.setTextViewText(R.id.widget_textview, category)
            remoteViews.addView(R.id.summary_labels, textView)
        }

        amounts.forEach { amount ->
            val textView = RemoteViews(context.packageName, R.layout.summary_widget_textview)
            textView.setTextViewText(R.id.widget_textview, amount)
            remoteViews.addView(R.id.summary_amounts, textView)
        }

        percentages.forEach { percentage ->
            val textView = RemoteViews(context.packageName, R.layout.summary_widget_textview)
            textView.setTextViewText(R.id.widget_textview, percentage)
            remoteViews.addView(R.id.summary_percentages, textView)
        }

        remoteViews.setTextViewText(
            R.id.updated_at,
            context.getString(R.string.widget_status_updated, getLocalizedDateTimeString())
        )
    }

    private fun setWidgetStatus(remoteViews: RemoteViews, context: Context) {
        remoteViews.setTextViewText(
            R.id.updated_at,
            widgetStatus
        )
    }

    private fun updateWidget(context: Context, updateUIElements: (remoteViews: RemoteViews, context: Context) -> Unit) {
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

            updateUIElements(remoteViews, context)

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

