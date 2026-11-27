package com.naman.rupeeflow.data.email

import android.content.Context
import android.util.Base64
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.data.sms.TransactionNotificationHelper
import com.naman.rupeeflow.domain.matching.MerchantNormalizer
import com.naman.rupeeflow.domain.model.PendingStatus
import com.naman.rupeeflow.domain.model.TransactionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class HdfcGmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val account = applicationContext.getSharedPreferences(GmailSyncScheduler.PREFS, Context.MODE_PRIVATE)
            .getString(GmailSyncScheduler.ACCOUNT_KEY, null) ?: return@withContext Result.success()
        try {
            val credential = GoogleAccountCredential.usingOAuth2(
                applicationContext, listOf(GmailScopes.GMAIL_READONLY)
            ).apply { selectedAccountName = account }
            val gmail = Gmail.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(), credential
            ).setApplicationName("RupeeFlow").build()

            val refs = gmail.users().messages().list("me")
                .setQ("newer_than:7d {from:(hdfcbank.com) from:(hdfcbank.net)}")
                .setMaxResults(100L).execute().messages.orEmpty()

            val app = applicationContext as? RupeeFlowApplication ?: return@withContext Result.failure()
            val coordinator = TransactionIngestionCoordinator(app.appContainer.database)
            val repository = app.appContainer.pendingTransactionRepository
            for (ref in refs) {
                val message = gmail.users().messages().get("me", ref.id).setFormat("full").execute()
                processMessage(message, coordinator, repository)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun processMessage(
        message: Message,
        coordinator: TransactionIngestionCoordinator,
        repository: com.naman.rupeeflow.data.repository.PendingTransactionRepository
    ) {
        val headers = message.payload?.headers.orEmpty()
        val from = headers.firstOrNull { it.name.equals("From", true) }?.value
        val subject = headers.firstOrNull { it.name.equals("Subject", true) }?.value
        val body = extractBody(message.payload)
        val timestamp = message.internalDate ?: System.currentTimeMillis()
        val parsed = HdfcEmailParser.parse(from, subject, body, timestamp) ?: return
        val ref = parsed.upiReferenceNumber ?: parsed.referenceNumber

        if (repository.isDuplicate(
            amountPaise = parsed.amountPaise, timestamp = parsed.timestamp, refNumber = ref,
            merchant = parsed.merchant, bank = parsed.bankName ?: "HDFC Bank",
            accountLast4 = parsed.accountLast4, rawContent = parsed.rawBody
        )) return

        val entity = PendingTransactionEntity(
            detectedAmountPaise = parsed.amountPaise, detectedType = parsed.type,
            detectedBank = parsed.bankName ?: "HDFC Bank", detectedAccount = parsed.accountLast4,
            detectedMerchant = parsed.merchant, rawMerchant = parsed.merchant,
            merchantNormalized = MerchantNormalizer.normalize(parsed.merchant).takeIf(String::isNotBlank),
            sourcePackage = "gmail", phoneNumber = parsed.phoneNumber, upiId = parsed.upiId,
            referenceNumber = ref, upiReferenceNumber = parsed.upiReferenceNumber ?: ref,
            rawContent = parsed.rawBody, timestamp = parsed.timestamp,
            status = PendingStatus.PENDING, source = TransactionSource.EMAIL
        )
        val id = coordinator.insertIfNew(entity, message.id) ?: return
        TransactionNotificationHelper.showPendingTransactionNotification(
            applicationContext, id, parsed.amountPaise, parsed.merchant ?: parsed.bankName,
            parsed.phoneNumber, parsed.upiId
        )
    }

    private fun extractBody(part: MessagePart?): String {
        if (part == null) return ""
        val bodies = mutableListOf<String>()
        fun visit(node: MessagePart) {
            val data = node.body?.data
            if (data != null && (node.mimeType == "text/plain" || node.mimeType == "text/html")) {
                val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                bodies += String(bytes, StandardCharsets.UTF_8)
            }
            node.parts.orEmpty().forEach(::visit)
        }
        visit(part)
        return bodies.firstOrNull { it.isNotBlank() } ?: ""
    }
}
