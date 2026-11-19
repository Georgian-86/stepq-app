package com.stepq.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.stepq.app.data.AuthRepository
import com.stepq.app.data.HomeRepository
import com.stepq.app.data.ProfileRepository
import com.stepq.app.data.Supabase
import com.stepq.app.push.PushChannels
import com.stepq.app.push.PushTokens
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class StepqApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        com.stepq.app.push.WaterInbox.schedule(this)
        registerForPush()
    }

    /**
     * The default channel (friends' water reminders, QRun reminders) plus one per kind of push,
     * so people can mute, say, water reminders in Android settings and keep the rest.
     */
    private fun createNotificationChannel() {
        fun channel(id: String, name: Int, desc: Int? = null) =
            NotificationChannel(id, getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                desc?.let { description = getString(it) }
            }
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                channel(getString(R.string.notification_channel_default), R.string.notification_channel_default_name),
                channel(PushChannels.NUDGES, R.string.notification_channel_nudges_name, R.string.notification_channel_nudges_desc),
                channel(PushChannels.WATER, R.string.notification_channel_water_name, R.string.notification_channel_water_desc),
                channel(PushChannels.NEWS, R.string.notification_channel_news_name, R.string.notification_channel_news_desc),
            ),
        )
    }

    /** Registers this phone for pushes whenever someone is signed in. */
    private fun registerForPush() {
        val client = Supabase.client ?: return
        appScope.launch {
            client.auth.sessionStatus
                .map { it is SessionStatus.Authenticated }
                .distinctUntilChanged()
                .filter { it }
                .collect { PushTokens.register() }
        }
    }
}
