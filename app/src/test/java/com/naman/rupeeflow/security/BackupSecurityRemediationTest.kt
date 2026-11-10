package com.naman.rupeeflow.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackupSecurityRemediationTest {

    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var friendRepo: FriendRepository
    private lateinit var groupRepo: FriendGroupRepository
    private lateinit var txnRepo: TransactionRepository
    private lateinit var settlementRepo: SettlementRepository
    private lateinit var backupRepo: BackupRestoreRepository

    private val strongPassword = "StrongProductionPassword2026!"
    private val sampleValidImageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) // PNG header
    private val sampleBase64Image = Base64.getEncoder().encodeToString(sampleValidImageBytes)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        categoryRepo = CategoryRepositoryImpl(database.categoryDao())
        friendRepo = FriendRepositoryImpl(database.friendDao())
        groupRepo = FriendGroupRepositoryImpl(database.friendGroupDao())
        txnRepo = TransactionRepositoryImpl(database.transactionDao(), database.categoryDao(), database.friendDao())
        settlementRepo = SettlementRepositoryImpl(database.settlementDao(), database.friendDao())
        backupRepo = BackupRestoreRepository(
            database = database,
            transactionRepository = txnRepo,
            friendRepository = friendRepo,
            settlementRepository = settlementRepo,
            categoryRepository = categoryRepo,
            friendGroupRepository = groupRepo,
            context = context
        )

        runBlocking {
            database.categoryDao().insertCategory(
                CategoryEntity(id = 1L, name = "Food", iconName = "restaurant", colorHex = "#EF4444", isDefault = true)
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // =========================================================================
    // SECTION 1: PATH TRAVERSAL & FILENAME VALIDATION TESTS
    // =========================================================================

    @Test
    fun test01_RejectDotDotSlashTraversal() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("../evil.png"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("../../receipts/bill.jpg"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("receipts/../bill.jpg"))
    }

    @Test
    fun test02_RejectDotDotBackslashTraversal() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("..\\evil.png"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("..\\..\\receipts\\bill.jpg"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("receipts\\..\\bill.jpg"))
    }

    @Test
    fun test03_RejectAbsolutePaths() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("/etc/passwd"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("/data/data/com.naman.rupeeflow/databases/rupeeflow.db"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("C:\\Windows\\System32\\cmd.exe"))
    }

    @Test
    fun test04_RejectNestedDirectoryPaths() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("sub/nested/image.jpg"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("receipts/nested.png"))
    }

    @Test
    fun test05_RejectEmptyAndBlankFilenames() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename(""))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("   "))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename(null))
    }

    @Test
    fun test06_RejectExcessivelyLongFilenames() {
        val longFilename = "a".repeat(129) + ".png"
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename(longFilename))
    }

    @Test
    fun test07_RejectNullBytesAndControlCharacters() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_image\u0000.png"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_image\r\n.jpg"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_image\t.png"))
    }

    @Test
    fun test08_RejectHiddenFilesAndDisallowedExtensions() {
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename(".hidden_image.jpg"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename(".profile.png"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("payload.sh"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("payload.apk"))
        assertFalse(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("payload.dex"))
    }

    @Test
    fun test09_AcceptValidImageFilenames() {
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_1731234567890.jpg"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_1731234567890.png"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("group_goa_trip.webp"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("profile_sukrut-2026.jpeg"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("attachment_binary.bin"))
        assertTrue(BackupRestoreRepository.AttachmentFilenameValidator.isValidFilename("bill_photo_raw"))
    }

    @Test
    fun test10_DatabaseOverwriteAttemptBlockedInRestore() = runBlocking {
        // Construct malicious JSON backup with path traversal in images
        val maliciousJson = JSONObject().apply {
            put("version", BackupRestoreRepository.CURRENT_BACKUP_VERSION)
            put("appName", BackupRestoreRepository.BACKUP_APP_NAME)
            put("exportTimestamp", System.currentTimeMillis())

            val images = JSONObject().apply {
                put("../../databases/rupeeflow.db", sampleBase64Image)
                put("..\\..\\databases\\rupeeflow.db", sampleBase64Image)
                put("/data/data/com.naman.rupeeflow/databases/rupeeflow.db", sampleBase64Image)
                put("../../shared_prefs/prefs.xml", sampleBase64Image)
            }
            put("images", images)
            put("categories", JSONArray())
        }.toString()

        val restored = backupRepo.restoreFromJson(maliciousJson)
        // Even if database portion is restored, none of the malicious files should be written outside
        val dbFile = File(context.filesDir.parentFile, "databases/rupeeflow.db")
        if (dbFile.exists()) {
            assertNotEquals("Database file content must not match sample image payload",
                String(sampleValidImageBytes), dbFile.readText())
        }

        val evilFile1 = File(context.filesDir, "../../databases/rupeeflow.db")
        val evilFile2 = File(context.filesDir, "receipts/../../databases/rupeeflow.db")
        assertFalse(evilFile1.exists())
        assertFalse(evilFile2.exists())
    }

    @Test
    fun test11_RestoreFailureCleansUpStagingDirectory() = runBlocking {
        // Construct backup with valid images but invalid corrupted database transaction
        val corruptJson = JSONObject().apply {
            put("version", BackupRestoreRepository.CURRENT_BACKUP_VERSION)
            put("appName", BackupRestoreRepository.BACKUP_APP_NAME)
            put("exportTimestamp", System.currentTimeMillis())

            val images = JSONObject().apply {
                put("bill_test_staging.jpg", sampleBase64Image)
            }
            put("images", images)

            // Invalid category object missing required "name" causing exception during withTransaction
            val badCategories = JSONArray().apply {
                put(JSONObject().apply { put("id", 999L) }) // missing "name"
            }
            put("categories", badCategories)
        }.toString()

        val result = backupRepo.restoreFromJson(corruptJson)
        assertFalse("Restore must fail when database insertion throws exception", result)

        // Verify no staging directories remain in filesDir
        val stagingDirs = context.filesDir.listFiles { _, name -> name.startsWith("staging_restore_") }
        assertTrue("All staging directories must be cleaned up on failure", stagingDirs.isNullOrEmpty())

        // Verify destination file was not finalized
        val receiptsDir = File(context.filesDir, "receipts")
        val destFile = File(receiptsDir, "bill_test_staging.jpg")
        assertFalse("Staged image must not be finalized if transaction failed", destFile.exists())
    }

    // =========================================================================
    // SECTION 2: BACKUP ENCRYPTION HARDENING & PERFORMANCE BENCHMARK
    // =========================================================================

    @Test
    fun test12_EnforceMinimum12CharacterPasswordPolicy() = runBlocking {
        try {
            backupRepo.exportEncryptedBackup("Short6!")
            fail("Expected exception for password shorter than 12 characters")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 12 characters") == true)
        }

        try {
            backupRepo.exportEncryptedBackup("ElevenChars")
            fail("Expected exception for 11 character password")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 12 characters") == true)
        }

        // Exactly 12 characters succeeds
        val envelope = backupRepo.exportEncryptedBackup("TwelveChar1!")
        assertTrue(envelope.contains("\"encrypted\": true"))
    }

    @Test
    fun test13_DecryptionWithCorrectPasswordSucceeds() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val decrypted = backupRepo.decryptBackup(envelope, strongPassword)
        assertTrue(decrypted.contains(BackupRestoreRepository.BACKUP_APP_NAME))
    }

    @Test
    fun test14_DecryptionWithIncorrectPasswordFails() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        try {
            backupRepo.decryptBackup(envelope, "WrongPassword2026!")
            fail("Expected cryptographic error for incorrect password")
        } catch (_: Exception) {
            // Success
        }
    }

    @Test
    fun test15_DecryptionWithTamperedCiphertextFails() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val obj = JSONObject(envelope)
        val realCiphertext = Base64.getDecoder().decode(obj.getString("ciphertext"))

        // Tamper with one byte in the ciphertext payload
        val mid = realCiphertext.size / 2
        realCiphertext[mid] = (realCiphertext[mid].toInt() xor 0xFF).toByte()
        obj.put("ciphertext", Base64.getEncoder().encodeToString(realCiphertext))

        try {
            backupRepo.decryptBackup(obj.toString(), strongPassword)
            fail("Expected GCM authentication tag verification failure on tampered ciphertext")
        } catch (_: Exception) {
            // AEADBadTagException expected
        }
    }

    @Test
    fun test16_DecryptionWithModifiedIVFails() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val obj = JSONObject(envelope)
        val iv = Base64.getDecoder().decode(obj.getString("iv"))
        iv[0] = (iv[0].toInt() xor 0x01).toByte() // Flip bit in IV
        obj.put("iv", Base64.getEncoder().encodeToString(iv))

        try {
            backupRepo.decryptBackup(obj.toString(), strongPassword)
            fail("Expected authentication tag failure on modified IV")
        } catch (_: Exception) {
            // Success
        }
    }

    @Test
    fun test17_DecryptionWithModifiedSaltFails() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val obj = JSONObject(envelope)
        val salt = Base64.getDecoder().decode(obj.getString("salt"))
        salt[0] = (salt[0].toInt() xor 0x01).toByte() // Flip bit in Salt
        obj.put("salt", Base64.getEncoder().encodeToString(salt))

        try {
            backupRepo.decryptBackup(obj.toString(), strongPassword)
            fail("Expected authentication failure on modified salt")
        } catch (_: Exception) {
            // Success
        }
    }

    @Test
    fun test18_RejectTooSmallIterationCount() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val obj = JSONObject(envelope)
        obj.put("iterations", 999) // Below minimum 10,000 bound

        try {
            backupRepo.decryptBackup(obj.toString(), strongPassword)
            fail("Expected rejection of dangerously small iteration count")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("iteration count too low") == true)
        }
    }

    @Test
    fun test19_RejectExcessivelyLargeIterationCountDoS() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val obj = JSONObject(envelope)
        obj.put("iterations", 10_000_000) // Exceeds max 2,000,000 bound

        try {
            backupRepo.decryptBackup(obj.toString(), strongPassword)
            fail("Expected rejection of excessively large iteration count to prevent DoS")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Excessively large PBKDF2 iteration count") == true)
        }
    }

    @Test
    fun test20_RejectUnsupportedKDF() = runBlocking {
        val envelope = backupRepo.exportEncryptedBackup(strongPassword)
        val obj = JSONObject(envelope)
        obj.put("kdf", "MD5WithHmac")

        try {
            backupRepo.decryptBackup(obj.toString(), strongPassword)
            fail("Expected rejection of unsupported KDF")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Unsupported KDF") == true)
        }
    }

    @Test
    fun test21_RejectInvalidBase64PayloadBeforeExpensiveCrypto() {
        val invalidEnvelope = JSONObject().apply {
            put("appName", BackupRestoreRepository.BACKUP_APP_NAME)
            put("version", BackupRestoreRepository.CURRENT_BACKUP_VERSION)
            put("encrypted", true)
            put("kdf", BackupRestoreRepository.KDF_ALGORITHM)
            put("iterations", 600_000)
            put("salt", "not-valid-base64-!@#$%^")
            put("iv", Base64.getEncoder().encodeToString(ByteArray(12)))
            put("ciphertext", Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3)))
        }.toString()

        try {
            backupRepo.decryptBackup(invalidEnvelope, strongPassword)
            fail("Expected failure on invalid Base64 salt")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid Base64") == true)
        }
    }

    @Test
    fun test22_LegacyBackupBackwardCompatibility() = runBlocking {
        // Simulate legacy backup from earlier version with 65,536 iterations and 8-character password
        val legacyPassword = "legacyPass1"
        val plainJson = JSONObject().apply {
            put("version", 1)
            put("appName", BackupRestoreRepository.BACKUP_APP_NAME)
            put("exportTimestamp", System.currentTimeMillis())
            put("categories", JSONArray())
            put("friends", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", 77L)
                    put("name", "Legacy Friend 2024")
                })
            })
        }.toString()

        // Manually craft legacy encrypted envelope with 65,536 iterations
        val salt = ByteArray(16) { 0x01 }
        val iv = ByteArray(12) { 0x02 }
        val keySpec = javax.crypto.spec.PBEKeySpec(legacyPassword.toCharArray(), salt, 65_536, 256)
        val secretKeyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")

        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))

        val legacyEnvelope = JSONObject().apply {
            put("appName", BackupRestoreRepository.BACKUP_APP_NAME)
            put("version", 1)
            put("encrypted", true)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", 65_536)
            put("salt", Base64.getEncoder().encodeToString(salt))
            put("iv", Base64.getEncoder().encodeToString(iv))
            put("ciphertext", Base64.getEncoder().encodeToString(ciphertext))
        }.toString()

        // Decrypt legacy backup
        val decrypted = backupRepo.decryptBackup(legacyEnvelope, legacyPassword)
        assertTrue(decrypted.contains("Legacy Friend 2024"))

        // Restore legacy backup into database
        val restored = backupRepo.restoreFromJson(decrypted)
        assertTrue(restored)

        val friends = friendRepo.getAllFriendsList()
        assertTrue(friends.any { it.name == "Legacy Friend 2024" })
    }

    @Test
    fun test23_PBKDF2Iteration600kPerformanceBenchmark() = runBlocking {
        // Benchmark 600,000 iterations
        val elapsedMs = measureTimeMillis {
            val envelope = backupRepo.exportEncryptedBackup(strongPassword)
            val decrypted = backupRepo.decryptBackup(envelope, strongPassword)
            assertTrue(decrypted.contains(BackupRestoreRepository.BACKUP_APP_NAME))
        }

        // Encryption + Decryption with 600,000 iterations takes acceptable wall-clock time
        // and provides cryptographic protection against GPU brute force
        assertTrue("600k iterations must complete within reasonable time limit", elapsedMs < 5000)
    }

    // =========================================================================
    // SECTION 3: ATOMIC DATABASE AND ATTACHMENT RESTORATION
    // =========================================================================

    @Test
    fun test24_AtomicRestorePreservesDatabaseOnInterruptedRestore() = runBlocking {
        // Seed initial transaction
        txnRepo.saveTransaction(
            Transaction(id = 1L, title = "Original Expense", amountPaise = 10000L, categoryId = 1L, transactionDate = System.currentTimeMillis(), paymentMethod = PaymentMethod.CASH, transactionType = TransactionType.EXPENSE)
        )
        val initialTxns = txnRepo.getAllTransactionsList()
        assertEquals(1, initialTxns.size)
        assertEquals("Original Expense", initialTxns[0].title)

        // Attempt restore of invalid payload with malformed structure
        val invalidPayload = "{ \"appName\": \"RupeeFlow\", \"version\": 4, \"categories\": \"NOT_AN_ARRAY\" }"
        val success = backupRepo.restoreFromJson(invalidPayload)
        assertFalse("Restore must return false for corrupted backup", success)

        // Existing transaction must still be present and intact
        val preservedTxns = txnRepo.getAllTransactionsList()
        assertEquals(1, preservedTxns.size)
        assertEquals("Original Expense", preservedTxns[0].title)
    }
}
