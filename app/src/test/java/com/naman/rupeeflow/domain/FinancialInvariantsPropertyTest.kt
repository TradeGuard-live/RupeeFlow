package com.naman.rupeeflow.domain

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.SafeMath
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * Property-based test suite verifying core financial and ledger invariants across pseudo-random inputs.
 */
class FinancialInvariantsPropertyTest {

    private val random = Random(42) // Fixed seed for deterministic, repeatable testing

    @Test
    fun testInvariant_ConservationOfMoney_EqualSplits() {
        // Invariant: For all positive amounts and participant counts, sum(shares) == totalAmount
        val testAmounts = listOf(
            1L, 2L, 3L, 99L, 100L, 101L, 1000L, 123456L, 1000000L, 999999999L
        )

        for (amount in testAmounts) {
            for (count in 1..25) {
                val shares = LedgerCalculator.calculateEqualSplit(amount, count)
                assertEquals("Participant count mismatch for amount $amount and count $count", count, shares.size)
                assertEquals("Conservation of money violated for amount $amount and count $count", amount, shares.sum())
                assertTrue("Negative share produced", shares.all { it >= 0L })

                // Invariant: Max share - min share <= 1 paise
                val max = shares.maxOrNull() ?: 0L
                val min = shares.minOrNull() ?: 0L
                assertTrue("Disproportionate distribution: max $max, min $min", (max - min) <= 1L)
            }
        }
    }

    @Test
    fun testInvariant_ConservationOfMoney_RandomizedEqualSplits() {
        // Property test over 500 randomized transactions
        for (i in 1..500) {
            val amount = 1L + random.nextLong().let { if (it < 0) -it else it } % 50_000_000L // 1 paise to ₹5,00,000
            val count = 1 + random.nextInt(20) // 1 to 20 participants
            val shares = LedgerCalculator.calculateEqualSplit(amount, count)

            assertEquals(count, shares.size)
            assertEquals("Conservation of money violated", amount, shares.sum())
        }
    }

    @Test
    fun testInvariant_FriendDebtNettingSymmetry() {
        val friend = Friend(id = 101L, name = "Invariant Friend")

        // Invariant: For any gross debts they owe me (X) and I owe them (Y):
        // net == X - Y. Status strictly matches sign of net.
        val testPairs = listOf(
            Pair(50000L, 20000L), // net = +30000
            Pair(20000L, 50000L), // net = -30000
            Pair(40000L, 40000L), // net = 0
            Pair(0L, 100000L),    // net = -100000
            Pair(100000L, 0L)     // net = +100000
        )

        for ((mePaidAmount, friendPaidAmount) in testPairs) {
            val txns = mutableListOf<Transaction>()
            if (mePaidAmount > 0) {
                txns.add(
                    Transaction(
                        id = 1L,
                        title = "Me Paid",
                        amountPaise = mePaidAmount,
                        paidByFriendId = null,
                        participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = mePaidAmount))
                    )
                )
            }
            if (friendPaidAmount > 0) {
                txns.add(
                    Transaction(
                        id = 2L,
                        title = "Friend Paid",
                        amountPaise = friendPaidAmount,
                        paidByFriendId = 101L,
                        participants = listOf(TransactionParticipant(friendId = null, shareAmountPaise = friendPaidAmount))
                    )
                )
            }

            val ledgers = LedgerCalculator.computeFriendLedgers(listOf(friend), txns, emptyList())
            val res = ledgers.first()
            val expectedNet = mePaidAmount - friendPaidAmount

            assertEquals("Net balance mismatch", expectedNet, res.netBalancePaise)

            when {
                expectedNet > 0 -> {
                    assertEquals(FriendBalanceStatus.THEY_OWE_ME, res.status)
                    assertEquals(expectedNet, res.totalTheyOweMePaise)
                    assertEquals(0L, res.totalIOweThemPaise)
                }
                expectedNet < 0 -> {
                    assertEquals(FriendBalanceStatus.I_OWE_THEM, res.status)
                    assertEquals(0L, res.totalTheyOweMePaise)
                    assertEquals(-expectedNet, res.totalIOweThemPaise)
                }
                else -> {
                    assertEquals(FriendBalanceStatus.SETTLED, res.status)
                    assertEquals(0L, res.totalTheyOweMePaise)
                    assertEquals(0L, res.totalIOweThemPaise)
                }
            }
        }
    }

    @Test
    fun testInvariant_SettlementReversibility() {
        val friend = Friend(id = 101L, name = "Reversible Friend")
        val txn = Transaction(
            title = "Base Debt",
            amountPaise = 50000L,
            paidByFriendId = null,
            participants = listOf(TransactionParticipant(friendId = 101L, shareAmountPaise = 50000L))
        )
        val initialLedger = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(txn), emptyList()).first()

        // Settle
        val settlement = Settlement(id = 1L, payerId = 101L, receiverId = null, amountPaise = 50000L, isReversed = false)
        val settledLedger = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(txn), listOf(settlement)).first()
        assertEquals(0L, settledLedger.netBalancePaise)

        // Reverse
        val reversed = settlement.copy(isReversed = true)
        val reversedLedger = LedgerCalculator.computeFriendLedgers(listOf(friend), listOf(txn), listOf(reversed)).first()

        // Invariant: Reversing a settlement returns to identical initial state
        assertEquals(initialLedger.netBalancePaise, reversedLedger.netBalancePaise)
        assertEquals(initialLedger.status, reversedLedger.status)
        assertEquals(initialLedger.totalTheyOweMePaise, reversedLedger.totalTheyOweMePaise)
    }

    @Test
    fun testInvariant_SafeMathOverflowMonotonicity() {
        // SafeMath.safeAdd never wraps around
        val nearMax = Long.MAX_VALUE - 100L
        for (i in 1..200) {
            val res = SafeMath.safeAdd(nearMax, i.toLong())
            assertTrue("Addition overflow resulted in negative number: $res", res > 0L)
        }

        // SafeMath.safeSubtract never wraps around
        val nearMin = Long.MIN_VALUE + 100L
        for (i in 1..200) {
            val res = SafeMath.safeSubtract(nearMin, i.toLong())
            assertTrue("Subtraction overflow resulted in positive number: $res", res < 0L)
        }

        // SafeMath.safeAbs(Long.MIN_VALUE) == Long.MAX_VALUE
        assertEquals(Long.MAX_VALUE, SafeMath.safeAbs(Long.MIN_VALUE))
    }
}

