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
     * Calculates shares based on integer weights/shares (e.g. 1 share, 2 shares).
     * Distributes any remainder paise deterministically so the sum strictly equals [totalPaise].
     */
    fun calculateSharesSplit(totalPaise: Long, weights: List<Int>): List<Long> {
        if (weights.isEmpty() || totalPaise <= 0L) return emptyList()
        require(weights.none { it < 0 }) { "Weights cannot be negative" }
        val totalWeight = weights.sum()
        require(totalWeight > 0) { "Sum of weights must be greater than zero" }

        val rawShares = weights.map { w ->
            (totalPaise * w) / totalWeight
        }.toMutableList()

        val sumCalculated = rawShares.sum()
        val remainder = (totalPaise - sumCalculated).toInt()
        for (i in 0 until remainder) {
            rawShares[i % rawShares.size] += 1L
        }
        return rawShares
    }

    /**
     * Solves minimal cash transfers to settle all group debts (min cash-flow algorithm).
     * Preserves the exact net balance of every member down to the exact paisa (0 remainder).
     * Uses integer paise arithmetic strictly.
     */
    fun simplifyDebts(
        netBalances: Map<Long?, Long>,
        memberNames: Map<Long?, String>
    ): List<SimplifiedDebt> {
        data class BalanceEntry(val memberId: Long?, var amountPaise: Long)

        val debtors = mutableListOf<BalanceEntry>()
        val creditors = mutableListOf<BalanceEntry>()

        for ((mId, net) in netBalances) {
            if (net < 0L) {
                debtors.add(BalanceEntry(mId, SafeMath.safeAbs(net)))
            } else if (net > 0L) {
                creditors.add(BalanceEntry(mId, net))
            }
        }

        val simplified = mutableListOf<SimplifiedDebt>()

        // Greedy matching of largest debtor with largest creditor
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortByDescending { it.amountPaise }
            creditors.sortByDescending { it.amountPaise }

            val debtor = debtors.first()
            val creditor = creditors.first()

            val transfer = minOf(debtor.amountPaise, creditor.amountPaise)
            if (transfer > 0L) {
                val fromName = memberNames[debtor.memberId] ?: if (debtor.memberId == null) "You" else "Member #${debtor.memberId}"
                val toName = memberNames[creditor.memberId] ?: if (creditor.memberId == null) "You" else "Member #${creditor.memberId}"
                simplified.add(
                    SimplifiedDebt(
                        fromMemberId = debtor.memberId,
                        fromMemberName = fromName,
                        toMemberId = creditor.memberId,
                        toMemberName = toName,
                        amountPaise = transfer
                    )
                )
            }

            debtor.amountPaise -= transfer
            creditor.amountPaise -= transfer

            if (debtor.amountPaise <= 0L) {
                debtors.removeAt(0)
            }
            if (creditor.amountPaise <= 0L) {
                creditors.removeAt(0)
            }
        }

        return simplified
    }

    /**
     * Computes full financial breakdown for a Split Pro group:
     * - Total group expenses
     * - User's total paid and personal share
     * - Member balances (who paid what, who consumed what, net balances)
     * - Direct pairwise debts
     * - Simplified minimal cash flow transfers
     */
    fun computeGroupBalances(
        groupId: Long,
        transactions: List<Transaction>,
        settlements: List<Settlement>,
        members: List<Friend>
    ): GroupBalanceSummary {
        val groupTxns = transactions.filter {
            (it.expenseGroupId == null || it.expenseGroupId == groupId) &&
                    it.transactionStatus == TransactionStatus.ACTIVE &&
                    (it.transactionType == TransactionType.EXPENSE ||
                     it.transactionType == TransactionType.UPI_LITE_PAYMENT ||
                     it.transactionType == TransactionType.REFUND ||
                     it.transactionType == TransactionType.UPI_LITE_REFUND)
        }
        val groupSettlements = settlements.filter { (it.groupId == null || it.groupId == groupId) && !it.isReversed }

        val memberMap = members.associateBy { it.id }
        val memberNames = mutableMapOf<Long?, String>()
        memberNames[null] = "You"
        for (f in members) {
            memberNames[f.id] = f.name
        }

        val allMemberIds = mutableSetOf<Long?>()
        allMemberIds.add(null) // "You" is always a member
        for (f in members) allMemberIds.add(f.id)

        // Include any participants from transactions or settlements
        for (t in groupTxns) {
            allMemberIds.add(t.paidByFriendId)
            for (p in t.participants) {
                allMemberIds.add(p.friendId)
            }
        }
        for (s in groupSettlements) {
            allMemberIds.add(s.payerId)
            allMemberIds.add(s.receiverId)
        }

        // Initialize ledger tracking
        val paidMap = mutableMapOf<Long?, Long>()
        val shareMap = mutableMapOf<Long?, Long>()
        for (m in allMemberIds) {
            paidMap[m] = 0L
            shareMap[m] = 0L
            if (!memberNames.containsKey(m)) {
                memberNames[m] = memberMap[m]?.name ?: "Friend #$m"
            }
        }

        // Pairwise net debt: pair (from, to) -> amount from owes to
        val pairwiseDebt = mutableMapOf<Pair<Long?, Long?>, Long>()

        var totalGroupExpensePaise = 0L

        // Process Transactions
        for (t in groupTxns) {
            val payer = t.paidByFriendId
            val isExpense = t.transactionType == TransactionType.EXPENSE || t.transactionType == TransactionType.UPI_LITE_PAYMENT
            val isRefund = t.transactionType == TransactionType.REFUND || t.transactionType == TransactionType.UPI_LITE_REFUND
            val factor = if (isExpense) 1L else -1L

            if (isExpense) {
                totalGroupExpensePaise = SafeMath.safeAdd(totalGroupExpensePaise, t.amountPaise)
            } else if (isRefund) {
                totalGroupExpensePaise = SafeMath.safeSubtract(totalGroupExpensePaise, t.amountPaise)
            }

            // Payer contributed t.amountPaise
            val currentPaid = paidMap[payer] ?: 0L
            paidMap[payer] = SafeMath.safeAdd(currentPaid, t.amountPaise * factor)

            if (t.participants.isNotEmpty()) {
                for (p in t.participants) {
                    val pMember = p.friendId
                    val curShare = shareMap[pMember] ?: 0L
                    shareMap[pMember] = SafeMath.safeAdd(curShare, p.shareAmountPaise * factor)

                    // If participant is not the payer, participant owes payer
                    if (pMember != payer) {
                        val key = Pair(pMember, payer)
                        val curPairDebt = pairwiseDebt[key] ?: 0L
                        pairwiseDebt[key] = SafeMath.safeAdd(curPairDebt, p.shareAmountPaise * factor)
                    }
                }
            } else {
                // Single payer consumed 100% of the expense
                val curShare = shareMap[payer] ?: 0L
                shareMap[payer] = SafeMath.safeAdd(curShare, t.amountPaise * factor)
            }
        }

        // Process Settlements
        for (s in groupSettlements) {
            val p = s.payerId
            val r = s.receiverId
            val amt = s.amountPaise

            // Payer increased contribution by paying debt
            paidMap[p] = SafeMath.safeAdd(paidMap[p] ?: 0L, amt)
            // Receiver reduced contribution by collecting debt
            paidMap[r] = SafeMath.safeSubtract(paidMap[r] ?: 0L, amt)

            // Direct debt between p and r reduced
            val key = Pair(p, r)
            pairwiseDebt[key] = SafeMath.safeSubtract(pairwiseDebt[key] ?: 0L, amt)
        }

        // Calculate member balances
        val netBalances = mutableMapOf<Long?, Long>()
        val memberBalances = allMemberIds.map { mId ->
            val paid = paidMap[mId] ?: 0L
            val share = shareMap[mId] ?: 0L
            val net = paid - share
            netBalances[mId] = net
            GroupMemberBalance(
                memberId = mId,
                memberName = memberNames[mId] ?: (if (mId == null) "You" else "Friend #$mId"),
                totalPaidPaise = paid,
                totalSharePaise = share,
                netBalancePaise = net
            )
        }.sortedWith(compareBy({ it.memberId != null }, { it.memberName }))

        // Calculate simplified debts
        val simplified = simplifyDebts(netBalances, memberNames)

        // Calculate net direct pairwise debts
        val directDebts = mutableListOf<SimplifiedDebt>()
        val processedPairs = mutableSetOf<Pair<Long?, Long?>>()

        for (m1 in allMemberIds) {
            for (m2 in allMemberIds) {
                if (m1 == m2) continue
                val pair1 = Pair(m1, m2)
                val pair2 = Pair(m2, m1)
                if (processedPairs.contains(pair1) || processedPairs.contains(pair2)) continue
                processedPairs.add(pair1)
                processedPairs.add(pair2)

                val debt1to2 = pairwiseDebt[pair1] ?: 0L
                val debt2to1 = pairwiseDebt[pair2] ?: 0L
                val netDiff = SafeMath.safeSubtract(debt1to2, debt2to1)

                if (netDiff > 0L) {
                    directDebts.add(
                        SimplifiedDebt(
                            fromMemberId = m1,
                            fromMemberName = memberNames[m1] ?: "Friend",
                            toMemberId = m2,
                            toMemberName = memberNames[m2] ?: "Friend",
                            amountPaise = netDiff
                        )
                    )
                } else if (netDiff < 0L) {
                    directDebts.add(
                        SimplifiedDebt(
                            fromMemberId = m2,
                            fromMemberName = memberNames[m2] ?: "Friend",
                            toMemberId = m1,
                            toMemberName = memberNames[m1] ?: "Friend",
                            amountPaise = SafeMath.safeAbs(netDiff)
                        )
                    )
                }
            }
        }

        val myPaid = paidMap[null] ?: 0L
        val myShare = shareMap[null] ?: 0L
        val myNet = netBalances[null] ?: 0L

        return GroupBalanceSummary(
            totalExpensePaise = totalGroupExpensePaise.coerceAtLeast(0L),
            myPaidPaise = myPaid,
            mySharePaise = myShare,
            myNetBalancePaise = myNet,
            memberBalances = memberBalances,
            directDebts = directDebts.sortedByDescending { it.amountPaise },
            simplifiedDebts = simplified.sortedByDescending { it.amountPaise }
        )
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

    // Calculate outstanding/unsettled expenses for a friend using deterministic FIFO allocation
    fun calculateUnsettledExpenses(
        friendId: Long,
        transactions: List<Transaction>,
        settlements: List<Settlement>
    ): List<UnsettledExpenseItem> {
        // Filter active transactions
        val activeTxns = transactions.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    (it.transactionType == TransactionType.EXPENSE || it.transactionType == TransactionType.REFUND)
        }
        val validSettlements = settlements.filter { !it.isReversed }

        // Group 1: Transactions where ME paid and Friend participated (Friend owes ME)
        val friendOwesMeExpenses = mutableListOf<UnsettledExpenseEntry>()
        // Group 2: Transactions where Friend paid and ME participated (ME owes Friend)
        val iOweFriendExpenses = mutableListOf<UnsettledExpenseEntry>()

        for (txn in activeTxns) {
            val payerId = txn.paidByFriendId
            if (txn.transactionType == TransactionType.EXPENSE) {
                if (payerId == null) {
                    val part = txn.participants.firstOrNull { it.friendId == friendId }
                    if (part != null && part.shareAmountPaise > 0L) {
                        friendOwesMeExpenses.add(
                            UnsettledExpenseEntry(txn, part.shareAmountPaise, ExpenseObligationDirection.THEY_OWE_ME)
                        )
                    }
                } else if (payerId == friendId) {
                    val myPart = txn.participants.firstOrNull { it.friendId == null }
                    if (myPart != null && myPart.shareAmountPaise > 0L) {
                        iOweFriendExpenses.add(
                            UnsettledExpenseEntry(txn, myPart.shareAmountPaise, ExpenseObligationDirection.I_OWE_THEM)
                        )
                    }
                }
            } else if (txn.transactionType == TransactionType.REFUND) {
                if (payerId == null) {
                    val part = txn.participants.firstOrNull { it.friendId == friendId }
                    if (part != null && part.shareAmountPaise > 0L) {
                        friendOwesMeExpenses.add(
                            UnsettledExpenseEntry(txn, -part.shareAmountPaise, ExpenseObligationDirection.THEY_OWE_ME)
                        )
                    }
                } else if (payerId == friendId) {
                    val myPart = txn.participants.firstOrNull { it.friendId == null }
                    if (myPart != null && myPart.shareAmountPaise > 0L) {
                        iOweFriendExpenses.add(
                            UnsettledExpenseEntry(txn, -myPart.shareAmountPaise, ExpenseObligationDirection.I_OWE_THEM)
                        )
                    }
                }
            }
        }

        // Sort chronologically (oldest first for FIFO allocation)
        friendOwesMeExpenses.sortBy { it.transaction.transactionDate }
        iOweFriendExpenses.sortBy { it.transaction.transactionDate }

        // Settlement pools
        var poolFriendPaidMe = validSettlements
            .filter { it.payerId == friendId && it.receiverId == null }
            .sumOf { it.amountPaise }
        var poolIPaidFriend = validSettlements
            .filter { it.payerId == null && it.receiverId == friendId }
            .sumOf { it.amountPaise }

        // Handle mutual expense offsets
        val grossTheyOweMeExpenses = friendOwesMeExpenses.filter { it.shareAmountPaise > 0L }.sumOf { it.shareAmountPaise }
        val grossIOweThemExpenses = iOweFriendExpenses.filter { it.shareAmountPaise > 0L }.sumOf { it.shareAmountPaise }
        val mutualOffset = minOf(grossTheyOweMeExpenses, grossIOweThemExpenses)
        poolFriendPaidMe = SafeMath.safeAdd(poolFriendPaidMe, mutualOffset)
        poolIPaidFriend = SafeMath.safeAdd(poolIPaidFriend, mutualOffset)

        val result = mutableListOf<UnsettledExpenseItem>()

        // Allocate to Group 1 (Friend owes ME)
        for (entry in friendOwesMeExpenses) {
            if (entry.shareAmountPaise <= 0L) continue
            val share = entry.shareAmountPaise
            val allocated = minOf(poolFriendPaidMe, share)
            val remaining = SafeMath.safeSubtract(share, allocated)
            poolFriendPaidMe = SafeMath.safeSubtract(poolFriendPaidMe, allocated)

            if (remaining > 0L) {
                result.add(
                    UnsettledExpenseItem(
                        transaction = entry.transaction,
                        totalTransactionAmountPaise = entry.transaction.amountPaise,
                        originalShareAmountPaise = share,
                        settledAmountPaise = allocated,
                        remainingOutstandingPaise = remaining,
                        isPartiallySettled = allocated > 0L,
                        direction = ExpenseObligationDirection.THEY_OWE_ME
                    )
                )
            }
        }

        // Allocate to Group 2 (ME owes Friend)
        for (entry in iOweFriendExpenses) {
            if (entry.shareAmountPaise <= 0L) continue
            val share = entry.shareAmountPaise
            val allocated = minOf(poolIPaidFriend, share)
            val remaining = SafeMath.safeSubtract(share, allocated)
            poolIPaidFriend = SafeMath.safeSubtract(poolIPaidFriend, allocated)

            if (remaining > 0L) {
                result.add(
                    UnsettledExpenseItem(
                        transaction = entry.transaction,
                        totalTransactionAmountPaise = entry.transaction.amountPaise,
                        originalShareAmountPaise = share,
                        settledAmountPaise = allocated,
                        remainingOutstandingPaise = remaining,
                        isPartiallySettled = allocated > 0L,
                        direction = ExpenseObligationDirection.I_OWE_THEM
                    )
                )
            }
        }

        // Return sorted by date descending for UI presentation
        return result.sortedByDescending { it.transaction.transactionDate }
    }

    private data class UnsettledExpenseEntry(
        val transaction: Transaction,
        val shareAmountPaise: Long,
        val direction: ExpenseObligationDirection
    )
}
