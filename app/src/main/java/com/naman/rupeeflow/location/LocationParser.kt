package com.naman.rupeeflow.location

import java.util.regex.Pattern

object LocationParser {

    // Matches coordinates in formats like:
    // "19.0760, 72.8777" or "19.0760,72.8777" or "19.0760 N, 72.8777 E"
    private val COORD_PATTERN = Pattern.compile(
        "^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*°?\\s*([NSns])?\\s*[,\\s/]+\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*°?\\s*([EWew])?\\s*$"
    )

    // Patterns for Google Maps URLs
    // e.g. https://www.google.com/maps/search/?api=1&query=19.0760,72.8777
    // e.g. https://maps.google.com/?q=19.0760,72.8777
    // e.g. https://www.google.com/maps/@19.0760,72.8777,15z
    // e.g. https://www.google.com/maps/place/19.0760,72.8777
    private val GOOGLE_MAPS_QUERY_PATTERN = Pattern.compile(
        "(?:query|q|loc)=([+-]?\\d+(?:\\.\\d+)?)[,%2C]+([+-]?\\d+(?:\\.\\d+)?)"
    )
    private val GOOGLE_MAPS_AT_PATTERN = Pattern.compile(
        "@([+-]?\\d+(?:\\.\\d+)?),([+-]?\\d+(?:\\.\\d+)?)"
    )
    private val GOOGLE_MAPS_PLACE_PATTERN = Pattern.compile(
        "/place/([+-]?\\d+(?:\\.\\d+)?)[,%2C]+([+-]?\\d+(?:\\.\\d+)?)"
    )

    /**
     * Checks whether latitude and longitude are valid non-NaN, non-infinite real numbers
     * within geographic boundaries [-90..90] and [-180..180].
     */
    fun isValid(latitude: Double, longitude: Double): Boolean {
        if (latitude.isNaN() || latitude.isInfinite()) return false
        if (longitude.isNaN() || longitude.isInfinite()) return false
        return latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    /**
     * Parses manual coordinate string, supporting degrees, decimal format, and cardinal directions.
     */
    fun parseCoordinates(input: String?): Pair<Double, Double>? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        val matcher = COORD_PATTERN.matcher(trimmed)
        if (matcher.matches()) {
            val rawLat = matcher.group(1)?.toDoubleOrNull() ?: return null
            val latDir = matcher.group(2)
            val rawLon = matcher.group(3)?.toDoubleOrNull() ?: return null
            val lonDir = matcher.group(4)

            var lat = rawLat
            if (latDir != null && (latDir.equals("S", ignoreCase = true))) {
                lat = -kotlin.math.abs(lat)
            }

            var lon = rawLon
            if (lonDir != null && (lonDir.equals("W", ignoreCase = true))) {
                lon = -kotlin.math.abs(lon)
            }

            if (isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // Direct comma split fallback: "12.9716, 77.5946"
        val parts = trimmed.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null && isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        return null
    }

    /**
     * Extracts geographic coordinates from Google Maps URLs and links.
     */
    fun parseGoogleMapsUrl(url: String?): Pair<Double, Double>? {
        if (url.isNullOrBlank()) return null
        val decoded = java.net.URLDecoder.decode(url.trim(), "UTF-8")

        // 1. Check query parameters (?q=... or ?query=...)
        val queryMatcher = GOOGLE_MAPS_QUERY_PATTERN.matcher(decoded)
        if (queryMatcher.find()) {
            val lat = queryMatcher.group(1)?.toDoubleOrNull()
            val lon = queryMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 2. Check /place/... pattern
        val placeMatcher = GOOGLE_MAPS_PLACE_PATTERN.matcher(decoded)
        if (placeMatcher.find()) {
            val lat = placeMatcher.group(1)?.toDoubleOrNull()
            val lon = placeMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        // 3. Check @lat,lon pattern
        val atMatcher = GOOGLE_MAPS_AT_PATTERN.matcher(decoded)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lon = atMatcher.group(2)?.toDoubleOrNull()
            if (lat != null && lon != null && isValid(lat, lon)) {
                return Pair(lat, lon)
            }
        }

        return null
    }
}

