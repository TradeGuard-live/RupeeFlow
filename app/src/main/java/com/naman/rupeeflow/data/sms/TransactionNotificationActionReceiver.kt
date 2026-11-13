package com.naman.rupeeflow.data.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.naman.rupeeflow.MainActivity
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.PendingTransactionRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.PendingStatus
import com.naman.rupeeflow.domain.model.PendingTransaction
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class TransactionNotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ADD_EXPENSE = "com.naman.rupeeflow.ACTION_ADD_EXPENSE"
        const val ACTION_IGNORE = "com.naman.rupeeflow.ACTION_IGNORE"
        const val ACTION_DELETE = "com.naman.rupeeflow.ACTION_DELETE"

        const val EXTRA_PENDING_ID = "EXTRA_PENDING_ID"
        const val EXTRA_NOTIFICATION_ID = "EXTRA_NOTIFICATION_ID"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val pendingId = intent.getLongExtra(EXTRA_PENDING_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, pendingId.toInt())

        if (pendingId <= 0L) {
            cancelNotification(context, notificationId)
            return
        }

        val app = context.applicationContext as? RupeeFlowApplication ?: return
        val pendingRepo = app.appContainer.pendingTransactionRepository
        val txnRepo = app.appContainer.transactionRepository
        val categoryRepo = app.appContainer.categoryRepository

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_ADD_EXPENSE -> {
                        handleAddExpense(
                            context = context,
                            pendingId = pendingId,
                            notificationId = notificationId,
                            pendingRepo = pendingRepo,
                            txnRepo = txnRepo,
                            categoryRepo = categoryRepo
                        )
                    }
                    ACTION_IGNORE -> {
                        handleIgnore(
                            pendingId = pendingId,
                            notificationId = notificationId,
                            context = context,
                            pendingRepo = pendingRepo
                        )
                    }
                    ACTION_DELETE -> {
                        handleDelete(
                            pendingId = pendingId,
                            notificationId = notificationId,
                            context = context,
                            pendingRepo = pendingRepo
                        )
                    }
                }
            } catch (_: Exception) {
                // Safeguard against background thread termination exceptions
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAddExpense(
        context: Context,
        pendingId: Long,
        notificationId: Int,
        pendingRepo: PendingTransactionRepository,
        txnRepo: TransactionRepository,
        categoryRepo: CategoryRepository
    ) {
        val pending = pendingRepo.getById(pendingId)
        if (pending == null) {
            cancelNotification(context, notificationId)
            return
        }

        // Idempotency: If already approved/resolved, do not create duplicate transactions
        if (pending.status == PendingStatus.RESOLVED) {
            cancelNotification(context, notificationId)
            return
        }

        // If pending detection has low confidence or insufficient info, open RupeeFlow's review screen prefilled
        if (pending.status == PendingStatus.UNKNOWN || pending.detectedAmountPaise <= 0L) {
            openReviewScreen(context, pendingId)
            cancelNotification(context, notificationId)
            return
        }

        // Resolve category
        val categories = categoryRepo.getAllCategoriesList()
        val categoryId = resolveCategoryId(pending, categories)

        val title = when {
            !pending.detectedMerchant.isNullOrBlank() -> pending.detectedMerchant
            !pending.phoneNumber.isNullOrBlank() -> "Payment to ${pending.phoneNumber}"
            !pending.detectedBank.isNullOrBlank() -> pending.detectedBank
            else -> "Expense"
        }

        val paymentMethod = when (pending.detectedType) {
            TransactionType.UPI_LITE_TOPUP, TransactionType.UPI_LITE_PAYMENT -> PaymentMethod.UPI_LITE
            else -> PaymentMethod.UPI
        }

        val txn = Transaction(
            title = title,
            amountPaise = pending.detectedAmountPaise,
            categoryId = categoryId,
            transactionDate = pending.timestamp,
            paymentMethod = paymentMethod,
            transactionType = pending.detectedType,
            transactionStatus = TransactionStatus.ACTIVE,
            merchantName = pending.detectedMerchant,
            accountSource = pending.detectedAccount,
            referenceNumber = pending.referenceNumber,
            source = pending.source,
            phoneNumber = pending.phoneNumber,
            upiId = pending.upiId,
            latitude = pending.latitude,
            longitude = pending.longitude,
            accuracyMeters = pending.accuracyMeters,
            locationCapturedAt = pending.locationCapturedAt,
            locationSource = pending.locationSource,
            locationLabel = pending.locationLabel,
            googleMapsUrl = pending.googleMapsUrl
        )

        txnRepo.saveTransaction(txn)
        pendingRepo.resolvePendingTransaction(pendingId)
        cancelNotification(context, notificationId)
    }

    private suspend fun handleIgnore(
        pendingId: Long,
        notificationId: Int,
        context: Context,
        pendingRepo: PendingTransactionRepository
    ) {
        val pending = pendingRepo.getById(pendingId)
        if (pending == null || pending.status == PendingStatus.RESOLVED) {
            cancelNotification(context, notificationId)
            return
        }
        pendingRepo.ignorePendingTransaction(pendingId)
        cancelNotification(context, notificationId)
    }

    private suspend fun handleDelete(
        pendingId: Long,
        notificationId: Int,
        context: Context,
        pendingRepo: PendingTransactionRepository
    ) {
        val pending = pendingRepo.getById(pendingId)
        if (pending == null) {
            cancelNotification(context, notificationId)
            return
        }
        // Safety: If detection was already approved, never delete approved financial transaction records
        if (pending.status == PendingStatus.RESOLVED) {
            cancelNotification(context, notificationId)
            return
        }
        pendingRepo.deletePendingTransaction(pendingId)
        cancelNotification(context, notificationId)
    }

    private fun resolveCategoryId(pending: PendingTransaction, categories: List<Category>): Long {
        if (categories.isEmpty()) return 1L
        val merchant = pending.detectedMerchant?.lowercase(Locale.ROOT) ?: ""
        val matched = when {
            merchant.contains("food") || merchant.contains("swiggy") || merchant.contains("zomato") ||
                    merchant.contains("restaurant") || merchant.contains("cafe") || merchant.contains("chai") ->
                categories.firstOrNull { it.name.equals("Food", ignoreCase = true) || it.name.equals("Food & Drinks", ignoreCase = true) }
            merchant.contains("uber") || merchant.contains("ola") || merchant.contains("metro") || merchant.contains("fuel") ->
                categories.firstOrNull { it.name.equals("Transport", ignoreCase = true) }
            merchant.contains("amazon") || merchant.contains("flipkart") || merchant.contains("myntra") || merchant.contains("zepto") || merchant.contains("blinkit") ->
                categories.firstOrNull { it.name.equals("Groceries", ignoreCase = true) || it.name.equals("Shopping", ignoreCase = true) }
            else -> null
        }
        return matched?.id ?: categories.firstOrNull { it.isDefault }?.id ?: categories.first().id
    }

    private fun openReviewScreen(context: Context, pendingId: Long) {
        val reviewIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", "pending_review")
            putExtra("EXTRA_PENDING_ID", pendingId)
        }
        context.startActivity(reviewIntent)
    }

    private fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(notificationId)
    }
}

