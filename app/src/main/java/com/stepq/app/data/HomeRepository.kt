package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate

/** Everything A12 Home shows, read with the signed-in user's token (RLS applies). */
data class HomeData(
    val me: Profile,
    /** The user's own days, newest first, enough to count a streak and fill this week. */
    val myDays: List<DailySteps>,
    /** Friends with their steps today (0 when they have no row yet). */
    val friendsToday: List<Pair<PersonSummary, Int>>,
)

class HomeRepository(private val client: SupabaseClient?, private val profiles: ProfileRepository) {

    suspend fun load(today: LocalDate): Result<HomeData?> = runCatching {
        val c = client ?: throw NotConfiguredException()
        val me = profiles.mine().getOrThrow() ?: return@runCatching null
        val db = c.postgrest

        val myDays = db["daily_steps"].select {
            filter {
                eq("user_id", me.id)
                gte("day", today.minusDays(400).toString())
            }
            order("day", Order.DESCENDING)
        }.decodeList<DailySteps>()

        val friendIds = db["friendships"].select { filter { eq("status", "accepted") } }
            .decodeList<Friendship>()
            .map { if (it.requester == me.id) it.addressee else it.requester }
            .distinct()

        val friendsToday = if (friendIds.isEmpty()) emptyList() else {
            val people = db["profiles"].select { filter { isIn("id", friendIds) } }.decodeList<PersonSummary>()
            val steps = db["daily_steps"].select {
                filter {
                    isIn("user_id", friendIds)
                    eq("day", today.toString())
                }
            }.decodeList<DailySteps>().associate { it.userId to it.steps }
            people.map { it to (steps[it.id] ?: 0) }
        }
        HomeData(me, myDays, friendsToday)
    }

    /** Saves days read from Health Connect. One upsert; RLS only lets the user write their own. */
    suspend fun saveDays(days: List<DailySteps>): Result<Unit> = runCatching {
        if (days.isEmpty()) return@runCatching
        val c = client ?: throw NotConfiguredException()
        c.postgrest["daily_steps"].upsert(days) { onConflict = "user_id,day" }
    }
}
