package com.stepq.app.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * One post as the community shows it: from app.community_feed() or app.post_thread().
 * Only the author's public fields come with it.
 */
@Serializable
data class Post(
    val id: String,
    @SerialName("author_id") val authorId: String,
    @SerialName("author_name") val authorName: String,
    @SerialName("author_username") val authorUsername: String,
    @SerialName("author_avatar") val authorAvatar: AvatarConfig = AvatarConfig(),
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("parent_username") val parentUsername: String? = null,
    val body: String = "",
    val kind: String = "thought",
    val day: String? = null,
    val steps: Int? = null,
    val goal: Int? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("like_count") val likeCount: Int = 0,
    @SerialName("reply_count") val replyCount: Int = 0,
    @SerialName("repost_count") val repostCount: Int = 0,
    val liked: Boolean = false,
    val reposted: Boolean = false,
    /** Feed only: when this entry went up (the repost time for reposts), for paging. */
    @SerialName("entry_at") val entryAt: String? = null,
    /** Feed only: who reposted it, when this entry is a repost. */
    @SerialName("reposted_by") val repostedBy: String? = null,
    /** Thread only: below 0 for the posts it replies to, 0 for the post, 1 for replies. */
    val depth: Int = 0,
) {
    val isAchievement: Boolean get() = kind == "achievement"
    val goalReached: Boolean get() = steps != null && goal != null && steps >= goal

    /** The same post with my like toggled, for an instant response before the server answers. */
    fun toggledLike(): Post = copy(liked = !liked, likeCount = (likeCount + if (liked) -1 else 1).coerceAtLeast(0))
    fun toggledRepost(): Post = copy(reposted = !reposted, repostCount = (repostCount + if (reposted) -1 else 1).coerceAtLeast(0))
}

/** Which posts the feed shows. */
enum class FeedScope(val value: String, val label: String) {
    Everyone("everyone", "For you"),
    Friends("friends", "Friends"),
    Mine("mine", "Your posts"),
}

@Serializable
private data class LikeRow(@SerialName("post_id") val postId: String, @SerialName("user_id") val userId: String)

@Serializable
private data class ReportRow(@SerialName("post_id") val postId: String, val reporter: String, val reason: String)

/** The community feed (Threads style). Reads go through RPCs; RLS limits writes to myself. */
class CommunityRepository(private val client: SupabaseClient?, private val profiles: ProfileRepository) {

    private fun c() = client ?: throw NotConfiguredException()
    private fun myId() = c().auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not signed in")

    suspend fun me(): Result<Profile?> = profiles.mine()

    /** My steps today, to preview an achievement before posting it. Null without a row yet. */
    suspend fun myStepsToday(today: LocalDate = LocalDate.now()): Int? = runCatching {
        c().postgrest["daily_steps"].select {
            filter {
                eq("user_id", myId())
                eq("day", today.toString())
            }
        }.decodeList<DailySteps>().firstOrNull()?.steps
    }.getOrNull()

    /** A page of the feed, newest first. Pass the last entry's [Post.entryAt] for the next page. */
    suspend fun feed(scope: FeedScope, before: String? = null, limit: Int = PAGE): Result<List<Post>> = runCatching {
        c().postgrest.rpc(
            "community_feed",
            buildJsonObject {
                put("p_scope", scope.value)
                if (before != null) put("p_before", before) else put("p_before", JsonNull)
                put("p_limit", limit)
            },
        ).decodeList<Post>()
    }

    /** A post with what it replies to above it and its replies below. */
    suspend fun thread(postId: String): Result<List<Post>> = runCatching {
        c().postgrest.rpc("post_thread", buildJsonObject { put("p_post", postId) }).decodeList<Post>()
    }

    /** Posts a thought, a reply ([parentId]) or, with [shareDay], my steps for that day. Returns its id. */
    suspend fun create(body: String, parentId: String? = null, shareDay: LocalDate? = null): Result<String> = runCatching {
        c().postgrest.rpc(
            "create_post",
            buildJsonObject {
                put("p_body", body.trim())
                if (parentId != null) put("p_parent", parentId) else put("p_parent", JsonNull)
                if (shareDay != null) put("p_share_day", shareDay.toString()) else put("p_share_day", JsonNull)
            },
        ).decodeAs<String>()
    }

    suspend fun setLiked(postId: String, liked: Boolean): Result<Unit> = runCatching {
        val table = c().postgrest["post_likes"]
        if (liked) {
            table.upsert(LikeRow(postId, myId())) { ignoreDuplicates = true }
        } else {
            table.delete { filter { eq("post_id", postId); eq("user_id", myId()) } }
        }
    }

    suspend fun setReposted(postId: String, reposted: Boolean): Result<Unit> = runCatching {
        val table = c().postgrest["post_reposts"]
        if (reposted) {
            table.upsert(LikeRow(postId, myId())) { ignoreDuplicates = true }
        } else {
            table.delete { filter { eq("post_id", postId); eq("user_id", myId()) } }
        }
    }

    /** Deletes my post, and with it its replies, likes and reposts. */
    suspend fun delete(postId: String): Result<Unit> = runCatching {
        c().postgrest["posts"].delete { filter { eq("id", postId); eq("author", myId()) } }
    }

    /** Reports a post. Three reports from different people hide it from everyone else. */
    suspend fun report(postId: String, reason: String): Result<Unit> = runCatching {
        c().postgrest["post_reports"].upsert(ReportRow(postId, myId(), reason.take(200))) {
            onConflict = "post_id,reporter"
            ignoreDuplicates = true
        }
    }

    companion object {
        const val PAGE = 25
        const val MAX_LENGTH = 500
    }
}
