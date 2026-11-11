package com.naman.rupeeflow.data.sms.parser

import com.naman.rupeeflow.data.sms.ParsedSmsResult

/**
 * Standard interface for bank-specific message parsers in RupeeFlow.
 * Isolates bank formatting rules and template variations.
 */
interface BankMessageParser {

    /**
     * The official display name of the financial institution.
     */
    val bankName: String

    /**
     * Set of normalized sender ID keywords this parser claims ownership of.
     */
    val supportedSenderKeywords: Set<String>

    /**
     * Determines whether this parser can handle the given sender and message body.
     */
    fun canParse(sender: String?, body: String): Boolean

    /**
     * Executes bank-specific parsing logic to extract amount, direction, reference, and account details.
     * Returns a valid ParsedSmsResult if parsed successfully, or null to delegate to the next adapter.
     */
    fun parse(sender: String?, body: String, timestamp: Long): ParsedSmsResult?
}

