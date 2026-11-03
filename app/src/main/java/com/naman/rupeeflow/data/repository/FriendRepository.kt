package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.FriendDao
import com.naman.rupeeflow.data.local.entity.FriendEntity
import com.naman.rupeeflow.domain.model.Friend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface FriendRepository {
    fun getActiveFriends(): Flow<List<Friend>>
    fun getAllFriends(): Flow<List<Friend>>
    suspend fun getAllFriendsList(): List<Friend>
    suspend fun getFriendById(id: Long): Friend?
    fun getFriendByIdFlow(id: Long): Flow<Friend?>
    suspend fun addFriend(friend: Friend): Long
    suspend fun updateFriend(friend: Friend)
    suspend fun deleteFriend(friend: Friend)
    suspend fun archiveFriend(id: Long)
    // ✅ Safe friend lifecycle management
    suspend fun unarchiveFriend(id: Long)
    suspend fun isFriendReferenced(friendId: Long): Boolean
}

class FriendRepositoryImpl(
    private val friendDao: FriendDao
) : FriendRepository {

    override fun getActiveFriends(): Flow<List<Friend>> {
        return friendDao.getAllActiveFriends().map { list -> list.map { it.toDomain() } }
    }

    override fun getAllFriends(): Flow<List<Friend>> {
        return friendDao.getAllFriends().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAllFriendsList(): List<Friend> {
        return friendDao.getAllFriendsList().map { it.toDomain() }
    }

    override suspend fun getFriendById(id: Long): Friend? {
        return friendDao.getFriendById(id)?.toDomain()
    }

    override fun getFriendByIdFlow(id: Long): Flow<Friend?> {
        return friendDao.getFriendByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun addFriend(friend: Friend): Long {
        return friendDao.insertFriend(friend.toEntity())
    }

    override suspend fun updateFriend(friend: Friend) {
        friendDao.updateFriend(friend.toEntity())
    }

    override suspend fun deleteFriend(friend: Friend) {
        // ✅ Protect against identity collision:
        // If friend is referenced in transactions or settlements, archive instead of deleting
        // to ensure foreign keys never convert friend to ME (null).
        if (isFriendReferenced(friend.id)) {
            friendDao.archiveFriend(friend.id)
        } else {
            friendDao.deleteFriend(friend.toEntity())
        }
    }

    override suspend fun archiveFriend(id: Long) {
        friendDao.archiveFriend(id)
    }

    override suspend fun unarchiveFriend(id: Long) {
        friendDao.unarchiveFriend(id)
    }

    override suspend fun isFriendReferenced(friendId: Long): Boolean {
        val txnCount = friendDao.countTransactionsPaidByFriend(friendId)
        val partCount = friendDao.countParticipantsForFriend(friendId)
        val setCount = friendDao.countSettlementsForFriend(friendId)
        return (txnCount + partCount + setCount) > 0
    }

    private fun FriendEntity.toDomain() = Friend(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        email = email,
        notes = notes,
        createdAt = createdAt,
        isArchived = isArchived
    )

    private fun Friend.toEntity() = FriendEntity(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        email = email,
        notes = notes,
        createdAt = createdAt,
        isArchived = isArchived
    )
}
