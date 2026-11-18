package com.naman.rupeeflow.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class SpendingSummaryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RupeeFlowWidgetManager.updateAllWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RupeeFlowWidgetManager.updateAllWidgets(context)
    }
}

