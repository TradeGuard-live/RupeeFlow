package com.naman.rupeeflow.voice.execution

import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.voice.model.VoiceActionPreview
import com.naman.rupeeflow.voice.model.VoiceCommandResult
import com.naman.rupeeflow.voice.model.VoiceLanguage
import java.util.Collections

/**
 * Idempotently executes approved financial voice previews against
 * TransactionRepository and SettlementRepository.
 */
class VoiceActionExecutor(
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository
) {
    // In-memory set of executed token hashes to guarantee idempotency across recompositions or double clicks
    private val executedTokens = Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun executePreview(
        preview: VoiceActionPreview,
        language: VoiceLanguage,
        executionToken: String
    ): VoiceCommandResult {
        if (!executedTokens.add(executionToken)) {
            // Already processed this exact execution token
            return VoiceCommandResult.Success(
                message = "Already processed.",
                spokenMessage = "Action already processed.",
                actionType = "IDEMPOTENT_DUPLICATE"
            )
        }

        return try {
            when (preview) {
                is VoiceActionPreview.TransactionPreview -> {
                    if (preview.isDelete) {
                        transactionRepository.deleteTransaction(preview.transaction.id)
                        val (text, spoken) = VoiceResponseGenerator.transactionDeletedSuccess(
                            preview.transaction.title,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "DELETE_TRANSACTION",
                            entityId = preview.transaction.id
                        )
                    } else {
                        val savedId = transactionRepository.saveTransaction(preview.transaction)
                        val savedTxn = preview.transaction.copy(id = savedId)
                        val (text, spoken) = VoiceResponseGenerator.transactionSavedSuccess(savedTxn, language)
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = if (preview.isEdit) "EDIT_TRANSACTION" else "CREATE_TRANSACTION",
                            entityId = savedId
                        )
                    }
                }
                is VoiceActionPreview.SettlementPreview -> {
                    if (preview.isDelete) {
                        settlementRepository.deleteSettlement(preview.settlement)
                        VoiceCommandResult.Success(
                            message = "Settlement deleted.",
                            spokenMessage = "Settlement deleted.",
                            actionType = "DELETE_SETTLEMENT",
                            entityId = preview.settlement.id
                        )
                    } else if (preview.isReverse) {
                        settlementRepository.reverseSettlement(preview.settlement.id)
                        val (text, spoken) = VoiceResponseGenerator.settlementReversedSuccess(
                            preview.friendName,
                            preview.settlement.amountPaise,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "REVERSE_SETTLEMENT",
                            entityId = preview.settlement.id
                        )
                    } else if (preview.originalSettlement != null) {
                        settlementRepository.updateSettlement(preview.settlement)
                        val (text, spoken) = VoiceResponseGenerator.settlementSavedSuccess(
                            preview.settlement,
                            preview.friendName,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "EDIT_SETTLEMENT",
                            entityId = preview.settlement.id
                        )
                    } else {
                        val id = settlementRepository.recordSettlement(preview.settlement)
                        val (text, spoken) = VoiceResponseGenerator.settlementSavedSuccess(
                            preview.settlement.copy(id = id),
                            preview.friendName,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "CREATE_SETTLEMENT",
                            entityId = id
                        )
                    }
                }
                is VoiceActionPreview.QueryResultPreview -> {
                    VoiceCommandResult.QueryResult(preview)
                }
            }
        } catch (e: Exception) {
            executedTokens.remove(executionToken) // Allow retry on failure
            VoiceCommandResult.Error(
                errorMessage = "Execution failed: ${e.localizedMessage ?: "Unknown error"}",
                spokenErrorMessage = "Action could not be completed."
            )
        }
    }
}
