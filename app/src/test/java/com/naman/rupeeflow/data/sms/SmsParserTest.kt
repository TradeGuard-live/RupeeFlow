package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {

    @Test
    fun testHdfcDebitSms() {
        val sms = "Dear Customer, your A/c XX1234 has been debited by Rs. 850.00 on 02-Nov-2026 for UPI transaction to SWIGGY. UPI Ref 432190812345."
        val result = SmsParser.parse(sms, "HDFCBK")

        assertTrue(result.isValidTransaction)
        assertEquals(85000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("HDFC Bank", result.bankName)
        assertEquals("1234", result.accountLast4)
        assertNotNull(result.merchant)
        assertEquals("432190812345", result.referenceNumber)
        assertEquals(PaymentMethod.UPI, result.paymentMethod)
        assertFalse(result.isOtp)
        assertFalse(result.isFailed)
    }

    @Test
    fun testSbiDebitSms() {
        val sms = "Dear SBI User, your A/c ending with 5678 debited by Rs 1,450.50 on 02Nov26 transfer to Zomato UPI Ref No 987654321012."
        val result = SmsParser.parse(sms, "SBISMS")

        assertTrue(result.isValidTransaction)
        assertEquals(145050L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("State Bank of India", result.bankName)
        assertEquals("5678", result.accountLast4)
        assertEquals("987654321012", result.referenceNumber)
    }

    @Test
    fun testIciciDebitSms() {
        val sms = "Acct XX9012 debited for INR 450.00 on 02-Nov-26. UPI:rahul@icici. Ref no 5544332211."
        val result = SmsParser.parse(sms, "ICICIB")

        assertTrue(result.isValidTransaction)
        assertEquals(45000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("ICICI Bank", result.bankName)
        assertEquals("9012", result.accountLast4)
    }

    @Test
    fun testCreditIncomeSms() {
        val sms = "Your A/c XX4321 has been credited by Rs. 25,000.00 on 01-Nov-2026 by salary transfer. Ref 88776655."
        val result = SmsParser.parse(sms, "AXISBK")

        assertTrue(result.isValidTransaction)
        assertEquals(2500000L, result.amountPaise)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Axis Bank", result.bankName)
    }

    @Test
    fun testRefundReversalSms() {
        val sms = "Refund of Rs 350.00 processed for your order at SWIGGY. Amount credited to your A/c XX1122 on 02-Nov-2026."
        val result = SmsParser.parse(sms, "KOTAKB")

        assertTrue(result.isValidTransaction)
        assertEquals(35000L, result.amountPaise)
        assertEquals(TransactionType.REFUND, result.type)
    }

    @Test
    fun testOtpExclusion_NeverTreatedAsTransaction() {
        val otp1 = "123456 is your secret OTP for payment of Rs. 850.00 on Swiggy. Do not share OTP with anyone. Valid for 10 mins."
        val result1 = SmsParser.parse(otp1, "HDFCBK")

        assertFalse(result1.isValidTransaction)
        assertTrue(result1.isOtp)

        val otp2 = "Use verification code 987654 to complete your login. Valid for 5 minutes."
        val result2 = SmsParser.parse(otp2, "SBIOTP")

        assertFalse(result2.isValidTransaction)
        assertTrue(result2.isOtp)
    }

    @Test
    fun testFailedTransactionExclusion_NeverTreatedAsExpense() {
        val failedSms = "Transaction of Rs. 1,200.00 on A/c XX3344 has failed due to insufficient funds. No amount was debited."
        val result = SmsParser.parse(failedSms, "HDFCBK")

        assertFalse(result.isValidTransaction)
        assertTrue(result.isFailed)
    }

    @Test
    fun testBalanceInquiryExclusion_NeverTreatedAsTransaction() {
        val balSms = "Dear Customer, available balance in your A/c XX9988 is Rs. 45,210.50 as on 02-Nov-2026."
        val result = SmsParser.parse(balSms, "BOBSMS")

        assertFalse(result.isValidTransaction)
        assertTrue(result.isBalanceInquiry)
    }

    @Test
    fun testAtmCashWithdrawal() {
        val atmSms = "Rs. 2,000.00 withdrawn from ATM using Card XX5544 on 02-Nov-2026. Avl Bal Rs. 12,000.00."
        val result = SmsParser.parse(atmSms, "SBISMS")

        assertTrue(result.isValidTransaction)
        assertEquals(200000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals(PaymentMethod.CASH, result.paymentMethod)
    }
}
