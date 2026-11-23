package com.naman.rupeeflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.view.WindowManager
import androidx.navigation.compose.rememberNavController
import com.naman.rupeeflow.security.AppLockState
import com.naman.rupeeflow.ui.AppViewModelProvider
import com.naman.rupeeflow.ui.navigation.RupeeFlowApp
import com.naman.rupeeflow.ui.navigation.Screen
import com.naman.rupeeflow.ui.screens.lock.AppLockScreen
import com.naman.rupeeflow.ui.screens.lock.AppLockViewModel
import com.naman.rupeeflow.ui.screens.splash.SplashScreen
import com.naman.rupeeflow.ui.screens.splash.SplashSessionManager
import com.naman.rupeeflow.ui.theme.AppTheme
import com.naman.rupeeflow.ui.theme.RupeeFlowTheme
import com.naman.rupeeflow.widget.RupeeFlowWidgetManager

class MainActivity : FragmentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled gracefully
    }

    private val appLockViewModel: AppLockViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request SMS & Notification permissions gracefully
        requestRequiredPermissions()

        val app = application as RupeeFlowApplication
        val userPrefsRepo = app.appContainer.userPreferencesRepository

        setContent {
            val userPrefs by userPrefsRepo.userPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = com.naman.rupeeflow.data.repository.UserPreferences()
            )

            val lockState by appLockViewModel.lockState.collectAsStateWithLifecycle()
            val isBiometricEnabled by appLockViewModel.isBiometricEnabled.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            // Cold-start splash state (plays only once per process lifetime)
            var isSplashVisible by rememberSaveable {
                mutableStateOf(!SplashSessionManager.hasPlayedColdStartSplash)
            }

            // Security Hardening: Apply FLAG_SECURE to prevent task switcher snapshot leaks
            // and unauthorized screen captures whenever biometric lock is enabled or locked
            LaunchedEffect(isBiometricEnabled, lockState) {
                if (isBiometricEnabled || lockState == AppLockState.LOCKED) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                // Sync home screen widgets with current lock state (redacts when locked)
                RupeeFlowWidgetManager.updateAllWidgets(this@MainActivity)
            }

            RupeeFlowTheme(themeMode = userPrefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppTheme.colors.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // ✅ Mount main application whenever initialized and splash completed so that
                        // navigation backstack, open modal sheets, and active form
                        // states survive biometric authentication and external pickers
                        if (!isSplashVisible && lockState != AppLockState.INITIALIZING) {
                            LaunchedEffect(intent) {
                                val target = intent?.getStringExtra("EXTRA_NAVIGATE_TO")
                                if (target == "pending_review") {
                                    navController.navigate(Screen.PendingTransactions.route)
                                }
                            }

                            RupeeFlowApp(navController = navController)
                        }

                        // ✅ When locked and biometric enabled (and splash is done), display the full-screen AppLockScreen on top
                        // completely obscuring all protected financial data until authenticated
                        if (!isSplashVisible) {
                            if (isBiometricEnabled && lockState == AppLockState.LOCKED) {
                                AppLockScreen(viewModel = appLockViewModel)
                            } else if (lockState == AppLockState.INITIALIZING) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(AppTheme.colors.background)
                                )
                            }
                        }

                        // ✅ Cold-start Splash Animation plays ONCE per process lifetime.
                        // On cold start: plays first, then prompts for biometrics and enters app.
                        // When app is in RAM (user switches apps and returns), splash is NOT shown.
                        if (isSplashVisible) {
                            SplashScreen(
                                onSplashFinished = {
                                    SplashSessionManager.hasPlayedColdStartSplash = true
                                    isSplashVisible = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestRequiredPermissions() {
        // Only request notification posting permission on Android 13+ for transaction alerts.
        // Sensitive RECEIVE_SMS is NEVER requested on app launch; it requires explicit in-app
        // user opt-in and prominent disclosure inside Settings pursuant to Google Play Policy.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // When activity is finishing (e.g. user swiped away from Recents overview or exited app),
        // reset the splash flag so the animation plays again next time the app is launched.
        if (isFinishing) {
            SplashSessionManager.hasPlayedColdStartSplash = false
        }
    }
}
