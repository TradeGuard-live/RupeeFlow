package com.naman.rupeeflow.location

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

object LocationIntentHelper {

    private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    /**
     * Builds an offline-compatible geo URI with coordinates and optional place label query.
     * Format: geo:LATITUDE,LONGITUDE?q=LATITUDE,LONGITUDE(Label)
     */
    fun buildGeoUri(latitude: Double, longitude: Double, label: String? = null): Uri {
        val query = if (!label.isNullOrBlank()) {
            "$latitude,$longitude(${Uri.encode(label.trim())})"
        } else {
            "$latitude,$longitude"
        }
        return Uri.parse("geo:$latitude,$longitude?q=$query")
    }

    /**
     * Generates a standard web fallback Google Maps URL.
     */
    fun buildGoogleMapsWebUrl(latitude: Double, longitude: Double): String {
        return "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
    }

    /**
     * Creates an Intent to display coordinates in a map application.
     * Prefers native Google Maps or any installed geo-capable map application,
     * falling back to the standard web browser URL if no dedicated map app is present.
     */
    fun createMapIntent(context: Context, latitude: Double, longitude: Double, label: String? = null): Intent {
        if (!LocationParser.isValid(latitude, longitude)) {
            throw IllegalArgumentException("Invalid coordinates: lat=$latitude, lon=$longitude")
        }

        val geoUri = buildGeoUri(latitude, longitude, label)
        val geoIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pm: PackageManager = context.packageManager
        // 1. Check if Google Maps is installed
        val googleMapsInstalled = runCatching {
            pm.getPackageInfo(GOOGLE_MAPS_PACKAGE, 0)
            true
        }.getOrDefault(false)

        if (googleMapsInstalled) {
            geoIntent.setPackage(GOOGLE_MAPS_PACKAGE)
            return geoIntent
        }

        // 2. Check if any map handler is available
        val activities = pm.queryIntentActivities(geoIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (activities.isNotEmpty()) {
            return geoIntent
        }

        // 3. Fallback to Web Browser URL
        val webUrl = buildGoogleMapsWebUrl(latitude, longitude)
        return Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Safe helper to open map coordinates from any Activity/Context.
     */
    fun openLocation(context: Context, latitude: Double, longitude: Double, label: String? = null): Boolean {
        return try {
            val intent = createMapIntent(context, latitude, longitude, label)
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}

