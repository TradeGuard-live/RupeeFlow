package com.naman.rupeeflow.upi

/**
 * Validates UPI Virtual Payment Address (VPA) syntax and payment amount constraints.
 *
 * NOTE: Syntax validation checks format compliance only. It does not verify bank registration
 * or prove recipient ownership of the given UPI address.
 */
object UpiValidator {

    // Standard NPCI UPI VPA format: username@bankhandle
    // Username allows alphanumeric, dots, underscores, hyphens (2-256 chars)
    // Handle allows letters (2-64 chars)
    private val UPI_VPA_REGEX = Regex("""^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z]{2,64}$""")

    /**
     * Checks if the given string adheres to standard UPI VPA syntax.
     * Returns true if valid, false otherwise.
     */
    fun isValidUpiId(upiId: String?): Boolean {
        if (upiId.isNullOrBlank()) return false
        val trimmed = upiId.trim()
        // Reject characters that could lead to URI parameter injection
        if (trimmed.contains('&') || trimmed.contains('=') || trimmed.contains('?') || trimmed.contains(' ')) {
            return false
        }
        return UPI_VPA_REGEX.matches(trimmed)
    }

    /**
     * Validates payment amount in paise against non-zero, positive, and optional debt limits.
     */
    fun validateAmount(amountPaise: Long, maxAllowedPaise: Long? = null): AmountValidationResult {
        if (amountPaise <= 0L) {
            return AmountValidationResult.Invalid("Settlement amount must be greater than zero")
        }
        if (maxAllowedPaise != null && maxAllowedPaise > 0L && amountPaise > maxAllowedPaise) {
            return AmountValidationResult.ExceedsBalance(
                "Amount exceeds outstanding balance",
                amountPaise,
                maxAllowedPaise
            )
        }
        return AmountValidationResult.Valid
    }
}

sealed class AmountValidationResult {
    object Valid : AmountValidationResult()
    data class Invalid(val message: String) : AmountValidationResult()
    data class ExceedsBalance(
        val message: String,
        val enteredPaise: Long,
        val maxAllowedPaise: Long
    ) : AmountValidationResult()
}
