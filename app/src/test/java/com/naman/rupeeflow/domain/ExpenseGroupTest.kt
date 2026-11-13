package com.naman.rupeeflow.domain

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ExpenseGroupTest {

    @Test
    fun testExpenseGroupCreationAndProperties() {
        val group = ExpenseGroup(
            id = 1L,
            name = "Goa Trip",
            description = "November 2026 beach vacation",
            iconName = "flight",
            colorHex = "#3B82F6",
            isArchived = false
        )

        assertEquals("Goa Trip", group.name)
        assertEquals("November 2026 beach vacation", group.description)
        assertFalse(group.isArchived)
        assertEquals(0L, group.totalSpendingPaise)
        assertEquals(0, group.transactionCount)
    }

    @Test
    fun testExpenseGroupArchiving() {
        val group = ExpenseGroup(id = 1L, name = "College Tour", isArchived = false)
        val archived = group.copy(isArchived = true)
        assertTrue(archived.isArchived)

        val unarchived = archived.copy(isArchived = false)
        assertFalse(unarchived.isArchived)
    }

    @Test
    fun testExpenseGroupSpendingMetrics() {
        val txns = listOf(
            Transaction(
                id = 1L,
                title = "Hotel Booking",
                amountPaise = 500000L, // ₹5,000
                categoryId = 1L,
                transactionDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.UPI,
                transactionType = TransactionType.EXPENSE,
                expenseGroupId = 10L,
                expenseGroupName = "Goa Trip"
            ),
            Transaction(
                id = 2L,
                title = "Beach Shack Snacks",
                amountPaise = 75000L, // ₹750
                categoryId = 2L,
                transactionDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.CASH,
                transactionType = TransactionType.EXPENSE,
                expenseGroupId = 10L,
                expenseGroupName = "Goa Trip"
            ),
            Transaction(
                id = 3L,
                title = "Fuel",
                amountPaise = 150000L, // ₹1,500
                categoryId = 3L,
                transactionDate = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.DEBIT_CARD,
                transactionType = TransactionType.EXPENSE,
                expenseGroupId = 10L,
                expenseGroupName = "Goa Trip"
            )
        )

        val totalExpenses = txns.sumOf { it.amountPaise }
        val cashExpenses = txns.filter { it.paymentMethod == PaymentMethod.CASH }.sumOf { it.amountPaise }
        val onlineExpenses = txns.filter { it.paymentMethod != PaymentMethod.CASH }.sumOf { it.amountPaise }

        assertEquals(725000L, totalExpenses) // ₹7,250
        assertEquals(75000L, cashExpenses)   // ₹750
        assertEquals(650000L, onlineExpenses) // ₹6,500
        assertEquals(totalExpenses, cashExpenses + onlineExpenses)
        assertEquals(3, txns.size)
    }

    @Test
    fun testSafeExpenseGroupDeletionPreservesTransactionsAndHistoricalName() {
        val originalTxn = Transaction(
            id = 42L,
            title = "Flight Tickets",
            amountPaise = 800000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            expenseGroupId = 99L,
            expenseGroupName = "Europe Vacation"
        )

        // When group 99 is deleted, foreign key sets expenseGroupId to null,
        // but historical expenseGroupName string snapshot is preserved!
        val detachedTxn = originalTxn.copy(expenseGroupId = null)

        assertNull(detachedTxn.expenseGroupId)
        assertEquals("Europe Vacation", detachedTxn.expenseGroupName)
        assertTrue("Transaction still indicates it belonged to a group historically", detachedTxn.hasExpenseGroup)
        assertEquals(800000L, detachedTxn.amountPaise)
    }

    @Test
    fun testExpenseGroupsNeverAlterFriendLedgerBalances() {
        val friends = listOf(Friend(id = 1L, name = "Jay"))

        // Transaction without expense group
        val txnWithoutGroup = Transaction(
            id = 1L,
            title = "Goa Dinner",
            amountPaise = 20000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null,
            participants = listOf(
                TransactionParticipant(id = 1L, transactionId = 1L, friendId = null, shareAmountPaise = 10000L),
                TransactionParticipant(id = 2L, transactionId = 1L, friendId = 1L, shareAmountPaise = 10000L)
            )
        )

        // Same transaction with expense group assigned
        val txnWithGroup = txnWithoutGroup.copy(
            expenseGroupId = 10L,
            expenseGroupName = "Goa Trip"
        )

        val balanceWithoutGroup = LedgerCalculator.computeFriendLedgers(friends, listOf(txnWithoutGroup), emptyList())
        val balanceWithGroup = LedgerCalculator.computeFriendLedgers(friends, listOf(txnWithGroup), emptyList())

        assertEquals(
            balanceWithoutGroup.first().netBalancePaise,
            balanceWithGroup.first().netBalancePaise
        )
        assertEquals(10000L, balanceWithGroup.first().netBalancePaise)
    }
}

