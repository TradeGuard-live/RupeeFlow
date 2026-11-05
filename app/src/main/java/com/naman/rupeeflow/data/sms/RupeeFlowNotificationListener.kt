package com.naman.rupeeflow.data.sms

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.naman.rupeeflow.RupeeFlowApplication
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.domain.model.PendingStatus
import com.naman.rupeeflow.domain.model.TransactionSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

class RupeeFlowNotificationListener : NotificationListenerService() {

    // Managed coroutine lifecycle
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)

    // Thread-safe deduplication lock and recent cache
    private val processedKeys = Collections.synchronizedSet(LinkedHashSet<String>())
    private val mutex = Mutex()

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
        "com.sbi.lotusintouch",                   // SBI Yono
        "in.amazon.mShop.android.shopping"        // Amazon Pay
    )

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Skip grouped summary notifications and ongoing background alerts
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val pkg = sbn.packageName ?: return
        val extras = notification.extras ?: return

        val app = applicationContext as? RupeeFlowApplication ?: return
        val pendingRepo = app.appContainer.pendingTransactionRepository
        val userPrefsRepo = app.appContainer.userPreferencesRepository

        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: ""

        // Avoid repeating text when bigText contains text
        val content = when {
            bigText.isNotBlank() && bigText.contains(text) -> bigText
            text.isNotBlank() && text.contains(bigText) -> text
            else -> "$text $bigText".trim()
        }
        val combined = "$title $content".trim()
        if (combined.isBlank()) return

        // Fast in-memory deduplication check
        val dedupeKey = "${sbn.key}_${notification.`when`}"
        if (!processedKeys.add(dedupeKey)) return
        if (processedKeys.size > 200) {
            val iterator = processedKeys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        // Check if package is a financial app or contains banking terms
        val isFinancialApp = supportedPackages.contains(pkg) ||
                pkg.contains("bank", ignoreCase = true) ||
                pkg.contains("pay", ignoreCase = true) ||
                pkg.contains("upi", ignoreCase = true)

        if (!isFinancialApp) return

        serviceScope.launch {
            try {
                val prefs = userPrefsRepo.userPreferencesFlow.first()
                if (!prefs.notificationListenerEnabled) return@launch

                val parseResult = SmsParser.parse(combined, pkg, sbn.postTime)
                if (!parseResult.isValidTransaction) return@launch

                mutex.withLock {
                    val isDup = pendingRepo.isDuplicate(
                        amountPaise = parseResult.amountPaise,
                        timestamp = parseResult.timestamp,
                        refNumber = parseResult.referenceNumber,
                        merchant = parseResult.merchant,
                        bank = parseResult.bankName ?: pkg,
                        accountLast4 = parseResult.accountLast4,
                        rawContent = combined
                    )
                    if (isDup) return@withLock

                    val entity = PendingTransactionEntity(
                        detectedAmountPaise = parseResult.amountPaise,
                        detectedType = parseResult.type,
                        detectedBank = parseResult.bankName ?: pkg,
                        detectedAccount = parseResult.accountLast4,
                        detectedMerchant = parseResult.merchant,
                        referenceNumber = parseResult.referenceNumber,
                        rawContent = parseResult.rawBody,
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
            } catch (_: Exception) {
                // Safeguard against unhandled exceptions during background processing
            }
        }
    }
}
