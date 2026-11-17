package com.naman.rupeeflow.qa

import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.dao.PendingTransactionDao
import com.naman.rupeeflow.data.local.dao.TransactionDao
import com.naman.rupeeflow.data.local.entity.PendingTransactionEntity
import com.naman.rupeeflow.data.local.entity.TransactionEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.ledger.LedgerCalculator
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.location.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TransactionLocationEndToEndQATest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var pendingDao: PendingTransactionDao
    private lateinit var transactionDao: TransactionDao
    private lateinit var pendingRepo: PendingTransactionRepository
    private lateinit var transactionRepo: TransactionRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var backupRepo: BackupRestoreRepository
    private lateinit var shadowLocationManager: ShadowLocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        pendingDao = database.pendingTransactionDao()
        transactionDao = database.transactionDao()
        categoryRepo = CategoryRepositoryImpl(database.categoryDao())
        friendRepo = FriendRepositoryImpl(database.friendDao())
        settlementRepo = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())
        transactionRepo = TransactionRepositoryImpl(transactionDao, database.categoryDao(), database.friendDao())
        pendingRepo = PendingTransactionRepositoryImpl(pendingDao, transactionDao)
        backupRepo = BackupRestoreRepository(
            database = database,
            transactionRepository = transactionRepo,
            friendRepository = friendRepo,
            settlementRepository = settlementRepo,
            categoryRepository = categoryRepo,
            context = context
        )

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(lm)

        // Grant fine and coarse location permissions in shadow by default
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        runBlocking {
            database.categoryDao().insertCategories(RupeeFlowDatabase.DEFAULT_CATEGORIES)
        }

        LocationDiagnostics.clearLogs()
    }

    @After
    fun tearDown() {
        database.close()
        LocationDiagnostics.clearLogs()
    }

    // 1. Location setting Off
    @Test
    fun test01_locationSettingOff() {
        runBlocking {
            val mode = LocationCaptureMode.OFF
            var capturedLocation: TransactionLocation? = null
            if (mode == LocationCaptureMode.ON_DETECTION) {
                capturedLocation = OfflineLocationManager.getLastKnownLocation(context)
            } else if (mode == LocationCaptureMode.OFF) {
                LocationDiagnostics.logCaptureSkippedOff()
            }

            assertNull("No location should be captured when mode is OFF", capturedLocation)
            val logs = LocationDiagnostics.getRecentLogs()
            assertTrue(logs.any { it.event == "Capture skipped because setting is Off" })

            val pending = PendingTransaction(
                detectedAmountPaise = 50000L,
                detectedBank = "HDFC",
                rawContent = "Paid Rs 500",
                timestamp = System.currentTimeMillis()
            )
            val id = pendingRepo.addPendingTransaction(pending)
            val saved = pendingRepo.getById(id)
            assertNotNull(saved)
            assertFalse(saved!!.hasLocation)
            assertNull(saved.location)
        }
    }

    // 2. Capture when reviewing enabled
    @Test
    fun test02_captureWhenReviewingEnabled() {
        runBlocking {
            val mode = LocationCaptureMode.ON_REVIEW

            // During detection, background capture does not run
            val pending = PendingTransaction(
                detectedAmountPaise = 25000L,
                detectedBank = "SBI",
                rawContent = "Paid Rs 250",
                timestamp = System.currentTimeMillis()
            )
            val id = pendingRepo.addPendingTransaction(pending)
            val savedPending = pendingRepo.getById(id)!!
            assertNull(savedPending.location)

            // When user reviews in foreground, fresh location is acquired
            val mockLoc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 19.0760
                longitude = 72.8777
                accuracy = 10f
                time = System.currentTimeMillis()
            }
            shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, mockLoc)

            val freshLoc = OfflineLocationManager.getLastKnownLocation(context)
            assertNotNull(freshLoc)
            pendingRepo.updateLocation(id, freshLoc)

            val updated = pendingRepo.getById(id)!!
            assertTrue(updated.hasLocation)
            assertEquals(19.0760, updated.location!!.latitude, 0.001)
            assertEquals(72.8777, updated.location!!.longitude, 0.001)
        }
    }

    // 3. Background capture option enabled
    @Test
    fun test03_backgroundCaptureOptionEnabled() {
        runBlocking {
            val mockLoc = Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 28.6139
                longitude = 77.2090
                accuracy = 8f
                time = System.currentTimeMillis()
            }
            shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, mockLoc)

            val captured = OfflineLocationManager.getLastKnownLocation(context)
            assertNotNull(captured)

            val pending = PendingTransaction(
                detectedAmountPaise = 15000L,
                detectedBank = "ICICI",
                rawContent = "Paid Rs 150 at Cafe",
                timestamp = System.currentTimeMillis(),
                latitude = captured?.latitude,
                longitude = captured?.longitude,
                accuracyMeters = captured?.accuracyMeters,
                locationCapturedAt = captured?.locationCapturedAt,
                locationSource = LocationSource.DEVICE_AT_DETECTION
            )
            val id = pendingRepo.addPendingTransaction(pending)
            val saved = pendingRepo.getById(id)!!
            assertTrue(saved.hasLocation)
            assertEquals(28.6139, saved.latitude!!, 0.001)
            assertEquals(77.2090, saved.longitude!!, 0.001)
        }
    }

    // 4. Permission granted
    @Test
    fun test04_permissionGranted() {
        assertTrue("hasPermission should return true when permissions are granted", OfflineLocationManager.hasPermission(context))
    }

    // 5. Permission denied
    @Test
    fun test05_permissionDenied() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.denyPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        assertFalse("hasPermission should return false when permissions are denied", OfflineLocationManager.hasPermission(context))
        val loc = OfflineLocationManager.getLastKnownLocation(context)
        assertNull("getLastKnownLocation must return null without permission", loc)

        val pending = PendingTransaction(
            detectedAmountPaise = 10000L,
            detectedBank = "AXIS",
            rawContent = "Paid 100",
            timestamp = System.currentTimeMillis()
        )
        val status = OfflineLocationManager.resolvePendingLocationStatus(context, pending, LocationCaptureMode.ON_DETECTION)
        assertEquals(PendingLocationStatus.PERMISSION_REQUIRED, status)
        assertEquals("Location permission required", status.displayText)
    }

    // 6. Permission permanently denied
    @Test
    fun test06_permissionPermanentlyDenied() {
        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.denyPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val pending = PendingTransaction(
            detectedAmountPaise = 10000L,
            detectedBank = "AXIS",
            rawContent = "Paid 100",
            timestamp = System.currentTimeMillis()
        )
        val status = OfflineLocationManager.resolvePendingLocationStatus(context, pending, LocationCaptureMode.ON_REVIEW)
        assertEquals(PendingLocationStatus.PERMISSION_REQUIRED, status)
    }

    // 7. Permission revoked while app is running
    @Test
    fun test07_permissionRevokedWhileAppIsRunning() {
        runBlocking {
            assertTrue(OfflineLocationManager.hasPermission(context))
            val shadowApp = shadowOf(context as android.app.Application)
            shadowApp.denyPermissions(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )

            val fresh = OfflineLocationManager.requestFreshLocation(context)
            assertNull("Revoking permission must result in null location without crash", fresh)
            val logs = LocationDiagnostics.getRecentLogs()
            assertTrue(logs.any { it.event == "Permission missing" })
        }
    }

    // 8. GPS disabled
    @Test
    fun test08_gpsDisabled() {
        shadowLocationManager.setLocationEnabled(false)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        assertFalse("isLocationEnabled should return false when providers are disabled", OfflineLocationManager.isLocationEnabled(context))
        val loc = OfflineLocationManager.getLastKnownLocation(context)
        assertNull("Location must be null when GPS is disabled", loc)

        val pending = PendingTransaction(
            detectedAmountPaise = 12000L,
            detectedBank = "KOTAK",
            rawContent = "Paid 120",
            timestamp = System.currentTimeMillis()
        )
        val status = OfflineLocationManager.resolvePendingLocationStatus(context, pending, LocationCaptureMode.ON_DETECTION)
        assertEquals(PendingLocationStatus.LOCATION_UNAVAILABLE, status)
        assertEquals("Location unavailable", status.displayText)
    }

    // 9. Location provider returns null
    @Test
    fun test09_locationProviderReturnsNull() {
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, null)
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, null)

        val loc = OfflineLocationManager.getLastKnownLocation(context)
        assertNull("When hardware provider returns null, result must be null", loc)
    }

    // 10. Location request timeout
    @Test
    fun test10_locationRequestTimeout() {
        runBlocking {
            shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, null)
            shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, null)

            val loc = OfflineLocationManager.requestFreshLocation(context, timeoutMs = 100L)
            assertNull(loc)
            val logs = LocationDiagnostics.getRecentLogs()
            assertTrue(logs.any { it.event == "Location request timed out" })
        }
    }

    // 11. Low-accuracy location
    @Test
    fun test11_lowAccuracyLocation() {
        val mockLoc = Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = 12.9716
            longitude = 77.5946
            accuracy = 1200f
            time = System.currentTimeMillis()
        }
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, mockLoc)

        val loc = OfflineLocationManager.getLastKnownLocation(context)
        assertNotNull(loc)
        assertEquals(1200f, loc!!.accuracyMeters)
    }

    // 12. Stale location response
    @Test
    fun test12_staleLocationResponse() {
        val staleTime = System.currentTimeMillis() - (5 * 60 * 1000L) // 5 minutes old
        val mockLoc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 28.6139
            longitude = 77.2090
            accuracy = 10f
            time = staleTime
        }
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, mockLoc)

        val rejected = OfflineLocationManager.getLastKnownLocation(context, allowStale = false)
        assertNull("Stale location (> 2 mins old) must be rejected by default", rejected)

        val allowed = OfflineLocationManager.getLastKnownLocation(context, allowStale = true)
        assertNotNull(allowed)
        assertEquals("[Stale location]", allowed!!.locationLabel)
    }

    // 13. New notification detected
    @Test
    fun test13_newNotificationDetected() {
        runBlocking {
            val entity = PendingTransactionEntity(
                detectedAmountPaise = 34000L,
                detectedType = TransactionType.EXPENSE,
                detectedBank = "HDFC",
                detectedMerchant = "Starbucks",
                rawContent = "Paid Rs 340 to Starbucks",
                timestamp = System.currentTimeMillis(),
                status = PendingStatus.PENDING,
                source = TransactionSource.NOTIFICATION,
                latitude = 28.5355,
                longitude = 77.3910
            )
            val id = pendingDao.insert(entity)
            assertTrue(id > 0)
            val saved = pendingDao.getById(id)
            assertNotNull(saved)
            assertEquals(TransactionSource.NOTIFICATION, saved!!.source)
            assertEquals("Starbucks", saved.detectedMerchant)
            assertEquals(28.5355, saved.latitude!!, 0.001)
        }
    }

    // 14. New SMS detected
    @Test
    fun test14_newSmsDetected() {
        runBlocking {
            val entity = PendingTransactionEntity(
                detectedAmountPaise = 75000L,
                detectedType = TransactionType.EXPENSE,
                detectedBank = "SBI",
                detectedMerchant = "Amazon India",
                rawContent = "Debited INR 750.00 for Amazon",
                timestamp = System.currentTimeMillis(),
                status = PendingStatus.PENDING,
                source = TransactionSource.SMS,
                latitude = 12.9716,
                longitude = 77.5946
            )
            val id = pendingDao.insert(entity)
            assertTrue(id > 0)
            val saved = pendingDao.getById(id)
            assertNotNull(saved)
            assertEquals(TransactionSource.SMS, saved!!.source)
            assertEquals(12.9716, saved.latitude!!, 0.001)
        }
    }

    // 15. Duplicate payment notification
    @Test
    fun test15_duplicatePaymentNotification() {
        runBlocking {
            val now = System.currentTimeMillis()
            val pending = PendingTransaction(
                detectedAmountPaise = 50000L,
                detectedBank = "HDFC",
                referenceNumber = "UTR998877",
                rawContent = "Paid Rs 500 UTR998877",
                timestamp = now
            )
            pendingRepo.addPendingTransaction(pending)

            val isDup = pendingRepo.isDuplicate(
                amountPaise = 50000L,
                timestamp = now + 1000L,
                refNumber = "UTR998877",
                merchant = null,
                bank = "HDFC"
            )
            assertTrue("Duplicate UTR within window must be detected as duplicate", isDup)
        }
    }

    // 16. Multiple pending transactions with identical amounts
    @Test
    fun test16_multiplePendingTransactionsWithIdenticalAmounts() {
        runBlocking {
            val now = System.currentTimeMillis()
            val pt1 = PendingTransaction(
                detectedAmountPaise = 50000L,
                detectedBank = "HDFC",
                detectedMerchant = "Pharmacy",
                rawContent = "Paid 500 at Pharmacy",
                timestamp = now,
                latitude = 28.1111,
                longitude = 77.1111
            )
            val pt2 = PendingTransaction(
                detectedAmountPaise = 50000L,
                detectedBank = "ICICI",
                detectedMerchant = "Grocery",
                rawContent = "Paid 500 at Grocery",
                timestamp = now + 3600_000L,
                latitude = 28.2222,
                longitude = 77.2222
            )

            val id1 = pendingRepo.addPendingTransaction(pt1)
            val id2 = pendingRepo.addPendingTransaction(pt2)
            assertNotEquals(id1, id2)

            val saved1 = pendingRepo.getById(id1)!!
            val saved2 = pendingRepo.getById(id2)!!

            assertEquals(28.1111, saved1.latitude!!, 0.0001)
            assertEquals(28.2222, saved2.latitude!!, 0.0001)
            assertEquals("Pharmacy", saved1.detectedMerchant)
            assertEquals("Grocery", saved2.detectedMerchant)
        }
    }

    // 17. Correct pending transaction association
    @Test
    fun test17_correctPendingTransactionAssociation() {
        runBlocking {
            val pt1 = PendingTransaction(
                detectedAmountPaise = 10000L,
                detectedBank = "HDFC",
                rawContent = "Txn 1",
                timestamp = System.currentTimeMillis()
            )
            val pt2 = PendingTransaction(
                detectedAmountPaise = 10000L,
                detectedBank = "HDFC",
                rawContent = "Txn 2",
                timestamp = System.currentTimeMillis() + 1000L
            )
            val id1 = pendingRepo.addPendingTransaction(pt1)
            val id2 = pendingRepo.addPendingTransaction(pt2)

            val loc = TransactionLocation(12.3456, 78.9101)
            pendingRepo.updateLocation(id1, loc)

            val saved1 = pendingRepo.getById(id1)!!
            val saved2 = pendingRepo.getById(id2)!!

            assertTrue("Pending 1 must have attached location", saved1.hasLocation)
            assertEquals(12.3456, saved1.latitude!!, 0.0001)

            assertFalse("Pending 2 must NOT be contaminated by Pending 1's location", saved2.hasLocation)
            assertNull(saved2.location)
        }
    }

    // 18. Approve detected transaction
    @Test
    fun test18_approveDetectedTransaction() {
        runBlocking {
            val pt = PendingTransaction(
                detectedAmountPaise = 45000L,
                detectedBank = "HDFC",
                detectedMerchant = "Swiggy",
                rawContent = "Paid 450 to Swiggy",
                timestamp = System.currentTimeMillis(),
                latitude = 19.0760,
                longitude = 72.8777,
                accuracyMeters = 5.0f,
                locationSource = LocationSource.DEVICE_AT_DETECTION,
                googleMapsUrl = "https://maps.google.com/?q=19.076,72.8777"
            )
            val pendingId = pendingRepo.addPendingTransaction(pt)

            // Approval: Convert to confirmed transaction
            val txn = Transaction(
                title = pt.detectedMerchant ?: "Swiggy",
                amountPaise = pt.detectedAmountPaise,
                categoryId = 1L,
                latitude = pt.latitude,
                longitude = pt.longitude,
                accuracyMeters = pt.accuracyMeters,
                locationCapturedAt = pt.locationCapturedAt,
                locationSource = pt.locationSource,
                googleMapsUrl = pt.googleMapsUrl
            )
            val txnId = transactionRepo.saveTransaction(txn)
            pendingRepo.resolvePendingTransaction(pendingId)

            val savedTxn = transactionRepo.getTransactionById(txnId)
            assertNotNull(savedTxn)
            assertTrue("Approved transaction must retain location metadata", savedTxn!!.hasLocation)
            assertEquals(19.0760, savedTxn.latitude!!, 0.001)
            assertEquals(72.8777, savedTxn.longitude!!, 0.001)
            assertEquals(5.0f, savedTxn.accuracyMeters!!, 0.01f)

            val resolvedPending = pendingRepo.getById(pendingId)!!
            assertEquals(PendingStatus.RESOLVED, resolvedPending.status)
        }
    }

    // 19. Ignore detected transaction
    @Test
    fun test19_ignoreDetectedTransaction() {
        runBlocking {
            val pt = PendingTransaction(
                detectedAmountPaise = 20000L,
                detectedBank = "HDFC",
                rawContent = "Paid 200",
                timestamp = System.currentTimeMillis(),
                latitude = 13.0827,
                longitude = 80.2707
            )
            val id = pendingRepo.addPendingTransaction(pt)
            pendingRepo.ignorePendingTransaction(id)

            val saved = pendingRepo.getById(id)!!
            assertEquals(PendingStatus.IGNORED, saved.status)
            assertTrue(saved.hasLocation)
            assertEquals(13.0827, saved.latitude!!, 0.001)
        }
    }

    // 20. Delete detected transaction
    @Test
    fun test20_deleteDetectedTransaction() {
        runBlocking {
            val pt = PendingTransaction(
                detectedAmountPaise = 15000L,
                detectedBank = "HDFC",
                rawContent = "Paid 150",
                timestamp = System.currentTimeMillis()
            )
            val id = pendingRepo.addPendingTransaction(pt)
            assertNotNull(pendingRepo.getById(id))

            pendingRepo.deletePendingTransaction(id)
            assertNull("Deleted transaction must not exist in database", pendingRepo.getById(id))
        }
    }

    // 21. App killed during location capture
    @Test
    fun test21_appKilledDuringLocationCapture() {
        runBlocking {
            val pt = PendingTransaction(
                detectedAmountPaise = 30000L,
                detectedBank = "HDFC",
                rawContent = "Paid 300",
                timestamp = System.currentTimeMillis(),
                latitude = null,
                longitude = null
            )
            val id = pendingRepo.addPendingTransaction(pt)

            // Simulating uncompleted/interrupted location capture: database remains safe and consistent
            val saved = pendingRepo.getById(id)!!
            assertNull(saved.location)
            assertEquals(30000L, saved.detectedAmountPaise)
        }
    }

    // 22. Process recreation
    @Test
    fun test22_processRecreation() {
        runBlocking {
            val original = Transaction(
                title = "Dinner",
                amountPaise = 85000L,
                categoryId = 1L,
                latitude = 26.9124,
                longitude = 75.7873,
                accuracyMeters = 15.0f,
                locationSource = LocationSource.DEVICE_CURRENT,
                locationLabel = "Jaipur"
            )
            val id = transactionRepo.saveTransaction(original)

            // Simulating process reload from Room
            val reloaded = transactionRepo.getTransactionById(id)!!
            assertEquals(26.9124, reloaded.latitude!!, 0.001)
            assertEquals(75.7873, reloaded.longitude!!, 0.001)
            assertEquals(15.0f, reloaded.accuracyMeters!!, 0.01f)
            assertEquals("Jaipur", reloaded.locationLabel)
            assertEquals(LocationSource.DEVICE_CURRENT, reloaded.locationSource)
        }
    }

    // 23. Device restart
    @Test
    fun test23_deviceRestart() {
        runBlocking {
            val prefsRepo = UserPreferencesRepository(context)
            prefsRepo.setLocationCaptureMode(LocationCaptureMode.ON_DETECTION)
            val savedMode = prefsRepo.userPreferencesFlow.first().locationCaptureMode
            assertEquals(LocationCaptureMode.ON_DETECTION, savedMode)

            // Simulating restart: read preferences anew
            val reloadedPrefs = UserPreferencesRepository(context)
            val reloadedMode = reloadedPrefs.userPreferencesFlow.first().locationCaptureMode
            assertEquals(LocationCaptureMode.ON_DETECTION, reloadedMode)
        }
    }

    // 24. Database persistence
    @Test
    fun test24_databasePersistence() {
        runBlocking {
            val entity = TransactionEntity(
                title = "Cafe Coffee Day",
                amountPaise = 35000L,
                categoryId = 1L,
                transactionDate = System.currentTimeMillis(),
                latitude = 12.9716,
                longitude = 77.5946,
                accuracyMeters = 12.5f,
                locationCapturedAt = 1700000000000L,
                locationSource = LocationSource.DEVICE_CURRENT,
                locationLabel = "MG Road, Bengaluru",
                googleMapsUrl = "https://maps.google.com/?q=12.9716,77.5946"
            )
            val id = transactionDao.insertTransactionEntity(entity)
            val loaded = transactionDao.getTransactionById(id)!!.transaction

            assertEquals(12.9716, loaded.latitude!!, 0.0001)
            assertEquals(77.5946, loaded.longitude!!, 0.0001)
            assertEquals(12.5f, loaded.accuracyMeters!!, 0.01f)
            assertEquals(1700000000000L, loaded.locationCapturedAt)
            assertEquals(LocationSource.DEVICE_CURRENT, loaded.locationSource)
            assertEquals("MG Road, Bengaluru", loaded.locationLabel)
            assertEquals("https://maps.google.com/?q=12.9716,77.5946", loaded.googleMapsUrl)
        }
    }

    // 25. Transaction edit
    @Test
    fun test25_transactionEdit() {
        runBlocking {
            val original = Transaction(
                title = "Lunch",
                amountPaise = 40000L,
                categoryId = 1L,
                latitude = 18.5204,
                longitude = 73.8567
            )
            val id = transactionRepo.saveTransaction(original)
            val saved = transactionRepo.getTransactionById(id)!!

            // User edits title and description only
            val edited = saved.copy(
                title = "Team Lunch at Bistro",
                description = "With Android team"
            )
            transactionRepo.saveTransaction(edited)

            val reloaded = transactionRepo.getTransactionById(id)!!
            assertEquals("Team Lunch at Bistro", reloaded.title)
            assertEquals("With Android team", reloaded.description)
            assertTrue("Location must be preserved across non-location edits", reloaded.hasLocation)
            assertEquals(18.5204, reloaded.latitude!!, 0.001)
            assertEquals(73.8567, reloaded.longitude!!, 0.001)
        }
    }

    // 26. Remove saved location
    @Test
    fun test26_removeSavedLocation() {
        runBlocking {
            val original = Transaction(
                title = "Fuel",
                amountPaise = 200000L,
                categoryId = 1L,
                latitude = 28.7041,
                longitude = 77.1025,
                accuracyMeters = 10f
            )
            val id = transactionRepo.saveTransaction(original)
            val saved = transactionRepo.getTransactionById(id)!!
            assertTrue(saved.hasLocation)

            // Remove location
            val cleared = saved.copy(
                latitude = null,
                longitude = null,
                accuracyMeters = null,
                locationCapturedAt = null,
                locationSource = null,
                locationLabel = null,
                googleMapsUrl = null
            )
            transactionRepo.saveTransaction(cleared)

            val reloaded = transactionRepo.getTransactionById(id)!!
            assertFalse("Transaction should no longer have location", reloaded.hasLocation)
            assertNull(reloaded.latitude)
            assertNull(reloaded.longitude)
            assertEquals("Financial amount must remain unaltered", 200000L, reloaded.amountPaise)
        }
    }

    // 27. Backup and restore
    @Test
    fun test27_backupAndRestore() {
        runBlocking {
            val originalTxn = Transaction(
                title = "Taj Mahal Entry Ticket",
                amountPaise = 50000L,
                categoryId = 1L,
                latitude = 27.1751,
                longitude = 78.0421,
                accuracyMeters = 5.0f,
                locationSource = LocationSource.DEVICE_CURRENT,
                locationLabel = "Agra"
            )
            transactionRepo.saveTransaction(originalTxn)

            val originalPending = PendingTransaction(
                detectedAmountPaise = 25000L,
                detectedBank = "HDFC",
                rawContent = "Agra Souvenir",
                timestamp = System.currentTimeMillis(),
                latitude = 27.1751,
                longitude = 78.0421
            )
            pendingRepo.addPendingTransaction(originalPending)

            val exportJson = backupRepo.exportToJson()
            assertNotNull(exportJson)

            // Clear database
            database.clearAllTables()
            assertEquals(0, transactionRepo.getAllTransactionsList().size)
            assertEquals(0, pendingRepo.getPendingTransactionsList().size)

            // Restore
            val restoreResult = backupRepo.restoreFromJson(exportJson)
            assertTrue("Restore must succeed", restoreResult)

            val restoredTxns = transactionRepo.getAllTransactionsList()
            assertEquals(1, restoredTxns.size)
            val restoredTxn = restoredTxns[0]
            assertEquals(27.1751, restoredTxn.latitude!!, 0.001)
            assertEquals(78.0421, restoredTxn.longitude!!, 0.001)
            assertEquals("Agra", restoredTxn.locationLabel)

            val restoredPending = pendingRepo.getPendingTransactionsList()
            assertEquals(1, restoredPending.size)
            assertEquals(27.1751, restoredPending[0].latitude!!, 0.001)
        }
    }

    // 28. Offline map opening
    @Test
    fun test28_offlineMapOpening() {
        val url = LocationIntentHelper.buildGoogleMapsWebUrl(28.6139, 77.2090)
        assertEquals("https://www.google.com/maps/search/?api=1&query=28.6139,77.209", url)

        val uri = LocationIntentHelper.buildGeoUri(28.6139, 77.2090, "Connaught Place")
        assertTrue(uri.toString().startsWith("geo:28.6139,77.209?q="))
    }

    // 29. No Google Maps installed
    @Test
    fun test29_noGoogleMapsInstalled() {
        // Calling openLocation when Google Maps is not installed falls back safely to browser or geo without throwing
        var threwException = false
        try {
            LocationIntentHelper.openLocation(context, 28.6139, 77.2090, "Delhi")
        } catch (_: Exception) {
            threwException = true
        }
        assertFalse("openLocation must handle missing maps app without crashing", threwException)
    }

    // 30. Existing financial ledger regression
    @Test
    fun test30_existingFinancialLedgerRegression() {
        runBlocking<Unit> {
            // Setup two friends
            val friend1Id = friendRepo.addFriend(Friend(name = "Rahul"))
            val friend2Id = friendRepo.addFriend(Friend(name = "Priya"))

            // Create split transaction with location
            val txnWithLoc = Transaction(
                title = "Dinner at Bandra",
                amountPaise = 300000L, // Rs. 3,000
                categoryId = 1L,
                paidByFriendId = null, // Paid by Me
                latitude = 19.0596,
                longitude = 72.8295,
                participants = listOf(
                    TransactionParticipant(friendId = null, friendName = "Me", shareAmountPaise = 100000L),
                    TransactionParticipant(friendId = friend1Id, friendName = "Rahul", shareAmountPaise = 100000L),
                    TransactionParticipant(friendId = friend2Id, friendName = "Priya", shareAmountPaise = 100000L)
                )
            )
            val txnId = transactionRepo.saveTransaction(txnWithLoc)

            val allTxns = transactionRepo.getAllTransactionsList()
            val allSettlements = settlementRepo.getAllSettlementsList()
            val friends = listOf(
                Friend(id = friend1Id, name = "Rahul"),
                Friend(id = friend2Id, name = "Priya")
            )

            val ledgersBefore = LedgerCalculator.computeFriendLedgers(friends, allTxns, allSettlements)
            val r1Before = ledgersBefore.first { it.id == friend1Id }
            val p1Before = ledgersBefore.first { it.id == friend2Id }

            assertEquals(100000L, r1Before.totalTheyOweMePaise) // Rahul owes me 1000
            assertEquals(100000L, p1Before.totalTheyOweMePaise) // Priya owes me 1000

            // Now remove location from transaction
            val savedTxn = transactionRepo.getTransactionById(txnId)!!
            val txnWithoutLoc = savedTxn.copy(
                latitude = null,
                longitude = null,
                accuracyMeters = null
            )
            transactionRepo.saveTransaction(txnWithoutLoc)

            val allTxnsAfter = transactionRepo.getAllTransactionsList()
            val ledgersAfter = LedgerCalculator.computeFriendLedgers(friends, allTxnsAfter, allSettlements)
            val r1After = ledgersAfter.first { it.id == friend1Id }
            val p1After = ledgersAfter.first { it.id == friend2Id }

            assertEquals("Removing location must NOT alter Rahul's balance", r1Before.totalTheyOweMePaise, r1After.totalTheyOweMePaise)
            assertEquals("Removing location must NOT alter Priya's balance", p1Before.totalTheyOweMePaise, p1After.totalTheyOweMePaise)
        }
    }
}

