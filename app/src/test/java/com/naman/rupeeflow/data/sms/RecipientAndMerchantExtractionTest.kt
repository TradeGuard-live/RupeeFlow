package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.data.sms.parser.AmountExtractor
import com.naman.rupeeflow.data.sms.parser.PaymentRecipientExtractor
import com.naman.rupeeflow.data.sms.parser.ReferenceExtractor
import org.junit.Assert.*
import org.junit.Test

class RecipientAndMerchantExtractionTest {

    @Test
    fun testTenDigitIndianPhoneNumberMustNeverBecomeMerchant() {
        val message = "Paid ₹13,000 to 7308080808 on 12-Nov-26 via UPI. Ref No 432109876543"
        val recipient = PaymentRecipientExtractor.extract(message)

        assertNull("10-digit phone number must never become merchant name", recipient.merchantName)
        assertEquals("7308080808", recipient.phoneNumber)
        assertNull(recipient.upiId)

        val result = SmsParser.parse(message, "net.one97.paytm")
        assertTrue(result.isValidTransaction)
        assertEquals(1300000L, result.amountPaise)
        assertNull("Parsed result merchant must be null for phone recipient", result.merchant)
        assertEquals("7308080808", result.phoneNumber)
    }

    @Test
    fun testUpiVpaExtraction() {
        val message = "Paid ₹350 to rahul@oksbi on 10-Nov-26. UPI Ref 998877665544"
        val recipient = PaymentRecipientExtractor.extract(message)

        assertEquals("rahul@oksbi", recipient.upiId)
        assertNull("Personal VPA without phone number should not have phone number", recipient.phoneNumber)

        val result = SmsParser.parse(message, "com.google.android.apps.nbu.paisa.user")
        assertTrue(result.isValidTransaction)
        assertEquals(35000L, result.amountPaise)
        assertEquals("rahul@oksbi", result.upiId)
    }

    @Test
    fun testUpiVpaWithPhoneNumberExtractsBothPhoneAndUpiId() {
        val message = "Paid ₹200 to 9876543210@paytm using UPI. Ref: 123456"
        val recipient = PaymentRecipientExtractor.extract(message)

        assertEquals("9876543210@paytm", recipient.upiId)
        assertEquals("9876543210", recipient.phoneNumber)
        assertNull(recipient.merchantName)
    }

    @Test
    fun testUpiReferenceNumberExtraction() {
        val message = "UPI Lite Top-up amounting to Rs.7.53 has been successful. Ref No 104810950877 - HDFC Bank"
        val ref = ReferenceExtractor.extract(message)

        assertEquals("104810950877", ref)

        val result = SmsParser.parse(message, "AD-HDFCBK-S")
        assertTrue(result.isValidTransaction)
        assertEquals(753L, result.amountPaise)
        assertEquals("104810950877", result.referenceNumber)
    }

    @Test
    fun testLargeTransactionAmountExtraction() {
        val message = "Paid INR 1,50,000.00 to Tata Motors on 11-Nov-2026. Ref No 887766554433"
        val amount = AmountExtractor.extract(message)

        assertEquals(15000000L, amount) // ₹1,50,000 = 15,000,000 paise

        val result = SmsParser.parse(message, "Generic Bank")
        assertTrue(result.isValidTransaction)
        assertEquals(15000000L, result.amountPaise)
        assertEquals("Tata Motors", result.merchant)
    }

    @Test
    fun testMissingMerchantDoesNotFabricateName() {
        val message = "Paid Rs 500 using UPI on 10-Nov-2026. Ref: 99887766"
        val recipient = PaymentRecipientExtractor.extract(message)

        assertNull("Do not fabricate merchant name when absent", recipient.merchantName)
        assertNull(recipient.phoneNumber)
    }

    @Test
    fun testMissingAmountRejectsTransaction() {
        val message = "Payment successfully sent to Swiggy. Thank you for using UPI."
        val result = SmsParser.parse(message, "com.phonepe.app")

        assertFalse("Message without amount cannot be valid transaction", result.isValidTransaction)
    }

    @Test
    fun testMultipleCurrencyValuesExtractsTransactionAmountNotBalance() {
        val message = "Paid ₹500.00 to Starbucks Coffee. Your available balance is ₹12,450.00. Ref 11223344"
        val result = SmsParser.parse(message, "net.one97.paytm")

        assertTrue(result.isValidTransaction)
        assertEquals("Must extract spent amount of ₹500 (50000 paise), not available balance", 50000L, result.amountPaise)
        assertEquals("Starbucks Coffee", result.merchant)
    }

    @Test
    fun testPromotionalAmountWithoutPaymentEvidenceIsRejected() {
        val message = "Refer your friends to our app and earn ₹100 for each friend!"
        val result = SmsParser.parse(message, "net.one97.paytm")

        assertFalse("Promotional earn amount must not be recorded as transaction", result.isValidTransaction)
        assertTrue(result.isPromotional)
    }

    @Test
    fun testReferenceNumberMustNotBecomeAmount() {
        val message = "Paid Rs. 50 to Chai Point. Ref No 104810950877"
        val amount = AmountExtractor.extract(message)

        assertEquals("Must extract Rs 50 (5000 paise), not 12-digit reference number", 5000L, amount)
    }

    @Test
    fun testUnknownNotificationWithoutFinancialIntentIsRejected() {
        val message = "Your service request ticket #44321 is in process."
        val result = SmsParser.parse(message, "com.phonepe.app")

        assertFalse(result.isValidTransaction)
        assertFalse(result.isPromotional)
    }

    @Test
    fun testUnicodeAndPunctuationVariations() {
        val m1 = "Paid ₹ 1,500.50 to Dominos Pizza"
        assertEquals(150050L, AmountExtractor.extract(m1))

        val m2 = "Debited by Rs. 2,000/- for shopping"
        assertEquals(200000L, AmountExtractor.extract(m2))

        val m3 = "Spent INR 750 at Petrol Pump"
        assertEquals(75000L, AmountExtractor.extract(m3))
    }
}

