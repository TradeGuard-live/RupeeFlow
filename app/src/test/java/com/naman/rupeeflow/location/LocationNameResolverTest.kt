package com.naman.rupeeflow.location

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocationNameResolverTest {

    @Test
    fun testUserEnteredNamedLocationPreserved() {
        val result = LocationNameResolver.resolveReadableLocation(
            context = RuntimeEnvironment.getApplication(),
            locationLabel = "Vallabh Vidyanagar, Anand",
            latitude = 22.5539,
            longitude = 72.9289
        )
        assertEquals("Vallabh Vidyanagar, Anand", result)
    }

    @Test
    fun testRawCoordinatesFilteredToLocationSavedFallback() {
        val result = LocationNameResolver.resolveReadableLocation(
            context = null, // offline / no geocoder
            locationLabel = "22.42184, 71.19957",
            latitude = 22.42184,
            longitude = 71.19957
        )
        assertEquals("Raw coordinates must resolve to human-readable fallback", "Location saved", result)
    }

    @Test
    fun testNullOrEmptyLocationLabelResolvesToLocationSaved() {
        val resultNull = LocationNameResolver.resolveReadableLocation(
            context = null,
            locationLabel = null,
            latitude = 22.42184,
            longitude = 71.19957
        )
        assertEquals("Location saved", resultNull)

        val resultBlank = LocationNameResolver.resolveReadableLocation(
            context = null,
            locationLabel = "   ",
            latitude = 22.42184,
            longitude = 71.19957
        )
        assertEquals("Location saved", resultBlank)
    }
}

