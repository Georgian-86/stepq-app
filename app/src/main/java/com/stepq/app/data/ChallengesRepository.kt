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
)

@Serializable
private data class Entry(@SerialName("challenge_id") val challengeId: String, @SerialName("joined_at") val joinedAt: String)

@Serializable
private data class NewEntry(@SerialName("challenge_id") val challengeId: String, @SerialName("user_id") val userId: String)

@Serializable
private data class Count(@SerialName("challenge_id") val challengeId: String, val joined: Long)

enum class Period { Weekly, Monthly }

/** A22/A23 ranking row: total steps in the period. */
data class Ranked(val person: PersonSummary, val steps: Int, val isMe: Boolean)

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
    val challenges: List<ChallengeCard>,
)

/** Weekly and monthly rankings, and partner challenges to join (A22–A25). */
class ChallengesRepository(private val client: SupabaseClient?, private val profiles: ProfileRepository) {

    private fun db() = (client ?: throw NotConfiguredException()).postgrest

    suspend fun load(today: LocalDate = LocalDate.now(FriendsRepository.IST)): Result<ChallengesData?> = runCatching {
        val db = db()
        val me = profiles.mine().getOrThrow() ?: return@runCatching null
        val monthStart = today.withDayOfMonth(1)
        val weekStart = weekStart(today)
        val from = minOf(monthStart, weekStart)

        // Everyone RLS lets me see: me, friends and group mates.
        val people = db["profiles"].select().decodeList<PersonSummary>()
        val days = db["daily_steps"].select {
            filter {
                gte("day", from.toString())
                lte("day", today.toString())
            }
        }.decodeList<DailySteps>()

        fun rank(start: LocalDate): List<Ranked> {
            val totals = days.filter { it.day >= start.toString() }.groupBy { it.userId }.mapValues { (_, d) -> d.sumOf { it.steps } }
            return people.map { Ranked(it, totals[it.id] ?: 0, it.id == me.id) }
                .sortedWith(compareByDescending<Ranked> { it.steps }.thenBy { it.person.displayName })
        }

        val now = Instant.now()
        val challenges = db["challenges"].select { filter { gt("ends_at", now.toString()) } }.decodeList<Challenge>()
            .sortedBy { it.endsAt }
        val entries = db["challenge_entries"].select().decodeList<Entry>().associateBy { it.challengeId }
        val counts = runCatching { db.rpc("challenge_counts").decodeList<Count>() }.getOrDefault(emptyList())
            .associate { it.challengeId to it.joined }
        val mySteps = db["daily_steps"].select { filter { eq("user_id", me.id) } }.decodeList<DailySteps>()

        ChallengesData(
            me = me,
            weekly = rank(weekStart),
            monthly = rank(monthStart),
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
