package com.naman.rupeeflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.naman.rupeeflow.data.local.entity.ExpenseAllocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseAllocationDao {

    @Query("SELECT * FROM expense_allocations WHERE transactionId = :transactionId ORDER BY allocationDate ASC")
    suspend fun getAllocationsForTransaction(transactionId: Long): List<ExpenseAllocationEntity>

    @Query("SELECT * FROM expense_allocations WHERE transactionId = :transactionId ORDER BY allocationDate ASC")
    fun getAllocationsForTransactionFlow(transactionId: Long): Flow<List<ExpenseAllocationEntity>>

    @Query("SELECT * FROM expense_allocations WHERE allocationDate BETWEEN :startMillis AND :endMillis ORDER BY allocationDate ASC")
    suspend fun getAllocationsBetween(startMillis: Long, endMillis: Long): List<ExpenseAllocationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<ExpenseAllocationEntity>)

    @Query("DELETE FROM expense_allocations WHERE transactionId = :transactionId")
    suspend fun deleteAllocationsForTransaction(transactionId: Long)

    @Query("DELETE FROM expense_allocations")
    suspend fun clearAllAllocations()
}

