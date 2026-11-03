package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.FriendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FriendDao {
    @Query("SELECT * FROM friends WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllActiveFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends ORDER BY name ASC")
    fun getAllFriends(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends ORDER BY name ASC")
    suspend fun getAllFriendsList(): List<FriendEntity>

    @Query("SELECT * FROM friends WHERE id = :id")
    suspend fun getFriendById(id: Long): FriendEntity?

    @Query("SELECT * FROM friends WHERE id = :id")
    fun getFriendByIdFlow(id: Long): Flow<FriendEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: FriendEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriends(friends: List<FriendEntity>)

    @Update
    suspend fun updateFriend(friend: FriendEntity)

    @Delete
    suspend fun deleteFriend(friend: FriendEntity)

    @Query("UPDATE friends SET isArchived = 1 WHERE id = :id")
    suspend fun archiveFriend(id: Long)

    // ✅ Unarchive friend
    @Query("UPDATE friends SET isArchived = 0 WHERE id = :id")
    suspend fun unarchiveFriend(id: Long)

    // ✅ Reference checks to prevent accidental historical corruption
    @Query("SELECT COUNT(*) FROM transactions WHERE paidByFriendId = :friendId")
    suspend fun countTransactionsPaidByFriend(friendId: Long): Int

    @Query("SELECT COUNT(*) FROM transaction_participants WHERE friendId = :friendId")
    suspend fun countParticipantsForFriend(friendId: Long): Int

    @Query("SELECT COUNT(*) FROM settlements WHERE payerId = :friendId OR receiverId = :friendId")
    suspend fun countSettlementsForFriend(friendId: Long): Int
}
