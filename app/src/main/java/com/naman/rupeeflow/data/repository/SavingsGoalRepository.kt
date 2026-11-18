package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.SavingsEntryDao
import com.naman.rupeeflow.data.local.dao.SavingsGoalDao
import com.naman.rupeeflow.data.local.entity.SavingsEntryEntity
import com.naman.rupeeflow.data.local.entity.SavingsGoalEntity
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.DateTimeUtils
import com.naman.rupeeflow.domain.util.SafeMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.Calendar

interface SavingsGoalRepository {
    fun getAllGoals(): Flow<List<SavingsGoal>>
    fun getActiveGoals(): Flow<List<SavingsGoal>>
    fun getGoalsByStatus(status: SavingsGoalStatus): Flow<List<SavingsGoal>>
    suspend fun getAllGoalsList(): List<SavingsGoal>
    fun getGoalById(goalId: Long): Flow<SavingsGoal?>
    suspend fun getGoalByIdSync(goalId: Long): SavingsGoal?

    suspend fun createGoal(
        name: String,
        targetAmountPaise: Long,
        initialSavedAmountPaise: Long = 0L,
        targetDate: Long? = null,
        category: SavingsGoalCategory = SavingsGoalCategory.CUSTOM,
        description: String? = null,
        imageUri: String? = null,
        colorHex: String = "#FFAD91",
        priority: SavingsGoalPriority = SavingsGoalPriority.MEDIUM
    ): Long

    suspend fun updateGoal(goal: SavingsGoal)
    suspend fun deleteGoal(goalId: Long)
    suspend fun setGoalStatus(goalId: Long, status: SavingsGoalStatus)

    suspend fun addDeposit(
        goalId: Long,
        amountPaise: Long,
        entryDate: Long,
        description: String,
        paymentMethod: String? = null,
        note: String? = null
    ): Long

    suspend fun withdraw(
        goalId: Long,
        amountPaise: Long,
        entryDate: Long,
        description: String,
        paymentMethod: String? = null,
        note: String? = null
    ): Long

    suspend fun updateEntry(entry: SavingsEntry)
    suspend fun deleteEntry(entryId: Long, goalId: Long)

    fun getEntriesForGoal(goalId: Long): Flow<List<SavingsEntry>>
    suspend fun getEntriesForGoalList(goalId: Long): List<SavingsEntry>
    fun getAllEntries(): Flow<List<SavingsEntry>>
    suspend fun getAllEntriesList(): List<SavingsEntry>
    fun getEntriesForGoalByDateRange(goalId: Long, startDate: Long, endDate: Long): Flow<List<SavingsEntry>>
    fun getDailySavingsGroups(goalId: Long): Flow<List<DailySavingsGroup>>
    fun getOverviewSummary(): Flow<SavingsOverviewSummary>
    suspend fun reconcileGoalBalance(goalId: Long)
}

class SavingsGoalRepositoryImpl(
    private val savingsGoalDao: SavingsGoalDao,
    private val savingsEntryDao: SavingsEntryDao
) : SavingsGoalRepository {

    override fun getAllGoals(): Flow<List<SavingsGoal>> {
        return savingsGoalDao.getAllGoals().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getActiveGoals(): Flow<List<SavingsGoal>> {
        return savingsGoalDao.getActiveGoals().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getGoalsByStatus(status: SavingsGoalStatus): Flow<List<SavingsGoal>> {
        return savingsGoalDao.getGoalsByStatus(status).map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllGoalsList(): List<SavingsGoal> = withContext(Dispatchers.IO) {
        savingsGoalDao.getAllGoalsList().map { it.toDomain() }
    }

    override fun getGoalById(goalId: Long): Flow<SavingsGoal?> {
        return savingsGoalDao.getGoalByIdFlow(goalId).map { it?.toDomain() }.flowOn(Dispatchers.IO)
    }

    override suspend fun getGoalByIdSync(goalId: Long): SavingsGoal? = withContext(Dispatchers.IO) {
        savingsGoalDao.getGoalById(goalId)?.toDomain()
    }

    override suspend fun createGoal(
        name: String,
        targetAmountPaise: Long,
        initialSavedAmountPaise: Long,
        targetDate: Long?,
        category: SavingsGoalCategory,
        description: String?,
        imageUri: String?,
        colorHex: String,
        priority: SavingsGoalPriority
    ): Long = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Goal name cannot be blank" }
        require(targetAmountPaise > 0L) { "Target amount must be greater than zero" }
        val safeInitial = initialSavedAmountPaise.coerceAtLeast(0L)

        val now = System.currentTimeMillis()
        val initialStatus = if (safeInitial >= targetAmountPaise) SavingsGoalStatus.COMPLETED else SavingsGoalStatus.ACTIVE

        val entity = SavingsGoalEntity(
            name = name.trim(),
            description = description?.trim()?.ifBlank { null },
            targetAmountPaise = targetAmountPaise,
            targetDate = targetDate,
            category = category,
            imageUri = imageUri,
            colorHex = colorHex,
            priority = priority,
            status = initialStatus,
            currentSavedPaise = safeInitial,
            createdAt = now,
            updatedAt = now
        )

        val goalId = savingsGoalDao.insertGoal(entity)

        if (safeInitial > 0L) {
            val initialEntry = SavingsEntryEntity(
                goalId = goalId,
                amountPaise = safeInitial,
                entryType = SavingsEntryType.DEPOSIT,
                entryDate = now,
                createdAt = now,
                description = "Initial savings deposit",
                paymentMethod = null,
                note = "Starting balance"
            )
            savingsEntryDao.insertEntry(initialEntry)
        }

        goalId
    }

    override suspend fun updateGoal(goal: SavingsGoal) = withContext(Dispatchers.IO) {
        require(goal.name.isNotBlank()) { "Goal name cannot be blank" }
        require(goal.targetAmountPaise > 0L) { "Target amount must be greater than zero" }

        val existing = savingsGoalDao.getGoalById(goal.id) ?: return@withContext
        val updatedStatus = if (existing.currentSavedPaise >= goal.targetAmountPaise && goal.status == SavingsGoalStatus.ACTIVE) {
            SavingsGoalStatus.COMPLETED
        } else {
            goal.status
        }

        savingsGoalDao.updateGoal(
            existing.copy(
                name = goal.name.trim(),
                description = goal.description?.trim()?.ifBlank { null },
                targetAmountPaise = goal.targetAmountPaise,
                targetDate = goal.targetDate,
                category = goal.category,
                imageUri = goal.imageUri,
                colorHex = goal.colorHex,
                priority = goal.priority,
                status = updatedStatus,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteGoal(goalId: Long) = withContext(Dispatchers.IO) {
        savingsGoalDao.deleteGoalById(goalId)
    }

    override suspend fun setGoalStatus(goalId: Long, status: SavingsGoalStatus) = withContext(Dispatchers.IO) {
        savingsGoalDao.updateGoalStatus(goalId, status)
    }

    override suspend fun addDeposit(
        goalId: Long,
        amountPaise: Long,
        entryDate: Long,
        description: String,
        paymentMethod: String?,
        note: String?
    ): Long = withContext(Dispatchers.IO) {
        require(amountPaise > 0L) { "Deposit amount must be greater than zero" }
        require(description.isNotBlank()) { "Deposit description is required" }

        val goal = savingsGoalDao.getGoalById(goalId)
            ?: throw IllegalArgumentException("Goal not found: $goalId")

        val now = System.currentTimeMillis()
        val entry = SavingsEntryEntity(
            goalId = goalId,
            amountPaise = amountPaise,
            entryType = SavingsEntryType.DEPOSIT,
            entryDate = entryDate,
            createdAt = now,
            description = description.trim(),
            paymentMethod = paymentMethod?.trim()?.ifBlank { null },
            note = note?.trim()?.ifBlank { null }
        )

        val entryId = savingsEntryDao.insertEntry(entry)

        // Safe arithmetic update
        val newBalance = SafeMath.safeAdd(goal.currentSavedPaise, amountPaise)
        val newStatus = if (newBalance >= goal.targetAmountPaise && goal.status == SavingsGoalStatus.ACTIVE) {
            SavingsGoalStatus.COMPLETED
        } else {
            goal.status
        }

        savingsGoalDao.updateGoal(
            goal.copy(
                currentSavedPaise = newBalance,
                status = newStatus,
                updatedAt = now
            )
        )

        entryId
    }

    override suspend fun withdraw(
        goalId: Long,
        amountPaise: Long,
        entryDate: Long,
        description: String,
        paymentMethod: String?,
        note: String?
    ): Long = withContext(Dispatchers.IO) {
        require(amountPaise > 0L) { "Withdrawal amount must be greater than zero" }
        require(description.isNotBlank()) { "Withdrawal description is required" }

        val goal = savingsGoalDao.getGoalById(goalId)
            ?: throw IllegalArgumentException("Goal not found: $goalId")

        require(amountPaise <= goal.currentSavedPaise) {
            "Withdrawal amount (${amountPaise / 100}) cannot exceed available saved balance (${goal.currentSavedPaise / 100})"
        }

        val now = System.currentTimeMillis()
        val entry = SavingsEntryEntity(
            goalId = goalId,
            amountPaise = amountPaise,
            entryType = SavingsEntryType.WITHDRAWAL,
            entryDate = entryDate,
            createdAt = now,
            description = description.trim(),
            paymentMethod = paymentMethod?.trim()?.ifBlank { null },
            note = note?.trim()?.ifBlank { null }
        )

        val entryId = savingsEntryDao.insertEntry(entry)

        val newBalance = SafeMath.safeSubtract(goal.currentSavedPaise, amountPaise).coerceAtLeast(0L)
        val newStatus = if (newBalance < goal.targetAmountPaise && goal.status == SavingsGoalStatus.COMPLETED) {
            SavingsGoalStatus.ACTIVE
        } else {
            goal.status
        }

        savingsGoalDao.updateGoal(
            goal.copy(
                currentSavedPaise = newBalance,
                status = newStatus,
                updatedAt = now
            )
        )

        entryId
    }

    override suspend fun updateEntry(entry: SavingsEntry) = withContext(Dispatchers.IO) {
        require(entry.amountPaise > 0L) { "Amount must be greater than zero" }
        require(entry.description.isNotBlank()) { "Description is required" }

        val existing = savingsEntryDao.getEntryById(entry.id) ?: return@withContext
        savingsEntryDao.updateEntry(
            existing.copy(
                amountPaise = entry.amountPaise,
                entryType = entry.entryType,
                entryDate = entry.entryDate,
                description = entry.description.trim(),
                paymentMethod = entry.paymentMethod?.trim()?.ifBlank { null },
                note = entry.note?.trim()?.ifBlank { null }
            )
        )
        reconcileGoalBalance(entry.goalId)
    }

    override suspend fun deleteEntry(entryId: Long, goalId: Long) = withContext(Dispatchers.IO) {
        savingsEntryDao.deleteEntryById(entryId)
        reconcileGoalBalance(goalId)
    }

    override fun getEntriesForGoal(goalId: Long): Flow<List<SavingsEntry>> {
        return savingsEntryDao.getEntriesForGoal(goalId).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getEntriesForGoalList(goalId: Long): List<SavingsEntry> = withContext(Dispatchers.IO) {
        savingsEntryDao.getEntriesForGoalList(goalId).map { it.toDomain() }
    }

    override fun getAllEntries(): Flow<List<SavingsEntry>> {
        return savingsEntryDao.getAllEntries().map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllEntriesList(): List<SavingsEntry> = withContext(Dispatchers.IO) {
        savingsEntryDao.getAllEntriesList().map { it.toDomain() }
    }

    override fun getEntriesForGoalByDateRange(
        goalId: Long,
        startDate: Long,
        endDate: Long
    ): Flow<List<SavingsEntry>> {
        return savingsEntryDao.getEntriesForGoalByDateRange(goalId, startDate, endDate).map { list ->
            list.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getDailySavingsGroups(goalId: Long): Flow<List<DailySavingsGroup>> {
        return savingsEntryDao.getEntriesForGoal(goalId).map { entries ->
            groupEntriesByDate(entries.map { it.toDomain() })
        }.flowOn(Dispatchers.IO)
    }

    override fun getOverviewSummary(): Flow<SavingsOverviewSummary> {
        return combine(
            savingsGoalDao.getAllGoals(),
            savingsEntryDao.getAllEntries()
        ) { goals, entries ->
            val activeGoals = goals.filter { it.status == SavingsGoalStatus.ACTIVE }
            val completedGoals = goals.filter { it.status == SavingsGoalStatus.COMPLETED }

            var totalSavedPaise = 0L
            var totalTargetPaise = 0L
            for (g in activeGoals) {
                totalSavedPaise = SafeMath.safeAdd(totalSavedPaise, g.currentSavedPaise)
                totalTargetPaise = SafeMath.safeAdd(totalTargetPaise, g.targetAmountPaise)
            }

            // Calculate savings added this calendar month across all active goals
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val monthStartMillis = cal.timeInMillis

            cal.add(Calendar.MONTH, 1)
            val monthEndMillis = cal.timeInMillis

            var monthSavedPaise = 0L
            val activeGoalIds = activeGoals.map { it.id }.toSet()
            for (e in entries) {
                if (e.goalId in activeGoalIds && e.entryDate in monthStartMillis until monthEndMillis) {
                    if (e.entryType == SavingsEntryType.DEPOSIT) {
                        monthSavedPaise = SafeMath.safeAdd(monthSavedPaise, e.amountPaise)
                    } else if (e.entryType == SavingsEntryType.WITHDRAWAL) {
                        monthSavedPaise = SafeMath.safeSubtract(monthSavedPaise, e.amountPaise)
                    }
                }
            }
            if (monthSavedPaise < 0L) monthSavedPaise = 0L

            val overallProgress = if (totalTargetPaise > 0L) {
                ((totalSavedPaise.toDouble() / totalTargetPaise.toDouble()) * 100.0).toFloat().coerceIn(0f, 100f)
            } else 0f

            SavingsOverviewSummary(
                totalSavedPaise = totalSavedPaise,
                savingsAddedThisMonthPaise = monthSavedPaise,
                activeGoalsCount = activeGoals.size,
                completedGoalsCount = completedGoals.size,
                overallProgressPercentage = overallProgress
            )
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun reconcileGoalBalance(goalId: Long) = withContext(Dispatchers.IO) {
        val goal = savingsGoalDao.getGoalById(goalId) ?: return@withContext
        val entries = savingsEntryDao.getEntriesForGoalList(goalId)

        var reconciledBalance = 0L
        for (e in entries) {
            reconciledBalance = when (e.entryType) {
                SavingsEntryType.DEPOSIT -> SafeMath.safeAdd(reconciledBalance, e.amountPaise)
                SavingsEntryType.WITHDRAWAL -> SafeMath.safeSubtract(reconciledBalance, e.amountPaise)
            }
        }
        reconciledBalance = reconciledBalance.coerceAtLeast(0L)

        val updatedStatus = if (reconciledBalance >= goal.targetAmountPaise && goal.targetAmountPaise > 0L && goal.status == SavingsGoalStatus.ACTIVE) {
            SavingsGoalStatus.COMPLETED
        } else if (reconciledBalance < goal.targetAmountPaise && goal.status == SavingsGoalStatus.COMPLETED) {
            SavingsGoalStatus.ACTIVE
        } else {
            goal.status
        }

        savingsGoalDao.updateGoal(
            goal.copy(
                currentSavedPaise = reconciledBalance,
                status = updatedStatus,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun groupEntriesByDate(entries: List<SavingsEntry>): List<DailySavingsGroup> {
        val grouped = LinkedHashMap<Long, MutableList<SavingsEntry>>()

        for (e in entries) {
            val normalizedDay = normalizeToStartOfDay(e.entryDate)
            grouped.getOrPut(normalizedDay) { mutableListOf() }.add(e)
        }

        return grouped.map { (dateMillis, dayEntries) ->
            var dayDeposits = 0L
            var dayWithdrawals = 0L
            for (entry in dayEntries) {
                when (entry.entryType) {
                    SavingsEntryType.DEPOSIT -> dayDeposits = SafeMath.safeAdd(dayDeposits, entry.amountPaise)
                    SavingsEntryType.WITHDRAWAL -> dayWithdrawals = SafeMath.safeAdd(dayWithdrawals, entry.amountPaise)
                }
            }
            val netDailySaved = SafeMath.safeSubtract(dayDeposits, dayWithdrawals)

            DailySavingsGroup(
                dateMillis = dateMillis,
                totalSavedPaise = netDailySaved,
                totalDepositsPaise = dayDeposits,
                totalWithdrawalsPaise = dayWithdrawals,
                entries = dayEntries.sortedByDescending { it.createdAt }
            )
        }.sortedByDescending { it.dateMillis }
    }

    private fun normalizeToStartOfDay(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun SavingsGoalEntity.toDomain() = SavingsGoal(
        id = id,
        name = name,
        description = description,
        targetAmountPaise = targetAmountPaise,
        targetDate = targetDate,
        category = category,
        imageUri = imageUri,
        colorHex = colorHex,
        priority = priority,
        status = status,
        currentSavedPaise = currentSavedPaise,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun SavingsEntryEntity.toDomain() = SavingsEntry(
        id = id,
        goalId = goalId,
        amountPaise = amountPaise,
        entryType = entryType,
        entryDate = entryDate,
        createdAt = createdAt,
        description = description,
        paymentMethod = paymentMethod,
        note = note
    )
}
