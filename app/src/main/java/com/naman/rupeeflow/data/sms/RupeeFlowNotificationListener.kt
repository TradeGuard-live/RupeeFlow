package com.naman.rupeeflow.data.sms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.domain.model.PendingStatus
import com.naman.rupeeflow.domain.model.TransactionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RupeeFlowNotificationListener : NotificationListenerService() {

    private val supportedPackages = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",                       // PhonePe
        "net.one97.paytm",                        // Paytm
        "in.org.npci.upiapp",                     // BHIM
        "com.dreamplug.androidapp",               // CRED
        "com.axis.mobile",                        // Axis
        "com.msf.kbank.mobile",                   // Kotak
        "com.csam.icici.bank.imobile",            // ICICI
        "com.snapwork.hdfc",                      // HDFC
        "com.sbi.lotusintouch"                    // SBI Yono
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val app = applicationContext as? RupeeFlowApplication ?: return
        val pendingRepo = app.appContainer.pendingTransactionRepository
        val userPrefsRepo = app.appContainer.userPreferencesRepository

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        val combined = "$title $text $bigText".trim()
        if (combined.isBlank()) return

        // Check if package is a financial app or contains banking terms
        val isFinancialApp = supportedPackages.contains(pkg) ||
                pkg.contains("bank", ignoreCase = true) ||
                pkg.contains("pay", ignoreCase = true) ||
                pkg.contains("upi", ignoreCase = true)

        if (!isFinancialApp) return

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = userPrefsRepo.userPreferencesFlow.first()
            if (!prefs.notificationListenerEnabled) return@launch

            val parseResult = SmsParser.parse(combined, pkg, sbn.postTime)
            if (!parseResult.isValidTransaction) return@launch

            val isDup = pendingRepo.isDuplicate(
                amountPaise = parseResult.amountPaise,
                timestamp = parseResult.timestamp,
                refNumber = parseResult.referenceNumber
            )
            if (isDup) return@launch

            val entity = PendingTransactionEntity(
                detectedAmountPaise = parseResult.amountPaise,
                detectedType = parseResult.type,
                detectedBank = parseResult.bankName ?: pkg,
                detectedAccount = parseResult.accountLast4,
                detectedMerchant = parseResult.merchant,
                referenceNumber = parseResult.referenceNumber,
                rawContent = combined,
                timestamp = parseResult.timestamp,
                status = PendingStatus.PENDING,
                source = TransactionSource.NOTIFICATION
            )

            val pendingId = app.appContainer.database.pendingTransactionDao().insert(entity)

            TransactionNotificationHelper.showPendingTransactionNotification(
                context = applicationContext,
                pendingId = pendingId,
                amountPaise = parseResult.amountPaise,
                merchantOrBank = parseResult.merchant ?: parseResult.bankName
            )
        }
    }
}
