package com.naman.rupeeflow.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rupeeflow_preferences")

data class UserPreferences(
    val themeMode: String = "DARK", // "DARK", "LIGHT", "SYSTEM"
    val biometricLockEnabled: Boolean = false,
    val smsDetectionEnabled: Boolean = true,
    val notificationListenerEnabled: Boolean = false,
    val trustedSenders: Set<String> = setOf(
        "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "BOB", "IDFC", "PAYTM", "GPAY", "PHONEPE", "CRED"
    )
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val SMS_DETECTION = booleanPreferencesKey("sms_detection")
        val NOTIFICATION_LISTENER = booleanPreferencesKey("notification_listener")
        val TRUSTED_SENDERS = stringSetPreferencesKey("trusted_senders")
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
            UserPreferences(
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "DARK",
                biometricLockEnabled = preferences[PreferencesKeys.BIOMETRIC_LOCK] ?: false,
                smsDetectionEnabled = preferences[PreferencesKeys.SMS_DETECTION] ?: true,
                notificationListenerEnabled = preferences[PreferencesKeys.NOTIFICATION_LISTENER] ?: false,
                trustedSenders = preferences[PreferencesKeys.TRUSTED_SENDERS] ?: setOf(
                    "HDFC", "SBI", "ICICI", "AXIS", "KOTAK", "BOB", "IDFC", "PAYTM", "GPAY", "PHONEPE", "CRED"
                )
            )
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
