package com.naman.rupeeflow

import android.app.Application
import com.naman.rupeeflow.data.sms.TransactionNotificationHelper
import com.naman.rupeeflow.di.AppContainer
import com.naman.rupeeflow.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RupeeFlowApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = DefaultAppContainer(this)
        registerActivityLifecycleCallbacks(appContainer.appLockManager)
        // ✅ Ensure pending attachment manager is initialized with registered unlock listener
        appContainer.pendingAttachmentManager

        TransactionNotificationHelper.createNotificationChannel(this)
        com.naman.rupeeflow.data.email.GmailSyncScheduler.scheduleIfConnected(this)

        CoroutineScope(Dispatchers.IO).launch {
            appContainer.categoryRepository.ensureDefaultCategories()
        }
    }
}
