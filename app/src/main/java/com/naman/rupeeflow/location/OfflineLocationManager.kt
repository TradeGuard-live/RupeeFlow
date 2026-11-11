package com.naman.rupeeflow.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.naman.rupeeflow.domain.model.LocationSource
import com.naman.rupeeflow.domain.model.TransactionLocation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object OfflineLocationManager {

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
     * Retrieves the best available cached/last-known location from GPS or Network provider.
     * Works 100% offline using hardware GPS satellites and cellular towers without internet requests.
     */
    fun getLastKnownLocation(context: Context): TransactionLocation? {
        if (!hasPermission(context)) return null

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

        return TransactionLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            locationCapturedAt = location.time,
            locationSource = LocationSource.DEVICE_CURRENT,
            googleMapsUrl = LocationIntentHelper.buildGoogleMapsWebUrl(location.latitude, location.longitude)
        )
    }

    /**
     * Requests a fresh one-shot hardware location with timeout.
     * Respects Android foreground lifecycle and cancels immediately upon completion.
     */
    suspend fun requestFreshLocation(
        context: Context,
        timeoutMs: Long = 5000L
    ): TransactionLocation? {
        if (!hasPermission(context)) return null

        val cached = getLastKnownLocation(context)
        // If cached location is less than 30 seconds old, reuse it
        if (cached != null && (System.currentTimeMillis() - cached.locationCapturedAt) < 30_000L) {
            return cached
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return cached

        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<TransactionLocation?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(loc: Location) {
                        locationManager.removeUpdates(this)
                        if (LocationParser.isValid(loc.latitude, loc.longitude)) {
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

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
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
                        cont.resume(cached)
                    }
                } catch (_: SecurityException) {
                    cont.resume(null)
                } catch (_: Exception) {
                    cont.resume(cached)
                }

                cont.invokeOnCancellation {
                    try {
                        locationManager.removeUpdates(listener)
                    } catch (_: Exception) {}
                }
            }
        } ?: cached
    }
}

