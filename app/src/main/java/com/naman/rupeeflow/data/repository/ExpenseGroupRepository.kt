package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.ExpenseGroupDao
import com.naman.rupeeflow.data.local.dao.FriendGroupDao
import com.naman.rupeeflow.data.local.dao.TransactionDao
import com.naman.rupeeflow.data.local.entity.ExpenseGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupMemberEntity
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface ExpenseGroupRepository {
    fun getAllExpenseGroups(): Flow<List<ExpenseGroup>>
    fun getActiveExpenseGroups(): Flow<List<ExpenseGroup>>
    suspend fun getAllExpenseGroupsList(): List<ExpenseGroup>
    suspend fun getExpenseGroupById(id: Long): ExpenseGroup?
    fun getExpenseGroupByIdFlow(id: Long): Flow<ExpenseGroup?>
    suspend fun createExpenseGroup(
        name: String,
        description: String? = null,
        destination: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        coverImageUri: String? = null,
        iconName: String? = "flight",
        colorHex: String? = "#3B82F6",
        friendIds: List<Long> = emptyList()
    ): Long
    suspend fun updateExpenseGroup(
        group: ExpenseGroup,
        friendIds: List<Long>? = null
    )
    suspend fun setArchived(id: Long, isArchived: Boolean)
    suspend fun deleteExpenseGroup(id: Long)
    suspend fun recreateLinkedFriendGroup(expenseGroupId: Long, friendIds: List<Long>): Long?
}

class ExpenseGroupRepositoryImpl(
    private val expenseGroupDao: ExpenseGroupDao,
    private val transactionDao: TransactionDao,
    private val friendGroupDao: FriendGroupDao? = null
) : ExpenseGroupRepository {

    override fun getAllExpenseGroups(): Flow<List<ExpenseGroup>> {
        val membersFlow = friendGroupDao?.getAllGroupMembers() ?: flowOf(emptyList())
        return combine(
            expenseGroupDao.getAllExpenseGroups(),
            transactionDao.getAllTransactions(),
            membersFlow
        ) { groupEntities, allTxns, allMembers ->
            val txnsByGroup = allTxns.groupBy { it.transaction.expenseGroupId }
            val membersByGroup = allMembers.groupBy { it.groupId }
            groupEntities.map { entity ->
                val groupTxns = txnsByGroup[entity.id] ?: emptyList()
                val memberIds = entity.linkedFriendGroupId?.let { fgId ->
                    membersByGroup[fgId]?.map { it.friendId }
                } ?: emptyList()
                entity.toDomain(groupTxns, memberIds)
            }
        }
    }

    override fun getActiveExpenseGroups(): Flow<List<ExpenseGroup>> {
        val membersFlow = friendGroupDao?.getAllGroupMembers() ?: flowOf(emptyList())
        return combine(
            expenseGroupDao.getActiveExpenseGroups(),
            transactionDao.getAllTransactions(),
            membersFlow
        ) { groupEntities, allTxns, allMembers ->
            val txnsByGroup = allTxns.groupBy { it.transaction.expenseGroupId }
            val membersByGroup = allMembers.groupBy { it.groupId }
            groupEntities.map { entity ->
                val groupTxns = txnsByGroup[entity.id] ?: emptyList()
                val memberIds = entity.linkedFriendGroupId?.let { fgId ->
                    membersByGroup[fgId]?.map { it.friendId }
                } ?: emptyList()
                entity.toDomain(groupTxns, memberIds)
            }
        }
    }

    override suspend fun getAllExpenseGroupsList(): List<ExpenseGroup> {
        val groupEntities = expenseGroupDao.getAllExpenseGroupsList()
        val allTxns = transactionDao.getAllTransactionsList()
        val allMembers = friendGroupDao?.getAllGroupMembersList() ?: emptyList()
        val txnsByGroup = allTxns.groupBy { it.transaction.expenseGroupId }
        val membersByGroup = allMembers.groupBy { it.groupId }
        return groupEntities.map { entity ->
            val groupTxns = txnsByGroup[entity.id] ?: emptyList()
            val memberIds = entity.linkedFriendGroupId?.let { fgId ->
                membersByGroup[fgId]?.map { it.friendId }
            } ?: emptyList()
            entity.toDomain(groupTxns, memberIds)
        }
    }

    override suspend fun getExpenseGroupById(id: Long): ExpenseGroup? {
        val entity = expenseGroupDao.getExpenseGroupById(id) ?: return null
        val groupTxns = transactionDao.getTransactionsForExpenseGroupList(id)
        val memberIds = entity.linkedFriendGroupId?.let { fgId ->
            friendGroupDao?.getMemberFriendIds(fgId)
        } ?: emptyList()
        return entity.toDomain(groupTxns, memberIds)
    }

    override fun getExpenseGroupByIdFlow(id: Long): Flow<ExpenseGroup?> {
        val membersFlow = friendGroupDao?.getAllGroupMembers() ?: flowOf(emptyList())
        return combine(
            expenseGroupDao.getExpenseGroupByIdFlow(id),
            transactionDao.getTransactionsForExpenseGroup(id),
            membersFlow
        ) { entity, groupTxns, allMembers ->
            if (entity == null) null
            else {
                val memberIds = entity.linkedFriendGroupId?.let { fgId ->
                    allMembers.filter { it.groupId == fgId }.map { it.friendId }
                } ?: emptyList()
                entity.toDomain(groupTxns, memberIds)
            }
        }
    }

    override suspend fun createExpenseGroup(
        name: String,
        description: String?,
        destination: String?,
        startDate: Long?,
        endDate: Long?,
        coverImageUri: String?,
        iconName: String?,
        colorHex: String?,
        friendIds: List<Long>
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Expense group name cannot be blank" }
        val now = System.currentTimeMillis()

        // 1. Automatically create or link corresponding FriendGroup if friendGroupDao is available
        var linkedFgId: Long? = null
        if (friendGroupDao != null) {
            val fgEntity = FriendGroupEntity(
                name = trimmed,
                description = "Trip group for $trimmed",
                iconName = iconName ?: "flight",
                colorHex = colorHex ?: "#3B82F6",
                groupImagePath = coverImageUri,
                createdAt = now,
                updatedAt = now
            )
            val newFgId = friendGroupDao.insertGroup(fgEntity)
            if (friendIds.isNotEmpty()) {
                val members = friendIds.distinct().map { fId ->
                    FriendGroupMemberEntity(groupId = newFgId, friendId = fId, addedAt = now)
                }
                friendGroupDao.insertMembers(members)
            }
            linkedFgId = newFgId
        }

        // 2. Create Trip ExpenseGroup linked to the created FriendGroup
        val entity = ExpenseGroupEntity(
            name = trimmed,
            description = description?.trim()?.ifBlank { null },
            destination = destination?.trim()?.ifBlank { null },
            startDate = startDate,
            endDate = endDate,
            coverImageUri = coverImageUri,
            iconName = iconName ?: "flight",
            colorHex = colorHex ?: "#3B82F6",
            linkedFriendGroupId = linkedFgId,
            isArchived = false,
            archivedAt = null,
            createdAt = now,
            updatedAt = now
        )
        return expenseGroupDao.insertExpenseGroup(entity)
    }

    override suspend fun updateExpenseGroup(
        group: ExpenseGroup,
        friendIds: List<Long>?
    ) {
        val trimmed = group.name.trim()
        require(trimmed.isNotBlank()) { "Expense group name cannot be blank" }
        val now = System.currentTimeMillis()

        var currentLinkedFgId = group.linkedFriendGroupId

        // If friendGroupDao exists, synchronize with the linked FriendGroup
        if (friendGroupDao != null) {
            if (currentLinkedFgId != null) {
                val existingFg = friendGroupDao.getGroupById(currentLinkedFgId)
                if (existingFg != null) {
                    val updatedFg = existingFg.copy(
                        name = trimmed,
                        description = group.description ?: existingFg.description,
                        groupImagePath = group.coverImageUri ?: existingFg.groupImagePath,
                        updatedAt = now
                    )
                    friendGroupDao.updateGroup(updatedFg)
                    if (friendIds != null) {
                        friendGroupDao.setGroupMembers(currentLinkedFgId, friendIds.distinct())
                    }
                } else if (friendIds != null && friendIds.isNotEmpty()) {
                    // Linked group was deleted independently; recreate it!
                    val newFg = FriendGroupEntity(
                        name = trimmed,
                        description = "Trip group for $trimmed",
                        iconName = group.iconName ?: "flight",
                        colorHex = group.colorHex ?: "#3B82F6",
                        groupImagePath = group.coverImageUri,
                        createdAt = now,
                        updatedAt = now
                    )
                    val newFgId = friendGroupDao.insertGroup(newFg)
                    friendGroupDao.setGroupMembers(newFgId, friendIds.distinct())
                    currentLinkedFgId = newFgId
                }
            } else if (friendIds != null && friendIds.isNotEmpty()) {
                // Trip had no linked friend group yet; create one!
                val newFg = FriendGroupEntity(
                    name = trimmed,
                    description = "Trip group for $trimmed",
                    iconName = group.iconName ?: "flight",
                    colorHex = group.colorHex ?: "#3B82F6",
                    groupImagePath = group.coverImageUri,
                    createdAt = now,
                    updatedAt = now
                )
                val newFgId = friendGroupDao.insertGroup(newFg)
                friendGroupDao.setGroupMembers(newFgId, friendIds.distinct())
                currentLinkedFgId = newFgId
            }
        }

        val entity = ExpenseGroupEntity(
            id = group.id,
            name = trimmed,
            description = group.description?.trim()?.ifBlank { null },
            destination = group.destination?.trim()?.ifBlank { null },
            startDate = group.startDate,
            endDate = group.endDate,
            coverImageUri = group.coverImageUri,
            iconName = group.iconName ?: "flight",
            colorHex = group.colorHex ?: "#3B82F6",
            linkedFriendGroupId = currentLinkedFgId,
            isArchived = group.isArchived,
            archivedAt = group.archivedAt,
            createdAt = group.createdAt,
            updatedAt = now
        )
        expenseGroupDao.updateExpenseGroup(entity)
        transactionDao.updateExpenseGroupNameForGroup(group.id, trimmed)
    }

    override suspend fun recreateLinkedFriendGroup(expenseGroupId: Long, friendIds: List<Long>): Long? {
        val group = expenseGroupDao.getExpenseGroupById(expenseGroupId) ?: return null
        if (friendGroupDao == null) return null

        val now = System.currentTimeMillis()
        val newFg = FriendGroupEntity(
            name = group.name,
            description = "Trip group for ${group.name}",
            iconName = group.iconName ?: "flight",
            colorHex = group.colorHex ?: "#3B82F6",
            groupImagePath = group.coverImageUri,
            createdAt = now,
            updatedAt = now
        )
        val newFgId = friendGroupDao.insertGroup(newFg)
        if (friendIds.isNotEmpty()) {
            friendGroupDao.setGroupMembers(newFgId, friendIds.distinct())
        }
        val updatedGroup = group.copy(linkedFriendGroupId = newFgId, updatedAt = now)
        expenseGroupDao.updateExpenseGroup(updatedGroup)
        return newFgId
    }

    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        val now = System.currentTimeMillis()
        val current = expenseGroupDao.getExpenseGroupById(id) ?: return
        val updated = current.copy(
            isArchived = isArchived,
            archivedAt = if (isArchived) now else null,
            updatedAt = now
        )
        expenseGroupDao.updateExpenseGroup(updated)
    }

    override suspend fun deleteExpenseGroup(id: Long) {
        // Safe deletion: transactions will have expenseGroupId set to null via foreign key CASCADE SET_NULL,
        // and expenseGroupName remains preserved as historical record!
        expenseGroupDao.deleteExpenseGroupById(id)
    }

    private fun ExpenseGroupEntity.toDomain(
        transactions: List<com.naman.rupeeflow.data.local.dao.TransactionWithParticipants>,
        memberFriendIds: List<Long> = emptyList()
    ): ExpenseGroup {
        val activeTxns = transactions.filter { it.transaction.transactionStatus == TransactionStatus.ACTIVE }
        val expenseTxns = activeTxns.filter {
            it.transaction.transactionType == TransactionType.EXPENSE ||
                    it.transaction.transactionType == TransactionType.UPI_LITE_PAYMENT
        }

        fun twpPersonalExpense(twp: com.naman.rupeeflow.data.local.dao.TransactionWithParticipants): Long {
            return if (twp.participants.isNotEmpty()) {
                val me = twp.participants.firstOrNull { it.friendId == null }
                me?.shareAmountPaise ?: 0L
            } else {
                if (twp.transaction.paidByFriendId == null) twp.transaction.amountPaise else 0L
            }
        }

        val totalPersonalSpendingPaise = expenseTxns.sumOf { twpPersonalExpense(it) }
        val grossSpendingPaise = expenseTxns.sumOf { it.transaction.amountPaise }
        val cashSpendingPaise = expenseTxns
            .filter { it.transaction.paymentMethod == PaymentMethod.CASH }
            .sumOf { twpPersonalExpense(it) }
        val onlineSpendingPaise = expenseTxns
            .filter { it.transaction.paymentMethod != PaymentMethod.CASH }
            .sumOf { twpPersonalExpense(it) }

        return ExpenseGroup(
            id = id,
            name = name,
            description = description,
            destination = destination,
            startDate = startDate,
            endDate = endDate,
            coverImageUri = coverImageUri,
            iconName = iconName,
            colorHex = colorHex,
            linkedFriendGroupId = linkedFriendGroupId,
            isArchived = isArchived,
            archivedAt = archivedAt,
            transactionCount = activeTxns.size,
            memberCount = memberFriendIds.size,
            memberFriendIds = memberFriendIds,
            totalSpendingPaise = totalPersonalSpendingPaise,
            grossSpendingPaise = grossSpendingPaise,
            cashSpendingPaise = cashSpendingPaise,
            onlineSpendingPaise = onlineSpendingPaise,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}


