package com.naman.rupeeflow.domain.model

enum class TransactionType {
    EXPENSE,
    INCOME,
    REFUND,
    TRANSFER,
    DEBT_RECEIVED,
    DEBT_PAID,
    ADJUSTMENT,
    UPI_LITE_TOPUP,
    UPI_LITE_PAYMENT,
    UPI_LITE_REFUND,
    UPI_LITE_REVERSAL,
    BANK_CHARGES,
    INVESTMENT;

    val displayName: String
        get() = when (this) {
            EXPENSE -> "Expense"
            INCOME -> "Income"
            REFUND -> "Refund"
            TRANSFER -> "Transfer"
            DEBT_RECEIVED -> "Debt Received"
            DEBT_PAID -> "Debt Paid"
            ADJUSTMENT -> "Adjustment"
            UPI_LITE_TOPUP -> "UPI Lite Top-up"
            UPI_LITE_PAYMENT -> "UPI Lite Payment"
            UPI_LITE_REFUND -> "UPI Lite Refund"
            UPI_LITE_REVERSAL -> "UPI Lite Reversal"
            BANK_CHARGES -> "Bank Charges"
            INVESTMENT -> "Investment"
        }
}

enum class PaymentMethod {
    UPI,
    UPI_LITE,
    CASH,
    DEBIT_CARD,
    CREDIT_CARD,
    BANK_TRANSFER,
    NET_BANKING,
    WALLET,
    OTHER;

    val displayName: String
        get() = when (this) {
            UPI -> "UPI"
            UPI_LITE -> "UPI Lite"
            CASH -> "Cash"
            DEBIT_CARD -> "Debit Card"
            CREDIT_CARD -> "Credit Card"
            BANK_TRANSFER -> "Bank Transfer"
            NET_BANKING -> "Net Banking"
            WALLET -> "Wallet"
            OTHER -> "Other"
        }
}

enum class SplitMethod {
    EQUAL,
    EXACT_AMOUNT,
    PERCENTAGE,
    SHARES,
    CUSTOM;

    val displayName: String
        get() = when (this) {
            EQUAL -> "Equal Split"
            EXACT_AMOUNT -> "Exact Amount"
            PERCENTAGE -> "Percentage"
            SHARES -> "Shares / Weights"
            CUSTOM -> "Custom Split"
        }
}

enum class GroupType {
    TRIP,
    HOME,
    FRIENDS,
    COUPLE,
    FAMILY,
    OFFICE,
    CUSTOM;

    val displayName: String
        get() = when (this) {
            TRIP -> "Trip"
            HOME -> "Home"
            FRIENDS -> "Friends"
            COUPLE -> "Couple"
            FAMILY -> "Family"
            OFFICE -> "Office"
            CUSTOM -> "Custom"
        }
}

enum class GroupActivityType {
    GROUP_CREATED,
    GROUP_UPDATED,
    GROUP_ARCHIVED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    EXPENSE_CREATED,
    EXPENSE_EDITED,
    EXPENSE_DELETED,
    SETTLEMENT_RECORDED,
    SETTLEMENT_REVERSED,
    SETTLEMENT_RESTORED;

    val displayName: String
        get() = when (this) {
            GROUP_CREATED -> "Group created"
            GROUP_UPDATED -> "Group updated"
            GROUP_ARCHIVED -> "Group archived"
            MEMBER_ADDED -> "Member added"
            MEMBER_REMOVED -> "Member removed"
            EXPENSE_CREATED -> "Expense added"
            EXPENSE_EDITED -> "Expense updated"
            EXPENSE_DELETED -> "Expense removed"
            SETTLEMENT_RECORDED -> "Payment recorded"
            SETTLEMENT_REVERSED -> "Payment reversed"
            SETTLEMENT_RESTORED -> "Payment restored"
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
    IGNORED,
    UNKNOWN
}

enum class LocationSource {
    DEVICE_CURRENT,
    DEVICE_AT_DETECTION,
    MANUAL_MAP_SELECTION,
    MANUAL_COORDINATES,
    MANUAL_MAPS_LINK,
    HISTORICAL_LOCATION_SUGGESTION;

    val displayName: String
        get() = when (this) {
            DEVICE_CURRENT -> "Current GPS Location"
            DEVICE_AT_DETECTION -> "Captured at SMS Detection"
            MANUAL_MAP_SELECTION -> "Map Selection"
            MANUAL_COORDINATES -> "Manual Coordinates"
            MANUAL_MAPS_LINK -> "Google Maps Link"
            HISTORICAL_LOCATION_SUGGESTION -> "Merchant Location Memory"
        }
}

enum class LocationCaptureMode {
    OFF,
    ON_REVIEW,
    ON_DETECTION;

    val displayName: String
        get() = when (this) {
            OFF -> "Off"
            ON_REVIEW -> "When reviewing a detected transaction"
            ON_DETECTION -> "When a transaction is detected"
        }
}

enum class FriendBalanceStatus {
    THEY_OWE_ME,
    I_OWE_THEM,
    SETTLED
}

enum class ReportPeriodType {
    WEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM;

    val displayName: String
        get() = when (this) {
            WEEKLY -> "Weekly"
            MONTHLY -> "Monthly"
            YEARLY -> "Yearly"
            CUSTOM -> "Custom Range"
        }
}

enum class SavingsGoalCategory {
    EMERGENCY_FUND,
    ELECTRONICS,
    TRAVEL,
    EDUCATION,
    VEHICLE,
    SHOPPING,
    PERSONAL,
    CUSTOM;

    val displayName: String
        get() = when (this) {
            EMERGENCY_FUND -> "Emergency Fund"
            ELECTRONICS -> "Electronics"
            TRAVEL -> "Travel"
            EDUCATION -> "Education"
            VEHICLE -> "Vehicle"
            SHOPPING -> "Shopping"
            PERSONAL -> "Personal"
            CUSTOM -> "Custom"
        }
}

enum class SavingsGoalStatus {
    ACTIVE,
    PAUSED,
    COMPLETED,
    ARCHIVED;

    val displayName: String
        get() = when (this) {
            ACTIVE -> "Active"
            PAUSED -> "Paused"
            COMPLETED -> "Completed"
            ARCHIVED -> "Archived"
        }
}

enum class SavingsGoalPriority {
    HIGH,
    MEDIUM,
    LOW;

    val displayName: String
        get() = when (this) {
            HIGH -> "High"
            MEDIUM -> "Medium"
            LOW -> "Low"
        }
}

enum class SavingsEntryType {
    DEPOSIT,
    WITHDRAWAL;

    val displayName: String
        get() = when (this) {
            DEPOSIT -> "Deposit"
            WITHDRAWAL -> "Withdrawal"
        }
}

