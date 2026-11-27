package com.naman.rupeeflow.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction_ingestion_keys")
data class TransactionIngestionKeyEntity(
    @PrimaryKey val key: String,
    val createdAt: Long = System.currentTimeMillis()
)
