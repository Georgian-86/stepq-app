package com.stepq.app.push

import com.stepq.app.ui.main.Tab
import kotlinx.coroutines.flow.MutableStateFlow

/** What the server sends in a push's data (see stepq.in `server/push/fcm.ts`). */
data class PushMessage(
    val title: String,
    val body: String,
    /** Android channel id: nudges, water or news. */
    val channel: String,
    val kind: String,
    val image: String?,
    val route: String,
) {
    companion object {
        val CHANNELS = setOf(PushChannels.NUDGES, PushChannels.WATER, PushChannels.NEWS)

        /** Null when there is nothing to show. Unknown channels fall back to news. */
        fun from(data: Map<String, String>, fallbackTitle: String? = null, fallbackBody: String? = null): PushMessage? {
            val title = data["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle ?: return null
            val body = data["body"] ?: fallbackBody.orEmpty()
            return PushMessage(
                title = title.take(80),
                body = body.take(300),
                channel = data["channel"]?.takeIf { it in CHANNELS } ?: PushChannels.NEWS,
                kind = data["kind"].orEmpty(),
                image = data["image"]?.takeIf { it.startsWith("https://") },
                route = data["route"]?.takeIf { it.isNotBlank() } ?: "home",
            )
        }
    }
}

object PushChannels {
    const val NUDGES = "nudges"
    const val WATER = "water"
    const val NEWS = "news"
}

/** A tapped notification's destination, picked up by MainShell once it is on screen. */
object PushRoute {
    const val EXTRA = "stepq_route"
    val pending = MutableStateFlow<String?>(null)

    fun tabFor(route: String?): Tab? = when (route) {
        null -> null
        "friends", "groups" -> Tab.Friends
        "challenges" -> Tab.Challenges
        "profile" -> Tab.Profile
        else -> Tab.Home
    }
}
