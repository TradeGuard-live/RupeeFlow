package com.naman.rupeeflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.naman.rupeeflow.ui.navigation.RupeeFlowApp
import com.naman.rupeeflow.ui.navigation.Screen
import com.naman.rupeeflow.ui.screens.lock.AppLockScreen
import com.naman.rupeeflow.ui.theme.MidnightDark
import com.naman.rupeeflow.ui.theme.RupeeFlowTheme

class MainActivity : FragmentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request SMS & Notification permissions gracefully
        requestRequiredPermissions()

        val app = application as RupeeFlowApplication
        val userPrefsRepo = app.appContainer.userPreferencesRepository

        setContent {
            val userPrefs by userPrefsRepo.userPreferencesFlow.collectAsState(
                initial = com.naman.rupeeflow.data.repository.UserPreferences()
            )

            var isAppUnlocked by remember { mutableStateOf(!userPrefs.biometricLockEnabled) }

            // Update unlock state if user turns off biometric lock
            LaunchedEffect(userPrefs.biometricLockEnabled) {
                if (!userPrefs.biometricLockEnabled) {
                    isAppUnlocked = true
                }
            }

            RupeeFlowTheme(themeMode = userPrefs.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MidnightDark
                ) {
                    if (userPrefs.biometricLockEnabled && !isAppUnlocked) {
                        AppLockScreen(
                            onUnlocked = { isAppUnlocked = true }
                        )
                    } else {
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

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECEIVE_SMS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
