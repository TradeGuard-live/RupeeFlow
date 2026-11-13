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

class GenericIndianBankParser : BankMessageParser {

    override val bankName: String = "Indian Bank"

    // Multi-bank identifier map mapping normalized sender tokens / body keywords to bank display names
    private val BANK_MAP = mapOf(
        "PNBSMS" to "Punjab National Bank",
        "PNB" to "Punjab National Bank",
        "PUNJAB" to "Punjab National Bank",
        "CANBNK" to "Canara Bank",
        "CANARA" to "Canara Bank",
        "UNIONB" to "Union Bank of India",
        "UBIN" to "Union Bank of India",
        "BOI" to "Bank of India",
        "BOITXN" to "Bank of India",
        "INDIANB" to "Indian Bank",
        "IDBIBK" to "IDBI Bank",
        "IDBI" to "IDBI Bank",
        "CENTRAL" to "Central Bank of India",
        "CBIN" to "Central Bank of India",
        "IDFCFB" to "IDFC FIRST Bank",
        "IDFC" to "IDFC FIRST Bank",
        "INDBNK" to "IndusInd Bank",
        "INDUS" to "IndusInd Bank",
        "YESBNK" to "Yes Bank",
        "YESB" to "Yes Bank",
        "FEDBNK" to "Federal Bank",
        "FEDERAL" to "Federal Bank",
        "RBLBNK" to "RBL Bank",
        "RBL" to "RBL Bank",
        "AUBANK" to "AU Small Finance Bank",
        "BANDHAN" to "Bandhan Bank",
        "SIBLTD" to "South Indian Bank",
        "SOUTHIN" to "South Indian Bank",
        "KTKBNK" to "Karnataka Bank",
        "KARNATAKA" to "Karnataka Bank",
        "UCOBNK" to "UCO Bank",
        "UCO" to "UCO Bank",
        "MAHABK" to "Bank of Maharashtra",
        "PSB" to "Punjab & Sind Bank",
        "IOB" to "Indian Overseas Bank",
        "IOBTXN" to "Indian Overseas Bank",
        "DCB" to "DCB Bank",
        "DCBBNK" to "DCB Bank",
        "EQUITAS" to "Equitas Small Finance Bank",
        "UJJIVAN" to "Ujjivan Small Finance Bank",
        "JANABK" to "Jana Small Finance Bank",
        "ESAF" to "ESAF Small Finance Bank"
    )

    override val supportedSenderKeywords: Set<String> = BANK_MAP.keys

    private val ACCOUNT_PATTERN = Pattern.compile(
        "(?i)(?:a/c|account|acct|ac|card)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with|in)?\\s*)?[xX*]*(\\d{3,4})"
    )

    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:to\\s+vpa|transfer\\s+to|paid\\s+to|sent\\s+to)\\s+([a-zA-Z0-9@._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|dated|\\.)|$)"),
        Pattern.compile("(?i)(?:at|towards|for|info:?)\\s+([a-zA-Z0-9&._\\-\\s]{3,30}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|dated|\\.)|$)"),
        Pattern.compile("(?i)(?:vpa\\s+)([a-zA-Z0-9@._\\-]+)")
    )

    override fun canParse(sender: String?, body: String): Boolean {
        val normSender = BankSenderNormalizer.normalize(sender)
        val upperBody = body.uppercase(Locale.ROOT)

        for (token in BANK_MAP.keys) {
            if (normSender.contains(token) || upperBody.contains(token)) return true
        }

        // Generic banking indicators
        val hasBankIndicator = upperBody.contains("A/C") || upperBody.contains("ACCOUNT") ||
                upperBody.contains("BANK") || upperBody.contains("UPI") || normSender.contains("BANK")
        val hasTransactionKeyword = upperBody.contains("DEBITED") || upperBody.contains("CREDITED") ||
                upperBody.contains("REFUND") || upperBody.contains("TRANSFERRED") ||
                upperBody.contains("PAID") || upperBody.contains("SPENT")

        return hasBankIndicator && hasTransactionKeyword
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

        val identifiedBank = identifyBank(sender, trimmed)

        // 1. UPI Lite top-up recognition
        if (lower.contains("upi lite top-up") || lower.contains("upi lite topup") || lower.contains("upi lite funding")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_TOPUP,
                bankName = identifiedBank,
                accountLast4 = accountLast4,
                merchant = "UPI Lite wallet",
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        // 2. UPI Lite payment recognition
        if (lower.contains("upi lite payment") || lower.contains("paid using upi lite")) {
            return ParsedSmsResult(
                isValidTransaction = true,
                amountPaise = amountPaise,
                type = TransactionType.UPI_LITE_PAYMENT,
                bankName = identifiedBank,
                accountLast4 = accountLast4,
                merchant = extractMerchant(trimmed),
                referenceNumber = refNumber,
                paymentMethod = PaymentMethod.UPI_LITE,
                timestamp = timestamp,
                rawSender = sender,
                rawBody = SmsParser.sanitizeRawContent(trimmed)
            )
        }

        val hasDebit = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("paid") || lower.contains("withdrawn") ||
                (lower.contains("sent") && !lower.contains("sent you"))
        val hasCredit = lower.contains("credited") || lower.contains("received") ||
                lower.contains("deposited") || lower.contains("refund") || lower.contains("reversed") ||
                lower.contains("sent you")

        val txnType = when {
            lower.contains("refund") || lower.contains("reversed") -> TransactionType.REFUND
            lower.contains("sent you") -> TransactionType.INCOME
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
            bankName = identifiedBank,
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

    private fun identifyBank(sender: String?, body: String): String {
        val norm = BankSenderNormalizer.normalize(sender)
        val upperBody = body.uppercase(Locale.ROOT)

        for ((token, name) in BANK_MAP) {
            if (norm.contains(token) || upperBody.contains(token)) {
                return name
            }
        }
        return "Indian Bank"
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

