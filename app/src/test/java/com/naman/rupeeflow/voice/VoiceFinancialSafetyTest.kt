package com.naman.rupeeflow.voice

import com.naman.rupeeflow.data.repository.FriendGroupRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.execution.VoiceActionExecutor
import com.naman.rupeeflow.voice.execution.VoiceCommandValidator
import com.naman.rupeeflow.voice.model.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class VoiceFinancialSafetyTest {

    private lateinit var friends: List<Friend>
    private lateinit var groups: List<FriendGroup>
    private lateinit var categories: List<Category>

    @Before
    fun setup() {
        friends = listOf(
            Friend(id = 101L, name = "Jay"),
            Friend(id = 102L, name = "Manav"),
            Friend(id = 103L, name = "Priya")
        )
        groups = listOf(
            FriendGroup(id = 1L, name = "Flatmates", memberFriendIds = listOf(101L, 102L))
        )
        categories = listOf(
            Category(id = 1L, name = "Food", iconName = "food", colorHex = "#EF4444")
        )
    }

    @Test
    fun test_SplitInvariant_PaiseConservationInEqualSplit() {
        // ₹100 split 3 ways -> 33.34, 33.33, 33.33 -> 3334, 3333, 3333 paise
        val cmd = VoiceCommand.CreateTransaction(
            title = "Dinner",
            amountPaise = 10000L,
            participantFriendIds = listOf(null, 101L, 102L), // 3 people
            splitMethod = SplitMethod.EQUAL
        )

        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.PreviewReady)
        val preview = (result as VoiceCommandResult.PreviewReady).preview as VoiceActionPreview.TransactionPreview

        val sumShares = preview.transaction.participants.sumOf { it.shareAmountPaise }
        assertEquals(10000L, sumShares)
        assertEquals(3, preview.transaction.participants.size)
        assertTrue(LedgerCalculator.validateParticipantShares(preview.transaction.amountPaise, preview.transaction.participants))
    }

    @Test
    fun test_ZeroOrNegativeAmount_RequiresClarificationOrFails() {
        val zeroCmd = VoiceCommand.CreateTransaction(
            title = "Lunch",
            amountPaise = 0L
        )
        val resultZero = VoiceCommandValidator.validateAndBuildPreview(zeroCmd, friends, groups, categories)
        assertTrue(resultZero is VoiceCommandResult.ClarificationRequired)
        assertEquals(VoiceClarificationType.MISSING_AMOUNT, (resultZero as VoiceCommandResult.ClarificationRequired).prompt.type)

        val negCmd = VoiceCommand.CreateTransaction(
            title = "Lunch",
            amountPaise = -5000L
        )
        val resultNeg = VoiceCommandValidator.validateAndBuildPreview(negCmd, friends, groups, categories)
        assertTrue(resultNeg is VoiceCommandResult.ClarificationRequired)
    }

    @Test
    fun test_ExactAmountSplit_MismatchRejection() {
        // Total is ₹100 (10000 paise), but shares sum to ₹90 (9000 paise)
        val cmd = VoiceCommand.CreateTransaction(
            title = "Trip Snack",
            amountPaise = 10000L,
            participantFriendIds = listOf(null, 101L),
            splitMethod = SplitMethod.EXACT_AMOUNT,
            customSharesPaise = mapOf(null to 5000L, 101L to 4000L) // 9000 total != 10000
        )

        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.Error)
        val err = result as VoiceCommandResult.Error
        assertTrue(err.errorMessage.contains("does not equal"))
    }

    @Test
    fun test_PercentageSplit_MustTotal100Percent() {
        val cmd = VoiceCommand.CreateTransaction(
            title = "Taxi",
            amountPaise = 50000L,
            participantFriendIds = listOf(null, 101L),
            splitMethod = SplitMethod.PERCENTAGE,
            customPercentages = mapOf(null to 60.0, 101L to 30.0) // 90% != 100%
        )

        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.Error)
    }

    @Test
    fun test_MissingFriendName_RequiresClarification() {
        val cmd = VoiceCommand.CreateFriend(name = "")
        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.ClarificationRequired)
        val clarification = result as VoiceCommandResult.ClarificationRequired
        assertEquals(VoiceClarificationType.MISSING_FRIEND_NAME, clarification.prompt.type)
    }

    @Test
    fun test_MissingGroupName_RequiresClarification() {
        val cmd = VoiceCommand.CreateGroup(name = "")
        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.ClarificationRequired)
        val clarification = result as VoiceCommandResult.ClarificationRequired
        assertEquals(VoiceClarificationType.MISSING_GROUP_NAME, clarification.prompt.type)
    }

    @Test
    fun test_FriendDeletion_MarkedAsDestructive() {
        val cmd = VoiceCommand.DeleteFriend(friendId = 101L, friendName = "Jay")
        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.PreviewReady)
        val preview = (result as VoiceCommandResult.PreviewReady).preview as VoiceActionPreview.FriendPreview
        assertTrue(preview.isDelete)
        assertEquals("Jay", preview.friend.name)
    }

    @Test
    fun test_GroupDeletion_MarkedAsDestructive() {
        val cmd = VoiceCommand.DeleteGroup(groupId = 1L, groupName = "Flatmates")
        val result = VoiceCommandValidator.validateAndBuildPreview(cmd, friends, groups, categories)
        assertTrue(result is VoiceCommandResult.PreviewReady)
        val preview = (result as VoiceCommandResult.PreviewReady).preview as VoiceActionPreview.GroupPreview
        assertTrue(preview.isDelete)
        assertEquals("Flatmates", preview.group.name)
    }

    @Test
    fun test_IdempotentExecution_PreventsDuplicateSaves() = runBlocking {
        var saveCallCount = 0

        val fakeTxnRepo = object : TransactionRepository {
            override fun getAllTransactions() = flowOf(emptyList<Transaction>())
            override suspend fun getAllTransactionsList() = emptyList<Transaction>()
            override fun getTransactionsByStatus(status: TransactionStatus) = flowOf(emptyList<Transaction>())
            override suspend fun getTransactionById(id: Long): Transaction? = null
            override fun getTransactionByIdFlow(id: Long) = flowOf(null)
            override suspend fun saveTransaction(transaction: Transaction): Long {
                saveCallCount++
                return 42L
            }
            override suspend fun updateTransactionStatus(id: Long, status: TransactionStatus) {}
            override suspend fun deleteTransaction(id: Long) {}
            override suspend fun isDuplicate(
                amountPaise: Long,
                timestamp: Long,
                refNumber: String?,
                merchant: String?,
                title: String?
            ) = false
        }

        val fakeSettlementRepo = object : SettlementRepository {
            override fun getAllSettlements() = flowOf(emptyList<Settlement>())
            override suspend fun getAllSettlementsList() = emptyList<Settlement>()
            override fun getSettlementsForFriend(friendId: Long) = flowOf(emptyList<Settlement>())
            override suspend fun getSettlementsForFriendList(friendId: Long) = emptyList<Settlement>()
            override suspend fun getSettlementById(id: Long): Settlement? = null
            override suspend fun recordSettlement(settlement: Settlement) = 1L
            override suspend fun updateSettlement(settlement: Settlement) {}
            override suspend fun reverseSettlement(id: Long) {}
            override suspend fun unreverseSettlement(id: Long) {}
            override suspend fun deleteSettlement(settlement: Settlement) {}
        }

        val fakeFriendRepo = object : FriendRepository {
            override fun getActiveFriends() = flowOf(emptyList<Friend>())
            override fun getAllFriends() = flowOf(emptyList<Friend>())
            override suspend fun getAllFriendsList() = emptyList<Friend>()
            override suspend fun getFriendById(id: Long): Friend? = null
            override fun getFriendByIdFlow(id: Long) = flowOf(null)
            override suspend fun addFriend(friend: Friend): Long = 101L
            override suspend fun updateFriend(friend: Friend) {}
            override suspend fun deleteFriend(friend: Friend) {}
            override suspend fun archiveFriend(id: Long) {}
            override suspend fun unarchiveFriend(id: Long) {}
            override suspend fun isFriendReferenced(friendId: Long) = false
        }

        val fakeGroupRepo = object : FriendGroupRepository {
            override fun getAllGroups() = flowOf(emptyList<FriendGroup>())
            override suspend fun getAllGroupsList() = emptyList<FriendGroup>()
            override suspend fun getGroupById(id: Long): FriendGroup? = null
            override fun getGroupByIdFlow(id: Long) = flowOf(null)
            override suspend fun createGroup(
                name: String,
                iconName: String?,
                colorHex: String?,
                friendIds: List<Long>,
                groupImagePath: String?
            ): Long = 1L
            override suspend fun updateGroup(group: FriendGroup, friendIds: List<Long>?) {}
            override suspend fun deleteGroup(groupId: Long) {}
            override suspend fun addFriendToGroup(groupId: Long, friendId: Long) {}
            override suspend fun removeFriendFromGroup(groupId: Long, friendId: Long) {}
            override suspend fun setGroupMembers(groupId: Long, friendIds: List<Long>) {}
            override fun getGroupsForFriend(friendId: Long) = flowOf(emptyList<FriendGroup>())
        }

        val executor = VoiceActionExecutor(fakeTxnRepo, fakeSettlementRepo, fakeFriendRepo, fakeGroupRepo)
        val preview = VoiceActionPreview.TransactionPreview(
            transaction = Transaction(title = "Coffee", amountPaise = 15000L)
        )

        val token = UUID.randomUUID().toString()

        // First execution -> succeeds and calls saveTransaction
        val result1 = executor.executePreview(preview, VoiceLanguage.ENGLISH, token)
        assertTrue(result1 is VoiceCommandResult.Success)
        assertEquals(1, saveCallCount)

        // Second execution with SAME token -> returns idempotent duplicate, does not increment saveCallCount
        val result2 = executor.executePreview(preview, VoiceLanguage.ENGLISH, token)
        assertTrue(result2 is VoiceCommandResult.Success)
        assertEquals("IDEMPOTENT_DUPLICATE", (result2 as VoiceCommandResult.Success).actionType)
        assertEquals(1, saveCallCount)
    }
}

