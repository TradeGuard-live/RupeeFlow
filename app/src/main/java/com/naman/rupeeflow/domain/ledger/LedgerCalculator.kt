package com.naman.rupeeflow.domain.ledger

import com.naman.rupeeflow.domain.model.*
import kotlin.math.abs
import kotlin.math.roundToLong

object LedgerCalculator {

    /**
     * Calculates equal shares among participant count, distributing remainder paise
     * so that the sum of shares strictly equals [totalPaise].
     */
    fun calculateEqualSplit(totalPaise: Long, count: Int): List<Long> {
        if (count <= 0) return emptyList()
        val baseShare = totalPaise / count
        val remainder = (totalPaise % count).toInt()
        return List(count) { index ->
            if (index < remainder) baseShare + 1 else baseShare
        }
    }

    /**
     * Calculates shares based on percentages, adjusting rounding on the largest share
     * so that the sum of shares strictly equals [totalPaise].
     */
    fun calculatePercentageSplit(totalPaise: Long, percentages: List<Double>): List<Long> {
        if (percentages.isEmpty()) return emptyList()
        val rawShares = percentages.map { pct ->
            ((totalPaise * pct) / 100.0).roundToLong()
        }.toMutableList()

        val sumCalculated = rawShares.sum()
        val diff = totalPaise - sumCalculated
        if (diff != 0L && rawShares.isNotEmpty()) {
            val maxIndex = rawShares.indices.maxByOrNull { rawShares[it] } ?: 0
            rawShares[maxIndex] = rawShares[maxIndex] + diff
        }
        return rawShares
    }

    /**
     * Source of truth ledger calculation for all friends.
     * Computes net balance, amount they owe me, amount I owe them, and settlement status.
     */
    fun computeFriendLedgers(
        friends: List<Friend>,
        activeTransactions: List<Transaction>,
        settlements: List<Settlement>
    ): List<Friend> {
        // Exclude ignored transactions
        val validTransactions = activeTransactions.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionType != TransactionType.TRANSFER
        }
        val validSettlements = settlements.filter { !it.isReversed }

        val friendMap = friends.associateBy { it.id }.toMutableMap()
        val theyOweMe = mutableMapOf<Long, Long>()
        val iOweThem = mutableMapOf<Long, Long>()
        val lastTxnDate = mutableMapOf<Long, Long>()

        // 1. Process Shared Transactions
        for (txn in validTransactions) {
            val payerId = txn.paidByFriendId // null = ME

            if (payerId == null) {
                // ME paid the total expense.
                // Any participant that is a friend owes ME their share amount.
                for (participant in txn.participants) {
                    val fId = participant.friendId ?: continue
                    val current = theyOweMe.getOrDefault(fId, 0L)
                    theyOweMe[fId] = current + participant.shareAmountPaise
                    val prevDate = lastTxnDate.getOrDefault(fId, 0L)
                    if (txn.transactionDate > prevDate) lastTxnDate[fId] = txn.transactionDate
                }
            } else {
                // A Friend paid the total expense.
                // If ME is a participant, ME owes this friend my share.
                val myParticipant = txn.participants.firstOrNull { it.friendId == null }
                if (myParticipant != null) {
                    val current = iOweThem.getOrDefault(payerId, 0L)
                    iOweThem[payerId] = current + myParticipant.shareAmountPaise
                }
                val prevDate = lastTxnDate.getOrDefault(payerId, 0L)
                if (txn.transactionDate > prevDate) lastTxnDate[payerId] = txn.transactionDate
            }
        }

        // 2. Process Settlements
        for (settlement in validSettlements) {
            val payerId = settlement.payerId // null = ME
            val receiverId = settlement.receiverId // null = ME

            if (payerId != null && receiverId == null) {
                // Friend paid ME -> reduces what they owe me
                val current = theyOweMe.getOrDefault(payerId, 0L)
                theyOweMe[payerId] = current - settlement.amountPaise
                val prevDate = lastTxnDate.getOrDefault(payerId, 0L)
                if (settlement.settlementDate > prevDate) lastTxnDate[payerId] = settlement.settlementDate
            } else if (payerId == null && receiverId != null) {
                // ME paid Friend -> reduces what I owe them
                val current = iOweThem.getOrDefault(receiverId, 0L)
                iOweThem[receiverId] = current - settlement.amountPaise
                val prevDate = lastTxnDate.getOrDefault(receiverId, 0L)
                if (settlement.settlementDate > prevDate) lastTxnDate[receiverId] = settlement.settlementDate
            }
        }

        // 3. Build updated Friend models
        return friends.map { friend ->
            val grossTheyOweMe = theyOweMe.getOrDefault(friend.id, 0L)
            val grossIOweThem = iOweThem.getOrDefault(friend.id, 0L)
            val net = grossTheyOweMe - grossIOweThem

            val (status, finalTheyOweMe, finalIOweThem) = when {
                net > 0 -> Triple(FriendBalanceStatus.THEY_OWE_ME, net, 0L)
                net < 0 -> Triple(FriendBalanceStatus.I_OWE_THEM, 0L, abs(net))
                else -> Triple(FriendBalanceStatus.SETTLED, 0L, 0L)
            }

            friend.copy(
                totalTheyOweMePaise = finalTheyOweMe,
                totalIOweThemPaise = finalIOweThem,
                netBalancePaise = net,
                lastTransactionDate = lastTxnDate[friend.id] ?: friend.lastTransactionDate,
                status = status
            )
        }
    }

    /**
     * Compute comprehensive financial dashboard summary.
     */
    fun computeFinancialSummary(
        transactions: List<Transaction>,
        friends: List<Friend>,
        pendingCount: Int,
        startOfMonth: Long,
        now: Long = System.currentTimeMillis()
    ): FinancialSummary {
        val activeThisMonth = transactions.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate >= startOfMonth &&
                    it.transactionDate <= now
        }

        var totalExpensePaise = 0L
        var totalIncomePaise = 0L
        var cashSpendingPaise = 0L
        var onlineSpendingPaise = 0L

        for (txn in activeThisMonth) {
            when (txn.transactionType) {
                TransactionType.EXPENSE -> {
                    // Only count the user's personal expense share; exclude friends' portion
                    val myShare = txn.userExpensePaise
                    totalExpensePaise += myShare

                    if (txn.paymentMethod == PaymentMethod.CASH) {
                        cashSpendingPaise += myShare
                    } else {
                        onlineSpendingPaise += myShare
                    }
                }
                TransactionType.INCOME, TransactionType.REFUND -> {
                    totalIncomePaise += txn.amountPaise
                }
                else -> {
                    // Transfers and adjustments are excluded from spending/income
                }
            }
        }

        val totalOwedToMe = friends.filter { it.netBalancePaise > 0 }.sumOf { it.netBalancePaise }
        val totalIOwe = friends.filter { it.netBalancePaise < 0 }.sumOf { abs(it.netBalancePaise) }
        val netPosition = totalOwedToMe - totalIOwe

        // Daily average
        val daysElapsed = kotlin.math.max(
            1,
            ((now - startOfMonth) / (1000 * 60 * 60 * 24)).toInt() + 1
        )
        val dailyAverage = totalExpensePaise / daysElapsed

        return FinancialSummary(
            totalExpenseThisMonthPaise = totalExpensePaise,
            totalIncomeThisMonthPaise = totalIncomePaise,
            totalCashSpendingPaise = cashSpendingPaise,
            totalOnlineSpendingPaise = onlineSpendingPaise,
            totalOwedToMePaise = totalOwedToMe,
            totalIOwePaise = totalIOwe,
            netReceivablesPayablesPaise = netPosition,
            averageDailySpendingPaise = dailyAverage,
            pendingTransactionsCount = pendingCount
        )
    }
}
