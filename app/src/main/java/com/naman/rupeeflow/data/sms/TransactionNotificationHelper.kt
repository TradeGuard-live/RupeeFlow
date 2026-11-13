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
        merchantOrBank: String?,
        phoneNumber: String? = null,
        upiId: String? = null
    ) {
        createNotificationChannel(context)

        // 1. Content Intent: Tapping notification body opens review screen prefilled
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "pending_review")
            putExtra("EXTRA_PENDING_ID", pendingId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            (pendingId * 10).toInt(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2. Action: + Add Expense
        val addIntent = Intent(context, TransactionNotificationActionReceiver::class.java).apply {
            action = TransactionNotificationActionReceiver.ACTION_ADD_EXPENSE
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
            putExtra(TransactionNotificationActionReceiver.EXTRA_NOTIFICATION_ID, pendingId.toInt())
        }
        val addPendingIntent = PendingIntent.getBroadcast(
            context,
            (pendingId * 10 + 1).toInt(),
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val addAction = NotificationCompat.Action.Builder(
            0,
            "+ Add Expense",
            addPendingIntent
        ).build()

        // 3. Action: Ignore
        val ignoreIntent = Intent(context, TransactionNotificationActionReceiver::class.java).apply {
            action = TransactionNotificationActionReceiver.ACTION_IGNORE
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
            putExtra(TransactionNotificationActionReceiver.EXTRA_NOTIFICATION_ID, pendingId.toInt())
        }
        val ignorePendingIntent = PendingIntent.getBroadcast(
            context,
            (pendingId * 10 + 2).toInt(),
            ignoreIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ignoreAction = NotificationCompat.Action.Builder(
            0,
            "Ignore",
            ignorePendingIntent
        ).build()

        // 4. Action: Delete
        val deleteIntent = Intent(context, TransactionNotificationActionReceiver::class.java).apply {
            action = TransactionNotificationActionReceiver.ACTION_DELETE
            putExtra(TransactionNotificationActionReceiver.EXTRA_PENDING_ID, pendingId)
            putExtra(TransactionNotificationActionReceiver.EXTRA_NOTIFICATION_ID, pendingId.toInt())
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            (pendingId * 10 + 3).toInt(),
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deleteAction = NotificationCompat.Action.Builder(
            0,
            "Delete",
            deletePendingIntent
        ).build()

        val amountStr = CurrencyUtils.formatPaiseToRupees(amountPaise)
        val payeeText = when {
            !merchantOrBank.isNullOrBlank() -> "at $merchantOrBank"
            !phoneNumber.isNullOrBlank() -> "to $phoneNumber"
            !upiId.isNullOrBlank() -> "to $upiId"
            else -> ""
        }
        val messageText = if (payeeText.isNotBlank()) "$amountStr detected $payeeText." else "$amountStr transaction detected."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("New Transaction Detected")
            .setContentText(messageText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$messageText\nTap an action below to add or dismiss without opening the app.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentPendingIntent)
            .addAction(addAction)
            .addAction(ignoreAction)
            .addAction(deleteAction)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(pendingId.toInt(), notification)
    }

    // Overload for backward compatibility
    fun showPendingTransactionNotification(
        context: Context,
        pendingId: Long,
        amountPaise: Long,
        merchantOrBank: String?
    ) {
        showPendingTransactionNotification(
            context = context,
            pendingId = pendingId,
            amountPaise = amountPaise,
            merchantOrBank = merchantOrBank,
            phoneNumber = null,
            upiId = null
        )
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(notificationId)
    }
}
