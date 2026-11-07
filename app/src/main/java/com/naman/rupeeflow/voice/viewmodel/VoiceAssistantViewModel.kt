package com.naman.rupeeflow.voice.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.CategoryRepository
import com.naman.rupeeflow.data.repository.FriendGroupRepository
import com.naman.rupeeflow.data.repository.FriendRepository
import com.naman.rupeeflow.data.repository.SettlementRepository
import com.naman.rupeeflow.data.repository.TransactionRepository
import com.naman.rupeeflow.domain.model.Category
import com.naman.rupeeflow.domain.model.Friend
import com.naman.rupeeflow.domain.model.FriendGroup
import com.naman.rupeeflow.voice.execution.VoiceActionExecutor
import com.naman.rupeeflow.voice.execution.VoiceCommandValidator
import com.naman.rupeeflow.voice.execution.VoiceQueryHandler
import com.naman.rupeeflow.voice.model.*
import com.naman.rupeeflow.voice.nlp.VoiceCommandParser
import com.naman.rupeeflow.voice.recognition.VoiceRecognitionManager
import com.naman.rupeeflow.voice.recognition.VoiceTtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class VoiceAssistantUiState(
    val status: VoiceAssistantStatus = VoiceAssistantStatus.IDLE,
    val selectedLanguage: VoiceLanguage = VoiceLanguage.ENGLISH,
    val availability: SpeechRecognitionAvailability = SpeechRecognitionAvailability.AVAILABLE_OFFLINE,
    val transcription: String = "",
    val partialTranscription: String = "",
    val rmsLevel: Float = 0f,
    val currentPreview: VoiceActionPreview? = null,
    val pendingCommand: VoiceCommand? = null,
    val clarificationPrompt: VoiceClarificationPrompt? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val isTtsSpeaking: Boolean = false,
    val isTtsEnabled: Boolean = true,
    val isManualInputMode: Boolean = false
)

class VoiceAssistantViewModel(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val friendRepository: FriendRepository,
    private val settlementRepository: SettlementRepository,
    private val categoryRepository: CategoryRepository,
    private val friendGroupRepository: FriendGroupRepository,
    recognitionManagerOverride: VoiceRecognitionManager? = null,
    ttsManagerOverride: VoiceTtsManager? = null
) : ViewModel() {

    private val recognitionManager = recognitionManagerOverride ?: VoiceRecognitionManager(context)
    private val ttsManager = ttsManagerOverride ?: VoiceTtsManager(context)
    private val actionExecutor = VoiceActionExecutor(transactionRepository, settlementRepository)

    private val _uiState = MutableStateFlow(VoiceAssistantUiState())
    val uiState: StateFlow<VoiceAssistantUiState> = _uiState.asStateFlow()

    private var cachedFriends: List<Friend> = emptyList()
    private var cachedGroups: List<FriendGroup> = emptyList()
    private var cachedCategories: List<Category> = emptyList()

    init {
        refreshAvailability()
        observeRecognition()
        observeTts()
        loadLocalEntities()
    }

    private fun loadLocalEntities() {
        viewModelScope.launch {
            try {
                cachedFriends = friendRepository.getAllFriendsList()
                cachedGroups = friendGroupRepository.getAllGroupsList()
                cachedCategories = categoryRepository.getAllCategoriesList()
            } catch (_: Exception) {}
        }
    }

    private fun observeRecognition() {
        viewModelScope.launch {
            recognitionManager.recognitionState.collect { state ->
                when (state) {
                    is VoiceRecognitionManager.RecognitionState.Idle -> {
                        if (_uiState.value.status == VoiceAssistantStatus.LISTENING) {
                            _uiState.update { it.copy(status = VoiceAssistantStatus.IDLE) }
                        }
                    }
                    is VoiceRecognitionManager.RecognitionState.Ready -> {
                        _uiState.update { it.copy(status = VoiceAssistantStatus.LISTENING, partialTranscription = "") }
                    }
                    is VoiceRecognitionManager.RecognitionState.Listening -> {
                        _uiState.update { it.copy(status = VoiceAssistantStatus.LISTENING) }
                    }
                    is VoiceRecognitionManager.RecognitionState.PartialResult -> {
                        _uiState.update { it.copy(partialTranscription = state.text) }
                    }
                    is VoiceRecognitionManager.RecognitionState.FinalResult -> {
                        _uiState.update {
                            it.copy(
                                transcription = state.text,
                                partialTranscription = "",
                                status = VoiceAssistantStatus.PROCESSING
                            )
                        }
                        processInput(state.text)
                    }
                    is VoiceRecognitionManager.RecognitionState.Error -> {
                        _uiState.update {
                            it.copy(
                                status = VoiceAssistantStatus.ERROR,
                                errorMessage = state.message,
                                availability = state.availability
                            )
                        }
                        if (_uiState.value.isTtsEnabled) {
                            ttsManager.speak(state.message, _uiState.value.selectedLanguage)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            recognitionManager.rmsLevel.collect { level ->
                _uiState.update { it.copy(rmsLevel = level) }
            }
        }
    }

    private fun observeTts() {
        viewModelScope.launch {
            ttsManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isTtsSpeaking = speaking) }
            }
        }
    }

    fun selectLanguage(language: VoiceLanguage) {
        _uiState.update { it.copy(selectedLanguage = language) }
        refreshAvailability()
    }

    fun refreshAvailability() {
        val avail = recognitionManager.checkAvailability(_uiState.value.selectedLanguage)
        _uiState.update { it.copy(availability = avail) }
    }

    fun toggleTts() {
        val newEnabled = !_uiState.value.isTtsEnabled
        if (!newEnabled) {
            ttsManager.stop()
        }
        _uiState.update { it.copy(isTtsEnabled = newEnabled) }
    }

    fun toggleManualInput() {
        val newMode = !_uiState.value.isManualInputMode
        if (newMode) {
            recognitionManager.stopListening()
        }
        _uiState.update { it.copy(isManualInputMode = newMode) }
    }

    fun startListening() {
        ttsManager.stop()
        refreshAvailability()
        _uiState.update {
            it.copy(
                errorMessage = null,
                successMessage = null,
                partialTranscription = "",
                status = VoiceAssistantStatus.LISTENING
            )
        }
        recognitionManager.startListening(_uiState.value.selectedLanguage)
    }

    fun stopListening() {
        recognitionManager.stopListening()
    }

    fun updateTranscriptionInput(newText: String) {
        _uiState.update { it.copy(transcription = newText) }
    }

    fun submitManualText(text: String) {
        if (text.isBlank()) return
        ttsManager.stop()
        _uiState.update {
            it.copy(
                transcription = text.trim(),
                status = VoiceAssistantStatus.PROCESSING,
                errorMessage = null,
                successMessage = null
            )
        }
        processInput(text.trim())
    }

    private fun processInput(rawText: String) {
        viewModelScope.launch {
            // Ensure local entity caches are current
            cachedFriends = friendRepository.getAllFriendsList()
            cachedGroups = friendGroupRepository.getAllGroupsList()
            cachedCategories = categoryRepository.getAllCategoriesList()

            // 1. Check if user is answering a pending clarification
            val pending = _uiState.value.pendingCommand
            val clarification = _uiState.value.clarificationPrompt

            if (pending != null && clarification != null) {
                handleClarificationResponse(rawText, pending, clarification)
                return@launch
            }

            // 2. Parse command from text
            val command = VoiceCommandParser.parse(
                text = rawText,
                friends = cachedFriends,
                groups = cachedGroups,
                categories = cachedCategories,
                language = _uiState.value.selectedLanguage
            )

            // 3. Handle Confirm / Cancel shortcuts directly
            when (command) {
                is VoiceCommand.Confirm -> {
                    confirmPendingAction()
                    return@launch
                }
                is VoiceCommand.Cancel -> {
                    cancelCurrentSession()
                    return@launch
                }
                is VoiceCommand.Help -> {
                    val helpText = "You can say:\n• \"Add 300 for dinner with Jay\"\n• \"Jay paid 500 for lunch\"\n• \"How much does Jay owe me?\"\n• \"Total spent this month\"\n• \"Settle 450 with Priya\""
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.SUCCESS,
                            successMessage = helpText
                        )
                    }
                    if (_uiState.value.isTtsEnabled) {
                        ttsManager.speak("You can create transactions, settle balances, or ask financial questions.", _uiState.value.selectedLanguage)
                    }
                    return@launch
                }
                else -> { /* proceed to validation and query handling */ }
            }

            // 4. Handle Read Queries
            if (command is VoiceCommand.Query) {
                val transactions = transactionRepository.getAllTransactionsList()
                val settlements = settlementRepository.getAllSettlementsList()
                val preview = VoiceQueryHandler.executeQuery(
                    query = command,
                    transactions = transactions,
                    friends = cachedFriends,
                    settlements = settlements,
                    groups = cachedGroups,
                    categories = cachedCategories,
                    language = _uiState.value.selectedLanguage
                )
                _uiState.update {
                    it.copy(
                        status = VoiceAssistantStatus.SUCCESS,
                        currentPreview = preview,
                        successMessage = preview.formattedAnswer
                    )
                }
                if (_uiState.value.isTtsEnabled) {
                    ttsManager.speak(preview.spokenAnswer, _uiState.value.selectedLanguage)
                }
                return@launch
            }

            // 5. Validate Mutation Commands (Create, Edit, Delete, Settle)
            val validationResult = VoiceCommandValidator.validateAndBuildPreview(
                command = command,
                friends = cachedFriends,
                groups = cachedGroups,
                categories = cachedCategories,
                language = _uiState.value.selectedLanguage
            )

            when (validationResult) {
                is VoiceCommandResult.PreviewReady -> {
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.PREVIEW,
                            currentPreview = validationResult.preview,
                            pendingCommand = validationResult.pendingCommand,
                            clarificationPrompt = null
                        )
                    }
                    if (_uiState.value.isTtsEnabled) {
                        ttsManager.speak(validationResult.spokenPrompt, _uiState.value.selectedLanguage)
                    }
                }
                is VoiceCommandResult.ClarificationRequired -> {
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.NEEDS_CLARIFICATION,
                            clarificationPrompt = validationResult.prompt,
                            pendingCommand = validationResult.partialCommand
                        )
                    }
                    if (_uiState.value.isTtsEnabled) {
                        ttsManager.speak(validationResult.prompt.spokenQuestion, _uiState.value.selectedLanguage)
                    }
                }
                is VoiceCommandResult.Error -> {
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.ERROR,
                            errorMessage = validationResult.errorMessage
                        )
                    }
                    if (_uiState.value.isTtsEnabled) {
                        ttsManager.speak(validationResult.spokenErrorMessage, _uiState.value.selectedLanguage)
                    }
                }
                is VoiceCommandResult.Cancelled -> {
                    cancelCurrentSession()
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.ERROR,
                            errorMessage = "Unexpected action result."
                        )
                    }
                }
            }
        }
    }

    private fun handleClarificationResponse(
        answerText: String,
        pending: VoiceCommand,
        prompt: VoiceClarificationPrompt
    ) {
        val mergedCommand = when (pending) {
            is VoiceCommand.CreateTransaction -> {
                when (prompt.type) {
                    VoiceClarificationType.MISSING_AMOUNT -> {
                        val parsedAmt = com.naman.rupeeflow.voice.nlp.VoiceNumberParser.parseAmountPaise(answerText)
                        if (parsedAmt != null && parsedAmt > 0L) {
                            pending.copy(amountPaise = parsedAmt)
                        } else {
                            null
                        }
                    }
                    VoiceClarificationType.AMBIGUOUS_FRIEND -> {
                        val matched = cachedFriends.firstOrNull { it.name.contains(answerText.trim(), ignoreCase = true) }
                        if (matched != null) {
                            pending.copy(participantFriendIds = (pending.participantFriendIds + matched.id).distinct())
                        } else {
                            null
                        }
                    }
                    else -> null
                }
            }
            else -> null
        }

        if (mergedCommand != null) {
            _uiState.update { it.copy(clarificationPrompt = null, pendingCommand = mergedCommand) }
            val revalidation = VoiceCommandValidator.validateAndBuildPreview(
                command = mergedCommand,
                friends = cachedFriends,
                groups = cachedGroups,
                categories = cachedCategories,
                language = _uiState.value.selectedLanguage
            )
            if (revalidation is VoiceCommandResult.PreviewReady) {
                _uiState.update {
                    it.copy(
                        status = VoiceAssistantStatus.PREVIEW,
                        currentPreview = revalidation.preview,
                        pendingCommand = revalidation.pendingCommand
                    )
                }
                if (_uiState.value.isTtsEnabled) {
                    ttsManager.speak(revalidation.spokenPrompt, _uiState.value.selectedLanguage)
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    errorMessage = "Could not apply answer. Please try again."
                )
            }
        }
    }

    fun selectClarificationOption(option: String) {
        val pending = _uiState.value.pendingCommand ?: return
        val prompt = _uiState.value.clarificationPrompt ?: return
        handleClarificationResponse(option, pending, prompt)
    }

    fun selectClarificationFriend(friend: Friend) {
        val pending = _uiState.value.pendingCommand ?: return
        val prompt = _uiState.value.clarificationPrompt ?: return
        handleClarificationResponse(friend.name, pending, prompt)
    }

    fun confirmPendingAction() {
        val preview = _uiState.value.currentPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(status = VoiceAssistantStatus.EXECUTING) }
            val executionToken = UUID.randomUUID().toString()
            val result = actionExecutor.executePreview(preview, _uiState.value.selectedLanguage, executionToken)
            when (result) {
                is VoiceCommandResult.Success -> {
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.SUCCESS,
                            successMessage = result.message,
                            currentPreview = null,
                            pendingCommand = null
                        )
                    }
                    if (_uiState.value.isTtsEnabled) {
                        ttsManager.speak(result.spokenMessage, _uiState.value.selectedLanguage)
                    }
                }
                is VoiceCommandResult.Error -> {
                    _uiState.update {
                        it.copy(
                            status = VoiceAssistantStatus.ERROR,
                            errorMessage = result.errorMessage
                        )
                    }
                    if (_uiState.value.isTtsEnabled) {
                        ttsManager.speak(result.spokenErrorMessage, _uiState.value.selectedLanguage)
                    }
                }
                else -> {}
            }
        }
    }

    fun cancelCurrentSession() {
        ttsManager.stop()
        recognitionManager.stopListening()
        _uiState.update {
            it.copy(
                status = VoiceAssistantStatus.IDLE,
                currentPreview = null,
                pendingCommand = null,
                clarificationPrompt = null,
                transcription = "",
                partialTranscription = "",
                errorMessage = null,
                successMessage = null
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        recognitionManager.cancel()
        ttsManager.shutdown()
    }
}
