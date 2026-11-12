package com.stepq.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.HomeRepository
import com.stepq.app.data.ProfileRepository
import com.stepq.app.data.Supabase

class StepqApplication : Application() {
    lateinit var auth: AuthRepository
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var home: HomeRepository
        private set
    lateinit var friends: com.stepq.app.data.FriendsRepository
        private set
    lateinit var groups: com.stepq.app.data.GroupsRepository
    lateinit var qruns: com.stepq.app.data.QRunsRepository
        private set
    lateinit var challenges: com.stepq.app.data.ChallengesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        com.stepq.app.debug.CrashLog.install(this)
        Supabase.init(this)
        auth = AuthRepository(Supabase.client)
        profiles = ProfileRepository(Supabase.client)
        home = HomeRepository(Supabase.client, profiles)
        friends = com.stepq.app.data.FriendsRepository(Supabase.client, profiles)
        groups = com.stepq.app.data.GroupsRepository(Supabase.client, profiles)
        qruns = com.stepq.app.data.QRunsRepository(Supabase.client)
        challenges = com.stepq.app.data.ChallengesRepository(Supabase.client, profiles)
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
