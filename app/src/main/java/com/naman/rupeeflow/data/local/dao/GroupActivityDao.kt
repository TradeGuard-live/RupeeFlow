package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.GroupActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupActivityDao {

    @Query("SELECT * FROM group_activities ORDER BY timestamp DESC, id DESC")
    fun getAllActivities(): Flow<List<GroupActivityEntity>>

    @Query("SELECT * FROM group_activities ORDER BY timestamp DESC, id DESC")
    suspend fun getAllActivitiesList(): List<GroupActivityEntity>

    @Query("SELECT * FROM group_activities WHERE groupId = :groupId ORDER BY timestamp DESC, id DESC")
    fun getActivitiesForGroup(groupId: Long): Flow<List<GroupActivityEntity>>

    @Query("SELECT * FROM group_activities WHERE groupId = :groupId ORDER BY timestamp DESC, id DESC")
    suspend fun getActivitiesForGroupList(groupId: Long): List<GroupActivityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: GroupActivityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<GroupActivityEntity>)

    @Query("DELETE FROM group_activities WHERE groupId = :groupId")
    suspend fun deleteActivitiesForGroup(groupId: Long)

    @Query("DELETE FROM group_activities WHERE id = :id")
    suspend fun deleteActivityById(id: Long)
}

