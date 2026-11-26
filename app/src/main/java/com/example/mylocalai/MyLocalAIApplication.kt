package com.example.mylocalai

import android.app.Application
import com.example.mylocalai.network.NetworkMonitor
import com.example.mylocalai.notification.AgentNotifications

class MyLocalAIApplication : Application() {
    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        // Create notification channels once, before any notification is posted.
        AgentNotifications.createChannels(this)
        networkMonitor = NetworkMonitor(this)
    }
}
