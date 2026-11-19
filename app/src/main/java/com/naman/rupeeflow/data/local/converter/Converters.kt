package com.naman.rupeeflow.data.local.converter

import androidx.room.TypeConverter
import com.naman.rupeeflow.domain.model.*

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType =
        runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod =
        runCatching { PaymentMethod.valueOf(value) }.getOrDefault(PaymentMethod.UPI)

    @TypeConverter
    fun fromSplitMethod(value: SplitMethod): String = value.name

    @TypeConverter
    fun toSplitMethod(value: String): SplitMethod =
        runCatching { SplitMethod.valueOf(value) }.getOrDefault(SplitMethod.EQUAL)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name

    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus =
        runCatching { TransactionStatus.valueOf(value) }.getOrDefault(TransactionStatus.ACTIVE)

    @TypeConverter
    fun fromTransactionSource(value: TransactionSource): String = value.name

    @TypeConverter
    fun toTransactionSource(value: String): TransactionSource =
        runCatching { TransactionSource.valueOf(value) }.getOrDefault(TransactionSource.MANUAL)

    @TypeConverter
    fun fromPendingStatus(value: PendingStatus): String = value.name

    @TypeConverter
    fun toPendingStatus(value: String): PendingStatus =
        runCatching { PendingStatus.valueOf(value) }.getOrDefault(PendingStatus.PENDING)

    @TypeConverter
    fun fromLocationSource(value: LocationSource?): String? = value?.name

    @TypeConverter
    fun toLocationSource(value: String?): LocationSource? =
        value?.let { runCatching { LocationSource.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromSavingsGoalCategory(value: SavingsGoalCategory): String = value.name

    @TypeConverter
    fun toSavingsGoalCategory(value: String): SavingsGoalCategory =
        runCatching { SavingsGoalCategory.valueOf(value) }.getOrDefault(SavingsGoalCategory.CUSTOM)

    @TypeConverter
    fun fromSavingsGoalStatus(value: SavingsGoalStatus): String = value.name

    @TypeConverter
    fun toSavingsGoalStatus(value: String): SavingsGoalStatus =
        runCatching { SavingsGoalStatus.valueOf(value) }.getOrDefault(SavingsGoalStatus.ACTIVE)

    @TypeConverter
    fun fromSavingsGoalPriority(value: SavingsGoalPriority): String = value.name

    @TypeConverter
    fun toSavingsGoalPriority(value: String): SavingsGoalPriority =
        runCatching { SavingsGoalPriority.valueOf(value) }.getOrDefault(SavingsGoalPriority.MEDIUM)

    @TypeConverter
    fun fromSavingsEntryType(value: SavingsEntryType): String = value.name

    @TypeConverter
    fun toSavingsEntryType(value: String): SavingsEntryType =
        runCatching { SavingsEntryType.valueOf(value) }.getOrDefault(SavingsEntryType.DEPOSIT)

    @TypeConverter
    fun fromFriendUpiVerificationStatus(value: FriendUpiVerificationStatus): String = value.name

    @TypeConverter
    fun toFriendUpiVerificationStatus(value: String): FriendUpiVerificationStatus =
        runCatching { FriendUpiVerificationStatus.valueOf(value) }.getOrDefault(FriendUpiVerificationStatus.UNVERIFIED)

    @TypeConverter
    fun fromSettlementPaymentStatus(value: SettlementPaymentStatus): String = value.name

    @TypeConverter
    fun toSettlementPaymentStatus(value: String): SettlementPaymentStatus =
        runCatching { SettlementPaymentStatus.valueOf(value) }.getOrDefault(SettlementPaymentStatus.SUCCESS)

    @TypeConverter
    fun fromSettlementVerificationStatus(value: SettlementVerificationStatus): String = value.name

    @TypeConverter
    fun toSettlementVerificationStatus(value: String): SettlementVerificationStatus =
        runCatching { SettlementVerificationStatus.valueOf(value) }.getOrDefault(SettlementVerificationStatus.MANUAL_UNVERIFIED)
}
