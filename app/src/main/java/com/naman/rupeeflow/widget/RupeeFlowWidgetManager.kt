package com.naman.rupeeflow.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.naman.rupeeflow.MainActivity
import com.naman.rupeeflow.R
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.FinancialSummary
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.DateTimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RupeeFlowWidgetManager {

    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Safely loads the financial summary from Room database without blocking the UI thread.
     * Returns a zeroed-out summary on missing data, errors, or lock state.
     */
    suspend fun getFinancialSummary(context: Context): FinancialSummary = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as? RupeeFlowApplication ?: return@withContext FinancialSummary()
            val container = app.appContainer
            val txns = container.transactionRepository.getAllTransactions().firstOrNull() ?: emptyList()
            val friends = container.friendRepository.getActiveFriends().firstOrNull() ?: emptyList()
            val settlements = container.settlementRepository.getAllSettlements().firstOrNull() ?: emptyList()
            val pending = container.pendingTransactionRepository.getPendingTransactions().firstOrNull() ?: emptyList()

            val friendsWithLedger = LedgerCalculator.computeFriendLedgers(friends, txns, settlements)
            val startOfMonth = DateTimeUtils.getStartOfMonth()

            LedgerCalculator.computeFinancialSummary(
                transactions = txns,
                friends = friendsWithLedger,
                pendingCount = pending.size,
                startOfMonth = startOfMonth
            )
        } catch (_: Exception) {
            FinancialSummary()
        }
    }

    /**
     * Updates all home screen widget instances across all 3 widget providers.
     */
    fun updateAllWidgets(context: Context) {
        scope.launch {
            val summary = getFinancialSummary(context)
            withContext(Dispatchers.Main) {
                updateMoneySnapshotWidgets(context, summary)
                updateQuickAddWidgets(context)
                updateSpendingSummaryWidgets(context, summary)
            }
        }
    }

    fun updateMoneySnapshotWidgets(context: Context, summary: FinancialSummary) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val componentName = ComponentName(context, MoneySnapshotWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val currentMonthLabel = monthFormat.format(Date())

        val savings = maxOf(0L, summary.totalIncomeThisMonthPaise - summary.totalExpenseThisMonthPaise)

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_money_snapshot)
            views.setTextViewText(R.id.widget_month_label, currentMonthLabel)
            views.setTextViewText(R.id.widget_spent_amount, CurrencyUtils.formatPaiseToRupees(summary.totalExpenseThisMonthPaise))
            views.setTextViewText(R.id.widget_income_amount, CurrencyUtils.formatPaiseToRupees(summary.totalIncomeThisMonthPaise))
            views.setTextViewText(R.id.widget_savings_amount, CurrencyUtils.formatPaiseToRupees(savings))
            views.setTextViewText(R.id.widget_owed_to_me_amount, CurrencyUtils.formatPaiseToRupees(summary.totalOwedToMePaise))
            views.setTextViewText(R.id.widget_i_owe_amount, CurrencyUtils.formatPaiseToRupees(summary.totalIOwePaise))

            val pendingIntent = getLaunchAppPendingIntent(context, "overview")
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    fun updateQuickAddWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val componentName = ComponentName(context, QuickAddWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)
            val pendingIntent = getLaunchAppPendingIntent(context, "fast_add")
            views.setOnClickPendingIntent(R.id.widget_quick_add_root, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    fun updateSpendingSummaryWidgets(context: Context, summary: FinancialSummary) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val componentName = ComponentName(context, SpendingSummaryWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (appWidgetIds.isEmpty()) return

        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val currentMonthLabel = monthFormat.format(Date())

        val dailyAvg = summary.averageDailySpendingPaise

        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_spending_summary)
            views.setTextViewText(R.id.widget_summary_month, currentMonthLabel)
            views.setTextViewText(R.id.widget_summary_total, CurrencyUtils.formatPaiseToRupees(summary.totalExpenseThisMonthPaise))
            views.setTextViewText(R.id.widget_summary_daily_avg, CurrencyUtils.formatPaiseToRupees(dailyAvg))

            val note = if (summary.totalExpenseThisMonthPaise > 0L) {
                "Average ${CurrencyUtils.formatPaiseToRupees(dailyAvg)}/day"
            } else {
                "No expenses yet this month"
            }
            views.setTextViewText(R.id.widget_summary_note, note)

            val pendingIntent = getLaunchAppPendingIntent(context, "overview")
            views.setOnClickPendingIntent(R.id.widget_summary_root, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun getLaunchAppPendingIntent(context: Context, destination: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", destination)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val requestCode = when (destination) {
            "fast_add" -> 101
            else -> 100
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}

