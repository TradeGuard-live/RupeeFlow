package com.naman.rupeeflow.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.naman.rupeeflow.domain.model.*

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "friends",
    indices = [Index(value = ["name"])]
)
data class FriendEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val profileImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = FriendEntity::class,
            parentColumns = ["id"],
            childColumns = ["paidByFriendId"],
            onDelete = ForeignKey.RESTRICT // RESTRICT prevents friend deletion from setting paidByFriendId to NULL (ME)
        ),
        ForeignKey(
            entity = FriendGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ExpenseGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseGroupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["paidByFriendId"]),
        Index(value = ["groupId"]),
        Index(value = ["expenseGroupId"]),
        Index(value = ["transactionDate"]),
        Index(value = ["transactionStatus"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val amountPaise: Long,
    val categoryId: Long,
    val transactionDate: Long,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val accountSource: String? = null,
    val paidByFriendId: Long? = null, // null represents "ME"
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val transactionStatus: TransactionStatus = TransactionStatus.ACTIVE,
    val source: TransactionSource = TransactionSource.MANUAL,
    val merchantName: String? = null,
    val referenceNumber: String? = null,
    val phoneNumber: String? = null,
    val upiId: String? = null,
    val groupId: Long? = null,
    val expenseGroupId: Long? = null,
    val expenseGroupName: String? = null,
    val billImagePath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationCapturedAt: Long? = null,
    val locationSource: LocationSource? = null,
    val locationLabel: String? = null,
    val googleMapsUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transaction_participants",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FriendEntity::class,
            parentColumns = ["id"],
            childColumns = ["friendId"],
            onDelete = ForeignKey.RESTRICT // RESTRICT prevents friend deletion from setting friendId to NULL (ME)
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["friendId"])
    ]
)
data class TransactionParticipantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val friendId: Long?, // null represents "ME"
    val shareAmountPaise: Long,
    val percentage: Double = 0.0,
    val splitMethod: SplitMethod = SplitMethod.EQUAL
)

@Entity(
    tableName = "settlements",
    foreignKeys = [
        ForeignKey(
            entity = FriendEntity::class,
            parentColumns = ["id"],
            childColumns = ["payerId"],
            onDelete = ForeignKey.RESTRICT // RESTRICT prevents friend deletion from setting payerId to NULL (ME)
        ),
        ForeignKey(
            entity = FriendEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiverId"],
            onDelete = ForeignKey.RESTRICT // RESTRICT prevents friend deletion from setting receiverId to NULL (ME)
        )
    ],
    indices = [
        Index(value = ["payerId"]),
        Index(value = ["receiverId"]),
        Index(value = ["settlementDate"])
    ]
)
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val payerId: Long?, // null represents "ME"
    val receiverId: Long?, // null represents "ME"
    val amountPaise: Long,
    val settlementDate: Long,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val referenceNumber: String? = null,
    val notes: String? = null,
    val isReversed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "pending_transactions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["timestamp"]),
        Index(value = ["referenceNumber"])
    ]
)
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val detectedAmountPaise: Long,
    val detectedType: TransactionType = TransactionType.EXPENSE,
    val detectedBank: String? = null,
    val detectedAccount: String? = null,
    val detectedMerchant: String? = null,
    val phoneNumber: String? = null,
    val upiId: String? = null,
    val referenceNumber: String? = null,
    val rawContent: String,
    val timestamp: Long,
    val status: PendingStatus = PendingStatus.PENDING,
    val source: TransactionSource = TransactionSource.SMS,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationCapturedAt: Long? = null,
    val locationSource: LocationSource? = null,
    val locationLabel: String? = null,
    val googleMapsUrl: String? = null
)

// Friend Group entities for organizing friends into custom groups
@Entity(tableName = "friend_groups")
data class FriendGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val iconName: String? = null,
    val colorHex: String? = null,
    val groupImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Expense Group entities for organizing transactions into events/trips
@Entity(
    tableName = "expense_groups",
    indices = [Index(value = ["name"])]
)
data class ExpenseGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val iconName: String? = null,
    val colorHex: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Report Draft entity for saving customized report configurations and exclusions
@Entity(tableName = "report_drafts")
data class ReportDraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val periodType: ReportPeriodType = ReportPeriodType.MONTHLY,
    val startDate: Long,
    val endDate: Long,
    val excludedTransactionIdsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "friend_group_members",
    primaryKeys = ["groupId", "friendId"],
    foreignKeys = [
        ForeignKey(
            entity = FriendGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = FriendEntity::class,
            parentColumns = ["id"],
            childColumns = ["friendId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["groupId"]),
        Index(value = ["friendId"])
    ]
)
data class FriendGroupMemberEntity(
    val groupId: Long,
    val friendId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

// Dynamic smart input ranking entity for adaptive user suggestions
@Entity(
    tableName = "input_usage",
    indices = [Index(value = ["fieldType", "optionId"], unique = true)]
)
data class InputUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fieldType: String,
    val optionId: String,
    val usageCount: Long = 1,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val preferenceScore: Double = 0.0
)
