package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.data.sms.parser.AmountExtractor
import com.naman.rupeeflow.data.sms.parser.BankMessageParser
import com.naman.rupeeflow.data.sms.parser.NotificationCategory
import com.naman.rupeeflow.data.sms.parser.NotificationCategoryClassifier
import com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor
import com.naman.rupeeflow.data.sms.parser.ReferenceExtractor
import com.naman.rupeeflow.data.sms.parser.banks.*
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType

data class ParsedSmsResult(
    val isValidTransaction: Boolean,
    val amountPaise: Long = 0L,
    val type: TransactionType = TransactionType.EXPENSE,
    val bankName: String? = null,
    val accountLast4: String? = null,
    val merchant: String? = null,
    val referenceNumber: String? = null,
    val isOtp: Boolean = false,
    val isFailed: Boolean = false,
    val isBalanceInquiry: Boolean = false,
    val isPromotional: Boolean = false,
    val isUnknown: Boolean = false,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val timestamp: Long = System.currentTimeMillis(),
    val rawSender: String? = null,
    val rawBody: String = "",
    val phoneNumber: String? = null,
    val upiId: String? = null,
    val rejectionReason: String? = null
)

object SmsParser {

    // Modular Indian Bank & UPI message parser pipeline
    private val parsers: List<BankMessageParser> = listOf(
        UpiAppNotificationParser(),
        HdfcBankParser(),
        SbiBankParser(),
        IciciBankParser(),
        AxisBankParser(),
        KotakBankParser(),
        BankOfBarodaParser(),
        GenericIndianBankParser()
    )

    /**
     * Sanitizes raw SMS / notification text to mask sensitive account and card numbers
     * while preserving the last 4 digits for recognition.
     */
    fun sanitizeRawContent(content: String): String {
        return content.replace(Regex("\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?(\\d{4})\\b"), "XXXX-XXXX-XXXX-$1")
            .replace(Regex("\\b\\d{8,12}(\\d{4})\\b"), "XXXX$1")
    }

    /**
     * Multi-stage classification and extraction pipeline for incoming notifications and SMS messages.
     * Evaluates source, category, and completed transaction evidence prior to amount extraction.
     */
    fun parseNotification(
        title: String,
        body: String,
        packageName: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): ParsedSmsResult {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        val combined = when {
            trimmedTitle.isNotBlank() && trimmedBody.isNotBlank() -> "$trimmedTitle $trimmedBody"
            trimmedTitle.isNotBlank() -> trimmedTitle
            else -> trimmedBody
        }.trim()

        // 1. Notification Category Classification prior to amount extraction
        val classification = NotificationCategoryClassifier.classify(
            title = trimmedTitle,
            body = trimmedBody,
            senderOrPackage = packageName
        )

        when (classification.category) {
            NotificationCategory.OTP -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isOtp = true,
                    rejectionReason = "OTP_MESSAGE",
                    rawSender = packageName,
                    rawBody = sanitizeRawContent(combined),
                    timestamp = timestamp
                )
            }
            NotificationCategory.PROMOTIONAL -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isPromotional = true,
                    rejectionReason = "PROMOTIONAL_CONTENT",
                    rawSender = packageName,
                    rawBody = sanitizeRawContent(combined),
                    timestamp = timestamp
                )
            }
            NotificationCategory.FAILED_TRANSACTION -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isFailed = true,
                    rejectionReason = "FAILED_PAYMENT",
                    rawSender = packageName,
                    rawBody = sanitizeRawContent(combined),
                    timestamp = timestamp
                )
            }
            NotificationCategory.BALANCE_UPDATE -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isBalanceInquiry = true,
                    rejectionReason = "BALANCE_INQUIRY",
                    rawSender = packageName,
                    rawBody = sanitizeRawContent(combined),
                    timestamp = timestamp
                )
            }
            NotificationCategory.INFORMATIONAL -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    rejectionReason = "INFORMATIONAL_ALERT",
                    rawSender = packageName,
                    rawBody = sanitizeRawContent(combined),
                    timestamp = timestamp
                )
            }
            else -> {
                // Potential transaction candidate or ambiguous notification
            }
        }

        // 2. Run through Bank & UPI Parsers pipeline
        for (parser in parsers) {
            if (parser.canParse(packageName, combined)) {
                val result = parser.parse(packageName, combined, timestamp)
                if (result != null && result.isValidTransaction) {
                    return result
                } else if (result != null && (result.isUnknown || result.isPromotional || result.isFailed)) {
                    return result
                }
            }
        }

        // 3. Fallback for unclassified candidate
        val amountPaise = AmountExtractor.extract(combined)
        val recipientInfo = PaymentRecipientExtractor.extract(combined)

        return ParsedSmsResult(
            isValidTransaction = false,
            isUnknown = true,
            amountPaise = amountPaise,
            bankName = packageName,
            merchant = recipientInfo.merchantName,
            phoneNumber = recipientInfo.phoneNumber,
            upiId = recipientInfo.upiId,
            referenceNumber = recipientInfo.referenceNumber ?: ReferenceExtractor.extract(combined),
            rawSender = packageName,
            rawBody = sanitizeRawContent(combined),
            timestamp = timestamp,
            rejectionReason = if (amountPaise <= 0L) "INSUFFICIENT_EVIDENCE" else null
        )
    }

    /**
     * Standard entry point for SMS parsing. Delegated to full pipeline.
     */
    fun parse(smsBody: String, sender: String? = null, timestamp: Long = System.currentTimeMillis()): ParsedSmsResult {
        return parseNotification(title = "", body = smsBody, packageName = sender, timestamp = timestamp)
    }
}
