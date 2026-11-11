package com.naman.rupeeflow.data.sms.parser

import java.util.Locale

object BankSenderNormalizer {

    /**
     * Strips 2-letter telecom/circle routing prefixes (e.g., "AD-", "VM-", "JM-", "VK-", "BP-", "BZ-", "AX-", "CP-")
     * and optional DLT service registration suffixes (e.g., "-S", "-G", "-T") to extract the canonical bank token.
     *
     * Example:
     * "AD-HDFCBK-S" -> "HDFCBK"
     * "VM-SBIN-G"   -> "SBIN"
     * "JM-ICICIB"   -> "ICICIB"
     * "AX-PAYTM"    -> "PAYTM"
     */
    fun normalize(rawSender: String?): String {
        if (rawSender.isNullOrBlank()) return ""
        var cleaned = rawSender.trim().uppercase(Locale.ROOT)

        // If format is CIRCLE-TOKEN or CIRCLE-TOKEN-SUFFIX (e.g. AD-HDFCBK-S)
        val parts = cleaned.split("-")
        if (parts.size >= 2) {
            // If the first part is a 2-3 char routing code, take the middle/primary token
            if (parts[0].length in 2..3 && parts[1].length >= 3) {
                cleaned = parts[1]
            }
        }

        // Strip any remaining trailing single character suffix (e.g. "HDFCBKS" -> "HDFCBK" if applicable)
        return cleaned.filter { it.isLetterOrDigit() }
    }

    /**
     * Checks if the sender or body matches any of the given bank keywords.
     */
    fun matchesKeywords(sender: String?, body: String, keywords: Set<String>): Boolean {
        val normSender = normalize(sender)
        val upperBody = body.uppercase(Locale.ROOT)

        for (kw in keywords) {
            val upperKw = kw.uppercase(Locale.ROOT)
            if (normSender.contains(upperKw)) return true
            if (upperBody.contains(upperKw)) return true
        }
        return false
    }
}

