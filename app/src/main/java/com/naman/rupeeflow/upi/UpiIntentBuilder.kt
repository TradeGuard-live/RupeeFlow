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

    const val PHONEPE_HANDLE = "@ybl"
    const val BHIM_HANDLE = "@upi"

    /**
     * Constructs a maximally-compatible UPI payment URI string for P2P friend settlements.
     *
     * Design rationale — "BHIM-safe by default":
     * NPCI mandates that P2P intent-based transactions from unsigned third-party apps
     * must be handled cautiously.  BHIM (the official NPCI PSP app) enforces the strictest
     * subset of the UPI Linking Specification.  To guarantee that the same URI works when
     * the user selects BHIM explicitly, picks it from the system chooser (targetPackage=null),
     * or uses any other PSP app, every P2P URI follows these rules:
     *
     * 1. 'pa'  — retains literal '@'; lowercase-normalized.
     * 2. 'pn'  — alphanumeric + spaces only; URI-encoded with %20 (not '+').
     * 3. 'am'  — exactly two decimal places (e.g. '500.00').
     * 4. 'cu'  — always 'INR'.
     * 5. 'tn'  — ALWAYS OMITTED for P2P settlements.
     *            BHIM rejects unsigned intents carrying 'tn' with
     *            "This type of request is not supported".
     *            Other apps (GPay, PhonePe, Paytm, MobiKwik, CRED) work
     *            correctly without 'tn'.
     * 6. 'tr'  — omitted unless explicitly provided for merchant flows.
     *            BHIM and MobiKwik treat 'tr' as a merchant-only field.
     * 7. 'mam' — never included (project requirement).
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

        // URI-encode the payee name for all apps (safe for BHIM too)
        val pnValue = Uri.encode(cleanPn)

        val sb = StringBuilder("upi://pay?")
            .append("pa=").append(cleanUpi)
            .append("&pn=").append(pnValue)
            .append("&am=").append(formattedAmount)
            .append("&cu=INR")

        // 'tn' (transaction note) is ALWAYS OMITTED for P2P settlements.
        //
        // BHIM (NPCI) strictly enforces anti-phishing guidelines on external intents.
        // Passing 'tn' in an unsigned P2P intent causes BHIM to reject the request with:
        //   "This type of request is not supported"
        //
        // Previously, 'tn' was only stripped when targetPackage matched BHIM.
        // This failed when the user opened BHIM via the system chooser (targetPackage=null)
        // or when the BHIM package name didn't match the detection heuristic.
        // By omitting 'tn' universally, the URI is safe regardless of which app opens it.
        // All other major UPI apps (GPay, PhonePe, Paytm, MobiKwik, CRED) work correctly
        // without 'tn' — they display their own default note or none.

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
     *
     * No explicit Intent categories are added.  The Android framework automatically
     * adds CATEGORY_DEFAULT during implicit-intent resolution.  Explicitly adding
     * CATEGORY_DEFAULT caused BHIM (NPCI) to reject the intent in certain device
     * configurations — removing it matches the standard pattern used by popular
     * UPI integration libraries (EasyUpiPayment, upi_pay, etc.).
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
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString))
        if (!targetPackage.isNullOrBlank()) {
            intent.setPackage(targetPackage.trim())
        }
        return intent
    }

    /**
     * Builds a Generic UPI chooser intent (Intent.ACTION_VIEW with no target package).
     * Android displays the system application chooser showing all installed apps handling upi://pay.
     */
    fun buildGenericChooserIntent(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String
    ): Intent {
        return buildUpiIntent(
            receiverUpiId = receiverUpiId,
            receiverName = receiverName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = null,
            targetPackage = null
        )
    }

    /**
     * Derives a UPI Virtual Payment Address (VPA) from a phone number for the target app.
     * - PhonePe: <cleanPhone>@ybl
     * - BHIM: <cleanPhone>@upi
     * - Default/Other: <cleanPhone>@upi (NPCI standard handle for mobile numbers)
     */
    fun buildPhoneNumberVpa(phoneNumber: String, targetPackage: String? = null): String {
        val cleanPhone = UpiValidator.cleanPhoneNumber(phoneNumber)
            ?: throw IllegalArgumentException("Invalid phone number: $phoneNumber")
        require(UpiValidator.isValidPhoneNumber(cleanPhone)) { "Invalid 10-digit phone number: $phoneNumber" }
        return when (targetPackage?.trim()) {
            PHONEPE_PACKAGE -> "${cleanPhone}@ybl"
            BHIM_PACKAGE -> "${cleanPhone}@upi"
            else -> "${cleanPhone}@upi"
        }
    }

    /**
     * Constructs a maximally-compatible UPI URI string targeting a phone number.
     * Automatically pre-fills payee name, amount, and currency while strictly adhering
     * to NPCI guidelines.
     */
    fun buildPhoneNumberUpiUri(
        phoneNumber: String,
        receiverName: String,
        amountPaise: Long,
        note: String = "RupeeFlow Settlement",
        targetPackage: String? = null
    ): String {
        val vpa = buildPhoneNumberVpa(phoneNumber, targetPackage)
        return buildUpiUri(
            receiverUpiId = vpa,
            receiverName = receiverName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = null,
            targetPackage = targetPackage
        )
    }

    /**
     * Builds an explicit ACTION_VIEW Intent targeting PhonePe or BHIM using a phone-linked UPI VPA.
     * Pre-fills amount, payee name, and INR currency.
     */
    fun buildPhoneNumberUpiIntent(
        phoneNumber: String,
        receiverName: String,
        amountPaise: Long,
        note: String = "RupeeFlow Settlement",
        targetPackage: String? = null
    ): Intent {
        val vpa = buildPhoneNumberVpa(phoneNumber, targetPackage)
        return buildUpiIntent(
            receiverUpiId = vpa,
            receiverName = receiverName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = null,
            targetPackage = targetPackage
        )
    }

    /**
     * Builds an explicit BHIM intent targeting in.org.npci.upiapp.
     * Uses the standard minimal P2P URI without 'tn' or 'tr'.
     */
    fun buildExplicitBhimIntent(
        receiverUpiId: String,
        receiverName: String,
        amountPaise: Long,
        note: String
    ): Intent {
        return buildUpiIntent(
            receiverUpiId = receiverUpiId,
            receiverName = receiverName,
            amountPaise = amountPaise,
            note = note,
            transactionRef = null,
            targetPackage = BHIM_PACKAGE
        )
    }

    /**
     * Checks if an intent can be resolved by any installed activity.
     */
    fun isIntentResolvable(context: Context, intent: Intent): Boolean {
        return try {
            intent.resolveActivity(context.packageManager) != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the flattened component string of the activity that resolves this intent, or null.
     */
    fun getResolvedActivityComponent(context: Context, intent: Intent): String? {
        return try {
            intent.resolveActivity(context.packageManager)?.flattenToShortString()
        } catch (_: Exception) {
            null
        }
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
     * Retrieves the application launcher intent for the specified package (e.g. PhonePe or BHIM).
     * Used when external UPI intents are blocked or rejected by the target application's security policies.
     */
    fun getAppLaunchIntent(context: Context, packageName: String): Intent? {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Checks if a specific application package is installed on the user's device.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if PhonePe is installed on the user's device.
     */
    fun isPhonePeInstalled(context: Context): Boolean = isAppInstalled(context, PHONEPE_PACKAGE)

    /**
     * Checks if BHIM is installed on the user's device.
     */
    fun isBhimInstalled(context: Context): Boolean = isAppInstalled(context, BHIM_PACKAGE)

    /**
     * Generates a unique, collision-resistant transaction reference string.
     */
    fun generateTransactionReference(): String {
        val timestamp = System.currentTimeMillis()
        val randomSuffix = Random.nextInt(1000, 9999)
        return "RF${timestamp}${randomSuffix}"
    }
}
