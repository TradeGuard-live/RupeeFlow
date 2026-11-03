package com.naman.rupeeflow.domain

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class TransactionAndSettlementEditTest {

    private val friendA = Friend(id = 1L, name = "Friend A")
    private val friendB = Friend(id = 2L, name = "Friend B")
    private val friendC = Friend(id = 3L, name = "Friend C")
    private val startOfMonth = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // ==========================================
    // PART 1: 15 TRANSACTION EDIT SCENARIOS
    // ==========================================

    @Test
    fun test01_EditAmountOnly_RecalculatesSharesAndBalances() {
        // Initial: Me paid ₹1,000 split equally with Friend A (₹500 each)
        val initialTxn = Transaction(
            id = 10L,
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 50000L, percentage = 50.0),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 50000L, percentage = 50.0)
            ),
            splitMethod = SplitMethod.EQUAL
        )
        val initialLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), emptyList())
        assertEquals(50000L, initialLedger.first().totalTheyOweMePaise)

        // Edit: Amount increased to ₹1,600 (Equal split -> ₹800 each)
        val newShares = LedgerCalculator.calculateEqualSplit(160000L, 2)
        val editedTxn = initialTxn.copy(
            amountPaise = 160000L,
            participants = listOf(
                initialTxn.participants[0].copy(shareAmountPaise = newShares[0]),
                initialTxn.participants[1].copy(shareAmountPaise = newShares[1])
            ),
            updatedAt = System.currentTimeMillis()
        )
        val updatedLedger = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(80000L, updatedLedger.first().totalTheyOweMePaise)
        assertEquals(80000L, updatedLedger.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, updatedLedger.first().status)
    }

    @Test
    fun test02_EditTitleAndDescriptionOnly_FinancialTotalsRemainIdentical() {
        val initialTxn = Transaction(
            id = 10L,
            title = "Old Dinner",
            description = "Old Note",
            amountPaise = 120000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 60000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 60000L)
            )
        )
        val editedTxn = initialTxn.copy(
            title = "New Fancy Dinner",
            description = "Updated detailed notes",
            updatedAt = System.currentTimeMillis()
        )
        val ledgerBefore = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), emptyList())
        val ledgerAfter = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())

        assertEquals(ledgerBefore.first().netBalancePaise, ledgerAfter.first().netBalancePaise)
        assertEquals(ledgerBefore.first().totalTheyOweMePaise, ledgerAfter.first().totalTheyOweMePaise)
    }

    @Test
    fun test03_ChangePayer_FromMeToFriend() {
        // Initially: Me paid ₹1,000, Friend A's share is ₹400 -> Friend A owes me ₹400
        val initialTxn = Transaction(
            id = 10L,
            title = "Lunch",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 60000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 40000L)
            )
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), emptyList())
        assertEquals(40000L, before.first().totalTheyOweMePaise)
        assertEquals(0L, before.first().totalIOweThemPaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, before.first().status)

        // Edit: Friend A paid total ₹1,000 -> My share is ₹600 -> I owe Friend A ₹600
        val editedTxn = initialTxn.copy(
            paidByFriendId = 1L,
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(0L, after.first().totalTheyOweMePaise)
        assertEquals(60000L, after.first().totalIOweThemPaise)
        assertEquals(-60000L, after.first().netBalancePaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, after.first().status)
    }

    @Test
    fun test04_ChangePayer_FromFriendToMe() {
        // Initially: Friend A paid ₹1,000, My share is ₹500 -> I owe Friend A ₹500
        val initialTxn = Transaction(
            id = 10L,
            title = "Groceries",
            amountPaise = 100000L,
            paidByFriendId = 1L,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 50000L)
            )
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), emptyList())
        assertEquals(50000L, before.first().totalIOweThemPaise)

        // Edit: Payer changed to Me (paidByFriendId = null) -> Friend A owes Me ₹500
        val editedTxn = initialTxn.copy(
            paidByFriendId = null,
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(50000L, after.first().totalTheyOweMePaise)
        assertEquals(0L, after.first().totalIOweThemPaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, after.first().status)
    }

    @Test
    fun test05_ChangePayer_FromFriendAToFriendB() {
        // Initially: Friend A paid ₹900, split 3 ways (Me=300, FriendA=300, FriendB=300)
        val initialTxn = Transaction(
            id = 10L,
            title = "Movie",
            amountPaise = 90000L,
            paidByFriendId = 1L, // Friend A paid
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 30000L),
                TransactionParticipant(id = 3, transactionId = 10, friendId = 2L, shareAmountPaise = 30000L)
            )
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(initialTxn), emptyList())
        val friendABefore = before.first { it.id == 1L }
        val friendBBefore = before.first { it.id == 2L }
        assertEquals(30000L, friendABefore.totalIOweThemPaise)
        assertEquals(0L, friendBBefore.totalIOweThemPaise)

        // Edit: Friend B paid (paidByFriendId = 2L) -> I owe Friend B ₹300, Friend A balance with Me is 0
        val editedTxn = initialTxn.copy(
            paidByFriendId = 2L,
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(editedTxn), emptyList())
        val friendAAfter = after.first { it.id == 1L }
        val friendBAfter = after.first { it.id == 2L }
        assertEquals(0L, friendAAfter.totalIOweThemPaise)
        assertEquals(30000L, friendBAfter.totalIOweThemPaise)
    }

    @Test
    fun test06_AddNewParticipantToExistingExpense() {
        // Me paid ₹1,200 split with Friend A (600 each)
        val initialTxn = Transaction(
            id = 10L,
            title = "Cafe",
            amountPaise = 120000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 60000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 60000L)
            )
        )
        // Add Friend B -> Now 3-way split ₹400 each
        val shares = LedgerCalculator.calculateEqualSplit(120000L, 3)
        val editedTxn = initialTxn.copy(
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = shares[0]),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = shares[1]),
                TransactionParticipant(id = 3, transactionId = 10, friendId = 2L, shareAmountPaise = shares[2])
            ),
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(editedTxn), emptyList())
        assertEquals(40000L, after.first { it.id == 1L }.totalTheyOweMePaise)
        assertEquals(40000L, after.first { it.id == 2L }.totalTheyOweMePaise)
    }

    @Test
    fun test07_RemoveParticipantFromSharedExpense() {
        // Initial: 3 participants (Me, Friend A, Friend B) ₹400 each
        val initialTxn = Transaction(
            id = 10L,
            title = "Cafe",
            amountPaise = 120000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 40000L),
                TransactionParticipant(id = 3, transactionId = 10, friendId = 2L, shareAmountPaise = 40000L)
            )
        )
        // Remove Friend B -> Now 2-way split (Me + Friend A) ₹600 each
        val shares = LedgerCalculator.calculateEqualSplit(120000L, 2)
        val editedTxn = initialTxn.copy(
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = shares[0]),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = shares[1])
            ),
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), listOf(editedTxn), emptyList())
        assertEquals(60000L, after.first { it.id == 1L }.totalTheyOweMePaise)
        assertEquals(0L, after.first { it.id == 2L }.totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.SETTLED, after.first { it.id == 2L }.status)
    }

    @Test
    fun test08_SwitchSplitMethod_Equal_To_Percentage() {
        // Initial: ₹1,000 split equally 50-50 (₹500 each)
        val initialTxn = Transaction(
            id = 10L,
            title = "Dinner",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 50000L, splitMethod = SplitMethod.EQUAL),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 50000L, splitMethod = SplitMethod.EQUAL)
            ),
            splitMethod = SplitMethod.EQUAL
        )
        // Switch to percentage: Me 30%, Friend A 70%
        val shares = LedgerCalculator.calculatePercentageSplit(100000L, listOf(30.0, 70.0))
        val editedTxn = initialTxn.copy(
            splitMethod = SplitMethod.PERCENTAGE,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = shares[0], percentage = 30.0, splitMethod = SplitMethod.PERCENTAGE),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = shares[1], percentage = 70.0, splitMethod = SplitMethod.PERCENTAGE)
            ),
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(70000L, after.first().totalTheyOweMePaise)
    }

    @Test
    fun test09_SwitchSplitMethod_Percentage_To_CustomAmount() {
        // Initial: ₹1,000 split 40% (₹400) / 60% (₹600)
        val initialTxn = Transaction(
            id = 10L,
            title = "Shopping",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 40000L, percentage = 40.0),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 60000L, percentage = 60.0)
            ),
            splitMethod = SplitMethod.PERCENTAGE
        )
        // Switch to exact: Me ₹250, Friend A ₹750
        val editedTxn = initialTxn.copy(
            splitMethod = SplitMethod.EXACT_AMOUNT,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 25000L, splitMethod = SplitMethod.EXACT_AMOUNT),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 75000L, splitMethod = SplitMethod.EXACT_AMOUNT)
            ),
            updatedAt = System.currentTimeMillis()
        )
        assertTrue(LedgerCalculator.validateParticipantShares(editedTxn.amountPaise, editedTxn.participants))
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(75000L, after.first().totalTheyOweMePaise)
    }

    @Test
    fun test10_ExcludeMeFromSharedExpense_PersonalShareBecomesZero() {
        // Me paid ₹1,000. Initially: Me=500, FriendA=500
        val initialTxn = Transaction(
            id = 10L,
            title = "Birthday Gift",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 50000L)
            )
        )
        assertEquals(50000L, initialTxn.userExpensePaise)

        // Edit: Exclude Me from the expense -> Friend A owes full ₹1,000 (I was just the payer)
        val editedTxn = initialTxn.copy(
            participants = listOf(
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 100000L)
            ),
            updatedAt = System.currentTimeMillis()
        )
        // User's personal counted expense is now 0!
        assertEquals(0L, editedTxn.userExpensePaise)

        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(100000L, after.first().totalTheyOweMePaise)

        // Summary check: Expense for user must be 0
        val summary = LedgerCalculator.computeFinancialSummary(listOf(editedTxn), after, 0, startOfMonth)
        assertEquals(0L, summary.totalExpenseThisMonthPaise)
    }

    @Test
    fun test11_IncludeMeInExistingFriendOnlySharedExpense() {
        // Me paid ₹1,000. Initially: Friend A owes full ₹1,000
        val initialTxn = Transaction(
            id = 10L,
            title = "Snacks",
            amountPaise = 100000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 100000L)
            )
        )
        assertEquals(0L, initialTxn.userExpensePaise)

        // Edit: Include Me as participant (50-50)
        val editedTxn = initialTxn.copy(
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 50000L)
            ),
            updatedAt = System.currentTimeMillis()
        )
        assertEquals(50000L, editedTxn.userExpensePaise)
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        assertEquals(50000L, after.first().totalTheyOweMePaise)
    }

    @Test
    fun test12_EditTransactionDate_AcrossMonthBoundary() {
        val now = System.currentTimeMillis()
        // Transaction dated in the current month
        val initialTxn = Transaction(
            id = 10L,
            title = "Groceries",
            amountPaise = 50000L,
            transactionDate = now,
            transactionType = TransactionType.EXPENSE
        )
        val summaryCurrentMonth = LedgerCalculator.computeFinancialSummary(listOf(initialTxn), emptyList(), 0, startOfMonth, now)
        assertEquals(50000L, summaryCurrentMonth.totalExpenseThisMonthPaise)

        // Move transaction to 60 days ago (prior month)
        val oldDate = now - 60L * 24 * 3600 * 1000
        val editedTxn = initialTxn.copy(transactionDate = oldDate)
        val summaryAfter = LedgerCalculator.computeFinancialSummary(listOf(editedTxn), emptyList(), 0, startOfMonth, now)
        // Should no longer appear in this month's summary!
        assertEquals(0L, summaryAfter.totalExpenseThisMonthPaise)
    }

    @Test
    fun test13_EditCategory_UpdatesCategoryAssociation() {
        val catFood = Category(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#FF0000")
        val catShopping = Category(id = 2L, name = "Shopping", iconName = "shopping_cart", colorHex = "#00FF00")

        val initialTxn = Transaction(
            id = 10L,
            title = "T-Shirt",
            amountPaise = 80000L,
            categoryId = 1L,
            category = catFood
        )
        assertEquals(1L, initialTxn.categoryId)

        val editedTxn = initialTxn.copy(
            categoryId = 2L,
            category = catShopping,
            updatedAt = System.currentTimeMillis()
        )
        assertEquals(2L, editedTxn.categoryId)
        assertEquals("Shopping", editedTxn.category?.name)
    }

    @Test
    fun test14_EditExpenseIntoRefund_ReversesLedgerEffect() {
        // Initial: Me paid ₹1,000 for Expense, Friend A share ₹500 -> Friend A owes me ₹500
        val initialTxn = Transaction(
            id = 10L,
            title = "Flight Ticket",
            amountPaise = 100000L,
            paidByFriendId = null,
            transactionType = TransactionType.EXPENSE,
            participants = listOf(
                TransactionParticipant(id = 1, transactionId = 10, friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(id = 2, transactionId = 10, friendId = 1L, shareAmountPaise = 50000L)
            )
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(initialTxn), emptyList())
        assertEquals(50000L, before.first().totalTheyOweMePaise)

        // Edit: Flight cancelled, converted to REFUND of shared expense
        val editedTxn = initialTxn.copy(
            transactionType = TransactionType.REFUND,
            updatedAt = System.currentTimeMillis()
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(editedTxn), emptyList())
        // Since it's a refund received by Me, Friend A's debt is reduced by ₹500 -> Net is -₹500 (I owe Friend A ₹500)
        assertEquals(0L, after.first().totalTheyOweMePaise)
        assertEquals(50000L, after.first().totalIOweThemPaise)
        assertEquals(FriendBalanceStatus.I_OWE_THEM, after.first().status)
    }

    @Test
    fun test15_EditPaymentMethod_SwitchesCashAndOnlineSpending() {
        val now = System.currentTimeMillis()
        val initialTxn = Transaction(
            id = 10L,
            title = "Dinner",
            amountPaise = 50000L,
            paymentMethod = PaymentMethod.CASH,
            transactionDate = now
        )
        val summary1 = LedgerCalculator.computeFinancialSummary(listOf(initialTxn), emptyList(), 0, startOfMonth, now)
        assertEquals(50000L, summary1.totalCashSpendingPaise)
        assertEquals(0L, summary1.totalOnlineSpendingPaise)

        val editedTxn = initialTxn.copy(paymentMethod = PaymentMethod.UPI)
        val summary2 = LedgerCalculator.computeFinancialSummary(listOf(editedTxn), emptyList(), 0, startOfMonth, now)
        assertEquals(0L, summary2.totalCashSpendingPaise)
        assertEquals(50000L, summary2.totalOnlineSpendingPaise)
    }

    // ==========================================
    // PART 2: 11 SETTLEMENT EDIT SCENARIOS
    // ==========================================

    @Test
    fun test16_IncreaseSettlementAmount_ReducesDebtFurther() {
        // Friend A owes Me ₹1,000 from an expense
        val expense = Transaction(
            id = 1L,
            title = "Rent",
            amountPaise = 200000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 100000L)
            )
        )
        // Initial settlement: Friend A paid Me ₹400 -> remaining debt ₹600
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 40000L,
            settlementDate = System.currentTimeMillis()
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(expense), listOf(initialSettlement))
        assertEquals(60000L, before.first().totalTheyOweMePaise)

        // Edit settlement: Amount increased to ₹700 -> remaining debt ₹300
        val editedSettlement = initialSettlement.copy(amountPaise = 70000L)
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(expense), listOf(editedSettlement))
        assertEquals(30000L, after.first().totalTheyOweMePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, after.first().status)
    }

    @Test
    fun test17_DecreaseSettlementAmount_RestoresDebt() {
        val expense = Transaction(
            id = 1L,
            title = "Rent",
            amountPaise = 200000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 100000L)
            )
        )
        // Initial settlement: Friend A paid Me ₹800 -> remaining debt ₹200
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 80000L,
            settlementDate = System.currentTimeMillis()
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(expense), listOf(initialSettlement))
        assertEquals(20000L, before.first().totalTheyOweMePaise)

        // Edit settlement: Amount corrected down to ₹300 -> remaining debt ₹700
        val editedSettlement = initialSettlement.copy(amountPaise = 30000L)
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), listOf(expense), listOf(editedSettlement))
        assertEquals(70000L, after.first().totalTheyOweMePaise)
    }

    @Test
    fun test18_ChangeSettlementDirection_FriendPaidMeToMePaidFriend() {
        // Friend A paid Me ₹500 (payerId=1, receiverId=null)
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis()
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), emptyList(), listOf(initialSettlement))
        // Since Friend A paid Me ₹500 without prior expense, I owe Friend A ₹500 (overpayment)
        assertEquals(50000L, before.first().totalIOweThemPaise)

        // Edit direction: Actually I paid Friend A ₹500 (payerId=null, receiverId=1)
        val editedSettlement = initialSettlement.copy(
            payerId = null,
            receiverId = 1L
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), emptyList(), listOf(editedSettlement))
        // Now Friend A owes Me ₹500!
        assertEquals(50000L, after.first().totalTheyOweMePaise)
        assertEquals(0L, after.first().totalIOweThemPaise)
    }

    @Test
    fun test19_ChangeSettlementPartner_FriendAToFriendB() {
        // Initial: Settlement assigned to Friend A
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis()
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), emptyList(), listOf(initialSettlement))
        assertEquals(50000L, before.first { it.id == 1L }.totalIOweThemPaise)
        assertEquals(0L, before.first { it.id == 2L }.totalIOweThemPaise)

        // Edit: Reassign settlement to Friend B
        val editedSettlement = initialSettlement.copy(
            payerId = 2L,
            receiverId = null
        )
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA, friendB), emptyList(), listOf(editedSettlement))
        assertEquals(0L, after.first { it.id == 1L }.totalIOweThemPaise)
        assertEquals(50000L, after.first { it.id == 2L }.totalIOweThemPaise)
    }

    @Test
    fun test20_EditPaymentMethodOfSettlement() {
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.CASH
        )
        val editedSettlement = initialSettlement.copy(paymentMethod = PaymentMethod.UPI)
        assertEquals(PaymentMethod.UPI, editedSettlement.paymentMethod)
        assertEquals(initialSettlement.id, editedSettlement.id)
    }

    @Test
    fun test21_EditSettlementDate() {
        val initialDate = 1000L
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = initialDate
        )
        val newDate = 5000L
        val editedSettlement = initialSettlement.copy(settlementDate = newDate)
        assertEquals(newDate, editedSettlement.settlementDate)
        assertEquals(5L, editedSettlement.id)
    }

    @Test
    fun test22_EditNotesAndReferenceNumber() {
        val initialSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            referenceNumber = null,
            notes = "Initial note"
        )
        val editedSettlement = initialSettlement.copy(
            referenceNumber = "UPI987654321",
            notes = "Settled via Google Pay"
        )
        assertEquals("UPI987654321", editedSettlement.referenceNumber)
        assertEquals("Settled via Google Pay", editedSettlement.notes)
    }

    @Test
    fun test23_EditReversedSettlement_PreservesReversalStatus() {
        // Reversed settlement has isReversed = true and does NOT impact ledger
        val reversedSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis(),
            isReversed = true
        )
        val before = LedgerCalculator.computeFriendLedgers(listOf(friendA), emptyList(), listOf(reversedSettlement))
        assertEquals(0L, before.first().totalIOweThemPaise)
        assertEquals(0L, before.first().totalTheyOweMePaise)

        // Editing notes/amount while reversed must keep isReversed = true
        val edited = reversedSettlement.copy(
            amountPaise = 60000L,
            notes = "Updated note"
        )
        assertTrue(edited.isReversed)
        val after = LedgerCalculator.computeFriendLedgers(listOf(friendA), emptyList(), listOf(edited))
        assertEquals(0L, after.first().totalIOweThemPaise)
        assertEquals(0L, after.first().totalTheyOweMePaise)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test24_AttemptInvalidSettlement_AmountZeroOrNegative() {
        val invalidSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 0L,
            settlementDate = System.currentTimeMillis()
        )
        require(invalidSettlement.amountPaise > 0L) { "Amount must be greater than zero" }
    }

    @Test(expected = IllegalArgumentException::class)
    fun test25_AttemptInvalidSettlement_PayerEqualsReceiver() {
        val invalidSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = 1L,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis()
        )
        require(invalidSettlement.payerId != invalidSettlement.receiverId) { "Payer and receiver cannot be the same entity" }
    }

    @Test(expected = IllegalArgumentException::class)
    fun test26_AttemptInvalidSettlement_NeitherSideIsMe() {
        // Payer = Friend 1, Receiver = Friend 2 (neither is Me/null)
        val invalidSettlement = Settlement(
            id = 5L,
            payerId = 1L,
            receiverId = 2L,
            amountPaise = 50000L,
            settlementDate = System.currentTimeMillis()
        )
        val hasMe = (invalidSettlement.payerId == null) != (invalidSettlement.receiverId == null)
        require(hasMe) { "Exactly one side of a settlement must be Me" }
    }
}

