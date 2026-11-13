package com.naman.rupeeflow.data.sms.parser.banks

import com.naman.rupeeflow.data.sms.ParsedSmsResult
import com.naman.rupeeflow.data.sms.SmsParser
import com.naman.rupeeflow.data.sms.parser.AmountExtractor
import com.naman.rupeeflow.data.sms.parser.BankMessageParser
import com.naman.rupeeflow.data.sms.parser.BankSenderNormalizer
import com.naman.rupeeflow.data.sms.parser.ReferenceExtractor
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
import java.util.Locale
import java.util.regex.Pattern

class SbiBankParser : BankMessageParser {

    override val bankName: String = "State Bank of India"

    override val supportedSenderKeywords: Set<String> = setOf(
        "SBIN", "SBIUPI", "SBISMS", "SBI"
    )

    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|account|ac)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with|in)?\\s*)?[xX*]*(\\d{3,4})"
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:to\\s+vpa|transfer\\s+to|paid\\s+to|sent\\s+to)\\s+([a-zA-Z0-9@._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|dated|\\.)|$)"),
        Pattern.compile("(?i)(?:at|towards|info:?|for)\\s+([a-zA-Z0-9&._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|dated|\\.)|$)")
    )

    override fun canParse(sender: String?, body: String): Boolean {
        if (BankSenderNormalizer.matchesKeywords(sender, body, supportedSenderKeywords)) return true
        val upper = body.uppercase(Locale.ROOT)
        return upper.contains("STATE BANK OF INDIA") || upper.contains("SBI USER")
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

        // Check for UPI Lite
        if (lower.contains("upi lite top-up") || lower.contains("upi lite topup")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_TOPUP,
                bankName = bankName,
                accountLast4 = accountLast4,
                merchant = "UPI Lite wallet",
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        val hasDebit = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("paid") || lower.contains("withdrawn") ||
                lower.contains("sent") || lower.contains("deducted")
        val hasCredit = lower.contains("credited") || lower.contains("received") ||
                lower.contains("deposited") || lower.contains("refund") || lower.contains("reversed")

        val txnType = when {
            lower.contains("refund") || lower.contains("reversed") -> TransactionType.REFUND
            hasCredit && !hasDebit -> TransactionType.INCOME
            hasDebit -> TransactionType.EXPENSE
            else -> TransactionType.EXPENSE
        }

        val paymentMethod = when {
            lower.contains("upi") -> PaymentMethod.UPI
            lower.contains("atm") || lower.contains("cash") || lower.contains("withdrawn") -> PaymentMethod.CASH
            lower.contains("credit card") || lower.contains("credit limit") || lower.contains("available limit") -> PaymentMethod.CREDIT_CARD
            lower.contains("debit card") || lower.contains("card") -> PaymentMethod.DEBIT_CARD
            lower.contains("yono") -> PaymentMethod.NET_BANKING
            else -> PaymentMethod.UPI
        }

        val recipient = com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor.extract(trimmed)
        return ParsedSmsResult(
            isValidTransaction = true,
            amountPaise = amountPaise,
            type = txnType,
            bankName = bankName,
            accountLast4 = accountLast4,
            merchant = recipient.merchantName ?: extractMerchant(trimmed),
            phoneNumber = recipient.phoneNumber,
            upiId = recipient.upiId,
            referenceNumber = refNumber,
            paymentMethod = paymentMethod,
            timestamp = timestamp,
            rawSender = sender,
            rawBody = SmsParser.sanitizeRawContent(trimmed)
        )
    }

    private fun extractMerchant(body: String): String? {
        val recipient = com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor.extract(body)
        if (recipient.merchantName != null) return recipient.merchantName

        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank()) {
                    val classified = com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor.classifyCandidate(candidate)
                    if (classified.merchantName != null) {
                        return classified.merchantName
                    }
                }
            }
        }
        return null
    }
}

