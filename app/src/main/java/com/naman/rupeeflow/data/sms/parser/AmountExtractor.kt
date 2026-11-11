package com.naman.rupeeflow.data.sms.parser

import java.util.regex.Pattern

object AmountExtractor {

    // Ordered list of regex patterns for extracting currency amounts from Indian banking SMS
    private val AMOUNT_PATTERNS = listOf(
        // e.g. "amounting to Rs.7.53", "amounting to INR 500"
        Pattern.compile("(?i)(?:amounting\\s*(?:to|for)?)\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        // e.g. "Rs. 850.00", "Rs.7.53", "INR 1,200.50", "₹500.00"
        Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        // e.g. "debited by Rs. 500", "spent INR 250", "paid ₹100"
        Pattern.compile("(?i)(?:debited|credited|spent|paid|withdrawn|sent|received|transferred|recharge|loaded)\\s*(?:by|for|of)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        // e.g. "amount of Rs 500", "sum of ₹2,500"
        Pattern.compile("(?i)(?:amount|sum|val)\\s*(?:of)?\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"),
        // e.g. "7.53 INR", "500.00 Rs"
        Pattern.compile("(?i)([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:rs\\.?|inr|₹)"),
        // e.g. "you paid 45.00"
        Pattern.compile("(?i)(?:you\\s+paid|you've\\s+spent|paid)\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)")
    )

    /**
     * Parses an extracted string representation of an amount into exact integer paise.
     * Prevents floating point rounding errors (e.g. 7.53 -> 753L, not 752L).
     * Prevents integer overflow.
     */
    fun parseAmountToPaise(rawAmountStr: String): Long {
        val clean = rawAmountStr.replace(",", "").trim()
        if (clean.isBlank()) return 0L

        return try {
            val parts = clean.split(".")
            val rupees = parts[0].toLongOrNull() ?: return 0L
            if (rupees < 0L || rupees > 10_000_000_000L) return 0L // Sanity bound (1000 crore)

            val paise = if (parts.size > 1) {
                val frac = parts[1].take(2).padEnd(2, '0')
                frac.toLongOrNull() ?: 0L
            } else {
                0L
            }

            (rupees * 100L) + paise
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Extracts amount in paise from SMS body.
     */
    fun extract(smsBody: String): Long {
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val group = matcher.group(1)
                if (group != null) {
                    val paise = parseAmountToPaise(group)
                    if (paise > 0L) {
                        return paise
                    }
                }
            }
        }
        return 0L
    }
}

