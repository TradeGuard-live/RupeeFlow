package com.naman.rupeeflow.data.email

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object GmailSyncScheduler {
    const val PREFS = "rupeeflow_gmail"
    const val ACCOUNT_KEY = "connected_account"
    private const val UNIQUE_WORK = "rupeeflow_hdfc_gmail_sync"
    private const val INITIAL_WORK = "rupeeflow_hdfc_gmail_initial_sync"

    fun schedule(context: Context) {
        val initial = OneTimeWorkRequestBuilder<HdfcGmailSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            INITIAL_WORK, androidx.work.ExistingWorkPolicy.KEEP, initial
        )
        val request = PeriodicWorkRequestBuilder<HdfcGmailSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun scheduleIfConnected(context: Context) {
        val account = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(ACCOUNT_KEY, null)
        if (!account.isNullOrBlank()) schedule(context)
    }

    fun disconnect(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ACCOUNT_KEY).apply()
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}
