package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.SettlementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements ORDER BY settlementDate DESC, id DESC")
    fun getAllSettlements(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements ORDER BY settlementDate DESC, id DESC")
    suspend fun getAllSettlementsList(): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE (payerId = :friendId OR receiverId = :friendId) ORDER BY settlementDate DESC")
    fun getSettlementsForFriend(friendId: Long): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE (payerId = :friendId OR receiverId = :friendId) ORDER BY settlementDate DESC")
    suspend fun getSettlementsForFriendList(friendId: Long): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY settlementDate DESC, id DESC")
    fun getSettlementsForGroup(groupId: Long): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY settlementDate DESC, id DESC")
    suspend fun getSettlementsForGroupList(groupId: Long): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun getSettlementById(id: Long): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE id = :id")
    fun getSettlementByIdFlow(id: Long): Flow<SettlementEntity?>

    @Query("SELECT * FROM settlements WHERE upiTransactionRef = :txnRef LIMIT 1")
    suspend fun getSettlementByUpiTransactionRef(txnRef: String): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE referenceNumber = :refNum LIMIT 1")
    suspend fun getSettlementByReferenceNumber(refNum: String): SettlementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlements(settlements: List<SettlementEntity>)

    @Update
    suspend fun updateSettlement(settlement: SettlementEntity)

    @Query("UPDATE settlements SET isReversed = 1 WHERE id = :id AND isReversed = 0")
    suspend fun reverseSettlement(id: Long): Int

    @Query("UPDATE settlements SET isReversed = 0 WHERE id = :id AND isReversed = 1")
    suspend fun unreverseSettlement(id: Long): Int

    @Delete
    suspend fun deleteSettlement(settlement: SettlementEntity)
}
