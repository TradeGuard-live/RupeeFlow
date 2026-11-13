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

class HdfcBankParser : BankMessageParser {

    override val bankName: String = "HDFC Bank"

    override val supportedSenderKeywords: Set<String> = setOf(
        "HDFCBK", "HDFC", "HDFCB", "HDFCLT"
    )

    private val UPI_LITE_TOPUP_PATTERN = Pattern.compile(
        "(?i)UPI\\s*LITE\\s*(?:Top-up|topup|funding)"
    )

    private val UPI_LITE_PAYMENT_PATTERN = Pattern.compile(
        "(?i)(?:UPI\\s*LITE\\s*(?:payment|paid)|(?:paid|payment)\\s+.*(?:from|via|using)?\\s*UPI\\s*LITE)"
    )

    private val UPI_LITE_REFUND_PATTERN = Pattern.compile(
        "(?i)(?:refund|reversed)\\s*.*UPI\\s*LITE"
    )

    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|acct|account|card)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with|in)?\\s*)?[xX*]*(\\d{3,4})"
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:to\\s+vpa|paid\\s+to|sent\\s+to|transferred\\s+to|\\bto\\b)\\s+([a-zA-Z0-9&._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|dated|\\.)|$)"),
        Pattern.compile("(?i)(?:at|towards|info:?|for)\\s+([a-zA-Z0-9&._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|dated|\\.)|$)"),
        Pattern.compile("(?i)(?:vpa\\s+)([a-zA-Z0-9@._\\-]+)")
    )

    override fun canParse(sender: String?, body: String): Boolean {
        if (BankSenderNormalizer.matchesKeywords(sender, body, supportedSenderKeywords)) return true
        val upper = body.uppercase(Locale.ROOT)
        return upper.contains("HDFC BANK") || (upper.contains("UPI LITE") && sender?.contains("HDFC", ignoreCase = true) == true)
    }

    override fun parse(sender: String?, body: String, timestamp: Long): ParsedSmsResult? {
        val trimmed = body.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        val amountPaise = AmountExtractor.extract(trimmed)
        if (amountPaise <= 0L) return null

        val refNumber = ReferenceExtractor.extract(trimmed)

        // Account number extraction
        var accountLast4: String? = null
        val acMatcher = ACCOUNT_PATTERN.matcher(trimmed)
        if (acMatcher.find()) {
            accountLast4 = acMatcher.group(1)
        }

        // 1. Check for UPI Lite Top-up
        if (UPI_LITE_TOPUP_PATTERN.matcher(trimmed).find()) {
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

        // 2. Check for UPI Lite Payment
        if (UPI_LITE_PAYMENT_PATTERN.matcher(trimmed).find()) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_PAYMENT,
                bankName = bankName,
                accountLast4 = accountLast4,
                merchant = extractMerchant(trimmed),
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 3. Check for UPI Lite Refund
        if (UPI_LITE_REFUND_PATTERN.matcher(trimmed).find()) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_REFUND,
                bankName = bankName,
                accountLast4 = accountLast4,
                merchant = extractMerchant(trimmed),
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 4. Standard transactions
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
            lower.contains("credit card") -> PaymentMethod.CREDIT_CARD
            lower.contains("debit card") || lower.contains("card") -> PaymentMethod.DEBIT_CARD
            lower.contains("atm") || lower.contains("cash") -> PaymentMethod.CASH
            lower.contains("netbanking") || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps") -> PaymentMethod.BANK_TRANSFER
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
            while (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank()) {
                    val cleaned = candidate.take(30).trimEnd('.', ',')
                    val lowerCandidate = cleaned.lowercase(Locale.ROOT)
                    if (lowerCandidate != "vpa" &&
                        lowerCandidate != "upi" &&
                        lowerCandidate != "upi lite" &&
                        !lowerCandidate.startsWith("hdfc") &&
                        !lowerCandidate.startsWith("your a/c") &&
                        !lowerCandidate.startsWith("account")
                    ) {
                        val classified = com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor.classifyCandidate(cleaned)
                        if (classified.merchantName != null) {
                            return classified.merchantName
                        }
                    }
                }
            }
        }
        return null
    }
}

