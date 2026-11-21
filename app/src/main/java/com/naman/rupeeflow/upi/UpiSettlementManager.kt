package com.naman.rupeeflow.upi

import android.content.Context
import android.content.Intent
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.GroupActivityRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class UpiSettlementManager(
    private val settlementRepository: SettlementRepository,
    private val groupActivityRepository: GroupActivityRepository? = null,
    private val friendRepository: FriendRepository? = null
) {

    private val _settlementReconciledEvents = MutableSharedFlow<Settlement>(extraBufferCapacity = 16)
    val settlementReconciledEvents: SharedFlow<Settlement> = _settlementReconciledEvents.asSharedFlow()

    /**
     * Creates an INITIATED settlement record and builds the corresponding UPI payment Intent.
     * The settlement does not affect the financial ledger while in INITIATED or PENDING status.
     */
    suspend fun initiateSettlement(
        friend: Friend,
        amountPaise: Long,
        groupId: Long? = null,
        note: String? = null,
        targetPackage: String? = null
    ): Pair<Long, Intent> {
        val upiId = friend.upiId?.trim()
        require(!upiId.isNullOrBlank() && UpiValidator.isValidUpiId(upiId)) {
            "Friend does not have a valid UPI ID"
        }
        require(amountPaise > 0L) {
            "Settlement amount must be greater than zero"
        }

        val transactionRef = UpiIntentBuilder.generateTransactionReference()
        val defaultNote = note?.trim()?.takeIf { it.isNotBlank() }
            ?: "RupeeFlow settlement to ${friend.name}"

        val settlement = Settlement(
            id = 0L,
            payerId = null, // ME
            receiverId = friend.id,
            amountPaise = amountPaise,
            settlementDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            referenceNumber = transactionRef,
            notes = defaultNote,
            groupId = groupId,
            isReversed = false,
            upiPaymentStatus = SettlementPaymentStatus.INITIATED,
            upiVerificationStatus = SettlementVerificationStatus.PENDING,
            upiTransactionRef = transactionRef,
            upiReceiverVpa = upiId
        )

        val settlementId = settlementRepository.recordSettlement(settlement)

        // For peer-to-peer friend settlements, transactionRef (tr) must NOT be passed to UPI URI.
        // NPCI UPI specification restricts 'tr' to merchant transactions.
        // Supplying 'tr' for personal accounts causes BHIM and MobiKwik to display an error.
        val intent = UpiIntentBuilder.buildUpiIntent(
            receiverUpiId = upiId,
            receiverName = friend.upiVerifiedName ?: friend.name,
            amountPaise = amountPaise,
            note = defaultNote,
            transactionRef = null,
            targetPackage = targetPackage
        )

        UpiDiagnostics.logIntentBuilt(
            action = intent.action ?: "android.intent.action.VIEW",
            targetPackage = targetPackage,
            uriString = intent.dataString
        )

        return Pair(settlementId, intent)
    }

    /**
     * Builds a fallback generic UPI intent without package constraint for system chooser.
     */
    fun buildGenericUpiIntent(
        friend: Friend,
        amountPaise: Long,
        note: String? = null
    ): Intent {
        val upiId = friend.upiId?.trim() ?: ""
        val defaultNote = note?.trim()?.takeIf { it.isNotBlank() }
            ?: "RupeeFlow settlement to ${friend.name}"
        return UpiIntentBuilder.buildUpiIntent(
            receiverUpiId = upiId,
            receiverName = friend.upiVerifiedName ?: friend.name,
            amountPaise = amountPaise,
            note = defaultNote,
            transactionRef = null,
            targetPackage = null
        )
    }

    /**
     * Processes activity result upon return from external UPI app.
     * Prevents duplicate execution if payment was already recorded as SUCCESS.
     */
    suspend fun handlePaymentResult(
        settlementId: Long,
        resultCode: Int,
        data: Intent?,
        recentDetectedDebits: List<PendingTransaction> = emptyList()
    ): Settlement {
        val existing = settlementRepository.getSettlementById(settlementId)
            ?: throw IllegalArgumentException("Settlement with ID $settlementId not found")

        // Idempotency: if already SUCCESS and ledger-applied, do not re-process
        if (existing.upiPaymentStatus == SettlementPaymentStatus.SUCCESS &&
            (existing.upiVerificationStatus == SettlementVerificationStatus.AUTOMATIC_VERIFIED ||
             existing.upiVerificationStatus == SettlementVerificationStatus.MANUAL_UNVERIFIED)) {
            return existing
        }

        val returnedKeys = UpiResponseParser.extractResponseKeys(data)
        val parsed = UpiResponseParser.parseActivityResult(resultCode, data)
        UpiDiagnostics.logActivityResult(resultCode, returnedKeys, parsed.paymentStatus.name)

        var finalPaymentStatus = parsed.paymentStatus
        var finalVerificationStatus = parsed.verificationStatus
        val approvalRef = parsed.approvalRefNo ?: parsed.txnId

        // Supporting evidence check:
        // If parsed status is not authoritative SUCCESS, check if an SMS or notification debit was captured
        if (finalPaymentStatus != SettlementPaymentStatus.SUCCESS) {
            val matchingDebit = recentDetectedDebits.firstOrNull { debit ->
                debit.detectedType == TransactionType.EXPENSE &&
                debit.detectedAmountPaise == existing.amountPaise &&
                (System.currentTimeMillis() - debit.timestamp) < 180_000L // within last 3 minutes
            }
            if (matchingDebit != null) {
                finalPaymentStatus = SettlementPaymentStatus.SUCCESS
                finalVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED
            }
        }

        val updated = existing.copy(
            upiPaymentStatus = finalPaymentStatus,
            upiVerificationStatus = finalVerificationStatus,
            referenceNumber = approvalRef ?: existing.referenceNumber,
            upiTransactionRef = parsed.txnId ?: existing.upiTransactionRef,
            upiResponseRaw = parsed.rawResponse ?: existing.upiResponseRaw
        )

        settlementRepository.updateSettlement(updated)

        UpiDiagnostics.logLedgerSafety(
            settlementId = updated.id,
            affectsLedger = updated.affectsLedger,
            reason = "Payment status is ${updated.upiPaymentStatus}, verification is ${updated.upiVerificationStatus}"
        )

        // Record group activity if settlement is verified and has groupId
        if (updated.affectsLedger && updated.groupId != null && groupActivityRepository != null) {
            try {
                groupActivityRepository.recordActivity(
                    groupId = updated.groupId,
                    activityType = GroupActivityType.SETTLEMENT_RECORDED,
                    actorName = "You",
                    description = "You settled UPI payment of ${updated.amountPaise / 100} INR"
                )
            } catch (_: Exception) {
                // Non-fatal activity logging
            }
        }

        return updated
    }

    /**
     * Explicit manual user confirmation when authoritative bank verification is unavailable.
     * Strictly records verificationStatus as MANUAL_UNVERIFIED (never labeled bank-verified).
     */
    suspend fun confirmManuallyUnverified(settlementId: Long): Settlement {
        val existing = settlementRepository.getSettlementById(settlementId)
            ?: throw IllegalArgumentException("Settlement with ID $settlementId not found")

        if (existing.upiPaymentStatus == SettlementPaymentStatus.SUCCESS &&
            (existing.upiVerificationStatus == SettlementVerificationStatus.AUTOMATIC_VERIFIED ||
             existing.upiVerificationStatus == SettlementVerificationStatus.MANUAL_UNVERIFIED)) {
            return existing
        }

        val updated = existing.copy(
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.MANUAL_UNVERIFIED
        )

        settlementRepository.updateSettlement(updated)

        if (updated.affectsLedger && updated.groupId != null && groupActivityRepository != null) {
            try {
                groupActivityRepository.recordActivity(
                    groupId = updated.groupId,
                    activityType = GroupActivityType.SETTLEMENT_RECORDED,
                    actorName = "You",
                    description = "You settled payment of ${updated.amountPaise / 100} INR (Manual Confirmation)"
                )
            } catch (_: Exception) {
                // Non-fatal
            }
        }

        return updated
    }

    /**
     * Cancels an initiated settlement without affecting the ledger.
     */
    suspend fun cancelSettlement(settlementId: Long): Settlement {
        val existing = settlementRepository.getSettlementById(settlementId)
            ?: throw IllegalArgumentException("Settlement with ID $settlementId not found")

        val updated = existing.copy(
            upiPaymentStatus = SettlementPaymentStatus.CANCELLED,
            upiVerificationStatus = SettlementVerificationStatus.UNVERIFIED
        )

        settlementRepository.updateSettlement(updated)
        return updated
    }

    /**
     * Creates an INITIATED settlement record for a QR code payment session.
     * Does not affect the financial ledger while in INITIATED or PENDING status.
     */
    suspend fun initiateQrSettlement(
        friend: Friend,
        amountPaise: Long,
        isPayingFriend: Boolean,
        qrReference: String,
        groupId: Long? = null,
        note: String? = null
    ): Long {
        require(amountPaise > 0L) { "Settlement amount must be greater than zero" }

        val defaultNote = note?.trim()?.takeIf { it.isNotBlank() }
            ?: if (isPayingFriend) "RupeeFlow settlement to ${friend.name}" else "RupeeFlow settlement from ${friend.name}"

        val settlement = Settlement(
            id = 0L,
            payerId = if (isPayingFriend) null else friend.id,
            receiverId = if (isPayingFriend) friend.id else null,
            amountPaise = amountPaise,
            settlementDate = System.currentTimeMillis(),
            paymentMethod = PaymentMethod.UPI,
            referenceNumber = qrReference,
            notes = defaultNote,
            groupId = groupId,
            isReversed = false,
            upiPaymentStatus = SettlementPaymentStatus.INITIATED,
            upiVerificationStatus = SettlementVerificationStatus.PENDING,
            upiTransactionRef = qrReference,
            upiPayerVpa = if (!isPayingFriend) friend.upiId else null,
            upiReceiverVpa = if (isPayingFriend) friend.upiId else null
        )

        return settlementRepository.recordSettlement(settlement)
    }

    /**
     * Updates an initiated settlement's amount and reference.
     */
    suspend fun updatePendingSettlementAmount(
        settlementId: Long,
        newAmountPaise: Long,
        newQrReference: String? = null
    ): Settlement {
        val existing = settlementRepository.getSettlementById(settlementId)
            ?: throw IllegalArgumentException("Settlement with ID $settlementId not found")
        require(newAmountPaise > 0L) { "Settlement amount must be greater than zero" }

        val updated = existing.copy(
            amountPaise = newAmountPaise,
            referenceNumber = newQrReference ?: existing.referenceNumber,
            upiTransactionRef = newQrReference ?: existing.upiTransactionRef
        )
        settlementRepository.updateSettlement(updated)
        return updated
    }

    /**
     * Matches an incoming bank/UPI transaction against active pending settlement sessions.
     * Reconciles matching settlement by updating it to SUCCESS with AUTOMATIC_VERIFIED.
     */
    suspend fun reconcileIncomingTransaction(
        amountPaise: Long,
        type: TransactionType,
        referenceNumber: String? = null,
        upiReferenceNumber: String? = null,
        merchantOrPayee: String? = null,
        senderUpiId: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        rawContent: String? = null
    ): Settlement? {
        if (amountPaise <= 0L) return null

        val allSettlements = settlementRepository.getAllSettlementsList()
        val pendingCandidates = allSettlements.filter { s ->
            !s.isReversed &&
            (s.upiPaymentStatus == SettlementPaymentStatus.INITIATED ||
             s.upiPaymentStatus == SettlementPaymentStatus.PENDING_VERIFICATION)
        }

        if (pendingCandidates.isEmpty()) return null

        // Exact amount match
        val amountMatches = pendingCandidates.filter { it.amountPaise == amountPaise }
        if (amountMatches.isEmpty()) return null

        // Direction match:
        // EXPENSE: User paid friend (payerId == null, receiverId != null)
        // INCOME: Friend paid user (payerId != null, receiverId == null)
        val directionMatches = amountMatches.filter { s ->
            when (type) {
                TransactionType.EXPENSE -> s.payerId == null && s.receiverId != null
                TransactionType.INCOME -> s.payerId != null && s.receiverId == null
                else -> false
            }
        }
        if (directionMatches.isEmpty()) return null

        var bestCandidate: Settlement? = null
        var bestScore = -1

        val ref = upiReferenceNumber?.trim()?.takeIf { it.isNotBlank() }
            ?: referenceNumber?.trim()?.takeIf { it.isNotBlank() }
        val payeeText = listOfNotNull(merchantOrPayee, senderUpiId, rawContent).joinToString(" ").lowercase()

        for (candidate in directionMatches) {
            var score = 0
            val friendId = candidate.receiverId ?: candidate.payerId
            val friend = friendId?.let { friendRepository?.getFriendById(it) }

            // Reference match
            val candidateRef = candidate.referenceNumber ?: candidate.upiTransactionRef
            if (ref != null && candidateRef != null) {
                if (ref.equals(candidateRef, ignoreCase = true) ||
                    (rawContent != null && rawContent.contains(candidateRef, ignoreCase = true))) {
                    score += 100
                }
            }

            // Payee / UPI ID match
            if (friend != null) {
                val friendUpi = friend.upiId?.lowercase()?.trim()
                val friendName = friend.name.lowercase().trim()
                val friendPhone = friend.phoneNumber?.trim()

                if (!friendUpi.isNullOrBlank() && payeeText.contains(friendUpi)) {
                    score += 60
                }
                if (friendName.isNotBlank() && payeeText.contains(friendName)) {
                    score += 40
                }
                if (!friendPhone.isNullOrBlank() && payeeText.contains(friendPhone)) {
                    score += 30
                }
            }

            // Time proximity
            val timeDiffMs = kotlin.math.abs(timestamp - candidate.createdAt)
            if (timeDiffMs <= 3 * 60 * 1000L) {
                score += 30
            } else if (timeDiffMs <= 15 * 60 * 1000L) {
                score += 15
            }

            if (directionMatches.size == 1 && timeDiffMs <= 15 * 60 * 1000L) {
                score += 20
            }

            if (score > bestScore) {
                bestScore = score
                bestCandidate = candidate
            }
        }

        if (bestCandidate == null || bestScore < 20) return null

        val finalRef = ref ?: bestCandidate.referenceNumber ?: bestCandidate.upiTransactionRef
        val updated = bestCandidate.copy(
            upiPaymentStatus = SettlementPaymentStatus.SUCCESS,
            upiVerificationStatus = SettlementVerificationStatus.AUTOMATIC_VERIFIED,
            referenceNumber = finalRef,
            upiTransactionRef = finalRef,
            upiResponseRaw = rawContent ?: bestCandidate.upiResponseRaw,
            settlementDate = timestamp
        )

        settlementRepository.updateSettlement(updated)

        UpiDiagnostics.logLedgerSafety(
            settlementId = updated.id,
            affectsLedger = updated.affectsLedger,
            reason = "Auto-reconciled via SMS/Notification matching score $bestScore (Ref: $finalRef)"
        )

        if (updated.affectsLedger && updated.groupId != null && groupActivityRepository != null) {
            try {
                groupActivityRepository.recordActivity(
                    groupId = updated.groupId,
                    activityType = GroupActivityType.SETTLEMENT_RECORDED,
                    actorName = "You",
                    description = "Settlement of ${updated.amountPaise / 100} INR auto-reconciled via bank transaction"
                )
            } catch (_: Exception) {}
        }

        _settlementReconciledEvents.tryEmit(updated)
        return updated
    }
}
