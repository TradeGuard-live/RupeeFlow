package com.naman.rupeeflow.data.sms.parser

import java.util.Locale
import java.util.regex.Pattern

object ReferenceExtractor {

    // Labels indicating non-UPI / app-internal / order IDs that must NEVER be extracted as UPI references
    private val APP_INTERNAL_OR_ORDER_LABELS = listOf(
        "google pay transaction id",
        "google transaction id",
        "gpay transaction id",
        "gpay txn id",
        "phonepe transaction id",
        "phonepe txn id",
        "phonepe ref id",
        "phonepe ref",
        "order id",
        "order no",
        "order #",
        "order ref",
        "order reference",
        "bill id"
    )

    // Contextual patterns indicating account numbers, phone numbers, or helplines
    private val ACCOUNT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:a/c|account|acct|ac|card)\\s*(?:no\\.?)?\\s*(?:ending\\s*(?:with|in)?\\s*)?[xX*]*(\\d{4,18})"),
        Pattern.compile("(?i)(?:in|to|from)\\s+(?:a/c|account|acct|ac)\\s*[xX*]*(\\d{4,18})")
    )

    private val HELPLINE_PATTERNS = listOf(
        Pattern.compile("(?i)(?:call|helpline|toll\\s*free|contact|dial|sms\\s+block)\\s*[:#-]?\\s*(\\d{6,15})")
    )

    private val OTP_PATTERNS = listOf(
        Pattern.compile("(?i)(?:\\botp|one\\s*time\\s*password|verification\\s*code|code\\s*is)\\s*[:#-]?\\s*(\\d{4,8})")
    )

    // Dedicated regex patterns for supported UPI reference number labels
    // Order matters: more specific labels are checked first
    private val UPI_REF_LABELED_PATTERNS = listOf(
        // 1. UPI Reference Number / UPI Ref No / UPI Ref ID / UPI Ref
        Pattern.compile("(?i)(?:\\bupi\\s*reference\\s*(?:number|no\\.?)?|\\bupi\\s*ref(?:erence)?\\s*(?:no\\.?|id|num|number)?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 2. Bank Reference Number / Bank Ref No
        Pattern.compile("(?i)(?:\\bbank\\s*reference\\s*(?:number|no\\.?)?|\\bbank\\s*ref\\s*(?:no\\.?|id|num|number)?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 3. Transaction Reference / Txn Ref No
        Pattern.compile("(?i)(?:\\b(?:transaction|txn)\\s*reference\\s*(?:number|no\\.?)?|\\b(?:transaction|txn)\\s*ref\\s*(?:no\\.?|id|num|number)?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 4. UTR / UTR No / UTR Number
        Pattern.compile("(?i)(?:\\butr(?:\\s*(?:no\\.?|id|num|number))?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 5. RRN / RRN No / RRN Number
        Pattern.compile("(?i)(?:\\brrn(?:\\s*(?:no\\.?|id|num|number))?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 6. Reference No / Ref No / Reference Number (when explicitly labeled as reference)
        Pattern.compile("(?i)(?:\\b(?:reference|ref)\\s*(?:no\\.?|num|number|#))[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 7. UPI Transaction ID (only when value is bank/network reference, verified by validator)
        Pattern.compile("(?i)(?:\\bupi\\s*(?:transaction|txn)\\s*(?:id|no\\.?|num|number)?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),

        // 8. Explicit Ref with separator and 8+ characters (e.g. "Ref: 104810950877" or "Ref 104810950877")
        Pattern.compile("(?i)(?:\\bref\\b)[\\s:=#-]+([0-9a-zA-Z]{8,35})"),

        // 9. Slash-delimited UPI reference patterns (e.g. "UPI/432109876543", "UPI/CR/432109876543", "UPI/P2A/432109876543/...")
        Pattern.compile("(?i)(?:\\bupi\\s*/\\s*(?:[a-zA-Z0-9]+\\s*/\\s*)?)([0-9]{12})\\b"),

        // 10. Slash-delimited UTR pattern (e.g. "UTR/432109876543")
        Pattern.compile("(?i)(?:\\butr\\s*/\\s*)([0-9a-zA-Z]{6,35})\\b")
    )

    // Fallback general reference patterns (IMPS, NEFT, RTGS)
    private val GENERAL_FALLBACK_PATTERNS = listOf(
        Pattern.compile("(?i)(?:\\b(?:imps|neft|rtgs)\\s*ref(?:\\s*no)?)[\\s:=#-]+([0-9a-zA-Z]{6,35})"),
        Pattern.compile("(?i)(?:\\b(?:txn\\s*id|transaction\\s*id))[\\s:=#-]+([0-9a-zA-Z]{6,35})")
    )

    private val INDIAN_PHONE_REGEX = Regex("^[6-9]\\d{9}$")
    private val DATE_YYYYMMDD_REGEX = Regex("^(?:19|20)\\d{6}$")
    private val DATE_DDMMYYYY_REGEX = Regex("^\\d{2}(?:0[1-9]|1[0-2])(?:19|20)\\d{2}$")

    private val RESERVED_WORDS = setOf(
        "SUCCESSFUL", "PENDING", "AMOUNT", "BALANCE", "RUPEES", "INR", "DEBITED", "CREDITED",
        "ACCOUNT", "PAYMENT", "TRANSFER", "TRANSACTION", "WALLET", "FAILED", "REVERSED", "REFUND"
    )

    /**
     * Extracts dedicated UPI reference number (UTR, UPI Ref No, RRN, Bank Ref No, etc.).
     * Strictly verifies that candidate is not an app-generated ID (Google Pay, PhonePe, order IDs),
     * account number, mobile number, balance, date, or helpline number.
     * If ambiguous or non-bank reference, returns null.
     */
    fun extractUpiReference(smsBody: String): String? {
        val trimmed = smsBody.trim()
        if (trimmed.isBlank()) return null

        for (pattern in UPI_REF_LABELED_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            while (matcher.find()) {
                val candidate = matcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':') ?: continue
                val matchStart = matcher.start()
                if (isValidUpiReference(candidate, trimmed, matchStart)) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Extracts reference number or transaction UTR from SMS body.
     * Tries extractUpiReference first, falling back to general banking references if needed.
     */
    fun extract(smsBody: String): String? {
        val upiRef = extractUpiReference(smsBody)
        if (upiRef != null) return upiRef

        val trimmed = smsBody.trim()
        if (trimmed.isBlank()) return null

        for (pattern in GENERAL_FALLBACK_PATTERNS) {
            val matcher = pattern.matcher(trimmed)
            while (matcher.find()) {
                val candidate = matcher.group(1)?.trim()?.trimEnd('.', ',', ';', ':') ?: continue
                val matchStart = matcher.start()
                if (isValidUpiReference(candidate, trimmed, matchStart)) {
                    return candidate
                }
            }
        }

        return null
    }

    /**
     * Comprehensive validation filter to reject app-internal IDs, order IDs, account numbers,
     * phone numbers, dates, balances, and helpline numbers.
     */
    private fun isValidUpiReference(candidate: String, fullText: String, matchStart: Int): Boolean {
        // Length boundary: UPI references/RRN are typically 12 numeric digits or 6-35 characters
        if (candidate.length !in 6..35) return false

        val candidateUpper = candidate.uppercase(Locale.ROOT)
        if (RESERVED_WORDS.contains(candidateUpper)) return false

        // 1. Check preceding label context (up to 45 chars before matchStart)
        val lookbackStart = (matchStart - 45).coerceAtLeast(0)
        val precedingText = fullText.substring(lookbackStart, matchStart).lowercase(Locale.ROOT)

        for (negLabel in APP_INTERNAL_OR_ORDER_LABELS) {
            if (precedingText.contains(negLabel)) {
                return false
            }
        }


        // 2. Reject Google Pay internal transaction IDs
        // Google Pay IDs often start with "CICAg" or base64 patterns e.g. CICAgICAgICAgA
        if (candidate.startsWith("CICAg", ignoreCase = true) || candidate.startsWith("CICA", ignoreCase = true)) {
            return false
        }

        // 3. Reject PhonePe internal transaction IDs
        // PhonePe transaction IDs typically start with 'T' or 'P' followed by 12+ digits or alphanumeric string
        if (Regex("(?i)^[TP]\\d{14,}$").matches(candidate) || Regex("(?i)^T[0-9A-Z]{18,}$").matches(candidate)) {
            return false
        }

        // 4. Reject Order IDs
        if (candidate.startsWith("order_", ignoreCase = true) ||
            candidate.startsWith("ord_", ignoreCase = true) ||
            candidate.startsWith("OD_", ignoreCase = true) ||
            candidate.contains("_")
        ) {
            return false
        }

        // 5. Reject 10-digit Indian Mobile Numbers (e.g. 9876543210)
        // Bank RRNs are 12 digits or alphanumeric; a 10-digit number starting with 6,7,8,9 without letters is a mobile number
        if (INDIAN_PHONE_REGEX.matches(candidate)) {
            return false
        }

        // 6. Reject Calendar Dates formatted as YYYYMMDD or DDMMYYYY
        if (DATE_YYYYMMDD_REGEX.matches(candidate) || DATE_DDMMYYYY_REGEX.matches(candidate)) {
            return false
        }

        // 7. Check if candidate matches any account number mentioned in the SMS
        for (pattern in ACCOUNT_PATTERNS) {
            val matcher = pattern.matcher(fullText)
            while (matcher.find()) {
                val acctNumber = matcher.group(1)?.trim()
                if (!acctNumber.isNullOrBlank() && candidate == acctNumber) {
                    return false
                }
            }
        }

        // 8. Check if candidate matches helpline numbers mentioned in the SMS
        for (pattern in HELPLINE_PATTERNS) {
            val matcher = pattern.matcher(fullText)
            while (matcher.find()) {
                val helpline = matcher.group(1)?.trim()
                if (!helpline.isNullOrBlank() && candidate == helpline) {
                    return false
                }
            }
        }

        // 9. Check if candidate matches any OTP mentioned in the SMS
        for (pattern in OTP_PATTERNS) {
            val matcher = pattern.matcher(fullText)
            while (matcher.find()) {
                val otp = matcher.group(1)?.trim()
                if (!otp.isNullOrBlank() && candidate == otp) {
                    return false
                }
            }
        }

        return true
    }
}
