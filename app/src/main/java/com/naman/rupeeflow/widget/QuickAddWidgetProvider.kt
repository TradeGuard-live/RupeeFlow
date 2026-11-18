package com.naman.rupeeflow.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class QuickAddWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RupeeFlowWidgetManager.updateQuickAddWidgets(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RupeeFlowWidgetManager.updateQuickAddWidgets(context)
    }
}

