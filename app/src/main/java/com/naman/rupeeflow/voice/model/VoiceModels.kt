package com.naman.rupeeflow.voice.model

import com.naman.rupeeflow.domain.model.*

/**
 * Supported spoken languages for RupeeFlow Voice Finance Assistant.
 */
enum class VoiceLanguage(val code: String, val displayName: String, val scriptName: String) {
    ENGLISH("en-IN", "English", "English (India)"),
    HINDI("hi-IN", "हिन्दी", "Hindi (Devanagari)"),
    GUJARATI("gu-IN", "ગુજરાતી", "Gujarati"),
    HINGLISH("en-IN", "Hinglish", "Hindi in Latin Script");

    companion object {
        fun fromCode(code: String): VoiceLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

/**
 * Structured intents supported by the voice assistant.
 */
enum class VoiceIntent {
    CREATE_EXPENSE,
    CREATE_INCOME,
    CREATE_REFUND,
    CREATE_TRANSFER,
    CREATE_SETTLEMENT,

    // Friend operations
    FRIEND_CREATE,
    FRIEND_UPDATE,
    FRIEND_DELETE,
    FRIEND_SEARCH,
    FRIEND_ARCHIVE,
    FRIEND_UNARCHIVE,

    // Group operations
    GROUP_CREATE,
    GROUP_UPDATE,
    GROUP_DELETE,
    GROUP_ADD_MEMBER,
    GROUP_REMOVE_MEMBER,
    GROUP_LIST,

    EDIT_TRANSACTION,
    DELETE_TRANSACTION,
    SEARCH_TRANSACTION,

    EDIT_SETTLEMENT,
    DELETE_SETTLEMENT,
    REVERSE_SETTLEMENT,

    QUERY_FRIEND_BALANCE,
    QUERY_ALL_BALANCES,
    QUERY_GROUP_BALANCE,
    QUERY_TOTAL_SPENDING,
    QUERY_MONTHLY_SPENDING,
    QUERY_CATEGORY_SPENDING,
    QUERY_PAYMENT_METHOD_SPENDING,
    QUERY_HIGHEST_EXPENSE,
    QUERY_TRANSACTION_DETAILS,
    QUERY_SETTLEMENT_HISTORY,

    // Navigation & System
    NAVIGATE_SCREEN,
    BACKUP_CREATE,
    BACKUP_RESTORE,

    HELP,
    CANCEL,
    CONFIRM,
    UNKNOWN_INTENT
}

/**
 * Typed commands produced by VoiceCommandParser.
 */
sealed class VoiceCommand {
    data class CreateTransaction(
        val title: String,
        val amountPaise: Long,
        val transactionType: TransactionType = TransactionType.EXPENSE,
        val categoryId: Long? = null,
        val categoryName: String? = null,
        val paidByFriendId: Long? = null, // null represents ME
        val payerName: String = "Me",
        val participantFriendIds: List<Long?> = emptyList(), // null in list represents ME
        val splitMethod: SplitMethod = SplitMethod.EQUAL,
        val customSharesPaise: Map<Long?, Long> = emptyMap(),
        val customPercentages: Map<Long?, Double> = emptyMap(),
        val paymentMethod: PaymentMethod = PaymentMethod.UPI,
        val transactionDate: Long = System.currentTimeMillis(),
        val merchantName: String? = null,
        val groupId: Long? = null,
        val groupName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class EditTransaction(
        val targetTransactionId: Long? = null,
        val searchCriteria: TransactionSearchCriteria? = null,
        val newTitle: String? = null,
        val newAmountPaise: Long? = null,
        val newPaymentMethod: PaymentMethod? = null,
        val newCategoryName: String? = null,
        val newDate: Long? = null,
        val newPaidByFriendId: Long? = null,
        val addedParticipantIds: List<Long> = emptyList(),
        val removedParticipantIds: List<Long> = emptyList(),
        val updatedSharesPaise: Map<Long?, Long> = emptyMap(),
        val rawInput: String = ""
    ) : VoiceCommand()

    data class DeleteTransaction(
        val targetTransactionId: Long? = null,
        val searchCriteria: TransactionSearchCriteria? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class CreateSettlement(
        val amountPaise: Long,
        val payerId: Long?, // null represents ME
        val receiverId: Long?, // null represents ME
        val friendName: String = "",
        val paymentMethod: PaymentMethod = PaymentMethod.UPI,
        val date: Long = System.currentTimeMillis(),
        val notes: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class EditSettlement(
        val settlementId: Long? = null,
        val friendName: String? = null,
        val newAmountPaise: Long? = null,
        val newPaymentMethod: PaymentMethod? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class ReverseSettlement(
        val settlementId: Long? = null,
        val friendName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class DeleteSettlement(
        val settlementId: Long? = null,
        val friendName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class Query(
        val intent: VoiceIntent,
        val friendName: String? = null,
        val friendId: Long? = null,
        val groupName: String? = null,
        val groupId: Long? = null,
        val categoryName: String? = null,
        val paymentMethod: PaymentMethod? = null,
        val dateRange: DateRangeFilter = DateRangeFilter.ALL_TIME,
        val limit: Int = 5,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class ClarificationAnswer(
        val answeredText: String,
        val selectedOptionIndex: Int? = null,
        val selectedFriendId: Long? = null,
        val selectedTransactionId: Long? = null
    ) : VoiceCommand()

    data class CreateFriend(
        val name: String,
        val phoneNumber: String? = null,
        val email: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class UpdateFriend(
        val friendId: Long? = null,
        val friendName: String? = null,
        val newPhoneNumber: String? = null,
        val newName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class DeleteFriend(
        val friendId: Long? = null,
        val friendName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class SearchFriend(
        val query: String,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class CreateGroup(
        val name: String,
        val initialMemberNames: List<String> = emptyList(),
        val rawInput: String = ""
    ) : VoiceCommand()

    data class UpdateGroup(
        val groupId: Long? = null,
        val groupName: String? = null,
        val newName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class AddGroupMember(
        val groupId: Long? = null,
        val groupName: String? = null,
        val friendNames: List<String> = emptyList(),
        val friendIds: List<Long> = emptyList(),
        val rawInput: String = ""
    ) : VoiceCommand()

    data class RemoveGroupMember(
        val groupId: Long? = null,
        val groupName: String? = null,
        val friendName: String? = null,
        val friendId: Long? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class DeleteGroup(
        val groupId: Long? = null,
        val groupName: String? = null,
        val rawInput: String = ""
    ) : VoiceCommand()

    data class ListGroups(
        val rawInput: String = ""
    ) : VoiceCommand()

    data class Navigate(
        val destination: String,
        val destinationTitle: String = "",
        val rawInput: String = ""
    ) : VoiceCommand()

    data class CreateBackup(
        val rawInput: String = ""
    ) : VoiceCommand()

    data class RestoreBackup(
        val rawInput: String = ""
    ) : VoiceCommand()

    data object Confirm : VoiceCommand()
    data object Cancel : VoiceCommand()
    data object Help : VoiceCommand()
    data class Unknown(val rawInput: String) : VoiceCommand()
}

data class TransactionSearchCriteria(
    val description: String? = null,
    val amountPaise: Long? = null,
    val friendName: String? = null,
    val dateFilter: DateRangeFilter? = null,
    val categoryName: String? = null
)

enum class DateRangeFilter {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    ALL_TIME
}

/**
 * Action preview displayed for user confirmation before financial execution.
 */
sealed class VoiceActionPreview {
    data class TransactionPreview(
        val transaction: Transaction,
        val originalTransaction: Transaction? = null,
        val isEdit: Boolean = false,
        val isDelete: Boolean = false,
        val summaryNote: String = ""
    ) : VoiceActionPreview()

    data class SettlementPreview(
        val settlement: Settlement,
        val friendName: String,
        val directionLabel: String,
        val originalSettlement: Settlement? = null,
        val isReverse: Boolean = false,
        val isDelete: Boolean = false
    ) : VoiceActionPreview()

    data class QueryResultPreview(
        val title: String,
        val formattedAnswer: String,
        val spokenAnswer: String,
        val details: List<String> = emptyList()
    ) : VoiceActionPreview()

    data class FriendPreview(
        val friend: Friend,
        val isDelete: Boolean = false,
        val isEdit: Boolean = false,
        val isSearch: Boolean = false,
        val summaryNote: String = ""
    ) : VoiceActionPreview()

    data class GroupPreview(
        val group: FriendGroup,
        val memberFriends: List<Friend> = emptyList(),
        val addedMemberNames: List<String> = emptyList(),
        val removedMemberName: String? = null,
        val isDelete: Boolean = false,
        val isEdit: Boolean = false,
        val isAddMember: Boolean = false,
        val isRemoveMember: Boolean = false,
        val summaryNote: String = ""
    ) : VoiceActionPreview()

    data class NavigationPreview(
        val destinationScreen: String,
        val destinationTitle: String,
        val spokenMessage: String = ""
    ) : VoiceActionPreview()

    data class BackupPreview(
        val isRestore: Boolean = false,
        val summaryNote: String = ""
    ) : VoiceActionPreview()
}

/**
 * Risk classification for voice operations.
 */
enum class VoiceRiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Types of clarification required when input is incomplete or ambiguous.
 */
enum class VoiceClarificationType {
    MISSING_AMOUNT,
    MISSING_PAYER,
    MISSING_PARTICIPANTS,
    MISSING_SPLIT_METHOD,
    MISSING_FRIEND_NAME,
    MISSING_GROUP_NAME,
    MISSING_TARGET_MEMBER,
    AMBIGUOUS_FRIEND,
    AMBIGUOUS_GROUP,
    AMBIGUOUS_TRANSACTION,
    AMBIGUOUS_SETTLEMENT,
    CONFIRM_DELETION,
    CONFIRM_HIGH_RISK
}

data class VoiceClarificationPrompt(
    val type: VoiceClarificationType,
    val question: String,
    val spokenQuestion: String,
    val options: List<String> = emptyList(),
    val candidateFriends: List<Friend> = emptyList(),
    val candidateTransactions: List<Transaction> = emptyList(),
    val candidateGroups: List<FriendGroup> = emptyList()
)

/**
 * Outcomes of voice command evaluation.
 */
sealed class VoiceCommandResult {
    data class Success(
        val message: String,
        val spokenMessage: String,
        val actionType: String,
        val entityId: Long? = null
    ) : VoiceCommandResult()

    data class PreviewReady(
        val preview: VoiceActionPreview,
        val pendingCommand: VoiceCommand,
        val confirmationPrompt: String,
        val spokenPrompt: String
    ) : VoiceCommandResult()

    data class ClarificationRequired(
        val prompt: VoiceClarificationPrompt,
        val partialCommand: VoiceCommand
    ) : VoiceCommandResult()

    data class QueryResult(
        val preview: VoiceActionPreview.QueryResultPreview
    ) : VoiceCommandResult()

    data class Error(
        val errorMessage: String,
        val spokenErrorMessage: String
    ) : VoiceCommandResult()

    data object Cancelled : VoiceCommandResult()
}

/**
 * UI State for the Voice Assistant bottom sheet / dialog.
 */
enum class VoiceAssistantStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    NEEDS_CLARIFICATION,
    PREVIEW,
    EXECUTING,
    SUCCESS,
    ERROR
}

/**
 * Real device speech recognizer support status.
 */
enum class SpeechRecognitionAvailability {
    AVAILABLE_OFFLINE,
    LANGUAGE_PACK_DOWNLOAD_REQUIRED,
    SPEECH_RECOGNIZER_UNAVAILABLE,
    PERMISSION_REQUIRED
}

