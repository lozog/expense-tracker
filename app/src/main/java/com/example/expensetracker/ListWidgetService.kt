package com.example.expensetracker

import android.content.Context
import android.content.Intent
import android.widget.RemoteViewsService
import android.widget.RemoteViews
import android.appwidget.AppWidgetManager
import android.util.Log

private const val REMOTE_VIEW_COUNT: Int = 10

class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ListRemoteViewsFactory(this.applicationContext, intent)
    }
}

class ListRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val LOG = "SUMMARY_WIDGET"
    private lateinit var expenseItems: List<ExpenseItem>
    private var mAppWidgetId: Int = intent.getIntExtra(
        AppWidgetManager.EXTRA_APPWIDGET_ID,
        AppWidgetManager.INVALID_APPWIDGET_ID
    )

    override fun onCreate() {
        // put some default items into expenseItems
        expenseItems = List(REMOTE_VIEW_COUNT) { index -> ExpenseItem("$index!") }
    }

    override fun getViewAt(position: Int): RemoteViews {
        // Construct a remote views item based on the app widget item XML file,
        // and set the text based on the position.
        return RemoteViews(context.packageName, R.layout.expense_item).apply {
            setTextViewText(R.id.expense_item, expenseItems[position].text)
        }

    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun onDataSetChanged() {
        // TODO
        Log.d(LOG, "onDataSetChanged")
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun onDestroy() {
        expenseItems = emptyList()
    }

    override fun getCount(): Int {
        return expenseItems.size
    }

}
