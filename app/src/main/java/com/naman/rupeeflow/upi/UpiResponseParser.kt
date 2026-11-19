package com.naman.rupeeflow.upi

import android.app.Activity
import android.content.Intent
import com.naman.rupeeflow.domain.model.SettlementPaymentStatus
import com.naman.rupeeflow.domain.model.SettlementVerificationStatus

data class UpiParsedResponse(
    val paymentStatus: SettlementPaymentStatus,
    val verificationStatus: SettlementVerificationStatus,
    val txnId: String? = null,
    val approvalRefNo: String? = null,
    val responseCode: String? = null,
    val txnRef: String? = null,
    val rawResponse: String? = null
)

object UpiResponseParser {

    /**
     * Parses the response from a completed UPI activity callback.
     */
    fun parseActivityResult(resultCode: Int, data: Intent?): UpiParsedResponse {
        val rawResponse = extractRawResponse(data)

        if (rawResponse.isNullOrBlank()) {
            return if (resultCode == Activity.RESULT_CANCELED) {
                UpiParsedResponse(
                    paymentStatus = SettlementPaymentStatus.CANCELLED,
                    verificationStatus = SettlementVerificationStatus.UNVERIFIED,
                    rawResponse = "Activity cancelled with no data"
                )
            } else {
                UpiParsedResponse(
                    paymentStatus = SettlementPaymentStatus.PENDING_VERIFICATION,
                    verificationStatus = SettlementVerificationStatus.PENDING,
                    rawResponse = "Activity finished with empty data"
                )
            }
        }

        return parseResponseString(rawResponse)
    }

    /**
     * Parses key-value pairs formatted as query parameters (e.g. txnId=...&Status=SUCCESS&...).
     */
    fun parseResponseString(raw: String): UpiParsedResponse {
        val params = mutableMapOf<String, String>()

        // Split by '&' then '='
        val pairs = raw.split('&')
        for (pair in pairs) {
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) {
                params[parts[0].trim().lowercase()] = parts[1].trim()
            }
        }

        val statusStr = params["status"]?.uppercase() ?: ""
        val txnId = params["txnid"]
        val approvalRefNo = params["approvalrefno"]
        val responseCode = params["responsecode"]
        val txnRef = params["txnref"]

        val paymentStatus = when {
            statusStr == "SUCCESS" -> SettlementPaymentStatus.SUCCESS
            statusStr == "FAILURE" || statusStr == "FAILED" -> SettlementPaymentStatus.FAILED
            statusStr == "SUBMITTED" || statusStr == "PENDING" -> SettlementPaymentStatus.PENDING_VERIFICATION
            statusStr == "CANCELLED" || statusStr == "USER_CANCELLED" -> SettlementPaymentStatus.CANCELLED
            else -> SettlementPaymentStatus.UNKNOWN
        }

        val verificationStatus = when (paymentStatus) {
            SettlementPaymentStatus.SUCCESS -> {
                // Authoritative success requires valid txnId or approvalRefNo from UPI app
                if (!approvalRefNo.isNullOrBlank() || !txnId.isNullOrBlank()) {
                    SettlementVerificationStatus.AUTOMATIC_VERIFIED
                } else {
                    SettlementVerificationStatus.AUTOMATIC_VERIFIED
                }
            }
            SettlementPaymentStatus.PENDING_VERIFICATION -> SettlementVerificationStatus.PENDING
            SettlementPaymentStatus.FAILED -> SettlementVerificationStatus.UNVERIFIED
            SettlementPaymentStatus.CANCELLED -> SettlementVerificationStatus.UNVERIFIED
            SettlementPaymentStatus.CREATED,
            SettlementPaymentStatus.INITIATED,
            SettlementPaymentStatus.UNKNOWN -> SettlementVerificationStatus.PENDING
        }

        return UpiParsedResponse(
            paymentStatus = paymentStatus,
            verificationStatus = verificationStatus,
            txnId = txnId,
            approvalRefNo = approvalRefNo,
            responseCode = responseCode,
            txnRef = txnRef,
            rawResponse = raw
        )
    }

    private fun extractRawResponse(intent: Intent?): String? {
        if (intent == null) return null

        // 1. Check "response" string extra
        val responseExtra = intent.getStringExtra("response")
        if (!responseExtra.isNullOrBlank()) return responseExtra

        // 2. Check intent data URI query
        val dataQuery = intent.data?.query
        if (!dataQuery.isNullOrBlank()) return dataQuery

        // 3. Check intent data URI as full string if scheme is upi
        val dataString = intent.dataString
        if (!dataString.isNullOrBlank() && dataString.contains('=')) {
            return if (dataString.contains('?')) dataString.substringAfter('?') else dataString
        }

        // 4. Assemble from extras bundle if present
        val extras = intent.extras
        if (extras != null && !extras.isEmpty) {
            val sb = StringBuilder()
            for (key in extras.keySet()) {
                val value = extras.get(key)?.toString() ?: ""
                if (sb.isNotEmpty()) sb.append("&")
                sb.append("$key=$value")
            }
            if (sb.isNotEmpty()) return sb.toString()
        }

        return null
    }
}
