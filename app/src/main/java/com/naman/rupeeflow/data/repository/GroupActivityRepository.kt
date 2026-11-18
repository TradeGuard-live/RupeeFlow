package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.GroupActivityDao
import com.naman.rupeeflow.data.local.entity.GroupActivityEntity
import com.naman.rupeeflow.domain.model.GroupActivity
import com.naman.rupeeflow.domain.model.GroupActivityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface GroupActivityRepository {
    fun getAllActivities(): Flow<List<GroupActivity>>
    fun getActivitiesForGroup(groupId: Long): Flow<List<GroupActivity>>
    suspend fun getActivitiesForGroupList(groupId: Long): List<GroupActivity>
    suspend fun recordActivity(
        groupId: Long,
        activityType: GroupActivityType,
        actorName: String,
        description: String,
        amountPaise: Long? = null,
        targetId: Long? = null
    ): Long
    suspend fun deleteActivitiesForGroup(groupId: Long)
}

class GroupActivityRepositoryImpl(
    private val groupActivityDao: GroupActivityDao
) : GroupActivityRepository {

    override fun getAllActivities(): Flow<List<GroupActivity>> {
        return groupActivityDao.getAllActivities().map { list -> list.map { it.toDomain() } }
    }

    override fun getActivitiesForGroup(groupId: Long): Flow<List<GroupActivity>> {
        return groupActivityDao.getActivitiesForGroup(groupId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getActivitiesForGroupList(groupId: Long): List<GroupActivity> {
        return groupActivityDao.getActivitiesForGroupList(groupId).map { it.toDomain() }
    }

    override suspend fun recordActivity(
        groupId: Long,
        activityType: GroupActivityType,
        actorName: String,
        description: String,
        amountPaise: Long?,
        targetId: Long?
    ): Long {
        val entity = GroupActivityEntity(
            groupId = groupId,
            activityType = activityType.name,
            actorName = actorName.trim().ifBlank { "User" },
            description = description.trim(),
            amountPaise = amountPaise,
            targetId = targetId,
            timestamp = System.currentTimeMillis()
        )
        return groupActivityDao.insertActivity(entity)
    }

    override suspend fun deleteActivitiesForGroup(groupId: Long) {
        groupActivityDao.deleteActivitiesForGroup(groupId)
    }

    private fun GroupActivityEntity.toDomain(): GroupActivity {
        val type = runCatching { GroupActivityType.valueOf(activityType) }
            .getOrDefault(GroupActivityType.EXPENSE_CREATED)
        return GroupActivity(
            id = id,
            groupId = groupId,
            activityType = type,
            actorName = actorName,
            description = description,
            amountPaise = amountPaise,
            targetId = targetId,
            timestamp = timestamp
        )
    }
}

