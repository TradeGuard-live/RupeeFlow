package com.naman.rupeeflow.upi

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.random.Random

data class UpiAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isBhim: Boolean = false,
    val isGooglePay: Boolean = false,
    val isPhonePe: Boolean = false,
    val isPaytm: Boolean = false
)

object UpiIntentBuilder {

    const val BHIM_PACKAGE = "in.org.npci.upiapp"
    const val GOOGLE_PAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE_PACKAGE = "com.phonepe.app"
    const val PAYTM_PACKAGE = "net.one97.paytm"

    /**
     * Constructs a compliant UPI payment URI string with securely encoded parameters.
     */
    fun buildUpiUri(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String,
        transactionRef: String = generateTransactionReference()
    ): String {
        require(UpiValidator.isValidUpiId(receiverUpiId)) { "Invalid receiver UPI ID: $receiverUpiId" }
        require(amountPaise > 0L) { "Payment amount must be greater than zero" }

        val amountRupees = amountPaise.toDouble() / 100.0
        val formattedAmount = String.format(Locale.US, "%.2f", amountRupees)

        val encodedPa = encodeParam(receiverUpiId.trim())
        val encodedPn = encodeParam(receiverName.trim())
        val encodedAm = encodeParam(formattedAmount)
        val encodedCu = encodeParam("INR")
        val encodedTn = encodeParam(note.trim())
        val encodedTr = encodeParam(transactionRef.trim())

        return "upi://pay?pa=$encodedPa&pn=$encodedPn&am=$encodedAm&cu=$encodedCu&tn=$encodedTn&tr=$encodedTr"
    }

    /**
     * Builds an ACTION_VIEW Intent targeting standard UPI handler applications.
     */
    fun buildUpiIntent(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String,
        transactionRef: String = generateTransactionReference(),
        targetPackage: String? = null
    ): Intent {
        val uriString = buildUpiUri(
            receiverUpiId = receiverUpiId,
            receiverName = receiverName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = transactionRef
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriString)
        }
        if (!targetPackage.isNullOrBlank()) {
            intent.setPackage(targetPackage.trim())
        }
        return intent
    }

    /**
     * Discovers all installed applications that declare an intent filter handling upi://pay.
     */
    fun getInstalledUpiApps(context: Context): List<UpiAppInfo> {
        val testIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("upi://pay")
        }
        val packageManager = context.packageManager
        val resolveInfoList: List<ResolveInfo> = try {
            packageManager.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
        } catch (_: Exception) {
            emptyList()
        }

        return resolveInfoList.map { info ->
            val pkg = info.activityInfo.packageName
            val label = info.loadLabel(packageManager)?.toString() ?: pkg
            val icon = info.loadIcon(packageManager)

            UpiAppInfo(
                packageName = pkg,
                appName = label,
                icon = icon,
                isBhim = pkg == BHIM_PACKAGE,
                isGooglePay = pkg == GOOGLE_PAY_PACKAGE,
                isPhonePe = pkg == PHONEPE_PACKAGE,
                isPaytm = pkg == PAYTM_PACKAGE
            )
        }
    }

    /**
     * Checks if any UPI-compatible application is installed.
     */
    fun isAnyUpiAppInstalled(context: Context): Boolean {
        return getInstalledUpiApps(context).isNotEmpty()
    }

    /**
     * Generates a unique, collision-resistant transaction reference string.
     */
    fun generateTransactionReference(): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = Random.nextInt(1000, 9999)
        return "RF${timestamp}${randomSuffix}"
    }

    private fun encodeParam(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }
}
