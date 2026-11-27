package com.naman.rupeeflow.data.email

import androidx.room.withTransaction
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionIngestionKeyEntity
import java.util.Locale

/**
 * Atomic final gate for SMS, banking notifications and Gmail.
 * The unique reference claim and pending-row insert commit together.
 */
class TransactionIngestionCoordinator(private val database: RupeeFlowDatabase) {
    suspend fun insertIfNew(
        pending: PendingTransactionEntity,
        externalMessageId: String? = null
    ): Long? = database.withTransaction {
        val reference = normalizeReference(pending.upiReferenceNumber ?: pending.referenceNumber)

        if (!externalMessageId.isNullOrBlank()) {
            val messageClaim = database.transactionIngestionKeyDao().claim(
                TransactionIngestionKeyEntity("GMAIL:${externalMessageId.trim()}")
            )
            if (messageClaim == -1L) return@withTransaction null
        }

        if (reference != null) {
            val referenceClaim = database.transactionIngestionKeyDao().claim(
                TransactionIngestionKeyEntity("REF:$reference")
            )
            if (referenceClaim == -1L) return@withTransaction null
        }

        database.pendingTransactionDao().insert(pending)
    }

    companion object {
        fun normalizeReference(value: String?): String? = value
            ?.uppercase(Locale.ROOT)
            ?.filter(Char::isLetterOrDigit)
            ?.takeIf { it.length >= 6 }
    }
}
