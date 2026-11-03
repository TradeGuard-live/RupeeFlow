package com.naman.rupeeflow.domain.model

enum class TransactionType {
    EXPENSE,
    INCOME,
    REFUND,
    TRANSFER,
    DEBT_RECEIVED,
    DEBT_PAID,
    ADJUSTMENT;

    val displayName: String
        get() = when (this) {
            EXPENSE -> "Expense"
            INCOME -> "Income"
            REFUND -> "Refund"
            TRANSFER -> "Transfer"
            DEBT_RECEIVED -> "Debt Received"
            DEBT_PAID -> "Debt Paid"
            ADJUSTMENT -> "Adjustment"
        }
}

enum class PaymentMethod {
    UPI,
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    BANK_TRANSFER,
    NET_BANKING,
    OTHER;

    val displayName: String
        get() = when (this) {
            UPI -> "UPI"
            CASH -> "Cash"
            DEBIT_CARD -> "Debit Card"
            CREDIT_CARD -> "Credit Card"
            BANK_TRANSFER -> "Bank Transfer"
            NET_BANKING -> "Net Banking"
            OTHER -> "Other"
        }
}

enum class SplitMethod {
    EQUAL,
    EXACT_AMOUNT,
    PERCENTAGE;

    val displayName: String
        get() = when (this) {
            EQUAL -> "Equal Split"
            EXACT_AMOUNT -> "Custom Exact"
            PERCENTAGE -> "Percentage"
        }
}

enum class TransactionStatus {
    ACTIVE,
    IGNORED
}

enum class TransactionSource {
    MANUAL,
    SMS,
    NOTIFICATION,
    IMPORTED
}

enum class PendingStatus {
    PENDING,
    RESOLVED,
    IGNORED
}

enum class FriendBalanceStatus {
    THEY_OWE_ME,
    I_OWE_THEM,
    SETTLED
}
