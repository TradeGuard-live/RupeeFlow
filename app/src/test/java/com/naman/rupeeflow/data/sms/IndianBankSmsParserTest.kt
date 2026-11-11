package com.naman.rupeeflow.data.sms

import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class IndianBankSmsParserTest {

    // ==========================================
    // PHASE 1 — EXACT HDFC UPI LITE REAL SMS
    // ==========================================

    @Test
    fun testExactHdfcUpiLiteTopUp_RealSmsFromUserPhone() {
        val sender = "AD-HDFCBK-S"
        val message = "UPI LITE Top-up amounting to Rs.7.53 has been successful. Ref No 104810950877 - HDFC Bank"
        val timestamp = 1731302400000L

        val result = SmsParser.parse(smsBody = message, sender = sender, timestamp = timestamp)

        assertTrue("Must be recognized as a valid transaction", result.isValidTransaction)
        assertFalse("Must not be classified as unknown", result.isUnknown)
        assertFalse("Must not be classified as OTP", result.isOtp)
        assertFalse("Must not be classified as promotional", result.isPromotional)
        assertFalse("Must not be classified as balance inquiry", result.isBalanceInquiry)
        assertFalse("Must not be classified as failed", result.isFailed)

        assertEquals("Bank name must be HDFC Bank", "HDFC Bank", result.bankName)
        assertEquals("Transaction type must be UPI_LITE_TOPUP", TransactionType.UPI_LITE_TOPUP, result.type)
        assertEquals("Amount in paise must be 753 (Rs. 7.53)", 753L, result.amountPaise)
        assertEquals("Reference number must match exactly", "104810950877", result.referenceNumber)
        assertEquals("Payment method must be UPI_LITE", PaymentMethod.UPI_LITE, result.paymentMethod)
        assertEquals("Sender ID must match", sender, result.rawSender)

        // Financial accounting rule verification:
        // A top-up must have userExpensePaise = 0L so it is NOT double-counted with subsequent expenses.
        val txn = Transaction(
            title = "HDFC Bank Top-up",
            amountPaise = result.amountPaise,
            categoryId = 1L,
            transactionType = result.type,
            paymentMethod = result.paymentMethod
        )
        assertEquals("Top-up expense impact on user must be 0 paise", 0L, txn.userExpensePaise)
    }

    @Test
    fun testHdfcUpiLitePayment() {
        val sender = "AD-HDFCBK"
        val message = "Paid Rs.25.00 from UPI Lite to Chai Point via HDFC Bank. Ref 9876543210."
        val result = SmsParser.parse(message, sender)

        assertTrue(result.isValidTransaction)
        assertEquals("HDFC Bank", result.bankName)
        assertEquals(TransactionType.UPI_LITE_PAYMENT, result.type)
        assertEquals(2500L, result.amountPaise)
        assertEquals(PaymentMethod.UPI_LITE, result.paymentMethod)
        assertEquals("Chai Point", result.merchant)

        val txn = Transaction(
            title = "Chai Point",
            amountPaise = result.amountPaise,
            categoryId = 1L,
            transactionType = result.type,
            paymentMethod = result.paymentMethod
        )
        assertEquals("UPI Lite payment must count as an actual user expense", 2500L, txn.userExpensePaise)
    }

    @Test
    fun testHdfcUpiLiteRefund() {
        val sender = "AD-HDFCBK-S"
        val message = "Refund of Rs.50.00 credited to UPI Lite wallet. Ref 1122334455 - HDFC Bank"
        val result = SmsParser.parse(message, sender)

        assertTrue(result.isValidTransaction)
        assertEquals("HDFC Bank", result.bankName)
        assertEquals(TransactionType.UPI_LITE_REFUND, result.type)
        assertEquals(5000L, result.amountPaise)
        assertEquals(PaymentMethod.UPI_LITE, result.paymentMethod)
        assertEquals("1122334455", result.referenceNumber)
    }

    // ==========================================
    // PHASE 2 — MAJOR INDIAN BANKS CORPUS
    // ==========================================

    @Test
    fun testSbiDebitAndUpiLite() {
        // SBI UPI Debit
        val sbiDebit = "Dear SBI User, your A/c ending with 5678 debited by Rs 1,450.50 on 02Nov26 transfer to Zomato UPI Ref No 987654321012."
        val res1 = SmsParser.parse(sbiDebit, "VM-SBISMS-S")
        assertTrue(res1.isValidTransaction)
        assertEquals("State Bank of India", res1.bankName)
        assertEquals(TransactionType.EXPENSE, res1.type)
        assertEquals(145050L, res1.amountPaise)
        assertEquals("5678", res1.accountLast4)
        assertEquals("987654321012", res1.referenceNumber)

        // SBI UPI Lite Top-up
        val sbiTopup = "UPI LITE Top-up of Rs.500.00 successful for A/C XX5678. Ref 776655443322 - State Bank of India"
        val res2 = SmsParser.parse(sbiTopup, "SBISMS")
        assertTrue(res2.isValidTransaction)
        assertEquals(TransactionType.UPI_LITE_TOPUP, res2.type)
        assertEquals(50000L, res2.amountPaise)
        assertEquals("776655443322", res2.referenceNumber)
    }

    @Test
    fun testIciciBankFormats() {
        val iciciSms = "Dear Customer, your Acct XX987 is debited with INR 450.00 on 11-Nov-26. Info: BIL*Zomato. Call 1800... if not done by you - ICICI Bank"
        val result = SmsParser.parse(iciciSms, "BZ-ICICIB-T")

        assertTrue(result.isValidTransaction)
        assertEquals("ICICI Bank", result.bankName)
        assertEquals(45000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("987", result.accountLast4)
        assertEquals("Zomato", result.merchant)
    }

    @Test
    fun testAxisBankFormats() {
        val axisSms = "Rs 890.00 spent on Axis Bank Card ending 4321 at RELIANCE RETAIL on 10-Nov-26. Available limit Rs 45,000. - Axis Bank"
        val result = SmsParser.parse(axisSms, "AD-AXISBK-S")

        assertTrue(result.isValidTransaction)
        assertEquals("Axis Bank", result.bankName)
        assertEquals(89000L, result.amountPaise)
        assertEquals(PaymentMethod.CREDIT_CARD, result.paymentMethod)
        assertEquals("RELIANCE RETAIL", result.merchant)
    }

    @Test
    fun testKotakBankFormats() {
        val kotakSms = "Sent Rs.120.00 from Kotak Bank AC *5678 to RAMESH on 11-Nov-26. UPI Ref 309876543210 - Kotak Mahindra Bank"
        val result = SmsParser.parse(kotakSms, "AD-KOTAKB")

        assertTrue(result.isValidTransaction)
        assertEquals("Kotak Mahindra Bank", result.bankName)
        assertEquals(12000L, result.amountPaise)
        assertEquals(TransactionType.EXPENSE, result.type)
        assertEquals("309876543210", result.referenceNumber)
        assertEquals("RAMESH", result.merchant)
    }

    @Test
    fun testBankOfBarodaAtmWithdrawal() {
        val bobSms = "Your A/c *8899 is debited for Rs.2,000.00 on 09-Nov-2026 by cash withdrawal at BOB ATM. Avail Bal: Rs.15,000.00 - Bank of Baroda"
        val result = SmsParser.parse(bobSms, "AD-BOBSMS-S")

        assertTrue(result.isValidTransaction)
        assertEquals("Bank of Baroda", result.bankName)
        assertEquals(200000L, result.amountPaise)
        assertEquals(PaymentMethod.CASH, result.paymentMethod)
        assertEquals("8899", result.accountLast4)
    }

    @Test
    fun testGenericIndianBanks_PnbCanaraUnionIdfc() {
        // PNB
        val pnbSms = "Your A/C *1122 debited by Rs 350.00 on 10-Nov-26 via UPI to Blinkit. UTR 9988776655 - Punjab National Bank"
        val pnbRes = SmsParser.parse(pnbSms, "VK-PNBSMS")
        assertTrue(pnbRes.isValidTransaction)
        assertEquals("Punjab National Bank", pnbRes.bankName)
        assertEquals(35000L, pnbRes.amountPaise)

        // Canara
        val canaraSms = "Rs. 600.00 debited from Canara Bank A/c ...3344 on 10-Nov-26 for transfer to Swiggy. Ref 112233 - Canara Bank"
        val canaraRes = SmsParser.parse(canaraSms, "CANBNK")
        assertTrue(canaraRes.isValidTransaction)
        assertEquals("Canara Bank", canaraRes.bankName)
        assertEquals(60000L, canaraRes.amountPaise)

        // Union Bank
        val unionSms = "Union Bank of India: A/c ...5566 debited for Rs.420.00 on 11-Nov-26. UPI Ref 334455"
        val unionRes = SmsParser.parse(unionSms, "UNIONB")
        assertTrue(unionRes.isValidTransaction)
        assertEquals("Union Bank of India", unionRes.bankName)
        assertEquals(42000L, unionRes.amountPaise)

        // IDFC FIRST
        val idfcSms = "Rs. 1,299.00 spent on IDFC FIRST Bank Card ending 7788 at Netflix on 10-Nov-26."
        val idfcRes = SmsParser.parse(idfcSms, "IDFCFB")
        assertTrue(idfcRes.isValidTransaction)
        assertEquals("IDFC FIRST Bank", idfcRes.bankName)
        assertEquals(129900L, idfcRes.amountPaise)
    }

    @Test
    fun testUpiAppNotifications_GooglePayPhonePePaytmCred() {
        // Google Pay
        val gpayNotif = "Paid ₹340.00 to Starbucks using Google Pay. UPI transaction ID: 432190812345"
        val gpayRes = SmsParser.parse(gpayNotif, "com.google.android.apps.nbu.paisa.user")
        assertTrue(gpayRes.isValidTransaction)
        assertEquals(34000L, gpayRes.amountPaise)
        assertEquals("Google Pay", gpayRes.bankName)
        assertEquals("Starbucks", gpayRes.merchant)

        // PhonePe
        val phonepeNotif = "Paid ₹150 to Swiggy via PhonePe. Txn ID: T261110123456"
        val phonepeRes = SmsParser.parse(phonepeNotif, "com.phonepe.app")
        assertTrue(phonepeRes.isValidTransaction)
        assertEquals(15000L, phonepeRes.amountPaise)
        assertEquals("PhonePe", phonepeRes.bankName)

        // Paytm
        val paytmNotif = "Payment of ₹85.00 successful to Chai Point via Paytm UPI. Ref 998877"
        val paytmRes = SmsParser.parse(paytmNotif, "net.one97.paytm")
        assertTrue(paytmRes.isValidTransaction)
        assertEquals(8500L, paytmRes.amountPaise)
        assertEquals("Paytm", paytmRes.bankName)

        // CRED
        val credNotif = "You paid ₹5,000 for HDFC Credit Card bill on CRED. Ref: CRD123456"
        val credRes = SmsParser.parse(credNotif, "com.dreamplug.androidapp")
        assertTrue(credRes.isValidTransaction)
        assertEquals(500000L, credRes.amountPaise)
        assertEquals("CRED", credRes.bankName)
    }

    // ==========================================
    // EXCLUSIONS & UNKNOWN HANDLING
    // ==========================================

    @Test
    fun testExclusionOfOtpMessages() {
        val otpSms = "492019 is your secret OTP for transaction of Rs. 750.00 on Amazon. Never share OTP with anyone - HDFC Bank"
        val result = SmsParser.parse(otpSms, "AD-HDFCBK-S")

        assertFalse("OTP must never be classified as valid transaction", result.isValidTransaction)
        assertTrue(result.isOtp)
    }

    @Test
    fun testExclusionOfPromotionalMessages() {
        val promoSms = "Get pre-approved Personal Loan of Rs. 5,00,000 with instant disbursal. Apply now at hdfcbank.com - HDFC Bank"
        val result = SmsParser.parse(promoSms, "AD-HDFCBK-S")

        assertFalse("Promo must never be treated as transaction", result.isValidTransaction)
        assertTrue(result.isPromotional)
    }

    @Test
    fun testExclusionOfBalanceInquiryMessages() {
        val balSms = "Available balance in your A/C XX1234 is Rs. 38,450.25 as of 11-Nov-2026. - SBI"
        val result = SmsParser.parse(balSms, "SBISMS")

        assertFalse("Balance inquiry must never be treated as transaction", result.isValidTransaction)
        assertTrue(result.isBalanceInquiry)
    }

    @Test
    fun testExclusionOfFailedTransactions() {
        val failedSms = "Transaction of Rs. 400.00 at Swiggy has failed due to bank server timeout. Money will be refunded if debited - ICICI Bank"
        val result = SmsParser.parse(failedSms, "ICICIB")

        assertFalse("Failed transaction must not be treated as expense", result.isValidTransaction)
        assertTrue(result.isFailed)
    }

    @Test
    fun testUnrecognizedMessageHandling_MarkedAsUnknown() {
        val ambiguousSms = "Notice regarding your relationship manager appointment on 15 Nov."
        val result = SmsParser.parse(ambiguousSms, "AD-HDFCBK-S")

        assertFalse("Ambiguous message must not be valid transaction", result.isValidTransaction)
        assertTrue("Must be marked as isUnknown so it can be routed to Unknown tab", result.isUnknown)
    }
}
