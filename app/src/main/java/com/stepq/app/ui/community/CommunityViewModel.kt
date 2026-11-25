package com.stepq.app.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stepq.app.data.CommunityRepository
import com.stepq.app.data.FeedScope
import com.stepq.app.data.Post
import com.stepq.app.data.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class FeedState(
    val posts: List<Post> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val end: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
)

data class ThreadState(
    val postId: String,
    val posts: List<Post> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
) {
    val main: Post? get() = posts.firstOrNull { it.depth == 0 }
    val above: List<Post> get() = posts.filter { it.depth < 0 }
    val replies: List<Post> get() = posts.filter { it.depth > 0 }
}

data class CommunityUiState(
    val me: Profile? = null,
    val scope: FeedScope = FeedScope.Everyone,
    val feeds: Map<FeedScope, FeedState> = emptyMap(),
    val thread: ThreadState? = null,
    val posting: Boolean = false,
    val composeError: String? = null,
    val stepsToday: Int? = null,
    val toast: String? = null,
) {
    val feed: FeedState get() = feeds[scope] ?: FeedState()
}

class CommunityViewModel(private val repo: CommunityRepository) : ViewModel() {
    private val _state = MutableStateFlow(CommunityUiState())
    val state: StateFlow<CommunityUiState> = _state.asStateFlow()

    /** Reloads my profile and the feed that's showing. Called when the section opens and on pull. */
    fun refresh() {
        viewModelScope.launch {
            repo.me().onSuccess { me -> _state.update { it.copy(me = me) } }
        }
        load(_state.value.scope, reset = true)
        _state.value.thread?.let { openThread(it.postId) }
    }

    fun setScope(scope: FeedScope) {
        _state.update { it.copy(scope = scope) }
        if (_state.value.feeds[scope]?.loaded != true) load(scope, reset = true)
    }

    /** Called as the list nears its end. */
    fun loadMore() {
        val f = _state.value.feed
        if (f.loading || f.loadingMore || f.end || f.posts.isEmpty()) return
        load(_state.value.scope, reset = false)
    }

    private fun load(scope: FeedScope, reset: Boolean) {
        val current = _state.value.feeds[scope] ?: FeedState()
        val before = if (reset) null else current.posts.lastOrNull()?.entryAt
        setFeed(scope) { if (reset) it.copy(loading = it.posts.isEmpty(), error = null) else it.copy(loadingMore = true) }
        viewModelScope.launch {
            repo.feed(scope, before)
                .onSuccess { page ->
                    setFeed(scope) {
                        val posts = if (reset) page else mergePage(it.posts, page)
                        it.copy(
                            posts = posts,
                            loading = false,
                            loadingMore = false,
                            end = page.size < CommunityRepository.PAGE,
                            error = null,
                            loaded = true,
                        )
                    }
                }
                .onFailure {
                    android.util.Log.w("StepqCommunity", "Couldn't load the feed", it)
                    setFeed(scope) { f ->
                        f.copy(loading = false, loadingMore = false, error = "Couldn't load the community. Pull to try again.")
                    }
                }
        }
    }

    fun openThread(postId: String) {
        val known = findPost(postId)
        _state.update {
            val keep = it.thread?.takeIf { t -> t.postId == postId }?.posts
            it.copy(thread = ThreadState(postId, keep ?: listOfNotNull(known?.copy(depth = 0)), loading = true))
        }
        viewModelScope.launch {
            repo.thread(postId)
                .onSuccess { posts ->
                    _state.update {
                        if (it.thread?.postId != postId) it
                        else it.copy(thread = ThreadState(postId, posts, loading = false, error = if (posts.none { p -> p.depth == 0 }) "This post was deleted." else null))
                    }
                }
                .onFailure {
                    _state.update { s ->
                        if (s.thread?.postId != postId) s
                        else s.copy(thread = s.thread.copy(loading = false, error = "Couldn't load this thread. Try again."))
                    }
                }
        }
    }

    fun closeThread() = _state.update { it.copy(thread = null) }

    /** Loads today's steps for the composer's "share my steps" card. */
    fun openComposer() {
        _state.update { it.copy(composeError = null) }
        viewModelScope.launch {
            val steps = repo.myStepsToday()
            _state.update { it.copy(stepsToday = steps) }
        }
    }

    /**
     * Posts a new thread, or a reply when [parentId] is set. With [shareSteps], today's steps go
     * with it as an achievement. Calls [onDone] once it's posted.
     */
    fun post(body: String, parentId: String? = null, shareSteps: Boolean = false, onDone: () -> Unit = {}) {
        val problem = postProblem(body, shareSteps)
        if (problem != null) {
            _state.update { it.copy(composeError = problem) }
            return
        }
        if (_state.value.posting) return
        _state.update { it.copy(posting = true, composeError = null) }
        viewModelScope.launch {
            repo.create(body, parentId, if (shareSteps) LocalDate.now() else null)
                .onSuccess {
                    _state.update { s -> s.copy(posting = false, toast = if (parentId == null) "Posted" else "Reply posted") }
                    if (parentId == null) com.stepq.app.data.Pro.refreshAsync()
                    onDone()
                    if (parentId == null) {
                        // Show it at the top of every feed it belongs to.
                        _state.update { s -> s.copy(feeds = s.feeds.mapValues { (_, f) -> f.copy(loaded = false) }) }
                        load(_state.value.scope, reset = true)
                    } else {
                        bumpReplies(parentId)
                        _state.value.thread?.let { t -> openThread(t.postId) }
                    }
                }
                .onFailure { e ->
                    android.util.Log.w("StepqCommunity", "Couldn't post", e)
                    val limited = com.stepq.app.ui.components.ProSheets.showFor(e)
                    _state.update { s -> s.copy(posting = false, composeError = if (limited) null else postErrorMessage(e)) }
                }
        }
    }

    fun toggleLike(post: Post) {
        val next = !post.liked
        patch(post.id) { if (it.liked != next) it.toggledLike() else it }
        viewModelScope.launch {
            repo.setLiked(post.id, next).onFailure {
                patch(post.id) { p -> if (p.liked == next) p.toggledLike() else p }
                _state.update { s -> s.copy(toast = "Couldn't update your like. Try again.") }
            }
        }
    }

    fun toggleRepost(post: Post) {
        val next = !post.reposted
        patch(post.id) { if (it.reposted != next) it.toggledRepost() else it }
        viewModelScope.launch {
            repo.setReposted(post.id, next)
                .onSuccess { _state.update { s -> s.copy(toast = if (next) "Reposted" else "Repost removed") } }
                .onFailure {
                    patch(post.id) { p -> if (p.reposted == next) p.toggledRepost() else p }
                    _state.update { s -> s.copy(toast = "Couldn't repost. Try again.") }
                }
        }
    }

    fun delete(post: Post) {
        viewModelScope.launch {
            repo.delete(post.id)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            feeds = s.feeds.mapValues { (_, f) -> f.copy(posts = f.posts.filterNot { it.id == post.id }) },
                            thread = when {
                                s.thread == null -> null
                                s.thread.postId == post.id -> s.thread.copy(posts = emptyList(), error = "This post was deleted.")
                                else -> s.thread.copy(posts = s.thread.posts.filterNot { it.id == post.id })
                            },
                            toast = "Post deleted",
                        )
                    }
                    post.parentId?.let { bumpReplies(it, -1) }
                }
                .onFailure { _state.update { s -> s.copy(toast = "Couldn't delete the post. Try again.") } }
        }
    }

    fun report(post: Post, reason: String) {
        viewModelScope.launch {
            repo.report(post.id, reason)
                .onSuccess { _state.update { s -> s.copy(toast = "Thanks. We'll take a look.") } }
                .onFailure { _state.update { s -> s.copy(toast = "Couldn't send the report. Try again.") } }
        }
    }

    fun block(post: Post) {
        viewModelScope.launch {
            repo.block(post.authorId)
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            feeds = s.feeds.mapValues { (_, f) -> f.copy(posts = f.posts.filterNot { it.authorId == post.authorId }) },
                            thread = s.thread?.copy(posts = s.thread.posts.filterNot { it.authorId == post.authorId }),
                            toast = "Blocked @${post.authorUsername}. Unblock in Profile → Privacy and data.",
                        )
                    }
                }
                .onFailure { _state.update { s -> s.copy(toast = "Couldn't block. Try again.") } }
        }
    }

    fun clearComposeError() = _state.update { it.copy(composeError = null) }
    fun toastShown() = _state.update { it.copy(toast = null) }
    fun showToast(message: String) = _state.update { it.copy(toast = message) }

    private fun findPost(id: String): Post? =
        _state.value.thread?.posts?.firstOrNull { it.id == id }
            ?: _state.value.feeds.values.firstNotNullOfOrNull { f -> f.posts.firstOrNull { it.id == id } }

    private fun setFeed(scope: FeedScope, change: (FeedState) -> FeedState) =
        _state.update { it.copy(feeds = it.feeds + (scope to change(it.feeds[scope] ?: FeedState()))) }

    /** Applies [change] to every copy of a post: in each feed (reposts too) and the open thread. */
    private fun patch(id: String, change: (Post) -> Post) = _state.update { s ->
        s.copy(
            feeds = s.feeds.mapValues { (_, f) -> f.copy(posts = f.posts.map { if (it.id == id) change(it) else it }) },
            thread = s.thread?.copy(posts = s.thread.posts.map { if (it.id == id) change(it) else it }),
        )
    }

    private fun bumpReplies(id: String, by: Int = 1) =
        patch(id) { it.copy(replyCount = (it.replyCount + by).coerceAtLeast(0)) }
}

/** Adds the next page, skipping entries already shown (same post, same reposter). */
fun mergePage(shown: List<Post>, page: List<Post>): List<Post> {
    val seen = shown.map { it.id to it.repostedBy }.toHashSet()
    return shown + page.filter { (it.id to it.repostedBy) !in seen }
}

/** Why a post can't go yet, or null. */
fun postProblem(body: String, shareSteps: Boolean): String? = when {
    body.isBlank() && !shareSteps -> "Write something first."
    body.trim().length > CommunityRepository.MAX_LENGTH -> "Keep it under ${CommunityRepository.MAX_LENGTH} characters."
    else -> null
}

/** The server's own words when it refused a post for a reason the user can fix. */
fun postErrorMessage(e: Throwable): String {
    val m = e.message.orEmpty()
    return listOf(
        "You're posting a lot",
        "Keep it under",
        "Write something first",
        "That post was deleted",
        "Finish setting up your profile",
        "Only today's steps",
    ).firstOrNull { m.contains(it) }?.let { key ->
        Regex("""${Regex.escape(key)}[^"\n]*""").find(m)?.value?.trim() ?: key
    } ?: "Couldn't post right now. Check your connection and try again."
}
