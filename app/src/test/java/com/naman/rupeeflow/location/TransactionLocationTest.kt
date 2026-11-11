package com.naman.rupeeflow.location

import com.naman.rupeeflow.domain.model.LocationSource
import com.naman.rupeeflow.domain.model.PendingTransaction
import com.naman.rupeeflow.domain.model.Transaction
import com.naman.rupeeflow.domain.model.TransactionLocation
import org.junit.Assert.*
import org.junit.Test

class TransactionLocationTest {

    // 1. Valid coordinates within normal ranges
    @Test
    fun test01_validCoordinatesWithinNormalRanges() {
        assertTrue(LocationParser.isValid(28.6139, 77.2090))
        val loc = TransactionLocation(28.6139, 77.2090)
        assertTrue(loc.isValid())
    }

    // 2. Boundary: North Pole
    @Test
    fun test02_boundaryNorthPole() {
        assertTrue(LocationParser.isValid(90.0, 0.0))
        val loc = TransactionLocation(90.0, 0.0)
        assertTrue(loc.isValid())
    }

    // 3. Boundary: South Pole
    @Test
    fun test03_boundarySouthPole() {
        assertTrue(LocationParser.isValid(-90.0, 0.0))
        val loc = TransactionLocation(-90.0, 0.0)
        assertTrue(loc.isValid())
    }

    // 4. Boundary: Antimeridian East
    @Test
    fun test04_boundaryAntimeridianEast() {
        assertTrue(LocationParser.isValid(0.0, 180.0))
        val loc = TransactionLocation(0.0, 180.0)
        assertTrue(loc.isValid())
    }

    // 5. Boundary: Antimeridian West
    @Test
    fun test05_boundaryAntimeridianWest() {
        assertTrue(LocationParser.isValid(0.0, -180.0))
        val loc = TransactionLocation(0.0, -180.0)
        assertTrue(loc.isValid())
    }

    // 6. Boundary: Equator / Prime Meridian
    @Test
    fun test06_boundaryEquatorPrimeMeridian() {
        assertTrue(LocationParser.isValid(0.0, 0.0))
        val loc = TransactionLocation(0.0, 0.0)
        assertTrue(loc.isValid())
    }

    // 7. Invalid Latitude > 90
    @Test
    fun test07_invalidLatitudeAboveRange() {
        assertFalse(LocationParser.isValid(90.0001, 77.2090))
        val loc = TransactionLocation(90.0001, 77.2090)
        assertFalse(loc.isValid())
    }

    // 8. Invalid Latitude < -90
    @Test
    fun test08_invalidLatitudeBelowRange() {
        assertFalse(LocationParser.isValid(-90.0001, 77.2090))
        val loc = TransactionLocation(-90.0001, 77.2090)
        assertFalse(loc.isValid())
    }

    // 9. Invalid Longitude > 180
    @Test
    fun test09_invalidLongitudeAboveRange() {
        assertFalse(LocationParser.isValid(28.6139, 180.0001))
        val loc = TransactionLocation(28.6139, 180.0001)
        assertFalse(loc.isValid())
    }

    // 10. Invalid Longitude < -180
    @Test
    fun test10_invalidLongitudeBelowRange() {
        assertFalse(LocationParser.isValid(28.6139, -180.0001))
        val loc = TransactionLocation(28.6139, -180.0001)
        assertFalse(loc.isValid())
    }

    // 11. Invalid Latitude NaN
    @Test
    fun test11_invalidLatitudeNaN() {
        assertFalse(LocationParser.isValid(Double.NaN, 77.2090))
        val loc = TransactionLocation(Double.NaN, 77.2090)
        assertFalse(loc.isValid())
    }

    // 12. Invalid Latitude Infinity
    @Test
    fun test12_invalidLatitudeInfinity() {
        assertFalse(LocationParser.isValid(Double.POSITIVE_INFINITY, 77.2090))
        assertFalse(LocationParser.isValid(Double.NEGATIVE_INFINITY, 77.2090))
    }

    // 13. Invalid Longitude NaN
    @Test
    fun test13_invalidLongitudeNaN() {
        assertFalse(LocationParser.isValid(28.6139, Double.NaN))
        val loc = TransactionLocation(28.6139, Double.NaN)
        assertFalse(loc.isValid())
    }

    // 14. Invalid Longitude Infinity
    @Test
    fun test14_invalidLongitudeInfinity() {
        assertFalse(LocationParser.isValid(28.6139, Double.POSITIVE_INFINITY))
        assertFalse(LocationParser.isValid(28.6139, Double.NEGATIVE_INFINITY))
    }

    // 15. Coordinate string parsing with comma and space
    @Test
    fun test15_parseCoordinatesStandard() {
        val parsed = LocationParser.parseCoordinates("28.6139, 77.2090")
        assertNotNull(parsed)
        assertEquals(28.6139, parsed!!.first, 0.0001)
        assertEquals(77.2090, parsed.second, 0.0001)
    }

    // 16. Coordinate string parsing without space
    @Test
    fun test16_parseCoordinatesWithoutSpace() {
        val parsed = LocationParser.parseCoordinates("12.9716,77.5946")
        assertNotNull(parsed)
        assertEquals(12.9716, parsed!!.first, 0.0001)
        assertEquals(77.5946, parsed.second, 0.0001)
    }

    // 17. Coordinate string parsing with excessive whitespace
    @Test
    fun test17_parseCoordinatesWithWhitespace() {
        val parsed = LocationParser.parseCoordinates("  19.0760   ,   72.8777  ")
        assertNotNull(parsed)
        assertEquals(19.0760, parsed!!.first, 0.0001)
        assertEquals(72.8777, parsed.second, 0.0001)
    }

    // 18. Coordinate string parsing invalid format
    @Test
    fun test18_parseCoordinatesInvalid() {
        assertNull(LocationParser.parseCoordinates("not a coordinate"))
        assertNull(LocationParser.parseCoordinates("12.34"))
        assertNull(LocationParser.parseCoordinates("abc, def"))
        assertNull(LocationParser.parseCoordinates(""))
    }

    // 19. Google Maps URL parsing standard query (?q=lat,lng)
    @Test
    fun test19_parseGoogleMapsUrlQuery() {
        val url = "https://maps.google.com/?q=28.6139,77.2090"
        val parsed = LocationParser.parseGoogleMapsUrl(url)
        assertNotNull(parsed)
        assertEquals(28.6139, parsed!!.first, 0.0001)
        assertEquals(77.2090, parsed.second, 0.0001)
    }

    // 20. Google Maps URL parsing @ notation
    @Test
    fun test20_parseGoogleMapsUrlAtNotation() {
        val url = "https://www.google.com/maps/@12.9716,77.5946,15z"
        val parsed = LocationParser.parseGoogleMapsUrl(url)
        assertNotNull(parsed)
        assertEquals(12.9716, parsed!!.first, 0.0001)
        assertEquals(77.5946, parsed.second, 0.0001)
    }

    // 21. Google Maps URL parsing search API format
    @Test
    fun test21_parseGoogleMapsUrlSearchApi() {
        val url = "https://www.google.com/maps/search/?api=1&query=19.0760,72.8777"
        val parsed = LocationParser.parseGoogleMapsUrl(url)
        assertNotNull(parsed)
        assertEquals(19.0760, parsed!!.first, 0.0001)
        assertEquals(72.8777, parsed.second, 0.0001)
    }

    // 22. Google Maps URL parsing invalid URL
    @Test
    fun test22_parseGoogleMapsUrlInvalid() {
        assertNull(LocationParser.parseGoogleMapsUrl("https://example.com/no/coordinates"))
        assertNull(LocationParser.parseGoogleMapsUrl("https://maps.google.com/?q=invalid,coords"))
        assertNull(LocationParser.parseGoogleMapsUrl(""))
    }

    // 23. Geo URI generation
    @Test
    fun test23_geoUriGeneration() {
        val loc = TransactionLocation(28.6139, 77.2090)
        val uri = loc.toGeoUri()
        assertTrue(uri.startsWith("geo:28.6139,77.209"))
        assertTrue(uri.contains("q=28.6139,77.209"))
    }

    // 24. Web URL fallback generation
    @Test
    fun test24_webUrlFallbackGeneration() {
        val loc = TransactionLocation(12.9716, 77.5946)
        val url = loc.toGoogleMapsUrl()
        assertTrue(url.contains("12.9716"))
        assertTrue(url.contains("77.5946"))
        assertTrue(url.startsWith("https://www.google.com/maps/search/"))
    }

    // 25. LocationSource enum values mapping
    @Test
    fun test25_locationSourceEnumMapping() {
        val sources = LocationSource.values()
        assertTrue(sources.contains(LocationSource.DEVICE_CURRENT))
        assertTrue(sources.contains(LocationSource.DEVICE_AT_DETECTION))
        assertTrue(sources.contains(LocationSource.MANUAL_MAP_SELECTION))
        assertTrue(sources.contains(LocationSource.MANUAL_COORDINATES))
        assertTrue(sources.contains(LocationSource.MANUAL_MAPS_LINK))
        assertTrue(sources.contains(LocationSource.HISTORICAL_LOCATION_SUGGESTION))
    }

    // 26. TransactionLocation domain object integrity and equality
    @Test
    fun test26_transactionLocationIntegrityAndEquality() {
        val loc1 = TransactionLocation(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 15.5f,
            locationCapturedAt = 1000L,
            locationSource = LocationSource.DEVICE_CURRENT,
            locationLabel = "Connaught Place",
            googleMapsUrl = "https://maps.google.com/?q=28.6139,77.2090"
        )
        val loc2 = TransactionLocation(
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 15.5f,
            locationCapturedAt = 1000L,
            locationSource = LocationSource.DEVICE_CURRENT,
            locationLabel = "Connaught Place",
            googleMapsUrl = "https://maps.google.com/?q=28.6139,77.2090"
        )
        assertEquals(loc1, loc2)
        assertEquals(15.5f, loc1.accuracyMeters)
        assertEquals("Connaught Place", loc1.locationLabel)
    }

    // 27. Transaction domain mapping with location fields round-trip
    @Test
    fun test27_transactionDomainLocationMapping() {
        val txn = Transaction(
            title = "Starbucks",
            amountPaise = 35000L,
            categoryId = 1L,
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 10.0f,
            locationCapturedAt = 2000L,
            locationSource = LocationSource.DEVICE_CURRENT,
            locationLabel = "CP Outlet",
            googleMapsUrl = "https://maps.google.com/?q=28.6139,77.2090"
        )

        assertTrue(txn.hasLocation)
        assertNotNull(txn.location)
        assertEquals(28.6139, txn.location!!.latitude, 0.0001)
        assertEquals(77.2090, txn.location!!.longitude, 0.0001)
        assertEquals(10.0f, txn.location!!.accuracyMeters)
        assertEquals("CP Outlet", txn.location!!.locationLabel)
    }

    // 28. PendingTransaction domain mapping with location fields round-trip
    @Test
    fun test28_pendingTransactionDomainLocationMapping() {
        val pending = PendingTransaction(
            detectedAmountPaise = 15000L,
            detectedBank = "HDFC Bank",
            rawContent = "Paid Rs 150 at Swiggy",
            latitude = 12.9716,
            longitude = 77.5946,
            accuracyMeters = 20.0f,
            locationCapturedAt = 3000L,
            locationSource = LocationSource.DEVICE_AT_DETECTION
        )

        assertTrue(pending.hasLocation)
        assertNotNull(pending.location)
        assertEquals(12.9716, pending.location!!.latitude, 0.0001)
        assertEquals(77.5946, pending.location!!.longitude, 0.0001)
        assertEquals(LocationSource.DEVICE_AT_DETECTION, pending.location!!.locationSource)
    }

    // 29. Merchant location memory concept
    @Test
    fun test29_merchantLocationMemorySuggestion() {
        val historicalLocation = TransactionLocation(
            latitude = 28.6139,
            longitude = 77.2090,
            locationSource = LocationSource.DEVICE_CURRENT,
            locationLabel = "Connaught Place"
        )
        // Suggestion applied to new transaction:
        val suggested = historicalLocation.copy(
            locationSource = LocationSource.HISTORICAL_LOCATION_SUGGESTION
        )
        assertEquals(LocationSource.HISTORICAL_LOCATION_SUGGESTION, suggested.locationSource)
        assertEquals(28.6139, suggested.latitude, 0.0001)
        assertEquals(77.2090, suggested.longitude, 0.0001)
    }

    // 30. Clearing location clears coordinates while preserving financial data
    @Test
    fun test30_clearingLocationPreservesFinancialData() {
        val original = Transaction(
            title = "Dinner with friends",
            amountPaise = 120000L,
            categoryId = 1L,
            latitude = 28.6139,
            longitude = 77.2090,
            accuracyMeters = 5.0f
        )
        assertTrue(original.hasLocation)
        assertEquals(120000L, original.amountPaise)

        // Clear location simulation:
        val cleared = original.copy(
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            locationCapturedAt = null,
            locationSource = null,
            locationLabel = null,
            googleMapsUrl = null
        )
        assertFalse(cleared.hasLocation)
        assertNull(cleared.location)
        assertEquals("Financial amount must remain identical after clearing location", 120000L, cleared.amountPaise)
        assertEquals("Title must remain identical", "Dinner with friends", cleared.title)
    }
}

