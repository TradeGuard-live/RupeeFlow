package com.naman.rupeeflow.data.email

import org.junit.Assert.*
import org.junit.Test

class HdfcEmailParserTest {
    @Test
    fun parsesHdfcTransactionEmailUsingSharedBankParser() {
        val result = HdfcEmailParser.parse(
            fromHeader = "HDFC Bank <alerts@hdfcbank.com>",
            subject = "UPI transaction alert",
            body = "Dear Customer, your A/c XX1234 has been debited by Rs. 850.00 on 02-Nov-2026 for UPI transaction to SWIGGY. UPI Ref 432190812345.",
            timestamp = 1793610000000L
        )
        assertNotNull(result)
        assertTrue(result!!.isValidTransaction)
        assertEquals(85000L, result.amountPaise)
        assertEquals("432190812345", result.referenceNumber)
    }

    @Test
    fun rejectsNonHdfcSenderDomain() {
        val result = HdfcEmailParser.parse(
            fromHeader = "HDFC Alerts <alerts@not-hdfc.example>",
            subject = "UPI transaction alert",
            body = "Your account debited by Rs. 100",
            timestamp = System.currentTimeMillis()
        )
        assertNull(result)
    }
}
