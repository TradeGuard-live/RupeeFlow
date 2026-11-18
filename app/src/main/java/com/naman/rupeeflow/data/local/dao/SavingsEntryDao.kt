package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.SavingsEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsEntryDao {

    @Query("SELECT * FROM savings_entries WHERE goalId = :goalId ORDER BY entryDate DESC, id DESC")
    fun getEntriesForGoal(goalId: Long): Flow<List<SavingsEntryEntity>>

    @Query("SELECT * FROM savings_entries WHERE goalId = :goalId ORDER BY entryDate DESC, id DESC")
    suspend fun getEntriesForGoalList(goalId: Long): List<SavingsEntryEntity>

    @Query("SELECT * FROM savings_entries ORDER BY entryDate DESC, id DESC")
    fun getAllEntries(): Flow<List<SavingsEntryEntity>>

    @Query("SELECT * FROM savings_entries ORDER BY entryDate DESC, id DESC")
    suspend fun getAllEntriesList(): List<SavingsEntryEntity>

    @Query("SELECT * FROM savings_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): SavingsEntryEntity?

    @Query("SELECT * FROM savings_entries WHERE goalId = :goalId AND entryDate >= :startDate AND entryDate <= :endDate ORDER BY entryDate DESC, id DESC")
    fun getEntriesForGoalByDateRange(goalId: Long, startDate: Long, endDate: Long): Flow<List<SavingsEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: SavingsEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<SavingsEntryEntity>): List<Long>

    @Update
    suspend fun updateEntry(entry: SavingsEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: SavingsEntryEntity)

    @Query("DELETE FROM savings_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM savings_entries WHERE goalId = :goalId")
    suspend fun deleteEntriesForGoal(goalId: Long)

    @Query("SELECT SUM(CASE WHEN entryType = 'DEPOSIT' THEN amountPaise ELSE -amountPaise END) FROM savings_entries WHERE goalId = :goalId")
    suspend fun getGoalNetBalance(goalId: Long): Long?

    @Query("DELETE FROM savings_entries")
    suspend fun deleteAllEntries()
}
