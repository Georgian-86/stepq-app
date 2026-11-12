package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

@Serializable
data class GroupRow(
    val id: String,
    val name: String,
    @SerialName("created_by") val createdBy: String,
    @SerialName("invite_code") val inviteCode: String,
)

@Serializable
private data class MemberRow(@SerialName("group_id") val groupId: String, @SerialName("user_id") val userId: String)

@Serializable
private data class NewGroup(val name: String, @SerialName("created_by") val createdBy: String)

/** A15 card and A17 page: a group with its members' steps today, most first. */
data class GroupToday(
    val group: GroupRow,
    val members: List<FriendToday>,
) {
    val totalSteps: Int get() = members.sumOf { it.steps }
    val totalDistanceM: Int get() = members.sumOf { it.distanceM }
}

data class GroupsData(
    val me: Profile,
    val groups: List<GroupToday>,
    /** My accepted friends, to pick from when creating a group (A16). */
    val friends: List<PersonSummary>,
    val friendIds: Set<String>,
)

/** Groups (A15–A17). RLS shows only groups I'm in; joining is by invite code only. */
class GroupsRepository(private val client: SupabaseClient?, private val profiles: ProfileRepository) {

    private fun db() = (client ?: throw NotConfiguredException()).postgrest

    suspend fun load(today: LocalDate = LocalDate.now(FriendsRepository.IST)): Result<GroupsData?> = runCatching {
        val db = db()
        val me = profiles.mine().getOrThrow() ?: return@runCatching null
        val groups = db["groups"].select().decodeList<GroupRow>()
        val members = if (groups.isEmpty()) emptyList() else {
            db["group_members"].select { filter { isIn("group_id", groups.map { it.id }) } }.decodeList<MemberRow>()
        }
        val friendIds = db["friendships"].select { filter { eq("status", "accepted") } }
            .decodeList<Friendship>()
            .map { if (it.requester == me.id) it.addressee else it.requester }
            .toSet()
        val peopleIds = (members.map { it.userId } + friendIds + me.id).distinct()
        val people = db["profiles"].select { filter { isIn("id", peopleIds) } }.decodeList<PersonSummary>()
            .associateBy { it.id }
        val days = db["daily_steps"].select {
            filter {
                isIn("user_id", peopleIds)
                eq("day", today.toString())
            }
        }.decodeList<DailySteps>().associateBy { it.userId }

        val byGroup = members.groupBy { it.groupId }
        GroupsData(
            me = me,
            groups = groups.map { g ->
                val rows = byGroup[g.id].orEmpty().mapNotNull { m ->
                    val p = people[m.userId] ?: return@mapNotNull null
                    val d = days[p.id]
                    FriendToday(p, d?.steps ?: 0, d?.distanceM ?: 0, isMe = p.id == me.id)
                }.sortedWith(compareByDescending<FriendToday> { it.steps }.thenBy { it.person.displayName })
                GroupToday(g, rows)
            }.sortedByDescending { it.totalSteps },
            friends = friendIds.mapNotNull { people[it] }.sortedBy { it.displayName.lowercase() },
            friendIds = friendIds,
        )
    }

    /** A16: creates the group, then adds me and the chosen friends. */
    suspend fun create(myId: String, name: String, friendIds: Collection<String>): Result<GroupRow> = runCatching {
        val db = db()
        val group = db["groups"].insert(NewGroup(name.trim(), myId)) { select() }.decodeSingle<GroupRow>()
        db["group_members"].insert((listOf(myId) + friendIds).distinct().map { MemberRow(group.id, it) })
        group
    }

    /** Joins with an invite code. Null when no group has that code. */
    suspend fun join(code: String): Result<String?> = runCatching {
        val result = db().rpc("join_group", buildJsonObject { put("code", code.trim().lowercase()) })
            .decodeAs<kotlinx.serialization.json.JsonElement>()
        (result as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
    }

    suspend fun leave(groupId: String, myId: String): Result<Unit> = runCatching {
        db()["group_members"].delete {
            filter {
                eq("group_id", groupId)
                eq("user_id", myId)
            }
        }
    }
}

/** What a group invite says when shared. */
fun groupInviteText(groupName: String, code: String): String =
    "Join my StepQ group \"$groupName\"! Get StepQ at https://stepq.in, then go to Friends → Groups → Join with a code and enter $code"
