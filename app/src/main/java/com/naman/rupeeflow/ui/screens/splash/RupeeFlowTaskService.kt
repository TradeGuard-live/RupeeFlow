package com.naman.rupeeflow.ui.screens.splash

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Lightweight background service bound to the app's task lifecycle.
 * When the user swipes RupeeFlow out of the Recents task switcher, Android dispatches
 * [onTaskRemoved], allowing reliable reset of the cold-start splash state even if
 * the app process remains alive due to [com.naman.rupeeflow.data.sms.RupeeFlowNotificationListener].
 */
class RupeeFlowTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        SplashSessionManager.hasPlayedColdStartSplash = false
        stopSelf()
    }
}
