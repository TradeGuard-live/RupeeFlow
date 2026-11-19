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
    val isPaytm: Boolean = false,
    val isMobiKwik: Boolean = false,
    val isCred: Boolean = false
)

object UpiIntentBuilder {

    const val BHIM_PACKAGE = "in.org.npci.upiapp"
    const val GOOGLE_PAY_PACKAGE = "com.google.android.apps.nbu.paisa.user"
    const val PHONEPE_PACKAGE = "com.phonepe.app"
    const val PAYTM_PACKAGE = "net.one97.paytm"
    const val MOBIKWIK_PACKAGE = "com.mobikwik_new"
    const val CRED_PACKAGE = "com.cred.club"

    /**
     * Constructs a compliant UPI payment URI string with standard RFC 3986 encoding.
     * For peer-to-peer (P2P) friend settlements:
     * 1. 'pa' retains literal '@' (e.g., 'user@bank') and normalized to lowercase.
     *    Encoding '@' as '%40' causes BHIM (NPCI) and MobiKwik VPA regex validation to fail with an error.
     * 2. 'pn' is sanitized to alphanumeric characters and spaces. For BHIM, a concise name is used.
     * 3. 'am' is formatted with exactly two decimal places (e.g., '500.00').
     * 4. 'cu' is set to 'INR'.
     * 5. 'tn' (transaction note) is omitted for BHIM (NPCI official app).
     *    BHIM strictly enforces NPCI anti-phishing guidelines on third-party app intents.
     *    Passing 'tn' in an un-signed P2P intent causes BHIM to reject the intent with:
     *    "This type of request is not supported".
     * 6. 'tr' is omitted for P2P personal payments because BHIM and MobiKwik treat 'tr' as a merchant
     *    order reference and fail with an error when 'tr' is supplied for a personal VPA.
     */
    fun buildUpiUri(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String,
        transactionRef: String? = null,
        targetPackage: String? = null
    ): String {
        require(UpiValidator.isValidUpiId(receiverUpiId)) { "Invalid receiver UPI ID: $receiverUpiId" }
        require(amountPaise > 0L) { "Payment amount must be greater than zero" }

        val amountRupees = amountPaise.toDouble() / 100.0
        val formattedAmount = String.format(Locale.US, "%.2f", amountRupees)

        // Sanitize Payee Name: alphanumeric and spaces only, max 50 characters (NPCI requirement)
        val cleanPn = receiverName.trim()
            .replace(Regex("""[^a-zA-Z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(50)
            .ifBlank { "Friend" }

        val cleanUpi = receiverUpiId.trim().lowercase()
        val isBhim = targetPackage == BHIM_PACKAGE || targetPackage?.contains("npci") == true

        // For BHIM (NPCI app), concise alphanumeric name avoids deep-link query parser rejections
        val pnValue = if (isBhim) {
            cleanPn.split(" ").firstOrNull()?.take(25)?.ifBlank { "Friend" } ?: "Friend"
        } else {
            Uri.encode(cleanPn)
        }

        val sb = StringBuilder("upi://pay?")
            .append("pa=").append(cleanUpi)
            .append("&pn=").append(pnValue)
            .append("&am=").append(formattedAmount)
            .append("&cu=INR")

        // Crucial for BHIM (NPCI) compatibility:
        // BHIM strictly enforces NPCI anti-phishing guidelines on external intents.
        // If 'tn' (transaction note) is passed in an un-signed P2P intent, BHIM flags it
        // as an unauthorized automated payload and rejects it with "this type of request is not supported".
        // Therefore, for BHIM, 'tn' must be omitted completely.
        // For other UPI applications (e.g., MobiKwik, GPay, PhonePe), clean sanitized 'tn' can be safely included.
        if (!isBhim) {
            val cleanTn = note.trim()
                .replace(Regex("""[^a-zA-Z0-9 ]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .take(50)
            if (cleanTn.isNotBlank()) {
                sb.append("&tn=").append(Uri.encode(cleanTn))
            }
        }

        // Only append tr if explicitly provided and non-blank (e.g., for verified merchant endpoints)
        if (!transactionRef.isNullOrBlank()) {
            val cleanTr = transactionRef.trim()
                .replace(Regex("""[^a-zA-Z0-9]"""), "")
                .take(35)
            if (cleanTr.isNotBlank()) {
                sb.append("&tr=").append(Uri.encode(cleanTr))
            }
        }

        return sb.toString()
    }

    /**
     * Builds an ACTION_VIEW Intent targeting standard UPI handler applications.
     */
    fun buildUpiIntent(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String,
        transactionRef: String? = null,
        targetPackage: String? = null
    ): Intent {
        val uriString = buildUpiUri(
            receiverUpiId = receiverUpiId,
            receiverName = receiverName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = transactionRef,
            targetPackage = targetPackage
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uriString)
            addCategory(Intent.CATEGORY_DEFAULT)
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
        val packageManager = context.packageManager
        val testIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("upi://pay?pa=payee@upi&pn=Payee&am=1.00&cu=INR")
        }
        val resolveInfoList: List<ResolveInfo> = try {
            val list = packageManager.queryIntentActivities(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (list.isEmpty()) {
                packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("upi://pay")), 0)
            } else {
                list
            }
        } catch (_: Exception) {
            emptyList()
        }

        val discovered = resolveInfoList.mapNotNull { info ->
            val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
            val label = info.loadLabel(packageManager)?.toString() ?: pkg
            val icon = info.loadIcon(packageManager)

            UpiAppInfo(
                packageName = pkg,
                appName = label,
                icon = icon,
                isBhim = pkg == BHIM_PACKAGE,
                isGooglePay = pkg == GOOGLE_PAY_PACKAGE,
                isPhonePe = pkg == PHONEPE_PACKAGE,
                isPaytm = pkg == PAYTM_PACKAGE,
                isMobiKwik = pkg == MOBIKWIK_PACKAGE,
                isCred = pkg == CRED_PACKAGE
            )
        }.distinctBy { it.packageName }.toMutableList()

        // Explicit check for known popular apps in case queryIntentActivities filtered them
        val knownPackages = listOf(
            BHIM_PACKAGE to "BHIM",
            MOBIKWIK_PACKAGE to "MobiKwik",
            PHONEPE_PACKAGE to "PhonePe",
            GOOGLE_PAY_PACKAGE to "Google Pay",
            PAYTM_PACKAGE to "Paytm",
            CRED_PACKAGE to "CRED"
        )
        for ((pkg, defaultName) in knownPackages) {
            if (discovered.none { it.packageName == pkg }) {
                try {
                    val appInfo = packageManager.getApplicationInfo(pkg, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = packageManager.getApplicationIcon(appInfo)
                    discovered.add(
                        UpiAppInfo(
                            packageName = pkg,
                            appName = label.ifBlank { defaultName },
                            icon = icon,
                            isBhim = pkg == BHIM_PACKAGE,
                            isGooglePay = pkg == GOOGLE_PAY_PACKAGE,
                            isPhonePe = pkg == PHONEPE_PACKAGE,
                            isPaytm = pkg == PAYTM_PACKAGE,
                            isMobiKwik = pkg == MOBIKWIK_PACKAGE,
                            isCred = pkg == CRED_PACKAGE
                        )
                    )
                } catch (_: Exception) {
                    // App is not installed
                }
            }
        }

        return discovered
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
}
