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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.naman.rupeeflow.security.AppLockState
import com.naman.rupeeflow.ui.AppViewModelProvider
import com.naman.rupeeflow.ui.navigation.RupeeFlowApp
import com.naman.rupeeflow.ui.navigation.Screen
import com.naman.rupeeflow.ui.screens.lock.AppLockScreen
import com.naman.rupeeflow.ui.screens.lock.AppLockViewModel
import com.naman.rupeeflow.ui.theme.AppTheme
import com.naman.rupeeflow.ui.theme.RupeeFlowTheme

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

            RupeeFlowTheme(themeMode = userPrefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppTheme.colors.background
                ) {
                    when {
                        // Protected state: While locked and biometric is enabled, show lock screen
                        isBiometricEnabled && lockState == AppLockState.LOCKED -> {
                            AppLockScreen(viewModel = appLockViewModel)
                        }
                        // Splash/Init state: while resolving preference on cold launch, keep background clean
                        lockState == AppLockState.INITIALIZING -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(AppTheme.colors.background)
                            )
                        }
                        // Unlocked state: render main application and navigation
                        else -> {
                            val navController = rememberNavController()

                            // Handle notification deep link if present
                            LaunchedEffect(intent) {
                                val target = intent?.getStringExtra("EXTRA_NAVIGATE_TO")
                                if (target == "pending_review") {
                                    navController.navigate(Screen.PendingTransactions.route)
                                }
                            }

                            RupeeFlowApp(navController = navController)
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
}
