package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
import com.naman.rupeeflow.domain.util.CurrencyUtils
import java.util.Locale
import java.util.regex.Pattern

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
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val timestamp: Long = System.currentTimeMillis(),
    val rawSender: String? = null,
    val rawBody: String = ""
)

object SmsParser {

    private val OTP_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(otp|one\\s*time\\s*password|verification\\s*code|secret\\s*code)\\b"),
        Pattern.compile("(?i)\\b(is\\s+your\\s+otp|use\\s+code|do\\s+not\\s+share)\\b")
    )

    private val FAILED_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(declined|failed|unsuccessful|cannot\\s*be\\s*processed|insufficient\\s*funds|rejected)\\b")
    )

    private val BALANCE_ONLY_PATTERNS = listOf(
        Pattern.compile("(?i).*(available\\s*balance|avl\\s*bal|clear\\s*bal|bal\\s*is).*")
    )

    // Amount regexes
    private val AMOUNT_PATTERNS = listOf(
        // e.g. "debited by Rs. 850.00", "debited by INR 1,200", "Rs 500 debited"
        Pattern.compile("(?i)(?:rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        Pattern.compile("(?i)(?:debited|credited|spent|paid|withdrawn|sent)\\s*(?:by|for|of)?\\s*(?:rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        Pattern.compile("(?i)([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr)"),
        Pattern.compile("(?i)(?:amount\\s*of)\\s*(?:rs\\.?|inr)?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    )

    // Account / Card
    private val ACCOUNT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:a/c|acct|account|card)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with|in)?\\s*)?[xX*]*(\\d{3,4})"),
        Pattern.compile("(?i)[xX*]{2,}(\\d{3,4})")
    )

    // Reference / UTR
    private val REF_PATTERNS = listOf(
        Pattern.compile("(?i)(?:upi\\s*ref(?:\\s*no)?|ref(?:\\s*no)?|rrn|utr|txn\\s*id)[\\s:]*([0-9a-zA-Z]{6,20})"),
        Pattern.compile("(?i)(?:ref\\s*no\\.?)\\s*([0-9a-zA-Z]{6,20})")
    )

    // Merchant / Recipient
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:to\\s+vpa|to)\\s+([a-zA-Z0-9@._\\-]{3,30})(?:\\s+(?:on|ref|upi|avl|bal|using|via|\\.)|$)"),
        Pattern.compile("(?i)(?:at|towards|info:?)\\s+([a-zA-Z0-9&._\\-\\s]{3,25}?)(?:\\s+(?:on|ref|upi|avl|bal|using|via|\\.)|$)"),
        Pattern.compile("(?i)(?:vpa\\s+)([a-zA-Z0-9@._\\-]+)")
    )

    // Bank identification from sender or body
    private val BANK_IDENTIFIERS = mapOf(
        "HDFC" to "HDFC Bank",
        "SBI" to "State Bank of India",
        "ICICI" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "KOTAK" to "Kotak Mahindra Bank",
        "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda",
        "IDFC" to "IDFC FIRST Bank",
        "INDUS" to "IndusInd Bank",
        "PNB" to "Punjab National Bank",
        "CANARA" to "Canara Bank",
        "PAYTM" to "Paytm Payments Bank",
        "FEDERAL" to "Federal Bank",
        "YES" to "Yes Bank",
        "RBL" to "RBL Bank",
        "UNION" to "Union Bank",
        "GPAY" to "Google Pay",
        "PHONEPE" to "PhonePe",
        "CRED" to "CRED"
    )

    fun parse(smsBody: String, sender: String? = null, timestamp: Long = System.currentTimeMillis()): ParsedSmsResult {
        val trimmed = smsBody.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check for OTP messages
        for (pattern in OTP_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                // If it mentions OTP, it is NOT a confirmed transaction
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isOtp = true,
                    rawSender = sender,
                    rawBody = trimmed,
                    timestamp = timestamp
                )
            }
        }

        // 2. Check for Failed transactions
        for (pattern in FAILED_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isFailed = true,
                    rawSender = sender,
                    rawBody = trimmed,
                    timestamp = timestamp
                )
            }
        }

        // 3. Check for pure balance enquiry
        val hasDebit = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("paid") || lower.contains("withdrawn") ||
                lower.contains("sent") || lower.contains("deducted")
        val hasCredit = lower.contains("credited") || lower.contains("received") ||
                lower.contains("deposited") || lower.contains("refund") || lower.contains("reversed")

        if (!hasDebit && !hasCredit) {
            // Neither debit nor credit -> balance inquiry or promotional
            return ParsedSmsResult(
                isValidTransaction = false,
                isBalanceInquiry = true,
                rawSender = sender,
                rawBody = trimmed,
                timestamp = timestamp
            )
        }

        // 4. Extract Amount
        var amountPaise = 0L
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val group = matcher.group(1)
                if (group != null) {
                    val paise = CurrencyUtils.parseInputToPaise(group)
                    if (paise > 0L) {
                        amountPaise = paise
                        break
                    }
                }
            }
        }

        if (amountPaise <= 0L) {
            return ParsedSmsResult(
                isValidTransaction = false,
                rawSender = sender,
                rawBody = trimmed,
                timestamp = timestamp
            )
        }

        // 5. Determine Transaction Type
        val transactionType = when {
            lower.contains("refund") || lower.contains("reversed") -> TransactionType.REFUND
            hasCredit && !hasDebit -> TransactionType.INCOME
            hasDebit -> TransactionType.EXPENSE
            else -> TransactionType.EXPENSE
        }

        // 6. Extract Account / Card
        var accountLast4: String? = null
        for (pattern in ACCOUNT_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                accountLast4 = matcher.group(1)
                break
            }
        }

        // 7. Extract Reference Number
        var refNumber: String? = null
        for (pattern in REF_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                refNumber = matcher.group(1)?.trim()
                break
            }
        }

        // 8. Extract Merchant / Recipient
        var merchant: String? = null
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && !candidate.equals("vpa", ignoreCase = true)) {
                    merchant = candidate.take(30).trimEnd('.', ',')
                    break
                }
            }
        }

        // 9. Payment method
        val paymentMethod = when {
            lower.contains("upi") || (merchant != null && merchant.contains("@")) -> PaymentMethod.UPI
            lower.contains("atm") || lower.contains("cash") -> PaymentMethod.CASH
            lower.contains("credit card") -> PaymentMethod.CREDIT_CARD
            lower.contains("debit card") || lower.contains("card") -> PaymentMethod.DEBIT_CARD
            lower.contains("netbanking") || lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps") -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.UPI
        }

        // 10. Identify Bank Name
        val bankName = identifyBank(sender, trimmed)

        return ParsedSmsResult(
            isValidTransaction = true,
            amountPaise = amountPaise,
            type = transactionType,
            bankName = bankName,
            accountLast4 = accountLast4,
            merchant = merchant,
            referenceNumber = refNumber,
            paymentMethod = paymentMethod,
            timestamp = timestamp,
            rawSender = sender,
            rawBody = trimmed
        )
    }

    private fun identifyBank(sender: String?, body: String): String {
        val upperSender = sender?.uppercase(Locale.ROOT) ?: ""
        val upperBody = body.uppercase(Locale.ROOT)

        for ((key, name) in BANK_IDENTIFIERS) {
            if (upperSender.contains(key) || upperBody.contains(key)) {
                return name
            }
        }
        return "Bank"
    }
}
