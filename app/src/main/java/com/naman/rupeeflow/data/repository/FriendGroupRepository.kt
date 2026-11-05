package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.FriendGroupDao
import com.naman.rupeeflow.data.local.entity.FriendGroupEntity
import com.naman.rupeeflow.data.local.entity.FriendGroupMemberEntity
import com.naman.rupeeflow.domain.model.FriendGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface FriendGroupRepository {
    fun getAllGroups(): Flow<List<FriendGroup>>
    suspend fun getAllGroupsList(): List<FriendGroup>
    suspend fun getGroupById(id: Long): FriendGroup?
    fun getGroupByIdFlow(id: Long): Flow<FriendGroup?>
    suspend fun createGroup(name: String, iconName: String? = null, colorHex: String? = null, friendIds: List<Long> = emptyList()): Long
    suspend fun updateGroup(group: FriendGroup, friendIds: List<Long>? = null)
    suspend fun deleteGroup(groupId: Long)
    suspend fun addFriendToGroup(groupId: Long, friendId: Long)
    suspend fun removeFriendFromGroup(groupId: Long, friendId: Long)
    suspend fun setGroupMembers(groupId: Long, friendIds: List<Long>)
    fun getGroupsForFriend(friendId: Long): Flow<List<FriendGroup>>
}

class FriendGroupRepositoryImpl(
    private val friendGroupDao: FriendGroupDao
) : FriendGroupRepository {

    override fun getAllGroups(): Flow<List<FriendGroup>> {
        return combine(
            friendGroupDao.getAllGroups(),
            friendGroupDao.getAllGroupMembers()
        ) { groups, members ->
            val membersByGroup = members.groupBy { it.groupId }
            groups.map { entity ->
                val groupMembers = membersByGroup[entity.id]?.map { it.friendId } ?: emptyList()
                entity.toDomain(groupMembers)
            }
        }
    }

    override suspend fun getAllGroupsList(): List<FriendGroup> {
        val groups = friendGroupDao.getAllGroupsList()
        val members = friendGroupDao.getAllGroupMembersList()
        val membersByGroup = members.groupBy { it.groupId }
        return groups.map { entity ->
            val groupMembers = membersByGroup[entity.id]?.map { it.friendId } ?: emptyList()
            entity.toDomain(groupMembers)
        }
    }

    override suspend fun getGroupById(id: Long): FriendGroup? {
        val entity = friendGroupDao.getGroupById(id) ?: return null
        val members = friendGroupDao.getMemberFriendIds(id)
        return entity.toDomain(members)
    }

    override fun getGroupByIdFlow(id: Long): Flow<FriendGroup?> {
        return combine(
            friendGroupDao.getGroupByIdFlow(id),
            friendGroupDao.getMembersForGroup(id)
        ) { groupEntity, members ->
            groupEntity?.toDomain(members.map { it.friendId })
        }
    }

    override suspend fun createGroup(
        name: String,
        iconName: String?,
        colorHex: String?,
        friendIds: List<Long>
    ): Long {
        val now = System.currentTimeMillis()
        val entity = FriendGroupEntity(
            name = name.trim(),
            iconName = iconName,
            colorHex = colorHex,
            createdAt = now,
            updatedAt = now
        )
        val groupId = friendGroupDao.insertGroup(entity)
        if (friendIds.isNotEmpty()) {
            friendGroupDao.setGroupMembers(groupId, friendIds)
        }
        return groupId
    }

    override suspend fun updateGroup(group: FriendGroup, friendIds: List<Long>?) {
        val entity = FriendGroupEntity(
            id = group.id,
            name = group.name.trim(),
            iconName = group.iconName,
            colorHex = group.colorHex,
            createdAt = group.createdAt,
            updatedAt = System.currentTimeMillis()
        )
        friendGroupDao.updateGroup(entity)
        if (friendIds != null) {
            friendGroupDao.setGroupMembers(group.id, friendIds)
        }
    }

    override suspend fun deleteGroup(groupId: Long) {
        // Deletes group and cascades member associations; never touches friends or transactions
        friendGroupDao.deleteGroupById(groupId)
    }

    override suspend fun addFriendToGroup(groupId: Long, friendId: Long) {
        friendGroupDao.insertMember(
            FriendGroupMemberEntity(
                groupId = groupId,
                friendId = friendId,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun removeFriendFromGroup(groupId: Long, friendId: Long) {
        friendGroupDao.removeMember(groupId, friendId)
    }

    override suspend fun setGroupMembers(groupId: Long, friendIds: List<Long>) {
        friendGroupDao.setGroupMembers(groupId, friendIds)
    }

    override fun getGroupsForFriend(friendId: Long): Flow<List<FriendGroup>> {
        return friendGroupDao.getGroupsForFriend(friendId).map { list ->
            list.map { it.toDomain(emptyList()) }
        }
    }

    private fun FriendGroupEntity.toDomain(members: List<Long>) = FriendGroup(
        id = id,
        name = name,
        iconName = iconName,
        colorHex = colorHex,
        memberCount = members.size,
        memberFriendIds = members,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
