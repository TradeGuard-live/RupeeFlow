package com.naman.rupeeflow.ui.screens.lock

import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.naman.rupeeflow.security.AppLockManager
import com.naman.rupeeflow.security.AppLockState
import com.naman.rupeeflow.security.BiometricAuthHandler
import kotlinx.coroutines.flow.StateFlow

class AppLockViewModel(
    private val appLockManager: AppLockManager
) : ViewModel() {

    val lockState: StateFlow<AppLockState> = appLockManager.lockState
    val isBiometricEnabled: StateFlow<Boolean> = appLockManager.isBiometricEnabled

    @Volatile
    private var isPromptShowing = false

    fun onAuthenticationSucceeded() {
        Log.d(TAG, "onAuthenticationSucceeded: unlocking session")
        isPromptShowing = false
        appLockManager.unlockSession()
    }

    fun onAuthenticationFailed() {
        Log.d(TAG, "onAuthenticationFailed: authentication failed, app remains locked")
    }

    fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        Log.d(TAG, "onAuthenticationError: code=$errorCode, msg=$errString, app remains locked")
        isPromptShowing = false
        appLockManager.isBiometricPromptActive = false
    }

    fun triggerBiometricPrompt(activity: FragmentActivity) {
        if (lockState.value != AppLockState.LOCKED) {
            Log.d(TAG, "triggerBiometricPrompt: skipping because lockState is ${lockState.value}")
            return
        }
        if (isPromptShowing) {
            Log.d(TAG, "triggerBiometricPrompt: skipping because prompt is already showing")
            return
        }

        Log.d(TAG, "triggerBiometricPrompt: initiating biometric prompt")
        val prompt = BiometricAuthHandler.showBiometricPrompt(
            activity = activity,
            onSuccess = { onAuthenticationSucceeded() },
            onError = { code, err -> onAuthenticationError(code, err) },
            onFailed = { onAuthenticationFailed() },
            onPromptDisplayed = {
                isPromptShowing = true
                appLockManager.isBiometricPromptActive = true
            }
        )

        if (prompt == null) {
            isPromptShowing = false
            appLockManager.isBiometricPromptActive = false
        }
    }

    fun setBiometricPromptActive(active: Boolean) {
        appLockManager.isBiometricPromptActive = active
    }

    fun relock() {
        appLockManager.relockSession()
    }

    companion object {
        private const val TAG = "AppLockViewModel"
    }
}
