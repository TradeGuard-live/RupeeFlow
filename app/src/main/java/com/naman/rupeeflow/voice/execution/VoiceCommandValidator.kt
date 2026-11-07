package com.naman.rupeeflow.voice.execution

import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.voice.model.*

/**
 * Validates parsed voice commands and constructs action previews.
 * Never allows ambiguous or unvalidated financial execution.
 */
object VoiceCommandValidator {

    fun validateAndBuildPreview(
        command: VoiceCommand,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        categories: List<Category>,
        language: VoiceLanguage = VoiceLanguage.ENGLISH
    ): VoiceCommandResult {
        return when (command) {
            is VoiceCommand.CreateTransaction -> validateCreateTransaction(command, friends, groups, categories, language)
            is VoiceCommand.CreateSettlement -> validateCreateSettlement(command, friends, language)
            is VoiceCommand.EditTransaction -> validateEditTransaction(command, language)
            is VoiceCommand.DeleteTransaction -> validateDeleteTransaction(command, language)
            is VoiceCommand.EditSettlement -> validateEditSettlement(command, language)
            is VoiceCommand.ReverseSettlement -> validateReverseSettlement(command, language)
            is VoiceCommand.DeleteSettlement -> validateDeleteSettlement(command, language)
            is VoiceCommand.Query -> VoiceCommandResult.Success("Query validated", "", "QUERY")
            is VoiceCommand.Confirm -> VoiceCommandResult.Success("Confirmed", "Confirmed", "CONFIRM")
            is VoiceCommand.Cancel -> VoiceCommandResult.Cancelled
            is VoiceCommand.Help -> VoiceCommandResult.Success("Help", "Help", "HELP")
            is VoiceCommand.Unknown -> VoiceCommandResult.Error(
                errorMessage = "I didn't understand that command. Try saying 'Add 300 for dinner' or 'How much does Jay owe me?'",
                spokenErrorMessage = "I didn't understand that command."
            )
            else -> VoiceCommandResult.Error("Unsupported action", "Unsupported action")
        }
    }

    private fun validateCreateTransaction(
        cmd: VoiceCommand.CreateTransaction,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        categories: List<Category>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        // 1. Check Amount
        if (cmd.amountPaise <= 0L) {
            return VoiceCommandResult.ClarificationRequired(
                prompt = VoiceClarificationPrompt(
                    type = VoiceClarificationType.MISSING_AMOUNT,
                    question = "How much was the amount?",
                    spokenQuestion = "How much was the amount?"
                ),
                partialCommand = cmd
            )
        }

        // 2. Build Participants with exact paise allocation
        val participantsList = mutableListOf<TransactionParticipant>()
        val participantCount = cmd.participantFriendIds.size

        if (participantCount > 0) {
            when (cmd.splitMethod) {
                SplitMethod.EQUAL -> {
                    val shares = LedgerCalculator.calculateEqualSplit(cmd.amountPaise, participantCount)
                    cmd.participantFriendIds.forEachIndexed { index, fId ->
                        val friendName = if (fId == null) "Me" else friends.firstOrNull { it.id == fId }?.name ?: "Friend"
                        participantsList.add(
                            TransactionParticipant(
                                friendId = fId,
                                friendName = friendName,
                                shareAmountPaise = shares[index],
                                percentage = (100.0 / participantCount),
                                splitMethod = SplitMethod.EQUAL
                            )
                        )
                    }
                }
                SplitMethod.PERCENTAGE -> {
                    val pcts = cmd.participantFriendIds.map { cmd.customPercentages[it] ?: (100.0 / participantCount) }
                    val shares = try {
                        LedgerCalculator.calculatePercentageSplit(cmd.amountPaise, pcts)
                    } catch (e: Exception) {
                        return VoiceCommandResult.Error("Invalid percentage split: ${e.message}", "Invalid percentage split")
                    }
                    cmd.participantFriendIds.forEachIndexed { index, fId ->
                        val friendName = if (fId == null) "Me" else friends.firstOrNull { it.id == fId }?.name ?: "Friend"
                        participantsList.add(
                            TransactionParticipant(
                                friendId = fId,
                                friendName = friendName,
                                shareAmountPaise = shares[index],
                                percentage = pcts[index],
                                splitMethod = SplitMethod.PERCENTAGE
                            )
                        )
                    }
                }
                SplitMethod.EXACT_AMOUNT -> {
                    var totalCustom = 0L
                    for (fId in cmd.participantFriendIds) {
                        val share = cmd.customSharesPaise[fId] ?: 0L
                        totalCustom += share
                        val friendName = if (fId == null) "Me" else friends.firstOrNull { it.id == fId }?.name ?: "Friend"
                        participantsList.add(
                            TransactionParticipant(
                                friendId = fId,
                                friendName = friendName,
                                shareAmountPaise = share,
                                splitMethod = SplitMethod.EXACT_AMOUNT
                            )
                        )
                    }
                    if (totalCustom != cmd.amountPaise) {
                        return VoiceCommandResult.Error(
                            "Total of individual shares (₹${totalCustom / 100}) does not equal total transaction amount (₹${cmd.amountPaise / 100})",
                            "Shares do not match the total amount"
                        )
                    }
                }
            }

            // Verify financial invariant
            if (!LedgerCalculator.validateParticipantShares(cmd.amountPaise, participantsList)) {
                return VoiceCommandResult.Error("Participant shares sum does not equal total amount", "Share calculation mismatch")
            }
        }

        val category = categories.firstOrNull { it.id == cmd.categoryId }
            ?: categories.firstOrNull()
            ?: Category(id = 1L, name = cmd.categoryName ?: "General", iconName = "category", colorHex = "#3B82F6")

        val transaction = Transaction(
            title = cmd.title,
            amountPaise = cmd.amountPaise,
            categoryId = category.id,
            category = category,
            transactionDate = cmd.transactionDate,
            paymentMethod = cmd.paymentMethod,
            paidByFriendId = cmd.paidByFriendId,
            paidByName = cmd.payerName,
            participants = participantsList,
            splitMethod = cmd.splitMethod,
            transactionType = cmd.transactionType,
            transactionStatus = TransactionStatus.ACTIVE,
            source = TransactionSource.MANUAL,
            groupId = cmd.groupId
        )

        val myShare = transaction.userExpensePaise
        val confirmationPrompt = "Create ${transaction.title} for ₹${transaction.amountPaise / 100}?" +
                if (participantsList.isNotEmpty()) " Your share is ₹${myShare / 100}." else ""

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.TransactionPreview(
                transaction = transaction,
                summaryNote = if (cmd.groupName != null) "Group: ${cmd.groupName}" else ""
            ),
            pendingCommand = cmd,
            confirmationPrompt = confirmationPrompt,
            spokenPrompt = confirmationPrompt
        )
    }

    private fun validateCreateSettlement(
        cmd: VoiceCommand.CreateSettlement,
        friends: List<Friend>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        if (cmd.amountPaise <= 0L) {
            return VoiceCommandResult.ClarificationRequired(
                prompt = VoiceClarificationPrompt(
                    type = VoiceClarificationType.MISSING_AMOUNT,
                    question = "How much was the settlement amount?",
                    spokenQuestion = "How much was the settlement amount?"
                ),
                partialCommand = cmd
            )
        }

        val friendId = cmd.receiverId ?: cmd.payerId
        val targetFriend = friends.firstOrNull { it.id == friendId }
            ?: if (cmd.friendName.isNotBlank()) friends.firstOrNull { it.name.contains(cmd.friendName, ignoreCase = true) } else null

        if (targetFriend == null) {
            return VoiceCommandResult.ClarificationRequired(
                prompt = VoiceClarificationPrompt(
                    type = VoiceClarificationType.AMBIGUOUS_FRIEND,
                    question = "Which friend is this settlement with?",
                    spokenQuestion = "Which friend is this settlement with?",
                    candidateFriends = friends.filter { !it.isArchived }
                ),
                partialCommand = cmd
            )
        }

        val (payerId, receiverId, directionLabel) = if (cmd.payerId == null) {
            Triple(null, targetFriend.id, "You paid ${targetFriend.name}")
        } else {
            Triple(targetFriend.id, null, "${targetFriend.name} paid You")
        }

        val settlement = Settlement(
            payerId = payerId,
            receiverId = receiverId,
            amountPaise = cmd.amountPaise,
            settlementDate = cmd.date,
            paymentMethod = cmd.paymentMethod,
            notes = cmd.notes
        )

        val confirmationPrompt = "Record settlement of ₹${settlement.amountPaise / 100} with ${targetFriend.name}?"

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.SettlementPreview(
                settlement = settlement,
                friendName = targetFriend.name,
                directionLabel = directionLabel
            ),
            pendingCommand = cmd.copy(payerId = payerId, receiverId = receiverId, friendName = targetFriend.name),
            confirmationPrompt = confirmationPrompt,
            spokenPrompt = confirmationPrompt
        )
    }

    private fun validateEditTransaction(cmd: VoiceCommand.EditTransaction, language: VoiceLanguage): VoiceCommandResult {
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.TransactionPreview(
                transaction = Transaction(title = cmd.newTitle ?: "Edited Transaction", amountPaise = cmd.newAmountPaise ?: 0L),
                isEdit = true
            ),
            pendingCommand = cmd,
            confirmationPrompt = "Update transaction?",
            spokenPrompt = "Update transaction?"
        )
    }

    private fun validateDeleteTransaction(cmd: VoiceCommand.DeleteTransaction, language: VoiceLanguage): VoiceCommandResult {
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.TransactionPreview(
                transaction = Transaction(title = "Delete Transaction", amountPaise = 0L),
                isDelete = true
            ),
            pendingCommand = cmd,
            confirmationPrompt = "Are you sure you want to delete this transaction?",
            spokenPrompt = "Are you sure you want to delete this transaction?"
        )
    }

    private fun validateEditSettlement(cmd: VoiceCommand.EditSettlement, language: VoiceLanguage): VoiceCommandResult {
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.SettlementPreview(
                settlement = Settlement(payerId = null, receiverId = null, amountPaise = cmd.newAmountPaise ?: 0L),
                friendName = cmd.friendName ?: "Friend",
                directionLabel = "Edit Settlement"
            ),
            pendingCommand = cmd,
            confirmationPrompt = "Update settlement?",
            spokenPrompt = "Update settlement?"
        )
    }

    private fun validateReverseSettlement(cmd: VoiceCommand.ReverseSettlement, language: VoiceLanguage): VoiceCommandResult {
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.SettlementPreview(
                settlement = Settlement(payerId = null, receiverId = null, amountPaise = 0L),
                friendName = cmd.friendName ?: "Friend",
                directionLabel = "Reverse Settlement",
                isReverse = true
            ),
            pendingCommand = cmd,
            confirmationPrompt = "Reverse this settlement?",
            spokenPrompt = "Reverse this settlement?"
        )
    }

    private fun validateDeleteSettlement(cmd: VoiceCommand.DeleteSettlement, language: VoiceLanguage): VoiceCommandResult {
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.SettlementPreview(
                settlement = Settlement(payerId = null, receiverId = null, amountPaise = 0L),
                friendName = cmd.friendName ?: "Friend",
                directionLabel = "Delete Settlement",
                isDelete = true
            ),
            pendingCommand = cmd,
            confirmationPrompt = "Delete this settlement permanently?",
            spokenPrompt = "Delete this settlement?"
        )
    }
}
