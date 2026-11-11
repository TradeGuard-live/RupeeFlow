package com.naman.rupeeflow.data.sms.parser

import java.util.regex.Pattern

object ReferenceExtractor {

    private val REF_PATTERNS = listOf(
        // e.g. "Ref No 104810950877", "Ref No. 104810950877", "Ref No: 104810950877"
        Pattern.compile("(?i)(?:ref\\s*no\\.?|ref\\s*#)[\\s:=#-]*([0-9a-zA-Z]{6,25})"),
        // e.g. "UPI Ref 432190812345", "UPI Ref No 987654321012", "UPI Ref ID 123456"
        Pattern.compile("(?i)(?:upi\\s*ref(?:\\s*no|\\s*id)?)[\\s:=#-]*([0-9a-zA-Z]{6,25})"),
        // e.g. "UTR 123456789012", "UTR: 123456789012", "UTR No. 123456"
        Pattern.compile("(?i)(?:utr(?:\\s*no)?)[\\s:=#-]*([0-9a-zA-Z]{6,25})"),
        // e.g. "Txn ID 12345678", "Transaction ID 12345678"
        Pattern.compile("(?i)(?:txn\\s*id|transaction\\s*id)[\\s:=#-]*([0-9a-zA-Z]{6,25})"),
        // e.g. "RRN 123456789012", "RRN: 123456789012"
        Pattern.compile("(?i)(?:rrn)[\\s:=#-]*([0-9a-zA-Z]{6,25})"),
        // e.g. "IMPS Ref No 12345678", "NEFT Ref 123456"
        Pattern.compile("(?i)(?:imps|neft|rtgs)\\s*ref(?:\\s*no)?[\\s:=#-]*([0-9a-zA-Z]{6,25})"),
        // e.g. "Ref 104810950877"
        Pattern.compile("(?i)(?:\\bref\\b)[\\s:=#-]+([0-9a-zA-Z]{8,25})")
    )

    /**
     * Extracts reference number or transaction UTR from SMS body.
     */
    fun extract(smsBody: String): String? {
        for (pattern in REF_PATTERNS) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrBlank() && candidate.length in 6..25) {
                    return candidate
                }
            }
        }
        return null
    }
}

