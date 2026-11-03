package com.naman.rupeeflow.data.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.naman.rupeeflow.MainActivity
import com.naman.rupeeflow.domain.util.CurrencyUtils

object TransactionNotificationHelper {

    const val CHANNEL_ID = "rupeeflow_transactions"
    private const val CHANNEL_NAME = "Transaction Detection"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming financial transactions"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showPendingTransactionNotification(
        context: Context,
        pendingId: Long,
        amountPaise: Long,
        merchantOrBank: String?
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "pending_review")
            putExtra("EXTRA_PENDING_ID", pendingId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            pendingId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val amountStr = CurrencyUtils.formatPaiseToRupees(amountPaise)
        val sourceInfo = if (!merchantOrBank.isNullOrBlank()) " at $merchantOrBank" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New Transaction Detected")
            .setContentText("$amountStr transaction detected$sourceInfo. Tap to categorize & split.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$amountStr transaction detected$sourceInfo.\nTap to review, assign category or split with friends.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(pendingId.toInt(), notification)
    }
}
