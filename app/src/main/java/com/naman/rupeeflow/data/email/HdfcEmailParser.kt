package com.naman.rupeeflow.data.email

import android.text.Html
import com.naman.rupeeflow.data.sms.ParsedSmsResult
import com.naman.rupeeflow.data.sms.SmsParser
import java.util.Locale

/** HDFC sender allowlist + shared existing HDFC transaction parser. */
object HdfcEmailParser {
    private val trustedDomains = setOf("hdfcbank.com", "hdfcbank.net")

    fun parse(fromHeader: String?, subject: String?, body: String?, timestamp: Long): ParsedSmsResult? {
        val sender = extractAddress(fromHeader) ?: return null
        val domain = sender.substringAfterLast('@', "").lowercase(Locale.ROOT)
        if (domain !in trustedDomains) return null

        val plainBody = Html.fromHtml(body.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString()
        val content = listOf(subject.orEmpty(), plainBody).filter(String::isNotBlank).joinToString("\n")
        if (content.isBlank()) return null
        return SmsParser.parse(content, "HDFCBK", timestamp)
            .takeIf { it.isValidTransaction && it.amountPaise > 0L }
    }

    private fun extractAddress(header: String?): String? {
        val value = header?.trim().orEmpty()
        if (value.isBlank()) return null
        val address = Regex("<([^<>]+)>").find(value)?.groupValues?.get(1) ?: value
        return address.trim().trim('<', '>').takeIf { it.contains('@') }
    }
}
