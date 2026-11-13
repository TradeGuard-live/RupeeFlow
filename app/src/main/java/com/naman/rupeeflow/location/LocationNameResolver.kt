package com.naman.rupeeflow.location

import android.content.Context
import android.location.Geocoder
import java.util.Locale

object LocationNameResolver {

    private val COORDINATE_REGEX = Regex("""^[-+]?[0-9]*\.?[0-9]+,\s*[-+]?[0-9]*\.?[0-9]+$""")

    fun resolveReadableLocation(
        context: Context?,
        locationLabel: String?,
        latitude: Double?,
        longitude: Double?
    ): String {
        // Priority 1: User-entered label / named place metadata (if not raw coordinate string)
        if (!locationLabel.isNullOrBlank()) {
            val trimmed = locationLabel.trim()
            if (!COORDINATE_REGEX.matches(trimmed) && !trimmed.contains("Accuracy", ignoreCase = true)) {
                return trimmed
            }
        }

        // Priority 2: Locally available geocoded address if available
        if (context != null && latitude != null && longitude != null && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    val subLocality = address.subLocality ?: address.featureName
                    val locality = address.locality ?: address.subAdminArea
                    val adminArea = address.adminArea

                    val parts = listOfNotNull(
                        subLocality?.takeIf { it.isNotBlank() && !it.matches(COORDINATE_REGEX) },
                        locality?.takeIf { it.isNotBlank() },
                        adminArea?.takeIf { locality.isNullOrBlank() && it.isNotBlank() }
                    ).distinct()

                    if (parts.isNotEmpty()) {
                        return parts.joinToString(", ")
                    }
                }
            } catch (_: Exception) {
                // Non-fatal: offline fallback
            }
        }

        // Priority 3: Clean, human-readable fallback
        return "Location saved"
    }
}

