package com.naman.rupeeflow.ui.screens.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.naman.rupeeflow.ui.theme.*

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current

    val triggerBiometric = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onUnlocked()
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock RupeeFlow")
                .setSubtitle("Confirm identity to access financial records")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            prompt.authenticate(promptInfo)
        }
    }

    LaunchedEffect(Unit) {
        triggerBiometric()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MidnightDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(ElectricBlue.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Locked",
                    tint = ElectricBlue,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "RupeeFlow is Locked",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Biometric security is active to protect your financial data.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { triggerBiometric() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricBlue,
                    contentColor = MidnightDark
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(50.dp)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unlock App", fontWeight = FontWeight.Bold)
            }
        }
    }
}
