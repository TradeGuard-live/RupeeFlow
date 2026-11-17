package com.naman.rupeeflow.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.naman.rupeeflow.domain.model.LocationCaptureMode
import com.naman.rupeeflow.domain.model.LocationSource
import com.naman.rupeeflow.domain.model.PendingTransaction
import com.naman.rupeeflow.domain.model.TransactionLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class PendingLocationStatus(val displayText: String) {
    SAVED("Location saved"),
    WAITING("Waiting for location"),
    PERMISSION_REQUIRED("Location permission required"),
    LOCATION_UNAVAILABLE("Location unavailable"),
    BACKGROUND_RESTRICTED("Location capture not supported in background"),
    OFF("Location capture off")
}

object OfflineLocationManager {

    const val FRESH_CACHE_MAX_AGE_MS = 30_000L // 30 seconds
    const val STALE_CACHE_MAX_AGE_MS = 120_000L // 2 minutes

    /**
     * Checks if the application currently has fine or coarse location permissions.
     */
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Checks whether system location services (GPS or Network) are enabled on the device.
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                locationManager.isLocationEnabled
            } else {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Retrieves the best available cached/last-known location from GPS or Network provider.
     * Rejects locations older than 2 minutes unless [allowStale] is true.
     * Works 100% offline using hardware GPS satellites and cellular towers without internet requests.
     */
    fun getLastKnownLocation(
        context: Context,
        allowStale: Boolean = false
    ): TransactionLocation? {
        if (!hasPermission(context)) {
            LocationDiagnostics.logPermissionMissing()
            return null
        }
        if (!isLocationEnabled(context)) {
            LocationDiagnostics.logGpsDisabled()
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        var bestLocation: Location? = null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (_: SecurityException) {
                // Safely handle permission revocation races
            } catch (_: Exception) {
                // Ignore provider unavailability
            }
        }

        val location = bestLocation ?: return null
        if (!LocationParser.isValid(location.latitude, location.longitude)) return null

        val ageMs = System.currentTimeMillis() - location.time
        val isStale = ageMs > STALE_CACHE_MAX_AGE_MS

        if (isStale && !allowStale) {
            // Do not associate stale location without explicit permission
            return null
        }

        val label = if (isStale) "[Stale location]" else null

        val result = TransactionLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            locationCapturedAt = location.time,
            locationSource = LocationSource.DEVICE_CURRENT,
            locationLabel = label,
            googleMapsUrl = LocationIntentHelper.buildGoogleMapsWebUrl(location.latitude, location.longitude)
        )

        LocationDiagnostics.logLocationReceived(location.hasAccuracy(), isCached = true)
        return result
    }

    /**
     * Requests a fresh one-shot hardware location with timeout.
     * Respects Android foreground lifecycle and cancels immediately upon completion.
     */
    suspend fun requestFreshLocation(
        context: Context,
        timeoutMs: Long = 5000L
    ): TransactionLocation? {
        if (!hasPermission(context)) {
            LocationDiagnostics.logPermissionMissing()
            return null
        }
        if (!isLocationEnabled(context)) {
            LocationDiagnostics.logGpsDisabled()
            return null
        }

        LocationDiagnostics.logLocationRequestStarted("requestFreshLocation")

        val cached = getLastKnownLocation(context, allowStale = false)
        // If cached location is less than 30 seconds old and reasonably accurate (< 150m), reuse it
        if (cached != null && (System.currentTimeMillis() - cached.locationCapturedAt) < FRESH_CACHE_MAX_AGE_MS) {
            val acc = cached.accuracyMeters ?: Float.MAX_VALUE
            if (acc <= 150f) {
                return cached
            }
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return cached

        val freshResult = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<TransactionLocation?> { cont ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val cancellationSignal = CancellationSignal()
                    cont.invokeOnCancellation {
                        try {
                            cancellationSignal.cancel()
                        } catch (_: Exception) {}
                    }

                    val provider = when {
                        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> LocationManager.PASSIVE_PROVIDER
                    }

                    try {
                        locationManager.getCurrentLocation(
                            provider,
                            cancellationSignal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc: Location? ->
                            if (cont.isActive) {
                                if (loc != null && LocationParser.isValid(loc.latitude, loc.longitude)) {
                                    LocationDiagnostics.logLocationReceived(loc.hasAccuracy(), isCached = false)
                                    cont.resume(
                                        TransactionLocation(
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                                            locationCapturedAt = loc.time,
                                            locationSource = LocationSource.DEVICE_CURRENT,
                                            googleMapsUrl = LocationIntentHelper.buildGoogleMapsWebUrl(loc.latitude, loc.longitude)
                                        )
                                    )
                                } else {
                                    cont.resume(cached)
                                }
                            }
                        }
                    } catch (_: SecurityException) {
                        LocationDiagnostics.logPermissionMissing()
                        if (cont.isActive) cont.resume(null)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(cached)
                    }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            try {
                                locationManager.removeUpdates(this)
                            } catch (_: Exception) {}

                            if (cont.isActive) {
                                if (LocationParser.isValid(loc.latitude, loc.longitude)) {
                                    LocationDiagnostics.logLocationReceived(loc.hasAccuracy(), isCached = false)
                                    cont.resume(
                                        TransactionLocation(
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else null,
                                            locationCapturedAt = loc.time,
                                            locationSource = LocationSource.DEVICE_CURRENT,
                                            googleMapsUrl = LocationIntentHelper.buildGoogleMapsWebUrl(loc.latitude, loc.longitude)
                                        )
                                    )
                                } else {
                                    cont.resume(cached)
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }

                    cont.invokeOnCancellation {
                        try {
                            locationManager.removeUpdates(listener)
                        } catch (_: Exception) {}
                    }

                    try {
                        val provider = when {
                            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                            else -> null
                        }

                        if (provider != null) {
                            locationManager.requestSingleUpdate(
                                provider,
                                listener,
                                Looper.getMainLooper()
                            )
                        } else {
                            if (cont.isActive) cont.resume(cached)
                        }
                    } catch (_: SecurityException) {
                        LocationDiagnostics.logPermissionMissing()
                        if (cont.isActive) cont.resume(null)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(cached)
                    }
                }
            }
        }

        if (freshResult == null) {
            LocationDiagnostics.logLocationTimeout()
        }

        return freshResult ?: cached
    }

    /**
     * Resolves the human-readable location status badge for a detected pending transaction.
     */
    fun resolvePendingLocationStatus(
        context: Context,
        item: PendingTransaction,
        mode: LocationCaptureMode,
        isCapturing: Boolean = false
    ): PendingLocationStatus {
        if (mode == LocationCaptureMode.OFF) return PendingLocationStatus.OFF
        if (item.hasLocation) return PendingLocationStatus.SAVED
        if (isCapturing) return PendingLocationStatus.WAITING
        if (!hasPermission(context)) return PendingLocationStatus.PERMISSION_REQUIRED
        if (!isLocationEnabled(context)) return PendingLocationStatus.LOCATION_UNAVAILABLE
        return PendingLocationStatus.BACKGROUND_RESTRICTED
    }
}
