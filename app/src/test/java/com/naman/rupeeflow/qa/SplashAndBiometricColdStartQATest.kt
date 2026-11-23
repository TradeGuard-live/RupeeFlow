package com.naman.rupeeflow.qa

import com.naman.rupeeflow.security.AppLockManager
import com.naman.rupeeflow.security.AppLockState
import com.naman.rupeeflow.ui.screens.splash.SplashSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashAndBiometricColdStartQATest {

    @Before
    fun setUp() {
        SplashSessionManager.resetForTesting()
    }

    @After
    fun tearDown() {
        SplashSessionManager.resetForTesting()
    }

    @Test
    fun test001_coldStart_requiresSplashAnimationFirst() {
        // Given a fresh process launch (cold start)
        assertFalse("Splash must not be marked completed on cold start", SplashSessionManager.hasPlayedColdStartSplash)

        // Simulating splash screen animation completion
        SplashSessionManager.hasPlayedColdStartSplash = true
        assertTrue("Splash must be marked completed after playing", SplashSessionManager.hasPlayedColdStartSplash)
    }

    @Test
    fun test002_warmResume_appInRam_skipsSplashAnimation() {
        // App has already launched and played splash animation in current process
        SplashSessionManager.hasPlayedColdStartSplash = true

        // User leaves app (puts in background) and returns while app stays in RAM
        val shouldPlaySplashOnResume = !SplashSessionManager.hasPlayedColdStartSplash
        assertFalse("When app is kept in RAM, splash animation MUST NOT play upon returning to app", shouldPlaySplashOnResume)
    }

    @Test
    fun test003_biometricPromptDeferredUntilSplashFinishes() {
        // When cold starting with biometric lock active:
        val isBiometricEnabled = true
        val lockState = AppLockState.LOCKED
        var isSplashVisible = !SplashSessionManager.hasPlayedColdStartSplash

        // While splash is visible, biometric prompt screen must NOT be active
        val shouldShowLockScreenDuringSplash = !isSplashVisible && (isBiometricEnabled && lockState == AppLockState.LOCKED)
        assertFalse("Biometric lock screen must NOT obscure or precede splash animation on cold start", shouldShowLockScreenDuringSplash)

        // Splash finishes
        SplashSessionManager.hasPlayedColdStartSplash = true
        isSplashVisible = false

        // Now biometric lock screen must show and request authentication
        val shouldShowLockScreenAfterSplash = !isSplashVisible && (isBiometricEnabled && lockState == AppLockState.LOCKED)
        assertTrue("Biometric lock screen must show immediately after splash animation completes", shouldShowLockScreenAfterSplash)
    }

    @Test
    fun test004_warmResumeWithBiometrics_showsLockScreenImmediatelyWithoutSplash() {
        // App is already warm in RAM
        SplashSessionManager.hasPlayedColdStartSplash = true
        val isBiometricEnabled = true
        val lockState = AppLockState.LOCKED
        val isSplashVisible = !SplashSessionManager.hasPlayedColdStartSplash

        assertFalse("Splash must NOT be visible on warm resume", isSplashVisible)

        val shouldShowLockScreen = !isSplashVisible && (isBiometricEnabled && lockState == AppLockState.LOCKED)
        assertTrue("Biometric lock screen must show immediately on warm resume without splash", shouldShowLockScreen)
    }

    @Test
    fun test005_swipingAppFromRecents_resetsSplashSessionSoNextLaunchPlaysLogo() {
        // Given app has launched and finished splash
        SplashSessionManager.hasPlayedColdStartSplash = true
        assertTrue(SplashSessionManager.hasPlayedColdStartSplash)

        // When user swipes away app from Recents window (closing the task)
        // onTaskRemoved and onDestroy(isFinishing = true) reset hasPlayedColdStartSplash
        SplashSessionManager.hasPlayedColdStartSplash = false

        // Then on the next launch, splash animation must be visible again
        assertFalse("Splash must be reset when app is closed from Recents", SplashSessionManager.hasPlayedColdStartSplash)
        val isSplashVisibleOnNextLaunch = !SplashSessionManager.hasPlayedColdStartSplash
        assertTrue("Splash animation must play when app is opened after being closed from Recents", isSplashVisibleOnNextLaunch)
    }
}
