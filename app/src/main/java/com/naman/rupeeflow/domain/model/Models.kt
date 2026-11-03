package com.naman.rupeeflow.domain.model

data class Friend(
    val id: Long = 0,
    val name: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
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

data class TransactionParticipant(
    val id: Long = 0,
    val transactionId: Long = 0,
    val friendId: Long?, // null represents "ME"
    val friendName: String = "Me",
    val shareAmountPaise: Long,
    val percentage: Double = 0.0,
    val splitMethod: SplitMethod = SplitMethod.EQUAL
)

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
    val referenceNumber: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isIgnored: Boolean
        get() = transactionStatus == TransactionStatus.IGNORED

    val isSplit: Boolean
        get() = participants.size > 1 || (participants.isNotEmpty() && participants.any { it.friendId != null })

    val userExpensePaise: Long
        get() {
            if (transactionStatus == TransactionStatus.IGNORED) return 0L
            if (transactionType != TransactionType.EXPENSE) return 0L

            return if (participants.isNotEmpty()) {
                val me = participants.firstOrNull { it.friendId == null }
                me?.shareAmountPaise ?: 0L
            } else {
                if (paidByFriendId == null) amountPaise else 0L
            }
        }
}

// ✅ Production hardening: default values for backward compatibility and clean construction
data class Settlement(
    val id: Long = 0,
    val payerId: Long?, // null means "ME", else friend ID
    val receiverId: Long?, // null means "ME", else friend ID
    val amountPaise: Long,
    val settlementDate: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val referenceNumber: String? = null,
    val notes: String? = null,
    val isReversed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// ✅ Production hardening: robust default parameters for testability and non-SMS sources
data class PendingTransaction(
    val id: Long = 0,
    val detectedAmountPaise: Long,
    val detectedType: TransactionType,
    val detectedBank: String? = null,
    val detectedAccount: String? = null,
    val detectedMerchant: String? = null,
    val referenceNumber: String? = null,
    val rawContent: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: PendingStatus = PendingStatus.PENDING,
    val source: TransactionSource = TransactionSource.SMS
)

data class FinancialSummary(
    val totalExpenseThisMonthPaise: Long = 0,
    val totalIncomeThisMonthPaise: Long = 0,
    val totalRefundThisMonthPaise: Long = 0, // ✅ Clearly separated refunds
    val netSpendingPaise: Long = 0,          // ✅ Net spending = (Gross Expenses - Refunds)
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
    val amountPaise: Long
)

data class AnalyticsData(
    val totalSpentPaise: Long = 0,
    val totalIncomePaise: Long = 0,
    val totalRefundPaise: Long = 0,  // ✅ Separated refund reporting
    val netSpendingPaise: Long = 0,  // ✅ Net spending after refunds
    val totalSavingsPaise: Long = 0,
    val savingsRate: Float = 0f,     // ✅ Savings rate percentage
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
