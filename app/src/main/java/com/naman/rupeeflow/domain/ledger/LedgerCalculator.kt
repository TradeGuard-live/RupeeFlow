package com.naman.rupeeflow.domain.ledger

import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.domain.util.SafeMath
import com.naman.rupeeflow.domain.util.safeSumOf
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

data class GroupLedgerResult(
    val totalTheyOweMePaise: Long,
    val totalIOweThemPaise: Long,
    val netBalancePaise: Long,
    val friendsWithLedger: List<Friend>
)

object LedgerCalculator {

    /**
     * Calculates equal shares among participant count, distributing remainder paise
     * so that the sum of shares strictly equals [totalPaise].
     */
    fun calculateEqualSplit(totalPaise: Long, count: Int): List<Long> {
        if (count <= 0 || totalPaise <= 0L) return emptyList()
        val baseShare = totalPaise / count
        val remainder = (totalPaise % count).toInt()
        return List(count) { index ->
            if (index < remainder) baseShare + 1 else baseShare
        }
    }

    /**
     * Strictly validates that percentages sum to exactly 100% (within 0.01 tolerance)
     * and no individual percentage is negative.
     */
    fun validatePercentages(percentages: List<Double>): Boolean {
        if (percentages.isEmpty()) return false
        if (percentages.any { it < 0.0 }) return false
        val sum = percentages.sum()
        return abs(sum - 100.0) < 0.01
    }

    /**
     * Calculates shares based on percentages using exact BigDecimal arithmetic.
     * Rejects invalid inputs instead of silently adjusting incorrect percentages.
     * Distributes any rounding remainder deterministically to the largest share.
     */
    fun calculatePercentageSplit(totalPaise: Long, percentages: List<Double>): List<Long> {
        if (percentages.isEmpty() || totalPaise <= 0L) return emptyList()
        require(validatePercentages(percentages)) {
            "Percentage shares must total exactly 100%. Given sum: ${percentages.sum()}%"
        }

        val totalBd = BigDecimal(totalPaise)
        val divisor = BigDecimal("100")
        val rawShares = percentages.map { pct ->
            val pctBd = BigDecimal(pct.toString())
            totalBd.multiply(pctBd).divide(divisor, 0, RoundingMode.HALF_UP).longValueExact()
        }.toMutableList()

        val sumCalculated = rawShares.sum()
        val diff = totalPaise - sumCalculated
        if (diff != 0L && rawShares.isNotEmpty()) {
            val maxIndex = rawShares.indices.maxByOrNull { rawShares[it] } ?: 0
            rawShares[maxIndex] = Math.addExact(rawShares[maxIndex], diff)
        }
        return rawShares
    }

    /**
     * Validates that participant shares strictly total the transaction amount.
     */
    fun validateParticipantShares(totalAmountPaise: Long, participants: List<TransactionParticipant>): Boolean {
        if (participants.isEmpty()) return true
        val sum = participants.sumOf { it.shareAmountPaise }
        return sum == totalAmountPaise
    }

    /**
     * Source of truth ledger calculation for all friends.
     * Computes net balance, amount they owe me, amount I owe them, and settlement status.
     * - Only processes shared expenses and shared refunds.
     * - Transfers and ordinary income are excluded from shared ledgers.
     * - Refunds reduce balances appropriately.
     * - Settlements reduce balances; reversed settlements restore balances.
     * - Archived friends retain correct historical balances.
     * - Over-settlements / negative balances are handled accurately.
     */
    fun computeFriendLedgers(
        friends: List<Friend>,
        activeTransactions: List<Transaction>,
        settlements: List<Settlement>
    ): List<Friend> {
        // Exclude ignored transactions, transfers, and plain income
        val validTransactions = activeTransactions.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    (it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.REFUND)
        }
        val validSettlements = settlements.filter { !it.isReversed }

        val theyOweMe = mutableMapOf<Long, Long>()
        val iOweThem = mutableMapOf<Long, Long>()
        val lastTxnDate = mutableMapOf<Long, Long>()

        // 1. Process Shared Transactions & Refunds
        for (txn in validTransactions) {
            val payerId = txn.paidByFriendId // null = ME

            if (txn.transactionType == TransactionType.EXPENSE) {
                if (payerId == null) {
                    // ME paid the total expense.
                    // Any participant that is a friend owes ME their share amount.
                    for (participant in txn.participants) {
                        val fId = participant.friendId ?: continue
                        val current = theyOweMe.getOrDefault(fId, 0L)
                        theyOweMe[fId] = SafeMath.safeAdd(current, participant.shareAmountPaise)
                        val prevDate = lastTxnDate.getOrDefault(fId, 0L)
                        if (txn.transactionDate > prevDate) lastTxnDate[fId] = txn.transactionDate
                    }
                } else {
                    // A Friend paid the total expense.
                    // If ME is a participant, ME owes this friend my share.
                    val myParticipant = txn.participants.firstOrNull { it.friendId == null }
                    if (myParticipant != null) {
                        val current = iOweThem.getOrDefault(payerId, 0L)
                        iOweThem[payerId] = SafeMath.safeAdd(current, myParticipant.shareAmountPaise)
                    }
                    val prevDate = lastTxnDate.getOrDefault(payerId, 0L)
                    if (txn.transactionDate > prevDate) lastTxnDate[payerId] = txn.transactionDate
                }
            } else if (txn.transactionType == TransactionType.REFUND) {
                // Shared transaction refund
                if (payerId == null) {
                    // ME received the refund -> reduces what friend participants owe ME
                    for (participant in txn.participants) {
                        val fId = participant.friendId ?: continue
                        val current = theyOweMe.getOrDefault(fId, 0L)
                        theyOweMe[fId] = SafeMath.safeSubtract(current, participant.shareAmountPaise)
                        val prevDate = lastTxnDate.getOrDefault(fId, 0L)
                        if (txn.transactionDate > prevDate) lastTxnDate[fId] = txn.transactionDate
                    }
                } else {
                    // Friend received refund -> reduces what ME owes that friend
                    val myParticipant = txn.participants.firstOrNull { it.friendId == null }
                    if (myParticipant != null) {
                        val current = iOweThem.getOrDefault(payerId, 0L)
                        iOweThem[payerId] = SafeMath.safeSubtract(current, myParticipant.shareAmountPaise)
                    }
                    val prevDate = lastTxnDate.getOrDefault(payerId, 0L)
                    if (txn.transactionDate > prevDate) lastTxnDate[payerId] = txn.transactionDate
                }
            }
        }

        // 2. Process Settlements
        for (settlement in validSettlements) {
            val payerId = settlement.payerId // null = ME
            val receiverId = settlement.receiverId // null = ME

            if (payerId != null && receiverId == null) {
                // Friend paid ME -> reduces what they owe me
                val current = theyOweMe.getOrDefault(payerId, 0L)
                theyOweMe[payerId] = SafeMath.safeSubtract(current, settlement.amountPaise)
                val prevDate = lastTxnDate.getOrDefault(payerId, 0L)
                if (settlement.settlementDate > prevDate) lastTxnDate[payerId] = settlement.settlementDate
            } else if (payerId == null && receiverId != null) {
                // ME paid Friend -> reduces what I owe them
                val current = iOweThem.getOrDefault(receiverId, 0L)
                iOweThem[receiverId] = SafeMath.safeSubtract(current, settlement.amountPaise)
                val prevDate = lastTxnDate.getOrDefault(receiverId, 0L)
                if (settlement.settlementDate > prevDate) lastTxnDate[receiverId] = settlement.settlementDate
            }
        }

        // 3. Build updated Friend models (retaining archived friends with correct historical balances)
        return friends.map { friend ->
            val grossTheyOweMe = theyOweMe.getOrDefault(friend.id, 0L)
            val grossIOweThem = iOweThem.getOrDefault(friend.id, 0L)
            // Overflow-protected net balance calculation
            val net = SafeMath.safeSubtract(grossTheyOweMe, grossIOweThem)
            val absNet = SafeMath.safeAbs(net)

            val (status, finalTheyOweMe, finalIOweThem) = when {
                net > 0L -> Triple(FriendBalanceStatus.THEY_OWE_ME, net, 0L)
                net < 0L -> Triple(FriendBalanceStatus.I_OWE_THEM, 0L, absNet)
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
     * Computes ledger summary and individual friend balances scoped to a specific group or consolidated.
     * When [groupId] is null, calculates consolidated balances across all friends.
     * When [groupId] is non-null, calculates strictly for members of that group and transactions/settlements in that scope.
     */
    fun computeGroupLedger(
        groupId: Long?,
        groupMemberIds: Set<Long>?,
        allFriends: List<Friend>,
        transactions: List<Transaction>,
        settlements: List<Settlement>
    ): GroupLedgerResult {
        if (groupId == null || groupMemberIds == null) {
            val friendsWithLedger = computeFriendLedgers(allFriends, transactions, settlements)
            val activeFriends = friendsWithLedger.filter { !it.isArchived }
            val theyOwe = activeFriends.filter { it.netBalancePaise > 0L }.safeSumOf { it.netBalancePaise }
            val iOwe = activeFriends.filter { it.netBalancePaise < 0L }.safeSumOf { SafeMath.safeAbs(it.netBalancePaise) }
            return GroupLedgerResult(
                totalTheyOweMePaise = theyOwe,
                totalIOweThemPaise = iOwe,
                netBalancePaise = SafeMath.safeSubtract(theyOwe, iOwe),
                friendsWithLedger = friendsWithLedger
            )
        }

        // Specific group selected:
        // Filter transactions:
        // If there are transactions explicitly tagged with this groupId, use those.
        // Otherwise, if no transactions are tagged with this groupId, consider transactions
        // where groupId is null and participants include group members.
        // Transactions tagged with a DIFFERENT group are ALWAYS excluded.
        val hasTaggedTransactions = transactions.any { it.groupId == groupId }
        val groupTransactions = transactions.filter { txn ->
            if (hasTaggedTransactions) {
                txn.groupId == groupId
            } else {
                txn.groupId == null && (
                    txn.participants.any { it.friendId in groupMemberIds } ||
                    txn.paidByFriendId in groupMemberIds
                )
            }
        }

        // Filter settlements to members of this group
        val groupSettlements = settlements.filter { s ->
            (s.payerId in groupMemberIds) || (s.receiverId in groupMemberIds)
        }

        // Filter friends to group members
        val memberFriends = allFriends.filter { it.id in groupMemberIds }
        val groupFriendsWithLedger = computeFriendLedgers(memberFriends, groupTransactions, groupSettlements)

        val activeGroupFriends = groupFriendsWithLedger.filter { !it.isArchived }
        val theyOwe = activeGroupFriends.filter { it.netBalancePaise > 0L }.safeSumOf { it.netBalancePaise }
        val iOwe = activeGroupFriends.filter { it.netBalancePaise < 0L }.safeSumOf { SafeMath.safeAbs(it.netBalancePaise) }

        return GroupLedgerResult(
            totalTheyOweMePaise = theyOwe,
            totalIOweThemPaise = iOwe,
            netBalancePaise = SafeMath.safeSubtract(theyOwe, iOwe),
            friendsWithLedger = groupFriendsWithLedger
        )
    }

    /**
     * Compute comprehensive financial dashboard summary.
     * Separates gross spending, ordinary income, refunds, and net spending.
     * Excludes future-dated transactions from current-period metrics.
     */
    fun computeFinancialSummary(
        transactions: List<Transaction>,
        friends: List<Friend>,
        pendingCount: Int,
        startOfMonth: Long,
        now: Long = System.currentTimeMillis()
    ): FinancialSummary {
        // Exclude ignored and future transactions
        val activeThisMonth = transactions.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    it.transactionDate >= startOfMonth &&
                    it.transactionDate <= now
        }

        val totalExpensePaise = PersonalFinancialAggregator.calculateTotalPersonalExpense(activeThisMonth)
        val totalIncomePaise = PersonalFinancialAggregator.calculateTotalPersonalIncome(activeThisMonth)
        val totalRefundPaise = PersonalFinancialAggregator.calculateTotalPersonalRefund(activeThisMonth)
        val cashSpendingPaise = PersonalFinancialAggregator.calculatePersonalCashSpending(activeThisMonth)
        val onlineSpendingPaise = PersonalFinancialAggregator.calculatePersonalOnlineSpending(activeThisMonth)
        val netSpending = PersonalFinancialAggregator.calculatePersonalNetSpending(activeThisMonth)

        val totalOwedToMe = friends.filter { it.netBalancePaise > 0L }.safeSumOf { it.netBalancePaise }
        val totalIOwe = friends.filter { it.netBalancePaise < 0L }.safeSumOf { SafeMath.safeAbs(it.netBalancePaise) }
        val netPosition = SafeMath.safeSubtract(totalOwedToMe, totalIOwe)

        // Daily average based on elapsed days in current month
        val daysElapsed = kotlin.math.max(
            1,
            ((now - startOfMonth) / (1000 * 60 * 60 * 24)).toInt() + 1
        )
        val dailyAverage = netSpending / daysElapsed

        return FinancialSummary(
            totalExpenseThisMonthPaise = totalExpensePaise,
            totalIncomeThisMonthPaise = totalIncomePaise,
            totalRefundThisMonthPaise = totalRefundPaise,
            netSpendingPaise = netSpending,
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
