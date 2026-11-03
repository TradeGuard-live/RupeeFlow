package com.naman.rupeeflow.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naman.rupeeflow.data.repository.*
import com.naman.rupeeflow.data.sms.ParsedSmsResult
import com.naman.rupeeflow.data.sms.SmsParser
import com.naman.rupeeflow.domain.model.Category
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: String = "DARK",
    val biometricLockEnabled: Boolean = false,
    val smsDetectionEnabled: Boolean = true,
    val notificationListenerEnabled: Boolean = false,
    val categories: List<Category> = emptyList(),
    val exportStatusMessage: String? = null,
    val testSmsResult: ParsedSmsResult? = null
)

class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val backupRestoreRepository: BackupRestoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            combine(
                userPreferencesRepository.userPreferencesFlow,
                categoryRepository.getAllCategories()
            ) { prefs, categories ->
                SettingsUiState(
                    themeMode = prefs.themeMode,
                    biometricLockEnabled = prefs.biometricLockEnabled,
                    smsDetectionEnabled = prefs.smsDetectionEnabled,
                    notificationListenerEnabled = prefs.notificationListenerEnabled,
                    categories = categories
                )
            }.collect { state ->
                _uiState.update { current ->
                    state.copy(
                        exportStatusMessage = current.exportStatusMessage,
                        testSmsResult = current.testSmsResult
                    )
                }
            }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBiometricLockEnabled(enabled)
        }
    }

    fun setSmsDetectionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setSmsDetectionEnabled(enabled)
        }
    }

    fun setNotificationListenerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNotificationListenerEnabled(enabled)
        }
    }

    fun addCategory(name: String, iconName: String, colorHex: String) {
        viewModelScope.launch {
            val cat = Category(name = name, iconName = iconName, colorHex = colorHex)
            categoryRepository.addCategory(cat)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepository.deleteCategory(category)
        }
    }

    fun testParseSms(smsText: String, sender: String? = null) {
        val result = SmsParser.parse(smsText, sender)
        _uiState.update { it.copy(testSmsResult = result) }
    }

    fun clearTestSmsResult() {
        _uiState.update { it.copy(testSmsResult = null) }
    }

    fun exportBackup(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = backupRestoreRepository.exportToJson()
            _uiState.update { it.copy(exportStatusMessage = "Backup exported successfully (${json.length} bytes)") }
            onExportReady(json)
        }
    }

    // ✅ Password-protected AES-256 encrypted export
    fun exportEncryptedBackup(password: String, onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val encrypted = backupRestoreRepository.exportEncryptedBackup(password)
                _uiState.update { it.copy(exportStatusMessage = "Encrypted backup exported securely!") }
                onExportReady(encrypted)
            } catch (e: Exception) {
                _uiState.update { it.copy(exportStatusMessage = "Export failed: ${e.message}") }
            }
        }
    }

    // ✅ Inspect backup before restoration
    fun inspectBackup(content: String, password: String? = null): BackupPreview {
        return backupRestoreRepository.inspectBackup(content, password)
    }

    fun restoreBackup(jsonString: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = backupRestoreRepository.restoreFromJson(jsonString)
            _uiState.update {
                it.copy(
                    exportStatusMessage = if (success) "Backup restored successfully!" else "Failed to restore backup. Invalid file format."
                )
            }
            onComplete(success)
        }
    }

    // ✅ Restore encrypted backup
    fun restoreEncryptedBackup(content: String, password: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val decrypted = backupRestoreRepository.decryptBackup(content, password)
                val success = backupRestoreRepository.restoreFromJson(decrypted)
                _uiState.update {
                    it.copy(
                        exportStatusMessage = if (success) "Encrypted backup verified & restored!" else "Failed to restore backup."
                    )
                }
                onComplete(success)
            } catch (e: Exception) {
                _uiState.update { it.copy(exportStatusMessage = "Decryption failed: Check password") }
                onComplete(false)
            }
        }
    }

    fun exportTransactionsCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = backupRestoreRepository.exportTransactionsToCsv()
            onReady(csv)
        }
    }

    fun exportFriendsCsv(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val csv = backupRestoreRepository.exportFriendsToCsv()
            onReady(csv)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(exportStatusMessage = null) }
    }

    fun resetApp(onComplete: () -> Unit) {
        viewModelScope.launch {
            backupRestoreRepository.resetApp()
            userPreferencesRepository.resetPreferences()
            _uiState.update { it.copy(exportStatusMessage = "App reset successfully. All data erased.") }
            onComplete()
        }
    }
}
