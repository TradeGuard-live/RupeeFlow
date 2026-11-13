package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class SourceSpecificNotificationParserTest {

    @Test
    fun testPhonePeSuccessfulPaymentSent() {
        val message = "Paid ₹450 to Chai Point on PhonePe. Transaction ID: T261113123456"
        val result = SmsParser.parse(message, "com.phonepe.app")

        assertTrue(result.isValidTransaction)
        assertEquals(45000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Chai Point", result.merchant)
        assertEquals("T261113123456", result.referenceNumber)
        assertEquals("PhonePe", result.bankName)
    }

    @Test
    fun testPhonePeSuccessfulPaymentReceived() {
        val message = "Received ₹1,500 from Ananya Rao on PhonePe. UTR No: 998877665544"
        val result = SmsParser.parse(message, "com.phonepe.app")

        assertTrue(result.isValidTransaction)
        assertEquals(150000L, result.amountPaise)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Ananya Rao", result.merchant)
        assertEquals("998877665544", result.referenceNumber)
    }

    @Test
    fun testGooglePaySuccessfulPaymentSent() {
        val message = "You paid ₹320 to Starbucks Coffee. UPI Ref: 123456789012"
        val result = SmsParser.parse(message, "com.google.android.apps.nbu.paisa.user")

        assertTrue(result.isValidTransaction)
        assertEquals(32000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Starbucks Coffee", result.merchant)
        assertEquals("123456789012", result.referenceNumber)
        assertEquals("Google Pay", result.bankName)
    }

    @Test
    fun testGooglePayPaymentReceived() {
        val message = "Karan sent you ₹750 on Google Pay. UPI Ref ID 554433221100"
        val result = SmsParser.parse(message, "com.google.android.apps.nbu.paisa.user")

        assertTrue(result.isValidTransaction)
        assertEquals(75000L, result.amountPaise)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Karan", result.merchant)
    }

    @Test
    fun testGooglePayPromotionalReferralOfferRejected() {
        val message = "Invite friends to Google Pay and earn ₹201 when they make their first payment!"
        val result = SmsParser.parse(message, "com.google.android.apps.nbu.paisa.user")

        assertFalse(result.isValidTransaction)
        assertTrue(result.isPromotional)
    }

    @Test
    fun testBhimSuccessfulPaymentSent() {
        val message = "Paid Rs. 200 to shop@upi using BHIM. Ref No: 88776655"
        val result = SmsParser.parse(message, "in.org.npci.upiapp")

        assertTrue(result.isValidTransaction)
        assertEquals(20000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("BHIM", result.bankName)
    }

    @Test
    fun testCredBillPayment() {
        val message = "Paid ₹5,000 for HDFC credit card bill via CRED. Ref ID: CR12345678"
        val result = SmsParser.parse(message, "com.dreamplug.androidapp")

        assertTrue(result.isValidTransaction)
        assertEquals(500000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("CRED", result.bankName)
    }

    @Test
    fun testAmazonPayRefund() {
        val message = "Refund of ₹499 from Amazon Pay has been credited to your account. Ref: AMZ98765432"
        val result = SmsParser.parse(message, "in.amazon.mShop.android.shopping")

        assertTrue(result.isValidTransaction)
        assertEquals(49900L, result.amountPaise)
        assertEquals(TransactionType.REFUND, result.type)
        assertEquals("Amazon Pay", result.bankName)
    }

    @Test
    fun testGenericOtpMessageRejected() {
        val message = "482910 is your OTP for transaction of Rs 500 at Flipkart. Do not share with anyone."
        val result = SmsParser.parse(message, "VK-HDFCBK")

        assertFalse(result.isValidTransaction)
        assertTrue(result.isOtp)
    }

    @Test
    fun testPureBalanceInquiryRejected() {
        val message = "Available Bal in your A/c ending 1234 is Rs. 45,210.50. Thank you."
        val result = SmsParser.parse(message, "Axis Bank")

        assertFalse(result.isValidTransaction)
        assertTrue(result.isBalanceInquiry)
    }

    @Test
    fun testReversalHandling() {
        val message = "Transaction of Rs. 350 at Dominos has been reversed to your A/c XX1234. Ref: REV987"
        val result = SmsParser.parse(message, "SBI")

        assertTrue(result.isValidTransaction)
        assertEquals(35000L, result.amountPaise)
        assertEquals(TransactionType.REFUND, result.type)
    }
}

