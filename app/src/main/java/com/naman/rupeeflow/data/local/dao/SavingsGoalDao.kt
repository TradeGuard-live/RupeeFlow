package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.SavingsGoalEntity
import com.naman.rupeeflow.domain.model.SavingsGoalStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun getActiveGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE status = :status ORDER BY createdAt DESC")
    fun getGoalsByStatus(status: SavingsGoalStatus): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    suspend fun getAllGoalsList(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getGoalById(id: Long): SavingsGoalEntity?

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    fun getGoalByIdFlow(id: Long): Flow<SavingsGoalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<SavingsGoalEntity>): List<Long>

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoalById(id: Long)

    @Query("UPDATE savings_goals SET currentSavedPaise = :newSavedPaise, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGoalSavedAmount(id: Long, newSavedPaise: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE savings_goals SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateGoalStatus(id: Long, status: SavingsGoalStatus, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()
}
