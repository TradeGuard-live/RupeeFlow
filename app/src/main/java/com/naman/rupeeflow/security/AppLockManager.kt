package com.naman.rupeeflow.security

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.naman.rupeeflow.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppLockManager(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate)
) : Application.ActivityLifecycleCallbacks {

    private val _lockState = MutableStateFlow(AppLockState.INITIALIZING)
    val lockState: StateFlow<AppLockState> = _lockState.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    @Volatile
    private var isSessionUnlocked = false

    @Volatile
    var isBiometricPromptActive = false

    // ✅ Track when an external camera or document picker activity is in flight
    @Volatile
    var isExternalActivityInFlight = false

    private val unlockListeners = mutableListOf<() -> Unit>()

    private var startedActivityCount = 0
    private var appWentToBackground = false
    private var lastBackgroundTime = 0L

    private val collectionJob = scope.launch {
        userPreferencesRepository.userPreferencesFlow.collect { prefs ->
            val enabled = prefs.biometricLockEnabled
            _isBiometricEnabled.value = enabled
            Log.d(TAG, "Preferences updated: biometricLockEnabled=$enabled, currentLockState=${_lockState.value}, sessionUnlocked=$isSessionUnlocked")

            if (!enabled) {
                isSessionUnlocked = true
                _lockState.value = AppLockState.UNLOCKED
            } else {
                if (!isSessionUnlocked) {
                    _lockState.value = AppLockState.LOCKED
                } else {
                    _lockState.value = AppLockState.UNLOCKED
                }
            }
        }
    }

    fun cleanup() {
        collectionJob.cancel()
    }

    // ✅ Register listener to be notified when biometric authentication unlocks the session
    fun addUnlockListener(listener: () -> Unit) {
        synchronized(unlockListeners) {
            if (!unlockListeners.contains(listener)) {
                unlockListeners.add(listener)
            }
        }
    }

    fun removeUnlockListener(listener: () -> Unit) {
        synchronized(unlockListeners) {
            unlockListeners.remove(listener)
        }
    }

    // ✅ Unlock session and notify listeners to resume pending operations
    fun unlockSession() {
        Log.d(TAG, "unlockSession() called: setting lockState=UNLOCKED")
        isSessionUnlocked = true
        isBiometricPromptActive = false
        _lockState.value = AppLockState.UNLOCKED

        val listenersCopy = synchronized(unlockListeners) { unlockListeners.toList() }
        listenersCopy.forEach { listener ->
            runCatching { listener() }.onFailure { Log.e(TAG, "Error in unlock listener", it) }
        }
    }

    fun relockSession() {
        Log.d(TAG, "relockSession() called: enabled=${_isBiometricEnabled.value}")
        if (_isBiometricEnabled.value) {
            isSessionUnlocked = false
            _lockState.value = AppLockState.LOCKED
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity.isChangingConfigurations) {
            Log.d(TAG, "onActivityStarted: ignoring config change for ${activity.localClassName}")
            return
        }
        startedActivityCount++
        Log.d(TAG, "onActivityStarted: count=$startedActivityCount, wasInBackground=$appWentToBackground, promptActive=$isBiometricPromptActive")

        if (startedActivityCount == 1 && appWentToBackground) {
            appWentToBackground = false
            if (_isBiometricEnabled.value && !isBiometricPromptActive) {
                Log.d(TAG, "Relocking application on return from background")
                isSessionUnlocked = false
                _lockState.value = AppLockState.LOCKED
                com.naman.rupeeflow.widget.RupeeFlowWidgetManager.updateAllWidgets(activity)
            }
        }
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity.isChangingConfigurations) {
            Log.d(TAG, "onActivityStopped: ignoring config change for ${activity.localClassName}")
            return
        }
        startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
        Log.d(TAG, "onActivityStopped: count=$startedActivityCount, promptActive=$isBiometricPromptActive")

        if (startedActivityCount == 0 && !isBiometricPromptActive) {
            appWentToBackground = true
            lastBackgroundTime = System.currentTimeMillis()
            Log.d(TAG, "Application genuinely entered background at $lastBackgroundTime")
            if (_isBiometricEnabled.value) {
                // Instantly redact widget values on home screen when app backgrounds
                com.naman.rupeeflow.widget.RupeeFlowWidgetManager.updateAllWidgets(activity)
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (activity is com.naman.rupeeflow.MainActivity && !activity.isChangingConfigurations) {
            com.naman.rupeeflow.ui.screens.splash.SplashSessionManager.hasPlayedColdStartSplash = false
        }
    }

    companion object {
        private const val TAG = "AppLockManager"
    }
}
