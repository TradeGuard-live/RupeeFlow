package com.naman.rupeeflow.voice.recognition

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.naman.rupeeflow.voice.model.SpeechRecognitionAvailability
import com.naman.rupeeflow.voice.model.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages strictly offline, on-device Android SpeechRecognizer sessions.
 * Never performs network requests or transmits voice buffers.
 */
class VoiceRecognitionManager(private val context: Context) {

    sealed class RecognitionState {
        data object Idle : RecognitionState()
        data object Ready : RecognitionState()
        data object Listening : RecognitionState()
        data class PartialResult(val text: String) : RecognitionState()
        data class FinalResult(val text: String) : RecognitionState()
        data class Error(val code: Int, val message: String, val availability: SpeechRecognitionAvailability) : RecognitionState()
    }

    private val _recognitionState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val recognitionState: StateFlow<RecognitionState> = _recognitionState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isCurrentlyListening = false

    /**
     * Checks whether offline/on-device speech recognition is available on this device
     * for the selected language.
     */
    fun checkAvailability(language: VoiceLanguage): SpeechRecognitionAvailability {
        // 1. Check microphone permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return SpeechRecognitionAvailability.PERMISSION_REQUIRED
        }

        // 2. Check Android SpeechRecognizer service availability
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            return SpeechRecognitionAvailability.SPEECH_RECOGNIZER_UNAVAILABLE
        }

        // 3. Check on-device availability if supported by Android platform
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val isOnDeviceSupported = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            if (!isOnDeviceSupported) {
                // Device does not have on-device speech recognition service installed or supported
                return SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED
            }
        }

        return SpeechRecognitionAvailability.AVAILABLE_OFFLINE
    }

    /**
     * Starts listening for voice input in the specified language offline.
     */
    fun startListening(language: VoiceLanguage) {
        stopListening()

        val availability = checkAvailability(language)
        if (availability != SpeechRecognitionAvailability.AVAILABLE_OFFLINE) {
            _recognitionState.value = RecognitionState.Error(
                code = -1,
                message = when (availability) {
                    SpeechRecognitionAvailability.PERMISSION_REQUIRED -> "Microphone permission required."
                    SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED ->
                        "Offline voice pack for ${language.displayName} is not installed on this device. Please use typed input or download the offline voice model in Android Settings."
                    SpeechRecognitionAvailability.SPEECH_RECOGNIZER_UNAVAILABLE ->
                        "Offline speech recognition is unavailable on this device. Please use text input."
                    else -> "Speech recognition unavailable."
                },
                availability = availability
            )
            return
        }

        try {
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(createListener(language))

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.code)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            isCurrentlyListening = true
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            isCurrentlyListening = false
            _recognitionState.value = RecognitionState.Error(
                code = -2,
                message = "Failed to start speech recognition: ${e.localizedMessage ?: "Unknown error"}",
                availability = SpeechRecognitionAvailability.SPEECH_RECOGNIZER_UNAVAILABLE
            )
        }
    }

    fun stopListening() {
        if (isCurrentlyListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {}
            isCurrentlyListening = false
        }
        _rmsLevel.value = 0f
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isCurrentlyListening = false
        _rmsLevel.value = 0f
        _recognitionState.value = RecognitionState.Idle
    }

    private fun createListener(language: VoiceLanguage) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _recognitionState.value = RecognitionState.Ready
        }

        override fun onBeginningOfSpeech() {
            _recognitionState.value = RecognitionState.Listening
        }

        override fun onRmsChanged(rmsdB: Float) {
            // Map rmsdB (-2 to 10 typical) to normalized 0f..1f for UI waveform
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _rmsLevel.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Do not store, persist, or log audio buffer bytes
        }

        override fun onEndOfSpeech() {
            _rmsLevel.value = 0f
        }

        override fun onError(error: Int) {
            isCurrentlyListening = false
            _rmsLevel.value = 0f

            val (msg, avail) = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error." to SpeechRecognitionAvailability.AVAILABLE_OFFLINE
                SpeechRecognizer.ERROR_CLIENT -> "Client error." to SpeechRecognitionAvailability.AVAILABLE_OFFLINE
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    "Microphone permission required." to SpeechRecognitionAvailability.PERMISSION_REQUIRED
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                    "Offline recognition requires downloaded language pack for ${language.displayName}." to
                            SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED
                SpeechRecognizer.ERROR_NO_MATCH ->
                    "No speech recognized. Please try again." to SpeechRecognitionAvailability.AVAILABLE_OFFLINE
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Recognition service is busy. Please try again." to SpeechRecognitionAvailability.AVAILABLE_OFFLINE
                SpeechRecognizer.ERROR_SERVER ->
                    "Offline voice model for ${language.displayName} not ready. Please use text input or check Android offline speech settings." to
                            SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    "No speech detected." to SpeechRecognitionAvailability.AVAILABLE_OFFLINE
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
                    "Language ${language.displayName} is not supported offline on this device." to
                            SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED
                SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT ->
                    "Offline language check unavailable on this device." to
                            SpeechRecognitionAvailability.LANGUAGE_PACK_DOWNLOAD_REQUIRED
                else -> "Recognition error (code $error)." to SpeechRecognitionAvailability.AVAILABLE_OFFLINE
            }

            _recognitionState.value = RecognitionState.Error(error, msg, avail)
        }

        override fun onResults(results: Bundle?) {
            isCurrentlyListening = false
            _rmsLevel.value = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val topMatch = matches?.firstOrNull()?.trim() ?: ""
            if (topMatch.isNotBlank()) {
                _recognitionState.value = RecognitionState.FinalResult(topMatch)
            } else {
                _recognitionState.value = RecognitionState.Error(
                    SpeechRecognizer.ERROR_NO_MATCH,
                    "No speech recognized.",
                    SpeechRecognitionAvailability.AVAILABLE_OFFLINE
                )
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = partials?.firstOrNull()?.trim() ?: ""
            if (partialText.isNotBlank()) {
                _recognitionState.value = RecognitionState.PartialResult(partialText)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
