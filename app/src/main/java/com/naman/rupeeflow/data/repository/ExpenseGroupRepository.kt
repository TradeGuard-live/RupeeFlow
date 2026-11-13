package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.ExpenseGroupDao
import com.naman.rupeeflow.data.local.dao.TransactionDao
import com.naman.rupeeflow.data.local.entity.ExpenseGroupEntity
import com.naman.rupeeflow.domain.model.ExpenseGroup
import com.naman.rupeeflow.domain.model.PaymentMethod
import com.naman.rupeeflow.domain.model.TransactionStatus
import com.naman.rupeeflow.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
        iconName: String? = null,
        colorHex: String? = null
    ): Long
    suspend fun updateExpenseGroup(group: ExpenseGroup)
    suspend fun setArchived(id: Long, isArchived: Boolean)
    suspend fun deleteExpenseGroup(id: Long)
}

class ExpenseGroupRepositoryImpl(
    private val expenseGroupDao: ExpenseGroupDao,
    private val transactionDao: TransactionDao
) : ExpenseGroupRepository {

    override fun getAllExpenseGroups(): Flow<List<ExpenseGroup>> {
        return combine(
            expenseGroupDao.getAllExpenseGroups(),
            transactionDao.getAllTransactions()
        ) { groupEntities, allTxns ->
            val txnsByGroup = allTxns.groupBy { it.transaction.expenseGroupId }
            groupEntities.map { entity ->
                val groupTxns = txnsByGroup[entity.id] ?: emptyList()
                entity.toDomain(groupTxns)
            }
        }
    }

    override fun getActiveExpenseGroups(): Flow<List<ExpenseGroup>> {
        return combine(
            expenseGroupDao.getActiveExpenseGroups(),
            transactionDao.getAllTransactions()
        ) { groupEntities, allTxns ->
            val txnsByGroup = allTxns.groupBy { it.transaction.expenseGroupId }
            groupEntities.map { entity ->
                val groupTxns = txnsByGroup[entity.id] ?: emptyList()
                entity.toDomain(groupTxns)
            }
        }
    }

    override suspend fun getAllExpenseGroupsList(): List<ExpenseGroup> {
        val groupEntities = expenseGroupDao.getAllExpenseGroupsList()
        val allTxns = transactionDao.getAllTransactionsList()
        val txnsByGroup = allTxns.groupBy { it.transaction.expenseGroupId }
        return groupEntities.map { entity ->
            val groupTxns = txnsByGroup[entity.id] ?: emptyList()
            entity.toDomain(groupTxns)
        }
    }

    override suspend fun getExpenseGroupById(id: Long): ExpenseGroup? {
        val entity = expenseGroupDao.getExpenseGroupById(id) ?: return null
        val groupTxns = transactionDao.getTransactionsForExpenseGroupList(id)
        return entity.toDomain(groupTxns)
    }

    override fun getExpenseGroupByIdFlow(id: Long): Flow<ExpenseGroup?> {
        return combine(
            expenseGroupDao.getExpenseGroupByIdFlow(id),
            transactionDao.getTransactionsForExpenseGroup(id)
        ) { entity, groupTxns ->
            entity?.toDomain(groupTxns)
        }
    }

    override suspend fun createExpenseGroup(
        name: String,
        description: String?,
        iconName: String?,
        colorHex: String?
    ): Long {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Expense group name cannot be blank" }
        val now = System.currentTimeMillis()
        val entity = ExpenseGroupEntity(
            name = trimmed,
            description = description?.trim()?.ifBlank { null },
            iconName = iconName,
            colorHex = colorHex,
            isArchived = false,
            createdAt = now,
            updatedAt = now
        )
        return expenseGroupDao.insertExpenseGroup(entity)
    }

    override suspend fun updateExpenseGroup(group: ExpenseGroup) {
        val trimmed = group.name.trim()
        require(trimmed.isNotBlank()) { "Expense group name cannot be blank" }
        val entity = ExpenseGroupEntity(
            id = group.id,
            name = trimmed,
            description = group.description?.trim()?.ifBlank { null },
            iconName = group.iconName,
            colorHex = group.colorHex,
            isArchived = group.isArchived,
            createdAt = group.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        expenseGroupDao.updateExpenseGroup(entity)
    }

    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        expenseGroupDao.setExpenseGroupArchived(id, isArchived)
    }

    override suspend fun deleteExpenseGroup(id: Long) {
        // Safe deletion: transactions will have expenseGroupId set to null via foreign key CASCADE SET_NULL,
        // and expenseGroupName remains preserved as historical record!
        expenseGroupDao.deleteExpenseGroupById(id)
    }

    private fun ExpenseGroupEntity.toDomain(
        transactions: List<com.naman.rupeeflow.data.local.dao.TransactionWithParticipants>
    ): ExpenseGroup {
        val activeTxns = transactions.filter { it.transaction.transactionStatus == TransactionStatus.ACTIVE }
        val expenseTxns = activeTxns.filter { it.transaction.transactionType == TransactionType.EXPENSE }

        val totalSpendingPaise = expenseTxns.sumOf { it.transaction.amountPaise }
        val cashSpendingPaise = expenseTxns.filter { it.transaction.paymentMethod == PaymentMethod.CASH }
            .sumOf { it.transaction.amountPaise }
        val onlineSpendingPaise = expenseTxns.filter { it.transaction.paymentMethod != PaymentMethod.CASH }
            .sumOf { it.transaction.amountPaise }

        return ExpenseGroup(
            id = id,
            name = name,
            description = description,
            iconName = iconName,
            colorHex = colorHex,
            isArchived = isArchived,
            transactionCount = activeTxns.size,
            totalSpendingPaise = totalSpendingPaise,
            cashSpendingPaise = cashSpendingPaise,
            onlineSpendingPaise = onlineSpendingPaise,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

