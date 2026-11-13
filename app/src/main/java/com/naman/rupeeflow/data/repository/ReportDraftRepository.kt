package com.naman.rupeeflow.data.repository

import com.naman.rupeeflow.data.local.dao.ReportDraftDao
import com.naman.rupeeflow.data.local.entity.ReportDraftEntity
import com.naman.rupeeflow.domain.model.ReportDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray

interface ReportDraftRepository {
    fun getAllDrafts(): Flow<List<ReportDraft>>
    suspend fun getAllDraftsList(): List<ReportDraft>
    suspend fun getDraftById(id: Long): ReportDraft?
    fun getDraftByIdFlow(id: Long): Flow<ReportDraft?>
    suspend fun saveDraft(draft: ReportDraft): Long
    suspend fun deleteDraft(id: Long)
}

class ReportDraftRepositoryImpl(
    private val reportDraftDao: ReportDraftDao
) : ReportDraftRepository {

    override fun getAllDrafts(): Flow<List<ReportDraft>> {
        return reportDraftDao.getAllDrafts().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getAllDraftsList(): List<ReportDraft> {
        return reportDraftDao.getAllDraftsList().map { it.toDomain() }
    }

    override suspend fun getDraftById(id: Long): ReportDraft? {
        return reportDraftDao.getDraftById(id)?.toDomain()
    }

    override fun getDraftByIdFlow(id: Long): Flow<ReportDraft?> {
        return reportDraftDao.getDraftByIdFlow(id).map { it?.toDomain() }
    }

    override suspend fun saveDraft(draft: ReportDraft): Long {
        val trimmed = draft.name.trim()
        require(trimmed.isNotBlank()) { "Report draft name cannot be blank" }
        val now = System.currentTimeMillis()
        val jsonArray = JSONArray()
        draft.excludedTransactionIds.forEach { jsonArray.put(it) }

        val entity = ReportDraftEntity(
            id = draft.id,
            name = trimmed,
            periodType = draft.periodType,
            startDate = draft.startDate,
            endDate = draft.endDate,
            excludedTransactionIdsJson = jsonArray.toString(),
            createdAt = if (draft.createdAt > 0L) draft.createdAt else now,
            updatedAt = now
        )

        return if (draft.id > 0L) {
            reportDraftDao.updateDraft(entity)
            draft.id
        } else {
            reportDraftDao.insertDraft(entity)
        }
    }

    override suspend fun deleteDraft(id: Long) {
        reportDraftDao.deleteDraftById(id)
    }

    private fun ReportDraftEntity.toDomain(): ReportDraft {
        val excludedSet = mutableSetOf<Long>()
        try {
            val jsonArray = JSONArray(excludedTransactionIdsJson)
            for (i in 0 until jsonArray.length()) {
                excludedSet.add(jsonArray.getLong(i))
            }
        } catch (_: Exception) {
            // Safe fallback if JSON parsing fails
        }

        return ReportDraft(
            id = id,
            name = name,
            periodType = periodType,
            startDate = startDate,
            endDate = endDate,
            excludedTransactionIds = excludedSet,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}

