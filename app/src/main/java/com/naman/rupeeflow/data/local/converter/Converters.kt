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
}
