package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Someone to add: from search, contacts, or friends of friends. Public fields only. */
@Serializable
data class Suggestion(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val username: String,
    val avatar: AvatarConfig = AvatarConfig(),
    /** Friends you have in common. */
    val mutual: Long = 0,
) {
    val person: PersonSummary get() = PersonSummary(id, displayName, username, avatar)
}

/** A13 leaderboard row: someone the user can see, with today's numbers. */
data class FriendToday(
    val person: PersonSummary,
    val steps: Int,
    val distanceM: Int,
    val isMe: Boolean,
    /** StepQ score: days the goal was reached. Null when unknown. */
    val score: Int? = null,
)

/** Everything A13 Friends shows. */
data class FriendsData(
    val me: Profile,
    /** Me and my accepted friends, most steps first. */
    val board: List<FriendToday>,
    /** People who asked to be my friend and are waiting for me. */
    val requests: List<PersonSummary>,
    /** People I asked who haven't answered yet. */
    val sentRequestIds: Set<String>,
    /** Friends I already sent a water reminder to today. */
    val remindedToday: Set<String>,
    /** Reminders I can still send today, or null when unlimited (Pro). */
    val remindersLeft: Int?,
    /** Reminders a day on my plan (admins can change it); null for unlimited. */
    val reminderLimit: Int? = FriendsRepository.FREE_REMINDERS_PER_DAY,
)

@Serializable
private data class Reminder(@SerialName("to_user") val toUser: String)

@Serializable
private data class NewReminder(@SerialName("from_user") val fromUser: String, @SerialName("to_user") val toUser: String)

@Serializable
private data class NewFriendship(val requester: String, val addressee: String)

/** Friends, friend requests and water reminders (A13, A14). Every query runs under RLS. */
class FriendsRepository(
    private val client: SupabaseClient?,
    private val profiles: ProfileRepository,
    /** My plan, for the water reminder limit (Android caches it in `Pro`). */
    private val myPlan: suspend () -> MyPlan? = { PlanRepository(client).myPlan() },
) {

    private fun db() = (client ?: throw NotConfiguredException()).postgrest

    suspend fun load(today: LocalDate = Dates.todayIst()): Result<FriendsData?> = runCatching {
        val db = db()
        val me = profiles.mine().getOrThrow() ?: return@runCatching null
        val links = db["friendships"].select().decodeList<Friendship>()
        val friendIds = links.filter { it.status == "accepted" }
            .map { if (it.requester == me.id) it.addressee else it.requester }
            .distinct()
        val sent = links.filter { it.status == "pending" && it.requester == me.id }.map { it.addressee }.toSet()

        val people = if (friendIds.isEmpty()) emptyList() else {
            db["profiles"].select { filter { isIn("id", friendIds) } }.decodeList<PersonSummary>()
        }
        val days = db["daily_steps"].select {
            filter {
                isIn("user_id", friendIds + me.id)
                eq("day", today.toString())
            }
        }.decodeList<DailySteps>().associateBy { it.userId }

        val mePerson = PersonSummary(me.id, me.displayName, me.username, me.avatar, me.dailyGoal)
        val board = (people + mePerson).map {
            val d = days[it.id]
            FriendToday(it, d?.steps ?: 0, d?.distanceM ?: 0, isMe = it.id == me.id)
        }.sortedWith(compareByDescending<FriendToday> { it.steps }.thenBy { it.person.displayName })

        // Names come from app.friend_requests() (migration 20261112000000). Without it the
        // request still shows, from my own friendships rows, so it can be accepted; the sender's
        // name stays hidden by RLS until then.
        val incoming = links.filter { it.status == "pending" && it.addressee == me.id }.map { it.requester }
        val requests = runCatching { db.rpc("friend_requests").decodeList<PersonSummary>() }
            .onFailure { logWarn("StepqFriends", "friend_requests unavailable", it) }
            .getOrNull()
            .let { named -> requestsWithFallback(named, incoming) }

        val startOfDay = Dates.startOfDay(today, IST).toString()
        val reminded = db["water_reminders"].select(io.github.jan.supabase.postgrest.query.Columns.list("to_user")) {
            filter {
                eq("from_user", me.id)
                gte("sent_at", startOfDay)
            }
        }.decodeList<Reminder>().map { it.toUser }

        val limit = reminderLimit(me.plan)
        FriendsData(
            me = me,
            board = board,
            requests = requests,
            sentRequestIds = sent,
            remindedToday = reminded.toSet(),
            remindersLeft = limit?.let { (it - reminded.size).coerceAtLeast(0) },
            reminderLimit = limit,
        )
    }

    /** My plan's daily limit from app.plan_features; the seeded default if it can't be read. */
    private suspend fun reminderLimit(plan: String?): Int? {
        val p = myPlan()
        val f = p?.feature(MyPlan.F.WATER_REMINDERS) ?: return if (plan == "pro") null else FREE_REMINDERS_PER_DAY
        return f.mine
    }

    /** One water reminder to a friend. Postgres refuses non-friends and any past my plan's daily limit. */
    suspend fun sendWaterReminder(fromId: String, toId: String): Result<Unit> = runCatching {
        db()["water_reminders"].insert(NewReminder(fromId, toId))
    }

    /** Exact-username lookup; returns only public fields. */
    suspend fun findByUsername(username: String): Result<PersonSummary?> = runCatching {
        val name = username.trim().removePrefix("@").lowercase()
        if (name.isEmpty()) return@runCatching null
        db().rpc("find_by_username", buildJsonObject { put("name", name) })
            .decodeList<PersonSummary>()
            .firstOrNull()
    }

    /**
     * Search as you type: names or usernames starting with [query]. Falls back to the exact
     * username lookup until the search migration is applied.
     */
    suspend fun searchPeople(query: String): Result<List<Suggestion>> = runCatching {
        val q = query.trim().removePrefix("@")
        if (q.length < 2) return@runCatching emptyList()
        runCatching {
            db().rpc("search_people", buildJsonObject { put("q", q) }).decodeList<Suggestion>()
        }.getOrElse {
            findByUsername(q).getOrThrow()?.let { listOf(Suggestion(it.id, it.displayName, it.username, it.avatar)) }.orEmpty()
        }
    }

    /** Friends of friends you aren't connected with yet. Empty until the migration is applied. */
    suspend fun peopleYouMayKnow(): Result<List<Suggestion>> = runCatching {
        runCatching { db().rpc("people_you_may_know").decodeList<Suggestion>() }.getOrDefault(emptyList())
    }

    /** People whose sign-in email is in the phone's contacts. Only SHA-256 hashes are sent. */
    suspend fun matchContacts(emailHashes: List<String>): Result<List<Suggestion>> = runCatching {
        if (emailHashes.isEmpty()) return@runCatching emptyList()
        db().rpc(
            "match_contacts",
            buildJsonObject {
                put("email_hashes", kotlinx.serialization.json.JsonArray(emailHashes.take(2000).map { kotlinx.serialization.json.JsonPrimitive(it) }))
            },
        ).decodeList<Suggestion>()
    }

    suspend fun sendRequest(fromId: String, toId: String): Result<Unit> = runCatching {
        db()["friendships"].insert(NewFriendship(fromId, toId))
    }

    suspend fun accept(myId: String, requesterId: String): Result<Unit> = runCatching {
        db()["friendships"].update({ set("status", "accepted") }) {
            filter {
                eq("requester", requesterId)
                eq("addressee", myId)
            }
        }
    }

    /** Declines a request, cancels one I sent, or unfriends: the row goes either way. */
    suspend fun remove(myId: String, otherId: String): Result<Unit> = runCatching {
        db()["friendships"].delete {
            filter {
                or {
                    and {
                        eq("requester", myId)
                        eq("addressee", otherId)
                    }
                    and {
                        eq("requester", otherId)
                        eq("addressee", myId)
                    }
                }
            }
        }
    }

    companion object {
        /**
         * Requests waiting for me: the named list from the server, plus any pending row it
         * missed (or all of them when the names couldn't load), so a request never goes unseen.
         */
        fun requestsWithFallback(named: List<PersonSummary>?, incomingIds: List<String>): List<PersonSummary> {
            val list = named.orEmpty()
            val known = list.map { it.id }.toSet()
            return list + incomingIds.filter { it !in known }.distinct().map { PersonSummary(it, UNKNOWN_SENDER, "") }
        }

        const val UNKNOWN_SENDER = "Someone on StepQ"

        /** Matches app.check_water_reminder(): the free plan's day is India time. */
        val IST: TimeZone = Dates.IST
        const val FREE_REMINDERS_PER_DAY = 3
    }
}
