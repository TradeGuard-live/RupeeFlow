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
            is VoiceCommand.CreateFriend -> validateCreateFriend(command, friends, language)
            is VoiceCommand.UpdateFriend -> validateUpdateFriend(command, friends, language)
            is VoiceCommand.DeleteFriend -> validateDeleteFriend(command, friends, language)
            is VoiceCommand.SearchFriend -> validateSearchFriend(command, friends, language)

            is VoiceCommand.CreateGroup -> validateCreateGroup(command, groups, language)
            is VoiceCommand.UpdateGroup -> validateUpdateGroup(command, groups, language)
            is VoiceCommand.DeleteGroup -> validateDeleteGroup(command, groups, language)
            is VoiceCommand.AddGroupMember -> validateAddGroupMember(command, friends, groups, language)
            is VoiceCommand.RemoveGroupMember -> validateRemoveGroupMember(command, friends, groups, language)
            is VoiceCommand.ListGroups -> VoiceCommandResult.Success("List groups", "Listing groups", "QUERY")

            is VoiceCommand.Navigate -> validateNavigate(command, language)
            is VoiceCommand.CreateBackup -> VoiceCommandResult.PreviewReady(
                preview = VoiceActionPreview.BackupPreview(isRestore = false, summaryNote = "Create local backup"),
                pendingCommand = command,
                confirmationPrompt = "Create local backup now?",
                spokenPrompt = "Create local backup now?"
            )
            is VoiceCommand.RestoreBackup -> VoiceCommandResult.PreviewReady(
                preview = VoiceActionPreview.BackupPreview(isRestore = true, summaryNote = "Restore backup data"),
                pendingCommand = command,
                confirmationPrompt = "Restore data from latest backup? This will replace current records.",
                spokenPrompt = "Restore data from backup?"
            )

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
                errorMessage = "I didn't understand that command. Try saying 'Add friend Jay' or 'Add 300 for dinner'.",
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

    private fun validateCreateFriend(
        cmd: VoiceCommand.CreateFriend,
        friends: List<Friend>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        if (cmd.name.isBlank()) {
            val (qText, qSpoken) = VoiceResponseGenerator.missingFriendNamePrompt(language)
            return VoiceCommandResult.ClarificationRequired(
                prompt = VoiceClarificationPrompt(
                    type = VoiceClarificationType.MISSING_FRIEND_NAME,
                    question = qText,
                    spokenQuestion = qSpoken
                ),
                partialCommand = cmd
            )
        }

        val friend = Friend(
            name = cmd.name,
            phoneNumber = cmd.phoneNumber,
            email = cmd.email
        )

        val (confirmText, confirmSpoken) = VoiceResponseGenerator.friendConfirmationPrompt(friend, isDelete = false, language)
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.FriendPreview(
                friend = friend,
                summaryNote = "Add to Friends"
            ),
            pendingCommand = cmd,
            confirmationPrompt = confirmText,
            spokenPrompt = confirmSpoken
        )
    }

    private fun validateDeleteFriend(
        cmd: VoiceCommand.DeleteFriend,
        friends: List<Friend>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val target = if (cmd.friendId != null) {
            friends.firstOrNull { it.id == cmd.friendId }
        } else if (!cmd.friendName.isNullOrBlank()) {
            val q = cmd.friendName.trim().lowercase()
            friends.firstOrNull { it.name.trim().lowercase() == q }
                ?: friends.firstOrNull { it.name.trim().lowercase().contains(q) }
        } else null

        if (target == null) {
            return VoiceCommandResult.Error(
                errorMessage = "Could not find friend \"${cmd.friendName ?: ""}\".",
                spokenErrorMessage = "Friend not found."
            )
        }

        val (confirmText, confirmSpoken) = VoiceResponseGenerator.friendConfirmationPrompt(target, isDelete = true, language)
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.FriendPreview(
                friend = target,
                isDelete = true,
                summaryNote = "Delete friend ${target.name}"
            ),
            pendingCommand = cmd,
            confirmationPrompt = confirmText,
            spokenPrompt = confirmSpoken
        )
    }

    private fun validateUpdateFriend(
        cmd: VoiceCommand.UpdateFriend,
        friends: List<Friend>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val target = if (cmd.friendId != null) {
            friends.firstOrNull { it.id == cmd.friendId }
        } else if (!cmd.friendName.isNullOrBlank()) {
            val q = cmd.friendName.trim().lowercase()
            friends.firstOrNull { it.name.trim().lowercase() == q }
        } else null

        if (target == null) {
            return VoiceCommandResult.Error("Friend not found to update.", "Friend not found.")
        }

        val updated = target.copy(
            phoneNumber = cmd.newPhoneNumber ?: target.phoneNumber,
            name = cmd.newName ?: target.name
        )

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.FriendPreview(friend = updated, isEdit = true, summaryNote = "Update Friend"),
            pendingCommand = cmd,
            confirmationPrompt = "Update ${target.name}'s details?",
            spokenPrompt = "Update friend details?"
        )
    }

    private fun validateSearchFriend(
        cmd: VoiceCommand.SearchFriend,
        friends: List<Friend>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val q = cmd.query.trim().lowercase()
        val match = friends.firstOrNull { it.name.trim().lowercase().contains(q) }
            ?: Friend(name = cmd.query)
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.FriendPreview(friend = match, isSearch = true),
            pendingCommand = cmd,
            confirmationPrompt = "Found friend: ${match.name}",
            spokenPrompt = "Found friend ${match.name}"
        )
    }

    private fun validateCreateGroup(
        cmd: VoiceCommand.CreateGroup,
        groups: List<FriendGroup>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        if (cmd.name.isBlank()) {
            val (qText, qSpoken) = VoiceResponseGenerator.missingGroupNamePrompt(language)
            return VoiceCommandResult.ClarificationRequired(
                prompt = VoiceClarificationPrompt(
                    type = VoiceClarificationType.MISSING_GROUP_NAME,
                    question = qText,
                    spokenQuestion = qSpoken
                ),
                partialCommand = cmd
            )
        }

        val group = FriendGroup(name = cmd.name)
        val (cText, cSpoken) = VoiceResponseGenerator.groupConfirmationPrompt(
            groupName = group.name,
            isDelete = false,
            isAddMember = false,
            isRemoveMember = false,
            targetMembers = cmd.initialMemberNames,
            language = language
        )

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.GroupPreview(
                group = group,
                addedMemberNames = cmd.initialMemberNames,
                summaryNote = "Create Group"
            ),
            pendingCommand = cmd,
            confirmationPrompt = cText,
            spokenPrompt = cSpoken
        )
    }

    private fun validateAddGroupMember(
        cmd: VoiceCommand.AddGroupMember,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val targetGroup = if (cmd.groupId != null) {
            groups.firstOrNull { it.id == cmd.groupId }
        } else if (!cmd.groupName.isNullOrBlank()) {
            val q = cmd.groupName.trim().lowercase()
            groups.firstOrNull { it.name.trim().lowercase() == q }
                ?: groups.firstOrNull { it.name.trim().lowercase().contains(q) }
        } else groups.firstOrNull()

        if (targetGroup == null) {
            return VoiceCommandResult.Error("Group not found.", "Group not found.")
        }

        val (cText, cSpoken) = VoiceResponseGenerator.groupConfirmationPrompt(
            groupName = targetGroup.name,
            isDelete = false,
            isAddMember = true,
            isRemoveMember = false,
            targetMembers = cmd.friendNames,
            language = language
        )

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.GroupPreview(
                group = targetGroup,
                addedMemberNames = cmd.friendNames,
                isAddMember = true,
                summaryNote = "Add to ${targetGroup.name}"
            ),
            pendingCommand = cmd,
            confirmationPrompt = cText,
            spokenPrompt = cSpoken
        )
    }

    private fun validateRemoveGroupMember(
        cmd: VoiceCommand.RemoveGroupMember,
        friends: List<Friend>,
        groups: List<FriendGroup>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val targetGroup = if (cmd.groupId != null) {
            groups.firstOrNull { it.id == cmd.groupId }
        } else if (!cmd.groupName.isNullOrBlank()) {
            val q = cmd.groupName.trim().lowercase()
            groups.firstOrNull { it.name.trim().lowercase() == q }
                ?: groups.firstOrNull { it.name.trim().lowercase().contains(q) }
        } else groups.firstOrNull()

        if (targetGroup == null) {
            return VoiceCommandResult.Error("Group not found.", "Group not found.")
        }

        val targetName = cmd.friendName ?: "Friend"
        val (cText, cSpoken) = VoiceResponseGenerator.groupConfirmationPrompt(
            groupName = targetGroup.name,
            isDelete = false,
            isAddMember = false,
            isRemoveMember = true,
            targetMembers = listOf(targetName),
            language = language
        )

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.GroupPreview(
                group = targetGroup,
                removedMemberName = targetName,
                isRemoveMember = true,
                summaryNote = "Remove from ${targetGroup.name}"
            ),
            pendingCommand = cmd,
            confirmationPrompt = cText,
            spokenPrompt = cSpoken
        )
    }

    private fun validateUpdateGroup(
        cmd: VoiceCommand.UpdateGroup,
        groups: List<FriendGroup>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val targetGroup = if (cmd.groupId != null) {
            groups.firstOrNull { it.id == cmd.groupId }
        } else if (!cmd.groupName.isNullOrBlank()) {
            val q = cmd.groupName.trim().lowercase()
            groups.firstOrNull { it.name.trim().lowercase() == q }
        } else null

        if (targetGroup == null) {
            return VoiceCommandResult.Error("Group not found.", "Group not found.")
        }

        val updated = targetGroup.copy(name = cmd.newName ?: targetGroup.name)
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.GroupPreview(group = updated, isEdit = true, summaryNote = "Rename Group"),
            pendingCommand = cmd,
            confirmationPrompt = "Rename group to ${updated.name}?",
            spokenPrompt = "Rename group?"
        )
    }

    private fun validateDeleteGroup(
        cmd: VoiceCommand.DeleteGroup,
        groups: List<FriendGroup>,
        language: VoiceLanguage
    ): VoiceCommandResult {
        val targetGroup = if (cmd.groupId != null) {
            groups.firstOrNull { it.id == cmd.groupId }
        } else if (!cmd.groupName.isNullOrBlank()) {
            val q = cmd.groupName.trim().lowercase()
            groups.firstOrNull { it.name.trim().lowercase() == q }
                ?: groups.firstOrNull { it.name.trim().lowercase().contains(q) }
        } else null

        if (targetGroup == null) {
            return VoiceCommandResult.Error("Group not found.", "Group not found.")
        }

        val (cText, cSpoken) = VoiceResponseGenerator.groupConfirmationPrompt(
            groupName = targetGroup.name,
            isDelete = true,
            isAddMember = false,
            isRemoveMember = false,
            targetMembers = emptyList(),
            language = language
        )

        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.GroupPreview(
                group = targetGroup,
                isDelete = true,
                summaryNote = "Delete group ${targetGroup.name}"
            ),
            pendingCommand = cmd,
            confirmationPrompt = cText,
            spokenPrompt = cSpoken
        )
    }

    private fun validateNavigate(cmd: VoiceCommand.Navigate, language: VoiceLanguage): VoiceCommandResult {
        return VoiceCommandResult.PreviewReady(
            preview = VoiceActionPreview.NavigationPreview(
                destinationScreen = cmd.destination,
                destinationTitle = cmd.destinationTitle,
                spokenMessage = "Opening ${cmd.destinationTitle}."
            ),
            pendingCommand = cmd,
            confirmationPrompt = "Open ${cmd.destinationTitle}?",
            spokenPrompt = "Opening ${cmd.destinationTitle}."
        )
    }
}
