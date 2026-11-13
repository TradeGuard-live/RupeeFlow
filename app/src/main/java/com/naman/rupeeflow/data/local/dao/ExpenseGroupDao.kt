package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.ExpenseGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseGroupDao {

    @Query("SELECT * FROM expense_groups ORDER BY isArchived ASC, name ASC")
    fun getAllExpenseGroups(): Flow<List<ExpenseGroupEntity>>

    @Query("SELECT * FROM expense_groups WHERE isArchived = 0 ORDER BY name ASC")
    fun getActiveExpenseGroups(): Flow<List<ExpenseGroupEntity>>

    @Query("SELECT * FROM expense_groups ORDER BY isArchived ASC, name ASC")
    suspend fun getAllExpenseGroupsList(): List<ExpenseGroupEntity>

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    suspend fun getExpenseGroupById(id: Long): ExpenseGroupEntity?

    @Query("SELECT * FROM expense_groups WHERE id = :id")
    fun getExpenseGroupByIdFlow(id: Long): Flow<ExpenseGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenseGroup(group: ExpenseGroupEntity): Long

    @Update
    suspend fun updateExpenseGroup(group: ExpenseGroupEntity)

    @Delete
    suspend fun deleteExpenseGroup(group: ExpenseGroupEntity)

    @Query("DELETE FROM expense_groups WHERE id = :id")
    suspend fun deleteExpenseGroupById(id: Long)

    @Query("UPDATE expense_groups SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setExpenseGroupArchived(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())
}

