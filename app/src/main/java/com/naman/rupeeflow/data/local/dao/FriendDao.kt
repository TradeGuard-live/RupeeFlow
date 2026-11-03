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
}
