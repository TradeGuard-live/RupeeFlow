package com.naman.rupeeflow.data.sms.parser

import java.util.Locale
import java.util.regex.Pattern

data class ExtractedRecipientInfo(
    val merchantName: String? = null,
    val phoneNumber: String? = null,
    val upiId: String? = null,
    val referenceNumber: String? = null
)

object PaymentRecipientExtractor {

    private val INDIAN_PHONE_REGEX = Regex("^(?:\\+?91|0)?([6-9]\\d{9})$")
    private val GENERIC_PHONE_REGEX = Regex("^(\\d{10})$")

    private val STOP_WORDS = listOf(
        " using ", " via ", " on ", " ref ", " with ", " for ", " at ",
        " dated ", " successful", " done", " to ", " from ",
        " has ", " have ", " had ", " was ", " were ", " is ", " are ", " been ", " will ",
        " your ", " upi ", " ref no", " ref id", " txn id", " transaction id", " utr ",
        " available balance", " avl bal", " balance is", " bal is", " a/c", " account"
    )

    private val RECIPIENT_PATTERNS = listOf(
        // e.g. "Paid ₹250 to Swiggy", "Payment of ₹85.00 successful to Chai Point", "Paid to 7308080808"
        Pattern.compile("(?i)(?:paid|sent|transferred|payment)\\s*(?:of)?\\s*(?:rs\\.?|inr|₹)?[\\d,.]*\\s*(?:successful)?\\s*(?:to)\\s+([a-zA-Z0-9@._\\-\\s]{2,40})"),
        // e.g. "Received ₹1,000 from Rohit Sharma", "Refund of ₹300 from Uber", "Money received from 9876543210"
        Pattern.compile("(?i)(?:received|credited|refund|money\\s+received)\\s*(?:of)?\\s*(?:rs\\.?|inr|₹)?[\\d,.]*\\s*(?:from)\\s+([a-zA-Z0-9@._\\-\\s]{2,40})"),
        // e.g. "Karan sent you ₹750"
        Pattern.compile("(?i)([a-zA-Z0-9@._\\-\\s]{2,30})\\s+(?:sent\\s+you)"),
        // e.g. "to Swiggy", "to 7308080808"
        Pattern.compile("(?i)(?:\\bto\\s+)([a-zA-Z0-9@._\\-\\s]{2,40})"),
        // e.g. "from Amit"
        Pattern.compile("(?i)(?:\\bfrom\\s+)([a-zA-Z0-9@._\\-\\s]{2,40})"),
        // e.g. "at Starbucks Coffee", "at Dominos"
        Pattern.compile("(?i)(?:\\bat\\s+)([a-zA-Z0-9&._\\-\\s]{2,35})")
    )

    private val GENERIC_IGNORE_WORDS = setOf(
        "upi", "vpa", "account", "a/c", "acct", "bank", "card", "wallet",
        "payment", "transfer", "self", "atm", "branch", "sms", "alert",
        "successful", "done", "using", "via", "your", "the", "credited", "debited"
    )

    /**
     * Extracts and strictly categorizes payee details: merchant name, phone number,
     * UPI ID / VPA, and reference numbers.
     * Prevents phone numbers and reference numbers from being misidentified as merchants.
     */
    fun extract(body: String): ExtractedRecipientInfo {
        for (pattern in RECIPIENT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val raw = matcher.group(1)?.trim()
                if (!raw.isNullOrBlank()) {
                    var cleanedCandidate: String = raw
                    if (cleanedCandidate.contains("\n")) {
                        cleanedCandidate = cleanedCandidate.substringBefore("\n")
                    }
                    if (cleanedCandidate.contains(". ") &&
                        !cleanedCandidate.startsWith("Dr. ", ignoreCase = true) &&
                        !cleanedCandidate.startsWith("Mr. ", ignoreCase = true) &&
                        !cleanedCandidate.startsWith("Ms. ", ignoreCase = true)
                    ) {
                        cleanedCandidate = cleanedCandidate.substringBefore(". ")
                    }
                    for (stopWord in STOP_WORDS) {
                        val idx = cleanedCandidate.indexOf(stopWord, ignoreCase = true)
                        if (idx != -1) {
                            cleanedCandidate = cleanedCandidate.substring(0, idx).trim()
                        }
                    }
                    val finalCandidate = cleanedCandidate.trimEnd('.', ',', '-', ':')
                    if (finalCandidate.isNotBlank()) {
                        val classified = classifyCandidate(finalCandidate)
                        if (classified.merchantName != null || classified.phoneNumber != null || classified.upiId != null) {
                            return classified
                        }
                    }
                }
            }
        }
        return ExtractedRecipientInfo()
    }

    /**
     * Strictly classifies a raw candidate recipient string into its correct component.
     */
    fun classifyCandidate(rawCandidate: String): ExtractedRecipientInfo {
        val candidate = rawCandidate.trim().trimEnd('.', ',', '-', ':')
        if (candidate.isBlank()) return ExtractedRecipientInfo()

        // 1. UPI VPA check (contains '@')
        if (candidate.contains("@")) {
            val parts = candidate.split("@")
            val prefix = parts.getOrNull(0)?.trim() ?: ""
            val phoneMatch = INDIAN_PHONE_REGEX.matchEntire(prefix) ?: GENERIC_PHONE_REGEX.matchEntire(prefix)
            return if (phoneMatch != null) {
                // e.g. 7308080808@paytm -> phone: 7308080808, upiId: 7308080808@paytm, merchant: null
                val cleanPhone = phoneMatch.groupValues[1]
                ExtractedRecipientInfo(
                    merchantName = null,
                    phoneNumber = cleanPhone,
                    upiId = candidate
                )
            } else {
                // e.g. swiggy@icici or user@okaxis
                val cleanMerchant = if (isLikelyBusinessName(prefix)) prefix else null
                ExtractedRecipientInfo(
                    merchantName = cleanMerchant,
                    phoneNumber = null,
                    upiId = candidate
                )
            }
        }

        // 2. Pure Phone number check (10-digit Indian phone number)
        val digitsOnly = candidate.replace(Regex("[\\s\\-]"), "")
        val phoneMatch = INDIAN_PHONE_REGEX.matchEntire(digitsOnly) ?: GENERIC_PHONE_REGEX.matchEntire(digitsOnly)
        if (phoneMatch != null) {
            val cleanPhone = phoneMatch.groupValues[1]
            return ExtractedRecipientInfo(
                merchantName = null,
                phoneNumber = cleanPhone,
                upiId = null
            )
        }

        // 3. Reference number / UTR check (6 or more digits without alphabetical characters)
        if (digitsOnly.length >= 6 && digitsOnly.all { it.isDigit() }) {
            return ExtractedRecipientInfo(
                merchantName = null,
                phoneNumber = null,
                upiId = null,
                referenceNumber = digitsOnly
            )
        }

        // 4. Filter out generic reserved non-merchant words
        val lower = candidate.lowercase(Locale.ROOT)
        if (GENERIC_IGNORE_WORDS.contains(lower)) {
            return ExtractedRecipientInfo()
        }

        // 5. Check if it contains alphabetical characters (must have at least 2 letters)
        val letterCount = candidate.count { it.isLetter() }
        if (letterCount < 2) {
            return ExtractedRecipientInfo()
        }

        // 6. Valid Merchant / Payee name
        val cleanMerchant = candidate.take(35).trim()
        return ExtractedRecipientInfo(
            merchantName = cleanMerchant,
            phoneNumber = null,
            upiId = null
        )
    }

    private fun isLikelyBusinessName(vpaPrefix: String): Boolean {
        val lower = vpaPrefix.lowercase(Locale.ROOT)
        val knownMerchants = listOf(
            "swiggy", "zomato", "uber", "ola", "amazon", "flipkart",
            "myntra", "zepto", "blinkit", "bigbasket", "dunzo",
            "starbucks", "dominos", "mcdonalds", "kfc", "tatacliq"
        )
        for (m in knownMerchants) {
            if (lower.contains(m)) return true
        }
        return false
    }
}
