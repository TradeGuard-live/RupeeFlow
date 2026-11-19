package com.naman.rupeeflow.domain.model

data class Friend(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val profileImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val upiId: String? = null,
    val upiVerifiedName: String? = null,
    val upiVerificationStatus: FriendUpiVerificationStatus = FriendUpiVerificationStatus.UNVERIFIED,
    val upiLastUpdated: Long? = null,
    // Dynamically computed ledger metrics
    val totalTheyOweMePaise: Long = 0,
    val totalIOweThemPaise: Long = 0,
    val netBalancePaise: Long = 0, // > 0 means they owe me, < 0 means I owe them, == 0 settled
    val lastTransactionDate: Long? = null,
    val status: FriendBalanceStatus = FriendBalanceStatus.SETTLED
)

data class Category(
    val id: Long = 0,
    val name: String,
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = false
)

// Friend Group domain model for organizing friends and group splitting
data class FriendGroup(
    val id: Long = 0,
    val name: String,
    val iconName: String? = null,
    val colorHex: String? = null,
    val groupImagePath: String? = null,
    val memberCount: Int = 0,
    val memberFriendIds: List<Long> = emptyList(),
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Expense Group / Trip Group domain model for organizing transactions by events/purposes
data class ExpenseGroup(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val destination: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val coverImageUri: String? = null,
    val iconName: String? = null,
    val colorHex: String? = null,
    val linkedFriendGroupId: Long? = null,
    val groupType: GroupType = GroupType.CUSTOM,
    val currencyCode: String = "INR",
    val simplifyDebts: Boolean = true,
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val transactionCount: Int = 0,
    val memberCount: Int = 0,
    val memberFriendIds: List<Long> = emptyList(),
    val totalSpendingPaise: Long = 0L,
    val grossSpendingPaise: Long = 0L,
    val cashSpendingPaise: Long = 0L,
    val onlineSpendingPaise: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

// Split Pro group activity domain model
data class GroupActivity(
    val id: Long = 0,
    val groupId: Long,
    val activityType: GroupActivityType,
    val actorName: String,
    val description: String,
    val amountPaise: Long? = null,
    val targetId: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// Individual member balance in a group
data class GroupMemberBalance(
    val memberId: Long?, // null = ME, or friendId
    val memberName: String,
    val totalPaidPaise: Long,
    val totalSharePaise: Long,
    val netBalancePaise: Long // positive = owed money, negative = owes money
)

// Simplified or direct debt transfer between two members
data class SimplifiedDebt(
    val fromMemberId: Long?, // null = ME, or friendId
    val fromMemberName: String,
    val toMemberId: Long?, // null = ME, or friendId
    val toMemberName: String,
    val amountPaise: Long
)

// Summary of all financial balances within a group
data class GroupBalanceSummary(
    val totalExpensePaise: Long,
    val myPaidPaise: Long,
    val mySharePaise: Long,
    val myNetBalancePaise: Long, // positive = others owe me, negative = I owe others
    val memberBalances: List<GroupMemberBalance>,
    val directDebts: List<SimplifiedDebt>, // original pairwise debts
    val simplifiedDebts: List<SimplifiedDebt> // minimal transfers
)

// Report draft domain model for saving customizable report exclusion presets
data class ReportDraft(
    val id: Long = 0,
    val name: String,
    val periodType: ReportPeriodType = ReportPeriodType.MONTHLY,
    val startDate: Long,
    val endDate: Long,
    val excludedTransactionIds: Set<Long> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class TransactionParticipant(
    val id: Long = 0,
    val transactionId: Long = 0,
    val friendId: Long?, // null represents "ME"
    val friendName: String = "Me",
    val shareAmountPaise: Long,
    val percentage: Double = 0.0,
    val splitMethod: SplitMethod = SplitMethod.EQUAL
)

data class TransactionLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val locationCapturedAt: Long = System.currentTimeMillis(),
    val locationSource: LocationSource = LocationSource.MANUAL_COORDINATES,
    val locationLabel: String? = null,
    val googleMapsUrl: String? = null
) {
    fun isValid(): Boolean {
        return !latitude.isNaN() && !latitude.isInfinite() &&
                !longitude.isNaN() && !longitude.isInfinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    fun toGeoUri(): String = "geo:$latitude,$longitude?q=$latitude,$longitude"
    fun toGoogleMapsUrl(): String = googleMapsUrl?.ifBlank { null }
        ?: "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
}

data class Transaction(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val amountPaise: Long,
    val categoryId: Long = 1L,
    val category: Category? = null,
    val transactionDate: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val accountSource: String? = null,
    val paidByFriendId: Long? = null, // null means "ME"
    val paidByName: String = "Me",
    val participants: List<TransactionParticipant> = emptyList(),
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val transactionStatus: TransactionStatus = TransactionStatus.ACTIVE,
    val source: TransactionSource = TransactionSource.MANUAL,
    val merchantName: String? = null,
    val merchantNormalized: String? = null,
    val rawMerchant: String? = null,
    val sourcePackage: String? = null,
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
) {
    val isIgnored: Boolean
        get() = transactionStatus == TransactionStatus.IGNORED

    val isSplit: Boolean
        get() = participants.size > 1 || (participants.isNotEmpty() && participants.any { it.friendId != null })

    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    val hasExpenseGroup: Boolean
        get() = expenseGroupId != null || !expenseGroupName.isNullOrBlank()

    val location: TransactionLocation?
        get() = if (latitude != null && longitude != null) {
            TransactionLocation(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                locationCapturedAt = locationCapturedAt ?: createdAt,
                locationSource = locationSource ?: LocationSource.MANUAL_COORDINATES,
                locationLabel = locationLabel,
                googleMapsUrl = googleMapsUrl
            )
        } else null

    val userExpensePaise: Long
        get() = com.naman.rupeeflow.domain.ledger.PersonalFinancialAggregator.calculatePersonalExpense(this)
}

// Direction of obligation for friend expenses
enum class ExpenseObligationDirection {
    THEY_OWE_ME,
    I_OWE_THEM
}

// Detailed model for an unsettled/outstanding expense with a friend
data class UnsettledExpenseItem(
    val transaction: Transaction,
    val totalTransactionAmountPaise: Long,
    val originalShareAmountPaise: Long,
    val settledAmountPaise: Long,
    val remainingOutstandingPaise: Long,
    val isPartiallySettled: Boolean,
    val direction: ExpenseObligationDirection
)

// Production hardening: default values for backward compatibility and clean construction
data class Settlement(
    val id: Long = 0,
    val payerId: Long?, // null means "ME", else friend ID
    val receiverId: Long?, // null means "ME", else friend ID
    val amountPaise: Long,
    val settlementDate: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val referenceNumber: String? = null,
    val notes: String? = null,
    val groupId: Long? = null,
    val proofImagePath: String? = null,
    val isReversed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val upiPaymentStatus: SettlementPaymentStatus = SettlementPaymentStatus.SUCCESS,
    val upiVerificationStatus: SettlementVerificationStatus = SettlementVerificationStatus.MANUAL_UNVERIFIED,
    val upiTransactionRef: String? = null,
    val upiResponseRaw: String? = null,
    val upiPayerVpa: String? = null,
    val upiReceiverVpa: String? = null
) {
    val affectsLedger: Boolean
        get() = (upiPaymentStatus == SettlementPaymentStatus.SUCCESS) &&
                (upiVerificationStatus == SettlementVerificationStatus.AUTOMATIC_VERIFIED ||
                 upiVerificationStatus == SettlementVerificationStatus.MANUAL_UNVERIFIED)
}

// Production hardening: robust default parameters for testability and non-SMS sources
data class PendingTransaction(
    val id: Long = 0,
    val detectedAmountPaise: Long,
    val detectedType: TransactionType = TransactionType.EXPENSE,
    val detectedBank: String? = null,
    val detectedAccount: String? = null,
    val detectedMerchant: String? = null,
    val rawMerchant: String? = null,
    val merchantNormalized: String? = null,
    val sourcePackage: String? = null,
    val phoneNumber: String? = null,
    val upiId: String? = null,
    val referenceNumber: String? = null,
    val rawContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: PendingStatus = PendingStatus.PENDING,
    val source: TransactionSource = TransactionSource.SMS,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val locationCapturedAt: Long? = null,
    val locationSource: LocationSource? = null,
    val locationLabel: String? = null,
    val googleMapsUrl: String? = null
) {
    val hasLocation: Boolean
        get() = latitude != null && longitude != null

    val location: TransactionLocation?
        get() = if (latitude != null && longitude != null) {
            TransactionLocation(
                latitude = latitude,
                longitude = longitude,
                accuracyMeters = accuracyMeters,
                locationCapturedAt = locationCapturedAt ?: timestamp,
                locationSource = locationSource ?: LocationSource.DEVICE_AT_DETECTION,
                locationLabel = locationLabel,
                googleMapsUrl = googleMapsUrl
            )
        } else null
}

data class FinancialSummary(
    val totalExpenseThisMonthPaise: Long = 0,
    val totalIncomeThisMonthPaise: Long = 0,
    val totalRefundThisMonthPaise: Long = 0, // Clearly separated refunds
    val netSpendingPaise: Long = 0,          // Net spending = (Gross Expenses - Refunds)
    val totalCashSpendingPaise: Long = 0,
    val totalOnlineSpendingPaise: Long = 0,
    val totalOwedToMePaise: Long = 0,
    val totalIOwePaise: Long = 0,
    val netReceivablesPayablesPaise: Long = 0,
    val averageDailySpendingPaise: Long = 0,
    val pendingTransactionsCount: Int = 0
)

data class CategorySpending(
    val category: Category,
    val totalSpentPaise: Long,
    val percentage: Float,
    val transactionCount: Int
)

data class DailySpending(
    val timestamp: Long,
    val dayLabel: String,
    val amountPaise: Long,
    val dateRangeLabel: String = ""
)

data class AnalyticsData(
    val totalSpentPaise: Long = 0,
    val totalIncomePaise: Long = 0,
    val totalRefundPaise: Long = 0,  // Separated refund reporting
    val netSpendingPaise: Long = 0,  // Net spending after refunds
    val totalSavingsPaise: Long = 0,
    val savingsRate: Float = 0f,     // Savings rate percentage
    val cashSpendingPaise: Long = 0,
    val onlineSpendingPaise: Long = 0,
    val dailyAveragePaise: Long = 0,
    val highestSpendingDay: String = "-",
    val highestSpendingDayAmountPaise: Long = 0,
    val highestCategory: Category? = null,
    val categoryBreakdown: List<CategorySpending> = emptyList(),
    val dailySpendingTrend: List<DailySpending> = emptyList(),
    val previousPeriodSpentPaise: Long = 0,
    val percentageChangeVsPrevious: Float = 0f
)

// Dynamic input ranking model
data class InputUsage(
    val id: Long = 0,
    val fieldType: String,
    val optionId: String,
    val usageCount: Long = 1,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val preferenceScore: Double = 0.0
)

// Savings Goals & Daily Savings Models
data class SavingsGoal(
    val id: Long = 0,
    val name: String,
    val description: String? = null,
    val targetAmountPaise: Long,
    val targetDate: Long? = null,
    val category: SavingsGoalCategory = SavingsGoalCategory.CUSTOM,
    val imageUri: String? = null,
    val colorHex: String = "#FFAD91",
    val priority: SavingsGoalPriority = SavingsGoalPriority.MEDIUM,
    val status: SavingsGoalStatus = SavingsGoalStatus.ACTIVE,
    val currentSavedPaise: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressPercentage: Float
        get() = if (targetAmountPaise > 0L) {
            ((currentSavedPaise.toDouble() / targetAmountPaise.toDouble()) * 100.0).toFloat().coerceAtLeast(0f)
        } else 0f

    val remainingPaise: Long
        get() = if (targetAmountPaise > currentSavedPaise) {
            targetAmountPaise - currentSavedPaise
        } else 0L

    val isCompleted: Boolean
        get() = (targetAmountPaise > 0L && currentSavedPaise >= targetAmountPaise) || status == SavingsGoalStatus.COMPLETED

    val isExceeded: Boolean
        get() = targetAmountPaise > 0L && currentSavedPaise > targetAmountPaise
}

data class SavingsEntry(
    val id: Long = 0,
    val goalId: Long,
    val amountPaise: Long,
    val entryType: SavingsEntryType = SavingsEntryType.DEPOSIT,
    val entryDate: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String,
    val paymentMethod: String? = null,
    val note: String? = null
)

data class DailySavingsGroup(
    val dateMillis: Long,
    val totalSavedPaise: Long,
    val totalDepositsPaise: Long,
    val totalWithdrawalsPaise: Long,
    val entries: List<SavingsEntry>
)

data class SavingsOverviewSummary(
    val totalSavedPaise: Long = 0,
    val savingsAddedThisMonthPaise: Long = 0,
    val activeGoalsCount: Int = 0,
    val completedGoalsCount: Int = 0,
    val overallProgressPercentage: Float = 0f
)

