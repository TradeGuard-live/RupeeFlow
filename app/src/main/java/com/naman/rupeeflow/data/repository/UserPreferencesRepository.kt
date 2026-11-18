package com.naman.rupeeflow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

import com.naman.rupeeflow.domain.model.LocationCaptureMode

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rupeeflow_preferences")

data class UserPreferences(
    val themeMode: String = "PEACHY", // "PEACHY" (default), "DARK", "LIGHT", "SYSTEM"
    val biometricLockEnabled: Boolean = false,
    val smsDetectionEnabled: Boolean = true,
    val notificationListenerEnabled: Boolean = false,
    val locationCaptureMode: LocationCaptureMode = LocationCaptureMode.OFF,
    val trustedSenders: Set<String> = setOf(
        "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "BOB", "IDFC", "PAYTM", "GPAY", "PHONEPE", "CRED"
    ),
    val defaultSplitMethod: com.naman.rupeeflow.domain.model.SplitMethod = com.naman.rupeeflow.domain.model.SplitMethod.EQUAL,
    val defaultParticipantSelection: String = "ALL", // "ALL" or "CUSTOM"
    val debtSimplificationEnabled: Boolean = true,
    val splitProCurrency: String = "INR",
    val groupNotificationsEnabled: Boolean = true,
    val settlementRemindersEnabled: Boolean = true,
    val autoCompressReceipts: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val SMS_DETECTION = booleanPreferencesKey("sms_detection")
        val NOTIFICATION_LISTENER = booleanPreferencesKey("notification_listener")
        val LOCATION_CAPTURE_MODE = stringPreferencesKey("location_capture_mode")
        val TRUSTED_SENDERS = stringSetPreferencesKey("trusted_senders")
        val DEFAULT_SPLIT_METHOD = stringPreferencesKey("default_split_method")
        val DEFAULT_PARTICIPANT_SELECTION = stringPreferencesKey("default_participant_selection")
        val DEBT_SIMPLIFICATION = booleanPreferencesKey("debt_simplification")
        val SPLIT_PRO_CURRENCY = stringPreferencesKey("split_pro_currency")
        val GROUP_NOTIFICATIONS = booleanPreferencesKey("group_notifications")
        val SETTLEMENT_REMINDERS = booleanPreferencesKey("settlement_reminders")
        val AUTO_COMPRESS_RECEIPTS = booleanPreferencesKey("auto_compress_receipts")
    }

    val userPreferencesFlow: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val locModeStr = preferences[PreferencesKeys.LOCATION_CAPTURE_MODE] ?: "OFF"
            val locMode = runCatching { LocationCaptureMode.valueOf(locModeStr) }.getOrDefault(LocationCaptureMode.OFF)

            val splitMethodStr = preferences[PreferencesKeys.DEFAULT_SPLIT_METHOD] ?: "EQUAL"
            val splitMethod = runCatching {
                com.naman.rupeeflow.domain.model.SplitMethod.valueOf(splitMethodStr)
            }.getOrDefault(com.naman.rupeeflow.domain.model.SplitMethod.EQUAL)

            UserPreferences(
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "PEACHY",
                biometricLockEnabled = preferences[PreferencesKeys.BIOMETRIC_LOCK] ?: false,
                smsDetectionEnabled = preferences[PreferencesKeys.SMS_DETECTION] ?: true,
                notificationListenerEnabled = preferences[PreferencesKeys.NOTIFICATION_LISTENER] ?: false,
                locationCaptureMode = locMode,
                trustedSenders = preferences[PreferencesKeys.TRUSTED_SENDERS] ?: setOf(
                    "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "BOB", "IDFC", "PAYTM", "GPAY", "PHONEPE", "CRED"
                ),
                defaultSplitMethod = splitMethod,
                defaultParticipantSelection = preferences[PreferencesKeys.DEFAULT_PARTICIPANT_SELECTION] ?: "ALL",
                debtSimplificationEnabled = preferences[PreferencesKeys.DEBT_SIMPLIFICATION] ?: true,
                splitProCurrency = preferences[PreferencesKeys.SPLIT_PRO_CURRENCY] ?: "INR",
                groupNotificationsEnabled = preferences[PreferencesKeys.GROUP_NOTIFICATIONS] ?: true,
                settlementRemindersEnabled = preferences[PreferencesKeys.SETTLEMENT_REMINDERS] ?: true,
                autoCompressReceipts = preferences[PreferencesKeys.AUTO_COMPRESS_RECEIPTS] ?: true
            )
        }

    suspend fun setDefaultSplitMethod(method: com.naman.rupeeflow.domain.model.SplitMethod) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_SPLIT_METHOD] = method.name
        }
    }

    suspend fun setDefaultParticipantSelection(selection: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_PARTICIPANT_SELECTION] = selection
        }
    }

    suspend fun setDebtSimplificationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEBT_SIMPLIFICATION] = enabled
        }
    }

    suspend fun setSplitProCurrency(currency: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SPLIT_PRO_CURRENCY] = currency
        }
    }

    suspend fun setGroupNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GROUP_NOTIFICATIONS] = enabled
        }
    }

    suspend fun setSettlementRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SETTLEMENT_REMINDERS] = enabled
        }
    }

    suspend fun setAutoCompressReceipts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_COMPRESS_RECEIPTS] = enabled
        }
    }

    suspend fun setThemeMode(themeMode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BIOMETRIC_LOCK] = enabled
        }
    }

    suspend fun setSmsDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SMS_DETECTION] = enabled
        }
    }

    suspend fun setNotificationListenerEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.NOTIFICATION_LISTENER] = enabled
        }
    }

    suspend fun setLocationCaptureMode(mode: LocationCaptureMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LOCATION_CAPTURE_MODE] = mode.name
        }
    }

    suspend fun setTrustedSenders(senders: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.TRUSTED_SENDERS] = senders
        }
    }

    suspend fun resetPreferences() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
