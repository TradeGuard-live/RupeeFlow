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

class BankOfBarodaParser : BankMessageParser {

    override val bankName: String = "Bank of Baroda"

    override val supportedSenderKeywords: Set<String> = setOf(
        "BOBTXN", "BARODA", "BOB", "BOBSMS"
    )

    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|account|ac|card)\\s*(?:no\\.?)?\\s*[xX*]*(\\d{3,4})"
    )

    override fun canParse(sender: String?, body: String): Boolean {
        if (BankSenderNormalizer.matchesKeywords(sender, body, supportedSenderKeywords)) return true
        val upper = body.uppercase(Locale.ROOT)
        return upper.contains("BANK OF BARODA") || upper.contains("BOB WORLD")
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

        val hasDebit = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("paid") || lower.contains("withdrawn")
        val hasCredit = lower.contains("credited") || lower.contains("received") ||
                lower.contains("refund") || lower.contains("reversed")

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
            else -> PaymentMethod.UPI
        }

        return ParsedSmsResult(
            isValidTransaction = true,
            amountPaise = amountPaise,
            type = txnType,
            bankName = bankName,
            accountLast4 = accountLast4,
            referenceNumber = refNumber,
            paymentMethod = paymentMethod,
            timestamp = timestamp,
            rawSender = sender,
            rawBody = SmsParser.sanitizeRawContent(trimmed)
        )
    }
}

