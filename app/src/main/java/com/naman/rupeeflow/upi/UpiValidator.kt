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
    private val INDIAN_PHONE_REGEX = Regex("""^[6-9]\d{9}$""")

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
     * Cleans an input phone string by stripping non-digit characters and normalizes
     * standard country prefixes (+91, 91, or leading 0) to yield a 10-digit number.
     */
    fun cleanPhoneNumber(rawPhone: String?): String? {
        if (rawPhone.isNullOrBlank()) return null
        val digits = rawPhone.replace(Regex("""\D"""), "")
        return when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.substring(1)
            digits.length == 12 && digits.startsWith("91") -> digits.substring(2)
            digits.length > 10 && digits.endsWith(Regex("""[6-9]\d{9}""").find(digits)?.value ?: "") -> {
                Regex("""[6-9]\d{9}""").find(digits)?.value
            }
            else -> null
        }
    }

    /**
     * Validates if the given string represents a valid 10-digit Indian mobile number.
     */
    fun isValidPhoneNumber(phone: String?): Boolean {
        val clean = cleanPhoneNumber(phone) ?: return false
        return clean.matches(INDIAN_PHONE_REGEX)
    }

    /**
     * Formats a clean 10-digit phone number into "+91 XXXXX XXXXX" for readable display.
     */
    fun formatPhoneDisplay(phone: String?): String {
        val clean = cleanPhoneNumber(phone) ?: return phone.orEmpty()
        return if (clean.length == 10) {
            "+91 ${clean.substring(0, 5)} ${clean.substring(5)}"
        } else {
            clean
        }
    }

    /**
     * Determines whether the given input string looks like a phone number rather than a UPI ID.
     */
    fun isPhoneNumberInput(input: String?): Boolean {
        if (input.isNullOrBlank()) return false
        val trimmed = input.trim()
        if (trimmed.contains('@')) return false
        val digits = trimmed.replace(Regex("""\D"""), "")
        return digits.length in 10..13
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
