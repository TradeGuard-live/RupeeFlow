package com.naman.rupeeflow.security

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthHandler {

    private const val TAG = "BiometricAuthHandler"

    fun canAuthenticate(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = getAuthenticators()
        val canAuth = biometricManager.canAuthenticate(authenticators)
        Log.d(TAG, "canAuthenticate result: $canAuth for authenticators: $authenticators")
        return canAuth == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    }

    fun showBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
        onError: (errorCode: Int, errString: CharSequence) -> Unit,
        onFailed: () -> Unit,
        onPromptDisplayed: () -> Unit = {}
    ): BiometricPrompt? {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                Log.d(TAG, "onAuthenticationSucceeded: authenticationType=${result.authenticationType}")
                onSuccess(result)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                Log.w(TAG, "onAuthenticationError: errorCode=$errorCode, errString=$errString")
                onError(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Log.w(TAG, "onAuthenticationFailed: fingerprint not recognized")
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock RupeeFlow")
            .setSubtitle("Confirm identity to access financial records")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        return try {
            val promptInfo = promptInfoBuilder.build()
            onPromptDisplayed()
            Log.d(TAG, "Calling prompt.authenticate()")
            prompt.authenticate(promptInfo)
            prompt
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating biometric prompt", e)
            onError(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, e.localizedMessage ?: "Failed to authenticate")
            null
        }
    }
}
