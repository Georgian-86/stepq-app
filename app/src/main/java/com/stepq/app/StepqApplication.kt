package com.stepq.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.ProfileRepository
import com.stepq.app.data.Supabase

class StepqApplication : Application() {
    lateinit var auth: AuthRepository
        private set
    lateinit var profiles: ProfileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        auth = AuthRepository(Supabase.client)
        profiles = ProfileRepository(Supabase.client)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            getString(R.string.notification_channel_default),
            getString(R.string.notification_channel_default_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
