package com.naman.rupeeflow.upi

import android.content.Context
import android.content.Intent
import com.naman.rupeeflow.data.repository.GroupActivityRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.domain.model.*

class UpiSettlementManager(
    private val settlementRepository: SettlementRepository,
    private val groupActivityRepository: GroupActivityRepository? = null
) {

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
        if (existing.upiPaymentStatus == SettlementPaymentStatus.SUCCESS) {
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

        if (existing.upiPaymentStatus == SettlementPaymentStatus.SUCCESS) {
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
}
