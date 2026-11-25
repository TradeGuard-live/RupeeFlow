package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.FriendRepositoryImpl
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.SettlementRepositoryImpl
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.data.repository.TransactionRepositoryImpl
import com.naman.rupeeflow.data.sms.parser.ReferenceExtractor
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.upi.UpiSettlementManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ItemWiseFeeAndIncomeReferenceQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var friendRepository: FriendRepository
    private lateinit var settlementRepository: SettlementRepository
    private lateinit var upiSettlementManager: UpiSettlementManager

    private var friendAId: Long = 0L
    private var friendBId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        context = RuntimeEnvironment.getApplication()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)

        friendRepository = FriendRepositoryImpl(database.friendDao())
        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            friendDao = database.friendDao()
        )
        settlementRepository = SettlementRepositoryImpl(
            settlementDao = database.settlementDao(),
            friendDao = database.friendDao(),
            context = context
        )
        upiSettlementManager = UpiSettlementManager(
            settlementRepository = settlementRepository,
            groupActivityRepository = null,
            friendRepository = friendRepository
        )

        friendAId = database.friendDao().insertFriend(FriendEntity(name = "Friend A", upiId = "frienda@upi"))
        friendBId = database.friendDao().insertFriend(FriendEntity(name = "Friend B", upiId = "friendb@upi"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test001_itemWiseSplit_spreadsDeliveryAndHandlingFeeAcrossParticipatingMembers() = runBlocking {
        // Given an order with items and delivery fee:
        // Item 1: Pizza ₹400 (40,000 paise) shared by Me (null) and Friend A
        // Item 2: Burger ₹200 (20,000 paise) assigned solely to Friend A
        // Delivery / Handling fee: ₹60 (6,000 paise)
        // Friend B is in the friend list but has NO items assigned (excluded from expense)
        val allParticipantIds: List<Long?> = listOf(null, friendAId, friendBId)

        val item1Assigned = listOf(null, friendAId)
        val item1Splits = LedgerCalculator.calculateEqualSplit(40_000L, item1Assigned.size) // 20,000 each

        val item2Assigned = listOf(friendAId)
        val item2Splits = LedgerCalculator.calculateEqualSplit(20_000L, item2Assigned.size) // 20,000

        // Participating members in items: Me (null) and Friend A
        val includedParticipantIds = allParticipantIds.filter { pId ->
            item1Assigned.contains(pId) || item2Assigned.contains(pId)
        }
        assertEquals(2, includedParticipantIds.size)
        assertTrue(includedParticipantIds.contains(null))
        assertTrue(includedParticipantIds.contains(friendAId))
        assertFalse("Friend B had no items and must not be charged delivery fee", includedParticipantIds.contains(friendBId))

        // Delivery fee spread: ₹60 / 2 = ₹30 (3,000 paise each)
        val deliveryFeePaise = 6_000L
        val feeSplits = LedgerCalculator.calculateEqualSplit(deliveryFeePaise, includedParticipantIds.size)
        val feeShareByParticipant = includedParticipantIds.mapIndexed { idx, pId ->
            pId to feeSplits[idx]
        }.toMap()

        assertEquals(3_000L, feeShareByParticipant[null])
        assertEquals(3_000L, feeShareByParticipant[friendAId])

        // Total calculated participant shares:
        // Me: ₹200 (item) + ₹30 (fee) = ₹230 (23,000 paise)
        // Friend A: ₹200 (item 1) + ₹200 (item 2) + ₹30 (fee) = ₹430 (43,000 paise)
        // Friend B: ₹0
        val myTotalShare = item1Splits[0] + feeShareByParticipant[null]!!
        val friendATotalShare = item1Splits[1] + item2Splits[0] + feeShareByParticipant[friendAId]!!
        val friendBTotalShare = 0L

        assertEquals(23_000L, myTotalShare)
        assertEquals(43_000L, friendATotalShare)
        assertEquals(0L, friendBTotalShare)

        val totalBillPaise = 40_000L + 20_000L + 6_000L // ₹660.00
        assertEquals(totalBillPaise, myTotalShare + friendATotalShare + friendBTotalShare)

        // Save transaction with items & delivery fee item
        val domainItems = listOf(
            TransactionItem(
                name = "Pizza",
                quantity = 1,
                unitPricePaise = 40_000L,
                totalAmountPaise = 40_000L,
                allocations = listOf(
                    ItemAllocation(friendId = null, allocatedAmountPaise = 20_000L),
                    ItemAllocation(friendId = friendAId, allocatedAmountPaise = 20_000L)
                )
            ),
            TransactionItem(
                name = "Burger",
                quantity = 1,
                unitPricePaise = 20_000L,
                totalAmountPaise = 20_000L,
                allocations = listOf(
                    ItemAllocation(friendId = friendAId, allocatedAmountPaise = 20_000L)
                )
            ),
            TransactionItem(
                name = "Delivery & Handling Fee",
                quantity = 1,
                unitPricePaise = 6_000L,
                totalAmountPaise = 6_000L,
                allocations = listOf(
                    ItemAllocation(friendId = null, allocatedAmountPaise = 3_000L),
                    ItemAllocation(friendId = friendAId, allocatedAmountPaise = 3_000L)
                )
            )
        )

        val participants = listOf(
            TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = myTotalShare, splitMethod = SplitMethod.ITEM_WISE),
            TransactionParticipant(friendId = friendAId, friendName = "Friend A", shareAmountPaise = friendATotalShare, splitMethod = SplitMethod.ITEM_WISE),
            TransactionParticipant(friendId = friendBId, friendName = "Friend B", shareAmountPaise = friendBTotalShare, splitMethod = SplitMethod.ITEM_WISE)
        )

        val txnId = transactionRepository.saveTransaction(
            Transaction(
                title = "Swiggy Dinner",
                amountPaise = totalBillPaise,
                transactionType = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.UPI,
                categoryId = 1L,
                splitMethod = SplitMethod.ITEM_WISE,
                participants = participants,
                items = domainItems
            )
        )

        val retrieved = transactionRepository.getTransactionById(txnId)
        assertNotNull(retrieved)
        assertEquals(totalBillPaise, retrieved!!.amountPaise)
        assertEquals(3, retrieved.items.size)
        assertTrue(retrieved.items.any { it.name == "Delivery & Handling Fee" && it.totalAmountPaise == 6_000L })
    }

    @Test
    fun test002_itemWiseSplit_oddPaiseDeliveryFee_preservesExactPaiseWithZeroRemainder() {
        // Delivery fee of ₹50.00 (5,000 paise) split equally among 3 members
        val feeSplits = LedgerCalculator.calculateEqualSplit(5_000L, 3)
        assertEquals(3, feeSplits.size)
        assertEquals(1667L, feeSplits[0])
        assertEquals(1667L, feeSplits[1])
        assertEquals(1666L, feeSplits[2])
        assertEquals(5_000L, feeSplits.sum())
    }

    @Test
    fun test003_incomeSmsReferenceExtraction_iciciBank() {
        val sms = "Dear Customer, your ICICI Bank Account XX1234 has been credited with INR 500.00 on 25-Nov-26. UPI:432612345678-Rahul. Available Balance is INR 12,345.00."
        val extractedUpi = ReferenceExtractor.extractUpiReference(sms)
        val extractedGeneral = ReferenceExtractor.extract(sms)
        assertEquals("432612345678", extractedUpi)
        assertEquals("432612345678", extractedGeneral)
    }

    @Test
    fun test004_incomeSmsReferenceExtraction_sbi() {
        val sms = "Dear SBI User, your A/c ending 1234 credited by Rs. 500.00 on 25Nov26 by UPI:432612345678. - SBI"
        val extractedUpi = ReferenceExtractor.extractUpiReference(sms)
        assertEquals("432612345678", extractedUpi)

        val smsRefNo = "Dear SBI User, your A/c ending 1234 credited by Rs. 500.00 on 25Nov26 by Transfer from JOHN DOE Ref No 432612345678."
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(smsRefNo))
    }

    @Test
    fun test005_incomeSmsReferenceExtraction_hdfcAndVpa() {
        val sms = "Your A/c 1234 is credited with INR 5,000.00 by VPA rahul@okaxis (UPI Ref. No. 432109876543)."
        val extractedUpi = ReferenceExtractor.extractUpiReference(sms)
        assertEquals("432109876543", extractedUpi)

        val smsSlash = "Your A/C ending 1234 is credited with INR 500.00 on 25-Nov-26. Info: UPI/432612345678/From John. Bal INR 10,000.00."
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(smsSlash))

        val smsCr = "Rs 350.00 credited to A/c XX5678 via UPI/CR/998877665544/Friend A."
        assertEquals("998877665544", ReferenceExtractor.extractUpiReference(smsCr))
    }

    @Test
    fun test006_incomeNotification_googlePay() {
        val notification1 = "Rahul Sharma paid you ₹500. UPI Ref: 432612345678"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(notification1))

        val notification2 = "Received ₹500 from Rahul Sharma. UPI transaction ID: 432612345678"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(notification2))
    }

    @Test
    fun test007_incomeNotification_phonePe() {
        val notification1 = "You have received Rs 500 from Naman (PhonePe Ref: 432612345678)"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(notification1))

        val notification2 = "PhonePe: ₹500 received from John Doe. UTR: 432612345678"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(notification2))
    }

    @Test
    fun test008_incomeSms_utrVariations() {
        val sms1 = "A/c *1234 credited by Rs 500.00 on 25-11-2026 through UPI transfer (UTR no 432612345678) by John Doe"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(sms1))

        val sms2 = "Txn done via UTR/765432109876"
        assertEquals("765432109876", ReferenceExtractor.extractUpiReference(sms2))

        val sms3 = "Credit alert: Rs 500.00 deposited into A/c 1234 on 25/11/2026. UTR No. 432612345678."
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(sms3))
    }

    @Test
    fun test009_incomeSms_impsAndNeft() {
        val imps1 = "INR 500.00 deposited in your A/C 1234 by IMPS-432612345678-JOHN"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(imps1))

        val imps2 = "Rs 500.00 credited to A/c 1234 on 25-Nov-26 by IMPS/432612345678"
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(imps2))

        val imps3 = "Update! INR 500.00 deposited in your A/C 1234 on 25-Nov-26 by IMPS Ref 432612345678. Bal: INR 10,000.00."
        assertEquals("432612345678", ReferenceExtractor.extractUpiReference(imps3))
    }

    @Test
    fun test010_incomeTransaction_reconcilesActiveSettlementUsingExtractedUpiRef() = runBlocking {
        val friendA = friendRepository.getFriendById(friendAId)!!

        // User initiates payment request of 500 from Friend A with QR reference "432612345678"
        upiSettlementManager.initiateQrSettlement(
            friend = friendA,
            amountPaise = 50_000L,
            isPayingFriend = false, // Friend pays user (INCOME)
            qrReference = "432612345678"
        )

        // Incoming bank credit SMS with ICICI UPI format:
        val rawIncomeNotification = "Account XX1234 credited with INR 500.00 on 25-Nov-26. UPI:432612345678-Friend A. Available Balance is INR 12,345.00."

        val reconciled = upiSettlementManager.reconcileIncomingTransaction(
            amountPaise = 50_000L,
            type = TransactionType.INCOME,
            referenceNumber = ReferenceExtractor.extract(rawIncomeNotification),
            upiReferenceNumber = ReferenceExtractor.extractUpiReference(rawIncomeNotification),
            merchantOrPayee = "Friend A",
            senderUpiId = "frienda@upi",
            rawContent = rawIncomeNotification
        )

        assertNotNull("Settlement must be reconciled automatically from income reference", reconciled)
        assertEquals(SettlementPaymentStatus.SUCCESS, reconciled!!.upiPaymentStatus)
        assertEquals(SettlementVerificationStatus.AUTOMATIC_VERIFIED, reconciled.upiVerificationStatus)
    }
}
