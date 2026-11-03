package com.naman.rupeeflow.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.domain.model.PendingStatus
import com.naman.rupeeflow.domain.model.TransactionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val app = context.applicationContext as? RupeeFlowApplication ?: return
        val pendingRepo = app.appContainer.pendingTransactionRepository
        val userPrefsRepo = app.appContainer.userPreferencesRepository

        // Reconstruct multi-part SMS if needed
        val fullBodyBuilder = java.lang.StringBuilder()
        var sender: String? = null
        var timestamp = System.currentTimeMillis()

        for (sms in messages) {
            sender = sms.displayOriginatingAddress ?: sms.originatingAddress
            timestamp = sms.timestampMillis
            fullBodyBuilder.append(sms.displayMessageBody ?: sms.messageBody ?: "")
        }

        val fullBody = fullBodyBuilder.toString()
        if (fullBody.isBlank()) return

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = userPrefsRepo.userPreferencesFlow.first()
            if (!prefs.smsDetectionEnabled) return@launch

            // Parse message
            val parseResult = SmsParser.parse(fullBody, sender, timestamp)
            if (!parseResult.isValidTransaction) return@launch

            // Duplicate detection
            val isDup = pendingRepo.isDuplicate(
                amountPaise = parseResult.amountPaise,
                timestamp = parseResult.timestamp,
                refNumber = parseResult.referenceNumber
            )
            if (isDup) return@launch

            // Save as PendingTransaction
            val entity = PendingTransactionEntity(
                detectedAmountPaise = parseResult.amountPaise,
                detectedType = parseResult.type,
                detectedBank = parseResult.bankName,
                detectedAccount = parseResult.accountLast4,
                detectedMerchant = parseResult.merchant,
                referenceNumber = parseResult.referenceNumber,
                rawContent = fullBody,
                timestamp = parseResult.timestamp,
                status = PendingStatus.PENDING,
                source = TransactionSource.SMS
            )

            val pendingId = app.appContainer.database.pendingTransactionDao().insert(entity)

            // Trigger notification
            TransactionNotificationHelper.showPendingTransactionNotification(
                context = context,
                pendingId = pendingId,
                amountPaise = parseResult.amountPaise,
                merchantOrBank = parseResult.merchant ?: parseResult.bankName
            )
        }
    }
}
