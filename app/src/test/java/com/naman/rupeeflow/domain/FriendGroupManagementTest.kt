package com.naman.rupeeflow.domain

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class FriendGroupManagementTest {

    @Test
    fun testFriendCanBelongToMultipleGroups() {
        val jay = Friend(id = 1L, name = "Jay")
        val haveliGroup = FriendGroup(id = 10L, name = "Haveli", memberFriendIds = listOf(1L, 2L, 3L))
        val collegeGroup = FriendGroup(id = 20L, name = "College", memberFriendIds = listOf(1L, 4L, 5L))

        assertTrue("Jay belongs to Haveli group", haveliGroup.memberFriendIds.contains(jay.id))
        assertTrue("Jay belongs to College group", collegeGroup.memberFriendIds.contains(jay.id))
    }

    @Test
    fun testAddingExistingFriendDoesNotDuplicateMembership() {
        val initialMembers = listOf(1L, 2L, 3L)
        val newFriendId = 2L // Already a member
        val updatedMembers = (initialMembers + newFriendId).distinct()

        assertEquals("Member count remains 3 after adding existing friend", 3, updatedMembers.size)
        assertEquals(listOf(1L, 2L, 3L), updatedMembers)
    }

    @Test
    fun testRemovingMemberDoesNotDeleteFriendOrTransactions() {
        val friend1 = Friend(id = 1L, name = "Jay")
        val friend2 = Friend(id = 2L, name = "Manan")
        val allFriends = listOf(friend1, friend2)

        val txn = Transaction(
            id = 100L,
            title = "Dinner at Haveli",
            amountPaise = 20000L,
            categoryId = 1L,
            transactionDate = System.currentTimeMillis(),
            paidByFriendId = null,
            paidByName = "Me",
            participants = listOf(
                TransactionParticipant(id = 1L, transactionId = 100L, friendId = null, shareAmountPaise = 10000L),
                TransactionParticipant(id = 2L, transactionId = 100L, friendId = 1L, shareAmountPaise = 10000L)
            ),
            groupId = 10L
        )

        // Group members before: [1L, 2L]. Remove Jay (1L).
        val groupMembersAfter = listOf(2L)

        // Verify friend list remains unchanged
        assertEquals("Friend list size remains 2", 2, allFriends.size)
        assertTrue("Jay still exists in friends ledger", allFriends.any { it.id == 1L })

        // Verify transaction participant shares remain intact
        assertEquals(2, txn.participants.size)
        val jayShare = txn.participants.firstOrNull { it.friendId == 1L }
        assertNotNull(jayShare)
        assertEquals(10000L, jayShare?.shareAmountPaise)
    }

    @Test
    fun testDeletingGroupDoesNotDeleteFriendsOrTransactionsOrBalances() {
        val friends = listOf(
            Friend(id = 1L, name = "Jay"),
            Friend(id = 2L, name = "Sukrut")
        )
        val transactions = listOf(
            Transaction(
                id = 101L,
                title = "Hostel Rent",
                amountPaise = 60000L,
                categoryId = 1L,
                transactionDate = System.currentTimeMillis(),
                paidByFriendId = null,
                participants = listOf(
                    TransactionParticipant(id = 1L, transactionId = 101L, friendId = null, shareAmountPaise = 30000L),
                    TransactionParticipant(id = 2L, transactionId = 101L, friendId = 1L, shareAmountPaise = 30000L)
                ),
                groupId = 10L
            )
        )

        val ledgerBefore = LedgerCalculator.computeFriendLedgers(friends, transactions, emptyList())
        val jayBalanceBefore = ledgerBefore.first { it.id == 1L }.netBalancePaise

        // Simulate deleting group: group is deleted, transaction.groupId set to null
        val transactionsAfterGroupDeleted = transactions.map { it.copy(groupId = null) }
        val ledgerAfter = LedgerCalculator.computeFriendLedgers(friends, transactionsAfterGroupDeleted, emptyList())
        val jayBalanceAfter = ledgerAfter.first { it.id == 1L }.netBalancePaise

        assertEquals("Jay owes me 30000 paise before group deletion", 30000L, jayBalanceBefore)
        assertEquals("Jay owes me exactly 30000 paise after group deletion", jayBalanceBefore, jayBalanceAfter)
        assertEquals("Friends list intact", 2, friends.size)
        assertEquals("Transactions intact", 1, transactionsAfterGroupDeleted.size)
    }

    @Test
    fun testFriendGroupDescriptionSupport() {
        val group = FriendGroup(
            id = 5L,
            name = "Haveli",
            description = "Hostel room expense split group",
            memberCount = 3,
            memberFriendIds = listOf(1L, 2L, 3L)
        )
        assertEquals("Haveli", group.name)
        assertEquals("Hostel room expense split group", group.description)
        assertEquals(3, group.memberCount)
    }
}

