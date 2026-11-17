package com.naman.rupeeflow.location

import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque

data class DiagnosticLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val event: String,
    val details: String = ""
)

object LocationDiagnostics {
    private const val TAG = "RupeeFlowLocation"
    private const val MAX_LOGS = 100
    private val logs = ConcurrentLinkedDeque<DiagnosticLogEntry>()

    private fun addEntry(event: String, details: String = "") {
        // Enforce privacy: never accept or log exact coordinates, merchant names, or PII
        val entry = DiagnosticLogEntry(
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
            // Non-fatal if running in JVM unit tests without android.util.Log
        }
    }

    fun logCaptureRequested(trigger: String) {
        addEntry("Capture requested", "Trigger: $trigger")
    }

    fun logCaptureSkippedOff() {
        addEntry("Capture skipped because setting is Off")
    }

    fun logPermissionMissing() {
        addEntry("Permission missing", "Neither FINE nor COARSE location permission granted")
    }

    fun logGpsDisabled() {
        addEntry("GPS disabled", "Hardware location services are switched off on device")
    }

    fun logLocationRequestStarted(trigger: String) {
        addEntry("Location request started", "Trigger: $trigger")
    }

    fun logLocationReceived(hasAccuracy: Boolean, isCached: Boolean) {
        val type = if (isCached) "cached" else "fresh fix"
        val acc = if (hasAccuracy) "with accuracy" else "unknown accuracy"
        addEntry("Location received", "$type, $acc")
    }

    fun logLocationTimeout() {
        addEntry("Location request timed out", "Fallback applied or location marked unavailable")
    }

    fun logAssociationSucceeded(pendingId: Long) {
        addEntry("Pending transaction association succeeded", "Pending ID: $pendingId")
    }

    fun logDatabaseSave(succeeded: Boolean, entityId: Long) {
        val status = if (succeeded) "succeeded" else "failed"
        addEntry("Database save $status", "Entity ID: $entityId")
    }

    fun logApprovalPreservedLocation(txnId: Long, pendingId: Long?) {
        val src = if (pendingId != null) "from pending #$pendingId" else "direct"
        addEntry("Approval preserved location", "Txn ID: $txnId ($src)")
    }

    fun getRecentLogs(): List<DiagnosticLogEntry> {
        return logs.toList()
    }

    fun clearLogs() {
        logs.clear()
    }
}

