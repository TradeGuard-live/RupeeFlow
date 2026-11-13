package com.naman.rupeeflow.data.sms.parser.banks

import com.naman.rupeeflow.data.sms.ParsedSmsResult
import com.naman.rupeeflow.data.sms.SmsParser
import com.naman.rupeeflow.data.sms.parser.AmountExtractor
import com.naman.rupeeflow.data.sms.parser.BankMessageParser
import com.naman.rupeeflow.data.sms.parser.NotificationCategory
import com.naman.rupeeflow.data.sms.parser.NotificationCategoryClassifier
import com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor
import com.naman.rupeeflow.data.sms.parser.ReferenceExtractor
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
import java.util.Locale
import java.util.regex.Pattern

class UpiAppNotificationParser : BankMessageParser {

    override val bankName: String = "UPI App"

    override val supportedSenderKeywords: Set<String> = setOf(
        "GPAY", "GOOGLEPAY", "PHONEPE", "PAYTM", "BHIM", "AMAZON", "CRED"
    )

    private val PACKAGE_MAP = mapOf(
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "com.phonepe.app" to "PhonePe",
        "net.one97.paytm" to "Paytm",
        "in.org.npci.upiapp" to "BHIM",
        "com.dreamplug.androidapp" to "CRED",
        "in.amazon.mShop.android.shopping" to "Amazon Pay"
    )

    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|acct|account|card)\\s*(?:ending\\s*(?:with|in)?\\s*)?[xX*]*(\\d{3,4})"
    )

    override fun canParse(sender: String?, body: String): Boolean {
        if (sender != null && PACKAGE_MAP.containsKey(sender)) return true
        val upperSender = sender?.uppercase(Locale.ROOT) ?: ""
        for (kw in supportedSenderKeywords) {
            if (upperSender.contains(kw)) return true
        }
        val upperBody = body.uppercase(Locale.ROOT)
        if (upperBody.contains("GOOGLE PAY") || upperBody.contains("PHONEPE") ||
            upperBody.contains("PAYTM") || upperBody.contains("BHIM") ||
            (Regex("(?i)\\bCRED\\b").containsMatchIn(body) && !upperBody.contains("CREDIT"))
        ) {
            return true
        }
        return false
    }

    override fun parse(sender: String?, body: String, timestamp: Long): ParsedSmsResult? {
        val trimmed = body.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        val providerName = PACKAGE_MAP[sender] ?: when {
            lower.contains("google pay") || lower.contains("gpay") -> "Google Pay"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("paytm") -> "Paytm"
            lower.contains("bhim") -> "BHIM"
            Regex("(?i)\\bcred\\b").containsMatchIn(lower) && !lower.contains("credit") -> "CRED"
            lower.contains("amazon") -> "Amazon Pay"
            else -> "UPI App"
        }

        // 1. Classification before amount extraction: reject promotional offers, ads, loans, referral campaigns
        val classification = NotificationCategoryClassifier.classify(
            title = "",
            body = trimmed,
            senderOrPackage = sender
        )

        when (classification.category) {
            NotificationCategory.PROMOTIONAL -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isPromotional = true,
                    rejectionReason = "PROMOTIONAL_CONTENT",
                    bankName = providerName,
                    rawSender = sender,
                    rawBody = SmsParser.sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
            NotificationCategory.FAILED_TRANSACTION -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isFailed = true,
                    rejectionReason = "FAILED_PAYMENT",
                    bankName = providerName,
                    rawSender = sender,
                    rawBody = SmsParser.sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
            NotificationCategory.OTP -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isOtp = true,
                    rejectionReason = "OTP_MESSAGE",
                    bankName = providerName,
                    rawSender = sender,
                    rawBody = SmsParser.sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
            NotificationCategory.BALANCE_UPDATE -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isBalanceInquiry = true,
                    rejectionReason = "BALANCE_INQUIRY",
                    bankName = providerName,
                    rawSender = sender,
                    rawBody = SmsParser.sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
            NotificationCategory.INFORMATIONAL -> {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    rejectionReason = "INFORMATIONAL_ALERT",
                    bankName = providerName,
                    rawSender = sender,
                    rawBody = SmsParser.sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
            else -> {
                // Continue parsing
            }
        }

        // 2. Extract Amount
        val amountPaise = AmountExtractor.extract(trimmed)
        if (amountPaise <= 0L) return null

        val recipientInfo = PaymentRecipientExtractor.extract(trimmed)
        val refNumber = recipientInfo.referenceNumber ?: ReferenceExtractor.extract(trimmed)

        var accountLast4: String? = null
        val acMatcher = ACCOUNT_PATTERN.matcher(trimmed)
        if (acMatcher.find()) {
            accountLast4 = acMatcher.group(1)
        }

        // 3. If amount is present but lacks sufficient completed payment evidence (e.g. ₹13,000 without past-tense debit/credit confirmation)
        if (!classification.hasConfirmedTransactionEvidence) {
            val isPendingReview = classification.category == NotificationCategory.PENDING_TRANSACTION
            return ParsedSmsResult(
                isValidTransaction = false,
                isUnknown = true, // Sent to review screen prefilled
                rejectionReason = if (isPendingReview) "PENDING_PAYMENT" else "INSUFFICIENT_EVIDENCE",
                amountPaise = amountPaise,
                bankName = providerName,
                accountLast4 = accountLast4,
                merchant = recipientInfo.merchantName,
                phoneNumber = recipientInfo.phoneNumber,
                upiId = recipientInfo.upiId,
                referenceNumber = refNumber,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 4. UPI Lite Top-up
        if (lower.contains("upi lite top-up") || lower.contains("upi lite topup") || lower.contains("added money to upi lite")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_TOPUP,
                bankName = providerName,
                merchant = recipientInfo.merchantName ?: "UPI Lite wallet",
                phoneNumber = recipientInfo.phoneNumber,
                upiId = recipientInfo.upiId,
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 5. UPI Lite Payment
        if (lower.contains("paid using upi lite") || lower.contains("upi lite payment")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_PAYMENT,
                bankName = providerName,
                merchant = recipientInfo.merchantName,
                phoneNumber = recipientInfo.phoneNumber,
                upiId = recipientInfo.upiId,
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 6. Direction & Transaction Type
        val hasDebit = lower.contains("paid") || lower.contains("sent to") ||
                (lower.contains("sent") && !lower.contains("sent you")) ||
                lower.contains("debited") || lower.contains("spent") || lower.contains("transferred")
        val hasCredit = lower.contains("received") || lower.contains("credited") ||
                lower.contains("cashback") || lower.contains("refund") || lower.contains("reversed") ||
                lower.contains("sent you")

        val txnType = when {
            lower.contains("refund") || lower.contains("reversed") -> TransactionType.REFUND
            lower.contains("cashback") || lower.contains("sent you") -> TransactionType.INCOME
            hasCredit && !hasDebit -> TransactionType.INCOME
            hasDebit -> TransactionType.EXPENSE
            else -> TransactionType.EXPENSE
        }

        return ParsedSmsResult(
            isValidTransaction = true,
            amountPaise = amountPaise,
            type = txnType,
            bankName = providerName,
            accountLast4 = accountLast4,
            merchant = recipientInfo.merchantName,
            phoneNumber = recipientInfo.phoneNumber,
            upiId = recipientInfo.upiId,
            referenceNumber = refNumber,
            paymentMethod = PaymentMethod.UPI,
            timestamp = timestamp,
            rawSender = sender,
            rawBody = SmsParser.sanitizeRawContent(trimmed)
        )
    }
}
