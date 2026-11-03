package com.naman.rupeeflow.domain

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.CurrencyUtils
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import org.junit.Assert.*
import org.junit.Test

class CurrencyAndLedgerTest {

    @Test
    fun testCurrencyFormattingAndParsing() {
        // ₹0
        assertEquals("₹0.00", CurrencyUtils.formatPaiseToRupees(0L))
        assertEquals(0L, CurrencyUtils.parseInputToPaise("0"))

        // ₹0.01 (1 paise)
        assertEquals("₹0.01", CurrencyUtils.formatPaiseToRupees(1L))
        assertEquals(1L, CurrencyUtils.parseInputToPaise("0.01"))

        // Regular amount ₹850.50
        assertEquals("₹850.50", CurrencyUtils.formatPaiseToRupees(85050L))
        assertEquals(85050L, CurrencyUtils.parseInputToPaise("850.50"))

        // Large amount ₹1,25,00,000.00 (1 crore 25 lakh)
        assertEquals("₹1,25,00,000.00", CurrencyUtils.formatPaiseToRupees(1250000000L))
        assertEquals(1250000000L, CurrencyUtils.parseInputToPaise("12500000"))

        // Compact formatting
        assertEquals("₹1.2k", CurrencyUtils.formatPaiseCompact(120000L))
        assertEquals("₹1.5L", CurrencyUtils.formatPaiseCompact(15000000L))
    }

    @Test
    fun testEqualSplit_NoRoundingLoss() {
        // Splitting ₹100.00 among 3 participants -> 33.34, 33.33, 33.33
        val shares100 = LedgerCalculator.calculateEqualSplit(10000L, 3)
        assertEquals(3, shares100.size)
        assertEquals(10000L, shares100.sum())
        assertEquals(3334L, shares100[0])
        assertEquals(3333L, shares100[1])
        assertEquals(3333L, shares100[2])

        // Splitting 1 paise among 3 people -> 1 paise, 0 paise, 0 paise (strictly sum = 1)
        val shares1 = LedgerCalculator.calculateEqualSplit(1L, 3)
        assertEquals(1L, shares1.sum())
    }

    @Test
    fun testPercentageSplit_NoRoundingLoss() {
        // ₹1000 split 33.33%, 33.33%, 33.34%
        val shares = LedgerCalculator.calculatePercentageSplit(100000L, listOf(33.33, 33.33, 33.34))
        assertEquals(3, shares.size)
        assertEquals(100000L, shares.sum())
    }

    @Test
    fun testFriendDebt_WhenMePaid() {
        // Me paid ₹1200 for Dinner with Ronit & Manali (Equal 3-way split: ₹400 each)
        val friendRonit = Friend(id = 1L, name = "Ronit")
        val friendManali = Friend(id = 2L, name = "Manali")

        val txn = Transaction(
            id = 101L,
            title = "Dinner",
            amountPaise = 120000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null, // ME
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 2L, shareAmountPaise = 40000L)
            ),
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        val computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit, friendManali),
            listOf(txn),
            emptyList()
        )

        val ronitResult = computed.first { it.id == 1L }
        val manaliResult = computed.first { it.id == 2L }

        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ronitResult.status)
        assertEquals(40000L, ronitResult.totalTheyOweMePaise)
        assertEquals(0L, ronitResult.totalIOweThemPaise)
        assertEquals(40000L, ronitResult.netBalancePaise)

        assertEquals(FriendBalanceStatus.THEY_OWE_ME, manaliResult.status)
        assertEquals(40000L, manaliResult.totalTheyOweMePaise)
    }

    @Test
    fun testFriendCredit_WhenFriendPaid() {
        // Friend Ronit paid ₹600 for Movie tickets (2-way split: Ronit ₹300, Me ₹300)
        val friendRonit = Friend(id = 1L, name = "Ronit")

        val txn = Transaction(
            id = 102L,
            title = "Movie",
            amountPaise = 60000L,
            categoryId = 2L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = 1L, // Ronit
            participants = listOf(
                TransactionParticipant(friendId = 1L, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L)
            ),
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        val computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit),
            listOf(txn),
            emptyList()
        )

        val ronitResult = computed.first { it.id == 1L }
        assertEquals(FriendBalanceStatus.I_OWE_THEM, ronitResult.status)
        assertEquals(30000L, ronitResult.totalIOweThemPaise)
        assertEquals(0L, ronitResult.totalTheyOweMePaise)
        assertEquals(-30000L, ronitResult.netBalancePaise)
    }

    @Test
    fun testPartialAndFullSettlements() {
        // Ronit owes me ₹1000
        val friendRonit = Friend(id = 1L, name = "Ronit")
        val txn = Transaction(
            id = 103L,
            title = "Trip Expense",
            amountPaise = 200000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 100000L)
            )
        )

        // Partial settlement: Ronit pays ₹400
        val settlement1 = Settlement(
            id = 1L,
            payerId = 1L, // Ronit
            receiverId = null, // ME
            amountPaise = 40000L,
            settlementDate = 2000L
        )

        var computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit),
            listOf(txn),
            listOf(settlement1)
        )
        var ronitResult = computed.first { it.id == 1L }
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ronitResult.status)
        assertEquals(60000L, ronitResult.netBalancePaise) // Remaining ₹600

        // Full settlement: Ronit pays remaining ₹600
        val settlement2 = Settlement(
            id = 2L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 60000L,
            settlementDate = 3000L
        )

        computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit),
            listOf(txn),
            listOf(settlement1, settlement2)
        )
        ronitResult = computed.first { it.id == 1L }
        assertEquals(FriendBalanceStatus.SETTLED, ronitResult.status)
        assertEquals(0L, ronitResult.netBalancePaise)
        assertEquals(0L, ronitResult.totalTheyOweMePaise)
    }

    @Test
    fun testSettlementReversal_RestoresDebt() {
        val friendRonit = Friend(id = 1L, name = "Ronit")
        val txn = Transaction(
            id = 104L,
            title = "Lunch",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            )
        )

        // Erroneous settlement of ₹500 that gets reversed
        val settlement = Settlement(
            id = 1L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            isReversed = true // REVERSED!
        )

        val computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit),
            listOf(txn),
            listOf(settlement)
        )
        val ronitResult = computed.first { it.id == 1L }
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ronitResult.status)
        assertEquals(50000L, ronitResult.netBalancePaise) // Debt fully restored!
    }

    @Test
    fun testIgnoredTransaction_ExcludedFromLedger() {
        val friendRonit = Friend(id = 1L, name = "Ronit")
        val txn = Transaction(
            id = 105L,
            title = "Ignored Shared Expense",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            ),
            transactionStatus = TransactionStatus.IGNORED // IGNORED!
        )

        val computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit),
            listOf(txn),
            emptyList()
        )
        val ronitResult = computed.first { it.id == 1L }
        assertEquals(FriendBalanceStatus.SETTLED, ronitResult.status)
        assertEquals(0L, ronitResult.netBalancePaise)

        // When restored to ACTIVE
        val restoredTxn = txn.copy(transactionStatus = TransactionStatus.ACTIVE)
        val computedRestored = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit),
            listOf(restoredTxn),
            emptyList()
        )
        val ronitRestored = computedRestored.first { it.id == 1L }
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ronitRestored.status)
        assertEquals(50000L, ronitRestored.netBalancePaise)
    }

    @Test
    fun testUserExpensePaise_FriendsShareExcludedFromSpendings() {
        // Scenario 1: User pays ₹1200 split 3 ways with 2 friends.
        // User's personal share is ₹400. Friends owe ₹800.
        // Only ₹400 must be counted in spending, NOT ₹1200!
        val sharedTxn = Transaction(
            id = 1L,
            title = "Team Lunch",
            amountPaise = 120000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null, // User paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 10L, shareAmountPaise = 40000L),
                TransactionParticipant(friendId = 20L, shareAmountPaise = 40000L)
            ),
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        assertEquals(40000L, sharedTxn.userExpensePaise)

        // Scenario 2: Friend pays ₹600 for movie tickets, user's share is ₹300.
        val friendPaidTxn = Transaction(
            id = 2L,
            title = "Movie Tickets",
            amountPaise = 60000L,
            categoryId = 2L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = 10L, // Friend paid
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 10L, shareAmountPaise = 30000L)
            ),
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        assertEquals(30000L, friendPaidTxn.userExpensePaise)

        // Scenario 3: User pays ₹500 completely on behalf of a friend (User is not a participant)
        val paidForFriendTxn = Transaction(
            id = 3L,
            title = "Friend Recharge",
            amountPaise = 50000L,
            categoryId = 3L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 10L, shareAmountPaise = 50000L)
            ),
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        assertEquals(0L, paidForFriendTxn.userExpensePaise)

        // Scenario 4: Normal un-split personal expense
        val soloTxn = Transaction(
            id = 4L,
            title = "Personal Coffee",
            amountPaise = 25000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null,
            participants = emptyList(),
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )
        assertEquals(25000L, soloTxn.userExpensePaise)
    }

    @Test
    fun testFinancialSummary_OnlyCountsUserShare() {
        val now = System.currentTimeMillis()
        val startOfMonth = now - 1000L

        // ₹1000 total bill, user's share = ₹300, friends' share = ₹700
        val sharedTxn = Transaction(
            id = 1L,
            title = "Group Dinner",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = now,
            paymentMethod = PaymentMethod.UPI,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 30000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 70000L)
            ),
            transactionType = TransactionType.EXPENSE
        )

        val summary = LedgerCalculator.computeFinancialSummary(
            transactions = listOf(sharedTxn),
            friends = emptyList(),
            pendingCount = 0,
            startOfMonth = startOfMonth,
            now = now
        )

        // Only the user's ₹300 (30000 paise) should be counted in spending, NOT ₹1000!
        assertEquals(30000L, summary.totalExpenseThisMonthPaise)
        assertEquals(30000L, summary.totalOnlineSpendingPaise)
        assertEquals(0L, summary.totalCashSpendingPaise)
    }

    @Test
    fun testSplit_WhenMeIsExcludedFromExpense() {
        val friendRonit = Friend(id = 1L, name = "Ronit")
        val friendManali = Friend(id = 2L, name = "Manali")

        // Me paid ₹1000 total, but Me didn't eat/participate.
        // Equal split between Ronit & Manali (₹500 each).
        val txn = Transaction(
            id = 201L,
            title = "Lunch for Friends",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null, // Me paid
            participants = listOf(
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 2L, shareAmountPaise = 50000L)
            ),
            splitMethod = SplitMethod.EQUAL,
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        // User personal expense must be 0!
        assertEquals(0L, txn.userExpensePaise)

        // Ledger: Both friends owe Me ₹500 each
        val computed = LedgerCalculator.computeFriendLedgers(
            listOf(friendRonit, friendManali),
            listOf(txn),
            emptyList()
        )

        val ronitResult = computed.first { it.id == 1L }
        val manaliResult = computed.first { it.id == 2L }

        assertEquals(FriendBalanceStatus.THEY_OWE_ME, ronitResult.status)
        assertEquals(50000L, ronitResult.totalTheyOweMePaise)
        assertEquals(50000L, manaliResult.totalTheyOweMePaise)
    }

    @Test
    fun testSettlementReversalAndUnreversal() {
        val friendRonit = Friend(id = 1L, name = "Ronit")

        // Ronit owes Me ₹500
        val txn = Transaction(
            id = 301L,
            title = "Coffee",
            amountPaise = 50000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            ),
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        // 1. Initial state: owes 500
        val s1 = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(txn), emptyList()).first()
        assertEquals(50000L, s1.netBalancePaise)

        // 2. Active settlement of 500: fully settled (0 balance)
        val activeSettlement = Settlement(
            id = 1L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 50000L,
            settlementDate = 2000L,
            isReversed = false
        )
        val s2 = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(txn), listOf(activeSettlement)).first()
        assertEquals(0L, s2.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, s2.status)

        // 3. Reversed settlement: back to owing 500
        val reversedSettlement = activeSettlement.copy(isReversed = true)
        val s3 = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(txn), listOf(reversedSettlement)).first()
        assertEquals(50000L, s3.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, s3.status)

        // 4. Un-reversed (restored to normal): back to 0
        val unreversedSettlement = reversedSettlement.copy(isReversed = false)
        val s4 = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(txn), listOf(unreversedSettlement)).first()
        assertEquals(0L, s4.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, s4.status)
    }

    @Test
    fun testRefundOfSharedTransaction() {
        val friendRonit = Friend(id = 1L, name = "Ronit")

        // 1. Me paid ₹1000 for a concert ticket split 50-50 with Ronit (Ronit owes ₹500)
        val initialTxn = Transaction(
            id = 401L,
            title = "Concert Ticket",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            ),
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        var computed = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(initialTxn), emptyList()).first()
        assertEquals(50000L, computed.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, computed.status)

        // 2. Concert canceled! Merchant refunds the ₹1000 to Me.
        val refundTxn = Transaction(
            id = 402L,
            title = "Concert Refund",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = 2000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            ),
            transactionType = TransactionType.REFUND,
            transactionStatus = TransactionStatus.ACTIVE
        )

        computed = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(initialTxn, refundTxn), emptyList()).first()
        // Because Me received the refund, Ronit's debt is cancelled (balance 0)
        assertEquals(0L, computed.netBalancePaise)
        assertEquals(FriendBalanceStatus.SETTLED, computed.status)
    }

    @Test
    fun testArchivedFriendWithHistoricalTransactions() {
        // Friend is archived, but has historical transactions
        val archivedFriend = Friend(id = 10L, name = "Former Roommate", isArchived = true)

        val txn = Transaction(
            id = 501L,
            title = "Old Electricity Bill",
            amountPaise = 200000L,
            categoryId = 1L,
            transactionDate = 500L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 100000L),
                TransactionParticipant(friendId = 10L, shareAmountPaise = 100000L)
            ),
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        val computed = LedgerCalculator.computeFriendLedgers(listOf(archivedFriend), listOf(txn), emptyList()).first()
        // Archived friend MUST retain correct balance and status!
        assertTrue(computed.isArchived)
        assertEquals(100000L, computed.netBalancePaise)
        assertEquals(FriendBalanceStatus.THEY_OWE_ME, computed.status)
    }

    @Test
    fun testOverSettlement_ProducesNegativeBalanceAccurately() {
        val friendRonit = Friend(id = 1L, name = "Ronit")

        // Ronit owes Me ₹500
        val txn = Transaction(
            id = 601L,
            title = "Lunch",
            amountPaise = 100000L,
            categoryId = 1L,
            transactionDate = 1000L,
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(friendId = null, shareAmountPaise = 50000L),
                TransactionParticipant(friendId = 1L, shareAmountPaise = 50000L)
            ),
            transactionType = TransactionType.EXPENSE,
            transactionStatus = TransactionStatus.ACTIVE
        )

        // Ronit settles ₹700 (overpaying by ₹200)
        val overSettlement = Settlement(
            id = 1L,
            payerId = 1L,
            receiverId = null,
            amountPaise = 70000L,
            settlementDate = 2000L
        )

        val computed = LedgerCalculator.computeFriendLedgers(listOf(friendRonit), listOf(txn), listOf(overSettlement)).first()
        // Now I owe Ronit ₹200
        assertEquals(FriendBalanceStatus.I_OWE_THEM, computed.status)
        assertEquals(20000L, computed.totalIOweThemPaise)
        assertEquals(0L, computed.totalTheyOweMePaise)
        assertEquals(-20000L, computed.netBalancePaise)
    }

    @Test
    fun testMonetaryPrecisionAndInputValidation() {
        // Strict input validation
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("-50.00"))
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("12.345")) // 3 decimal places rejected
        assertNull(CurrencyUtils.parseInputToPaiseOrNull("abc"))
        assertEquals(123450L, CurrencyUtils.parseInputToPaiseOrNull("1234.50"))
        assertEquals(123400L, CurrencyUtils.parseInputToPaiseOrNull("1234"))
        assertEquals(5L, CurrencyUtils.parseInputToPaiseOrNull("0.05"))

        // Exact decimal parsing without floating point errors
        // 0.29 * 100 in double is 28.999999999999996 -> must be exactly 29 paise
        assertEquals(29L, CurrencyUtils.parseInputToPaiseOrNull("0.29"))

        // Percentages validation
        assertTrue(LedgerCalculator.validatePercentages(listOf(50.0, 50.0)))
        assertTrue(LedgerCalculator.validatePercentages(listOf(33.33, 33.33, 33.34)))
        assertFalse(LedgerCalculator.validatePercentages(listOf(50.0, 49.0))) // Sum 99% -> reject!
        assertFalse(LedgerCalculator.validatePercentages(listOf(50.0, -10.0, 60.0))) // Negative % -> reject!

        try {
            LedgerCalculator.calculatePercentageSplit(10000L, listOf(50.0, 40.0))
            fail("Expected IllegalArgumentException for percentages not summing to 100%")
        } catch (_: IllegalArgumentException) {
            // Success
        }
    }

    @Test
    fun testSafeMath_OverflowProtectionAndMinMaxHandling() {
        // SafeMath.safeAbs handling Long.MIN_VALUE
        // Math.abs(Long.MIN_VALUE) returns Long.MIN_VALUE (negative!), but SafeMath returns Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, SafeMath.safeAbs(Long.MIN_VALUE))
        assertEquals(500L, SafeMath.safeAbs(-500L))
        assertEquals(500L, SafeMath.safeAbs(500L))

        // SafeMath.safeNegate
        assertEquals(Long.MAX_VALUE, SafeMath.safeNegate(Long.MIN_VALUE))
        assertEquals(-100L, SafeMath.safeNegate(100L))
        assertEquals(100L, SafeMath.safeNegate(-100L))

        // SafeMath.safeAdd overflow clamping
        assertEquals(Long.MAX_VALUE, SafeMath.safeAdd(Long.MAX_VALUE - 10L, 50L))
        assertEquals(Long.MIN_VALUE, SafeMath.safeAdd(Long.MIN_VALUE + 10L, -50L))
        assertEquals(300L, SafeMath.safeAdd(100L, 200L))

        // SafeMath.safeSubtract overflow clamping
        assertEquals(Long.MAX_VALUE, SafeMath.safeSubtract(Long.MAX_VALUE - 10L, -50L))
        assertEquals(Long.MIN_VALUE, SafeMath.safeSubtract(Long.MIN_VALUE + 10L, 50L))
        assertEquals(50L, SafeMath.safeSubtract(150L, 100L))

        // safeSumOf extension
        val largeList = listOf(Long.MAX_VALUE / 2 + 100L, Long.MAX_VALUE / 2 + 200L)
        val clampedSum = largeList.safeSumOf { it }
        assertEquals(Long.MAX_VALUE, clampedSum)

        // Normal safe sum
        val normalList = listOf(100L, 200L, 300L)
        assertEquals(600L, normalList.safeSumOf { it })
    }
}
