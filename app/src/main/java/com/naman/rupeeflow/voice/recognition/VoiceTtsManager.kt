package com.naman.rupeeflow.voice.recognition

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.naman.rupeeflow.voice.model.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * Manages strictly offline Android TextToSpeech playback.
 * Checks for offline-capable voices, preventing network lookups.
 * Safe fallback to silent operation if offline language voice is unavailable.
 */
class VoiceTtsManager(
    private val context: Context,
    private val onSpeechCompleted: (() -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (_: Exception) {
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onSpeechCompleted?.invoke()
                }

                @Deprecated("Deprecated in Java", ReplaceWith("onError(utteranceId, -1)"))
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                }
            })
        } else {
            isInitialized = false
        }
    }

    /**
     * Speaks the given text using an offline voice corresponding to [language].
     * If the language voice is not available offline, does not throw or block;
     * visual response is always available in the UI.
     */
    fun speak(text: String, language: VoiceLanguage) {
        if (!isInitialized || tts == null || text.isBlank()) {
            return
        }

        val targetLocale = when (language) {
            VoiceLanguage.ENGLISH -> Locale.Builder().setLanguage("en").setRegion("IN").build()
            VoiceLanguage.HINDI -> Locale.Builder().setLanguage("hi").setRegion("IN").build()
            VoiceLanguage.GUJARATI -> Locale.Builder().setLanguage("gu").setRegion("IN").build()
            VoiceLanguage.HINGLISH -> Locale.Builder().setLanguage("en").setRegion("IN").build()
        }

        try {
            val availability = tts?.isLanguageAvailable(targetLocale)
            if (availability == TextToSpeech.LANG_AVAILABLE ||
                availability == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                availability == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            ) {
                tts?.language = targetLocale

                // Ensure selected voice doesn't require network connection
                tts?.voices?.firstOrNull { voice ->
                    voice.locale.language == targetLocale.language &&
                            !voice.isNetworkConnectionRequired
                }?.let { offlineVoice ->
                    tts?.voice = offlineVoice
                }

                val utteranceId = UUID.randomUUID().toString()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        } catch (_: Exception) {
            _isSpeaking.value = false
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isInitialized = false
        _isSpeaking.value = false
    }
}
