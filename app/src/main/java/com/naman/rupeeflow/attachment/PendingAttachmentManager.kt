package com.naman.rupeeflow.attachment

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.naman.rupeeflow.data.local.ImageStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State machine representing pending receipt/bill attachment lifecycle.
 * Ensures attachments survive camera, file picker, biometric lock,
 * Activity recreation, and process death.
 */
enum class AttachmentOperationState {
    IDLE,
    WAITING_FOR_CAMERA,
    WAITING_FOR_IMAGE_PICKER,
    WAITING_FOR_PDF_PICKER,
    ATTACHMENT_RECEIVED,
    WAITING_FOR_BIOMETRIC,
    PROCESSING_ATTACHMENT,
    ATTACHMENT_READY,
    CANCELLED,
    ERROR
}

/**
 * Lifecycle-aware manager coordinating attachment capture and biometric authentication.
 * Backed by SharedPreferences to guarantee persistence across process recreation.
 */
class PendingAttachmentManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _state = MutableStateFlow(AttachmentOperationState.IDLE)
    val state: StateFlow<AttachmentOperationState> = _state.asStateFlow()

    private val _pendingCameraFilePath = MutableStateFlow<String?>(null)
    val pendingCameraFilePath: StateFlow<String?> = _pendingCameraFilePath.asStateFlow()

    private val _pendingUri = MutableStateFlow<Uri?>(null)
    val pendingUri: StateFlow<Uri?> = _pendingUri.asStateFlow()

    private val _processedAttachmentPath = MutableStateFlow<String?>(null)
    val processedAttachmentPath: StateFlow<String?> = _processedAttachmentPath.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    @Volatile
    var isExternalActivityInFlight: Boolean = false
        private set

    init {
        restoreStateFromPreferences()
    }

    // ✅ Restore pending state after activity recreation or process restart
    private fun restoreStateFromPreferences() {
        val savedStateStr = prefs.getString(KEY_OPERATION_STATE, AttachmentOperationState.IDLE.name)
        val savedState = runCatching {
            AttachmentOperationState.valueOf(savedStateStr ?: AttachmentOperationState.IDLE.name)
        }.getOrDefault(AttachmentOperationState.IDLE)

        val savedCameraPath = prefs.getString(KEY_CAMERA_FILE_PATH, null)
        val savedUriStr = prefs.getString(KEY_PENDING_URI, null)
        val savedProcessedPath = prefs.getString(KEY_PROCESSED_PATH, null)
        val savedError = prefs.getString(KEY_ERROR_MESSAGE, null)
        isExternalActivityInFlight = prefs.getBoolean(KEY_EXTERNAL_IN_FLIGHT, false)

        _pendingCameraFilePath.value = savedCameraPath
        _pendingUri.value = savedUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
        _processedAttachmentPath.value = savedProcessedPath
        _errorMessage.value = savedError
        _state.value = savedState

        Log.d(TAG, "Restored state: state=$savedState, cameraPath=$savedCameraPath, uri=$savedUriStr, inFlight=$isExternalActivityInFlight")
    }

    private fun persistState(
        operationState: AttachmentOperationState,
        cameraPath: String? = _pendingCameraFilePath.value,
        uri: Uri? = _pendingUri.value,
        processedPath: String? = _processedAttachmentPath.value,
        error: String? = _errorMessage.value,
        inFlight: Boolean = isExternalActivityInFlight
    ) {
        // Set payload fields before updating state so observers receive complete state atomically
        _pendingCameraFilePath.value = cameraPath
        _pendingUri.value = uri
        _processedAttachmentPath.value = processedPath
        _errorMessage.value = error
        isExternalActivityInFlight = inFlight
        _state.value = operationState

        prefs.edit()
            .putString(KEY_OPERATION_STATE, operationState.name)
            .putString(KEY_CAMERA_FILE_PATH, cameraPath)
            .putString(KEY_PENDING_URI, uri?.toString())
            .putString(KEY_PROCESSED_PATH, processedPath)
            .putString(KEY_ERROR_MESSAGE, error)
            .putBoolean(KEY_EXTERNAL_IN_FLIGHT, inFlight)
            .apply()
    }

    // ✅ Prepare destination file and URI for camera capture before launching intent
    fun prepareCameraCapture(appContext: Context = context): Uri {
        ImageStorageManager.cleanTempCameraFiles(appContext)
        val tempFile = ImageStorageManager.createTempCameraFile(appContext)
        val contentUri = runCatching {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                tempFile
            )
        }.getOrElse {
            Uri.fromFile(tempFile)
        }

        persistState(
            operationState = AttachmentOperationState.WAITING_FOR_CAMERA,
            cameraPath = tempFile.absolutePath,
            uri = contentUri,
            processedPath = null,
            error = null,
            inFlight = true
        )

        Log.d(TAG, "prepareCameraCapture: tempFile=${tempFile.absolutePath}, uri=$contentUri")
        return contentUri
    }

    // ✅ Handle camera result with validation of captured content
    fun onCameraResult(appContext: Context = context, success: Boolean, isAppLocked: Boolean) {
        Log.d(TAG, "onCameraResult: success=$success, isAppLocked=$isAppLocked")
        isExternalActivityInFlight = false
        prefs.edit().putBoolean(KEY_EXTERNAL_IN_FLIGHT, false).apply()

        val tempPath = _pendingCameraFilePath.value
        if (!success || tempPath.isNullOrBlank()) {
            // Camera was cancelled or returned empty
            if (!tempPath.isNullOrBlank()) {
                File(tempPath).delete()
            }
            persistState(
                operationState = AttachmentOperationState.CANCELLED,
                cameraPath = null,
                uri = null,
                inFlight = false
            )
            // Immediately transition to IDLE
            _state.value = AttachmentOperationState.IDLE
            prefs.edit().putString(KEY_OPERATION_STATE, AttachmentOperationState.IDLE.name).apply()
            return
        }

        val tempFile = File(tempPath)
        if (!tempFile.exists() || tempFile.length() <= 0L) {
            tempFile.delete()
            persistState(
                operationState = AttachmentOperationState.ERROR,
                cameraPath = null,
                uri = null,
                error = "Captured photo was empty or could not be saved.",
                inFlight = false
            )
            return
        }

        if (isAppLocked) {
            Log.d(TAG, "Camera capture preserved safely, waiting for biometric unlock")
            persistState(
                operationState = AttachmentOperationState.WAITING_FOR_BIOMETRIC,
                cameraPath = tempPath,
                inFlight = false
            )
        } else {
            finalizeCameraPhoto(appContext, tempPath)
        }
    }

    // ✅ Prepare state for document / image / PDF picker launch
    fun preparePicker(isPdfOnly: Boolean) {
        val targetState = if (isPdfOnly) {
            AttachmentOperationState.WAITING_FOR_PDF_PICKER
        } else {
            AttachmentOperationState.WAITING_FOR_IMAGE_PICKER
        }

        persistState(
            operationState = targetState,
            cameraPath = null,
            uri = null,
            processedPath = null,
            error = null,
            inFlight = true
        )
        Log.d(TAG, "preparePicker: state=$targetState")
    }

    // ✅ Handle result from system document/image picker
    fun onPickerResult(appContext: Context = context, uri: Uri?, isAppLocked: Boolean) {
        Log.d(TAG, "onPickerResult: uri=$uri, isAppLocked=$isAppLocked")
        isExternalActivityInFlight = false
        prefs.edit().putBoolean(KEY_EXTERNAL_IN_FLIGHT, false).apply()

        if (uri == null) {
            persistState(
                operationState = AttachmentOperationState.CANCELLED,
                cameraPath = null,
                uri = null,
                inFlight = false
            )
            _state.value = AttachmentOperationState.IDLE
            prefs.edit().putString(KEY_OPERATION_STATE, AttachmentOperationState.IDLE.name).apply()
            return
        }

        // Try to persist read permission when offered by Storage Access Framework
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        if (isAppLocked) {
            Log.d(TAG, "Picker URI preserved safely, waiting for biometric unlock")
            persistState(
                operationState = AttachmentOperationState.WAITING_FOR_BIOMETRIC,
                uri = uri,
                inFlight = false
            )
        } else {
            processPickerDocument(appContext, uri)
        }
    }

    // ✅ Resume pending attachment processing automatically when biometric unlocks
    fun onBiometricUnlocked(appContext: Context = context) {
        Log.d(TAG, "onBiometricUnlocked: current state=${_state.value}")
        val currentState = _state.value
        if (currentState != AttachmentOperationState.WAITING_FOR_BIOMETRIC &&
            currentState != AttachmentOperationState.ATTACHMENT_RECEIVED
        ) {
            return
        }

        val cameraPath = _pendingCameraFilePath.value
        val uri = _pendingUri.value

        when {
            !cameraPath.isNullOrBlank() -> {
                finalizeCameraPhoto(appContext, cameraPath)
            }
            uri != null -> {
                processPickerDocument(appContext, uri)
            }
            else -> {
                persistState(AttachmentOperationState.IDLE)
            }
        }
    }

    private fun finalizeCameraPhoto(appContext: Context, tempPath: String) {
        persistState(
            operationState = AttachmentOperationState.PROCESSING_ATTACHMENT,
            cameraPath = tempPath
        )

        scope.launch {
            val finalPath = ImageStorageManager.finalizeCameraCapture(appContext, tempPath)
            if (finalPath != null) {
                Log.d(TAG, "Camera photo finalized successfully: $finalPath")
                persistState(
                    operationState = AttachmentOperationState.ATTACHMENT_READY,
                    cameraPath = null,
                    processedPath = finalPath
                )
            } else {
                Log.e(TAG, "Failed to finalize camera photo at $tempPath")
                persistState(
                    operationState = AttachmentOperationState.ERROR,
                    cameraPath = null,
                    error = "Failed to process captured camera photo."
                )
            }
        }
    }

    private fun processPickerDocument(appContext: Context, uri: Uri) {
        persistState(
            operationState = AttachmentOperationState.PROCESSING_ATTACHMENT,
            uri = uri
        )

        scope.launch {
            val savedPath = ImageStorageManager.saveBillAttachment(appContext, uri)
            if (savedPath != null) {
                Log.d(TAG, "Picker document processed successfully: $savedPath")
                persistState(
                    operationState = AttachmentOperationState.ATTACHMENT_READY,
                    uri = null,
                    processedPath = savedPath
                )
            } else {
                Log.e(TAG, "Failed to process picker document from $uri")
                persistState(
                    operationState = AttachmentOperationState.ERROR,
                    uri = null,
                    error = "Could not process selected attachment. File may exceed 15MB or format is unsupported."
                )
            }
        }
    }

    // ✅ Consume ready attachment path and reset state back to IDLE
    fun consumeReadyAttachment(): String? {
        val path = _processedAttachmentPath.value
        Log.d(TAG, "consumeReadyAttachment: path=$path")
        persistState(
            operationState = AttachmentOperationState.IDLE,
            cameraPath = null,
            uri = null,
            processedPath = null,
            error = null,
            inFlight = false
        )
        return path
    }

    // ✅ Explicit user cancellation
    fun cancelOperation() {
        val tempPath = _pendingCameraFilePath.value
        if (!tempPath.isNullOrBlank()) {
            File(tempPath).delete()
        }
        persistState(
            operationState = AttachmentOperationState.IDLE,
            cameraPath = null,
            uri = null,
            processedPath = null,
            error = null,
            inFlight = false
        )
    }

    // ✅ Dismiss current error
    fun clearError() {
        _errorMessage.value = null
        _state.value = AttachmentOperationState.IDLE
        prefs.edit()
            .putString(KEY_OPERATION_STATE, AttachmentOperationState.IDLE.name)
            .remove(KEY_ERROR_MESSAGE)
            .apply()
    }

    companion object {
        private const val TAG = "PendingAttachmentMgr"
        private const val PREFS_NAME = "rupeeflow_attachment_state"
        private const val KEY_OPERATION_STATE = "key_operation_state"
        private const val KEY_CAMERA_FILE_PATH = "key_camera_file_path"
        private const val KEY_PENDING_URI = "key_pending_uri"
        private const val KEY_PROCESSED_PATH = "key_processed_path"
        private const val KEY_ERROR_MESSAGE = "key_error_message"
        private const val KEY_EXTERNAL_IN_FLIGHT = "key_external_in_flight"

        @Volatile
        private var instance: PendingAttachmentManager? = null

        fun getInstance(context: Context): PendingAttachmentManager {
            return instance ?: synchronized(this) {
                instance ?: PendingAttachmentManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}

