package com.naman.rupeeflow.upi

import android.content.Intent
import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

data class UpiDiagnosticEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val details: String = ""
)

/**
 * Diagnostics logger for Split Pro direct UPI intent payment lifecycle.
 *
 * Privacy Guarantees:
 * - NEVER logs receiver UPI ID (VPA), user credentials, bank details, PINs, or OTPs.
 * - Only logs parameter names (keys), target packages, activity resolution status,
 *   result codes, and returned response keys.
 */
object UpiDiagnostics {
    private const val TAG = "RupeeFlowUPI"
    private const val MAX_LOGS = 100
    private val logs = ConcurrentLinkedDeque<UpiDiagnosticEntry>()

    private fun addEntry(event: String, details: String = "") {
        val entry = UpiDiagnosticEntry(
            timestamp = System.currentTimeMillis(),
            event = event,
            details = details
        )
        logs.addLast(entry)
        while (logs.size > MAX_LOGS) {
            logs.pollFirst()
        }
        try {
            Log.d(TAG, if (details.isBlank()) event else "$event: $details")
        } catch (_: Exception) {
            // Non-fatal when running under JVM unit test environments without android.util.Log
        }
    }

    /**
     * Extracts query parameter names without revealing parameter values (privacy-safe).
     */
    fun extractParamNames(uriString: String?): Set<String> {
        if (uriString.isNullOrBlank()) return emptySet()
        val qIdx = uriString.indexOf('?')
        if (qIdx == -1 || qIdx == uriString.length - 1) return emptySet()
        val query = uriString.substring(qIdx + 1)
        return query.split('&').mapNotNull { pair ->
            val eq = pair.indexOf('=')
            if (eq > 0) pair.substring(0, eq).trim().lowercase() else null
        }.toSet()
    }

    fun logIntentBuilt(action: String, targetPackage: String?, uriString: String?) {
        val paramNames = extractParamNames(uriString).sorted().joinToString(", ")
        val pkg = targetPackage ?: "Generic Chooser (No package)"
        addEntry("UPI Intent Built", "Action: $action, Target: $pkg, URI Params: [$paramNames]")
    }

    fun logActivityResolution(targetPackage: String?, resolvedActivity: String?) {
        val pkg = targetPackage ?: "Generic Chooser"
        val resolved = resolvedActivity ?: "UNRESOLVED / NULL"
        addEntry("Activity Resolution", "Target: $pkg -> Resolved: $resolved")
    }

    fun logLaunchAttempt(targetPackage: String?, isGenericChooser: Boolean) {
        val mode = if (isGenericChooser) "Generic System Chooser" else "Explicit Package Target: $targetPackage"
        addEntry("UPI Launch Attempt", "Mode: $mode")
    }

    fun logLaunchException(targetPackage: String?, throwable: Throwable) {
        val pkg = targetPackage ?: "Generic Chooser"
        addEntry("UPI Launch Failed", "Target: $pkg, Error: ${throwable.javaClass.simpleName} - ${throwable.message}")
    }

    fun logActivityResult(resultCode: Int, returnedKeys: Set<String>, parsedStatus: String) {
        val keysStr = if (returnedKeys.isEmpty()) "none" else returnedKeys.sorted().joinToString(", ")
        addEntry("UPI Activity Result", "ResultCode: $resultCode, Keys: [$keysStr], ParsedStatus: $parsedStatus")
    }

    fun logLedgerSafety(settlementId: Long, affectsLedger: Boolean, reason: String) {
        addEntry("Ledger Safety Audit", "Settlement #$settlementId, AffectsLedger: $affectsLedger, Reason: $reason")
    }

    fun getRecentLogs(): List<UpiDiagnosticEntry> = logs.toList()

    fun clearLogs() {
        logs.clear()
    }
}

