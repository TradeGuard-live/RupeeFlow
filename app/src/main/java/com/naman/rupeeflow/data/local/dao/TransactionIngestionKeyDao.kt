package com.naman.rupeeflow.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import com.naman.rupeeflow.data.local.entity.TransactionIngestionKeyEntity

@Dao
interface TransactionIngestionKeyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun claim(entity: TransactionIngestionKeyEntity): Long
}
