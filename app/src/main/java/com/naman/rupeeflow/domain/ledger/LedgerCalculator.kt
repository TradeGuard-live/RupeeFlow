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
     * Calculates item-wise shares for participants.
     * For each item, divides the item's total amount equally among its assigned participants,
     * distributing remainder paise deterministically.
     * Aggregates each participant's shares across all items.
     * Strictly guarantees that sum of participant shares == sum of item total amounts.
     */
    fun calculateItemWiseShares(
        items: List<TransactionItem>,
        allParticipantIds: List<Long?>
    ): Map<Long?, Long> {
        val resultMap = allParticipantIds.associateWith { 0L }.toMutableMap()
        for (item in items) {
            val assignedIds = item.allocations.map { it.friendId }
            if (assignedIds.isNotEmpty() && item.totalAmountPaise > 0L) {
                val splits = calculateEqualSplit(item.totalAmountPaise, assignedIds.size)
                assignedIds.forEachIndexed { index, pId ->
                    val cur = resultMap[pId] ?: 0L
                    resultMap[pId] = cur + splits[index]
                }
            }
        }
        return resultMap
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

        // Greedy matching of largest debtor with largest creditor, with deterministic tie-breaking
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            debtors.sortWith(compareByDescending<BalanceEntry> { it.amountPaise }.thenBy { it.memberId ?: -1L })
            creditors.sortWith(compareByDescending<BalanceEntry> { it.amountPaise }.thenBy { it.memberId ?: -1L })

            val debtor = debtors.first()
            val creditor = creditors.first()

            val transfer = minOf(debtor.amountPaise, creditor.amountPaise)
            if (transfer > 0L) {
                val fromName = memberNames[debtor.memberId] ?: if (debtor.memberId == null) "You" else "Unavailable Member"
                val toName = memberNames[creditor.memberId] ?: if (creditor.memberId == null) "You" else "Unavailable Member"
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
        members: List<Friend>,
        allFriends: List<Friend> = emptyList()
    ): GroupBalanceSummary {
        val groupTxns = transactions.filter {
            (it.expenseGroupId == null || it.expenseGroupId == groupId) &&
                    it.transactionStatus == TransactionStatus.ACTIVE &&
                    (it.transactionType == TransactionType.EXPENSE ||
                     it.transactionType == TransactionType.UPI_LITE_PAYMENT ||
                     it.transactionType == TransactionType.REFUND ||
                     it.transactionType == TransactionType.UPI_LITE_REFUND)
        }
        val groupSettlements = settlements.filter { (it.groupId == null || it.groupId == groupId) && !it.isReversed && it.affectsLedger }

        val friendsLookup = (allFriends + members).associateBy { it.id }
        val memberNames = mutableMapOf<Long?, String>()
        memberNames[null] = "You"
        for (f in members) {
            memberNames[f.id] = f.name
        }
        for (f in allFriends) {
            if (!memberNames.containsKey(f.id)) {
                memberNames[f.id] = f.name
            }
        }

        val allMemberIds = mutableSetOf<Long?>()
        allMemberIds.add(null) // "You" is always a member
        for (f in members) allMemberIds.add(f.id)

        // Include any participants from transactions or settlements
        for (t in groupTxns) {
            if (t.paidByFriendId != null) allMemberIds.add(t.paidByFriendId)
            for (p in t.participants) {
                if (p.friendId != null) allMemberIds.add(p.friendId)
            }
        }
        for (s in groupSettlements) {
            if (s.payerId != null) allMemberIds.add(s.payerId)
            if (s.receiverId != null) allMemberIds.add(s.receiverId)
        }

        // Initialize ledger tracking
        val paidMap = mutableMapOf<Long?, Long>()
        val shareMap = mutableMapOf<Long?, Long>()
        for (m in allMemberIds) {
            paidMap[m] = 0L
            shareMap[m] = 0L
            if (!memberNames.containsKey(m)) {
                memberNames[m] = friendsLookup[m]?.name ?: if (m == null) "You" else "Unavailable Member"
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
                memberName = memberNames[mId] ?: (if (mId == null) "You" else "Unavailable Member"),
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
                            fromMemberName = memberNames[m1] ?: (if (m1 == null) "You" else "Unavailable Member"),
                            toMemberId = m2,
                            toMemberName = memberNames[m2] ?: (if (m2 == null) "You" else "Unavailable Member"),
                            amountPaise = netDiff
                        )
                    )
                } else if (netDiff < 0L) {
                    directDebts.add(
                        SimplifiedDebt(
                            fromMemberId = m2,
                            fromMemberName = memberNames[m2] ?: (if (m2 == null) "You" else "Unavailable Member"),
                            toMemberId = m1,
                            toMemberName = memberNames[m1] ?: (if (m1 == null) "You" else "Unavailable Member"),
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

    data class FriendLedgerReconciliation(
        val friend: Friend,
        val allExpenseHistory: List<UnsettledExpenseItem>,
        val unsettledExpenses: List<UnsettledExpenseItem>
    ) {
        val isAllSettledUp: Boolean
            get() = unsettledExpenses.isEmpty() && friend.netBalancePaise == 0L
    }

    /**
     * Single Source of Truth Ledger Reconciliation for Friend & Split Pro.
     * Guarantees absolute consistency between:
     * 1. Friend Balance card (friend.netBalancePaise & friend.status).
     * 2. Outstanding Expenses tab count and individual item balances.
     * 3. INVARIANT: sum(unsettledExpenses.remainingOutstandingPaise) == abs(friend.netBalancePaise).
     * 4. INVARIANT: unsettledExpenses.isEmpty() <==> friend.netBalancePaise == 0L.
     */
    fun reconcileFriendLedger(
        friend: Friend,
        transactions: List<Transaction>,
        settlements: List<Settlement>
    ): FriendLedgerReconciliation {
        val friendId = friend.id
        val validTransactions = transactions.filter {
            it.transactionStatus == TransactionStatus.ACTIVE &&
                    (it.transactionType == TransactionType.EXPENSE ||
                     it.transactionType == TransactionType.UPI_LITE_PAYMENT ||
                     it.transactionType == TransactionType.REFUND ||
                     it.transactionType == TransactionType.UPI_LITE_REFUND)
        }
        val validSettlements = settlements.filter { !it.isReversed && it.affectsLedger }

        // Group 1: Transactions where ME paid and Friend participated (Friend owes ME)
        val friendOwesMeExpenses = mutableListOf<UnsettledExpenseEntry>()
        // Group 2: Transactions where Friend paid and ME participated (ME owes Friend)
        val iOweFriendExpenses = mutableListOf<UnsettledExpenseEntry>()

        var lastTxnDate: Long? = friend.lastTransactionDate

        for (txn in validTransactions) {
            val payerId = txn.paidByFriendId
            val isExpense = txn.transactionType == TransactionType.EXPENSE || txn.transactionType == TransactionType.UPI_LITE_PAYMENT
            val isRefund = txn.transactionType == TransactionType.REFUND || txn.transactionType == TransactionType.UPI_LITE_REFUND

            if (isExpense) {
                if (payerId == null) {
                    val part = txn.participants.firstOrNull { it.friendId == friendId }
                    if (part != null && part.shareAmountPaise > 0L) {
                        friendOwesMeExpenses.add(
                            UnsettledExpenseEntry(txn, part.shareAmountPaise, ExpenseObligationDirection.THEY_OWE_ME)
                        )
                        if (lastTxnDate == null || txn.transactionDate > lastTxnDate) {
                            lastTxnDate = txn.transactionDate
                        }
                    }
                } else if (payerId == friendId) {
                    val myPart = txn.participants.firstOrNull { it.friendId == null }
                    if (myPart != null && myPart.shareAmountPaise > 0L) {
                        iOweFriendExpenses.add(
                            UnsettledExpenseEntry(txn, myPart.shareAmountPaise, ExpenseObligationDirection.I_OWE_THEM)
                        )
                        if (lastTxnDate == null || txn.transactionDate > lastTxnDate) {
                            lastTxnDate = txn.transactionDate
                        }
                    }
                }
            } else if (isRefund) {
                if (payerId == null) {
                    val part = txn.participants.firstOrNull { it.friendId == friendId }
                    if (part != null && part.shareAmountPaise > 0L) {
                        friendOwesMeExpenses.add(
                            UnsettledExpenseEntry(txn, -part.shareAmountPaise, ExpenseObligationDirection.THEY_OWE_ME)
                        )
                        if (lastTxnDate == null || txn.transactionDate > lastTxnDate) {
                            lastTxnDate = txn.transactionDate
                        }
                    }
                } else if (payerId == friendId) {
                    val myPart = txn.participants.firstOrNull { it.friendId == null }
                    if (myPart != null && myPart.shareAmountPaise > 0L) {
                        iOweFriendExpenses.add(
                            UnsettledExpenseEntry(txn, -myPart.shareAmountPaise, ExpenseObligationDirection.I_OWE_THEM)
                        )
                        if (lastTxnDate == null || txn.transactionDate > lastTxnDate) {
                            lastTxnDate = txn.transactionDate
                        }
                    }
                }
            }
        }

        // Settlements tracking
        for (s in validSettlements) {
            if (s.payerId == friendId || s.receiverId == friendId) {
                if (lastTxnDate == null || s.settlementDate > lastTxnDate) {
                    lastTxnDate = s.settlementDate
                }
            }
        }

        val grossTheyOweMeExpenses = friendOwesMeExpenses.sumOf { it.shareAmountPaise }
        val grossIOweThemExpenses = iOweFriendExpenses.sumOf { it.shareAmountPaise }

        val settlementsFriendPaidMe = validSettlements
            .filter { it.payerId == friendId && it.receiverId == null }
            .sumOf { it.amountPaise }
        val settlementsIPaidFriend = validSettlements
            .filter { it.payerId == null && it.receiverId == friendId }
            .sumOf { it.amountPaise }

        val theyOweMeNet = SafeMath.safeSubtract(grossTheyOweMeExpenses, settlementsFriendPaidMe)
        val iOweThemNet = SafeMath.safeSubtract(grossIOweThemExpenses, settlementsIPaidFriend)

        val netBalancePaise = SafeMath.safeSubtract(theyOweMeNet, iOweThemNet)
        val absNet = SafeMath.safeAbs(netBalancePaise)

        val status = when {
            netBalancePaise > 0L -> FriendBalanceStatus.THEY_OWE_ME
            netBalancePaise < 0L -> FriendBalanceStatus.I_OWE_THEM
            else -> FriendBalanceStatus.SETTLED
        }

        val finalTheyOweMe = if (netBalancePaise > 0L) netBalancePaise else 0L
        val finalIOweThem = if (netBalancePaise < 0L) absNet else 0L

        val updatedFriend = friend.copy(
            totalTheyOweMePaise = finalTheyOweMe,
            totalIOweThemPaise = finalIOweThem,
            netBalancePaise = netBalancePaise,
            lastTransactionDate = lastTxnDate,
            status = status
        )

        // Sort chronologically (oldest first for allocation)
        val sortedTheyOweMe = friendOwesMeExpenses.filter { it.shareAmountPaise > 0L }.sortedBy { it.transaction.transactionDate }
        val sortedIOweThem = iOweFriendExpenses.filter { it.shareAmountPaise > 0L }.sortedBy { it.transaction.transactionDate }

        val allHistory = mutableListOf<UnsettledExpenseItem>()

        when (status) {
            FriendBalanceStatus.THEY_OWE_ME -> {
                // Friend owes ME absNet.
                // Outstanding items must come from sortedTheyOweMe.
                // Amount of sortedTheyOweMe that is satisfied = grossTheyOweMe - absNet.
                val satisfied = SafeMath.safeSubtract(grossTheyOweMeExpenses, absNet)
                var pool = maxOf(0L, satisfied)

                // First pass: direct note matching for specific settlements
                val directAllocations = mutableMapOf<Long, Long>()
                for (s in validSettlements.filter { it.payerId == friendId && it.receiverId == null }) {
                    val sNote = (s.notes ?: "").lowercase().trim()
                    if (sNote.isNotBlank()) {
                        for (entry in sortedTheyOweMe) {
                            val title = entry.transaction.title.lowercase().trim()
                            if (title.isNotBlank() && (sNote.contains(title) || title.contains(sNote))) {
                                val currentAlloc = directAllocations.getOrDefault(entry.transaction.id, 0L)
                                val available = SafeMath.safeSubtract(entry.shareAmountPaise, currentAlloc)
                                if (available > 0L) {
                                    val alloc = minOf(s.amountPaise, available)
                                    directAllocations[entry.transaction.id] = SafeMath.safeAdd(currentAlloc, alloc)
                                    pool = SafeMath.safeSubtract(pool, alloc)
                                }
                                break
                            }
                        }
                    }
                }
                pool = maxOf(0L, pool)

                for (entry in sortedTheyOweMe) {
                    val share = entry.shareAmountPaise
                    val direct = directAllocations.getOrDefault(entry.transaction.id, 0L)
                    val additional = minOf(pool, SafeMath.safeSubtract(share, direct))
                    val totalAllocated = SafeMath.safeAdd(direct, additional)
                    val remaining = SafeMath.safeSubtract(share, totalAllocated)
                    pool = SafeMath.safeSubtract(pool, additional)

                    allHistory.add(
                        UnsettledExpenseItem(
                            transaction = entry.transaction,
                            totalTransactionAmountPaise = entry.transaction.amountPaise,
                            originalShareAmountPaise = share,
                            settledAmountPaise = totalAllocated,
                            remainingOutstandingPaise = remaining,
                            isPartiallySettled = totalAllocated > 0L && remaining > 0L,
                            direction = ExpenseObligationDirection.THEY_OWE_ME
                        )
                    )
                }

                // All expenses where friend paid (iOweFriend) are fully satisfied in this net state
                for (entry in sortedIOweThem) {
                    allHistory.add(
                        UnsettledExpenseItem(
                            transaction = entry.transaction,
                            totalTransactionAmountPaise = entry.transaction.amountPaise,
                            originalShareAmountPaise = entry.shareAmountPaise,
                            settledAmountPaise = entry.shareAmountPaise,
                            remainingOutstandingPaise = 0L,
                            isPartiallySettled = false,
                            direction = ExpenseObligationDirection.I_OWE_THEM
                        )
                    )
                }
            }

            FriendBalanceStatus.I_OWE_THEM -> {
                // ME owes friend absNet.
                // Outstanding items must come from sortedIOweThem.
                val satisfied = SafeMath.safeSubtract(grossIOweThemExpenses, absNet)
                var pool = maxOf(0L, satisfied)

                // First pass: direct note matching for specific settlements
                val directAllocations = mutableMapOf<Long, Long>()
                for (s in validSettlements.filter { it.payerId == null && it.receiverId == friendId }) {
                    val sNote = (s.notes ?: "").lowercase().trim()
                    if (sNote.isNotBlank()) {
                        for (entry in sortedIOweThem) {
                            val title = entry.transaction.title.lowercase().trim()
                            if (title.isNotBlank() && (sNote.contains(title) || title.contains(sNote))) {
                                val currentAlloc = directAllocations.getOrDefault(entry.transaction.id, 0L)
                                val available = SafeMath.safeSubtract(entry.shareAmountPaise, currentAlloc)
                                if (available > 0L) {
                                    val alloc = minOf(s.amountPaise, available)
                                    directAllocations[entry.transaction.id] = SafeMath.safeAdd(currentAlloc, alloc)
                                    pool = SafeMath.safeSubtract(pool, alloc)
                                }
                                break
                            }
                        }
                    }
                }
                pool = maxOf(0L, pool)

                for (entry in sortedIOweThem) {
                    val share = entry.shareAmountPaise
                    val direct = directAllocations.getOrDefault(entry.transaction.id, 0L)
                    val additional = minOf(pool, SafeMath.safeSubtract(share, direct))
                    val totalAllocated = SafeMath.safeAdd(direct, additional)
                    val remaining = SafeMath.safeSubtract(share, totalAllocated)
                    pool = SafeMath.safeSubtract(pool, additional)

                    allHistory.add(
                        UnsettledExpenseItem(
                            transaction = entry.transaction,
                            totalTransactionAmountPaise = entry.transaction.amountPaise,
                            originalShareAmountPaise = share,
                            settledAmountPaise = totalAllocated,
                            remainingOutstandingPaise = remaining,
                            isPartiallySettled = totalAllocated > 0L && remaining > 0L,
                            direction = ExpenseObligationDirection.I_OWE_THEM
                        )
                    )
                }

                // All expenses where ME paid (friendOwesMe) are fully satisfied in this net state
                for (entry in sortedTheyOweMe) {
                    allHistory.add(
                        UnsettledExpenseItem(
                            transaction = entry.transaction,
                            totalTransactionAmountPaise = entry.transaction.amountPaise,
                            originalShareAmountPaise = entry.shareAmountPaise,
                            settledAmountPaise = entry.shareAmountPaise,
                            remainingOutstandingPaise = 0L,
                            isPartiallySettled = false,
                            direction = ExpenseObligationDirection.THEY_OWE_ME
                        )
                    )
                }
            }

            FriendBalanceStatus.SETTLED -> {
                // When balance is zero, all historical expenses are 100% settled
                for (entry in sortedTheyOweMe) {
                    allHistory.add(
                        UnsettledExpenseItem(
                            transaction = entry.transaction,
                            totalTransactionAmountPaise = entry.transaction.amountPaise,
                            originalShareAmountPaise = entry.shareAmountPaise,
                            settledAmountPaise = entry.shareAmountPaise,
                            remainingOutstandingPaise = 0L,
                            isPartiallySettled = false,
                            direction = ExpenseObligationDirection.THEY_OWE_ME
                        )
                    )
                }
                for (entry in sortedIOweThem) {
                    allHistory.add(
                        UnsettledExpenseItem(
                            transaction = entry.transaction,
                            totalTransactionAmountPaise = entry.transaction.amountPaise,
                            originalShareAmountPaise = entry.shareAmountPaise,
                            settledAmountPaise = entry.shareAmountPaise,
                            remainingOutstandingPaise = 0L,
                            isPartiallySettled = false,
                            direction = ExpenseObligationDirection.I_OWE_THEM
                        )
                    )
                }
            }
        }

        val sortedHistory = allHistory.sortedByDescending { it.transaction.transactionDate }
        val unsettled = sortedHistory.filter { it.remainingOutstandingPaise > 0L }

        return FriendLedgerReconciliation(
            friend = updatedFriend,
            allExpenseHistory = sortedHistory,
            unsettledExpenses = unsettled
        )
    }

    /**
     * Source of truth ledger calculation for all friends.
     * Delegates to reconcileFriendLedger for guaranteed consistency across profiles and tabs.
     */
    fun computeFriendLedgers(
        friends: List<Friend>,
        activeTransactions: List<Transaction>,
        settlements: List<Settlement>
    ): List<Friend> {
        return friends.map { friend ->
            reconcileFriendLedger(friend, activeTransactions, settlements).friend
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
            ((s.payerId in groupMemberIds) || (s.receiverId in groupMemberIds)) && !s.isReversed && s.affectsLedger
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

    // Calculate outstanding/unsettled expenses for a friend using single source of truth reconciliation
    fun calculateUnsettledExpenses(
        friendId: Long,
        transactions: List<Transaction>,
        settlements: List<Settlement>
    ): List<UnsettledExpenseItem> {
        val dummyFriend = Friend(id = friendId, name = "")
        return reconcileFriendLedger(dummyFriend, transactions, settlements).unsettledExpenses
    }

    // Complete friend expense history retaining unpaid, partially settled and fully settled expenses
    fun calculateFriendExpenseHistory(
        friendId: Long,
        transactions: List<Transaction>,
        settlements: List<Settlement>
    ): List<UnsettledExpenseItem> {
        val dummyFriend = Friend(id = friendId, name = "")
        return reconcileFriendLedger(dummyFriend, transactions, settlements).allExpenseHistory
    }

    private data class UnsettledExpenseEntry(
        val transaction: Transaction,
        val shareAmountPaise: Long,
        val direction: ExpenseObligationDirection
    )
}
