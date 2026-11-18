package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionParticipantEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LedgerRepairAndTransactionEditQATest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var db: RupeeFlowDatabase
    private lateinit var expenseGroupRepo: ExpenseGroupRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var friendGroupRepo: FriendGroupRepository
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var settlementRepo: SettlementRepository

    private var defaultCategoryId: Long = 1L
    private var friendViyangId: Long = 18L
    private var friendMananId: Long = 19L
    private var friendJayId: Long = 20L
    private var friendManavId: Long = 21L
    private var friendPranavId: Long = 22L

    @Before
    fun setUp() = runBlocking {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        expenseGroupRepo = ExpenseGroupRepositoryImpl(
            db.expenseGroupDao(),
            db.transactionDao(),
            db.friendGroupDao()
        )
        friendRepo = FriendRepositoryImpl(db.friendDao())
        categoryRepo = CategoryRepositoryImpl(db.categoryDao())
        friendGroupRepo = FriendGroupRepositoryImpl(db.friendGroupDao())
        transactionRepo = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), db.friendDao())
        settlementRepo = SettlementRepositoryImpl(db.settlementDao(), db.friendDao())

        // Seed default category
        defaultCategoryId = db.categoryDao().insertCategory(
            CategoryEntity(name = "General", iconName = "receipt", colorHex = "#3B82F6", isDefault = true)
        )

        // Seed friends with specific IDs from reported defects
        friendViyangId = friendRepo.addFriend(Friend(id = 18L, name = "Viyang", phoneNumber = "9876543218"))
        friendMananId = friendRepo.addFriend(Friend(id = 19L, name = "Manan", phoneNumber = "9876543219"))
        friendJayId = friendRepo.addFriend(Friend(id = 20L, name = "Jay", phoneNumber = "9876543220"))
        friendManavId = friendRepo.addFriend(Friend(id = 21L, name = "Manav", phoneNumber = "9876543221"))
        friendPranavId = friendRepo.addFriend(Friend(id = 22L, name = "Pranav", phoneNumber = "9876543222"))
    }

    @After
    fun tearDown() {
        db.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    // =========================================================================
    // 1. FRIEND NAME RESOLUTION & FALLBACK TESTS
    // =========================================================================

    @Test
    fun test001_friendNameResolution_resolvesKnownFriendFromAllFriends() {
        val allFriends = listOf(
            Friend(id = 18L, name = "Viyang"),
            Friend(id = 19L, name = "Manan")
        )
        val txn = Transaction(
            title = "Lunch",
            amountPaise = 20000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = 18L,
            participants = listOf(
                TransactionParticipant(friendId = 18L, shareAmountPaise = 10000L),
                TransactionParticipant(friendId = 19L, shareAmountPaise = 10000L)
            ),
            expenseGroupId = 1L
        )
        // Group members list initially empty or partial
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = emptyList(),
            members = emptyList(),
            allFriends = allFriends
        )

        val viyangBalance = summary.memberBalances.firstOrNull { it.memberId == 18L }
        assertNotNull(viyangBalance)
        assertEquals("Viyang", viyangBalance!!.memberName)

        val mananBalance = summary.memberBalances.firstOrNull { it.memberId == 19L }
        assertNotNull(mananBalance)
        assertEquals("Manan", mananBalance!!.memberName)

        assertFalse(viyangBalance.memberName.contains("Friend #"))
        assertFalse(mananBalance.memberName.contains("Friend #"))
    }

    @Test
    fun test002_friendNameResolution_deletedFriendFallsBackToUnavailableMember() {
        val txn = Transaction(
            title = "Coffee",
            amountPaise = 15000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 7500L),
                TransactionParticipant(friendId = 999L, shareAmountPaise = 7500L)
            ),
            expenseGroupId = 1L
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = emptyList(),
            members = emptyList(),
            allFriends = emptyList()
        )

        val member999 = summary.memberBalances.firstOrNull { it.memberId == 999L }
        assertNotNull(member999)
        assertEquals("Unavailable Member", member999!!.memberName)
        assertFalse(member999.memberName.contains("Friend #"))
        assertFalse(member999.memberName.contains("Member #"))
    }

    @Test
    fun test003_friendNameResolution_resolvesYouForNullMemberId() {
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = emptyList(),
            settlements = emptyList(),
            members = emptyList(),
            allFriends = emptyList()
        )
        val me = summary.memberBalances.firstOrNull { it.memberId == null }
        assertNotNull(me)
        assertEquals("You", me!!.memberName)
    }

    @Test
    fun test004_friendNameResolution_simplifyDebtsUsesRealNames() {
        val netBalances = mapOf<Long?, Long>(
            18L to -50000L,
            19L to 50000L
        )
        val memberNames = mapOf<Long?, String>(
            18L to "Viyang",
            19L to "Manan"
        )
        val transfers = LedgerCalculator.simplifyDebts(netBalances, memberNames)
        assertEquals(1, transfers.size)
        assertEquals("Viyang", transfers[0].fromMemberName)
        assertEquals("Manan", transfers[0].toMemberName)
        assertFalse(transfers[0].fromMemberName.contains("Member #"))
        assertFalse(transfers[0].toMemberName.contains("Member #"))
    }

    @Test
    fun test005_friendNameResolution_simplifyDebtsUnavailableMemberFallback() {
        val netBalances = mapOf<Long?, Long>(
            999L to -50000L,
            null to 50000L
        )
        val memberNames = emptyMap<Long?, String>()
        val transfers = LedgerCalculator.simplifyDebts(netBalances, memberNames)
        assertEquals(1, transfers.size)
        assertEquals("Unavailable Member", transfers[0].fromMemberName)
        assertEquals("You", transfers[0].toMemberName)
    }

    // =========================================================================
    // 2. GROUP MEMBERSHIP & GRAMMATICAL MEMBER COUNT TESTS
    // =========================================================================

    @Test
    fun test006_memberCount_singleMemberFormatsAsSingular() {
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = emptyList(),
            settlements = emptyList(),
            members = emptyList()
        )
        val count = summary.memberBalances.size
        assertEquals(1, count)
        val text = if (count == 1) "1 member" else "$count members"
        assertEquals("1 member", text)
    }

    @Test
    fun test007_memberCount_multipleMembersFormatAsPlural() {
        val allFriends = listOf(
            Friend(id = 18L, name = "Viyang"),
            Friend(id = 19L, name = "Manan")
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = emptyList(),
            settlements = emptyList(),
            members = allFriends
        )
        val count = summary.memberBalances.size
        assertEquals(3, count) // "You" + Viyang + Manan
        val text = if (count == 1) "1 member" else "$count members"
        assertEquals("3 members", text)
    }

    @Test
    fun test008_memberCount_matchesBalancesSizeExactly() {
        val allFriends = listOf(
            Friend(id = 18L, name = "Viyang"),
            Friend(id = 19L, name = "Manan"),
            Friend(id = 20L, name = "Jay")
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = emptyList(),
            settlements = emptyList(),
            members = allFriends,
            allFriends = allFriends
        )
        assertEquals(summary.memberBalances.size, 4)
    }

    // =========================================================================
    // 3. SPLIT CALCULATIONS & MATHEMATICAL CORRECTNESS
    // =========================================================================

    @Test
    fun test009_equalSplit_distributesRemainderPaiseDeterministically() {
        val totalPaise = 10000L // 100 INR among 3 people = 33.33 + 33.33 + 33.34
        val shares = LedgerCalculator.calculateEqualSplit(totalPaise, 3)
        assertEquals(3, shares.size)
        assertEquals(3334L, shares[0])
        assertEquals(3333L, shares[1])
        assertEquals(3333L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test010_percentageSplit_exactBigDecimalArithmetic() {
        val totalPaise = 10000L
        val percentages = listOf(50.0, 30.0, 20.0)
        val shares = LedgerCalculator.calculatePercentageSplit(totalPaise, percentages)
        assertEquals(3, shares.size)
        assertEquals(5000L, shares[0])
        assertEquals(3000L, shares[1])
        assertEquals(2000L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test011_percentageSplit_rejectsNonHundredPercentSum() {
        val totalPaise = 10000L
        val percentages = listOf(50.0, 30.0) // 80%
        try {
            LedgerCalculator.calculatePercentageSplit(totalPaise, percentages)
            fail("Should have thrown IllegalArgumentException for non-100% total")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Percentage shares must total exactly 100%"))
        }
    }

    @Test
    fun test012_sharesSplit_integerWeightsWithRemainderDistribution() {
        val totalPaise = 10000L
        val weights = listOf(2, 1, 1) // 4 parts total
        val shares = LedgerCalculator.calculateSharesSplit(totalPaise, weights)
        assertEquals(3, shares.size)
        assertEquals(5000L, shares[0])
        assertEquals(2500L, shares[1])
        assertEquals(2500L, shares[2])
        assertEquals(totalPaise, shares.sum())
    }

    @Test
    fun test013_validateParticipantShares_checksStrictPaisaEquality() {
        val participants = listOf(
            TransactionParticipant(friendId = null, shareAmountPaise = 6000L),
            TransactionParticipant(friendId = 18L, shareAmountPaise = 4000L)
        )
        assertTrue(LedgerCalculator.validateParticipantShares(10000L, participants))
        assertFalse(LedgerCalculator.validateParticipantShares(9999L, participants))
    }

    // =========================================================================
    // 4. GROUP SPENDING & PERSONAL SHARE RECONCILIATION
    // =========================================================================

    @Test
    fun test014_groupSpending_whenYouPayForEveryone() {
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 120000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = null, // Me paid 1,200 INR
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 18L, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 19L, shareAmountPaise = 40000L)
            ),
            expenseGroupId = 1L
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = emptyList(),
            members = listOf(Friend(18L, "Viyang"), Friend(19L, "Manan"))
        )

        assertEquals(120000L, summary.totalExpensePaise)
        assertEquals(120000L, summary.myPaidPaise)
        assertEquals(40000L, summary.mySharePaise)
        assertEquals(80000L, summary.myNetBalancePaise) // Others owe me 800 INR
    }

    @Test
    fun test015_groupSpending_whenFriendPaysForEveryone() {
        val txn = Transaction(
            title = "Hotel",
            amountPaise = 300000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = 18L, // Viyang paid 3,000 INR
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 18L, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 19L, shareAmountPaise = 100000L)
            ),
            expenseGroupId = 1L
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = emptyList(),
            members = listOf(Friend(18L, "Viyang"), Friend(19L, "Manan"))
        )

        assertEquals(300000L, summary.totalExpensePaise)
        assertEquals(0L, summary.myPaidPaise)
        assertEquals(100000L, summary.mySharePaise)
        assertEquals(-100000L, summary.myNetBalancePaise) // I owe 1,000 INR
    }

    @Test
    fun test016_groupSpending_refundReducesTotalAndShares() {
        val expense = Transaction(
            title = "Flight",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            transactionType = TransactionType.EXPENSE,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 18L, shareAmountPaise = 50000L)
            ),
            expenseGroupId = 1L
        )
        val refund = Transaction(
            title = "Flight Refund",
            amountPaise = 20000L,
            categoryId = defaultCategoryId,
            transactionDate = 2000L,
            transactionType = TransactionType.REFUND,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 10000L),
                TransactionParticipant(friendId = 18L, shareAmountPaise = 10000L)
            ),
            expenseGroupId = 1L
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(expense, refund),
            settlements = emptyList(),
            members = listOf(Friend(18L, "Viyang"))
        )

        assertEquals(80000L, summary.totalExpensePaise)
        assertEquals(80000L, summary.myPaidPaise)
        assertEquals(40000L, summary.mySharePaise)
        assertEquals(40000L, summary.myNetBalancePaise)
    }

    // =========================================================================
    // 5. SETTLEMENT LEDGER & DEBT REDUCTIONS
    // =========================================================================

    @Test
    fun test017_settlement_reducesPairwiseDebtBetweenPayerAndReceiver() {
        val txn = Transaction(
            title = "Cab",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 18L, shareAmountPaise = 50000L)
            ),
            expenseGroupId = 1L
        )
        val settlement = Settlement(
            payerId = 18L, // Viyang paid Me
            receiverId = null,
            amountPaise = 30000L,
            settlementDate = 2000L,
            groupId = 1L
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = listOf(settlement),
            members = listOf(Friend(18L, "Viyang"))
        )

        assertEquals(20000L, summary.myNetBalancePaise)
        assertEquals(-20000L, summary.memberBalances.first { it.memberId == 18L }.netBalancePaise)
        assertEquals(20000L, summary.directDebts.first().amountPaise)
    }

    @Test
    fun test018_settlement_betweenTwoFriendsInGroup() {
        // Viyang paid 1000 for Manan
        val txn = Transaction(
            title = "Movie Tickets",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = 18L, // Viyang paid
            participants = listOf(
                TransactionParticipant(friendId = 18L, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 19L, shareAmountPaise = 50000L) // Manan owes Viyang
            ),
            expenseGroupId = 1L
        )
        // Manan pays Viyang back via UPI
        val settlement = Settlement(
            payerId = 19L, // Manan
            receiverId = 18L, // Viyang
            amountPaise = 50000L,
            settlementDate = 2000L,
            paymentMethod = PaymentMethod.UPI,
            groupId = 1L
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = listOf(settlement),
            members = listOf(Friend(18L, "Viyang"), Friend(19L, "Manan")),
            allFriends = listOf(Friend(18L, "Viyang"), Friend(19L, "Manan"))
        )

        val viyang = summary.memberBalances.first { it.memberId == 18L }
        val manan = summary.memberBalances.first { it.memberId == 19L }

        assertEquals(0L, viyang.netBalancePaise)
        assertEquals(0L, manan.netBalancePaise)
        assertTrue(summary.directDebts.isEmpty())
        assertTrue(summary.simplifiedDebts.isEmpty())
    }

    @Test
    fun test019_settlement_reversalRestoresDebt() {
        val txn = Transaction(
            title = "Dinner",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 18L, shareAmountPaise = 50000L)
            ),
            expenseGroupId = 1L
        )
        val reversedSettlement = Settlement(
            payerId = 18L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            groupId = 1L,
            isReversed = true
        )
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = 1L,
            transactions = listOf(txn),
            settlements = listOf(reversedSettlement),
            members = listOf(Friend(18L, "Viyang"))
        )

        assertEquals(50000L, summary.myNetBalancePaise)
        assertEquals(-50000L, summary.memberBalances.first { it.memberId == 18L }.netBalancePaise)
    }

    @Test
    fun test020_settlement_rejectsSamePayerAndReceiver() = runBlocking {
        try {
            settlementRepo.recordSettlement(
                Settlement(
                    payerId = 18L,
                    receiverId = 18L,
                    amountPaise = 10000L,
                    groupId = 1L
                )
            )
            fail("Recording settlement with same payer and receiver must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Payer and receiver cannot be the same"))
        }
    }

    // =========================================================================
    // 6. DEBT SIMPLIFICATION TESTS
    // =========================================================================

    @Test
    fun test021_debtSimplification_deterministicTieBreaking() {
        val netBalances = mapOf<Long?, Long>(
            18L to -20000L,
            19L to -20000L,
            20L to 40000L
        )
        val memberNames = mapOf<Long?, String>(
            18L to "Viyang",
            19L to "Manan",
            20L to "Jay"
        )
        val run1 = LedgerCalculator.simplifyDebts(netBalances, memberNames)
        val run2 = LedgerCalculator.simplifyDebts(netBalances, memberNames)

        assertEquals(run1.size, run2.size)
        for (i in run1.indices) {
            assertEquals(run1[i].fromMemberId, run2[i].fromMemberId)
            assertEquals(run1[i].toMemberId, run2[i].toMemberId)
            assertEquals(run1[i].amountPaise, run2[i].amountPaise)
        }
    }

    @Test
    fun test022_debtSimplification_preservesExactNetBalancesDownToZeroRemainder() {
        val netBalances = mapOf<Long?, Long>(
            null to -30000L,
            18L to -20000L,
            19L to 50000L
        )
        val memberNames = mapOf<Long?, String>(
            null to "You",
            18L to "Viyang",
            19L to "Manan"
        )
        val transfers = LedgerCalculator.simplifyDebts(netBalances, memberNames)

        val simulatedNets = mutableMapOf<Long?, Long>()
        for (m in netBalances.keys) simulatedNets[m] = 0L

        for (t in transfers) {
            simulatedNets[t.fromMemberId] = (simulatedNets[t.fromMemberId] ?: 0L) - t.amountPaise
            simulatedNets[t.toMemberId] = (simulatedNets[t.toMemberId] ?: 0L) + t.amountPaise
        }

        for ((m, net) in netBalances) {
            assertEquals(net, simulatedNets[m])
        }
    }

    @Test
    fun test023_debtSimplification_emptyBalancesReturnsEmptyTransfers() {
        val transfers = LedgerCalculator.simplifyDebts(emptyMap(), emptyMap())
        assertTrue(transfers.isEmpty())
    }

    // =========================================================================
    // 7. TRANSACTION EDIT LIFECYCLE & PERSISTENCE TESTS
    // =========================================================================

    @Test
    fun test024_editTransaction_updatesTitleAndAmountPreservingIdAndCreatedAt() = runBlocking {
        val originalCreatedAt = 1600000000000L
        val txn = Transaction(
            title = "Original Lunch",
            amountPaise = 50000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            createdAt = originalCreatedAt,
            updatedAt = originalCreatedAt
        )
        val id = transactionRepo.saveTransaction(txn)
        assertTrue(id > 0L)

        val retrieved = transactionRepo.getTransactionById(id)
        assertNotNull(retrieved)
        assertEquals("Original Lunch", retrieved!!.title)
        assertEquals(50000L, retrieved.amountPaise)

        // Edit Title and Amount
        val updated = retrieved.copy(
            title = "Team Lunch at Bistro",
            amountPaise = 75000L,
            updatedAt = System.currentTimeMillis()
        )
        transactionRepo.saveTransaction(updated)

        val afterEdit = transactionRepo.getTransactionById(id)
        assertNotNull(afterEdit)
        assertEquals(id, afterEdit!!.id)
        assertEquals("Team Lunch at Bistro", afterEdit.title)
        assertEquals(75000L, afterEdit.amountPaise)
        assertEquals(originalCreatedAt, afterEdit.createdAt)
        assertTrue(afterEdit.updatedAt >= originalCreatedAt)
    }

    @Test
    fun test025_editTransaction_updatesParticipantsWithoutOrphanedRecords() = runBlocking {
        val txn = Transaction(
            title = "Shared Taxi",
            amountPaise = 60000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = friendViyangId, shareAmountPaise = 30000L)
            )
        )
        val id = transactionRepo.saveTransaction(txn)

        val stored1 = transactionRepo.getTransactionById(id)!!
        assertEquals(2, stored1.participants.size)

        // Edit transaction: change participants to 3 members
        val editedTxn = stored1.copy(
            amountPaise = 60000L,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 20000L),
                TransactionParticipant(friendId = friendViyangId, shareAmountPaise = 20000L),
                TransactionParticipant(friendId = friendMananId, shareAmountPaise = 20000L)
            )
        )
        transactionRepo.saveTransaction(editedTxn)

        val stored2 = transactionRepo.getTransactionById(id)!!
        assertEquals(3, stored2.participants.size)
        assertEquals(setOf(null, friendViyangId, friendMananId), stored2.participants.map { it.friendId }.toSet())

        // Verify direct database query has exactly 3 participant rows for this transaction
        val rawTxnWithParticipants = db.transactionDao().getTransactionById(id)
        assertNotNull(rawTxnWithParticipants)
        assertEquals(3, rawTxnWithParticipants!!.participants.size)
    }

    @Test
    fun test026_editTransaction_billImagePathPreserved() = runBlocking {
        val txn = Transaction(
            title = "Grocery",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            billImagePath = "/data/user/0/com.naman.rupeeflow/files/bills/bill_123.jpg"
        )
        val id = transactionRepo.saveTransaction(txn)

        val retrieved = transactionRepo.getTransactionById(id)!!
        assertEquals("/data/user/0/com.naman.rupeeflow/files/bills/bill_123.jpg", retrieved.billImagePath)

        val edited = retrieved.copy(
            billImagePath = "/data/user/0/com.naman.rupeeflow/files/bills/bill_updated.jpg"
        )
        transactionRepo.saveTransaction(edited)

        val afterEdit = transactionRepo.getTransactionById(id)!!
        assertEquals("/data/user/0/com.naman.rupeeflow/files/bills/bill_updated.jpg", afterEdit.billImagePath)
    }

    @Test
    fun test027_editTransaction_locationMetadataPreservedAndUpdated() = runBlocking {
        val txn = Transaction(
            title = "Cafe Coffee Day",
            amountPaise = 35000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 5.0f,
            locationSource = LocationSource.DEVICE_AT_DETECTION,
            locationLabel = "CCD MG Road",
            googleMapsUrl = "https://maps.google.com/?q=12.9716,77.5946"
        )
        val id = transactionRepo.saveTransaction(txn)

        val retrieved = transactionRepo.getTransactionById(id)!!
        assertTrue(retrieved.hasLocation)
        assertEquals(12.9716, retrieved.latitude!!, 0.0001)
        assertEquals(77.5946, retrieved.longitude!!, 0.0001)
        assertEquals("CCD MG Road", retrieved.locationLabel)

        // Edit location
        val updatedLocation = retrieved.copy(
            latitude = 12.9750,
            longitude = 77.6000,
            locationLabel = "CCD Brigade Road"
        )
        transactionRepo.saveTransaction(updatedLocation)

        val afterEdit = transactionRepo.getTransactionById(id)!!
        assertEquals(12.9750, afterEdit.latitude!!, 0.0001)
        assertEquals("CCD Brigade Road", afterEdit.locationLabel)
    }

    @Test
    fun test028_editTransaction_datePreservedAcrossMonthBoundary() = runBlocking {
        val novemberDate = 1763462400000L // 18 Nov 2025
        val decemberDate = 1766054400000L // 18 Dec 2025

        val txn = Transaction(
            title = "Monthly Utility",
            amountPaise = 150000L,
            categoryId = defaultCategoryId,
            transactionDate = novemberDate
        )
        val id = transactionRepo.saveTransaction(txn)

        val retrieved = transactionRepo.getTransactionById(id)!!
        assertEquals(novemberDate, retrieved.transactionDate)

        val edited = retrieved.copy(transactionDate = decemberDate)
        transactionRepo.saveTransaction(edited)

        val afterEdit = transactionRepo.getTransactionById(id)!!
        assertEquals(decemberDate, afterEdit.transactionDate)
    }

    @Test
    fun test029_saveTransactionWithParticipants_rejectsMismatchedParticipantSum() = runBlocking {
        val entity = TransactionEntity(
            title = "Invalid Split",
            amountPaise = 10000L, // 100 INR
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val invalidParticipants = listOf(
            TransactionParticipantEntity(transactionId = 0L, friendId = null, shareAmountPaise = 5000L),
            TransactionParticipantEntity(transactionId = 0L, friendId = friendViyangId, shareAmountPaise = 4000L) // Sum = 9000L != 10000L
        )
        try {
            db.transactionDao().saveTransactionWithParticipants(entity, invalidParticipants)
            fail("Saving with mismatched participant shares must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Participant shares"))
        }
    }

    @Test
    fun test030_saveTransactionWithParticipants_rejectsDuplicateParticipants() = runBlocking {
        val entity = TransactionEntity(
            title = "Duplicate Participant",
            amountPaise = 10000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val duplicateParticipants = listOf(
            TransactionParticipantEntity(transactionId = 0L, friendId = friendViyangId, shareAmountPaise = 5000L),
            TransactionParticipantEntity(transactionId = 0L, friendId = friendViyangId, shareAmountPaise = 5000L)
        )
        try {
            db.transactionDao().saveTransactionWithParticipants(entity, duplicateParticipants)
            fail("Saving with duplicate participants must be rejected")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Duplicate participants are not allowed"))
        }
    }
}
