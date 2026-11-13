package com.naman.rupeeflow.data.local.dao

import androidx.room.*
import com.naman.rupeeflow.data.local.entity.ReportDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDraftDao {

    @Query("SELECT * FROM report_drafts ORDER BY updatedAt DESC")
    fun getAllDrafts(): Flow<List<ReportDraftEntity>>

    @Query("SELECT * FROM report_drafts ORDER BY updatedAt DESC")
    suspend fun getAllDraftsList(): List<ReportDraftEntity>

    @Query("SELECT * FROM report_drafts WHERE id = :id")
    suspend fun getDraftById(id: Long): ReportDraftEntity?

    @Query("SELECT * FROM report_drafts WHERE id = :id")
    fun getDraftByIdFlow(id: Long): Flow<ReportDraftEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDraft(draft: ReportDraftEntity): Long

    @Update
    suspend fun updateDraft(draft: ReportDraftEntity)

    @Delete
    suspend fun deleteDraft(draft: ReportDraftEntity)

    @Query("DELETE FROM report_drafts WHERE id = :id")
    suspend fun deleteDraftById(id: Long)
}

