package com.naman.rupeeflow.security

import android.app.Activity
import com.naman.rupeeflow.data.repository.UserPreferences
import com.naman.rupeeflow.data.repository.UserPreferencesRepository
import com.naman.rupeeflow.ui.screens.lock.AppLockViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AppLockBiometricRegressionTest {

    private lateinit var mockPrefsRepo: UserPreferencesRepository
    private lateinit var prefsFlow: MutableStateFlow<UserPreferences>
    private lateinit var mockActivity: Activity

    @Before
    fun setUp() {
        mockPrefsRepo = mock(UserPreferencesRepository::class.java)
        prefsFlow = MutableStateFlow(UserPreferences())
        `when`(mockPrefsRepo.userPreferencesFlow).thenReturn(prefsFlow)
        mockActivity = mock(Activity::class.java)
        `when`(mockActivity.isChangingConfigurations).thenReturn(false)
    }

    private fun createManager(): AppLockManager {
        return AppLockManager(mockPrefsRepo, CoroutineScope(Dispatchers.Unconfined))
    }

    // 1. Prompt appears when locked and biometric is enabled
    @Test
    fun test_01_PromptAppearsWhenLocked() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        assertEquals(AppLockState.LOCKED, viewModel.lockState.value)
        assertTrue(viewModel.isBiometricEnabled.value)
    }

    // 2. Successful fingerprint callback unlocks the app
    @Test
    fun test_02_SuccessfulFingerprintCallback() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        assertEquals(AppLockState.LOCKED, viewModel.lockState.value)

        viewModel.onAuthenticationSucceeded()
        assertEquals(AppLockState.UNLOCKED, viewModel.lockState.value)
    }

    // 3. Authentication state changes to UNLOCKED in single source of truth
    @Test
    fun test_03_AuthenticationStateChangesToUnlocked() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        assertEquals(AppLockState.LOCKED, manager.lockState.value)
        manager.unlockSession()
        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)
    }

    // 4. Lock screen disappears: StateFlow reflects UNLOCKED so Compose removes lock screen
    @Test
    fun test_04_LockScreenDisappearsOnUnlock() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationSucceeded()

        val shouldShowLockScreen = viewModel.isBiometricEnabled.value && viewModel.lockState.value == AppLockState.LOCKED
        assertFalse(shouldShowLockScreen)
    }

    // 5. Main application becomes visible when unlocked
    @Test
    fun test_05_MainApplicationBecomesVisible() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationSucceeded()

        val isMainAppVisible = viewModel.lockState.value == AppLockState.UNLOCKED
        assertTrue(isMainAppVisible)
    }

    // 6. Failed fingerprint keeps app locked
    @Test
    fun test_06_FailedFingerprintKeepsAppLocked() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationFailed()

        assertEquals(AppLockState.LOCKED, viewModel.lockState.value)
        val shouldShowLockScreen = viewModel.isBiometricEnabled.value && viewModel.lockState.value == AppLockState.LOCKED
        assertTrue(shouldShowLockScreen)
    }

    // 7. Cancelled authentication keeps app locked
    @Test
    fun test_07_CancelledAuthenticationKeepsAppLocked() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationError(10, "User cancelled")

        assertEquals(AppLockState.LOCKED, viewModel.lockState.value)
        assertFalse(manager.isBiometricPromptActive)
    }

    // 8. Authentication error keeps app locked
    @Test
    fun test_08_AuthenticationErrorKeepsAppLocked() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationError(1, "Hardware unavailable")

        assertEquals(AppLockState.LOCKED, viewModel.lockState.value)
    }

    // 9. Repeated success callback is idempotent and does not break navigation
    @Test
    fun test_09_RepeatedSuccessCallbackIsIdempotent() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationSucceeded()
        viewModel.onAuthenticationSucceeded()
        viewModel.onAuthenticationSucceeded()

        assertEquals(AppLockState.UNLOCKED, viewModel.lockState.value)
    }

    // 10. Activity recreation retains unlocked session
    @Test
    fun test_10_ActivityRecreationRetainsUnlockedSession() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel1 = AppLockViewModel(manager)
        viewModel1.onAuthenticationSucceeded()
        assertEquals(AppLockState.UNLOCKED, viewModel1.lockState.value)

        // Activity is recreated (config change or new Activity instance)
        val viewModel2 = AppLockViewModel(manager)
        assertEquals(AppLockState.UNLOCKED, viewModel2.lockState.value)
    }

    // 11. Background and foreground transitions relock the application
    @Test
    fun test_11_BackgroundAndForegroundTransitionsRelock() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        // 1. Initial start & unlock
        manager.onActivityStarted(mockActivity)
        manager.unlockSession()
        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)

        // 2. User presses home (activity stops, count goes to 0)
        manager.onActivityStopped(mockActivity)

        // 3. User returns to app (activity starts)
        manager.onActivityStarted(mockActivity)

        // Must be RELOCKED
        assertEquals(AppLockState.LOCKED, manager.lockState.value)
    }

    // 12. Cold application restart respects biometricLockEnabled
    @Test
    fun test_12_ColdStartBiometricDisabledUnlocksImmediately() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = false)
        val manager = createManager()

        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)
        assertFalse(manager.isBiometricEnabled.value)
    }

    // 13. No infinite biometric prompt loop when already unlocked
    @Test
    fun test_13_NoBiometricPromptWhenAlreadyUnlocked() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)
        viewModel.onAuthenticationSucceeded()

        // Calling prompt when already unlocked is a no-op
        viewModel.triggerBiometricPrompt(mock(androidx.fragment.app.FragmentActivity::class.java))
        assertFalse(manager.isBiometricPromptActive)
    }

    // 14. Protected financial data stays hidden before authentication
    @Test
    fun test_14_ProtectedFinancialDataStaysHiddenBeforeAuth() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        val viewModel = AppLockViewModel(manager)

        // While locked, financial screens must NOT be permitted to render
        val isFinancialDataVisible = (viewModel.lockState.value == AppLockState.UNLOCKED) ||
                (!viewModel.isBiometricEnabled.value)
        assertFalse(isFinancialDataVisible)

        // After auth, financial data is accessible
        viewModel.onAuthenticationSucceeded()
        val isFinancialDataVisibleAfterAuth = (viewModel.lockState.value == AppLockState.UNLOCKED)
        assertTrue(isFinancialDataVisibleAfterAuth)
    }

    // 15. Biometric prompt active flag prevents false relock on prompt dismiss
    @Test
    fun test_15_BiometricPromptActivePreventsFalseRelock() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        manager.onActivityStarted(mockActivity)
        manager.isBiometricPromptActive = true

        // Prompt window transition causes stopped event
        manager.onActivityStopped(mockActivity)

        // Success dismisses prompt
        manager.unlockSession()
        assertFalse(manager.isBiometricPromptActive)
        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)

        // Activity resumes/starts: should NOT falsely relock because prompt active protected it
        manager.onActivityStarted(mockActivity)
        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)
    }

    // 16. Configuration changes (orientation) do not relock
    @Test
    fun test_16_ConfigurationChangesDoNotRelock() = runBlocking {
        prefsFlow.value = UserPreferences(biometricLockEnabled = true)
        val manager = createManager()

        manager.onActivityStarted(mockActivity)
        manager.unlockSession()
        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)

        // Config change
        `when`(mockActivity.isChangingConfigurations).thenReturn(true)
        manager.onActivityStopped(mockActivity)
        manager.onActivityStarted(mockActivity)

        assertEquals(AppLockState.UNLOCKED, manager.lockState.value)
    }
}
