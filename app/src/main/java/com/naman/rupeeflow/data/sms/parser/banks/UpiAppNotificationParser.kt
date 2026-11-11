package com.naman.rupeeflow.data.sms.parser.banks

import com.naman.rupeeflow.data.sms.ParsedSmsResult
import com.naman.rupeeflow.data.sms.SmsParser
import com.naman.rupeeflow.data.sms.parser.AmountExtractor
import com.naman.rupeeflow.data.sms.parser.BankMessageParser
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

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:paid\\s+to|sent\\s+to|to)\\s+([a-zA-Z0-9@._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|using|via|successful|done)|$)"),
        Pattern.compile("(?i)(?:received\\s+from|from)\\s+([a-zA-Z0-9@._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|using|via|successful|done)|$)")
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

        val amountPaise = AmountExtractor.extract(trimmed)
        if (amountPaise <= 0L) return null

        val refNumber = ReferenceExtractor.extract(trimmed)

        var accountLast4: String? = null
        val acMatcher = ACCOUNT_PATTERN.matcher(trimmed)
        if (acMatcher.find()) {
            accountLast4 = acMatcher.group(1)
        }

        val providerName = PACKAGE_MAP[sender] ?: when {
            lower.contains("google pay") || lower.contains("gpay") -> "Google Pay"
            lower.contains("phonepe") -> "PhonePe"
            lower.contains("paytm") -> "Paytm"
            lower.contains("bhim") -> "BHIM"
            Regex("(?i)\\bcred\\b").containsMatchIn(lower) && !lower.contains("credit") -> "CRED"
            lower.contains("amazon") -> "Amazon Pay"
            else -> "UPI App"
        }

        // 1. UPI Lite top-up in UPI apps
        if (lower.contains("upi lite top-up") || lower.contains("upi lite topup") || lower.contains("added money to upi lite")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_TOPUP,
                bankName = providerName,
                merchant = "UPI Lite wallet",
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 2. UPI Lite payment in UPI apps
        if (lower.contains("paid using upi lite") || lower.contains("upi lite payment")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_PAYMENT,
                bankName = providerName,
                merchant = extractMerchant(trimmed),
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        val hasDebit = lower.contains("paid") || lower.contains("sent") ||
                lower.contains("debited") || lower.contains("spent")
        val hasCredit = lower.contains("received") || lower.contains("credited") ||
                lower.contains("cashback") || lower.contains("refund")

        val txnType = when {
            lower.contains("refund") || lower.contains("cashback") -> TransactionType.REFUND
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
            merchant = extractMerchant(trimmed),
            referenceNumber = refNumber,
            paymentMethod = PaymentMethod.UPI,
            timestamp = timestamp,
            rawSender = sender,
            rawBody = SmsParser.sanitizeRawContent(trimmed)
        )
    }

    private fun extractMerchant(body: String): String? {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && !candidate.equals("vpa", ignoreCase = true)) {
                    return candidate.take(30).trimEnd('.', ',')
                }
            }
        }
        return null
    }
}

