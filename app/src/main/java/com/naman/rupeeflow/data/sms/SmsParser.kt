package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.data.sms.parser.AmountExtractor
import com.naman.rupeeflow.data.sms.parser.BankMessageParser
import com.naman.rupeeflow.data.sms.parser.banks.*
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
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
    val isPromotional: Boolean = false,
    val isUnknown: Boolean = false,
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

    private val PROMOTIONAL_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(pre-approved|apply\\s*now|avail\\s*now|loan\\s*offer|get\\s*up\\s*to|congratulations|reward\\s*points|earn\\s*cashback|discount\\s*voucher)\\b")
    )

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

    fun parse(smsBody: String, sender: String? = null, timestamp: Long = System.currentTimeMillis()): ParsedSmsResult {
        val trimmed = smsBody.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. Check for OTP messages
        for (pattern in OTP_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isOtp = true,
                    rawSender = sender,
                    rawBody = sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
        }

        // 2. Check for Promotional messages without confirmed transaction
        val hasDebit = lower.contains("debited") || lower.contains("spent") ||
                lower.contains("paid") || lower.contains("withdrawn") ||
                lower.contains("sent") || lower.contains("deducted")
        val hasCredit = lower.contains("credited") || lower.contains("received") ||
                lower.contains("deposited") || lower.contains("refund") || lower.contains("reversed")
        val hasUpiLite = lower.contains("upi lite") || lower.contains("upi_lite")

        for (pattern in PROMOTIONAL_PATTERNS) {
            if (pattern.matcher(lower).find() && !hasDebit && !hasCredit && !hasUpiLite) {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isPromotional = true,
                    rawSender = sender,
                    rawBody = sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
        }

        // 3. Check for Failed transactions
        for (pattern in FAILED_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return ParsedSmsResult(
                    isValidTransaction = false,
                    isFailed = true,
                    rawSender = sender,
                    rawBody = sanitizeRawContent(trimmed),
                    timestamp = timestamp
                )
            }
        }

        // 4. Check for pure balance enquiry
        val isBalanceOnly = (lower.contains("bal:") || lower.contains("avl bal") || lower.contains("available bal") || lower.contains("balance in your a/c")) &&
                !hasDebit && !hasCredit && !hasUpiLite
        if (isBalanceOnly) {
            return ParsedSmsResult(
                isValidTransaction = false,
                isBalanceInquiry = true,
                rawSender = sender,
                rawBody = sanitizeRawContent(trimmed),
                timestamp = timestamp
            )
        }

        // 5. Run through Bank Parsers pipeline
        for (parser in parsers) {
            if (parser.canParse(sender, trimmed)) {
                val result = parser.parse(sender, trimmed, timestamp)
                if (result != null && result.isValidTransaction) {
                    return result
                }
            }
        }

        // 6. Unknown / Unrecognized transaction message handling
        return ParsedSmsResult(
            isValidTransaction = false,
            isUnknown = true,
            rawSender = sender,
            rawBody = sanitizeRawContent(trimmed),
            timestamp = timestamp
        )
    }
}
