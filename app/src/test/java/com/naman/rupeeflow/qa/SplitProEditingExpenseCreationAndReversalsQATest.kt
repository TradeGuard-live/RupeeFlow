package com.naman.rupeeflow.qa

import android.content.Context
import androidx.room.Room
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
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
class SplitProEditingExpenseCreationAndReversalsQATest {

    private val testDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var db: RupeeFlowDatabase
    private lateinit var expenseGroupRepo: ExpenseGroupRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var groupActivityRepo: GroupActivityRepository

    private var defaultCategoryId: Long = 1L
    private var friendViyangId: Long = 18L
    private var friendMananId: Long = 19L
    private var friendJayId: Long = 20L
    private var testGroupId: Long = 100L

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
        transactionRepo = TransactionRepositoryImpl(db.transactionDao(), db.categoryDao(), db.friendDao())
        settlementRepo = SettlementRepositoryImpl(db.settlementDao(), db.friendDao())
        groupActivityRepo = GroupActivityRepositoryImpl(db.groupActivityDao())

        defaultCategoryId = db.categoryDao().insertCategory(
            CategoryEntity(name = "General", iconName = "receipt", colorHex = "#3B82F6", isDefault = true)
        )

        friendViyangId = friendRepo.addFriend(Friend(name = "Viyang", phoneNumber = "9876543218"))
        friendMananId = friendRepo.addFriend(Friend(name = "Manan", phoneNumber = "9876543219"))
        friendJayId = friendRepo.addFriend(Friend(name = "Jay", phoneNumber = "9876543220"))

        testGroupId = expenseGroupRepo.createExpenseGroup(
            name = "Goa Trip",
            groupType = GroupType.TRIP,
            friendIds = listOf(friendViyangId, friendMananId, friendJayId)
        )
    }

    @After
    fun tearDown() {
        db.close()
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun test01_editNormalTransaction_preservesIdAndCreatedAt() = runBlocking {
        val original = Transaction(
            title = "Coffee",
            amountPaise = 25000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis() - 50000L,
            paymentMethod = PaymentMethod.UPI,
            createdAt = 1000000L,
            updatedAt = 1000000L
        )
        val insertedId = transactionRepo.saveTransaction(original)
        assertTrue(insertedId > 0L)

        val loaded = transactionRepo.getTransactionById(insertedId)
        assertNotNull(loaded)
        assertEquals("Coffee", loaded!!.title)
        assertEquals(25000L, loaded.amountPaise)
        assertEquals(1000000L, loaded.createdAt)

        val updated = loaded.copy(
            title = "Specialty Coffee",
            amountPaise = 32000L,
            paymentMethod = PaymentMethod.CREDIT_CARD
        )
        val savedId = transactionRepo.saveTransaction(updated)
        assertEquals(insertedId, savedId)

        val reloaded = transactionRepo.getTransactionById(insertedId)
        assertNotNull(reloaded)
        assertEquals("Specialty Coffee", reloaded!!.title)
        assertEquals(32000L, reloaded.amountPaise)
        assertEquals(PaymentMethod.CREDIT_CARD, reloaded.paymentMethod)
        assertEquals(1000000L, reloaded.createdAt)
        assertTrue(reloaded.updatedAt >= 1000000L)

        val allTxns = transactionRepo.getAllTransactionsList()
        assertEquals(1, allTxns.size)
    }

    @Test
    fun test02_editSplitProTransaction_preservesExpenseGroupIdAndParticipants() = runBlocking {
        val participants = listOf(
            TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 30000L),
            TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 30000L)
        )
        val original = Transaction(
            title = "Dinner",
            amountPaise = 60000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            expenseGroupId = testGroupId,
            expenseGroupName = "Goa Trip",
            participants = participants,
            splitMethod = SplitMethod.EQUAL,
            createdAt = 1500000L
        )
        val id = transactionRepo.saveTransaction(original)

        val loaded = transactionRepo.getTransactionById(id)
        assertNotNull(loaded)
        assertEquals(testGroupId, loaded!!.expenseGroupId)
        assertEquals("Goa Trip", loaded.expenseGroupName)
        assertEquals(2, loaded.participants.size)

        val newParticipants = listOf(
            TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 40000L),
            TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 40000L)
        )
        val updated = loaded.copy(
            title = "Luxury Dinner",
            amountPaise = 80000L,
            participants = newParticipants
        )
        val savedId = transactionRepo.saveTransaction(updated)
        assertEquals(id, savedId)

        val reloaded = transactionRepo.getTransactionById(id)
        assertNotNull(reloaded)
        assertEquals("Luxury Dinner", reloaded!!.title)
        assertEquals(80000L, reloaded.amountPaise)
        assertEquals(testGroupId, reloaded.expenseGroupId)
        assertEquals("Goa Trip", reloaded.expenseGroupName)
        assertEquals(2, reloaded.participants.size)
        assertEquals(40000L, reloaded.participants[0].shareAmountPaise)
        assertEquals(40000L, reloaded.participants[1].shareAmountPaise)

        val allTxns = transactionRepo.getAllTransactionsList()
        assertEquals(1, allTxns.size)
    }

    @Test
    fun test03_editTransaction_cancelEdit_doesNotMutateDatabase() = runBlocking {
        val original = Transaction(
            title = "Breakfast",
            amountPaise = 15000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis()
        )
        val id = transactionRepo.saveTransaction(original)

        val inMemoryEdit = original.copy(title = "Canceled Edit", amountPaise = 99999L)
        assertNotNull(inMemoryEdit)

        val fromDb = transactionRepo.getTransactionById(id)
        assertNotNull(fromDb)
        assertEquals("Breakfast", fromDb!!.title)
        assertEquals(15000L, fromDb.amountPaise)
    }

    @Test
    fun test04_addExpenseFromMain_andFromSplitPro_associatesCorrectGroupId() = runBlocking {
        val normalTxn = Transaction(
            title = "Personal Lunch",
            amountPaise = 20000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis()
        )
        val normalId = transactionRepo.saveTransaction(normalTxn)
        val loadedNormal = transactionRepo.getTransactionById(normalId)
        assertNotNull(loadedNormal)
        assertNull(loadedNormal!!.expenseGroupId)

        val groupTxn = Transaction(
            title = "Group Villa Stay",
            amountPaise = 1500000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            expenseGroupId = testGroupId,
            expenseGroupName = "Goa Trip",
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 500000L),
                TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 500000L),
                TransactionParticipant(friendId = friendMananId, friendName = "Manan", shareAmountPaise = 500000L)
            )
        )
        val groupId = transactionRepo.saveTransaction(groupTxn)
        val loadedGroup = transactionRepo.getTransactionById(groupId)
        assertNotNull(loadedGroup)
        assertEquals(testGroupId, loadedGroup!!.expenseGroupId)
        assertEquals("Goa Trip", loadedGroup.expenseGroupName)
        assertEquals(3, loadedGroup.participants.size)
    }

    @Test
    fun test05_receiptAttachmentAndPdfAttachment_preservedOnSaveAndEdit() = runBlocking {
        val txn = Transaction(
            title = "Hardware Bill",
            amountPaise = 50000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            billImagePath = "/storage/emulated/0/RupeeFlow/receipt_123.pdf"
        )
        val id = transactionRepo.saveTransaction(txn)
        val loaded = transactionRepo.getTransactionById(id)
        assertEquals("/storage/emulated/0/RupeeFlow/receipt_123.pdf", loaded?.billImagePath)

        val updated = loaded!!.copy(billImagePath = "/storage/emulated/0/RupeeFlow/receipt_updated.jpg")
        transactionRepo.saveTransaction(updated)
        val reloaded = transactionRepo.getTransactionById(id)
        assertEquals("/storage/emulated/0/RupeeFlow/receipt_updated.jpg", reloaded?.billImagePath)
    }

    @Test
    fun test06_settlementReversal_activeToReversed_conditionalUpdate() = runBlocking {
        val settlement = Settlement(
            payerId = friendViyangId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )
        val id = settlementRepo.recordSettlement(settlement)

        val reversed = settlementRepo.reverseSettlement(id)
        assertTrue(reversed)

        val loaded = settlementRepo.getSettlementById(id)
        assertNotNull(loaded)
        assertTrue(loaded!!.isReversed)
    }

    @Test
    fun test07_settlementReversal_idempotentSecondCallReturnsFalse() = runBlocking {
        val settlement = Settlement(
            payerId = friendViyangId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )
        val id = settlementRepo.recordSettlement(settlement)

        val firstRev = settlementRepo.reverseSettlement(id)
        assertTrue(firstRev)

        val secondRev = settlementRepo.reverseSettlement(id)
        assertFalse(secondRev)

        val loaded = settlementRepo.getSettlementById(id)
        assertTrue(loaded!!.isReversed)
    }

    @Test
    fun test08_settlementRestore_reversedToActive_conditionalUpdate() = runBlocking {
        val settlement = Settlement(
            payerId = friendViyangId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )
        val id = settlementRepo.recordSettlement(settlement)
        settlementRepo.reverseSettlement(id)

        val restored = settlementRepo.unreverseSettlement(id)
        assertTrue(restored)

        val loaded = settlementRepo.getSettlementById(id)
        assertNotNull(loaded)
        assertFalse(loaded!!.isReversed)
    }

    @Test
    fun test09_settlementRestore_idempotentSecondCallReturnsFalse() = runBlocking {
        val settlement = Settlement(
            payerId = friendViyangId,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )
        val id = settlementRepo.recordSettlement(settlement)

        val restoredOnActive = settlementRepo.unreverseSettlement(id)
        assertFalse(restoredOnActive)

        settlementRepo.reverseSettlement(id)
        val firstRestore = settlementRepo.unreverseSettlement(id)
        assertTrue(firstRestore)

        val secondRestore = settlementRepo.unreverseSettlement(id)
        assertFalse(secondRestore)
    }

    @Test
    fun test10_reverseNonExistentSettlement_returnsFalse() = runBlocking {
        val result = settlementRepo.reverseSettlement(999999L)
        assertFalse(result)

        val restoreResult = settlementRepo.unreverseSettlement(999999L)
        assertFalse(restoreResult)
    }

    @Test
    fun test11_groupActivity_recordsSettlementEvents_withTargetId() = runBlocking {
        val settlement = Settlement(
            payerId = friendViyangId,
            receiverId = null,
            amountPaise = 25000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId
        )
        val sId = settlementRepo.recordSettlement(settlement)

        groupActivityRepo.recordActivity(
            groupId = testGroupId,
            activityType = GroupActivityType.SETTLEMENT_RECORDED,
            actorName = "Viyang",
            description = "Viyang paid You",
            amountPaise = 25000L,
            targetId = sId
        )

        settlementRepo.reverseSettlement(sId)
        groupActivityRepo.recordActivity(
            groupId = testGroupId,
            activityType = GroupActivityType.SETTLEMENT_REVERSED,
            actorName = "You",
            description = "Reversed payment of ₹250",
            amountPaise = 25000L,
            targetId = sId
        )

        settlementRepo.unreverseSettlement(sId)
        groupActivityRepo.recordActivity(
            groupId = testGroupId,
            activityType = GroupActivityType.SETTLEMENT_RESTORED,
            actorName = "You",
            description = "Restored payment of ₹250",
            amountPaise = 25000L,
            targetId = sId
        )

        val activities = groupActivityRepo.getActivitiesForGroupList(testGroupId)
        assertEquals(3, activities.size)
        assertEquals(GroupActivityType.SETTLEMENT_RESTORED, activities[0].activityType)
        assertEquals(sId, activities[0].targetId)
        assertEquals(GroupActivityType.SETTLEMENT_REVERSED, activities[1].activityType)
        assertEquals(sId, activities[1].targetId)
        assertEquals(GroupActivityType.SETTLEMENT_RECORDED, activities[2].activityType)
        assertEquals(sId, activities[2].targetId)
    }

    @Test
    fun test12_groupActivity_recordsExpenseEvents_withTargetId() = runBlocking {
        val txn = Transaction(
            title = "Groceries",
            amountPaise = 50000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            expenseGroupId = testGroupId
        )
        val tId = transactionRepo.saveTransaction(txn)
        groupActivityRepo.recordActivity(
            groupId = testGroupId,
            activityType = GroupActivityType.EXPENSE_CREATED,
            actorName = "You",
            description = "Added expense \"Groceries\"",
            amountPaise = 50000L,
            targetId = tId
        )

        groupActivityRepo.recordActivity(
            groupId = testGroupId,
            activityType = GroupActivityType.EXPENSE_EDITED,
            actorName = "You",
            description = "Edited expense \"Groceries\"",
            amountPaise = 60000L,
            targetId = tId
        )

        val activities = groupActivityRepo.getActivitiesForGroupList(testGroupId)
        assertEquals(2, activities.size)
        assertEquals(GroupActivityType.EXPENSE_EDITED, activities[0].activityType)
        assertEquals(tId, activities[0].targetId)
        assertEquals(GroupActivityType.EXPENSE_CREATED, activities[1].activityType)
        assertEquals(tId, activities[1].targetId)
    }

    @Test
    fun test13_scenarioA_mePaid600_threeMembersSplitEqually() = runBlocking {
        val friends = listOf(
            Friend(id = friendViyangId, name = "Viyang"),
            Friend(id = friendMananId, name = "Manan")
        )
        val shares = LedgerCalculator.calculateEqualSplit(60000L, 3)
        assertEquals(listOf(20000L, 20000L, 20000L), shares)

        val txn = Transaction(
            id = 1L,
            title = "Lunch",
            amountPaise = 60000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null,
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 20000L),
                TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 20000L),
                TransactionParticipant(friendId = friendMananId, friendName = "Manan", shareAmountPaise = 20000L)
            )
        )

        val summary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = emptyList(),
            members = friends
        )

        assertEquals(60000L, summary.totalExpensePaise)
        assertEquals(60000L, summary.myPaidPaise)
        assertEquals(20000L, summary.mySharePaise)
        assertEquals(40000L, summary.myNetBalancePaise)

        val viyangBal = summary.memberBalances.first { it.memberId == friendViyangId }
        assertEquals(0L, viyangBal.totalPaidPaise)
        assertEquals(20000L, viyangBal.totalSharePaise)
        assertEquals(-20000L, viyangBal.netBalancePaise)

        val mananBal = summary.memberBalances.first { it.memberId == friendMananId }
        assertEquals(0L, mananBal.totalPaidPaise)
        assertEquals(20000L, mananBal.totalSharePaise)
        assertEquals(-20000L, mananBal.netBalancePaise)
    }

    @Test
    fun test14_scenarioB_friendPays1100_sharedWithMe() = runBlocking {
        val friends = listOf(Friend(id = friendViyangId, name = "Viyang"))
        val shares = LedgerCalculator.calculateEqualSplit(110000L, 2)
        assertEquals(listOf(55000L, 55000L), shares)

        val txn = Transaction(
            id = 1L,
            title = "Activity",
            amountPaise = 110000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            paidByName = "Viyang",
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 55000L),
                TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 55000L)
            )
        )

        val summary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = emptyList(),
            members = friends
        )

        assertEquals(110000L, summary.totalExpensePaise)
        assertEquals(0L, summary.myPaidPaise)
        assertEquals(55000L, summary.mySharePaise)
        assertEquals(-55000L, summary.myNetBalancePaise)

        val viyangBal = summary.memberBalances.first { it.memberId == friendViyangId }
        assertEquals(110000L, viyangBal.totalPaidPaise)
        assertEquals(55000L, viyangBal.totalSharePaise)
        assertEquals(55000L, viyangBal.netBalancePaise)
    }

    @Test
    fun test15_scenarioC_settlement1100_reverseAndRestore() = runBlocking {
        val friends = listOf(Friend(id = friendViyangId, name = "Viyang"))
        val txn = Transaction(
            id = 1L,
            title = "Activity",
            amountPaise = 110000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            paidByName = "Viyang",
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 110000L)
            )
        )

        val activeSettlement = Settlement(
            id = 10L,
            payerId = null,
            receiverId = friendViyangId,
            amountPaise = 110000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )

        // 1. With active settlement: balance settled to 0
        val summary1 = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(activeSettlement),
            members = friends
        )
        assertEquals(0L, summary1.myNetBalancePaise)
        assertEquals(0L, summary1.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)

        // 2. Reverse the settlement: previous debt returns
        val reversedSettlement = activeSettlement.copy(isReversed = true)
        val summary2 = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(reversedSettlement),
            members = friends
        )
        assertEquals(-110000L, summary2.myNetBalancePaise)
        assertEquals(110000L, summary2.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)

        // 3. Restore the settlement: balance becomes zero again
        val restoredSettlement = reversedSettlement.copy(isReversed = false)
        val summary3 = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(restoredSettlement),
            members = friends
        )
        assertEquals(0L, summary3.myNetBalancePaise)
        assertEquals(0L, summary3.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)
    }

    @Test
    fun test16_scenarioD_twoExpenses500And600_oneSettlement1100() = runBlocking {
        val friends = listOf(Friend(id = friendViyangId, name = "Viyang"))
        val txn1 = Transaction(
            id = 1L,
            title = "Snacks",
            amountPaise = 50000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 50000L)
            )
        )
        val txn2 = Transaction(
            id = 2L,
            title = "Fuel",
            amountPaise = 60000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 60000L)
            )
        )

        val settlement = Settlement(
            id = 1L,
            payerId = null,
            receiverId = friendViyangId,
            amountPaise = 110000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )

        val summary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn1, txn2),
            settlements = listOf(settlement),
            members = friends
        )

        assertEquals(0L, summary.myNetBalancePaise)
        assertEquals(0L, summary.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)
        assertTrue(summary.simplifiedDebts.isEmpty())
    }

    @Test
    fun test17_scenarioE_reverseOneOfMultipleSettlements() = runBlocking {
        val friends = listOf(Friend(id = friendViyangId, name = "Viyang"))
        val txn = Transaction(
            id = 1L,
            title = "Hotel",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 100000L)
            )
        )

        val s1 = Settlement(
            id = 1L,
            payerId = null,
            receiverId = friendViyangId,
            amountPaise = 40000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )
        val s2 = Settlement(
            id = 2L,
            payerId = null,
            receiverId = friendViyangId,
            amountPaise = 60000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )

        val allSettledSummary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(s1, s2),
            members = friends
        )
        assertEquals(0L, allSettledSummary.myNetBalancePaise)

        // Reverse s1 only
        val s1Reversed = s1.copy(isReversed = true)
        val partialSummary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(s1Reversed, s2),
            members = friends
        )
        // Debt was 100000, s2 paid 60000, so remaining debt is 40000
        assertEquals(-40000L, partialSummary.myNetBalancePaise)
        assertEquals(40000L, partialSummary.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)
    }

    @Test
    fun test18_scenarioF_editExpenseAfterSettlement() = runBlocking {
        val friends = listOf(Friend(id = friendViyangId, name = "Viyang"))
        val txn = Transaction(
            id = 1L,
            title = "Car Rental",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 50000L),
                TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 50000L)
            )
        )

        val settlement = Settlement(
            id = 1L,
            payerId = null,
            receiverId = friendViyangId,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )

        val initialSummary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(settlement),
            members = friends
        )
        assertEquals(0L, initialSummary.myNetBalancePaise)

        // Edit expense from 100000 to 140000 (70000 each)
        val editedTxn = txn.copy(
            amountPaise = 140000L,
            participants = listOf(
                TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 70000L),
                TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 70000L)
            )
        )

        val editedSummary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(editedTxn),
            settlements = listOf(settlement),
            members = friends
        )
        // Viyang paid 140000 expense - 50000 received settlement = 90000 paid. Share 70000. Net = +20000.
        // Me paid 50000 settlement. Share 70000. Net = -20000.
        assertEquals(-20000L, editedSummary.myNetBalancePaise)
        assertEquals(20000L, editedSummary.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)
    }

    @Test
    fun test19_scenarioG_deleteExpenseWithExistingSettlement() = runBlocking {
        val friends = listOf(Friend(id = friendViyangId, name = "Viyang"))
        val settlement = Settlement(
            id = 1L,
            payerId = null,
            receiverId = friendViyangId,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )

        // If expense is deleted, remaining transactions is empty
        val summary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = emptyList(),
            settlements = listOf(settlement),
            members = friends
        )
        // Me paid 50000 in settlement, consumed 0 share -> Net = +50000
        // Viyang collected 50000 in settlement, consumed 0 share -> Net = -50000
        assertEquals(50000L, summary.myNetBalancePaise)
        assertEquals(-50000L, summary.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)
    }

    @Test
    fun test20_percentageSplit_acceptedAndRoundedZeroPaiseRemainder() = runBlocking {
        val totalPaise = 10000L
        val count = 3
        val shares = LedgerCalculator.calculateEqualSplit(totalPaise, count)
        assertEquals(3, shares.size)
        assertEquals(totalPaise, shares.sum())
        assertEquals(3334L, shares[0])
        assertEquals(3333L, shares[1])
        assertEquals(3333L, shares[2])
    }

    @Test
    fun test21_friendToFriendGroupSettlement_neitherIsMe() = runBlocking {
        val friends = listOf(
            Friend(id = friendViyangId, name = "Viyang"),
            Friend(id = friendMananId, name = "Manan")
        )
        val txn = Transaction(
            id = 1L,
            title = "Supplies",
            amountPaise = 100000L,
            categoryId = defaultCategoryId,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = friendViyangId,
            expenseGroupId = testGroupId,
            participants = listOf(
                TransactionParticipant(friendId = friendViyangId, friendName = "Viyang", shareAmountPaise = 50000L),
                TransactionParticipant(friendId = friendMananId, friendName = "Manan", shareAmountPaise = 50000L)
            )
        )
        val settlement = Settlement(
            id = 1L,
            payerId = friendMananId,
            receiverId = friendViyangId,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            groupId = testGroupId,
            isReversed = false
        )

        val summary = LedgerCalculator.computeGroupBalances(
            groupId = testGroupId,
            transactions = listOf(txn),
            settlements = listOf(settlement),
            members = friends
        )

        assertEquals(0L, summary.myNetBalancePaise)
        assertEquals(0L, summary.memberBalances.first { it.memberId == friendViyangId }.netBalancePaise)
        assertEquals(0L, summary.memberBalances.first { it.memberId == friendMananId }.netBalancePaise)
    }

    @Test
    fun test22_groupActivityType_displayNameAndEnums() {
        assertEquals("Payment restored", GroupActivityType.SETTLEMENT_RESTORED.displayName)
        assertEquals("Payment reversed", GroupActivityType.SETTLEMENT_REVERSED.displayName)
        assertEquals("Payment recorded", GroupActivityType.SETTLEMENT_RECORDED.displayName)
    }
}

