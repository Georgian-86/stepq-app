package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Serializable
data class Challenge(
    val id: String,
    val title: String,
    @SerialName("partner_name") val partnerName: String? = null,
    val description: String? = null,
    val reward: String? = null,
    @SerialName("target_steps") val targetSteps: Int? = null,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("ends_at") val endsAt: String,
    /** Only StepQ Pro members can join (feature all_challenges). */
    @SerialName("pro_only") val proOnly: Boolean = false,
)

@Serializable
private data class Entry(@SerialName("challenge_id") val challengeId: String, @SerialName("joined_at") val joinedAt: String)

@Serializable
private data class NewEntry(@SerialName("challenge_id") val challengeId: String, @SerialName("user_id") val userId: String)

@Serializable
private data class Count(@SerialName("challenge_id") val challengeId: String, val joined: Long)

enum class Period { Weekly, Monthly }

/** A22/A23 ranking row: total steps in the period and the place (ties share a place). */
data class Ranked(val person: PersonSummary, val steps: Int, val isMe: Boolean, val rank: Int = 0)

/** A row of app.country_ranking(). */
@Serializable
private data class RankRow(
    val rank: Int,
    val id: String,
    @SerialName("display_name") val displayName: String,
    val avatar: AvatarConfig = AvatarConfig(),
    val steps: Long,
    @SerialName("is_me") val isMe: Boolean,
    val ranked: Long,
)

data class ChallengeCard(
    val challenge: Challenge,
    val joined: Long,
    /** When I joined, or null if I haven't. */
    val myJoinedAt: Instant?,
    /** My steps since the day I joined, up to the end. */
    val myProgress: Int,
)

data class ChallengesData(
    val me: Profile,
    val weekly: List<Ranked>,
    val monthly: List<Ranked>,
    /** ISO country code the rankings cover, e.g. "IN". */
    val country: String? = null,
    /** How many people are in each ranking (everyone in the country who walked, plus me). */
    val weeklyCount: Int = weekly.size,
    val monthlyCount: Int = monthly.size,
    val challenges: List<ChallengeCard>,
)

/** Weekly and monthly rankings, and partner challenges to join (A22–A25). */
class ChallengesRepository(private val client: SupabaseClient?, private val profiles: ProfileRepository) {

    private fun db() = (client ?: throw NotConfiguredException()).postgrest

    /**
     * [country] is where the phone is from (SIM, then network, then region setting); it's saved
     * on my profile when it changes, and the rankings cover everyone with the same country.
     */
    suspend fun load(country: String?, today: LocalDate = LocalDate.now(FriendsRepository.IST)): Result<ChallengesData?> = runCatching {
        val db = db()
        var me = profiles.mine().getOrThrow() ?: return@runCatching null
        if (country != null && country != me.country) {
            runCatching { db["profiles"].update({ set("country", country) }) { filter { eq("id", me.id) } } }
                .onSuccess { me = me.copy(country = country) }
        }
        val monthStart = today.withDayOfMonth(1)
        val weekStart = weekStart(today)

        suspend fun countryRank(start: LocalDate): Pair<List<Ranked>, Int> {
            val rows = db.rpc(
                "country_ranking",
                kotlinx.serialization.json.buildJsonObject {
                    put("from_day", kotlinx.serialization.json.JsonPrimitive(start.toString()))
                    put("to_day", kotlinx.serialization.json.JsonPrimitive(today.toString()))
                    put("max_rows", kotlinx.serialization.json.JsonPrimitive(50))
                },
            ).decodeList<RankRow>()
            val list = rows.map { Ranked(PersonSummary(it.id, it.displayName, "", it.avatar), it.steps.toInt(), it.isMe, it.rank) }
            return list to (rows.firstOrNull()?.ranked?.toInt() ?: 0)
        }

        // Until the country_ranking migration is applied: everyone RLS shows me (friends, group mates).
        suspend fun visibleRank(start: LocalDate): Pair<List<Ranked>, Int> {
            val people = db["profiles"].select().decodeList<PersonSummary>()
            val days = db["daily_steps"].select {
                filter {
                    gte("day", start.toString())
                    lte("day", today.toString())
                }
            }.decodeList<DailySteps>()
            val totals = days.groupBy { it.userId }.mapValues { (_, d) -> d.sumOf { it.steps } }
            val list = withRanks(people.map { Ranked(it, totals[it.id] ?: 0, it.id == me.id) })
            return list to list.size
        }

        suspend fun rank(start: LocalDate) = runCatching { countryRank(start) }.getOrElse { visibleRank(start) }

        val (weekly, weeklyCount) = rank(weekStart)
        val (monthly, monthlyCount) = rank(monthStart)

        val now = Instant.now()
        val challenges = db["challenges"].select { filter { gt("ends_at", now.toString()) } }.decodeList<Challenge>()
            .sortedBy { it.endsAt }
        val entries = db["challenge_entries"].select().decodeList<Entry>().associateBy { it.challengeId }
        val counts = runCatching { db.rpc("challenge_counts").decodeList<Count>() }.getOrDefault(emptyList())
            .associate { it.challengeId to it.joined }
        val mySteps = db["daily_steps"].select { filter { eq("user_id", me.id) } }.decodeList<DailySteps>()

        ChallengesData(
            me = me,
            weekly = weekly,
            monthly = monthly,
            country = me.country,
            weeklyCount = weeklyCount,
            monthlyCount = monthlyCount,
            challenges = challenges.map { c ->
                val joinedAt = entries[c.id]?.joinedAt?.let(::parseInstant)
                ChallengeCard(
                    challenge = c,
                    joined = counts[c.id] ?: 0,
                    myJoinedAt = joinedAt,
                    myProgress = if (joinedAt == null) 0 else progress(mySteps, joinedAt, parseInstant(c.endsAt)),
                )
            },
        )
    }

    suspend fun join(myId: String, challengeId: String): Result<Unit> = runCatching {
        db()["challenge_entries"].insert(NewEntry(challengeId, myId))
    }

    suspend fun leave(myId: String, challengeId: String): Result<Unit> = runCatching {
        db()["challenge_entries"].delete {
            filter {
                eq("challenge_id", challengeId)
                eq("user_id", myId)
            }
        }
    }

    companion object {
        /** Sorts by steps, most first, and numbers them; ties share a place (1, 2, 2, 4). */
        fun withRanks(rows: List<Ranked>): List<Ranked> {
            val sorted = rows.sortedWith(compareByDescending<Ranked> { it.steps }.thenBy { it.person.displayName })
            return sorted.mapIndexed { i, r -> r.copy(rank = sorted.indexOfFirst { it.steps == r.steps } + 1) }
        }

        fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        /** Steps from the day of joining (India time) through the last day of the challenge. */
        fun progress(days: List<DailySteps>, joinedAt: Instant, endsAt: Instant): Int {
            val first = joinedAt.atZone(FriendsRepository.IST).toLocalDate().toString()
            val last = endsAt.atZone(FriendsRepository.IST).toLocalDate().toString()
            return days.filter { it.day in first..last }.sumOf { it.steps }
        }

        fun parseInstant(s: String): Instant =
            runCatching { Instant.parse(s) }.getOrElse { java.time.OffsetDateTime.parse(s).toInstant() }
    }
}
