package com.naman.rupeeflow.voice.execution

import com.naman.rupeeflow.data.repository.FriendGroupRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.voice.model.VoiceActionPreview
import com.naman.rupeeflow.voice.model.VoiceCommandResult
import com.naman.rupeeflow.voice.model.VoiceLanguage
import java.util.Collections

/**
 * Idempotently executes approved voice previews against
 * TransactionRepository, SettlementRepository, FriendRepository, and FriendGroupRepository.
 * Prevents duplicate operations across rapid clicks or recompositions.
 */
class VoiceActionExecutor(
    private val transactionRepository: TransactionRepository,
    private val settlementRepository: SettlementRepository,
    private val friendRepository: FriendRepository,
    private val friendGroupRepository: FriendGroupRepository
) {
    // In-memory set of executed token hashes to guarantee idempotency
    private val executedTokens = Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun executePreview(
        preview: VoiceActionPreview,
        language: VoiceLanguage,
        executionToken: String
    ): VoiceCommandResult {
        if (!executedTokens.add(executionToken)) {
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
                is VoiceActionPreview.FriendPreview -> {
                    if (preview.isDelete) {
                        friendRepository.deleteFriend(preview.friend)
                        val (text, spoken) = VoiceResponseGenerator.friendDeletedSuccess(
                            preview.friend.name,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "DELETE_FRIEND",
                            entityId = preview.friend.id
                        )
                    } else if (preview.isEdit) {
                        friendRepository.updateFriend(preview.friend)
                        val (text, spoken) = VoiceResponseGenerator.friendSavedSuccess(
                            preview.friend,
                            isEdit = true,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "EDIT_FRIEND",
                            entityId = preview.friend.id
                        )
                    } else if (preview.isSearch) {
                        VoiceCommandResult.Success(
                            message = "Friend: ${preview.friend.name}",
                            spokenMessage = "Friend ${preview.friend.name}",
                            actionType = "SEARCH_FRIEND",
                            entityId = preview.friend.id
                        )
                    } else {
                        val id = friendRepository.addFriend(preview.friend)
                        val (text, spoken) = VoiceResponseGenerator.friendSavedSuccess(
                            preview.friend.copy(id = id),
                            isEdit = false,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "CREATE_FRIEND",
                            entityId = id
                        )
                    }
                }
                is VoiceActionPreview.GroupPreview -> {
                    if (preview.isDelete) {
                        friendGroupRepository.deleteGroup(preview.group.id)
                        val (text, spoken) = VoiceResponseGenerator.groupDeletedSuccess(
                            preview.group.name,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "DELETE_GROUP",
                            entityId = preview.group.id
                        )
                    } else if (preview.isAddMember) {
                        val allFriends = friendRepository.getAllFriendsList()
                        val addedMemberNames = mutableListOf<String>()
                        for (name in preview.addedMemberNames) {
                            val existing = allFriends.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            val fId = if (existing != null) {
                                existing.id
                            } else {
                                friendRepository.addFriend(com.naman.rupeeflow.domain.model.Friend(name = name))
                            }
                            friendGroupRepository.addFriendToGroup(preview.group.id, fId)
                            addedMemberNames.add(name)
                        }
                        val (text, spoken) = VoiceResponseGenerator.groupMemberAddedSuccess(
                            preview.group.name,
                            addedMemberNames,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "ADD_GROUP_MEMBER",
                            entityId = preview.group.id
                        )
                    } else if (preview.isRemoveMember) {
                        val allFriends = friendRepository.getAllFriendsList()
                        val memberToRemove = allFriends.firstOrNull {
                            it.name.equals(preview.removedMemberName, ignoreCase = true)
                        }
                        if (memberToRemove != null) {
                            friendGroupRepository.removeFriendFromGroup(preview.group.id, memberToRemove.id)
                        }
                        val (text, spoken) = VoiceResponseGenerator.groupMemberRemovedSuccess(
                            preview.group.name,
                            preview.removedMemberName ?: "Friend",
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "REMOVE_GROUP_MEMBER",
                            entityId = preview.group.id
                        )
                    } else if (preview.isEdit) {
                        friendGroupRepository.updateGroup(preview.group)
                        VoiceCommandResult.Success(
                            message = "Group \"${preview.group.name}\" updated.",
                            spokenMessage = "Group ${preview.group.name} updated.",
                            actionType = "UPDATE_GROUP",
                            entityId = preview.group.id
                        )
                    } else {
                        // Create group with initial members
                        val allFriends = friendRepository.getAllFriendsList()
                        val memberIds = mutableListOf<Long>()
                        for (name in preview.addedMemberNames) {
                            val f = allFriends.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            val fId = f?.id ?: friendRepository.addFriend(com.naman.rupeeflow.domain.model.Friend(name = name))
                            memberIds.add(fId)
                        }
                        val groupId = friendGroupRepository.createGroup(
                            name = preview.group.name,
                            friendIds = memberIds
                        )
                        val (text, spoken) = VoiceResponseGenerator.groupSavedSuccess(
                            preview.group.name,
                            language
                        )
                        VoiceCommandResult.Success(
                            message = text,
                            spokenMessage = spoken,
                            actionType = "CREATE_GROUP",
                            entityId = groupId
                        )
                    }
                }
                is VoiceActionPreview.NavigationPreview -> {
                    VoiceCommandResult.Success(
                        message = "Navigating to ${preview.destinationTitle}.",
                        spokenMessage = preview.spokenMessage,
                        actionType = "NAVIGATE"
                    )
                }
                is VoiceActionPreview.BackupPreview -> {
                    VoiceCommandResult.Success(
                        message = if (preview.isRestore) "Backup restored." else "Backup created.",
                        spokenMessage = if (preview.isRestore) "Backup restored." else "Backup created.",
                        actionType = if (preview.isRestore) "RESTORE_BACKUP" else "CREATE_BACKUP"
                    )
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
