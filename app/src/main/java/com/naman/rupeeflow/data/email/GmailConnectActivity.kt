package com.naman.rupeeflow.data.email

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.AccountPicker
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.gmail.GmailScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Explicit user-consented Gmail read-only OAuth connection. */
class GmailConnectActivity : ComponentActivity() {
    private var selectedAccount: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chooseGoogleAccount()
    }

    @Suppress("DEPRECATION")
    private fun chooseGoogleAccount() {
        val options = AccountPicker.AccountChooserOptions.Builder()
            .setAllowableAccountsTypes(listOf("com.google"))
            .build()
        startActivityForResult(AccountPicker.newChooseAccountIntent(options), REQUEST_ACCOUNT)
    }

    @Deprecated("Account picker and OAuth consent use activity results.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            finish()
            return
        }
        when (requestCode) {
            REQUEST_ACCOUNT -> {
                selectedAccount = data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (selectedAccount.isNullOrBlank()) finishWithMessage("No Google account selected")
                else authorizeGmail()
            }
            REQUEST_CONSENT -> authorizeGmail()
        }
    }

    private fun authorizeGmail() {
        val account = selectedAccount ?: return finishWithMessage("Google account missing")
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val credential = GoogleAccountCredential.usingOAuth2(
                        applicationContext, listOf(GmailScopes.GMAIL_READONLY)
                    )
                    credential.selectedAccountName = account
                    credential.token
                }
                getSharedPreferences(GmailSyncScheduler.PREFS, MODE_PRIVATE)
                    .edit().putString(GmailSyncScheduler.ACCOUNT_KEY, account).apply()
                GmailSyncScheduler.schedule(this@GmailConnectActivity)
                finishWithMessage("HDFC email transaction sync connected")
            } catch (recoverable: UserRecoverableAuthException) {
                @Suppress("DEPRECATION")
                startActivityForResult(recoverable.intent, REQUEST_CONSENT)
            } catch (_: Exception) {
                finishWithMessage("Gmail authorization failed. Please try again.")
            }
        }
    }

    private fun finishWithMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private const val REQUEST_ACCOUNT = 7101
        private const val REQUEST_CONSENT = 7102
    }
}
