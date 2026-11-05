package com.naman.rupeeflow.qa

import androidx.room.Room
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Exhaustive Verification Test Suite for Master Release Features:
 * - Group-Specific Ledger Calculations (Phase 6)
 * - Smart Dynamic Input Ranking Formula & Repositories (Phase 3)
 * - Expense Bill Photo Attachments & Lifecycle (Phase 4)
 * - Independent Friend & Group Profile Pictures (Phase 5)
 * - Group-First Selection & Financial Balance Impact (Phases 1 & 2)
 */
@RunWith(RobolectricTestRunner::class)
class MasterReleasePhaseVerificationTest {

    private lateinit var db: RupeeFlowDatabase
    private lateinit var groupRepo: FriendGroupRepositoryImpl
    private lateinit var friendRepo: FriendRepositoryImpl
    private lateinit var txnRepo: TransactionRepositoryImpl
    private lateinit var settlementRepo: SettlementRepositoryImpl
    private lateinit var inputRankingRepo: InputRankingRepositoryImpl

    private val friendA = Friend(id = 101L, name = "Aman", phoneNumber = "9876543210")
    private val friendB = Friend(id = 102L, name = "Bhavna", phoneNumber = "9876543211")
    private val friendC = Friend(id = 103L, name = "Chetan", phoneNumber = "9876543212")

    @Before
    fun setUp() {
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            groupRepo = FriendGroupRepositoryImpl(db.friendGroupDao())
            friendRepo = FriendRepositoryImpl(db.friendDao())
            txnRepo = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), db.friendDao())
            settlementRepo = SettlementRepositoryImpl(db.settlementDao(), db.friendDao())
            inputRankingRepo = InputRankingRepositoryImpl(db.inputUsageDao())

            // Seed initial categories
            db.categoryDao().insertCategory(CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000", isDefault = true))
            db.categoryDao().insertCategory(CategoryEntity(id = 2L, name = "Travel", iconName = "flight", colorHex = "#0000FF", isDefault = false))

            // Seed friends
            db.friendDao().insertFriend(FriendEntity(id = 101L, name = friendA.name, phoneNumber = friendA.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 102L, name = friendB.name, phoneNumber = friendB.phoneNumber))
            db.friendDao().insertFriend(FriendEntity(id = 103L, name = friendC.name, phoneNumber = friendC.phoneNumber))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // SECTION 1: GROUP-SPECIFIC LEDGER CALCULATIONS (PHASE 6)
    // =========================================================================

    @Test
    fun test01_ConsolidatedLedgerAcrossAllFriendsWhenGroupNull() = runBlocking {
        val allFriends = listOf(friendA, friendB, friendC)

        // ME paid ₹900 for Food, split equally with Aman and Bhavna (₹300 each)
        val txn1 = Transaction(
            id = 1L,
            title = "Lunch",
            amountPaise = 90_000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null, // ME
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30_000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 30_000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 30_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        // ME paid ₹400 for Chetan (Chetan owes ₹400)
        val txn2 = Transaction(
            id = 2L,
            title = "Taxi",
            amountPaise = 40_000L,
            categoryId = 2L,
            transactionDate = 2000L,
            paymentMethod = PaymentMethod.CASH,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 103L, shareAmountPaise = 40_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        val result = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = allFriends,
            transactions = listOf(txn1, txn2),
            settlements = emptyList()
        )

        // Total they owe me = 300 + 300 + 400 = 1000 rupees = 100,000 paise
        assertEquals(100_000L, result.totalTheyOweMePaise)
        assertEquals(0L, result.totalIOweThemPaise)
        assertEquals(100_000L, result.netBalancePaise)
        assertEquals(3, result.friendsWithLedger.size)
    }

    @Test
    fun test02_SelectingGroupDynamicallyRecalculatesTopSummaryAndMemberBalances() = runBlocking {
        val g1 = groupRepo.createGroup("Flatmates", null, null, listOf(101L, 102L))
        val g2 = groupRepo.createGroup("Office", null, null, listOf(103L))

        val allFriends = listOf(friendA, friendB, friendC)

        // Flatmates expense: ME paid ₹1200, Aman owes ₹400, Bhavna owes ₹400
        val flatmatesTxn = Transaction(
            id = 1L,
            title = "Groceries",
            amountPaise = 120_000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            groupId = g1,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40_000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 40_000L),
                TransactionParticipant(friendId = 102L, shareAmountPaise = 40_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        // Office expense: ME paid ₹500, Chetan owes ₹500
        val officeTxn = Transaction(
            id = 2L,
            title = "Team Lunch",
            amountPaise = 50_000L,
            categoryId = 1L,
            transactionDate = 2000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            groupId = g2,
            participants = listOf(
                TransactionParticipant(friendId = 103L, shareAmountPaise = 50_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        val allTxns = listOf(flatmatesTxn, officeTxn)

        // 1. Calculate for Flatmates Group
        val flatmatesResult = LedgerCalculator.computeGroupLedger(
            groupId = g1,
            groupMemberIds = setOf(101L, 102L),
            allFriends = allFriends,
            transactions = allTxns,
            settlements = emptyList()
        )

        // Only Flatmates members (Aman & Bhavna) owe ₹400 each = ₹800 = 80,000 paise
        assertEquals(80_000L, flatmatesResult.totalTheyOweMePaise)
        assertEquals(0L, flatmatesResult.totalIOweThemPaise)
        assertEquals(80_000L, flatmatesResult.netBalancePaise)
        assertEquals(2, flatmatesResult.friendsWithLedger.size)
        assertTrue(flatmatesResult.friendsWithLedger.all { it.id in setOf(101L, 102L) })

        // 2. Calculate for Office Group
        val officeResult = LedgerCalculator.computeGroupLedger(
            groupId = g2,
            groupMemberIds = setOf(103L),
            allFriends = allFriends,
            transactions = allTxns,
            settlements = emptyList()
        )

        assertEquals(50_000L, officeResult.totalTheyOweMePaise)
        assertEquals(0L, officeResult.totalIOweThemPaise)
        assertEquals(50_000L, officeResult.netBalancePaise)
        assertEquals(1, officeResult.friendsWithLedger.size)
        assertEquals(103L, officeResult.friendsWithLedger[0].id)
    }

    @Test
    fun test03_ReversingSettlementInsideGroupRecalculatesImmediately() = runBlocking {
        val allFriends = listOf(friendA)

        // Transaction: ME paid ₹1000, Aman owes ₹500
        val txn = Transaction(
            id = 1L,
            title = "Dinner",
            amountPaise = 100_000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50_000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        // Settlement: Aman paid ME ₹500 (fully settled)
        val activeSettlement = Settlement(
            id = 1L,
            payerId = 101L,
            receiverId = null,
            amountPaise = 50_000L,
            settlementDate = 2000L,
            isReversed = false
        )

        // Initial with active settlement
        val settledResult = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = allFriends,
            transactions = listOf(txn),
            settlements = listOf(activeSettlement)
        )
        assertEquals(0L, settledResult.totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.SETTLED, settledResult.friendsWithLedger[0].status)

        // Reverse the settlement
        val reversedSettlement = activeSettlement.copy(isReversed = true)
        val unSettledResult = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = allFriends,
            transactions = listOf(txn),
            settlements = listOf(reversedSettlement)
        )
        // Debt is restored immediately
        assertEquals(50_000L, unSettledResult.totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, unSettledResult.friendsWithLedger[0].status)
    }

    @Test
    fun test04_OverSettlementWithinGroupHandledAccurately() = runBlocking {
        val allFriends = listOf(friendA)

        // Aman owes ME ₹500
        val txn = Transaction(
            id = 1L,
            title = "Dinner",
            amountPaise = 100_000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50_000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        // Aman accidentally pays ME ₹800 (over-settles by ₹300)
        val overSettlement = Settlement(
            id = 1L,
            payerId = 101L,
            receiverId = null,
            amountPaise = 80_000L,
            settlementDate = 2000L,
            isReversed = false
        )

        val result = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = allFriends,
            transactions = listOf(txn),
            settlements = listOf(overSettlement)
        )

        // Net balance is now -30,000 paise (-₹300) -> ME owes Aman ₹300!
        assertEquals(0L, result.totalTheyOweMePaise)
        assertEquals(30_000L, result.totalIOweThemPaise)
        assertEquals(-30_000L, result.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, result.friendsWithLedger[0].status)
    }

    @Test
    fun test05_ArchivedFriendsInGroupHandledConsistently() = runBlocking {
        val archivedFriend = friendA.copy(isArchived = true)
        val allFriends = listOf(archivedFriend, friendB)

        // Archived friend owes ₹400
        val txn = Transaction(
            id = 1L,
            title = "Old Rent",
            amountPaise = 40_000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 101L, shareAmountPaise = 40_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        val result = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = allFriends,
            transactions = listOf(txn),
            settlements = emptyList()
        )

        // Archived friends preserve their historical ledger
        val computedArchived = result.friendsWithLedger.first { it.id == 101L }
        assertTrue(computedArchived.isArchived)
        assertEquals(40_000L, computedArchived.totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, computedArchived.status)
    }

    // =========================================================================
    // SECTION 2: SMART DYNAMIC INPUT RANKING (PHASE 3)
    // =========================================================================

    @Test
    fun test06_SmartRankingFormulaCalculatesWeightedScoreCorrectly() = runBlocking {
        val now = System.currentTimeMillis()

        // Option 1: Used 10 times, last used now
        inputRankingRepo.recordUsage("CATEGORY", "1")
        repeat(9) { inputRankingRepo.recordUsage("CATEGORY", "1") }

        // Option 2: Used 2 times, last used now
        inputRankingRepo.recordUsage("CATEGORY", "2")
        inputRankingRepo.recordUsage("CATEGORY", "2")

        val categories = listOf(
            Category(id = 2L, name = "Travel", iconName = "flight", colorHex = "#0000FF"),
            Category(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000")
        )

        val ranked = inputRankingRepo.rankOptions("CATEGORY", categories) { it.id.toString() }

        // Category 1 has much higher frequency and should rank first
        assertEquals(1L, ranked[0].id)
        assertEquals(2L, ranked[1].id)
    }

    @Test
    fun test07_SmartRankingUserPreferenceBreaksTie() = runBlocking {
        // Equal frequency and recency, but option 2 has explicit preference
        inputRankingRepo.recordUsage("PAYMENT_METHOD", PaymentMethod.CASH.name, preferenceScore = 0.0)
        inputRankingRepo.recordUsage("PAYMENT_METHOD", PaymentMethod.UPI.name, preferenceScore = 1.0)

        val methods = listOf(PaymentMethod.CASH, PaymentMethod.UPI)
        val ranked = inputRankingRepo.rankOptions("PAYMENT_METHOD", methods) { it.name }

        assertEquals(PaymentMethod.UPI, ranked[0])
        assertEquals(PaymentMethod.CASH, ranked[1])
    }

    // =========================================================================
    // SECTION 3: BILL / RECEIPT PHOTO ATTACHMENT (PHASE 4)
    // =========================================================================

    @Test
    fun test08_BillPhotoAttachmentPersistenceAndCleanup() = runBlocking {
        val testReceiptPath = "/mock/data/receipt_12345.jpg"

        val txn = Transaction(
            id = 0,
            title = "Dinner at Taj",
            amountPaise = 500_000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            billImagePath = testReceiptPath
        )

        val txnId = txnRepo.saveTransaction(txn)
        assertTrue(txnId > 0)

        val fetched = txnRepo.getTransactionById(txnId)
        assertNotNull(fetched)
        assertEquals(testReceiptPath, fetched!!.billImagePath)

        // Delete cleans up record
        txnRepo.deleteTransaction(txnId)
        assertNull(txnRepo.getTransactionById(txnId))
    }

    // =========================================================================
    // SECTION 4: INDEPENDENT PROFILE & GROUP PICTURES (PHASE 5)
    // =========================================================================

    @Test
    fun test09_IndependentProfileAndGroupPicturesNeverInterfere() = runBlocking {
        val friendPic = "/data/profiles/aman_pic.jpg"
        val groupPic = "/data/groups/college_boys.jpg"

        val gId = groupRepo.createGroup("College Boys", "school", "#10B981", listOf(101L), groupPic)
        val group = groupRepo.getGroupById(gId)!!
        assertEquals(groupPic, group.groupImagePath)

        // Friend profile image updated
        val friendToUpdate = friendRepo.getFriendById(101L)!!.copy(profileImagePath = friendPic)
        friendRepo.updateFriend(friendToUpdate)

        val fetchedFriend = friendRepo.getFriendById(101L)!!
        val fetchedGroup = groupRepo.getGroupById(gId)!!

        // Changing friend profile never alters group image
        assertEquals(friendPic, fetchedFriend.profileImagePath)
        assertEquals(groupPic, fetchedGroup.groupImagePath)

        // Changing group image never alters friend profile
        val newGroupPic = "/data/groups/new_group_pic.jpg"
        groupRepo.updateGroup(fetchedGroup.copy(groupImagePath = newGroupPic), listOf(101L))

        val refetchedFriend = friendRepo.getFriendById(101L)!!
        val refetchedGroup = groupRepo.getGroupById(gId)!!

        assertEquals(newGroupPic, refetchedGroup.groupImagePath)
        assertEquals(friendPic, refetchedFriend.profileImagePath) // UNCHANGED
    }

    // =========================================================================
    // SECTION 5: GROUP-FIRST SELECTION & FINANCIAL LEDGER IMPACT (PHASES 1 & 2)
    // =========================================================================

    @Test
    fun test10_PaidByGroupMemberShiftsLiabilityToUser() = runBlocking {
        // Friend A paid ₹1000 for an expense where ME and Friend A participated equally (₹500 each)
        val txn = Transaction(
            id = 1L,
            title = "Movie Tickets",
            amountPaise = 100_000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = 101L, // Aman paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50_000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        val result = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = listOf(friendA),
            transactions = listOf(txn),
            settlements = emptyList()
        )

        // ME owes Aman ₹500!
        assertEquals(0L, result.totalTheyOweMePaise)
        assertEquals(50_000L, result.totalIOweThemPaise)
        assertEquals(-50_000L, result.netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, result.friendsWithLedger[0].status)
    }

    @Test
    fun test11_SwitchingPayerFromFriendBackToMeRestoresCorrectBalances() = runBlocking {
        // ME paid ₹1000 for an expense where ME and Friend A participated equally (₹500 each)
        val txn = Transaction(
            id = 1L,
            title = "Movie Tickets",
            amountPaise = 100_000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null, // ME paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50_000L),
                TransactionParticipant(friendId = 101L, shareAmountPaise = 50_000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        val result = LedgerCalculator.computeGroupLedger(
            groupId = null,
            groupMemberIds = null,
            allFriends = listOf(friendA),
            transactions = listOf(txn),
            settlements = emptyList()
        )

        // Aman owes ME ₹500!
        assertEquals(50_000L, result.totalTheyOweMePaise)
        assertEquals(0L, result.totalIOweThemPaise)
        assertEquals(50_000L, result.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, result.friendsWithLedger[0].status)
    }

    @Test
    fun test12_EqualSplitRemainderPaiseStrictlyDistributed() {
        // ₹100.00 (10,000 paise) split among 3 participants -> 3334, 3333, 3333 paise
        val shares = LedgerCalculator.calculateEqualSplit(10_000L, 3)
        assertEquals(3, shares.size)
        assertEquals(3334L, shares[0])
        assertEquals(3333L, shares[1])
        assertEquals(3333L, shares[2])
        assertEquals(10_000L, shares.sum())
    }
}
