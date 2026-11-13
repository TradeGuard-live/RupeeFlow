package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.data.sms.parser.NotificationCategory
import com.naman.rupeeflow.data.sms.parser.NotificationCategoryClassifier
import com.naman.rupeeflow.data.sms.parser.RejectionReason
import com.naman.rupeeflow.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class PaytmNotificationFalsePositiveTest {

    private val paytmPkg = "net.one97.paytm"

    @Test
    fun testPaytmReferralNotificationWith100RupeesIsRejectedAsPromotional() {
        val title = "Apply Now"
        val body = "Your Friends Can Get a Lifetime Free Card. Refer them to the Paytm AU Credit Card and earn ₹100 when they get it."

        val classification = NotificationCategoryClassifier.classify(title, body, paytmPkg)
        assertEquals(NotificationCategory.PROMOTIONAL, classification.category)
        assertEquals(RejectionReason.PROMOTIONAL_CONTENT, classification.rejectionReason)
        assertFalse(classification.isTransactionCandidate)

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse("Promotional referral must never be valid transaction", result.isValidTransaction)
        assertTrue("Promotional flag must be set", result.isPromotional)
        assertEquals("PROMOTIONAL_CONTENT", result.rejectionReason)
    }

    @Test
    fun testPaytmCreditCardMarketingPromotionRejected() {
        val title = "Exclusive Offer"
        val body = "Get a Lifetime Free Card! Apply now for Paytm SBI Credit Card and enjoy unlimited reward points."

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse(result.isValidTransaction)
        assertTrue(result.isPromotional)
        assertEquals("PROMOTIONAL_CONTENT", result.rejectionReason)
    }

    @Test
    fun testPaytmLoanAdvertisementRejected() {
        val title = "Pre-approved"
        val body = "Pre-approved personal loan of ₹2,00,000 is ready for you. Avail now in 2 minutes."

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse(result.isValidTransaction)
        assertTrue(result.isPromotional)
        assertEquals("PROMOTIONAL_CONTENT", result.rejectionReason)
    }

    @Test
    fun testPaytmShoppingDiscountCouponRejected() {
        val title = "Flat ₹150 Off"
        val body = "Flat ₹150 off on orders above ₹499 at top brands. Use discount voucher now."

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse(result.isValidTransaction)
        assertTrue(result.isPromotional)
        assertEquals("PROMOTIONAL_CONTENT", result.rejectionReason)
    }

    @Test
    fun testPaytmCashbackPromotionOfferRejected() {
        val title = "Special Offer"
        val body = "Recharge your mobile now and get up to ₹50 cashback on Paytm."

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse(result.isValidTransaction)
        assertTrue(result.isPromotional)
        assertEquals("PROMOTIONAL_CONTENT", result.rejectionReason)
    }

    @Test
    fun testPaytmActualSuccessfulPaymentParsedCorrectly() {
        val title = "Paytm"
        val body = "Paid ₹250 to Swiggy using Paytm UPI. UPI Ref: 123456789012"

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertTrue("Real transaction must be valid", result.isValidTransaction)
        assertEquals(25000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("Swiggy", result.merchant)
        assertEquals("123456789012", result.referenceNumber)
        assertFalse(result.isPromotional)
    }

    @Test
    fun testPaytmActualReceivedPaymentParsedAsIncome() {
        val title = "Paytm"
        val body = "Received ₹1,000 from Rohit Sharma on Paytm. Ref No 987654321012"

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertTrue("Received payment must be valid", result.isValidTransaction)
        assertEquals(100000L, result.amountPaise)
        assertEquals(TransactionType.INCOME, result.type)
        assertEquals("Rohit Sharma", result.merchant)
        assertEquals("987654321012", result.referenceNumber)
    }

    @Test
    fun testPaytmRefundProcessedAsRefund() {
        val title = "Paytm"
        val body = "Refund of ₹300 from Uber has been processed successfully to your Paytm balance. Ref No: 112233445566"

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertTrue("Refund must be valid transaction", result.isValidTransaction)
        assertEquals(30000L, result.amountPaise)
        assertEquals(TransactionType.REFUND, result.type)
        assertEquals("Uber", result.merchant)
        assertEquals("112233445566", result.referenceNumber)
    }

    @Test
    fun testPaytmFailedPaymentRejectedAsFailed() {
        val title = "Payment Failed"
        val body = "Payment of ₹500 to Swiggy failed due to bank server timeout. Any debited amount will be reversed."

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse("Failed payment cannot be valid transaction", result.isValidTransaction)
        assertTrue("Failed flag must be set", result.isFailed)
        assertEquals("FAILED_PAYMENT", result.rejectionReason)
    }

    @Test
    fun testPaytmPendingPaymentRoutedToReview() {
        val title = "Paytm"
        val body = "Your payment of ₹500 to Zomato is pending with your bank and will be processed shortly."

        val result = SmsParser.parseNotification(title, body, paytmPkg)
        assertFalse("Pending payment must not automatically become confirmed transaction", result.isValidTransaction)
        assertTrue("Pending payment candidate routed to review", result.isUnknown)
    }
}

