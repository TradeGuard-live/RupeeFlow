package com.naman.rupeeflow.data.sms.parser

import java.util.Locale
import java.util.regex.Pattern

enum class NotificationCategory {
    TRANSACTION,
    PENDING_TRANSACTION,
    FAILED_TRANSACTION,
    PROMOTIONAL,
    INFORMATIONAL,
    OTP,
    BALANCE_UPDATE,
    UNKNOWN
}

enum class RejectionReason {
    PROMOTIONAL_CONTENT,
    OTP_MESSAGE,
    FAILED_PAYMENT,
    BALANCE_INQUIRY,
    INFORMATIONAL_ALERT,
    INSUFFICIENT_EVIDENCE
}

data class NotificationClassificationResult(
    val category: NotificationCategory,
    val rejectionReason: RejectionReason? = null,
    val isTransactionCandidate: Boolean = false,
    val hasConfirmedTransactionEvidence: Boolean = false
)

object NotificationCategoryClassifier {

    private val OTP_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(otp|one\\s*time\\s*password|verification\\s*code|secret\\s*code|auth\\s*code)\\b"),
        Pattern.compile("(?i)\\b(is\\s+your\\s+otp|use\\s+code|do\\s+not\\s+share|valid\\s+for\\s+\\d+\\s+mins?)\\b")
    )

    private val FAILED_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(declined|failed|unsuccessful|cannot\\s*be\\s*processed|insufficient\\s*funds|rejected|payment\\s+failed|transaction\\s+failed)\\b"),
        Pattern.compile("(?i)(?:could\\s+not\\s+be\\s+completed|was\\s+declined|is\\s+declined)")
    )

    private val PENDING_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(payment\\s+pending|transaction\\s+pending|payment\\s+processing|under\\s+review|in\\s+process)\\b"),
        Pattern.compile("(?i)(?:will\\s+be\\s+processed|will\\s+be\\s+credited\\s+within)")
    )

    private val PROMOTIONAL_TITLE_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(apply\\s*now|refer\\s*(?:&|and)?\\s*earn|referral|special\\s*offer|limited\\s*period|exclusive\\s*deal|pre-approved|loan\\s*offer|win\\s*cash|congratulations|reward|scratch\\s*card)\\b")
    )

    private val PROMOTIONAL_BODY_PATTERNS = listOf(
        // Referral and invites
        Pattern.compile("(?i)\\b(refer\\s+(?:your\\s+friends|them)|invite\\s+(?:your\\s+)?friends|on\\s+every\\s+referral|referral\\s+bonus)\\b"),
        // Marketing products
        Pattern.compile("(?i)\\b(lifetime\\s*free(?:\\s*card)?|pre-approved|apply\\s*now|avail\\s*now|instant\\s*loan|personal\\s*loan|upgrade\\s*your\\s*card)\\b"),
        // Earning / winning claims
        Pattern.compile("(?i)\\b(earn\\s*(?:rs\\.?|inr|₹|up\\s*to|flat)|win\\s*(?:rs\\.?|inr|₹|up\\s*to|jackpot)|stand\\s*a\\s*chance\\s*to\\s*win|chance\\s*to\\s*win)\\b"),
        // Discounts, coupons, shopping vouchers
        Pattern.compile("(?i)\\b(discount\\s*voucher|coupon\\s*code|flat\\s*(?:rs\\.?|inr|₹)?\\s*\\d+\\s*off|upto\\s*(?:rs\\.?|inr|₹)?\\s*\\d+\\s*cashback|cashback\\s*offer)\\b"),
        // Rewards and gamification
        Pattern.compile("(?i)\\b(scratch\\s*card|claim\\s*(?:your\\s+)?reward|spin\\s*(?:the\\s+)?wheel|reward\\s*points|rewards\\s*waiting)\\b"),
        // Eligibility claims
        Pattern.compile("(?i)\\b(you\\s*are\\s*eligible\\s*(?:for|to)|eligible\\s+for\\s+(?:a\\s+)?loan)\\b"),
        // Conditional promises
        Pattern.compile("(?i)\\b(get\\s*(?:rs\\.?|inr|₹|up\\s*to|cashback)\\s*(?:on|when\\s+they|when\\s+you))\\b")
    )

    // Strong active transaction evidence indicating a completed past-tense financial event
    private val CONFIRMED_DEBIT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:debited\\s*(?:by|for|with)?|spent|paid|withdrawn|deducted|transferred\\s*(?:to)?|sent)\\s*(?:rs\\.?|inr|₹)?\\s*[\\d,]+"),
        Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?\\s*(?:debited|paid|spent|withdrawn|sent|deducted|transferred)"),
        Pattern.compile("(?i)(?:paid\\s+to|sent\\s+to|payment\\s+to|transferred\\s+to)\\s+[a-zA-Z0-9@._\\-\\s]{2,30}"),
        Pattern.compile("(?i)(?:payment\\s+of\\s*(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?\\s*(?:to\\s+[a-zA-Z0-9@._\\-\\s]+)?(?:is|was)?\\s*successful)"),
        Pattern.compile("(?i)(?:upi\\s*lite\\s*top-?up\\s*(?:amounting\\s*to)?|added\\s*money\\s*to\\s*upi\\s*lite)")
    )

    private val CONFIRMED_CREDIT_PATTERNS = listOf(
        Pattern.compile("(?i)(?:credited\\s*(?:by|for|with)?|received|deposited|refunded|reversed)\\s*(?:rs\\.?|inr|₹)?\\s*[\\d,]+"),
        Pattern.compile("(?i)(?:rs\\.?|inr|₹)\\s*[\\d,]+(?:\\.\\d{1,2})?\\s*(?:credited|received|deposited|refunded|reversed)"),
        Pattern.compile("(?i)(?:received\\s+from|money\\s+received\\s+from)\\s+[a-zA-Z0-9@._\\-\\s]{2,30}"),
        Pattern.compile("(?i)(?:sent\\s+you)\\s*(?:rs\\.?|inr|₹)?\\s*[\\d,.]+"),
        Pattern.compile("(?i)[a-zA-Z0-9\\s]{2,30}\\s+sent\\s+you"),
        Pattern.compile("(?i)(?:refund\\s+of\\s*(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?\\s*(?:from\\s+[a-zA-Z0-9@._\\-\\s]+)?(?:is|was)?\\s*(?:successful|processed|credited))"),
        Pattern.compile("(?i)(?:cashback\\s+of\\s*(?:rs\\.?|inr|₹)?\\s*[\\d,]+(?:\\.\\d{1,2})?\\s*credited)")
    )

    private val INFORMATIONAL_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(kyc\\s*(?:update|verification|reminder)|scheduled\\s*maintenance|terms\\s*(?:and|&)?\\s*conditions\\s*updated|security\\s*alert|logged\\s*in\\s*from\\s*new\\s*device)\\b")
    )

    /**
     * Classifies notification or SMS content into a verified category prior to amount extraction.
     */
    fun classify(title: String, body: String, senderOrPackage: String? = null): NotificationClassificationResult {
        val trimmedTitle = title.trim()
        val trimmedBody = body.trim()
        val combined = "$trimmedTitle $trimmedBody".trim()
        val lower = combined.lowercase(Locale.ROOT)
        val titleLower = trimmedTitle.lowercase(Locale.ROOT)

        // 1. Check for OTP messages
        for (pattern in OTP_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return NotificationClassificationResult(
                    category = NotificationCategory.OTP,
                    rejectionReason = RejectionReason.OTP_MESSAGE,
                    isTransactionCandidate = false
                )
            }
        }

        // 2. Check for Promotional Notification Title
        for (pattern in PROMOTIONAL_TITLE_PATTERNS) {
            if (pattern.matcher(titleLower).find()) {
                return NotificationClassificationResult(
                    category = NotificationCategory.PROMOTIONAL,
                    rejectionReason = RejectionReason.PROMOTIONAL_CONTENT,
                    isTransactionCandidate = false
                )
            }
        }

        // 3. Check for Promotional Notification Body
        var hasPromotionalBody = false
        for (pattern in PROMOTIONAL_BODY_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                hasPromotionalBody = true
                break
            }
        }

        // 4. Validate Completed Transaction Evidence
        var hasDebitEvidence = false
        for (pattern in CONFIRMED_DEBIT_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                hasDebitEvidence = true
                break
            }
        }

        var hasCreditEvidence = false
        for (pattern in CONFIRMED_CREDIT_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                hasCreditEvidence = true
                break
            }
        }

        val hasTransactionEvidence = hasDebitEvidence || hasCreditEvidence

        // If promotional phrases are present and there is NO confirmed past transaction evidence,
        // or if promotional phrases dominate (e.g. refer friends, earn ₹100, lifetime free card):
        if (hasPromotionalBody && !hasTransactionEvidence) {
            return NotificationClassificationResult(
                category = NotificationCategory.PROMOTIONAL,
                rejectionReason = RejectionReason.PROMOTIONAL_CONTENT,
                isTransactionCandidate = false
            )
        }

        // Even if some verb like "get" or "paid" is ambiguously present, if it's explicitly a referral/card marketing offer:
        if (hasPromotionalBody && (lower.contains("refer them") || lower.contains("refer your") || lower.contains("lifetime free card") || lower.contains("apply now"))) {
            return NotificationClassificationResult(
                category = NotificationCategory.PROMOTIONAL,
                rejectionReason = RejectionReason.PROMOTIONAL_CONTENT,
                isTransactionCandidate = false
            )
        }

        // 5. Check for Failed transactions
        for (pattern in FAILED_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return NotificationClassificationResult(
                    category = NotificationCategory.FAILED_TRANSACTION,
                    rejectionReason = RejectionReason.FAILED_PAYMENT,
                    isTransactionCandidate = false
                )
            }
        }

        // 6. Check for Pending / Processing payments
        for (pattern in PENDING_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                return NotificationClassificationResult(
                    category = NotificationCategory.PENDING_TRANSACTION,
                    rejectionReason = null,
                    isTransactionCandidate = true,
                    hasConfirmedTransactionEvidence = false
                )
            }
        }

        // 7. Check for pure balance enquiry
        val isBalanceOnly = (lower.contains("bal:") || lower.contains("avl bal") || lower.contains("available bal") || lower.contains("balance in your a/c")) &&
                !hasTransactionEvidence
        if (isBalanceOnly) {
            return NotificationClassificationResult(
                category = NotificationCategory.BALANCE_UPDATE,
                rejectionReason = RejectionReason.BALANCE_INQUIRY,
                isTransactionCandidate = false
            )
        }

        // 8. Check for Informational notifications
        for (pattern in INFORMATIONAL_PATTERNS) {
            if (pattern.matcher(lower).find() && !hasTransactionEvidence) {
                return NotificationClassificationResult(
                    category = NotificationCategory.INFORMATIONAL,
                    rejectionReason = RejectionReason.INFORMATIONAL_ALERT,
                    isTransactionCandidate = false
                )
            }
        }

        // 9. Verified Completed Financial Event
        if (hasTransactionEvidence) {
            return NotificationClassificationResult(
                category = NotificationCategory.TRANSACTION,
                rejectionReason = null,
                isTransactionCandidate = true,
                hasConfirmedTransactionEvidence = true
            )
        }

        // 10. Ambiguous / Unknown candidate
        return NotificationClassificationResult(
            category = NotificationCategory.UNKNOWN,
            rejectionReason = RejectionReason.INSUFFICIENT_EVIDENCE,
            isTransactionCandidate = false,
            hasConfirmedTransactionEvidence = false
        )
    }
}

