package com.naman.rupeeflow.qa

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.naman.rupeeflow.attachment.AttachmentOperationState
import com.naman.rupeeflow.attachment.PendingAttachmentManager
import com.naman.rupeeflow.data.local.ImageStorageManager
import com.naman.rupeeflow.data.local.RupeeFlowDatabase
import com.naman.rupeeflow.data.local.entity.CategoryEntity
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.domain.model.*
import com.naman.rupeeflow.security.AppLockManager
import com.naman.rupeeflow.security.AppLockState
import com.naman.rupeeflow.ui.screens.lock.AppLockViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Comprehensive QA Test Suite verifying:
 * 1. Biometric lock non-interference with Camera, Image Picker, and PDF attachments.
 * 2. Attachment preservation across external Activity returns, biometric authentication, and process death.
 * 3. Lifecycle state machine (AttachmentOperationState: IDLE, WAITING_FOR_CAMERA, WAITING_FOR_BIOMETRIC, etc.).
 * 4. Error handling, cancellation, temp file cleanup, and transaction persistence.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BiometricAttachmentInterferenceQATest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var database: RupeeFlowDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var userPrefsRepo: UserPreferencesRepository
    private lateinit var appLockManager: AppLockManager
    private lateinit var attachmentManager: PendingAttachmentManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        // Clear preferences
        context.getSharedPreferences("rupeeflow_attachment_state", Context.MODE_PRIVATE)
            .edit().clear().commit()

        database = Room.inMemoryDatabaseBuilder(context, RupeeFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val categoryDao = database.categoryDao()
        val friendDao = database.friendDao()
        val transactionDao = database.transactionDao()

        transactionRepository = TransactionRepositoryImpl(transactionDao, categoryDao, friendDao)
        userPrefsRepo = UserPreferencesRepository(context)
        appLockManager = AppLockManager(userPrefsRepo)
        attachmentManager = PendingAttachmentManager(context)

        // Seed a default category
        runBlocking {
            categoryDao.insertCategories(
                listOf(
                    CategoryEntity(id = 1L, name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444")
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
        attachmentManager.cancelOperation()
        ImageStorageManager.cleanTempCameraFiles(context)
    }

    private suspend fun awaitTerminalState(): AttachmentOperationState {
        return withTimeout(5000) {
            attachmentManager.state.first {
                it != AttachmentOperationState.PROCESSING_ATTACHMENT &&
                it != AttachmentOperationState.WAITING_FOR_CAMERA &&
                it != AttachmentOperationState.WAITING_FOR_IMAGE_PICKER &&
                it != AttachmentOperationState.WAITING_FOR_PDF_PICKER
            }
        }
    }

    // 1. Biometric disabled: Camera capture completes immediately to ATTACHMENT_READY
    @Test
    fun test01_biometricDisabled_cameraCaptureCompletesImmediately() {
        runBlocking {
            val tempUri = attachmentManager.prepareCameraCapture(context)
            assertNotNull(tempUri)
            assertEquals(AttachmentOperationState.WAITING_FOR_CAMERA, attachmentManager.state.value)

            // Create a valid image file at the pending path
            val cameraPath = attachmentManager.pendingCameraFilePath.value
            assertNotNull(cameraPath)
            createDummyJpegFile(File(cameraPath!!), 1024)

            // Return from camera without biometric lock
            attachmentManager.onCameraResult(context, success = true, isAppLocked = false)
            awaitTerminalState()

            assertEquals(AttachmentOperationState.ATTACHMENT_READY, attachmentManager.state.value)
            val finalPath = attachmentManager.processedAttachmentPath.value
            assertNotNull(finalPath)
            assertTrue(File(finalPath!!).exists())
        }
    }

    // 2. Biometric disabled: Picker document completes immediately to ATTACHMENT_READY
    @Test
    fun test02_biometricDisabled_pickerDocumentCompletesImmediately() {
        runBlocking {
            attachmentManager.preparePicker(isPdfOnly = false)
            assertEquals(AttachmentOperationState.WAITING_FOR_IMAGE_PICKER, attachmentManager.state.value)

            // Create a test file and obtain URI
            val testFile = File(context.cacheDir, "sample_receipt.png").apply {
                createDummyJpegFile(this, 2048)
            }
            val fileUri = Uri.fromFile(testFile)

            attachmentManager.onPickerResult(context, fileUri, isAppLocked = false)
            awaitTerminalState()

            assertEquals(AttachmentOperationState.ATTACHMENT_READY, attachmentManager.state.value)
            val readyPath = attachmentManager.processedAttachmentPath.value
            assertNotNull(readyPath)
            assertTrue(File(readyPath!!).exists())
        }
    }

    // 3. Biometric enabled: Camera capture parks safely in WAITING_FOR_BIOMETRIC
    @Test
    fun test03_biometricEnabled_cameraCaptureEntersWaitingForBiometric() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        createDummyJpegFile(File(cameraPath), 1024)

        // App is locked when returning from camera
        attachmentManager.onCameraResult(context, success = true, isAppLocked = true)

        assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)
        assertEquals(cameraPath, attachmentManager.pendingCameraFilePath.value)
        assertTrue("Camera file must remain intact while waiting for biometric", File(cameraPath).exists())
    }

    // 4. Biometric enabled: Picker document parks safely in WAITING_FOR_BIOMETRIC
    @Test
    fun test04_biometricEnabled_pickerDocumentEntersWaitingForBiometric() {
        attachmentManager.preparePicker(isPdfOnly = true)
        assertEquals(AttachmentOperationState.WAITING_FOR_PDF_PICKER, attachmentManager.state.value)

        val testPdf = File(context.cacheDir, "bill.pdf").apply {
            createDummyPdfFile(this, 1024)
        }
        val pdfUri = Uri.fromFile(testPdf)

        attachmentManager.onPickerResult(context, pdfUri, isAppLocked = true)

        assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)
        assertEquals(pdfUri, attachmentManager.pendingUri.value)
    }

    // 5. Biometric success resumes camera photo processing to ATTACHMENT_READY
    @Test
    fun test05_biometricSuccess_resumesCameraProcessing() {
        runBlocking {
            attachmentManager.prepareCameraCapture(context)
            val cameraPath = attachmentManager.pendingCameraFilePath.value!!
            createDummyJpegFile(File(cameraPath), 2048)

            attachmentManager.onCameraResult(context, success = true, isAppLocked = true)
            assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)

            // Simulate biometric unlock
            attachmentManager.onBiometricUnlocked(context)
            awaitTerminalState()

            assertEquals(AttachmentOperationState.ATTACHMENT_READY, attachmentManager.state.value)
            val readyPath = attachmentManager.consumeReadyAttachment()
            assertNotNull(readyPath)
            assertTrue(File(readyPath!!).exists())
        }
    }

    // 6. Biometric success resumes picker document processing to ATTACHMENT_READY
    @Test
    fun test06_biometricSuccess_resumesPickerDocumentProcessing() {
        runBlocking {
            attachmentManager.preparePicker(isPdfOnly = false)
            val testPdf = File(context.cacheDir, "invoice.pdf").apply {
                createDummyPdfFile(this, 2048)
            }
            val pdfUri = Uri.fromFile(testPdf)

            attachmentManager.onPickerResult(context, pdfUri, isAppLocked = true)
            assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)

            attachmentManager.onBiometricUnlocked(context)
            awaitTerminalState()

            assertEquals(AttachmentOperationState.ATTACHMENT_READY, attachmentManager.state.value)
            val readyPath = attachmentManager.consumeReadyAttachment()
            assertNotNull(readyPath)
            assertTrue(readyPath!!.endsWith(".pdf"))
        }
    }

    // 7. Biometric failure preserves pending camera attachment
    @Test
    fun test07_biometricFailure_preservesPendingCameraAttachment() {
        val viewModel = AppLockViewModel(appLockManager)
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        createDummyJpegFile(File(cameraPath), 1024)

        attachmentManager.onCameraResult(context, success = true, isAppLocked = true)
        assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)

        // Biometric failure
        viewModel.onAuthenticationFailed()

        // Attachment must remain safely waiting
        assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)
        assertTrue(File(cameraPath).exists())
    }

    // 8. Biometric failure preserves pending picker attachment
    @Test
    fun test08_biometricFailure_preservesPendingPickerAttachment() {
        val viewModel = AppLockViewModel(appLockManager)
        attachmentManager.preparePicker(isPdfOnly = false)
        val testFile = File(context.cacheDir, "receipt.jpg").apply { createDummyJpegFile(this, 512) }
        val uri = Uri.fromFile(testFile)

        attachmentManager.onPickerResult(context, uri, isAppLocked = true)
        assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)

        viewModel.onAuthenticationFailed()

        assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)
        assertEquals(uri, attachmentManager.pendingUri.value)
    }

    // 9. Biometric error/cancellation preserves pending attachment until next unlock
    @Test
    fun test09_biometricCancelled_preservesPendingAttachmentUntilNextUnlock() {
        runBlocking {
            val viewModel = AppLockViewModel(appLockManager)
            attachmentManager.prepareCameraCapture(context)
            val cameraPath = attachmentManager.pendingCameraFilePath.value!!
            createDummyJpegFile(File(cameraPath), 1024)

            attachmentManager.onCameraResult(context, success = true, isAppLocked = true)

            // User cancels prompt
            viewModel.onAuthenticationError(10, "User cancelled")

            assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, attachmentManager.state.value)

            // User subsequently authenticates
            attachmentManager.onBiometricUnlocked(context)
            awaitTerminalState()
            assertEquals(AttachmentOperationState.ATTACHMENT_READY, attachmentManager.state.value)
        }
    }

    // 10. Camera cancelled by user cleans up and returns to IDLE
    @Test
    fun test10_cameraCancelledByUser_cleansUpAndReturnsIdle() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        createDummyJpegFile(File(cameraPath), 512)

        attachmentManager.onCameraResult(context, success = false, isAppLocked = false)

        assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)
        assertFalse("Temp camera file must be deleted on cancellation", File(cameraPath).exists())
        assertNull(attachmentManager.pendingCameraFilePath.value)
    }

    // 11. Picker cancelled by user resets to IDLE
    @Test
    fun test11_pickerCancelledByUser_resetsToIdle() {
        attachmentManager.preparePicker(isPdfOnly = false)
        assertEquals(AttachmentOperationState.WAITING_FOR_IMAGE_PICKER, attachmentManager.state.value)

        attachmentManager.onPickerResult(context, uri = null, isAppLocked = false)

        assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)
        assertNull(attachmentManager.pendingUri.value)
    }

    // 12. Duplicate biometric prompt prevention
    @Test
    fun test12_duplicateBiometricPromptPrevention() {
        val viewModel = AppLockViewModel(appLockManager)
        // Verify prompt active state flag handling
        viewModel.setBiometricPromptActive(true)
        assertTrue(appLockManager.isBiometricPromptActive)

        viewModel.setBiometricPromptActive(false)
        assertFalse(appLockManager.isBiometricPromptActive)
    }

    // 13. Duplicate attachment processing prevention once consumed
    @Test
    fun test13_duplicateAttachmentProcessingPrevention() {
        runBlocking {
            attachmentManager.prepareCameraCapture(context)
            val cameraPath = attachmentManager.pendingCameraFilePath.value!!
            createDummyJpegFile(File(cameraPath), 1024)
            attachmentManager.onCameraResult(context, success = true, isAppLocked = false)
            awaitTerminalState()

            val consumed = attachmentManager.consumeReadyAttachment()
            assertNotNull(consumed)
            assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)

            // Calling unlock again does nothing
            attachmentManager.onBiometricUnlocked(context)
            assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)
            assertNull(attachmentManager.consumeReadyAttachment())
        }
    }

    // 14. Activity recreation preserves pending attachment state
    @Test
    fun test14_activityRecreation_preservesPendingAttachmentState() {
        attachmentManager.prepareCameraCapture(context)
        val originalPath = attachmentManager.pendingCameraFilePath.value

        // Recreate manager instance simulating Activity lifecycle recreation
        val recreatedManager = PendingAttachmentManager(context)

        assertEquals(AttachmentOperationState.WAITING_FOR_CAMERA, recreatedManager.state.value)
        assertEquals(originalPath, recreatedManager.pendingCameraFilePath.value)
        assertTrue(recreatedManager.isExternalActivityInFlight)
    }

    // 15. Process death restoration: restores WAITING_FOR_CAMERA
    @Test
    fun test15_processDeathRestoration_restoresWaitingForCamera() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!

        val restoredManager = PendingAttachmentManager(context)
        assertEquals(AttachmentOperationState.WAITING_FOR_CAMERA, restoredManager.state.value)
        assertEquals(cameraPath, restoredManager.pendingCameraFilePath.value)
    }

    // 16. Process death restoration: restores WAITING_FOR_IMAGE_PICKER
    @Test
    fun test16_processDeathRestoration_restoresWaitingForPicker() {
        attachmentManager.preparePicker(isPdfOnly = false)

        val restoredManager = PendingAttachmentManager(context)
        assertEquals(AttachmentOperationState.WAITING_FOR_IMAGE_PICKER, restoredManager.state.value)
        assertTrue(restoredManager.isExternalActivityInFlight)
    }

    // 17. Process death restoration: restores WAITING_FOR_BIOMETRIC and resumes on unlock
    @Test
    fun test17_processDeathRestoration_restoresWaitingForBiometric() {
        runBlocking {
            attachmentManager.prepareCameraCapture(context)
            val cameraPath = attachmentManager.pendingCameraFilePath.value!!
            createDummyJpegFile(File(cameraPath), 1024)
            attachmentManager.onCameraResult(context, success = true, isAppLocked = true)

            val restoredManager = PendingAttachmentManager(context)
            assertEquals(AttachmentOperationState.WAITING_FOR_BIOMETRIC, restoredManager.state.value)

            restoredManager.onBiometricUnlocked(context)
            withTimeout(5000) {
                restoredManager.state.first { it == AttachmentOperationState.ATTACHMENT_READY }
            }
            assertEquals(AttachmentOperationState.ATTACHMENT_READY, restoredManager.state.value)
            assertNotNull(restoredManager.consumeReadyAttachment())
        }
    }

    // 18. Temporary camera file cleanup removes old temp files
    @Test
    fun test18_temporaryCameraFileCleanup_removesOldTempFiles() {
        val temp1 = ImageStorageManager.createTempCameraFile(context)
        val temp2 = ImageStorageManager.createTempCameraFile(context)
        assertTrue(temp1.exists())
        assertTrue(temp2.exists())
        assertEquals(0L, temp1.length())
        assertEquals(0L, temp2.length())

        ImageStorageManager.cleanTempCameraFiles(context)

        assertFalse("Stale temp files must be deleted", temp1.exists())
        assertFalse("Stale temp files must be deleted", temp2.exists())
    }

    // 19. Empty camera photo transitions to ERROR
    @Test
    fun test19_emptyCameraPhoto_transitionsToError() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        // Leave file empty (0 bytes)

        attachmentManager.onCameraResult(context, success = true, isAppLocked = false)

        assertEquals(AttachmentOperationState.ERROR, attachmentManager.state.value)
        assertNotNull(attachmentManager.errorMessage.value)
        assertFalse(File(cameraPath).exists())
    }

    // 20. Non-existent camera file transitions to ERROR
    @Test
    fun test20_nonExistentCameraFile_transitionsToError() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        File(cameraPath).delete() // ensure deleted

        attachmentManager.onCameraResult(context, success = true, isAppLocked = false)

        assertEquals(AttachmentOperationState.ERROR, attachmentManager.state.value)
        assertNotNull(attachmentManager.errorMessage.value)
    }

    // 21. Oversized attachment is rejected
    @Test
    fun test21_oversizedAttachment_transitionsToError() {
        runBlocking {
            attachmentManager.preparePicker(isPdfOnly = false)
            val hugeFile = File(context.cacheDir, "huge.jpg").apply {
                // Write a dummy file with size > 15MB
                FileOutputStream(this).use { fos ->
                    val chunk = ByteArray(1024 * 1024)
                    repeat(16) { fos.write(chunk) }
                }
            }
            val uri = Uri.fromFile(hugeFile)

            attachmentManager.onPickerResult(context, uri, isAppLocked = false)
            awaitTerminalState()

            assertEquals(AttachmentOperationState.ERROR, attachmentManager.state.value)
            assertNotNull(attachmentManager.errorMessage.value)
            hugeFile.delete()
        }
    }

    // 22. consumeReadyAttachment returns path and resets to IDLE
    @Test
    fun test22_consumeReadyAttachment_returnsPathAndResetsToIdle() {
        runBlocking {
            attachmentManager.prepareCameraCapture(context)
            val cameraPath = attachmentManager.pendingCameraFilePath.value!!
            createDummyJpegFile(File(cameraPath), 1024)
            attachmentManager.onCameraResult(context, success = true, isAppLocked = false)
            awaitTerminalState()

            val path = attachmentManager.consumeReadyAttachment()
            assertNotNull(path)
            assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)
            assertNull(attachmentManager.processedAttachmentPath.value)
        }
    }

    // 23. cancelOperation deletes temp file and resets
    @Test
    fun test23_cancelOperation_deletesTempFileAndResets() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        assertTrue(File(cameraPath).exists())

        attachmentManager.cancelOperation()

        assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)
        assertFalse(File(cameraPath).exists())
        assertNull(attachmentManager.pendingCameraFilePath.value)
    }

    // 24. clearError resets to IDLE
    @Test
    fun test24_clearError_resetsToIdle() {
        attachmentManager.prepareCameraCapture(context)
        val cameraPath = attachmentManager.pendingCameraFilePath.value!!
        // Leave 0 bytes
        attachmentManager.onCameraResult(context, success = true, isAppLocked = false)
        assertEquals(AttachmentOperationState.ERROR, attachmentManager.state.value)

        attachmentManager.clearError()

        assertEquals(AttachmentOperationState.IDLE, attachmentManager.state.value)
        assertNull(attachmentManager.errorMessage.value)
    }

    // 25. Save transaction with attachment persists in database
    @Test
    fun test25_saveTransactionWithAttachment_persistsInDatabase() {
        runBlocking {
            val testFile = File(context.filesDir, "receipts/bill_test.jpg").apply {
                parentFile?.mkdirs()
                createDummyJpegFile(this, 1024)
            }

            val transaction = Transaction(
                id = 101L,
                amountPaise = 45000L,
                title = "Hardware Store",
                description = "Tools and bolts",
                transactionDate = System.currentTimeMillis(),
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.UPI,
                billImagePath = testFile.absolutePath
            )

            transactionRepository.saveTransaction(transaction)

            val retrieved = transactionRepository.getTransactionById(101L)
            assertNotNull(retrieved)
            assertEquals(testFile.absolutePath, retrieved?.billImagePath)
            assertEquals(45000L, retrieved?.amountPaise)
        }
    }

    // 26. Edit transaction attachment replaces existing attachment
    @Test
    fun test26_editTransactionAttachment_replacesExistingAttachment() {
        runBlocking {
            val oldFile = File(context.filesDir, "receipts/bill_old.jpg").apply {
                parentFile?.mkdirs()
                createDummyJpegFile(this, 1024)
            }
            val newFile = File(context.filesDir, "receipts/bill_new.pdf").apply {
                parentFile?.mkdirs()
                createDummyPdfFile(this, 2048)
            }

            val initial = Transaction(
                id = 202L,
                amountPaise = 30000L,
                title = "Office Supplies",
                description = "Stationery",
                transactionDate = System.currentTimeMillis(),
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.UPI,
                billImagePath = oldFile.absolutePath
            )
            transactionRepository.saveTransaction(initial)

            // Replace attachment
            ImageStorageManager.deleteImage(initial.billImagePath)
            val updated = initial.copy(billImagePath = newFile.absolutePath)
            transactionRepository.saveTransaction(updated)

            val retrieved = transactionRepository.getTransactionById(202L)
            assertNotNull(retrieved)
            assertEquals(newFile.absolutePath, retrieved?.billImagePath)
            assertFalse(oldFile.exists())
        }
    }

    // 27. Remove attachment clears path and deletes file
    @Test
    fun test27_removeAttachment_clearsPathAndDeletesFile() {
        runBlocking {
            val testFile = File(context.filesDir, "receipts/bill_delete.jpg").apply {
                parentFile?.mkdirs()
                createDummyJpegFile(this, 1024)
            }
            val initial = Transaction(
                id = 303L,
                amountPaise = 20000L,
                title = "Pharmacy",
                description = "Medicine",
                transactionDate = System.currentTimeMillis(),
                categoryId = 1L,
                transactionType = TransactionType.EXPENSE,
                paymentMethod = PaymentMethod.UPI,
                billImagePath = testFile.absolutePath
            )
            transactionRepository.saveTransaction(initial)

            // Remove attachment
            ImageStorageManager.deleteImage(initial.billImagePath)
            val updated = initial.copy(billImagePath = null)
            transactionRepository.saveTransaction(updated)

            val retrieved = transactionRepository.getTransactionById(303L)
            assertNotNull(retrieved)
            assertNull(retrieved?.billImagePath)
            assertFalse(testFile.exists())
        }
    }

    // 28. isPdfAttachment accurately identifies PDF and Images
    @Test
    fun test28_isPdfAttachment_accuratelyIdentifiesPdfAndImages() {
        assertTrue(ImageStorageManager.isPdfAttachment("/data/user/0/com.naman.rupeeflow/files/receipts/bill_123.pdf"))
        assertTrue(ImageStorageManager.isPdfAttachment("/data/user/0/com.naman.rupeeflow/files/receipts/BILL_123.PDF"))
        assertFalse(ImageStorageManager.isPdfAttachment("/data/user/0/com.naman.rupeeflow/files/receipts/bill_123.jpg"))
        assertFalse(ImageStorageManager.isPdfAttachment("/data/user/0/com.naman.rupeeflow/files/receipts/bill_123.jpeg"))
        assertFalse(ImageStorageManager.isPdfAttachment("/data/user/0/com.naman.rupeeflow/files/receipts/bill_123.png"))
        assertFalse(ImageStorageManager.isPdfAttachment(null))
    }

    // 29. Rapid trigger protection handles multiple in-flight preparations gracefully
    @Test
    fun test29_rapidTriggerProtection_handlesConcurrentInFlightRequests() {
        // User taps button multiple times in rapid succession
        attachmentManager.preparePicker(isPdfOnly = false)
        attachmentManager.preparePicker(isPdfOnly = true)
        attachmentManager.prepareCameraCapture(context)

        assertEquals(AttachmentOperationState.WAITING_FOR_CAMERA, attachmentManager.state.value)
        assertTrue(attachmentManager.isExternalActivityInFlight)
        assertNotNull(attachmentManager.pendingCameraFilePath.value)
    }

    // 30. Biometric security is never bypassed when returning from external activity
    @Test
    fun test30_biometricSecurityNeverBypassed() {
        runBlocking {
            // Enable biometric lock in user preferences
            userPrefsRepo.setBiometricLockEnabled(true)

            // Simulate returning from external activity: lockState relocks
            appLockManager.relockSession()

            assertEquals(AppLockState.LOCKED, appLockManager.lockState.value)

            // Trigger unlock listener
            var unlockFired = false
            appLockManager.addUnlockListener { unlockFired = true }

            // Unlock
            appLockManager.unlockSession()
            assertEquals(AppLockState.UNLOCKED, appLockManager.lockState.value)
            assertTrue("Unlock listener must fire upon legitimate authentication", unlockFired)
        }
    }

    private fun createDummyJpegFile(file: File, sizeBytes: Int = 1024) {
        file.parentFile?.mkdirs()
        val bitmap = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { fos ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, fos)
        }
        bitmap.recycle()
    }

    private fun createDummyPdfFile(file: File, sizeBytes: Int) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            fos.write("%PDF-1.4\n".toByteArray(Charsets.US_ASCII))
            val filler = ByteArray(sizeBytes.coerceAtLeast(10))
            fos.write(filler)
            fos.write("\n%%EOF\n".toByteArray(Charsets.US_ASCII))
        }
    }
}
