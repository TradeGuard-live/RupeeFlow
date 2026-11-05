package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.FriendGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupMemberEntity
import kotlinx.coroutines.flow.Flow

// DAO for friend groups and memberships
@Dao
interface FriendGroupDao {

    @Query("SELECT * FROM friend_groups ORDER BY name ASC")
    fun getAllGroups(): Flow<List<FriendGroupEntity>>

    @Query("SELECT * FROM friend_groups ORDER BY name ASC")
    suspend fun getAllGroupsList(): List<FriendGroupEntity>

    @Query("SELECT * FROM friend_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): FriendGroupEntity?

    @Query("SELECT * FROM friend_groups WHERE id = :id")
    fun getGroupByIdFlow(id: Long): Flow<FriendGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: FriendGroupEntity): Long

    @Update
    suspend fun updateGroup(group: FriendGroupEntity)

    @Delete
    suspend fun deleteGroup(group: FriendGroupEntity)

    @Query("DELETE FROM friend_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Query("SELECT * FROM friend_group_members WHERE groupId = :groupId")
    fun getMembersForGroup(groupId: Long): Flow<List<FriendGroupMemberEntity>>

    @Query("SELECT * FROM friend_group_members WHERE groupId = :groupId")
    suspend fun getMembersForGroupList(groupId: Long): List<FriendGroupMemberEntity>

    @Query("SELECT * FROM friend_group_members")
    fun getAllGroupMembers(): Flow<List<FriendGroupMemberEntity>>

    @Query("SELECT * FROM friend_group_members")
    suspend fun getAllGroupMembersList(): List<FriendGroupMemberEntity>

    @Query("SELECT friendId FROM friend_group_members WHERE groupId = :groupId")
    fun getMemberFriendIdsFlow(groupId: Long): Flow<List<Long>>

    @Query("SELECT friendId FROM friend_group_members WHERE groupId = :groupId")
    suspend fun getMemberFriendIds(groupId: Long): List<Long>

    @Query("SELECT g.* FROM friend_groups g INNER JOIN friend_group_members m ON g.id = m.groupId WHERE m.friendId = :friendId ORDER BY g.name ASC")
    fun getGroupsForFriend(friendId: Long): Flow<List<FriendGroupEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMember(member: FriendGroupMemberEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<FriendGroupMemberEntity>)

    @Query("DELETE FROM friend_group_members WHERE groupId = :groupId AND friendId = :friendId")
    suspend fun removeMember(groupId: Long, friendId: Long)

    @Query("DELETE FROM friend_group_members WHERE groupId = :groupId")
    suspend fun removeAllMembersForGroup(groupId: Long)

    @Transaction
    suspend fun setGroupMembers(groupId: Long, friendIds: List<Long>) {
        removeAllMembersForGroup(groupId)
        if (friendIds.isNotEmpty()) {
            val distinctMembers = friendIds.distinct().map {
                FriendGroupMemberEntity(groupId = groupId, friendId = it)
            }
            insertMembers(distinctMembers)
        }
    }
}

