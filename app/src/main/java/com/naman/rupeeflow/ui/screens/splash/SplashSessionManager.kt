package com.naman.rupeeflow.ui.screens.splash

/**
 * Tracks whether the cold-start splash animation has already run in the current process lifetime.
 *
 * Requirements:
 * - On cold process start: [hasPlayedColdStartSplash] is false -> splash animation plays once.
 * - When app is in RAM (user switches apps, minimizes, or puts app in background):
 *   [hasPlayedColdStartSplash] remains true -> splash animation must NOT play again upon resume.
 * - If biometric lock is enabled, upon cold launch the splash animation plays FIRST, then
 *   biometric authentication is requested.
 */
object SplashSessionManager {
    @Volatile
    var hasPlayedColdStartSplash: Boolean = false

    fun resetForTesting() {
        hasPlayedColdStartSplash = false
    }
}
